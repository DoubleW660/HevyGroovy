package com.example.hevygroovy.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.dto.CreateExerciseRequest
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Functional device checks. Named databases keep these tests away from personal workouts. */
class PhoneReadinessTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun mixedUnitsAndCompletedSetsSurviveStorageReopenAndDraftRestoration() {
        val database = "phone-recovery-${UUID.randomUUID()}.db"
        val userId = LocalUserIdentity(context).userId
        val workoutId = seedMixedUnitWorkout(database, userId)
        var container: AppContainer? = AppContainer(context, database)
        val current = mutableStateOf(newWorkoutModel(container!!))
        val restoration = StateRestorationTester(compose)
        try {
            restoration.setContent { HevyGroovyTheme { WorkoutHomeScreen(current.value) } }
            awaitWorkout(current.value)
            assertUnitRows()

            compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Edit workout details"))
            compose.onNodeWithText("Edit workout details").performClick()
            compose.onNodeWithTag("workout-name").performTextReplacement("Unsaved phone draft")
            compose.onNodeWithTag("workout-notes").performTextReplacement("Preserve this while rotating")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("workout-name").assertTextContains("Unsaved phone draft")
            compose.onNodeWithTag("workout-notes").assertTextContains("Preserve this while rotating")
            compose.onNodeWithText("Cancel").performClick()

            // A fresh worker, storage connection and ViewModel must reconstruct the saved session.
            container!!.close()
            container = null
            container = AppContainer(context, database)
            val reopened = newWorkoutModel(container!!)
            compose.runOnIdle { current.value = reopened }
            awaitWorkout(reopened)
            compose.runOnIdle {
                val workout = reopened.state.activeWorkout!!
                assertEquals(workoutId, workout.workoutId)
                assertEquals("Mixed units session", workout.nameSnapshot)
                assertEquals("Saved without a network account", workout.notes)
                val row = workout.exercises.single()
                assertEquals("Use full range of motion", row.notes)
                assertEquals(90, row.restTimeSeconds)
                assertEquals(listOf(WeightUnit.KG, WeightUnit.LB), row.sets.map { it.unit })
                assertEquals(listOf(true, false), row.sets.map { it.isCompleted })
            }
            assertUnitRows()
        } finally {
            container?.close()
            context.deleteDatabase(database)
        }
    }

    @Test
    fun discardConfirmationPreservesRealSessionUntilConfirmedAndSurvivesReopen() {
        val database = "phone-discard-${UUID.randomUUID()}.db"
        val userId = LocalUserIdentity(context).userId
        val workoutId = seedMixedUnitWorkout(database, userId)
        try {
            AppContainer(context, database).use { container ->
                val model = newWorkoutModel(container)
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitWorkout(model)
                compose.onNodeWithText("Discard workout").performScrollTo().performClick()
                compose.onNodeWithText("Discard workout?").assertIsDisplayed()
                compose.onNodeWithText("Keep workout").performClick()
                compose.runOnIdle { assertEquals(workoutId, model.state.activeWorkout!!.workoutId) }

                compose.onNodeWithText("Discard workout").performScrollTo().performClick()
                compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
                awaitWorkout(model)
                compose.onNodeWithText("Start empty workout").assertIsDisplayed()
                compose.runOnIdle {
                    assertNull(model.state.activeWorkout)
                    assertTrue(model.state.history.isEmpty())
                }
            }
            SQLiteAppStorage(context, database).use { reopened ->
                assertFalse(reopened.workoutSessions.getActiveWorkout(userId).isPresent)
                assertTrue(reopened.history.getWorkoutHistory(userId).isEmpty())
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun historyLoadsBeyondTwentyAndShowsOriginalUnitsNotesAndConvertedTotals() {
        val database = "phone-history-${UUID.randomUUID()}.db"
        val userId = LocalUserIdentity(context).userId
        val oldestWorkoutId = seedMixedUnitWorkout(database, userId)
        try {
            SQLiteAppStorage(context, database).use { storage ->
                val row = storage.workoutSessions.getActiveWorkout(userId).get().exercises.single()
                storage.setEntries.updateSet(userId, row.sets[1].id,
                    UpdateSetEntryRequest().apply { completed = true })
                // A large unfinished placeholder must not inflate completed-workout totals.
                storage.setEntries.addSet(userId, row.workoutExerciseId).also { placeholder ->
                    storage.setEntries.updateSet(userId, placeholder.id, UpdateSetEntryRequest().apply {
                        weight = 999.0; reps = 999; unit = WeightUnit.KG; completed = false
                    })
                }
                storage.workoutSessions.finishWorkout(userId, oldestWorkoutId)
                repeat(25) { index ->
                    val workout = storage.workoutSessions.startEmptyWorkout(userId)
                    storage.workoutSessions.renameWorkout(userId, workout.id, "Later session ${index + 1}")
                    val exercise = storage.workoutSessions.addExerciseToWorkout(userId, workout.id, row.exerciseId)
                    val set = storage.setEntries.addSet(userId, exercise.id)
                    storage.setEntries.updateSet(userId, set.id, UpdateSetEntryRequest().apply {
                        weight = 20.0; reps = 5; unit = WeightUnit.KG; completed = true
                    })
                    storage.workoutSessions.finishWorkout(userId, workout.id)
                }
                // Sharing a catalog exercise must never share another local user's history.
                val privateWorkout = storage.workoutSessions.startEmptyWorkout(userId + 1)
                storage.workoutSessions.renameWorkout(userId + 1, privateWorkout.id, "Other user's workout")
                val privateRow = storage.workoutSessions.addExerciseToWorkout(userId + 1, privateWorkout.id, row.exerciseId)
                val privateSet = storage.setEntries.addSet(userId + 1, privateRow.id)
                storage.setEntries.updateSet(userId + 1, privateSet.id,
                    UpdateSetEntryRequest().apply { completed = true })
                storage.workoutSessions.finishWorkout(userId + 1, privateWorkout.id)
            }

            AppContainer(context, database).use { container ->
                lateinit var model: HistoryViewModel
                instrumentation.runOnMainSync {
                    model = HistoryViewModel(container.history)
                    model.refresh()
                }
                compose.setContent { HevyGroovyTheme { HistoryScreen(model) {} } }
                compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
                compose.runOnIdle {
                    assertNull(model.state.error)
                    assertEquals(20, model.state.items.size)
                    assertTrue(model.state.hasMore)
                    assertFalse(model.state.items.any { it.workoutId == oldestWorkoutId })
                }
                compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-load-more"))
                compose.onNodeWithTag("history-load-more").performClick()
                compose.waitUntil(10_000) { !model.state.loadingMore && model.state.items.size == 26 }
                compose.runOnIdle {
                    assertNull(model.state.moreError)
                    assertFalse(model.state.hasMore)
                    assertEquals(26, model.state.items.map { it.workoutId }.distinct().size)
                    assertFalse(model.state.items.any { it.nameSnapshot == "Other user's workout" })
                }
                compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-card-$oldestWorkoutId"))
                compose.onNodeWithTag("history-card-$oldestWorkoutId").performClick()
                compose.waitUntil(10_000) { !model.state.detailLoading && model.state.detail != null }
                compose.runOnIdle {
                    val detail = model.state.detail!!
                    assertNull(model.state.detailError)
                    assertEquals(oldestWorkoutId, detail.workoutId)
                    assertEquals(2, detail.totalSets)
                    assertEquals(14, detail.totalReps)
                    assertEquals(480.0 + 135.0 * 6 * 0.45359237, detail.totalVolume, 0.00001)
                    assertEquals(listOf(WeightUnit.KG, WeightUnit.LB), detail.exercises.single().sets.map { it.unit })
                }
                compose.onNodeWithText("Mixed units session").assertIsDisplayed()
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("Saved without a network account"))
                compose.onNodeWithText("Saved without a network account").assertIsDisplayed()
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("Use full range of motion"))
                compose.onNodeWithText("Use full range of motion").assertIsDisplayed()
                compose.onNodeWithContentDescription("60 kg, 8 reps, Completed", substring = true)
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("135 lb, 6 reps, Completed", substring = true)
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription("999 kg", substring = true).assertDoesNotExist()
                compose.onNodeWithTag("history-detail").performScrollToNode(hasTestTag("history-back"))
                compose.onNodeWithTag("history-back").performClick()
                compose.runOnIdle {
                    assertNull(model.state.selectedWorkoutId)
                    assertEquals(26, model.state.items.size)
                }
                compose.onNodeWithTag("history-card-$oldestWorkoutId").assertIsDisplayed()
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    private fun seedMixedUnitWorkout(database: String, userId: Long): Long {
        SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Phone bench"
                primaryMuscleGroup = MuscleGroup.CHEST
            })
            val workout = storage.workoutSessions.startEmptyWorkout(userId)
            storage.workoutSessions.updateWorkoutDetails(userId, workout.id,
                "Mixed units session", "Saved without a network account")
            val row = storage.workoutSessions.addExerciseToWorkout(userId, workout.id, exercise.exerciseId)
            storage.workoutSessions.updateExerciseDetails(userId, row.id, "Use full range of motion", 90)
            storage.setEntries.addSet(userId, row.id).also { set ->
                storage.setEntries.updateSet(userId, set.id, UpdateSetEntryRequest().apply {
                    weight = 60.0; reps = 8; unit = WeightUnit.KG; completed = true
                })
            }
            storage.setEntries.addSet(userId, row.id).also { set ->
                storage.setEntries.updateSet(userId, set.id, UpdateSetEntryRequest().apply {
                    weight = 135.0; reps = 6; unit = WeightUnit.LB; completed = false
                })
            }
            return workout.id
        }
    }

    private fun assertUnitRows() {
        val kilograms = hasText("60.0 KG × 8 reps") or hasText("60 KG × 8 reps")
        val pounds = hasText("135.0 LB × 6 reps") or hasText("135 LB × 6 reps")
        compose.onNodeWithTag("workout-list").performScrollToNode(kilograms)
        compose.onNode(kilograms).assertIsDisplayed()
        compose.onNodeWithTag("workout-list").performScrollToNode(pounds)
        compose.onNode(pounds).assertIsDisplayed()
    }

    private fun newWorkoutModel(container: AppContainer): WorkoutHomeViewModel {
        lateinit var model: WorkoutHomeViewModel
        instrumentation.runOnMainSync { model = WorkoutHomeViewModel(container.workouts) }
        return model
    }

    private fun awaitWorkout(model: WorkoutHomeViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
}

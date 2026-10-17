package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.app.UserPreferences
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

class HistoryDeleteAndUnitsFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun editingExerciseMovementTypeRecalculatesExistingHistoryWithoutRestarting() {
        val database = "movement-ui-" + UUID.randomUUID() + ".db"
        val preferences = UserPreferences(context)
        val previousUnit = preferences.readDefaultUnit()
        preferences.setDefaultUnit(WeightUnit.LB)
        val user = LocalUserIdentity(context).userId
        var exercise = 0L
        var workout = 0L
        SQLiteAppStorage(context, database).use { storage ->
            exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Single leg press"; primaryMuscleGroup = MuscleGroup.QUADRICEPS
            }).exerciseId
            workout = storage.workoutSessions.startEmptyWorkout(user).id
            val row = storage.workoutSessions.addExerciseToWorkout(user, workout, exercise).id
            val set = storage.setEntries.addSet(user, row).id
            storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                weight = 140.0; reps = 17; unit = WeightUnit.LB; completed = true
            })
            storage.workoutSessions.finishWorkout(user, workout)
        }
        try {
            AppContainer(context, database).use { container ->
                val models = models(container)
                show(models)
                compose.waitUntil(10_000) { models.profile.state.loaded }
                compose.onNodeWithTag("history-tab").performClick()
                open(models.history, workout)
                compose.onNode(hasText("2,380") and hasAnyAncestor(hasTestTag("history-total-volume")))
                    .assertIsDisplayed()

                listOf(true, false).forEachIndexed { index, unilateral ->
                    compose.onNodeWithTag("exercises-tab").performClick()
                    compose.waitUntil(10_000) { models.library.state.loaded && !models.library.state.loading }
                    compose.onNodeWithTag("edit-exercise-" + exercise).performScrollTo().performClick()
                    val choice = if (unilateral) "catalog-unilateral" else "catalog-bilateral"
                    compose.onNodeWithTag(choice).performScrollTo().performClick().assertIsSelected()
                    compose.onNodeWithText("Save exercise").performClick()
                    val expected = (if (unilateral) 4760 else 2380) * 0.45359237
                    compose.waitUntil(10_000) {
                        models.library.state.exerciseChanges == index + 1L && !models.library.state.loading &&
                            !models.workouts.state.loading && !models.history.state.loading &&
                            !models.history.state.detailLoading &&
                            kotlin.math.abs(models.history.state.detail!!.totalVolume - expected) < 0.000001
                    }
                    compose.onNodeWithTag("history-tab").performClick()
                    compose.waitUntil(10_000) { !models.history.state.detailLoading }
                    compose.onNode(hasText(if (unilateral) "4,760" else "2,380") and
                        hasAnyAncestor(hasTestTag("history-total-volume"))).assertIsDisplayed()
                    compose.runOnIdle {
                        assertEquals(unilateral, models.history.state.detail!!.exercises.single().isUnilateral)
                        assertEquals(17, models.history.state.detail!!.totalReps)
                        assertEquals(140.0, models.history.state.detail!!.exercises.single().sets.single().weight, 0.0)
                        assertEquals(expected, models.workouts.state.history.single().totalVolume, 0.000001)
                    }
                }
            }
        } finally {
            preferences.setDefaultUnit(previousUnit)
            context.deleteDatabase(database)
        }
    }

    @Test fun historyTotalsRecordsAndEstimatesUseSavedProfileUnitAndUpdateWhenItChanges() {
        val database = "history-units-" + UUID.randomUUID() + ".db"
        val preferences = UserPreferences(context)
        val previousUnit = preferences.readDefaultUnit()
        preferences.setDefaultUnit(WeightUnit.LB)
        val user = LocalUserIdentity(context).userId
        val latest = seed(database, user)
        try {
            AppContainer(context, database).use { container ->
                val models = models(container)
                show(models)
                compose.waitUntil(10_000) { models.profile.state.loaded }
                compose.onNodeWithTag("history-tab").performClick()
                open(models.history, latest)
                compose.onNodeWithText("Volume (lb)").assertIsDisplayed()
                compose.onNode(hasText("500") and hasAnyAncestor(hasTestTag("history-total-volume")))
                    .assertIsDisplayed()
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("Estimated 1RM: 116.7 lb · Epley"))
                compose.onNodeWithText("Estimated 1RM: 116.7 lb · Epley").assertIsDisplayed()
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("500 lb × reps"))
                compose.onAllNodesWithText("500 lb × reps")[0].assertIsDisplayed()

                compose.onNodeWithTag("profile-tab").performClick()
                compose.onNodeWithTag("profile-unit-KG").performScrollTo().performClick()
                compose.waitUntil(10_000) { !models.profile.state.loading && models.profile.state.unit == WeightUnit.KG }
                compose.onNodeWithTag("history-tab").performClick()
                compose.waitUntil(10_000) { !models.history.state.detailLoading }
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("Volume (kg)"))
                compose.onNodeWithText("Volume (kg)").assertIsDisplayed()
                compose.onNode(hasText("226.8") and hasAnyAncestor(hasTestTag("history-total-volume")))
                    .assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(226.796185, models.history.state.detail!!.totalVolume, 0.000001)
                    assertEquals(WeightUnit.LB, models.history.state.detail!!.exercises.single().sets.single().unit)
                }
            }
        } finally {
            preferences.setDefaultUnit(previousUnit)
            context.deleteDatabase(database)
        }
    }

    @Test fun finishedWorkoutDeletionRequiresConfirmationRefreshesHomeAndSurvivesReopen() {
        val database = "history-delete-ui-" + UUID.randomUUID() + ".db"
        val user = LocalUserIdentity(context).userId
        val latest = seed(database, user)
        try {
            AppContainer(context, database).use { container ->
                val models = models(container)
                show(models)
                compose.onNodeWithTag("history-tab").performClick()
                open(models.history, latest)
                compose.onNodeWithTag("history-detail").performScrollToNode(hasTestTag("history-delete-button"))
                compose.onNodeWithTag("history-delete-button").performClick()
                compose.onNodeWithTag("history-delete-dialog").assertIsDisplayed()
                compose.onNodeWithTag("history-keep-workout").performClick()
                compose.runOnIdle { assertEquals(latest, models.history.state.selectedWorkoutId) }
                compose.onNodeWithTag("history-delete-button").performClick()
                compose.onNodeWithTag("history-confirm-delete").performClick()
                compose.waitUntil(10_000) {
                    !models.history.state.deleting && !models.history.state.loading &&
                        models.history.state.selectedWorkoutId == null &&
                        !models.workouts.state.loading &&
                        models.workouts.state.history.none { it.workoutId == latest }
                }
                compose.onNodeWithTag("history-list").assertIsDisplayed()
                compose.runOnIdle {
                    assertEquals(1, models.history.state.items.size)
                    assertEquals(1L, models.history.state.deletedWorkouts)
                    assertNull(models.history.state.deleteError)
                }
                compose.onNodeWithTag("home-tab").performClick()
                compose.onNodeWithText("Session to delete").assertDoesNotExist()
            }
            SQLiteAppStorage(context, database).use { reopened ->
                assertEquals(1, reopened.history.getWorkoutHistory(user).size)
                assertTrue(reopened.history.getWorkoutHistory(user).none { it.workoutId == latest })
            }
        } finally { context.deleteDatabase(database) }
    }

    private data class Models(val workouts: WorkoutHomeViewModel, val library: LibraryViewModel,
        val history: HistoryViewModel, val profile: ProfileViewModel, val records: ExerciseRecordsViewModel)

    private fun models(container: AppContainer): Models {
        lateinit var value: Models
        instrumentation.runOnMainSync {
            value = Models(WorkoutHomeViewModel(container.workouts), LibraryViewModel(container.library),
                HistoryViewModel(container.history), ProfileViewModel(container.profile),
                ExerciseRecordsViewModel(container.records))
        }
        return value
    }
    private fun show(models: Models) {
        compose.setContent { HevyGroovyTheme {
            WorkoutApp(models.workouts, models.library, models.history, models.profile, models.records)
        } }
        compose.waitUntil(10_000) { models.workouts.state.loaded && !models.workouts.state.loading }
    }
    private fun open(model: HistoryViewModel, id: Long) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.onNodeWithTag("history-list").performScrollToNode(hasTestTag("history-card-$id"))
        compose.onNodeWithTag("history-card-$id").performClick()
        compose.waitUntil(10_000) { model.state.detail?.workoutId == id && !model.state.detailLoading }
    }
    private fun seed(database: String, user: Long): Long {
        SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "History unit bench"; primaryMuscleGroup = MuscleGroup.CHEST
            }).exerciseId
            var latest = 0L
            listOf(60.0, 100.0).forEach { weight ->
                latest = storage.workoutSessions.startEmptyWorkout(user).id
                storage.workoutSessions.updateWorkoutDetails(user, latest,
                    if (weight == 100.0) "Session to delete" else "Earlier session", null)
                val row = storage.workoutSessions.addExerciseToWorkout(user, latest, exercise).id
                val set = storage.setEntries.addSet(user, row).id
                storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                    this.weight = weight; reps = 5; unit = WeightUnit.LB; completed = true
                })
                storage.workoutSessions.finishWorkout(user, latest)
            }
            return latest
        }
    }
}


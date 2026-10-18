package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.dto.CreateExerciseRequest
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.RecordType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Records are earned through the real UI and real storage, in an isolated local profile. */
class PersonalRecordFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun completingSetCelebratesNewBestsAndFinishingStoresThemInHistoryAndLibrary() {
        val database = "record-flow-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedProgression(database, user)
        try {
            AppContainer(context, database).use { container ->
                lateinit var workouts: WorkoutHomeViewModel
                lateinit var library: LibraryViewModel
                lateinit var history: HistoryViewModel
                lateinit var profile: ProfileViewModel
                lateinit var records: ExerciseRecordsViewModel
                instrumentation.runOnMainSync {
                    workouts = WorkoutHomeViewModel(container.workouts)
                    library = LibraryViewModel(container.library)
                    history = HistoryViewModel(container.history)
                    profile = ProfileViewModel(container.profile)
                    records = ExerciseRecordsViewModel(container.records)
                }
                compose.setContent {
                    HevyGroovyTheme { WorkoutApp(workouts, library, history, profile, records) }
                }
                awaitWorkouts(workouts)
                compose.runOnIdle { assertTrue(workouts.state.activeRecords.isEmpty()) }
                compose.onNodeWithTag("resume-workout").performClick()
                compose.onNodeWithContentDescription("Complete set 1 for Record bench press")
                    .performScrollTo().performClick()
                awaitWorkouts(workouts)
                compose.runOnIdle {
                    assertEquals(4, workouts.state.activeRecords.size)
                    assertEquals(1L, workouts.state.recordNoticeVersion)
                    assertEquals(setOf(RecordType.HEAVIEST_WEIGHT, RecordType.MOST_REPS,
                        RecordType.BEST_SET_VOLUME, RecordType.WORKOUT_VOLUME),
                        workouts.state.activeRecords.map { it.type }.toSet())
                }
                compose.onNodeWithTag("new-record-snackbar").assertIsDisplayed()
                compose.onNodeWithText("4 new records · Record bench press").assertIsDisplayed()
                compose.onNodeWithText("View", useUnmergedTree = true).performClick()
                compose.onNodeWithTag("record-notice-dialog").assertIsDisplayed()
                compose.onNodeWithText("Finish workout to save these records.")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithTag("record-notice-close").performClick()
                compose.onNodeWithTag("active-records-${fixture.rowId}")
                    .performScrollTo().assertIsDisplayed()

                compose.onNodeWithTag("finish-workout").performClick()
                compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
                awaitWorkouts(workouts)
                compose.waitUntil(10_000) {
                    !history.state.detailLoading && history.state.detail?.workoutId == fixture.workoutId
                }
                compose.runOnIdle {
                    assertNull(workouts.state.activeWorkout)
                    assertEquals(4, history.state.detail!!.personalRecords.size)
                    assertEquals(100.0, history.state.detail!!.personalRecords
                        .first { it.type == RecordType.HEAVIEST_WEIGHT }.weight, 0.0)
                }
                compose.onNodeWithTag("history-detail").performScrollToNode(hasText("Heaviest weight"))
                compose.onNodeWithText("Heaviest weight").assertIsDisplayed()

                compose.onNodeWithTag("exercises-tab").performClick()
                compose.waitUntil(10_000) { library.state.loaded && !library.state.loading }
                compose.onNodeWithTag("records-exercise-${fixture.exerciseId}")
                    .performScrollTo().performClick()
                compose.waitUntil(10_000) { records.state.loaded && !records.state.loading }
                compose.runOnIdle {
                    assertNull(records.state.error)
                    assertEquals(4, records.state.records.size)
                    assertTrue(records.state.records.all { it.workoutId == fixture.workoutId })
                    assertEquals(100.0 * 0.45359237 * (1 + 6 / 30.0),
                        records.state.estimatedOneRepMaxKg, 0.000001)
                }
                compose.onNodeWithTag("exercise-detail").assertIsDisplayed()
                compose.onNodeWithTag("exercise-tab-overview").assertIsSelected()
                compose.onNodeWithTag("exercise-overview-list").performScrollToNode(hasText("Personal records"))
                compose.onNodeWithText("Personal records").assertIsDisplayed()
                compose.onNodeWithTag("exercise-detail-back").performClick()
                compose.onNodeWithTag("exercise-detail").assertDoesNotExist()
            }
            // A new connection graph recovers the same bests after the app's worker is closed.
            SQLiteAppStorage(context, database).use { reopened ->
                val saved = reopened.personalRecords.getExerciseRecords(user, fixture.exerciseId)
                assertEquals(4, saved.size)
                assertTrue(saved.all { it.workoutId == fixture.workoutId })
                assertEquals(100.0, saved.first { it.type == RecordType.HEAVIEST_WEIGHT }.weight, 0.0)
                assertEquals(WeightUnit.LB, saved.first { it.type == RecordType.HEAVIEST_WEIGHT }.unit)
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    @Test
    fun undoingCompletionRemovesProvisionalRecordsWithoutReplacingSavedBests() {
        val database = "record-undo-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = seedProgression(database, user)
        try {
            AppContainer(context, database).use { container ->
                lateinit var model: WorkoutHomeViewModel
                instrumentation.runOnMainSync { model = WorkoutHomeViewModel(container.workouts) }
                compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
                awaitWorkouts(model)
                val completion = "Complete set 1 for Record bench press"
                compose.onNodeWithContentDescription(completion).performScrollTo().performClick()
                awaitWorkouts(model)
                compose.runOnIdle { assertEquals(4, model.state.activeRecords.size) }
                compose.onNodeWithTag("active-records-${fixture.rowId}")
                    .performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription(completion).performScrollTo().performClick()
                awaitWorkouts(model)
                compose.runOnIdle { assertTrue(model.state.activeRecords.isEmpty()) }
                compose.onNodeWithTag("active-records-${fixture.rowId}").assertDoesNotExist()
            }
            SQLiteAppStorage(context, database).use { reopened ->
                val saved = reopened.personalRecords.getExerciseRecords(user, fixture.exerciseId)
                assertEquals(90.0, saved.first { it.type == RecordType.HEAVIEST_WEIGHT }.weight, 0.0)
                assertTrue(reopened.personalRecords.getWorkoutRecords(user, fixture.workoutId).isEmpty())
            }
        } finally {
            context.deleteDatabase(database)
        }
    }

    private fun awaitWorkouts(model: WorkoutHomeViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }

    private data class Fixture(val exerciseId: Long, val workoutId: Long, val rowId: Long)

    private fun seedProgression(database: String, user: Long): Fixture {
        SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Record bench press"
                primaryMuscleGroup = MuscleGroup.CHEST
            }).exerciseId
            val baseline = storage.workoutSessions.startEmptyWorkout(user).id
            val baselineRow = storage.workoutSessions.addExerciseToWorkout(user, baseline, exercise).id
            val baselineSet = storage.setEntries.addSet(user, baselineRow).id
            storage.setEntries.updateSet(user, baselineSet, UpdateSetEntryRequest().apply {
                weight = 90.0; reps = 5; unit = WeightUnit.LB; completed = true
            })
            storage.workoutSessions.finishWorkout(user, baseline)
            val workout = storage.workoutSessions.startEmptyWorkout(user).id
            val row = storage.workoutSessions.addExerciseToWorkout(user, workout, exercise).id
            val set = storage.setEntries.addSet(user, row).id
            storage.setEntries.updateSet(user, set, UpdateSetEntryRequest().apply {
                weight = 100.0; reps = 6; unit = WeightUnit.LB; completed = false
            })
            return Fixture(exercise, workout, row)
        }
    }
}

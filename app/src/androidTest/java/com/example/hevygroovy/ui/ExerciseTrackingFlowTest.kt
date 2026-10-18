package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.app.LocalUserIdentity
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.TrackingType
import com.example.hevygroovy.persistence.SQLiteAppStorage
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import java.util.UUID
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ExerciseTrackingFlowTest {
    @get:Rule val compose = createComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun infoPageEditsCategoriesAndTrackingAndActiveSetSavesTimeAndDistance() {
        val database = "tracking-ui-${UUID.randomUUID()}.db"
        val user = LocalUserIdentity(context).userId
        val fixture = SQLiteAppStorage(context, database).use { storage ->
            val exercise = storage.exercises.createExercise(CreateExerciseRequest().apply {
                title = "Track run"; primaryMuscleGroup = MuscleGroup.OTHER
            }).exerciseId
            val workout = storage.workoutSessions.startEmptyWorkout(user).id
            val row = storage.workoutSessions.addExerciseToWorkout(user, workout, exercise).id
            val set = storage.setEntries.addSet(user, row).id
            longArrayOf(exercise, row, set)
        }
        try {
            AppContainer(context, database).use { container ->
                lateinit var workouts: WorkoutHomeViewModel
                lateinit var library: LibraryViewModel
                lateinit var records: ExerciseRecordsViewModel
                lateinit var history: HistoryViewModel
                lateinit var profile: ProfileViewModel
                instrumentation.runOnMainSync {
                    workouts = WorkoutHomeViewModel(container.workouts); library = LibraryViewModel(container.library)
                    records = ExerciseRecordsViewModel(container.records); history = HistoryViewModel(container.history)
                    profile = ProfileViewModel(container.profile)
                }
                compose.setContent { HevyGroovyTheme { WorkoutApp(workouts, library, history, profile, records) } }
                compose.waitUntil(10_000) { workouts.state.loaded && !workouts.state.loading }
                compose.onNodeWithTag("resume-workout").performClick()
                compose.onNodeWithTag("inline-weight-${fixture[2]}").performScrollTo().performTextReplacement("invalid")
                compose.onNodeWithTag("open-active-exercise-${fixture[1]}").performScrollTo().performClick()
                compose.waitUntil(10_000) { records.state.loaded && library.state.loaded && !library.state.loading }
                compose.onNodeWithTag("exercise-tab-info").performClick()
                compose.onNodeWithTag("edit-exercise-information").performScrollTo().performClick()
                compose.onNodeWithTag("tracking-DISTANCE_TIME").performScrollTo().performClick()
                compose.onNodeWithTag("exercise-major-muscle").performScrollTo().performClick()
                compose.onNodeWithTag("major-muscle-QUADRICEPS").performScrollTo().performClick()
                compose.onNodeWithTag("exercise-minor-muscles").performScrollTo().performClick()
                compose.onNodeWithTag("minor-muscle-CALVES").performScrollTo().performClick()
                compose.onNodeWithText("Done").performScrollTo().performClick()
                compose.onNodeWithText("Save exercise").performClick()
                compose.waitUntil(10_000) {
                    !library.state.loading && !workouts.state.loading &&
                        workouts.state.activeWorkout?.exercises?.firstOrNull()?.trackingType == TrackingType.DISTANCE_TIME
                }
                compose.runOnIdle {
                    val exercise = library.state.exercises.first { it.id == fixture[0] }
                    assertEquals(MuscleGroup.QUADRICEPS, exercise.primaryMuscle)
                    assertEquals(listOf(MuscleGroup.CALVES), exercise.secondaryMuscles)
                }
                compose.onNodeWithTag("exercise-detail-back").performClick()
                compose.onNodeWithTag("inline-weight-${fixture[2]}").assertDoesNotExist()
                compose.onNodeWithTag("inline-duration-${fixture[2]}").performScrollTo().performTextReplacement("90")
                compose.onNodeWithTag("inline-distance-${fixture[2]}").performTextReplacement("250")
                compose.onNodeWithTag("inline-distance-${fixture[2]}").performImeAction()
                compose.waitUntil(10_000) { !workouts.state.busy && workouts.state.activeWorkout!!.exercises[0].sets[0].distanceMeters == 250.0 }
                compose.onNodeWithTag("inline-complete-${fixture[2]}").performClick()
                compose.waitUntil(10_000) { !workouts.state.busy && workouts.state.activeWorkout!!.exercises[0].sets[0].isCompleted }
                compose.runOnIdle {
                    assertEquals(90, workouts.state.activeWorkout!!.exercises[0].sets[0].durationSeconds)
                }
                compose.onNodeWithTag("finish-workout").assertIsEnabled()
                compose.onNodeWithText("Add exercise").performScrollTo().performClick()
                compose.onNodeWithText("Create exercise").performClick()
                compose.onNodeWithTag("catalog-exercise-name").performTextReplacement("New timed carry")
                compose.onNodeWithTag("catalog-unilateral").performScrollTo().performClick()
                compose.onNodeWithTag("tracking-WEIGHT_TIME").performScrollTo().performClick()
                compose.onNodeWithText("Save exercise").performClick()
                compose.waitUntil(10_000) { !workouts.state.busy && workouts.state.exercises.any { it.title == "New timed carry" } }
                val createdId = compose.runOnIdle { workouts.state.exercises.first { it.title == "New timed carry" }.id }
                compose.onNodeWithTag("exercise-option-$createdId").performClick()
                compose.waitUntil(10_000) { !workouts.state.busy && workouts.state.activeWorkout!!.exercises.size == 2 }
                compose.runOnIdle {
                    val row = workouts.state.activeWorkout!!.exercises[1]
                    assertTrue(row.isUnilateral); assertEquals(TrackingType.WEIGHT_TIME, row.trackingType)
                }
            }
            SQLiteAppStorage(context, database).use { storage ->
                val set = storage.workoutSessions.getActiveWorkout(user).orElseThrow().exercises[0].sets[0]
                assertEquals(90, set.durationSeconds); assertEquals(250.0, set.distanceMeters!!, 0.0)
            }
        } finally { context.deleteDatabase(database) }
    }
}

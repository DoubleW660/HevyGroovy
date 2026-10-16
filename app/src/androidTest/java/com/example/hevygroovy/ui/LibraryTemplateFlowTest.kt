package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class LibraryTemplateFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun manageLibraryBuildTemplateAndCompleteSeededWorkout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "library-flow-${UUID.randomUUID()}.db"
        val gateway = AppContainer(context, name)
        lateinit var workouts: WorkoutHomeViewModel
        lateinit var library: LibraryViewModel
        lateinit var history: HistoryViewModel
        lateinit var profile: ProfileViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            workouts = WorkoutHomeViewModel(gateway.workouts); library = LibraryViewModel(gateway.library)
            history = HistoryViewModel(gateway.history)
            profile = ProfileViewModel(gateway.profile)
        }
        try {
            compose.setContent { HevyGroovyTheme { WorkoutApp(workouts, library, history, profile) } }
            awaitWorkouts(workouts)
            compose.onNodeWithText("Exercises", useUnmergedTree = true).performClick()
            awaitLibrary(library)
            compose.onNodeWithText("New exercise").performClick()
            compose.onNodeWithTag("catalog-exercise-name").performTextReplacement("Bench")
            compose.onNodeWithTag("catalog-exercise-description").performTextReplacement("Paused")
            compose.onNodeWithText("Save exercise").performClick()
            awaitLibrary(library)
            val exerciseId = library.state.exercises.single().id
            compose.onNodeWithTag("edit-exercise-$exerciseId").performScrollTo().performClick()
            compose.onNodeWithTag("catalog-exercise-name").performTextReplacement("Bench revised")
            compose.onNodeWithTag("catalog-exercise-description").performTextClearance()
            compose.onNodeWithText("Save exercise").performClick()
            awaitLibrary(library)
            assertEquals("", library.state.exercises.single().description)

            compose.onNodeWithTag("archive-exercise-$exerciseId").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-library-action").performClick()
            awaitLibrary(library)
            assertTrue(library.state.exercises.single().isArchived)
            compose.onNodeWithContentDescription("Show archived exercises").performScrollTo().performClick()
            compose.onNodeWithTag("archive-exercise-$exerciseId").performScrollTo().performClick()
            awaitLibrary(library)
            assertFalse(library.state.exercises.single().isArchived)

            compose.onNodeWithTag("templates-tab").performClick()
            awaitLibrary(library)
            compose.onNodeWithText("New routine").performClick()
            compose.onNodeWithTag("template-name").performTextReplacement("Upper A")
            compose.onNodeWithTag("template-description").performTextReplacement("Routine")
            compose.onNodeWithText("Save routine").performClick()
            awaitLibrary(library)
            val templateId = library.state.selectedTemplate!!.id

            addTemplateExercise(library, exerciseId)
            addTemplateExercise(library, exerciseId)
            val firstId = library.state.selectedTemplate!!.exercises.first().id
            val secondId = library.state.selectedTemplate!!.exercises.last().id
            compose.onNodeWithTag("template-up-$secondId").performScrollTo().performClick()
            awaitLibrary(library)
            assertEquals(secondId, library.state.selectedTemplate!!.exercises.first().id)
            compose.onNodeWithTag("template-remove-$firstId").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-library-action").performClick()
            awaitLibrary(library)
            compose.onNodeWithTag("template-rest-$secondId").performScrollTo().performClick()
            compose.onNodeWithTag("template-rest-input").performTextReplacement("90")
            compose.onNodeWithText("Save rest").performClick()
            awaitLibrary(library)
            assertEquals(90, library.state.selectedTemplate!!.exercises.single().restSeconds)

            compose.onNodeWithText("Archive routine").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-library-action").performClick()
            awaitLibrary(library)
            compose.onNodeWithText("Restore routine").performClick()
            awaitLibrary(library)
            compose.onNodeWithText("Edit routine").performClick()
            compose.onNodeWithTag("template-name").performTextReplacement("Upper revised")
            compose.onNodeWithTag("template-description").performTextClearance()
            compose.onNodeWithText("Save routine").performClick()
            awaitLibrary(library)
            assertEquals("", library.state.selectedTemplate!!.description)
            compose.onNodeWithText("Start this workout").performClick()
            awaitWorkouts(workouts)
            assertEquals(templateId, workouts.state.activeWorkout!!.templateId)
            assertEquals("Upper revised", workouts.state.activeWorkout!!.nameSnapshot)
            assertEquals(90, workouts.state.activeWorkout!!.exercises.single().restTimeSeconds)
            val workoutId = workouts.state.activeWorkout!!.workoutId

            compose.onNodeWithContentDescription("Add set to Bench revised").performScrollTo().performClick()
            awaitWorkouts(workouts)
            val setId = workouts.state.activeWorkout!!.exercises.single().sets.single().id
            compose.onNodeWithTag("inline-weight-$setId").performScrollTo().performTextReplacement("50")
            compose.onNodeWithTag("inline-reps-$setId").performTextReplacement("5")
            compose.onNodeWithTag("inline-unit-$setId").performClick()
            compose.onNodeWithText("KG").performClick()
            compose.waitUntil(10_000) {
                !workouts.state.loading && workouts.state.activeWorkout?.exercises?.single()?.sets?.single()?.unit == WeightUnit.KG
            }
            awaitWorkouts(workouts)
            compose.onNodeWithTag("inline-complete-$setId").performScrollTo().performClick()
            awaitWorkouts(workouts)

            // Finish from a session resumed over an already-loaded History tab. The
            // automatic detail navigation must also refresh that tab's existing list.
            compose.onNodeWithTag("minimize-workout").performClick()
            compose.onNodeWithTag("history-tab").performClick()
            awaitHistoryList(history)
            compose.runOnIdle { assertTrue(history.state.items.isEmpty()) }
            compose.onNodeWithTag("resume-workout").performClick()
            compose.onNodeWithTag("workout-list").performScrollToNode(hasText("Finish workout"))
            compose.onNodeWithText("Finish workout").performClick()
            compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
            awaitWorkouts(workouts)
            assertNull(workouts.state.activeWorkout)
            assertEquals("Upper revised", workouts.state.history.single().nameSnapshot)
            assertEquals(250.0, workouts.state.history.single().totalVolume, 0.0)
            awaitHistoryDetail(history, workoutId)
            compose.onNodeWithTag("history-detail").assertIsDisplayed()
            compose.runOnIdle { assertEquals(250.0, history.state.detail!!.totalVolume, 0.0) }
            compose.onNodeWithTag("history-back").performClick()
            awaitHistoryList(history)
            compose.runOnIdle { assertEquals(listOf(workoutId), history.state.items.map { it.workoutId }) }
            compose.onNodeWithTag("history-card-$workoutId").assertIsDisplayed().performClick()
            awaitHistoryDetail(history, workoutId)

            // Explicit list shortcuts must not reopen a previously selected detail.
            compose.onNodeWithTag("home-tab").performClick()
            awaitWorkouts(workouts)
            compose.onNodeWithTag("workouts-ready").performScrollToNode(hasText("View all"))
            compose.onNodeWithText("View all").performClick()
            awaitHistoryList(history)
            compose.onNodeWithTag("history-detail").assertDoesNotExist()
            compose.onNodeWithTag("history-card-$workoutId").assertIsDisplayed().performClick()
            awaitHistoryDetail(history, workoutId)

            compose.onNodeWithTag("profile-tab").performClick()
            compose.onNodeWithTag("profile-screen").performScrollToNode(hasText("Workout history"))
            compose.onNodeWithText("Workout history").performClick()
            awaitHistoryList(history)
            compose.onNodeWithTag("history-detail").assertDoesNotExist()
            compose.onNodeWithTag("history-card-$workoutId").assertIsDisplayed()
        } finally { gateway.close(); context.deleteDatabase(name) }
    }

    private fun addTemplateExercise(model: LibraryViewModel, id: Long) {
        compose.onNodeWithText("Add routine exercise").performScrollTo().performClick()
        compose.onNodeWithTag("template-picker-$id").performClick()
        awaitLibrary(model)
    }
    private fun awaitLibrary(model: LibraryViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
    private fun awaitWorkouts(model: WorkoutHomeViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
    private fun awaitHistoryList(model: HistoryViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle {
            assertNull(model.state.error)
            assertNull(model.state.selectedWorkoutId)
        }
        compose.onNodeWithTag("history-list").assertIsDisplayed()
    }
    private fun awaitHistoryDetail(model: HistoryViewModel, workoutId: Long) {
        compose.waitUntil(10_000) {
            !model.state.loading && !model.state.detailLoading && model.state.detail?.workoutId == workoutId
        }
        compose.runOnIdle { assertNull(model.state.detailError) }
    }
}

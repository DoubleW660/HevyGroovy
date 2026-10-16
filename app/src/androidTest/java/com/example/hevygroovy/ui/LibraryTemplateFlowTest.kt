package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
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
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            workouts = WorkoutHomeViewModel(gateway.workouts); library = LibraryViewModel(gateway.library)
        }
        try {
            compose.setContent { HevyGroovyTheme { WorkoutApp(workouts, library) } }
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

            compose.onNodeWithText("Templates", useUnmergedTree = true).performClick()
            awaitLibrary(library)
            compose.onNodeWithText("New template").performClick()
            compose.onNodeWithTag("template-name").performTextReplacement("Upper A")
            compose.onNodeWithTag("template-description").performTextReplacement("Routine")
            compose.onNodeWithText("Save template").performClick()
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

            compose.onNodeWithText("Archive template").performScrollTo().performClick()
            compose.onNodeWithTag("confirm-library-action").performClick()
            awaitLibrary(library)
            compose.onNodeWithText("Restore template").performClick()
            awaitLibrary(library)
            compose.onNodeWithText("Edit template").performClick()
            compose.onNodeWithTag("template-name").performTextReplacement("Upper revised")
            compose.onNodeWithTag("template-description").performTextClearance()
            compose.onNodeWithText("Save template").performClick()
            awaitLibrary(library)
            assertEquals("", library.state.selectedTemplate!!.description)
            compose.onNodeWithText("Start this workout").performClick()
            awaitWorkouts(workouts)
            assertEquals(templateId, workouts.state.activeWorkout!!.templateId)
            assertEquals("Upper revised", workouts.state.activeWorkout!!.nameSnapshot)
            assertEquals(90, workouts.state.activeWorkout!!.exercises.single().restTimeSeconds)

            compose.onNodeWithContentDescription("Add set to Bench revised").performScrollTo().performClick()
            awaitWorkouts(workouts)
            compose.onNodeWithContentDescription("Edit set 1 for Bench revised").performScrollTo().performClick()
            compose.onNodeWithTag("set-weight").performTextReplacement("50")
            compose.onNodeWithTag("set-reps").performTextReplacement("5")
            compose.onNodeWithText("KG").performClick()
            compose.onNodeWithText("Save set").performClick()
            awaitWorkouts(workouts)
            compose.onNodeWithContentDescription("Complete set 1 for Bench revised").performScrollTo().performClick()
            awaitWorkouts(workouts)
            compose.onNodeWithText("Finish workout").performScrollTo().performClick()
            compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
            awaitWorkouts(workouts)
            assertNull(workouts.state.activeWorkout)
            assertEquals("Upper revised", workouts.state.history.single().nameSnapshot)
            assertEquals(250.0, workouts.state.history.single().totalVolume, 0.0)
        } finally { gateway.close(); context.deleteDatabase(name) }
    }

    private fun addTemplateExercise(model: LibraryViewModel, id: Long) {
        compose.onNodeWithText("Add template exercise").performScrollTo().performClick()
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
}

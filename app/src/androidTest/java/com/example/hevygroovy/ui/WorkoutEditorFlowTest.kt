package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.app.AppContainer
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

/** Actual UI -> ViewModel -> worker -> Java services -> isolated SQLite file. */
class WorkoutEditorFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun createExerciseRecordSetAndFinishWorkoutIntoHistory() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "editor-${UUID.randomUUID()}.db"
        val gateway = AppContainer(context, name)
        lateinit var model: WorkoutHomeViewModel
        InstrumentationRegistry.getInstrumentation().runOnMainSync { model = WorkoutHomeViewModel(gateway.workouts) }
        try {
            val restoration = StateRestorationTester(compose)
            restoration.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
            awaitLoaded(model)
            compose.onNodeWithText("Start empty workout").performClick()
            awaitLoaded(model)
            compose.onNodeWithText("Finish workout").assertIsNotEnabled()

            compose.onNodeWithText("Edit workout details").performClick()
            compose.onNodeWithTag("workout-name").performTextReplacement("Phone session")
            compose.onNodeWithTag("workout-notes").performTextReplacement("UI integration test")
            compose.onNodeWithText("Save workout").performClick()
            awaitLoaded(model)

            compose.onNodeWithText("Add exercise").performClick()
            compose.onNodeWithText("Create exercise").performClick()
            compose.onNodeWithTag("exercise-name").performTextReplacement("Test bench")
            compose.onNodeWithText("Save exercise").performClick()
            compose.waitUntil(10_000) { !model.state.loading && model.state.exercises.any { it.title == "Test bench" } }
            val exerciseId = model.state.exercises.single { it.title == "Test bench" }.id
            compose.onNodeWithTag("exercise-option-$exerciseId").performClick()
            awaitLoaded(model)
            compose.onNodeWithContentDescription("Add set to Test bench").performScrollTo().performClick()
            awaitLoaded(model)
            val setId = model.state.activeWorkout!!.exercises.single().sets.single().id
            compose.onNodeWithTag("inline-weight-$setId").performScrollTo().performTextReplacement("60")
            compose.onNodeWithTag("inline-reps-$setId").performTextReplacement("-1")
            compose.onNodeWithTag("inline-complete-$setId").assertIsNotEnabled()
            compose.onNodeWithTag("inline-reps-$setId").performTextReplacement("8")
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithTag("inline-weight-$setId").assertTextContains("60")
            compose.onNodeWithTag("inline-reps-$setId").assertTextContains("8")
            compose.onNodeWithTag("inline-unit-$setId").performClick()
            compose.onNodeWithText("KG").performClick()
            compose.waitUntil(10_000) {
                !model.state.loading && model.state.activeWorkout?.exercises?.single()?.sets?.single()?.unit == WeightUnit.KG
            }
            awaitLoaded(model)
            compose.runOnIdle {
                val set = model.state.activeWorkout!!.exercises[0].sets[0]
                assertEquals(60.0, set.weight, 0.0)
                assertEquals(8, set.reps)
                assertEquals(WeightUnit.KG, set.unit)
                assertFalse(set.isCompleted)
            }
            compose.onNodeWithTag("inline-complete-$setId").performScrollTo().performClick()
            awaitLoaded(model)
            compose.onNodeWithText("Finish workout").performScrollTo().performClick()
            compose.onNodeWithText("Finish this workout?").assertIsDisplayed()
            compose.onNodeWithText("Finish", useUnmergedTree = true).performClick()
            awaitLoaded(model)
            compose.runOnIdle {
                assertNull(model.state.activeWorkout)
                val history = model.state.history.single()
                assertEquals("Phone session", history.nameSnapshot)
                assertEquals(1, history.totalSets)
                assertEquals(8, history.totalReps)
                assertEquals(480.0, history.totalVolume, 0.0)
            }
            compose.onNodeWithText("Phone session").assertIsDisplayed()
        } finally {
            gateway.close()
            context.deleteDatabase(name)
        }
    }

    private fun awaitLoaded(model: WorkoutHomeViewModel) {
        compose.waitUntil(10_000) { model.state.loaded && !model.state.loading }
        compose.runOnIdle { assertNull(model.state.error) }
    }
}

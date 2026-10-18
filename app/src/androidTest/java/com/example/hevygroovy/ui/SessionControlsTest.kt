package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveSetEntry
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.dto.ActiveWorkoutExercise
import com.example.hevygroovy.entity.LoggedWorkout
import com.example.hevygroovy.entity.SetEntry
import com.example.hevygroovy.entity.WorkoutExercise
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import com.example.hevygroovy.resttimer.RestTimerState
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import java.lang.reflect.Proxy
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SessionControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun finishStaysAtTheTopWhileExercisesScrollAndRetainsConfirmation() {
        val model = model()
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model, showHistory = false, onMinimize = {}) } }
        val initialTop = compose.onNodeWithTag("finish-workout").fetchSemanticsNode().boundsInRoot.top
        compose.onNodeWithTag("workout-list").performScrollToIndex(9)
        compose.onNodeWithTag("finish-workout").assertIsDisplayed().assertIsEnabled()
        assertEquals(initialTop, compose.onNodeWithTag("finish-workout").fetchSemanticsNode().boundsInRoot.top, 0.01f)
        compose.onNodeWithTag("finish-workout").performClick()
        compose.onNodeWithText("Finish this workout?").assertIsDisplayed()
        compose.onNodeWithText("Keep training").performClick()
        compose.onNodeWithTag("minimize-workout").assertIsDisplayed()
    }

    @Test
    fun pinnedFinishRemainsDisabledForAnInvalidUnsavedSet() {
        val model = model()
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model, showHistory = false, onMinimize = {}) } }
        compose.onNodeWithTag("inline-weight-11").performScrollTo().performTextReplacement("invalid")
        compose.onNodeWithTag("finish-workout").assertIsNotEnabled()
    }

    @Test
    fun timerShowsTheExerciseAndRoutesAdjustAndSkipActions() {
        val adjustments = mutableListOf<Int>()
        var skips = 0
        compose.setContent { HevyGroovyTheme {
            RestTimerBar(RestTimerState("test", 100, 1, 11, "Bench", 1_000, 75),
                onAdjust = adjustments::add, onSkip = { skips++ })
        } }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("1:15").assertIsDisplayed()
        compose.onNodeWithTag("rest-minus-15").performClick()
        compose.onNodeWithTag("rest-plus-15").performClick()
        compose.onNodeWithTag("rest-skip").performClick()
        assertEquals(listOf(-15, 15), adjustments)
        assertEquals(1, skips)
    }

    @Test
    fun completedTimerShowsGetItWithoutCountdownControlsOrPermissionPrompt() {
        compose.setContent { HevyGroovyTheme {
            RestTimerBar(RestTimerState("complete", 100, 1, 11, "Bench", 1_000, 0, isComplete = true),
                onAdjust = { error("A completed timer cannot be adjusted") },
                onSkip = { error("A completed timer has no Skip button") },
                permissionAction = { error("Completion cannot prompt for permissions") },
                permissionLabel = "Enable rest notifications")
        } }
        compose.onNodeWithText("Bench").assertIsDisplayed()
        compose.onNodeWithText("Get It!").assertIsDisplayed()
        compose.onNodeWithTag("rest-timer-remaining").assertDoesNotExist()
        compose.onNodeWithTag("rest-minus-15").assertDoesNotExist()
        compose.onNodeWithTag("rest-plus-15").assertDoesNotExist()
        compose.onNodeWithTag("rest-skip").assertDoesNotExist()
        compose.onNodeWithTag("rest-alert-permission").assertDoesNotExist()
    }

    private fun model(): WorkoutHomeViewModel {
        val workout = LoggedWorkout().apply { id = 100; userId = 1; nameSnapshot = "Upper" }
        val exercises = (1L..8L).map { id ->
            val row = WorkoutExercise().apply {
                this.id = id; loggedWorkoutId = 100; exerciseId = id; exerciseNameSnapshot = "Exercise $id"
            }
            val sets = (1..3).map { number -> ActiveSetEntry(SetEntry().apply {
                this.id = id * 10 + number; workoutExerciseId = id; setNumber = number
                weight = 100.0; reps = 8; unit = WeightUnit.LB; setType = SetType.NORMAL; setCompleted(true)
            }) }
            ActiveWorkoutExercise(row, sets)
        }
        val gateway = Proxy.newProxyInstance(WorkoutGateway::class.java.classLoader, arrayOf(WorkoutGateway::class.java)) {
            _, method, arguments ->
            if (method.name != "load") error("Unexpected ${method.name}")
            (arguments!!.last() as WorkoutGateway.Callback).onSuccess(
                WorkoutGateway.Snapshot(ActiveWorkoutDetail(workout, exercises), emptyList()))
            null
        } as WorkoutGateway
        return WorkoutHomeViewModel(gateway)
    }
}

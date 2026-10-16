package com.example.hevygroovy.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.entity.LoggedWorkout
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorkoutHomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun discardRequiresConfirmationAndKeepDoesNotDelete() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        compose.setContent { HevyGroovyTheme { WorkoutHomeScreen(model) } }
        compose.onNodeWithText("Discard workout").performClick()
        compose.onNodeWithText("Discard workout?").assertIsDisplayed()
        compose.onNodeWithText("Keep workout").performClick()
        assertEquals(0, gateway.discards)
        compose.onNodeWithText("Workout in progress").assertIsDisplayed()
        compose.onNodeWithText("Discard workout").performClick()
        compose.onNodeWithText("Discard", useUnmergedTree = true).performClick()
        compose.onNodeWithText("Start empty workout").assertIsDisplayed()
        assertEquals(1, gateway.discards)
    }

    private class FakeGateway : WorkoutGateway {
        var discards = 0
        override fun load(callback: WorkoutGateway.Callback) {
            val workout = LoggedWorkout().apply { id = 100L; userId = 1L; nameSnapshot = "Leg day" }
            callback.onSuccess(WorkoutGateway.Snapshot(ActiveWorkoutDetail(workout, emptyList()), emptyList()))
        }
        override fun startEmpty(callback: WorkoutGateway.Callback) = load(callback)
        override fun discard(workoutId: Long, callback: WorkoutGateway.Callback) {
            discards++
            callback.onSuccess(WorkoutGateway.Snapshot(null, emptyList()))
        }
    }
}

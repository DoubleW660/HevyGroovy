package com.example.hevygroovy.resttimer

import android.os.SystemClock
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.example.hevygroovy.ui.RestTimerBar
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class RestTimerCompletionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun naturalExpiryDismissesAndANewTimerSurvivesThePreviousCompletionDismissal() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        lateinit var controller: RestTimerController
        compose.runOnUiThread {
            controller = RestTimerController.get(context)
            controller.skip()
        }
        try {
            compose.setContent {
                HevyGroovyTheme {
                    val timer by controller.state.collectAsState()
                    timer?.let { RestTimerBar(it, controller::adjustSeconds, controller::skip) }
                }
            }

            compose.runOnUiThread { controller.start(900_001L, 1L, 1L, "First exercise", 1) }
            compose.waitUntil(5_000L) { controller.state.value?.isComplete == true }
            compose.onNodeWithText("Get It!").assertIsDisplayed()
            compose.onNodeWithTag("rest-skip").assertDoesNotExist()
            compose.waitUntil(5_000L) { controller.state.value == null }
            compose.onNodeWithTag("rest-timer").assertDoesNotExist()

            compose.runOnUiThread { controller.start(900_001L, 2L, 2L, "Second exercise", 1) }
            compose.waitUntil(5_000L) { controller.state.value?.isComplete == true }
            compose.onNodeWithText("Get It!").assertIsDisplayed()
            val completedToken = controller.state.value!!.token
            var replacementToken = ""
            var replacementStartedAt = 0L
            compose.runOnUiThread {
                controller.start(900_001L, 3L, 3L, "Replacement exercise", 30)
                replacementToken = controller.state.value!!.token
                replacementStartedAt = SystemClock.elapsedRealtime()
            }
            assertNotEquals(completedToken, replacementToken)
            compose.onNodeWithText("Get It!").assertDoesNotExist()
            compose.onNodeWithText("Replacement exercise").assertIsDisplayed()

            // Wait past the old message's dismissal using the controller's real clock.
            compose.waitUntil(5_000L) {
                SystemClock.elapsedRealtime() - replacementStartedAt >= 1_100L
            }
            compose.runOnIdle {
                val replacement = checkNotNull(controller.state.value)
                assertEquals(replacementToken, replacement.token)
                assertFalse(replacement.isComplete)
                assertTrue(replacement.remainingSeconds > 0)
            }
            compose.onNodeWithTag("rest-timer").assertIsDisplayed()
            compose.onNodeWithText("Replacement exercise").assertIsDisplayed()
        } finally {
            compose.runOnUiThread { controller.skip() }
        }
    }
}

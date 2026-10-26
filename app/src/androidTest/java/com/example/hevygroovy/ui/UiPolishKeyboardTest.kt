package com.example.hevygroovy.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.InputModeManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class, ExperimentalComposeUiApi::class)
class UiPolishKeyboardTest {
    @get:Rule val compose = createComposeRule()

    @Test fun primaryCardIsOneKeyboardDestinationAndSupportsEnter() {
        var visits = 0
        lateinit var inputMode: InputModeManager
        compose.setContent { HevyGroovyTheme {
            inputMode = LocalInputModeManager.current
            PrimaryActionCard("Your next session", "Choose a routine or start fresh.", "Start a workout",
                onClick = { visits++ }, enabled = true)
        } }
        compose.onAllNodes(hasClickAction()).assertCountEquals(1)
        compose.runOnIdle { inputMode.requestInputMode(InputMode.Keyboard) }
        val action = compose.onNodeWithText("Start a workout")
        action.performSemanticsAction(SemanticsActions.RequestFocus) { it() }
        action.assertHasClickAction().assertIsFocused()
        action.performKeyInput { pressKey(Key.Enter) }
        compose.runOnIdle { assertEquals(1, visits) }
    }

    @Test fun disabledPrimaryCardCannotStartASecondOperation() {
        var visits = 0
        compose.setContent { HevyGroovyTheme {
            PrimaryActionCard("Loading your session", "Your training will be ready shortly.", "Start a workout",
                onClick = { visits++ }, enabled = false)
        } }
        compose.onNodeWithText("Start a workout").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, visits) }
    }
}

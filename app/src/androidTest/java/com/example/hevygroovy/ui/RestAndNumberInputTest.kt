package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class RestAndNumberInputTest {
    @get:Rule val compose = createComposeRule()

    @Test fun numberFieldAcceptsAnEarlierValueAfterAnExternalUpdateWithoutReportingSelectionChanges() {
        var value by mutableStateOf("80")
        val edits = mutableListOf<String>()
        compose.setContent { HevyGroovyTheme {
            InlineNumberField(value, { value = it; edits += it }, false,
                KeyboardType.Decimal, "Weight", Modifier.width(140.dp).testTag("weight"), {})
        } }
        val field = compose.onNodeWithTag("weight")
        field.performClick().performTextReplacement("95")
        field.assertTextEquals("95")
        compose.runOnIdle { value = "120"; edits.clear() }
        field.assertTextEquals("120")
        field.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(0, 3, false)) }
        compose.runOnIdle { assertTrue(edits.isEmpty()) }
        field.performTextInput("95")
        field.assertTextEquals("95")
        field.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(1, 1, false)) }
        compose.runOnIdle { assertEquals(listOf("95"), edits) }
    }

    @Test fun weightAndRepsSelectAllOnFocusAndAllowNormalCursorEditingWhileFocused() {
        val edits = mutableListOf<String>()
        compose.setContent {
            HevyGroovyTheme {
                var weight by remember { mutableStateOf("80") }
                var reps by remember { mutableStateOf("8") }
                val focus = LocalFocusManager.current
                Column {
                    InlineNumberField(weight, { weight = it; edits += "weight:$it" }, false,
                        KeyboardType.Decimal, "Weight", Modifier.width(140.dp).testTag("weight"), {})
                    InlineNumberField(reps, { reps = it; edits += "reps:$it" }, false,
                        KeyboardType.Number, "Reps", Modifier.width(140.dp).testTag("reps"), {})
                    TextButton(onClick = { focus.clearFocus() }) { Text("Clear focus") }
                }
            }
        }
        val weight = compose.onNodeWithTag("weight")
        val reps = compose.onNodeWithTag("reps")
        weight.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 2)))
        compose.runOnIdle { assertTrue(edits.isEmpty()) }
        weight.performTextInput("95")
        weight.assertTextEquals("95")
        weight.performSemanticsAction(SemanticsActions.SetSelection) { assertTrue(it(1, 1, false)) }
        weight.performTextInput(".")
        weight.assertTextEquals("9.5")
        reps.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 1)))
        reps.performTextInput("10")
        reps.assertTextEquals("10")
        weight.performClick().assert(SemanticsMatcher.expectValue(SemanticsProperties.TextSelectionRange, TextRange(0, 3)))
        weight.performTextInput("100")
        weight.assertTextEquals("100")
        compose.onNodeWithText("Clear focus").performClick()
        compose.runOnIdle {
            assertEquals(listOf("weight:95", "weight:9.5", "reps:10", "weight:100"), edits)
        }
    }

    @Test fun restWheelCrossesMinuteBoundaryAndSavesOnlyOnConfirmation() {
        val saved = mutableListOf<Int?>()
        compose.setContent { HevyGroovyTheme { RestDurationPicker(55, {}, { saved += it }) } }
        compose.onNodeWithTag("rest-duration-60").performClick()
        compose.onNodeWithTag("rest-duration-60").assertIsSelected()
        compose.onNodeWithTag("rest-duration-75").performClick()
        compose.onNodeWithTag("rest-duration-75").assertIsSelected()
        compose.runOnIdle { assertTrue(saved.isEmpty()) }
        compose.onNodeWithTag("rest-duration-wheel").performScrollToIndex(restDurationOptions.indexOf(600))
        compose.onNodeWithTag("rest-duration-600").performClick().assertIsSelected()
        compose.onNodeWithTag("save-rest-duration").performClick()
        compose.runOnIdle { assertEquals(listOf(600), saved) }
    }

    @Test fun cancellingRestWheelDiscardsAnEditedSelection() {
        val saved = mutableListOf<Int?>()
        var dismissed = false
        compose.setContent { HevyGroovyTheme { RestDurationPicker(60, { dismissed = true }, { saved += it }) } }
        compose.onNodeWithTag("rest-duration-75").performClick().assertIsSelected()
        compose.onNodeWithTag("cancel-rest-duration").performClick()
        compose.runOnIdle { assertTrue(dismissed); assertTrue(saved.isEmpty()) }
    }

    @Test fun offGridSavedDurationSurvivesOpeningAndSavingWithoutMovingTheWheel() {
        var saved: Int? = null
        compose.setContent { HevyGroovyTheme { RestDurationPicker(73, {}, { saved = it }) } }
        compose.onNodeWithTag("rest-duration-73").assertIsSelected()
        compose.onNodeWithTag("save-rest-duration").performClick()
        compose.runOnIdle { assertEquals(73, saved) }
    }

    @Test fun savedDurationAboveTenMinutesIsPreserved() {
        var saved: Int? = null
        compose.setContent { HevyGroovyTheme { RestDurationPicker(750, {}, { saved = it }) } }
        compose.onNodeWithTag("rest-duration-750").assertIsSelected()
        compose.onNodeWithTag("save-rest-duration").performClick()
        compose.runOnIdle { assertEquals(750, saved) }
    }

    @Test fun optionalSetRestDistinguishesInheritingExerciseRestFromZeroSeconds() {
        val saved = mutableListOf<Int?>()
        compose.setContent { HevyGroovyTheme { RestDurationPicker(null, {}, { saved += it }, allowInherit = true) } }
        compose.onNodeWithTag("rest-duration-inherit").assertIsSelected()
        compose.onNodeWithTag("save-rest-duration").performClick()
        compose.onNodeWithTag("rest-duration-0").performClick().assertIsSelected()
        compose.onNodeWithTag("save-rest-duration").performClick()
        compose.runOnIdle { assertEquals(listOf(null, 0), saved) }
    }
}

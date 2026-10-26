package com.example.hevygroovy.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.app.HistoryGateway
import com.example.hevygroovy.app.ProfileGateway
import com.example.hevygroovy.dto.WorkoutExerciseHistory
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.WorkoutHistoryDetail
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.Duration
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

class UiPolishTest {
    @get:Rule val compose = createComposeRule()

    @Test fun shortLandscapeRestPickerWithLargeTextKeepsActionsReachableAndSavesSelection() {
        val saved = mutableListOf<Int?>()
        var dismissed = false
        compose.setContent { HevyGroovyTheme {
            val density = LocalDensity.current
            val landscape = Configuration(LocalConfiguration.current).apply {
                orientation = Configuration.ORIENTATION_LANDSCAPE
                screenWidthDp = 640
                screenHeightDp = 320
                fontScale = 2f
            }
            CompositionLocalProvider(LocalConfiguration provides landscape,
                LocalDensity provides Density(density.density, landscape.fontScale)) {
                RestDurationPicker(null, onDismiss = { dismissed = true }, onSave = saved::add,
                    allowInherit = true)
            }
        } }

        val save = compose.onNodeWithTag("save-rest-duration")
        val cancel = compose.onNodeWithTag("cancel-rest-duration")
        val content = compose.onNodeWithTag("rest-duration-layout").getUnclippedBoundsInRoot()
        assertTrue("Rest picker content exceeds the configured short screen: content=$content, configuredHeight=320dp",
            content.bottom - content.top <= 320.dp)
        listOf("Cancel" to cancel, "Save rest" to save).forEach { (label, action) ->
            action.assertIsDisplayed().assertHasClickAction().assertHeightIsAtLeast(48.dp)
            // The modal's root owns a separate window; compare actions with its measured content instead.
            val bounds = action.getUnclippedBoundsInRoot()
            assertTrue("Rest picker $label action extends outside its content: action=$bounds, content=$content, configuredHeight=320dp",
                bounds.top >= content.top && bounds.bottom <= content.bottom)
        }

        val wheel = compose.onNodeWithTag("rest-duration-wheel")
        wheel.performScrollTo().assertIsDisplayed()
        val inheritedRow = compose.onNodeWithTag("rest-duration-inherit").getUnclippedBoundsInRoot()
        compose.onNodeWithText("Use exercise rest").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { getLayout ->
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(getLayout(layouts))
            val layout = layouts.single()
            assertEquals(2f, layout.layoutInput.density.fontScale)
            // Paragraph widths can exceed the rounded integer text bounds by a fraction of a pixel.
            assertFalse("The inherited-rest label is clipped: size=${layout.size}, lines=${layout.lineCount}, " +
                "overflowHeight=${layout.didOverflowHeight}, overflowWidth=${layout.didOverflowWidth}, " +
                "constraints=${layout.layoutInput.constraints}, fontScale=${layout.layoutInput.density.fontScale}, row=$inheritedRow",
                layout.didOverflowHeight || layout.multiParagraph.width > layout.size.width + 1f)
        }
        wheel.performScrollToNode(hasTestTag("rest-duration-60"))
        compose.onNodeWithTag("rest-duration-60").performClick().assertIsSelected()
        save.assertIsDisplayed().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf(60), saved) }
        cancel.assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(dismissed) }
    }

    @Test fun historyExerciseOpensFromCardPaddingAndExposesOneDestinationAction() {
        lateinit var model: HistoryViewModel
        val opened = mutableListOf<Long>()
        compose.runOnUiThread {
            model = HistoryViewModel(HistoryFixture())
            model.refresh()
            model.openWorkout(101L)
        }
        compose.setContent { HevyGroovyTheme {
            HistoryScreen(model, onOpenExercise = { id, _ -> opened.add(id) }, onStartWorkout = {})
        } }

        val card = compose.onNodeWithTag("open-history-exercise-201")
        compose.onNodeWithTag("history-detail").performScrollToNode(hasTestTag("open-history-exercise-201"))
        card.assertHasClickAction().assertIsDisplayed()
        // Touch the bottom padding, away from the title that used to be the only link.
        card.performTouchInput { click(Offset(width / 2f, height - 8f)) }
        compose.runOnIdle { assertEquals(listOf(7L), opened) }
        // The same destination also has a semantic action for assistive input.
        card.performClick()
        compose.runOnIdle { assertEquals(listOf(7L, 7L), opened) }
        compose.onNodeWithTag("history-delete-dialog").assertDoesNotExist()
    }

    @Test fun narrowProfileWithLargeTextKeepsUnitChoicesAndShortcutCardsUsable() {
        lateinit var model: ProfileViewModel
        val savedUnits = mutableListOf<WeightUnit>()
        var exerciseVisits = 0
        compose.runOnUiThread {
            model = ProfileViewModel(object : ProfileGateway {
                override fun load(callback: ProfileGateway.Callback) = callback.onSuccess(WeightUnit.KG)
                override fun setDefaultUnit(unit: WeightUnit, callback: ProfileGateway.Callback) {
                    savedUnits.add(unit)
                    callback.onSuccess(unit)
                }
            })
            model.refresh()
        }
        compose.setContent { HevyGroovyTheme {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 1.5f)) {
                Surface {
                    Box(Modifier.requiredWidth(320.dp).height(620.dp).testTag("narrow-profile")) {
                        ProfileScreen(model, onHistory = {}, onExercises = { exerciseVisits++ })
                    }
                }
            }
        } }

        val kilograms = compose.onNodeWithTag("profile-unit-KG")
        val pounds = compose.onNodeWithTag("profile-unit-LB")
        kilograms.performScrollTo().assertIsDisplayed().assertIsSelected().assertHeightIsAtLeast(48.dp)
        pounds.performScrollTo().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        val viewport = compose.onNodeWithTag("narrow-profile").getUnclippedBoundsInRoot()
        listOf(kilograms, pounds).forEach { choice ->
            val bounds = choice.getUnclippedBoundsInRoot()
            assertTrue("Unit choice extends outside the narrow screen", bounds.left >= viewport.left && bounds.right <= viewport.right)
        }
        pounds.performClick().assertIsSelected()
        kilograms.assertIsNotSelected()
        compose.runOnIdle { assertEquals(listOf(WeightUnit.LB), savedUnits) }

        val shortcut = compose.onNodeWithText("Exercise library")
        shortcut.performScrollTo().assertHasClickAction().assertIsDisplayed()
        shortcut.performTouchInput { click(Offset(width / 2f, height - 8f)) }
        compose.runOnIdle { assertEquals(1, exerciseVisits) }
    }

    private class HistoryFixture : HistoryGateway {
        private val summary = WorkoutHistory(101L, "Upper body", 1_700_000_000_000L,
            1_700_003_600_000L, Duration.ofHours(1), 1, 0, 0, 0.0)
        private val detail = WorkoutHistoryDetail(summary, null, listOf(
            WorkoutExerciseHistory(201L, 7L, "Bench press", 0, null,
                0, 0, 0.0, 0.0, 0.0, 0.0, false, 0, emptyList())
        ))

        override fun loadPage(offset: Int, callback: HistoryGateway.PageCallback) =
            callback.onSuccess(HistoryGateway.HistoryPage(listOf(summary), false, 1))
        override fun loadDetail(workoutId: Long, callback: HistoryGateway.DetailCallback) = callback.onSuccess(detail)
        override fun deleteWorkout(workoutId: Long, callback: HistoryGateway.DeleteCallback) = error("Unexpected deletion")
    }
}

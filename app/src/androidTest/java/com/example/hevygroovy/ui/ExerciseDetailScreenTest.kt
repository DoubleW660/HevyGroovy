package com.example.hevygroovy.ui

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.hevygroovy.app.RecordGateway
import com.example.hevygroovy.dto.CatalogExercise
import com.example.hevygroovy.dto.ExercisePerformance
import com.example.hevygroovy.entity.SetEntry
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.model.ExerciseModel
import com.example.hevygroovy.ui.theme.HevyGroovyTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

class ExerciseDetailScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun historyIncludesOlderWorkoutsAcrossRoutinesAndInfoKeepsItsScrollPosition() {
        val history = history(40)
        val lastInstruction = "Final guide instruction: return the equipment."
        val guide = ((1..24).map { "$it. A complete exercise instruction with enough detail to read before the next step." } +
            lastInstruction).joinToString("\n\n")
        val model = model(RecordGateway.Snapshot(emptyList(), 48.0, catalog(guide), history))
        compose.setContent { HevyGroovyTheme { ExerciseDetailScreen(model, "Exercise", {}) } }

        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithText("Routine B #40").assertIsDisplayed()
        compose.onNode(hasText("Set 1 · Warm-up") and hasAnyAncestor(hasTestTag("exercise-history-40"))).assertIsDisplayed()
        compose.onNodeWithTag("exercise-history-set-4003").assertDoesNotExist()
        compose.onNodeWithTag("exercise-history-list").performScrollToNode(hasTestTag("exercise-history-1"))
        compose.onNodeWithText("Routine A #1").assertIsDisplayed()

        compose.onNodeWithTag("exercise-tab-info").performClick()
        compose.onNodeWithText("Chest").assertIsDisplayed()
        compose.onNodeWithTag("exercise-info-list").performScrollToNode(hasText(lastInstruction))
        compose.onNodeWithText(lastInstruction).assertIsDisplayed()
        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithText("Routine A #1").assertIsDisplayed()
        compose.onNodeWithTag("exercise-tab-info").performClick()
        compose.onNodeWithText(lastInstruction).assertIsDisplayed()

        compose.onNodeWithTag("exercise-tab-overview").performClick()
        compose.onNodeWithTag("exercise-overview-list").performScrollToNode(hasTestTag("exercise-rep-record-5"))
        compose.onNodeWithTag("exercise-rep-record-5").assertIsDisplayed()
        compose.onNode(hasText("39 kg") and hasAnyAncestor(hasTestTag("exercise-rep-record-5"))).assertIsDisplayed()
        compose.onNodeWithTag("exercise-overview-list").performScrollToNode(hasTestTag("exercise-rep-record-6"))
        compose.onNode(hasText("40 kg") and hasAnyAncestor(hasTestTag("exercise-rep-record-6"))).assertIsDisplayed()
        compose.onNodeWithTag("exercise-rep-record-4").assertDoesNotExist()
    }

    @Test fun chartMetricsUseProfileUnitsKeepSelectionAndReachOutsideTheVisibleWindow() {
        val model = model(RecordGateway.Snapshot(emptyList(), 48.0, catalog("Guide"), history(40)))
        var unit by mutableStateOf(WeightUnit.LB)
        compose.setContent { HevyGroovyTheme { CompositionLocalProvider(LocalWeightUnit provides unit) {
            ExerciseDetailScreen(model, "Exercise", {})
        } } }
        compose.onNodeWithTag("exercise-chart-value").assertTextEquals("${exerciseNumber(40.0.inWeightUnit(WeightUnit.LB))} lb")
        compose.onNodeWithTag("exercise-metric-reps").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("6 reps")
        compose.onNodeWithTag("exercise-chart-previous").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("5 reps")
        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithTag("exercise-tab-overview").performClick()
        compose.onNodeWithTag("exercise-chart-value").assertTextEquals("5 reps")
        compose.onNodeWithTag("exercise-metric-weight").performScrollTo().performClick()
        compose.runOnIdle { unit = WeightUnit.KG }
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("39 kg")
        repeat(38) { compose.onNodeWithTag("exercise-chart-previous").performScrollTo().performClick() }
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("1 kg")
        compose.onNodeWithTag("exercise-chart-previous").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("exercise-metric-1rm").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo()
            .assertTextEquals("${exerciseNumber(1.0 * (1 + 5 / 30.0))} kg")
        compose.onNodeWithTag("exercise-metric-set-volume").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("5 kg × reps")
        compose.onNodeWithTag("exercise-metric-session-volume").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("5 kg × reps")
    }

    @Test fun emptyExerciseCanRetryAndOpenDirectlyToInfoThenGoBack() {
        var calls = 0
        var backCount = 0
        lateinit var model: ExerciseRecordsViewModel
        compose.runOnUiThread {
            model = ExerciseRecordsViewModel(RecordGateway { _, callback ->
                if (++calls == 1) callback.onFailure(IllegalStateException("Read failed"))
                else callback.onSuccess(RecordGateway.Snapshot(emptyList(), 0.0, catalog(""), emptyList()))
            })
            model.refresh(7L)
        }
        compose.setContent { HevyGroovyTheme { ExerciseDetailScreen(model, "Exercise", { backCount++ }, initialTab = 2) } }
        compose.onNodeWithTag("exercise-tab-info").assertIsSelected()
        compose.onNodeWithTag("retry-exercise-records").performClick()
        compose.onNodeWithText("No instructions have been added for this exercise.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exercise-tab-overview").performClick()
        compose.onNodeWithText("Finish a workout with this exercise to see your progress here.").assertIsDisplayed()
        compose.onNodeWithTag("exercise-progress-chart").assertDoesNotExist()
        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithText("No completed workouts yet").assertIsDisplayed()
        compose.onNodeWithTag("exercise-detail-back").performClick()
        compose.runOnIdle { assertEquals(1, backCount); assertEquals(2, calls) }
    }

    @Test fun highRepSessionRemainsInHistoryWithoutPlottingAnUnavailableOneRepMaxAsZero() {
        val performance = ExercisePerformance(1L, 7L, "Exercise bench", "High rep workout", 1L, 2L,
            listOf(set(1L, 1, 50.0, 12, SetType.NORMAL)), null, false)
        val model = model(RecordGateway.Snapshot(emptyList(), 0.0, catalog("Guide"), listOf(performance)))
        compose.setContent { HevyGroovyTheme { ExerciseDetailScreen(model, "Exercise", {}) } }
        compose.onNodeWithTag("exercise-chart-value").assertTextEquals("50 kg")
        compose.onNodeWithTag("exercise-metric-1rm").performScrollTo().performClick()
        compose.onNodeWithText("No estimate available. Complete a working set of 1–10 reps with weight to track estimated 1RM.")
            .assertIsDisplayed()
        compose.onNodeWithTag("exercise-progress-chart").assertDoesNotExist()
        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithText("High rep workout").assertIsDisplayed()
        compose.onNodeWithTag("exercise-history-set-1").assertIsDisplayed()
    }

    @Test fun warmupOnlyAndZeroRepSessionsStayInHistoryWithoutCreatingFalseGraphDrops() {
        val history = listOf(
            ExercisePerformance(3L, 7L, "Exercise bench", "Working workout", 5L, 6L,
                listOf(set(3L, 1, 50.0, 5, SetType.NORMAL)), null, false),
            ExercisePerformance(2L, 7L, "Exercise bench", "Warm-up workout", 3L, 4L,
                listOf(set(2L, 1, 25.0, 5, SetType.WARMUP)), null, false),
            ExercisePerformance(1L, 7L, "Exercise bench", "Zero rep workout", 1L, 2L,
                listOf(set(1L, 1, 30.0, 0, SetType.NORMAL)), null, false),
        )
        val model = model(RecordGateway.Snapshot(emptyList(), 50.0 * (1 + 5 / 30.0), catalog("Guide"), history))
        compose.setContent { HevyGroovyTheme { ExerciseDetailScreen(model, "Exercise", {}) } }
        compose.onNodeWithTag("exercise-chart-value").assertTextEquals("50 kg")
        compose.onNodeWithText("Workout 1 of 1").assertIsDisplayed()
        compose.onNodeWithTag("exercise-chart-previous").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("exercise-metric-reps").performScrollTo().performClick()
        compose.onNodeWithTag("exercise-chart-value").performScrollTo().assertTextEquals("5 reps")
        compose.onNodeWithText("Workout 1 of 1").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("exercise-tab-history").performClick()
        compose.onNodeWithTag("exercise-history-list").performScrollToNode(hasText("Warm-up workout"))
        compose.onNodeWithText("Warm-up workout").assertIsDisplayed()
        compose.onNodeWithTag("exercise-history-list").performScrollToNode(hasText("Zero rep workout"))
        compose.onNodeWithText("Zero rep workout").assertIsDisplayed()
        compose.onNodeWithTag("exercise-history-set-1").assertIsDisplayed()
    }

    private fun model(snapshot: RecordGateway.Snapshot): ExerciseRecordsViewModel {
        lateinit var result: ExerciseRecordsViewModel
        compose.runOnUiThread {
            result = ExerciseRecordsViewModel(RecordGateway { _, callback -> callback.onSuccess(snapshot) })
            result.refresh(7L)
        }
        return result
    }


    private fun catalog(guide: String) = CatalogExercise(ExerciseModel().apply {
        exerciseId = 7L
        title = "Exercise bench"
        description = guide
        primaryMuscleGroup = MuscleGroup.CHEST
        secondaryMuscleGroups = listOf(MuscleGroup.TRICEPS)
    })

    private fun history(count: Int): List<ExercisePerformance> = (1..count).reversed().map { number ->
        val sets = listOf(
            set(number * 100L + 1, 1, number + 50.0, 4, SetType.WARMUP),
            set(number * 100L + 2, 2, number.toDouble(), if (number % 2 == 0) 6 else 5, SetType.NORMAL),
            set(number * 100L + 3, 3, 999.0, 10, SetType.NORMAL).apply { isCompleted = false },
        )
        ExercisePerformance(number.toLong(), 7L, "Exercise bench", "Routine ${if (number % 2 == 0) "B" else "A"} #$number",
            number * 86_400_000L, number * 86_400_000L + 3_600_000L, sets, null, false)
    }

    private fun set(setId: Long, number: Int, kilograms: Double, count: Int, type: SetType) = SetEntry().apply {
        id = setId
        setNumber = number
        weight = kilograms
        unit = WeightUnit.KG
        reps = count
        setType = type
        isCompleted = true
    }
}

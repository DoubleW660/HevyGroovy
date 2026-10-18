package com.example.hevygroovy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.ExercisePerformance
import com.example.hevygroovy.entity.enums.SetType
import kotlin.math.roundToInt

private enum class ExerciseMetric(val label: String, val tag: String) {
    Weight("Heaviest weight", "weight"),
    OneRepMax("Estimated 1RM", "1rm"),
    SetVolume("Best set volume", "set-volume"),
    SessionVolume("Session volume", "session-volume"),
    Reps("Total reps", "reps"),
}

/** A bounded plot window keeps long exercise histories legible; point controls reach every session. */
@Composable
internal fun ExerciseProgressChart(exerciseId: Long, history: List<ExercisePerformance>) {
    var metric by rememberSaveable(exerciseId) { mutableStateOf(ExerciseMetric.Weight) }
    var selectedWorkout by rememberSaveable(exerciseId) { mutableStateOf<Long?>(null) }
    val sessions = remember(history, metric) {
        history.filter { session ->
            session.sets.any { it.isCompleted && it.reps > 0 && it.setType != null && it.setType != SetType.WARMUP } &&
                (metric != ExerciseMetric.OneRepMax || session.estimatedOneRepMaxKg > 0)
        }
            .sortedWith(compareBy<ExercisePerformance> { it.endedAtEpochMillis }.thenBy { it.workoutId })
    }
    val selected = sessions.indexOfFirst { it.workoutId == selectedWorkout }.takeIf { it >= 0 } ?: sessions.lastIndex
    val unit = LocalWeightUnit.current
    val values = remember(sessions, metric, unit) { sessions.map {
        when (metric) {
            ExerciseMetric.Weight -> it.heaviestWeightKg.inWeightUnit(unit)
            ExerciseMetric.OneRepMax -> it.estimatedOneRepMaxKg.inWeightUnit(unit)
            ExerciseMetric.SetVolume -> it.bestSetVolumeKg.inWeightUnit(unit)
            ExerciseMetric.SessionVolume -> it.sessionVolumeKg.inWeightUnit(unit)
            ExerciseMetric.Reps -> it.totalReps.toDouble()
        }
    } }
    val suffix = when (metric) {
        ExerciseMetric.Reps -> "reps"
        ExerciseMetric.SetVolume, ExerciseMetric.SessionVolume -> "${unit.symbol} × reps"
        else -> unit.symbol
    }
    val ink = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.surface
    Card(Modifier.fillMaxWidth().testTag("exercise-progress")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Progress", style = MaterialTheme.typography.titleLarge)
            Text("Completed workouts across all routines", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExerciseMetric.entries.forEach { option ->
                    FilterChip(metric == option, onClick = { metric = option }, label = { Text(option.label) },
                        modifier = Modifier.testTag("exercise-metric-${option.tag}"))
                }
            }
            Text(metric.label, style = MaterialTheme.typography.labelLarge)
            if (sessions.isEmpty()) {
                Text(if (metric == ExerciseMetric.OneRepMax && history.isNotEmpty())
                    "No estimate available. Complete a working set of 1–10 reps with weight to track estimated 1RM."
                    else if (history.isNotEmpty()) "Complete a working set and finish your workout to see progress here."
                    else "Finish a workout with this exercise to see your progress here.", Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                val selectedSession = sessions[selected]
                Text("${exerciseNumber(values[selected])} $suffix", style = MaterialTheme.typography.headlineSmall,
                    color = ink, modifier = Modifier.testTag("exercise-chart-value"))
                Text("${exerciseDate(selectedSession.endedAtEpochMillis)} · ${selectedSession.workoutName}",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("exercise-chart-session"))
                // Keep at most 30 points on screen, centering earlier selections when possible.
                val end = (selected + 16).coerceAtMost(sessions.size).coerceAtLeast(minOf(30, sessions.size))
                val start = (end - 30).coerceAtLeast(0)
                val plotValues = values.subList(start, end)
                val plotSessions = sessions.subList(start, end)
                val selectedPlot = selected - start
                val maximum = (plotValues.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxHeight().widthIn(min = 30.dp, max = 80.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Text(exerciseNumber(maximum), style = MaterialTheme.typography.labelSmall)
                        Text(exerciseNumber(maximum / 2), style = MaterialTheme.typography.labelSmall)
                        Text("0", style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight().testTag("exercise-progress-chart")
                        .semantics {
                            contentDescription = "${metric.label}: ${exerciseNumber(values[selected])} $suffix, " +
                                "${exerciseDate(selectedSession.endedAtEpochMillis)}, ${selectedSession.workoutName}. " +
                                "Workout ${selected + 1} of ${sessions.size}."
                            customActions = buildList {
                                if (selected > 0) add(CustomAccessibilityAction("Previous workout") {
                                    selectedWorkout = sessions[selected - 1].workoutId; true
                                })
                                if (selected < sessions.lastIndex) add(CustomAccessibilityAction("Next workout") {
                                    selectedWorkout = sessions[selected + 1].workoutId; true
                                })
                            }
                        }
                        .pointerInput(plotSessions.map { it.workoutId }) {
                            detectTapGestures { point ->
                                val inset = 8.dp.toPx()
                                val fraction = ((point.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                selectedWorkout = plotSessions[(fraction * plotSessions.lastIndex).roundToInt()].workoutId
                            }
                        }) {
                        val inset = 8.dp.toPx()
                        val plotWidth = (size.width - 2 * inset).coerceAtLeast(1f)
                        val plotHeight = (size.height - 2 * inset).coerceAtLeast(1f)
                        repeat(3) { tick ->
                            val y = inset + plotHeight * tick / 2
                            drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                        }
                        val points = plotValues.mapIndexed { index, value ->
                            Offset(inset + if (plotValues.size == 1) plotWidth / 2 else plotWidth * index / plotValues.lastIndex,
                                inset + plotHeight * (1 - value / maximum).toFloat())
                        }
                        if (points.size > 1) {
                            val line = Path().apply {
                                moveTo(points.first().x, points.first().y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            val area = Path().apply {
                                addPath(line); lineTo(points.last().x, size.height - inset)
                                lineTo(points.first().x, size.height - inset); close()
                            }
                            drawPath(area, Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f), ink.copy(alpha = 0.01f))))
                            drawPath(line, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        drawLine(grid, Offset(points[selectedPlot].x, inset),
                            Offset(points[selectedPlot].x, size.height - inset), 1.dp.toPx())
                        points.forEachIndexed { index, point ->
                            drawCircle(background, if (index == selectedPlot) 6.dp.toPx() else 4.dp.toPx(), point)
                            drawCircle(ink, if (index == selectedPlot) 4.dp.toPx() else 2.dp.toPx(), point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(exerciseDate(plotSessions.first().endedAtEpochMillis), style = MaterialTheme.typography.labelSmall)
                    if (plotSessions.size > 1) Text(exerciseDate(plotSessions.last().endedAtEpochMillis),
                        style = MaterialTheme.typography.labelSmall)
                }
                Text(if (sessions.size > 30) "Showing workouts ${start + 1}–$end of ${sessions.size} · selected ${selected + 1}"
                    else "Workout ${selected + 1} of ${sessions.size}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedWorkout = sessions[selected - 1].workoutId }, enabled = selected > 0,
                        modifier = Modifier.testTag("exercise-chart-previous")) { Text("Previous") }
                    TextButton(onClick = { selectedWorkout = sessions[selected + 1].workoutId }, enabled = selected < sessions.lastIndex,
                        modifier = Modifier.testTag("exercise-chart-next")) { Text("Next") }
                }
            }
            Text(if (metric == ExerciseMetric.OneRepMax) "Epley estimate from working sets of 1–10 reps. Sessions without an eligible estimate are omitted."
                else "Working sets only. Workouts without working sets are omitted. Volume counts both sides for unilateral exercises.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

package com.example.hevygroovy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import com.example.hevygroovy.dto.WorkoutHistory
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import kotlin.math.roundToInt

private enum class RoutineMetric(val label: String, val tag: String) {
    Volume("Volume", "volume"), Reps("Reps", "reps"), Duration("Duration", "duration")
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun RoutineProgressChart(templateId: Long, history: List<WorkoutHistory>) {
    var metric by rememberSaveable(templateId) { mutableStateOf(RoutineMetric.Volume) }
    var selectedWorkout by rememberSaveable(templateId) { mutableStateOf<Long?>(null) }
    val sessions = remember(history) { history.sortedWith(compareBy<WorkoutHistory> { it.endedAtEpochMillis }.thenBy { it.workoutId }) }
    val selected = sessions.indexOfFirst { it.workoutId == selectedWorkout }.takeIf { it >= 0 } ?: sessions.lastIndex
    val unit = LocalWeightUnit.current
    val values = sessions.map { when (metric) {
        RoutineMetric.Volume -> it.totalVolume.inWeightUnit(unit)
        RoutineMetric.Reps -> it.totalReps.toDouble()
        RoutineMetric.Duration -> it.durationSeconds / 60.0
    } }
    val suffix = when (metric) { RoutineMetric.Volume -> unit.symbol; RoutineMetric.Reps -> "reps"; RoutineMetric.Duration -> "min" }
    val label = "Total " + metric.label.lowercase() + if (metric == RoutineMetric.Reps) "" else " (" + suffix + ")"
    val ink = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val background = MaterialTheme.colorScheme.surface
    Card(Modifier.fillMaxWidth().testTag("routine-progress")) {
        Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
            Text("Routine progress", style = MaterialTheme.typography.titleLarge)
            Text("Last 12 completed workouts", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                RoutineMetric.entries.forEach { option ->
                    FilterChip(metric == option, onClick = { metric = option }, label = { Text(option.label) },
                        modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("routine-metric-" + option.tag))
                }
            }
            Text(label, style = MaterialTheme.typography.labelLarge)
            if (sessions.isEmpty()) {
                Text("Finish this routine to see your progress here.", Modifier.padding(vertical = 24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(chartNumber(values[selected]) + " " + suffix, style = MaterialTheme.typography.headlineSmall,
                        color = ink, modifier = Modifier.testTag("routine-chart-value"))
                    Text(chartDate(sessions[selected].endedAtEpochMillis!!), style = MaterialTheme.typography.bodyMedium)
                }
                val maximum = (values.maxOrNull() ?: 0.0).coerceAtLeast(1.0)
                Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.fillMaxHeight().widthIn(min = 30.dp, max = 66.dp),
                        verticalArrangement = Arrangement.SpaceBetween) {
                        Text(chartNumber(maximum), style = MaterialTheme.typography.labelSmall)
                        Text(chartNumber(maximum / 2), style = MaterialTheme.typography.labelSmall)
                        Text("0", style = MaterialTheme.typography.labelSmall)
                    }
                    Canvas(Modifier.weight(1f).fillMaxHeight().testTag("routine-progress-chart")
                        .semantics {
                            contentDescription = label + ": " + chartNumber(values[selected]) + " " + suffix + ", " +
                                chartDate(sessions[selected].endedAtEpochMillis!!) + ". Point " + (selected + 1) + " of " + sessions.size
                            customActions = buildList {
                                if (selected > 0) add(CustomAccessibilityAction("Previous workout") {
                                    selectedWorkout = sessions[selected - 1].workoutId; true
                                })
                                if (selected < sessions.lastIndex) add(CustomAccessibilityAction("Next workout") {
                                    selectedWorkout = sessions[selected + 1].workoutId; true
                                })
                            }
                        }
                        .pointerInput(sessions.map { it.workoutId }) {
                            detectTapGestures { point ->
                                val inset = 8.dp.toPx()
                                val fraction = ((point.x - inset) / (size.width - 2 * inset).coerceAtLeast(1f)).coerceIn(0f, 1f)
                                selectedWorkout = sessions[(fraction * sessions.lastIndex).roundToInt()].workoutId
                            }
                        }) {
                        val inset = 8.dp.toPx()
                        val plotWidth = (size.width - 2 * inset).coerceAtLeast(1f)
                        val plotHeight = (size.height - 2 * inset).coerceAtLeast(1f)
                        repeat(3) { tick ->
                            val y = inset + plotHeight * tick / 2
                            drawLine(grid, Offset(inset, y), Offset(size.width - inset, y), 1.dp.toPx())
                        }
                        val points = values.mapIndexed { index, value ->
                            Offset(inset + if (values.size == 1) plotWidth / 2 else plotWidth * index / values.lastIndex,
                                inset + plotHeight * (1 - value / maximum).toFloat())
                        }
                        if (points.size > 1) {
                            val line = Path().apply {
                                moveTo(points[0].x, points[0].y)
                                points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            val area = Path().apply {
                                addPath(line); lineTo(points.last().x, size.height - inset)
                                lineTo(points.first().x, size.height - inset); close()
                            }
                            drawPath(area, Brush.verticalGradient(listOf(ink.copy(alpha = 0.22f), ink.copy(alpha = 0.01f))))
                            drawPath(line, ink, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        drawLine(grid, Offset(points[selected].x, inset), Offset(points[selected].x, size.height - inset), 1.dp.toPx())
                        points.forEachIndexed { index, point ->
                            drawCircle(background, if (index == selected) 6.dp.toPx() else 4.dp.toPx(), point)
                            drawCircle(ink, if (index == selected) 4.dp.toPx() else 2.dp.toPx(), point)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(chartDate(sessions.first().endedAtEpochMillis!!), style = MaterialTheme.typography.labelSmall)
                    if (sessions.size > 1) Text(chartDate(sessions.last().endedAtEpochMillis!!), style = MaterialTheme.typography.labelSmall)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { selectedWorkout = sessions[selected - 1].workoutId }, enabled = selected > 0,
                        modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("routine-chart-previous")) { Text("Previous") }
                    TextButton(onClick = { selectedWorkout = sessions[selected + 1].workoutId }, enabled = selected < sessions.lastIndex,
                        modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("routine-chart-next")) { Text("Next") }
                }
            }
        }
    }
}

private fun chartNumber(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 1 }.format(value)
private fun chartDate(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))

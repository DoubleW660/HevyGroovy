package com.example.hevygroovy.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.entity.enums.RecordType
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** A shared celebration for live candidates and records earned in a finished workout. */
@Composable
fun PersonalRecordSection(
    records: List<PersonalRecord>,
    modifier: Modifier = Modifier,
    provisional: Boolean = false,
    title: String = "Personal records",
    showExerciseName: Boolean = true,
    showAchievedDate: Boolean = false,
) {
    if (records.isEmpty()) return
    Card(
        modifier = modifier.fillMaxWidth().testTag("personal-record-section"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f)),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RecordTrophy()
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary)
            }
            records.groupBy { it.exerciseId }.values.forEach { exerciseRecords ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (showExerciseName) {
                        Text(exerciseRecords.first().exerciseName, style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold)
                    }
                    exerciseRecords.forEach { record -> PersonalRecordRow(record, showAchievedDate) }
                }
            }
            if (provisional) {
                Text("Finish workout to save these records.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary)
            }
            Text("Completed working sets count. Warm-ups are excluded. Volume is weight × reps, converted to kg.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PersonalRecordRow(record: PersonalRecord, showAchievedDate: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("personal-record-${record.exerciseId}-${record.type.name}"),
        verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(record.type.recordLabel(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(record.recordValue(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        record.recordContext()?.let { context ->
            Text(context, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (showAchievedDate) {
            Text("Achieved ${recordDate(record.achievedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun PersonalRecordBadge(records: List<PersonalRecord>, modifier: Modifier = Modifier) {
    if (records.isEmpty()) return
    val count = records.size
    Surface(modifier.testTag("exercise-record-badge-${records.first().workoutExerciseId}")
        .semantics { contentDescription = records.joinToString(prefix = "Personal records: ") { it.type.recordLabel() } },
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            RecordTrophy(Modifier.size(16.dp))
            Text("$count personal record${if (count == 1) "" else "s"}",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
internal fun ExerciseRecordsDialog(model: ExerciseRecordsViewModel, exerciseName: String) {
    val state = model.state
    AlertDialog(onDismissRequest = model::close, modifier = Modifier.testTag("exercise-records-dialog"),
        title = { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exerciseName)
            Text("All-time records", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary)
        } },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading records…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                state.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::retry, enabled = !state.loading,
                        modifier = Modifier.testTag("retry-exercise-records")) { Text("Retry") }
                }
                if (state.records.isNotEmpty()) {
                    PersonalRecordSection(state.records, showExerciseName = false, showAchievedDate = true)
                } else if (state.loaded && !state.loading && state.error == null) {
                    Text("No records yet", style = MaterialTheme.typography.titleMedium)
                    Text("Complete a working set and finish your workout to start tracking records for this exercise.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = { TextButton(onClick = model::close,
            modifier = Modifier.testTag("close-exercise-records")) { Text("Close") } },
    )
}

@Composable
private fun RecordTrophy(modifier: Modifier = Modifier.size(22.dp)) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        scale(size.width / 24f, size.height / 24f, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            drawPath(Path().apply {
                moveTo(7f, 3f); lineTo(17f, 3f); lineTo(17f, 9f)
                cubicTo(17f, 16f, 7f, 16f, 7f, 9f); close()
            }, color, style = stroke)
            drawPath(Path().apply {
                moveTo(7f, 5f); lineTo(3f, 5f); lineTo(3f, 8f); quadraticTo(3f, 12f, 8f, 12f)
                moveTo(17f, 5f); lineTo(21f, 5f); lineTo(21f, 8f); quadraticTo(21f, 12f, 16f, 12f)
            }, color, style = stroke)
            drawLine(color, Offset(12f, 15f), Offset(12f, 20f), 1.8f, StrokeCap.Round)
            drawLine(color, Offset(7f, 21f), Offset(17f, 21f), 1.8f, StrokeCap.Round)
        }
    }
}

internal fun RecordType.recordLabel(): String = when (this) {
    RecordType.HEAVIEST_WEIGHT -> "Heaviest weight"
    RecordType.MOST_REPS -> "Most reps in a set"
    RecordType.BEST_SET_VOLUME -> "Best set volume"
    RecordType.WORKOUT_VOLUME -> "Most volume in a workout"
}

internal fun PersonalRecord.recordValue(): String = when (type) {
    RecordType.HEAVIEST_WEIGHT -> "${recordNumber(weight)} ${unit.name.lowercase()}"
    RecordType.MOST_REPS -> "$reps reps"
    RecordType.BEST_SET_VOLUME, RecordType.WORKOUT_VOLUME -> "${recordNumber(value)} kg × reps"
}

internal fun PersonalRecord.recordContext(): String? = when (type) {
    RecordType.HEAVIEST_WEIGHT -> "$reps reps"
    RecordType.MOST_REPS -> "At ${recordNumber(weight)} ${unit.name.lowercase()}"
    RecordType.BEST_SET_VOLUME -> "${recordNumber(weight)} ${unit.name.lowercase()} × $reps reps"
    RecordType.WORKOUT_VOLUME -> "$reps total reps in this exercise"
}

private fun recordNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 2
}.format(value)

private fun recordDate(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("MMM d, yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.hevygroovy.dto.ActiveSetEntry
import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.dto.WorkoutExerciseHistory
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.WorkoutHistoryDetail
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun HistoryScreen(model: HistoryViewModel, onStartWorkout: () -> Unit) {
    val state = model.state
    // Keep the list position while a detail is open, including when returning from an older page.
    val listState = rememberLazyListState()
    BackHandler(enabled = state.selectedWorkoutId != null, onBack = model::closeDetail)

    if (state.selectedWorkoutId != null) {
        key(state.selectedWorkoutId) { HistoryDetailScreen(model) }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("history-list"),
        state = listState,
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("YOUR TRAINING LOG", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.6.sp)
                    Text("History", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("Every session, saved.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = model::refresh, enabled = !state.loading && !state.loadingMore) { Text("Refresh") }
            }
        }
        if (state.loading) {
            item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.items.isEmpty()) {
                    Text("Loading your workouts…", Modifier.padding(top = 12.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        state.error?.let { message ->
            item(key = "error") { HistoryErrorCard(message, !state.loading, model::refresh) }
        }
        if (state.loaded && state.items.isEmpty() && !state.loading && state.error == null) {
            item(key = "empty") {
                Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("No workouts yet", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text("Finish a workout to see your exercises, sets, and progress here.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onStartWorkout) { Text("Start a workout") }
                    }
                }
            }
        }
        items(state.items, key = { "workout-${it.workoutId}" }) { workout ->
            HistoryWorkoutCard(workout, onClick = { model.openWorkout(workout.workoutId) })
        }
        if (state.items.isNotEmpty()) {
            item(key = "pagination") {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.moreError?.let { HistoryErrorCard(it, !state.loadingMore && !state.loading, model::loadMore) }
                    if (state.loadingMore) {
                        CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        Text("Loading older workouts…", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else if (state.hasMore && state.moreError == null) {
                        OutlinedButton(onClick = model::loadMore, enabled = !state.loading,
                            modifier = Modifier.fillMaxWidth().testTag("history-load-more")) {
                            Text("Load older workouts")
                        }
                    } else if (!state.hasMore) {
                        Text("You’ve reached your first workout.", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryWorkoutCard(workout: WorkoutHistory, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().testTag("history-card-${workout.workoutId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(historyDate(workout.startedAtEpochMillis), modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("›", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
            }
            Text(workout.nameSnapshot?.takeIf { it.isNotBlank() } ?: "Workout",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HistoryMetric("Duration", historyDuration(workout.durationSeconds), Modifier.weight(1f))
                HistoryMetric("Volume (kg)", historyNumber(workout.totalVolume), Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("${historyCount(workout.totalExercises, "exercise")}  ·  ${historyCount(workout.totalSets, "completed set")}",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryDetailScreen(model: HistoryViewModel) {
    val state = model.state
    val detail = state.detail
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("history-detail"),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "back") {
            TextButton(onClick = model::closeDetail, contentPadding = PaddingValues(start = 0.dp, end = 16.dp),
                modifier = Modifier.testTag("history-back")) {
                Text("‹", fontSize = 26.sp)
                Spacer(Modifier.width(8.dp))
                Text("History")
            }
        }
        if (state.detailLoading) {
            item(key = "loading") {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                if (detail == null) Text("Loading workout…", Modifier.padding(top = 12.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        state.detailError?.let { message ->
            item(key = "error") { HistoryErrorCard(message, !state.detailLoading, model::retryDetail) }
        }
        if (detail != null) {
            item(key = "title") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("COMPLETED WORKOUT", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.5.sp)
                    Text(detail.nameSnapshot?.takeIf { it.isNotBlank() } ?: "Workout",
                        style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Text("${historyDate(detail.startedAtEpochMillis)} · ${historyTime(detail.startedAtEpochMillis)}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item(key = "totals") { HistoryDetailTotals(detail) }
            if (detail.personalRecords.isNotEmpty()) {
                item(key = "records") {
                    Box(Modifier.testTag("history-personal-records")) {
                        PersonalRecordSection(detail.personalRecords, title = "Records set in this workout")
                    }
                }
            }
            detail.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                item(key = "notes") {
                    Card(Modifier.fillMaxWidth().testTag("history-detail-notes"), shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Workout notes", style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary)
                            Text(notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
            item(key = "exercises-heading") {
                Text("Exercises", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            if (detail.exercises.isEmpty()) {
                item(key = "no-exercises") {
                    Text("No exercises recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(detail.exercises, key = { "exercise-${it.workoutExerciseId}" }) { exercise ->
                HistoryExerciseCard(exercise, detail.personalRecords.filter { it.workoutExerciseId == exercise.workoutExerciseId })
            }
        }
    }
}

@Composable
private fun HistoryDetailTotals(detail: WorkoutHistoryDetail) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HistoryMetric("Duration", historyDuration(detail.durationSeconds), Modifier.weight(1f))
                HistoryMetric("Volume (kg)", historyNumber(detail.totalVolume),
                    Modifier.weight(1f).testTag("history-total-volume"))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                HistoryMetric("Completed sets", detail.totalSets.toString(), Modifier.weight(1f))
                HistoryMetric("Total reps", detail.totalReps.toString(), Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("Totals use completed sets. Volume is converted to kg × reps; sets keep their original units.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HistoryMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HistoryExerciseCard(exercise: WorkoutExerciseHistory, records: List<PersonalRecord>) {
    Card(Modifier.fillMaxWidth().testTag("history-exercise-${exercise.workoutExerciseId}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            PersonalRecordBadge(records)
            Text("${historyCount(exercise.totalSets, "completed set")} · ${historyNumber(exercise.totalVolume)} kg volume",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            exercise.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(notes, style = MaterialTheme.typography.bodyMedium)
            }
            if (exercise.sets.isEmpty()) {
                Text("No sets recorded.", color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("SET", Modifier.width(36.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("WEIGHT", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("REPS", Modifier.width(42.dp), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(76.dp))
                }
                exercise.sets.forEach { set ->
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    key(set.id) { HistorySetRow(set) }
                }
            }
        }
    }
}

@Composable
private fun HistorySetRow(set: ActiveSetEntry) {
    val unit = if (set.unit == WeightUnit.LB) "lb" else "kg"
    val type = when (set.setType) {
        SetType.WARMUP -> "Warm-up"
        SetType.NORMAL -> "Normal"
        SetType.FAILURE -> "Failure"
        SetType.DROP -> "Drop"
        else -> "Normal"
    }
    val extras = buildList {
        add(type)
        set.rpe?.let { add("RPE ${historyPreciseNumber(it.toString().toDouble())}") }
        set.restSeconds?.let { add("Rest ${historyDuration(it.toLong(), includeSeconds = true)}") }
    }.joinToString(" · ")
    val status = if (set.isCompleted) "Completed" else "Unfinished"
    val description = "Set ${set.setNumber}, ${historyPreciseNumber(set.weight)} $unit, ${set.reps} reps, $status. $extras"
    Column(Modifier.fillMaxWidth().testTag("history-set-${set.id}").clearAndSetSemantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(set.setNumber.toString(), Modifier.width(36.dp), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${historyPreciseNumber(set.weight)} $unit", Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(set.reps.toString(), Modifier.width(42.dp), style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium)
            Text(status, Modifier.width(76.dp), style = MaterialTheme.typography.labelSmall,
                color = if (set.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(extras, Modifier.padding(start = 36.dp), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HistoryErrorCard(message: String, enabled: Boolean, onRetry: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry, enabled = enabled) { Text("Retry") }
        }
    }
}

private fun historyCount(count: Int, name: String): String = "$count $name${if (count == 1) "" else "s"}"

private fun historyNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 1
}.format(value)

private fun historyPreciseNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 340
    isGroupingUsed = false
}.format(value)

private fun historyDate(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("EEE, MMM d, yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun historyTime(epochMillis: Long): String = DateTimeFormatter
    .ofPattern("h:mm a", Locale.getDefault()).withZone(ZoneId.systemDefault())
    .format(Instant.ofEpochMilli(epochMillis))

private fun historyDuration(seconds: Long, includeSeconds: Boolean = false): String {
    val safe = seconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val remainder = safe % 60
    return when {
        hours > 0 -> "${hours}h ${minutes}m"
        includeSeconds && minutes > 0 && remainder > 0 -> "${minutes}m ${remainder}s"
        minutes > 0 -> "${minutes}m"
        includeSeconds -> "${remainder}s"
        safe > 0 -> "<1 min"
        else -> "0 min"
    }
}

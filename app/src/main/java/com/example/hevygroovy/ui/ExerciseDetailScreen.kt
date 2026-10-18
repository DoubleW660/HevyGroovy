package com.example.hevygroovy.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.ActiveSetEntry
import com.example.hevygroovy.dto.CatalogExercise
import com.example.hevygroovy.dto.ExercisePerformance
import com.example.hevygroovy.dto.ExerciseRepRecord
import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.entity.enums.RecordType
import com.example.hevygroovy.entity.enums.SetType
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

/** One exercise page, whether opened from the library, a routine, or a workout. */
@Composable
fun ExerciseDetailScreen(
    model: ExerciseRecordsViewModel,
    exerciseName: String,
    onBack: () -> Unit,
    initialTab: Int = 0,
    library: LibraryViewModel? = null,
) {
    val state = model.state
    val exerciseId = state.selectedExerciseId
    var selectedTab by rememberSaveable(exerciseId, initialTab) { mutableIntStateOf(initialTab.coerceIn(0, 2)) }
    val tabStates = rememberSaveableStateHolder()
    var editing by rememberSaveable(exerciseId) { mutableStateOf(false) }
    var saving by rememberSaveable(exerciseId) { mutableStateOf(false) }
    LaunchedEffect(library?.state?.savedChanges) {
        if (saving) {
            saving = false
            editing = false
            exerciseId?.let(model::refresh)
        }
    }
    BackHandler(onBack = onBack)
    LaunchedEffect(exerciseId, state.loaded) {
        if (exerciseId != null && !state.loaded && !state.loading && state.error == null) model.refresh(exerciseId)
    }
    Scaffold(Modifier.fillMaxSize().testTag("exercise-detail"), topBar = {
        Surface {
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onBack, modifier = Modifier.testTag("exercise-detail-back")) { Text("Back") }
                    Column(Modifier.weight(1f).padding(start = 8.dp, end = 12.dp)) {
                        Text("EXERCISE", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary)
                        Text(state.exercise?.title ?: exerciseName, style = MaterialTheme.typography.titleLarge)
                    }
                }
                TabRow(selectedTabIndex = selectedTab) {
                    listOf("Overview", "History", "Info").forEachIndexed { index, title ->
                        Tab(selected = selectedTab == index, onClick = { selectedTab = index },
                            modifier = Modifier.testTag("exercise-tab-${title.lowercase()}"), text = { Text(title) })
                    }
                }
            }
        }
    }) { padding ->
        if (!state.loaded) {
            // Do not restore a saved list against the temporary loading/error items: measuring
            // a shorter list would clamp its saved position before the exercise has arrived.
            Column(Modifier.fillMaxSize().padding(padding).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (state.loading) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text("Loading exercise…", style = MaterialTheme.typography.bodySmall)
                }
                state.error?.let { error ->
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::retry, enabled = !state.loading,
                        modifier = Modifier.testTag("retry-exercise-records")) { Text("Retry") }
                }
            }
        } else {
            // Capture the tab so an exiting list cannot read the newly selected tab's items.
            val activeTab = selectedTab
            val listTag = when (activeTab) { 1 -> "exercise-history-list"; 2 -> "exercise-info-list"; else -> "exercise-overview-list" }
            key(exerciseId, activeTab) {
                tabStates.SaveableStateProvider("${exerciseId ?: 0L}:$activeTab") {
                    val listState = rememberLazyListState()
                    LazyColumn(Modifier.fillMaxSize().padding(padding).testTag(listTag), state = listState,
                        contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        when (activeTab) {
                            0 -> {
                                item(key = "progress") { ExerciseProgressChart(exerciseId ?: 0L, state.history) }
                                item(key = "personal-records") { ExercisePersonalRecords(state.records, state.estimatedOneRepMaxKg) }
                                exerciseRepRecords(state.repRecords)
                            }
                            1 -> {
                                item(key = "heading") {
                                    Text("Exercise history", style = MaterialTheme.typography.titleLarge)
                                    Text("Every completed workout · all routines", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (state.history.isEmpty()) item(key = "empty") {
                                    Text("No completed workouts yet", style = MaterialTheme.typography.titleMedium)
                                    Text("Complete a set and finish your workout to see it here.",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                items(state.history, key = { "session-${it.workoutId}" }) { ExerciseHistoryCard(it) }
                            }
                            2 -> exerciseInfo(state.exercise, library != null) { editing = true }
                        }
                    }
                }
            }
        }
    }
    if (editing && library != null && state.exercise != null) {
        ExerciseCatalogDialog(state.exercise, library.state.busy || !library.state.loaded, library.state.error,
            onDismiss = { editing = false; saving = false }) { title, description, primary, secondary, unilateral, tracking ->
            saving = true
            library.saveExercise(exerciseId, title, description, primary, secondary, unilateral, tracking)
        }
    }
}

@Composable
private fun ExercisePersonalRecords(records: List<PersonalRecord>, estimatedOneRepMaxKg: Double) {
    val unit = LocalWeightUnit.current
    Card(Modifier.fillMaxWidth().testTag("exercise-personal-records"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.32f))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Personal records", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            if (records.isEmpty() && estimatedOneRepMaxKg <= 0) {
                Text("No records yet", style = MaterialTheme.typography.titleMedium)
                Text("Complete a working set and finish your workout to start tracking records.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            records.forEach { record ->
                Column(Modifier.testTag("personal-record-${record.exerciseId}-${record.type.name}"),
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(record.type.recordLabel(), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val value = when (record.type) {
                        RecordType.HEAVIEST_WEIGHT -> "${exerciseNumber(record.value.inWeightUnit(unit))} ${unit.symbol}"
                        RecordType.MOST_REPS -> "${record.reps} reps"
                        RecordType.BEST_SET_VOLUME, RecordType.WORKOUT_VOLUME ->
                            "${exerciseNumber(record.value.inWeightUnit(unit))} ${unit.symbol} × reps"
                    }
                    Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    if (record.setId != 0L) Text(
                        "${exerciseNumber(record.weight.asKilograms(record.unit).inWeightUnit(unit))} ${unit.symbol} × ${record.reps} reps",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Achieved ${exerciseDate(record.achievedAtEpochMillis)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (estimatedOneRepMaxKg > 0) Column(Modifier.testTag("exercise-estimated-1rm"),
                verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Best estimated 1RM", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${exerciseNumber(estimatedOneRepMaxKg.inWeightUnit(unit))} ${unit.symbol}",
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("Epley estimate from working sets of 1–10 reps.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Completed working sets count. Warm-ups are excluded. Unilateral volume counts both sides.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun LazyListScope.exerciseRepRecords(records: List<ExerciseRepRecord>) {
    item(key = "rep-records-heading") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Set records", style = MaterialTheme.typography.titleLarge)
            Text("Best weight at each rep count, starting at 5 reps. Warm-ups are excluded.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (records.isEmpty()) item(key = "rep-records-empty") {
        Text("Complete a working set of 5 or more reps to earn a set record.",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(records.filter { it.reps >= 5 }.sortedBy { it.reps }, key = { "rep-record-${it.reps}" }) { record ->
        val unit = LocalWeightUnit.current
        Card(Modifier.fillMaxWidth().testTag("exercise-rep-record-${record.reps}")) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                Text("${record.reps} reps", style = MaterialTheme.typography.titleMedium)
                Column(horizontalAlignment = Alignment.End) {
                    Text("${exerciseNumber(record.weightKg.inWeightUnit(unit))} ${unit.symbol}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    Text(exerciseDate(record.achievedAtEpochMillis), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ExerciseHistoryCard(session: ExercisePerformance) {
    val unit = LocalWeightUnit.current
    Card(Modifier.fillMaxWidth().testTag("exercise-history-${session.workoutId}"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(session.workoutName, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            Text(exerciseDateTime(session.endedAtEpochMillis), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${session.sets.size} completed sets · ${session.totalReps} working reps · " +
                "${exerciseNumber(session.sessionVolumeKg.inWeightUnit(unit))} ${unit.symbol} × reps",
                style = MaterialTheme.typography.bodySmall)
            if (session.isUnilateral) Text("Unilateral · reps per side · volume × 2",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            if (session.estimatedOneRepMaxKg > 0) Text(
                "Estimated 1RM: ${exerciseNumber(session.estimatedOneRepMaxKg.inWeightUnit(unit))} ${unit.symbol}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            session.notes?.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            session.sets.forEachIndexed { index, set ->
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                ExercisePerformanceSetRow(set, index + 1)
            }
        }
    }
}

@Composable
private fun ExercisePerformanceSetRow(set: ActiveSetEntry, number: Int) {
    val unit = LocalWeightUnit.current
    val type = when (set.setType) {
        SetType.WARMUP -> "Warm-up"
        SetType.FAILURE -> "Failure"
        SetType.DROP -> "Drop"
        else -> "Normal"
    }
    Column(Modifier.fillMaxWidth().testTag("exercise-history-set-${set.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Set $number · $type", style = MaterialTheme.typography.bodyMedium)
            Text("${exerciseNumber(set.weight.asKilograms(set.unit).inWeightUnit(unit))} ${unit.symbol} × ${set.reps}",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        val extras = buildList {
            addAll(set.measurementDetails())
            set.rpe?.let { add("RPE ${exerciseNumber(it.toDouble())}") }
            set.restSeconds?.let { add("Rest ${it}s") }
        }
        if (extras.isNotEmpty()) Text(extras.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.exerciseInfo(exercise: CatalogExercise?, editable: Boolean, onEdit: () -> Unit) {
    item(key = "information") {
        Card(Modifier.fillMaxWidth().testTag("exercise-information")) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Exercise information", style = MaterialTheme.typography.titleLarge)
                if (exercise == null) {
                    Text("Information is unavailable for this exercise.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ExerciseInfoField("Major (primary) muscle", exercise.primaryMuscle?.exerciseMuscleLabel() ?: "Other")
                    ExerciseInfoField("Minor (secondary) muscles", exercise.secondaryMuscles.takeIf { it.isNotEmpty() }
                        ?.joinToString { it.exerciseMuscleLabel() } ?: "None listed")
                    ExerciseInfoField("Movement", if (exercise.isUnilateral) "Unilateral" else "Bilateral")
                    ExerciseInfoField("Tracking", exercise.trackingType.label())
                    if (editable) OutlinedButton(onClick = onEdit,
                        modifier = Modifier.testTag("edit-exercise-information")) { Text("Edit categories & tracking") }
                    Text(if (exercise.isUnilateral) "Enter measurements per side. Weight × rep volume counts both sides."
                        else "Time is entered in seconds and distance in meters.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (exercise.isArchived) Text("Archived exercise · saved workouts and records are retained.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    item(key = "guide-heading") { Text("How to", style = MaterialTheme.typography.titleLarge) }
    val paragraphs = exercise?.description.orEmpty().split(Regex("\\n\\s*\\n")).filter { it.isNotBlank() }
    if (paragraphs.isEmpty()) item(key = "guide-empty") {
        Text("No instructions have been added for this exercise.", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    items(paragraphs.size, key = { "guide-paragraph-$it" }) { index ->
        Text(paragraphs[index], style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ExerciseInfoField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

internal fun exerciseNumber(value: Double): String = NumberFormat.getNumberInstance().apply {
    maximumFractionDigits = 2
}.format(value)

internal fun exerciseDate(time: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(time))
private fun exerciseDateTime(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
private fun Double.asKilograms(unit: WeightUnit): Double = if (unit == WeightUnit.LB) this * 0.45359237 else this
private fun Enum<*>.exerciseMuscleLabel(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

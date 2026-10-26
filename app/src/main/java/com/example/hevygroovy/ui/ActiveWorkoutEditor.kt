package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.TrackingType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ActiveWorkoutHeader(model: WorkoutHomeViewModel, active: ActiveWorkoutDetail, onDiscard: () -> Unit,
    hasUnsavedSets: Boolean = false, showFinish: Boolean = true) {
    val busy = model.state.busy
    var editDetails by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    var savingWorkoutDetails by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    var pickExercise by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    val canFinish = active.exercises.any { exercise -> exercise.sets.any { it.isCompleted } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout in progress", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary)
            Text(active.nameSnapshot ?: "Workout", style = MaterialTheme.typography.headlineSmall)
            active.notes?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { savingWorkoutDetails = false; editDetails = true }, enabled = !busy,
                    contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight)) {
                    Text("Edit workout details")
                }
                TextButton(onClick = onDiscard, enabled = !busy,
                    modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("cancel-workout"),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Discard workout")
                }
            }
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickExercise = true }, enabled = !busy,
                    modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight)) { Text("Add exercise") }
                if (showFinish) FinishWorkoutButton(model, active, hasUnsavedSets)
            }
            if (!canFinish) Text("Complete at least one set to finish.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (hasUnsavedSets) Text("Save or correct your set entries before finishing.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("finish-unsaved-set-warning"))
        }
    }
    if (editDetails) {
        LaunchedEffect(model.state.savedChanges) {
            if (savingWorkoutDetails) { editDetails = false; savingWorkoutDetails = false }
        }
        var name by rememberSaveable { mutableStateOf(active.nameSnapshot ?: "Workout") }
        var notes by rememberSaveable { mutableStateOf(active.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { savingWorkoutDetails = false; editDetails = false }, title = { Text("Workout details") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
                    model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(name, { name = it }, label = { Text("Workout name") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workout-name"))
                    OutlinedTextField(notes, { notes = it }, label = { Text("Workout notes") }, maxLines = 5,
                        modifier = Modifier.fillMaxWidth().testTag("workout-notes"))
                }
            },
            confirmButton = {
                TextButton(onClick = { savingWorkoutDetails = true; model.updateWorkout(name, notes) }, enabled = !busy && name.isNotBlank()) {
                    Text("Save workout")
                }
            },
            dismissButton = { TextButton(onClick = { savingWorkoutDetails = false; editDetails = false }) { Text("Cancel") } },
        )
    }
    if (pickExercise) ExercisePicker(model, onDismiss = { pickExercise = false })
}

@Composable
internal fun FinishWorkoutButton(model: WorkoutHomeViewModel, active: ActiveWorkoutDetail,
    hasUnsavedSets: Boolean) {
    var finish by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    val busy = model.state.busy
    val canFinish = active.exercises.any { exercise -> exercise.sets.any { it.isCompleted } }
    Button(onClick = { finish = true }, modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("finish-workout"),
        enabled = !busy && canFinish && !hasUnsavedSets && model.state.error == null) {
        Text("Finish workout")
    }
    if (finish) {
        AlertDialog(
            onDismissRequest = { finish = false }, title = { Text("Finish this workout?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Completed sets will appear in your history. Incomplete sets won't count toward your totals.")
                if (hasUnsavedSets) Text("Save or correct your set entries before finishing.",
                    color = MaterialTheme.colorScheme.error)
            } },
            confirmButton = {
                TextButton(onClick = { finish = false; model.finishWorkout() },
                    enabled = !busy && !hasUnsavedSets && canFinish && model.state.error == null) { Text("Finish") }
            },
            dismissButton = { TextButton(onClick = { finish = false }) { Text("Keep training") } },
        )
    }
}

@Composable
private fun ExercisePicker(model: WorkoutHomeViewModel, onDismiss: () -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var creating by rememberSaveable { mutableStateOf(false) }
    var title by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf(false) }
    val busy = model.state.busy
    LaunchedEffect(model.state.savedChanges) {
        if (submitted) {
            submitted = false
            if (creating && !model.state.refreshRequired) { search = title.trim(); creating = false } else onDismiss()
        }
    }
    if (creating) {
        ExerciseCatalogDialog(null, busy, model.state.error, onDismiss = { creating = false }, initialTitle = title) {
                name, description, primary, secondary, unilateral, tracking ->
            title = name
            submitted = true
            model.createExercise(CreateExerciseRequest().apply {
                this.title = name; this.description = description; primaryMuscleGroup = primary
                secondaryMuscleGroups = secondary; isUnilateral = unilateral; trackingType = tracking
            })
        }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(search, { search = it }, label = { Text("Search exercises") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    val options = model.state.exercises.filter { it.title.contains(search, ignoreCase = true) }
                    if (options.isEmpty()) Text("No matching exercises. Create one to get started.")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(options, key = { it.id }) { exercise ->
                            TextButton(onClick = { submitted = true; model.addExercise(exercise.id) }, enabled = !busy,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                                modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight)
                                    .testTag("exercise-option-${exercise.id}")) {
                                Text(exercise.title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
                            }
                        }
                    }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
                TextButton(onClick = { title = search; creating = true }, enabled = !busy) { Text("Create exercise") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WorkoutExerciseCard(
    model: WorkoutHomeViewModel,
    row: ActiveWorkoutExercise,
    position: Int,
    count: Int,
    collapsed: Boolean = false,
    reorderModifier: Modifier = Modifier,
    canPersist: () -> Boolean = { true },
    onDraftChanged: (Long, InlineSetDraft) -> Unit = { _, _ -> },
    onOpenExercise: ((Long, String) -> Unit)? = null,
) {
    val displayUnit = LocalWeightUnit.current
    val busy = model.state.busy
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val name = row.exerciseNameSnapshot ?: "Exercise"
    var editDetails by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var editRest by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var savingRest by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var remove by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var editingSetId by rememberSaveable(row.workoutExerciseId) { mutableStateOf<Long?>(null) }
    var savingSetOptions by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var showRecords by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    val records = model.state.activeRecords.filter { it.workoutExerciseId == row.workoutExerciseId }
    val savedRows = rememberSaveableStateHolder()
    Card(Modifier.fillMaxWidth().testTag(if (collapsed) "collapsed-exercise-" + row.workoutExerciseId
        else "workout-exercise-" + row.workoutExerciseId)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val compact = maxWidth < 540.dp || LocalDensity.current.fontScale > 1.2f
            Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(reorderModifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ExerciseDragHandle()
                    Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                        .testTag("open-active-exercise-" + row.workoutExerciseId)
                        .clickable(enabled = onOpenExercise != null, role = Role.Button) {
                            focus.clearFocus(); keyboard?.hide()
                            onOpenExercise?.invoke(row.exerciseId, name)
                        }.padding(vertical = 12.dp))
                    if (collapsed) Text((position + 1).toString(), color = MaterialTheme.colorScheme.primary)
                }
                if (!collapsed) savedRows.SaveableStateProvider(row.workoutExerciseId) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (row.isUnilateral) Text(if (row.trackingType.tracksReps()) "Unilateral · reps per side · volume × 2"
                            else "Unilateral · measurements per side",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        if (records.isNotEmpty()) {
                            TextButton(onClick = { showRecords = true },
                                modifier = Modifier.testTag("active-records-" + row.workoutExerciseId)) {
                                Text("★ " + records.size + " provisional records · View")
                            }
                        }
                        if (!row.notes.isNullOrBlank()) Text(row.notes)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { focus.clearFocus(); keyboard?.hide(); editRest = true }, enabled = !busy,
                                modifier = Modifier.weight(1f).testTag("exercise-rest-" + row.workoutExerciseId)
                                    .semantics { contentDescription = "Rest for $name" }) {
                                Text("Rest: " + restDurationLabel(row.restTimeSeconds))
                            }
                            TextButton(onClick = { editDetails = true }, enabled = !busy) { Text("Edit notes") }
                        }
                        if (row.estimatedOneRepMax > 0) {
                            Text("Estimated 1RM: " + workoutNumber(row.estimatedOneRepMax.inWeightUnit(displayUnit)) +
                                " ${displayUnit.symbol} · completed sets, 1–10 reps",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.testTag("active-estimated-1rm-" + row.workoutExerciseId))
                        }
                        Text(if (row.previousEndedAtEpochMillis != null) "Previous · " + previousDate(row.previousEndedAtEpochMillis)
                            else "No previous sets yet", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (row.trackingType == TrackingType.WEIGHT_REPS && !compact) InlineSetTableHeading(compact)
                        else Text(row.trackingType.label(), style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        row.sets.forEachIndexed { index, set ->
                            key(set.id) {
                                val warmup = set.setType == SetType.WARMUP
                                val ordinal = row.sets.take(index).count { (it.setType == SetType.WARMUP) == warmup }
                                val previous = row.previousSets.filter { (it.setType == SetType.WARMUP) == warmup }.getOrNull(ordinal)
                                val normalNumber = row.sets.take(index + 1).count { it.setType == SetType.NORMAL }.coerceAtLeast(1)
                                InlineSetRow(model, name, set,
                                    tracking = row.trackingType,
                                    compact = compact,
                                    previous = previous, normalSetNumber = normalNumber,
                                    canPersist = canPersist, onDraftChanged = onDraftChanged,
                                    onMore = { savingSetOptions = false; editingSetId = set.id })
                            }
                        }
                        OutlinedButton(onClick = { model.addSet(row.workoutExerciseId) }, enabled = !busy,
                            modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight)
                                .semantics { contentDescription = "Add set to $name" }) { Text("Add set") }
                        TextButton(onClick = { remove = true }, enabled = !busy,
                            modifier = Modifier.align(Alignment.End).heightIn(min = GroovyLayout.ButtonHeight)) {
                            Text("Remove exercise", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
        }
        }
    }
    if (showRecords && records.isNotEmpty()) {
        AlertDialog(onDismissRequest = { showRecords = false }, title = { Text("$name records") },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    PersonalRecordSection(records, provisional = true, showExerciseName = false)
                }
            }, confirmButton = { TextButton(onClick = { showRecords = false }) { Text("Close") } })
    }
    if (remove) {
        AlertDialog(onDismissRequest = { remove = false }, title = { Text("Remove $name?") },
            text = { Text("This removes the exercise and its sets from this workout.") },
            confirmButton = { TextButton(onClick = { remove = false; model.removeExercise(row.workoutExerciseId) },
                enabled = !busy, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } })
    }
    if (editDetails) {
        var savingDetails by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(model.state.savedChanges) {
            if (savingDetails) { editDetails = false; savingDetails = false }
        }
        var notes by rememberSaveable { mutableStateOf(row.notes.orEmpty()) }
        AlertDialog(onDismissRequest = { editDetails = false }, title = { Text("Exercise notes") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(notes, { notes = it }, label = { Text("Exercise notes") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 5)
            } },
            confirmButton = { TextButton(onClick = {
                savingDetails = true; model.updateExercise(row.workoutExerciseId, notes, row.restTimeSeconds)
            },
                enabled = !busy) { Text("Save notes") } },
            dismissButton = { TextButton(onClick = { editDetails = false }) { Text("Cancel") } })
    }
    if (editRest) {
        LaunchedEffect(model.state.savedChanges) {
            if (savingRest) { editRest = false; savingRest = false }
        }
        RestDurationPicker(row.restTimeSeconds, onDismiss = { editRest = false; savingRest = false },
            onSave = { seconds ->
                savingRest = true
                model.updateExercise(row.workoutExerciseId, row.notes.orEmpty(), seconds!!)
            }, enabled = !busy, error = model.state.error)
    }
    row.sets.find { it.id == editingSetId }?.let { set ->
        LaunchedEffect(model.state.savedChanges) {
            if (savingSetOptions) { editingSetId = null; savingSetOptions = false }
        }
        SetOptionsDialog(set, busy, model.state.error, onDismiss = { savingSetOptions = false; editingSetId = null },
            onSave = { request -> savingSetOptions = true; model.updateSet(set.id, request) },
            onDelete = { savingSetOptions = true; model.deleteSet(set.id) })
    }
}

@Composable
private fun InlineSetTableHeading(compact: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("SET", Modifier.width(48.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall)
        if (!compact) Text("PREVIOUS", Modifier.weight(0.95f), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("WEIGHT", Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("REPS", Modifier.weight(0.65f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("✓", Modifier.width(48.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun InlineSetRow(model: WorkoutHomeViewModel, exerciseName: String, set: ActiveSetEntry,
    tracking: TrackingType, compact: Boolean,
    previous: ActiveSetEntry?, normalSetNumber: Int, canPersist: () -> Boolean,
    onDraftChanged: (Long, InlineSetDraft) -> Unit, onMore: () -> Unit) {
    var weight by rememberSaveable(set.id) { mutableStateOf(workoutInputNumber(set.weight)) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var unit by rememberSaveable(set.id) { mutableStateOf(set.unit ?: WeightUnit.LB) }
    var type by rememberSaveable(set.id) { mutableStateOf(set.setType ?: SetType.NORMAL) }
    var duration by rememberSaveable(set.id) { mutableStateOf((set.durationSeconds ?: 0).toString()) }
    var distance by rememberSaveable(set.id) { mutableStateOf(workoutInputNumber(set.distanceMeters ?: 0.0)) }
    var durationDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var distanceDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var weightDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var repsDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var unitDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var typeDirty by rememberSaveable(set.id) { mutableStateOf(false) }
    var typeMenu by remember { mutableStateOf(false) }
    var unitMenu by remember { mutableStateOf(false) }
    var pendingSave by rememberSaveable(set.id) { mutableStateOf(false) }
    var pendingCompleted by rememberSaveable(set.id) { mutableStateOf<Boolean?>(null) }
    var focusWithinRow by remember { mutableStateOf(false) }
    var skipNextBlur by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val busy = model.state.busy
    val weightValue = weight.replace(',', '.').toDoubleOrNull()
    val repsValue = reps.toIntOrNull()
    val validWeight = !tracking.tracksWeight() || (weightValue != null && weightValue.isFinite() && weightValue >= 0)
    val validReps = !tracking.tracksReps() || (repsValue != null && repsValue >= 0)
    val durationValue = duration.toIntOrNull()
    val distanceValue = distance.replace(',', '.').toDoubleOrNull()
    val validDuration = !tracking.tracksDuration() || (durationValue != null && durationValue >= 0)
    val validDistance = !tracking.tracksDistance() || (distanceValue != null && distanceValue.isFinite() && distanceValue >= 0)
    val valid = validWeight && validReps && validDuration && validDistance
    fun currentDraft() = InlineSetDraft(if (tracking.tracksWeight()) weight else workoutInputNumber(set.weight),
        if (tracking.tracksReps()) reps else set.reps.toString(), unit, type,
        if (tracking.tracksDuration()) duration else null, if (tracking.tracksDistance()) distance else null)
    val draft = currentDraft()
    SideEffect { onDraftChanged(set.id, draft) }

    // Keep drafts when a save fails or another set is saved. Adopt external changes only for clean fields.
    LaunchedEffect(set.weight, set.reps, set.unit, set.setType, set.durationSeconds, set.distanceMeters) {
        if (!weightDirty || weight.replace(',', '.').toDoubleOrNull() == set.weight) {
            weight = workoutInputNumber(set.weight); weightDirty = false
        }
        if (!repsDirty || reps.toIntOrNull() == set.reps) { reps = set.reps.toString(); repsDirty = false }
        if (!unitDirty || unit == set.unit) { unit = set.unit ?: WeightUnit.LB; unitDirty = false }
        if (!typeDirty || type == set.setType) { type = set.setType ?: SetType.NORMAL; typeDirty = false }
        if (!durationDirty || duration.toIntOrNull() == set.durationSeconds) {
            duration = (set.durationSeconds ?: 0).toString(); durationDirty = false
        }
        if (!distanceDirty || distance.replace(',', '.').toDoubleOrNull() == set.distanceMeters) {
            distance = workoutInputNumber(set.distanceMeters ?: 0.0); distanceDirty = false
        }
    }

    fun saveIfChanged() {
        if (!valid || !canPersist() || model.state.refreshRequired) return
        if (model.state.busy) {
            // The draft can equal the old saved value while a different value is in flight.
            if (weightDirty || repsDirty || unitDirty || typeDirty || durationDirty || distanceDirty || pendingCompleted != null) pendingSave = true
            return
        }
        val changed = !currentDraft().matches(set) || pendingCompleted != null
        if (!changed) { pendingSave = false; return }
        pendingSave = true
        val completed = pendingCompleted
        pendingSave = false
        pendingCompleted = null
        model.updateSet(set.id, UpdateSetEntryRequest().apply {
            if (tracking.tracksWeight()) setWeight(weightValue!!)
            if (tracking.tracksReps()) setReps(repsValue!!)
            setUnit(unit)
            setSetType(type)
            if (tracking.tracksDuration()) setDurationSeconds(durationValue)
            if (tracking.tracksDistance()) setDistanceMeters(distanceValue)
            if (completed != null) setCompleted(completed)
        })
    }

    // A blur during an earlier save queues the latest valid draft instead of dropping the edit.
    LaunchedEffect(busy, pendingSave) {
        if (!busy && pendingSave) saveIfChanged()
    }

    val typeControl: @Composable () -> Unit = {
            Box(Modifier.width(48.dp)) {
                Box(Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("inline-type-" + set.id)
                    .clickable(enabled = !busy, role = Role.Button) {
                        if (focusWithinRow) skipNextBlur = true
                        focus.clearFocus(); keyboard?.hide(); typeMenu = true
                    }
                    .semantics { contentDescription = "Set type for set " + set.setNumber + " for $exerciseName" },
                    contentAlignment = Alignment.Center) {
                    Text(when (type) { SetType.NORMAL -> normalSetNumber.toString(); SetType.WARMUP -> "W"
                        SetType.DROP -> "D"; SetType.FAILURE -> "F" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (type == SetType.WARMUP) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
                }
                DropdownMenu(typeMenu, onDismissRequest = { typeMenu = false; saveIfChanged() }) {
                    SetType.values().forEach { value ->
                        DropdownMenuItem(text = { Text(if (value == SetType.WARMUP) "Warm-up" else value.label()) },
                            onClick = { type = value; typeDirty = true; typeMenu = false; saveIfChanged() })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("RPE, rest & delete") }, enabled = !busy,
                        modifier = Modifier.testTag("set-editor-" + set.id).semantics {
                            contentDescription = "More options for set " + set.setNumber + " for $exerciseName"
                        }, onClick = { typeMenu = false; saveIfChanged(); onMore() })
                }
            }
    }
    val unitControl: @Composable () -> Unit = {
        Box {
            Text(unit.name.lowercase(), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp).testTag("inline-unit-" + set.id)
                    .clickable(enabled = !busy, role = Role.Button) {
                        if (focusWithinRow) skipNextBlur = true
                        focus.clearFocus(); keyboard?.hide(); unitMenu = true
                    }.wrapContentSize(Alignment.Center)
                    .semantics { contentDescription = "Weight unit for set " + set.setNumber + " for $exerciseName" })
            DropdownMenu(unitMenu, onDismissRequest = { unitMenu = false; saveIfChanged() }) {
                WeightUnit.values().forEach { value ->
                    DropdownMenuItem(text = { Text(value.name) }, onClick = {
                        unit = value; unitDirty = true; unitMenu = false; saveIfChanged()
                    })
                }
            }
        }
    }
    val completeControl: @Composable () -> Unit = {
        Checkbox(checked = set.isCompleted, enabled = !busy && valid,
            onCheckedChange = { checked ->
                if (focusWithinRow) skipNextBlur = true
                pendingCompleted = checked
                saveIfChanged()
                focus.clearFocus()
                keyboard?.hide()
            }, modifier = Modifier.width(48.dp).testTag("inline-complete-" + set.id).semantics {
                contentDescription = "Complete set " + set.setNumber + " for $exerciseName"
            })
    }
    val fields: @Composable RowScope.() -> Unit = {
            if (tracking == TrackingType.WEIGHT_REPS && !compact) Column(Modifier.weight(0.95f).testTag("previous-set-" + set.id)) {
                if (previous == null) Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                    Text(workoutNumber(previous.weight) + " " + previous.unit.name.lowercase() + " × " + previous.reps,
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (tracking.tracksWeight()) Column(Modifier.weight(1f)) {
                if (compact) Text("WEIGHT (${unit.name.lowercase()})", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                InlineNumberField(weight, { weight = it; weightDirty = true }, !validWeight, KeyboardType.Decimal,
                    "Weight for set " + set.setNumber + " for $exerciseName",
                    Modifier.testTag("inline-weight-" + set.id), onDone = {
                        if (focusWithinRow) skipNextBlur = true
                        saveIfChanged(); focus.clearFocus(); keyboard?.hide()
                    }, suffix = if (compact) null else unitControl)
            }
            if (tracking.tracksReps()) Column(Modifier.weight(0.65f)) {
                if (compact) Text("REPS", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                InlineNumberField(reps, { reps = it; repsDirty = true }, !validReps, KeyboardType.Number,
                "Reps for set " + set.setNumber + " for $exerciseName",
                Modifier.testTag("inline-reps-" + set.id),
                onDone = {
                    if (focusWithinRow) skipNextBlur = true
                    saveIfChanged(); focus.clearFocus(); keyboard?.hide()
                })
            }
            if (tracking.tracksDuration()) Column(Modifier.weight(1f)) {
                Text("TIME (seconds)", style = MaterialTheme.typography.labelSmall)
                InlineNumberField(duration, { duration = it; durationDirty = true }, !validDuration, KeyboardType.Number,
                    "Time in seconds for set ${set.setNumber} for $exerciseName",
                    Modifier.testTag("inline-duration-${set.id}"), onDone = {
                        if (focusWithinRow) skipNextBlur = true
                        saveIfChanged(); focus.clearFocus(); keyboard?.hide()
                    })
            }
            if (tracking.tracksDistance()) Column(Modifier.weight(1f)) {
                Text("DISTANCE (meters)", style = MaterialTheme.typography.labelSmall)
                InlineNumberField(distance, { distance = it; distanceDirty = true }, !validDistance, KeyboardType.Decimal,
                    "Distance in meters for set ${set.setNumber} for $exerciseName",
                    Modifier.testTag("inline-distance-${set.id}"), onDone = {
                        if (focusWithinRow) skipNextBlur = true
                        saveIfChanged(); focus.clearFocus(); keyboard?.hide()
                    })
            }
    }
    Column(Modifier.fillMaxWidth().onFocusChanged {
        val wasFocused = focusWithinRow
        focusWithinRow = it.hasFocus
        if (wasFocused && !it.hasFocus) {
            if (skipNextBlur) skipNextBlur = false else saveIfChanged()
        }
    }.focusGroup(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (compact) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Set", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                typeControl()
                Spacer(Modifier.weight(1f))
                if (tracking.tracksWeight()) unitControl()
                completeControl()
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom, content = fields)
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                typeControl()
                fields()
                completeControl()
            }
        }
        if (compact && tracking == TrackingType.WEIGHT_REPS && previous != null) {
            Row(Modifier.testTag("previous-set-" + set.id),
                horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Previous", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(workoutNumber(previous.weight) + " " + previous.unit.name.lowercase() + " × " + previous.reps,
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (tracking != TrackingType.WEIGHT_REPS && previous != null) Text(
            "Previous: " + (previous.measurementDetails() +
                (if (tracking.tracksWeight()) listOf("${workoutNumber(previous.weight)} ${previous.unit.name.lowercase()}") else emptyList()) +
                (if (tracking.tracksReps()) listOf("${previous.reps} reps") else emptyList())).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (!valid) Text("Enter nonnegative values; reps and seconds must be whole numbers.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        else if (!draft.matches(set)) {
            Text(if (busy) "Saving…" else "Unsaved changes · tap ✓ or Done to save",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun InlineNumberField(value: String, onChange: (String) -> Unit, invalid: Boolean,
    keyboardType: KeyboardType, description: String, modifier: Modifier, onDone: () -> Unit,
    suffix: (@Composable () -> Unit)? = null) {
    val shape = MaterialTheme.shapes.small
    var fieldValue by remember { mutableStateOf(TextFieldValue(value)) }
    // The IME can repeat a text update before the parent's new value is recomposed.
    var lastDispatchedText by remember(value) { mutableStateOf(value) }
    var isFocused by remember { mutableStateOf(false) }
    LaunchedEffect(isFocused) {
        if (isFocused) {
            // Apply after the focus tap's cursor placement, once per focus entry.
            withFrameNanos { }
            fieldValue = fieldValue.copy(text = value, selection = TextRange(0, value.length))
        }
    }
    BasicTextField(fieldValue.copy(text = value), onValueChange = {
        fieldValue = it
        if (it.text != lastDispatchedText) {
            lastDispatchedText = it.text
            onChange(it.text)
        }
    }, singleLine = true,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.heightIn(min = 48.dp).background(MaterialTheme.colorScheme.surfaceVariant, shape)
            .border(if (isFocused) 2.dp else 1.dp, when {
                invalid -> MaterialTheme.colorScheme.error
                isFocused -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }, shape)
            .padding(horizontal = 5.dp).onFocusChanged { isFocused = it.isFocused }
            .semantics { contentDescription = description },
        decorationBox = { inner ->
            Row(Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight), horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f).padding(vertical = 12.dp)) { inner() }
                suffix?.invoke()
            }
        })
}

@Composable
private fun SetOptionsDialog(set: ActiveSetEntry, busy: Boolean, error: String?, onDismiss: () -> Unit,
    onSave: (UpdateSetEntryRequest) -> Unit, onDelete: () -> Unit) {
    var rpe by rememberSaveable(set.id) { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var rest by rememberSaveable(set.id) { mutableStateOf<Int?>(set.restSeconds) }
    var editRest by rememberSaveable(set.id) { mutableStateOf(false) }
    var delete by rememberSaveable(set.id) { mutableStateOf(false) }
    val rpeValue = rpe.replace(',', '.').toFloatOrNull()
    val validRpe = rpe.isBlank() || (rpeValue != null && rpeValue.isFinite() && rpeValue in 0f..10f)
    if (delete) {
        AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete set " + set.setNumber + "?") },
            text = { Text("This permanently removes this set from the workout.") },
            confirmButton = { TextButton(onClick = onDelete, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep set") } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Set " + set.setNumber + " options") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            OutlinedTextField(rpe, { rpe = it }, label = { Text("RPE (optional, 0–10)") }, singleLine = true,
                isError = !validRpe, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag("set-rpe-" + set.id))
            OutlinedButton(onClick = { editRest = true }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight).testTag("set-rest-" + set.id)) {
                Text("Rest: " + (rest?.let(::restDurationLabel) ?: "Use exercise rest"))
            }
            TextButton(onClick = { delete = true }, enabled = !busy,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("Delete set") }
        } },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("save-set"), enabled = !busy && validRpe, onClick = {
                onSave(UpdateSetEntryRequest().apply {
                    setRpe(if (rpe.isBlank()) null else rpeValue)
                    setRestSeconds(rest)
                })
            }) { Text("Save set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    if (editRest) RestDurationPicker(rest, onDismiss = { editRest = false },
        onSave = { rest = it; editRest = false }, allowInherit = true, enabled = !busy)
}

private fun workoutInputNumber(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
private fun workoutNumber(value: Double): String = NumberFormat.getNumberInstance().apply { maximumFractionDigits = 2 }.format(value)
private fun previousDate(value: Long): String = DateTimeFormatter.ofPattern("MMM d")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(value))
private fun Enum<*>.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

/** The session compares these saved drafts with current storage, even when a row scrolls offscreen. */
data class InlineSetDraft(val weightText: String, val repsText: String, val unit: WeightUnit, val type: SetType,
    val durationText: String? = null, val distanceText: String? = null) {
    fun matches(set: ActiveSetEntry): Boolean {
        val weight = weightText.replace(',', '.').toDoubleOrNull()
        val reps = repsText.toIntOrNull()
        return weight != null && weight.isFinite() && weight >= 0 && reps != null && reps >= 0 &&
            weight == set.weight && reps == set.reps && unit == set.unit && type == set.setType &&
            (durationText == null || (durationText.toIntOrNull()?.let { it >= 0 && it == (set.durationSeconds ?: 0) } == true)) &&
            (distanceText == null || (distanceText.replace(',', '.').toDoubleOrNull()?.let {
                it.isFinite() && it >= 0 && it == (set.distanceMeters ?: 0.0) } == true))
    }
}

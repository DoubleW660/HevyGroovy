package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit

@Composable
fun ActiveWorkoutHeader(model: WorkoutHomeViewModel, active: ActiveWorkoutDetail, onDiscard: () -> Unit) {
    val busy = model.state.busy
    var editDetails by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    var pickExercise by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    var finish by rememberSaveable(active.workoutId) { mutableStateOf(false) }
    val canFinish = active.exercises.any { exercise -> exercise.sets.any { it.isCompleted } }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Workout in progress", style = MaterialTheme.typography.labelLarge)
            Text(active.nameSnapshot ?: "Workout", style = MaterialTheme.typography.headlineSmall)
            active.notes?.takeIf { it.isNotBlank() }?.let { Text(it) }
            TextButton(onClick = { editDetails = true }, enabled = !busy) { Text("Edit workout details") }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickExercise = true }, enabled = !busy) { Text("Add exercise") }
                Button(onClick = { finish = true }, enabled = !busy && canFinish) { Text("Finish workout") }
            }
            if (!canFinish) Text("Complete at least one set to finish.")
            TextButton(onClick = onDiscard, enabled = !busy) {
                Text("Discard workout", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (editDetails) {
        SaveResultEffect(model.state.savedChanges) { editDetails = false }
        var name by rememberSaveable { mutableStateOf(active.nameSnapshot ?: "Workout") }
        var notes by rememberSaveable { mutableStateOf(active.notes.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editDetails = false }, title = { Text("Workout details") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(name, { name = it }, label = { Text("Workout name") }, singleLine = true,
                        modifier = Modifier.testTag("workout-name"))
                    OutlinedTextField(notes, { notes = it }, label = { Text("Workout notes") }, maxLines = 5,
                        modifier = Modifier.testTag("workout-notes"))
                }
            },
            confirmButton = {
                TextButton(onClick = { model.updateWorkout(name, notes) }, enabled = !busy && name.isNotBlank()) {
                    Text("Save workout")
                }
            },
            dismissButton = { TextButton(onClick = { editDetails = false }) { Text("Cancel") } },
        )
    }
    if (pickExercise) ExercisePicker(model, onDismiss = { pickExercise = false })
    if (finish) {
        AlertDialog(
            onDismissRequest = { finish = false }, title = { Text("Finish this workout?") },
            text = { Text("Completed sets will appear in your history. Incomplete sets won't count toward your totals.") },
            confirmButton = {
                TextButton(onClick = { finish = false; model.finishWorkout() }, enabled = !busy) { Text("Finish") }
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
    var muscle by rememberSaveable { mutableStateOf(MuscleGroup.OTHER) }
    var expanded by remember { mutableStateOf(false) }
    val busy = model.state.busy
    SaveResultEffect(model.state.savedChanges) {
        if (creating && !model.state.refreshRequired) { search = title.trim(); creating = false } else onDismiss()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (creating) "Create exercise" else "Choose exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (creating) {
                    OutlinedTextField(title, { title = it }, label = { Text("Exercise name") },
                        singleLine = true, modifier = Modifier.testTag("exercise-name"))
                    Box {
                        OutlinedButton(onClick = { expanded = true }) { Text("Muscle: ${muscle.label()}") }
                        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                            MuscleGroup.values().forEach { group ->
                                DropdownMenuItem(text = { Text(group.label()) }, onClick = { muscle = group; expanded = false })
                            }
                        }
                    }
                } else {
                    OutlinedTextField(search, { search = it }, label = { Text("Search exercises") }, singleLine = true)
                    val options = model.state.exercises.filter { it.title.contains(search, ignoreCase = true) }
                    if (options.isEmpty()) Text("No matching exercises. Create one to get started.")
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(options, key = { it.id }) { exercise ->
                            TextButton(onClick = { model.addExercise(exercise.id) }, enabled = !busy,
                                modifier = Modifier.fillMaxWidth().testTag("exercise-option-${exercise.id}")) { Text(exercise.title) }
                        }
                    }
                }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            if (creating) {
                TextButton(onClick = {
                    model.createExercise(title, muscle)
                }, enabled = !busy && title.isNotBlank()) { Text("Save exercise") }
            } else {
                TextButton(onClick = { title = search; creating = true }, enabled = !busy) { Text("Create exercise") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun WorkoutExerciseCard(model: WorkoutHomeViewModel, row: ActiveWorkoutExercise, position: Int, count: Int) {
    val busy = model.state.busy
    val name = row.exerciseNameSnapshot ?: "Exercise"
    var editDetails by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var remove by rememberSaveable(row.workoutExerciseId) { mutableStateOf(false) }
    var editingSetId by rememberSaveable(row.workoutExerciseId) { mutableStateOf<Long?>(null) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(name, style = MaterialTheme.typography.titleLarge)
            if (!row.notes.isNullOrBlank()) Text(row.notes)
            Text("Rest: ${row.restTimeSeconds} seconds")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { model.moveExercise(row.workoutExerciseId, position - 1) }, enabled = !busy && position > 0,
                    modifier = Modifier.semantics { contentDescription = "Move $name up" }) { Text("Up") }
                TextButton(onClick = { model.moveExercise(row.workoutExerciseId, position + 1) }, enabled = !busy && position < count - 1,
                    modifier = Modifier.semantics { contentDescription = "Move $name down" }) { Text("Down") }
                TextButton(onClick = { editDetails = true }, enabled = !busy) { Text("Notes & rest") }
            }
            row.sets.forEach { set ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("Set ${set.setNumber} · ${set.setType?.label() ?: "Normal"}")
                        Text("${set.weight} ${set.unit?.name ?: "LB"} × ${set.reps} reps")
                        TextButton(onClick = { editingSetId = set.id }, enabled = !busy,
                            modifier = Modifier.testTag("set-editor-${set.id}").semantics { contentDescription = "Edit set ${set.setNumber} for $name" }) {
                            Text("Edit set ${set.setNumber}")
                        }
                    }
                    Checkbox(checked = set.isCompleted, enabled = !busy,
                        onCheckedChange = { checked ->
                            model.updateSet(set.id, UpdateSetEntryRequest().apply { completed = checked })
                        }, modifier = Modifier.semantics { contentDescription = "Complete set ${set.setNumber} for $name" })
                }
            }
            OutlinedButton(onClick = { model.addSet(row.workoutExerciseId) }, enabled = !busy,
                modifier = Modifier.semantics { contentDescription = "Add set to $name" }) { Text("Add set") }
            TextButton(onClick = { remove = true }, enabled = !busy) {
                Text("Remove exercise", color = MaterialTheme.colorScheme.error)
            }
        }
    }
    if (remove) {
        AlertDialog(
            onDismissRequest = { remove = false }, title = { Text("Remove $name?") },
            text = { Text("This removes the exercise and its sets from this workout.") },
            confirmButton = { TextButton(onClick = { remove = false; model.removeExercise(row.workoutExerciseId) }, enabled = !busy) { Text("Remove") } },
            dismissButton = { TextButton(onClick = { remove = false }) { Text("Cancel") } },
        )
    }
    if (editDetails) {
        SaveResultEffect(model.state.savedChanges) { editDetails = false }
        var notes by rememberSaveable { mutableStateOf(row.notes.orEmpty()) }
        var rest by rememberSaveable { mutableStateOf(row.restTimeSeconds.toString()) }
        val restValue = rest.toIntOrNull()
        AlertDialog(
            onDismissRequest = { editDetails = false }, title = { Text("Notes & rest") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    OutlinedTextField(notes, { notes = it }, label = { Text("Exercise notes") })
                    OutlinedTextField(rest, { rest = it }, label = { Text("Rest seconds") }, singleLine = true,
                        isError = restValue == null || restValue < 0, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
            },
            confirmButton = { TextButton(onClick = {
                model.updateExercise(row.workoutExerciseId, notes, restValue!!)
            }, enabled = !busy && restValue != null && restValue >= 0) { Text("Save details") } },
            dismissButton = { TextButton(onClick = { editDetails = false }) { Text("Cancel") } },
        )
    }
    row.sets.find { it.id == editingSetId }?.let { set ->
        SaveResultEffect(model.state.savedChanges) { editingSetId = null }
        SetEditorDialog(set, busy, model.state.error, onDismiss = { editingSetId = null },
            onSave = { request -> model.updateSet(set.id, request) },
            onDelete = { model.deleteSet(set.id) })
    }
}

@Composable
@OptIn(ExperimentalComposeUiApi::class)
private fun SetEditorDialog(set: ActiveSetEntry, busy: Boolean, error: String?, onDismiss: () -> Unit,
                            onSave: (UpdateSetEntryRequest) -> Unit, onDelete: () -> Unit) {
    var weight by rememberSaveable(set.id) { mutableStateOf(set.weight.toString()) }
    var reps by rememberSaveable(set.id) { mutableStateOf(set.reps.toString()) }
    var unit by rememberSaveable(set.id) { mutableStateOf(set.unit ?: WeightUnit.LB) }
    var type by rememberSaveable(set.id) { mutableStateOf(set.setType ?: SetType.NORMAL) }
    var rpe by rememberSaveable(set.id) { mutableStateOf(set.rpe?.toString().orEmpty()) }
    var rest by rememberSaveable(set.id) { mutableStateOf(set.restSeconds?.toString().orEmpty()) }
    var completed by rememberSaveable(set.id) { mutableStateOf(set.isCompleted) }
    var delete by rememberSaveable(set.id) { mutableStateOf(false) }
    val weightValue = weight.replace(',', '.').toDoubleOrNull()
    val repsValue = reps.toIntOrNull()
    val rpeValue = rpe.replace(',', '.').toFloatOrNull()
    val restValue = rest.toIntOrNull()
    val validWeight = weightValue != null && weightValue.isFinite() && weightValue >= 0
    val validReps = repsValue != null && repsValue >= 0
    val validRpe = rpe.isBlank() || (rpeValue != null && rpeValue.isFinite() && rpeValue in 0f..10f)
    val validRest = rest.isBlank() || (restValue != null && restValue >= 0)
    if (delete) {
        AlertDialog(onDismissRequest = { delete = false }, title = { Text("Delete set ${set.setNumber}?") },
            text = { Text("This permanently removes this set from the workout.") },
            confirmButton = { TextButton(onClick = onDelete, enabled = !busy) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { delete = false }) { Text("Keep set") } })
        return
    }
    AlertDialog(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        onDismissRequest = onDismiss, title = { Text("Edit set ${set.setNumber}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(weight, { weight = it }, label = { Text("Weight") }, singleLine = true,
                    isError = !validWeight, modifier = Modifier.testTag("set-weight"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeightUnit.values().forEach { value -> FilterChip(unit == value, onClick = { unit = value }, label = { Text(value.name) }) }
                }
                OutlinedTextField(reps, { reps = it }, label = { Text("Reps") }, singleLine = true,
                    isError = !validReps, modifier = Modifier.testTag("set-reps"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { expanded = true }) { Text("Type: ${type.label()}") }
                    DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        SetType.values().forEach { value -> DropdownMenuItem(text = { Text(value.label()) }, onClick = { type = value; expanded = false }) }
                    }
                }
                OutlinedTextField(rpe, { rpe = it }, label = { Text("RPE (optional, 0–10)") }, singleLine = true,
                    isError = !validRpe, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(rest, { rest = it }, label = { Text("Rest seconds (optional)") }, singleLine = true,
                    isError = !validRest, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                Row { Checkbox(completed, onCheckedChange = { completed = it }); Text("Mark completed", Modifier.padding(top = 12.dp)) }
                if (!validWeight || !validReps || !validRpe || !validRest) {
                    Text("Use nonnegative weight, reps and rest. RPE must be between 0 and 10.", color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { delete = true }, enabled = !busy) { Text("Delete set") }
            }
        },
        confirmButton = {
            TextButton(modifier = Modifier.testTag("save-set"), enabled = !busy && validWeight && validReps && validRpe && validRest, onClick = {
                onSave(UpdateSetEntryRequest().apply {
                    setWeight(weightValue!!); setReps(repsValue!!); setUnit(unit); setSetType(type)
                    setRpe(if (rpe.isBlank()) null else rpeValue); setRestSeconds(if (rest.isBlank()) null else restValue)
                    setCompleted(completed)
                })
            }) { Text("Save set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun Enum<*>.label(): String = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

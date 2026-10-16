package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup

@Composable
private fun LibraryPage(title: String, model: LibraryViewModel, content: LazyListScope.() -> Unit) {
    val state = model.state
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.error) { state.error?.let { snackbar.showSnackbar(it) } }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "heading") { Text(title, style = MaterialTheme.typography.headlineLarge) }
            if (state.loading) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error ->
                item(key = "error") {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::refresh, enabled = !state.loading) { Text("Retry") }
                }
            }
            content()
        }
    }
}

@Composable
fun ExerciseLibraryScreen(model: LibraryViewModel) {
    val state = model.state
    var search by rememberSaveable { mutableStateOf("") }
    var includeArchived by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    val filtered = state.exercises.filter { (includeArchived || !it.isArchived) && it.title.contains(search, true) }
    LibraryPage("Exercise library", model) {
        item(key = "controls") {
            OutlinedTextField(search, { search = it }, label = { Text("Search exercises") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                Checkbox(includeArchived, onCheckedChange = { includeArchived = it },
                    modifier = Modifier.semantics { contentDescription = "Show archived exercises" })
                Text("Show archived", Modifier.padding(top = 12.dp))
            }
            Button(onClick = { creating = true }, enabled = !state.loading) { Text("New exercise") }
        }
        if (state.loaded && filtered.isEmpty()) item(key = "empty") { Text("No matching exercises.") }
        items(filtered, key = { it.id }) { exercise ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(exercise.title, style = MaterialTheme.typography.titleLarge)
                    Text(exercise.primaryMuscle?.displayName() ?: "Other")
                    if (!exercise.description.isNullOrBlank()) Text(exercise.description)
                    if (exercise.isArchived) Text("Archived")
                    Row {
                        TextButton(onClick = { editingId = exercise.id }, enabled = !state.loading,
                            modifier = Modifier.testTag("edit-exercise-${exercise.id}")) { Text("Edit exercise") }
                        TextButton(onClick = {
                            if (exercise.isArchived) model.archiveExercise(exercise.id, false) else archiveId = exercise.id
                        }, enabled = !state.loading, modifier = Modifier.testTag("archive-exercise-${exercise.id}")) {
                            Text(if (exercise.isArchived) "Restore" else "Archive")
                        }
                    }
                }
            }
        }
    }
    val editing = state.exercises.find { it.id == editingId }
    if (creating || editing != null) {
        ExerciseCatalogDialog(editing, state.loading, onDismiss = { creating = false; editingId = null }) { title, description, primary, secondary ->
            model.saveExercise(editing?.id, title, description, primary, secondary)
            creating = false; editingId = null
        }
    }
    state.exercises.find { it.id == archiveId }?.let { exercise ->
        Confirmation("Archive ${exercise.title}?", "It will be hidden from exercise pickers. Existing workouts keep their saved names.",
            "Archive", state.loading, onDismiss = { archiveId = null }) {
            model.archiveExercise(exercise.id, true); archiveId = null
        }
    }
}

@Composable
private fun ExerciseCatalogDialog(exercise: CatalogExercise?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String, MuscleGroup, List<MuscleGroup>) -> Unit) {
    var title by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.title.orEmpty()) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var primary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.primaryMuscle ?: MuscleGroup.OTHER) }
    var secondary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.secondaryMuscles?.toList() ?: emptyList()) }
    var primaryMenu by remember { mutableStateOf(false) }
    var secondaryMenu by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (exercise == null) "New exercise" else "Edit exercise") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Exercise name") }, singleLine = true,
                    modifier = Modifier.testTag("catalog-exercise-name"))
                OutlinedTextField(description, { description = it }, label = { Text("Description") },
                    modifier = Modifier.testTag("catalog-exercise-description"))
                Box {
                    OutlinedButton(onClick = { primaryMenu = true }) { Text("Primary: ${primary.displayName()}") }
                    DropdownMenu(primaryMenu, { primaryMenu = false }) {
                        MuscleGroup.values().forEach { group -> DropdownMenuItem(text = { Text(group.displayName()) },
                            onClick = { primary = group; primaryMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { secondaryMenu = true }) { Text("Secondary muscles (${secondary.size})") }
                    DropdownMenu(secondaryMenu, { secondaryMenu = false }) {
                        DropdownMenuItem(text = { Text("Done") }, onClick = { secondaryMenu = false })
                        MuscleGroup.values().forEach { group -> DropdownMenuItem(text = { Text(group.displayName()) },
                            trailingIcon = { Checkbox(group in secondary, onCheckedChange = null) },
                            onClick = { secondary = if (group in secondary) secondary - group else secondary + group }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, primary, secondary) },
            enabled = !busy && title.isNotBlank()) { Text("Save exercise") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun TemplateLibraryScreen(model: LibraryViewModel, workoutBlocked: Boolean, onStart: (Long) -> Unit) {
    val state = model.state
    if (state.selectedTemplate != null) {
        TemplateEditorScreen(model, state.selectedTemplate, workoutBlocked, onStart)
        return
    }
    var creating by rememberSaveable { mutableStateOf(false) }
    var includeArchived by rememberSaveable { mutableStateOf(false) }
    val visible = state.templates.filter { includeArchived || !it.isArchived }
    LibraryPage("Workout templates", model) {
        item(key = "controls") {
            Button(onClick = { creating = true }, enabled = !state.loading) { Text("New template") }
            Row {
                Checkbox(includeArchived, onCheckedChange = { includeArchived = it },
                    modifier = Modifier.semantics { contentDescription = "Show archived templates" })
                Text("Show archived", Modifier.padding(top = 12.dp))
            }
        }
        if (state.loaded && visible.isEmpty()) item(key = "empty") { Text("Create a template to save your exercise routine.") }
        items(visible, key = { it.id }) { template ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(template.title, style = MaterialTheme.typography.titleLarge)
                    if (!template.description.isNullOrBlank()) Text(template.description)
                    if (template.isArchived) Text("Archived")
                    TextButton(onClick = { model.openTemplate(template.id) }, enabled = !state.loading,
                        modifier = Modifier.testTag("open-template-${template.id}")) { Text("Open template") }
                }
            }
        }
    }
    if (creating) TemplateDetailsDialog(null, state.loading, { creating = false }) { title, description ->
        model.saveTemplate(null, title, description); creating = false
    }
}

@Composable
private fun TemplateEditorScreen(model: LibraryViewModel, template: TemplateEditorDetail,
    workoutBlocked: Boolean, onStart: (Long) -> Unit) {
    val busy = model.state.loading
    var editing by rememberSaveable(template.id) { mutableStateOf(false) }
    var adding by rememberSaveable(template.id) { mutableStateOf(false) }
    var archiving by rememberSaveable(template.id) { mutableStateOf(false) }
    var removing by rememberSaveable(template.id) { mutableStateOf<Long?>(null) }
    var editingRest by rememberSaveable(template.id) { mutableStateOf<Long?>(null) }
    LibraryPage(template.title, model) {
        item(key = "controls") {
            TextButton(onClick = model::showTemplateList, enabled = !busy) { Text("All templates") }
            if (!template.description.isNullOrBlank()) Text(template.description)
            if (template.isArchived) {
                Text("Archived template")
                Button(onClick = { model.archiveTemplate(false) }, enabled = !busy) { Text("Restore template") }
            } else {
                Row {
                    TextButton(onClick = { editing = true }, enabled = !busy) { Text("Edit template") }
                    TextButton(onClick = { archiving = true }, enabled = !busy) { Text("Archive template") }
                }
                Button(onClick = { onStart(template.id) }, enabled = !busy && !workoutBlocked) { Text("Start this workout") }
                if (workoutBlocked) Text("Finish or discard your current workout before starting another.")
                OutlinedButton(onClick = { adding = true }, enabled = !busy) { Text("Add template exercise") }
            }
        }
        if (template.exercises.isEmpty()) item(key = "empty") { Text("This template has no exercises yet.") }
        itemsIndexed(template.exercises, key = { _, row -> row.id }) { index, row ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${index + 1}. ${row.title}", style = MaterialTheme.typography.titleLarge)
                    Text("Rest: ${row.restSeconds} seconds")
                    if (!template.isArchived) {
                        Row {
                            TextButton(onClick = { model.moveExercise(row.id, index - 1) }, enabled = !busy && index > 0,
                                modifier = Modifier.testTag("template-up-${row.id}")) { Text("Up") }
                            TextButton(onClick = { model.moveExercise(row.id, index + 1) }, enabled = !busy && index < template.exercises.lastIndex,
                                modifier = Modifier.testTag("template-down-${row.id}")) { Text("Down") }
                            TextButton(onClick = { editingRest = row.id }, enabled = !busy,
                                modifier = Modifier.testTag("template-rest-${row.id}")) { Text("Edit rest") }
                        }
                        TextButton(onClick = { removing = row.id }, enabled = !busy,
                            modifier = Modifier.testTag("template-remove-${row.id}")) { Text("Remove") }
                    }
                }
            }
        }
    }
    if (editing) TemplateDetailsDialog(template, busy, { editing = false }) { title, description ->
        model.saveTemplate(template.id, title, description); editing = false
    }
    if (adding) TemplateExercisePicker(model, { adding = false }) { id, rest ->
        model.addExercise(id, rest); adding = false
    }
    if (archiving) Confirmation("Archive this template?", "You can restore it later. Existing workouts won't change.",
        "Archive", busy, { archiving = false }) { model.archiveTemplate(true); archiving = false }
    template.exercises.find { it.id == removing }?.let { row ->
        Confirmation("Remove ${row.title}?", "This only removes the exercise from this template.", "Remove", busy,
            { removing = null }) { model.removeExercise(row.id); removing = null }
    }
    template.exercises.find { it.id == editingRest }?.let { row ->
        var seconds by rememberSaveable(row.id) { mutableStateOf(row.restSeconds.toString()) }
        val value = seconds.toIntOrNull()
        AlertDialog(onDismissRequest = { editingRest = null }, title = { Text("Rest target") },
            text = { OutlinedTextField(seconds, { seconds = it }, label = { Text("Rest seconds") }, singleLine = true,
                modifier = Modifier.testTag("template-rest-input"), isError = value == null || value < 0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) },
            confirmButton = { TextButton(onClick = { model.updateRest(row.id, value!!); editingRest = null },
                enabled = !busy && value != null && value >= 0) { Text("Save rest") } },
            dismissButton = { TextButton(onClick = { editingRest = null }) { Text("Cancel") } })
    }
}

@Composable
private fun TemplateDetailsDialog(template: TemplateEditorDetail?, busy: Boolean, onDismiss: () -> Unit,
    onSave: (String, String) -> Unit) {
    var title by rememberSaveable(template?.id) { mutableStateOf(template?.title.orEmpty()) }
    var description by rememberSaveable(template?.id) { mutableStateOf(template?.description.orEmpty()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (template == null) "New template" else "Edit template") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Template name") }, singleLine = true,
                    modifier = Modifier.testTag("template-name"))
                OutlinedTextField(description, { description = it }, label = { Text("Description") },
                    modifier = Modifier.testTag("template-description"))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description) }, enabled = !busy && title.isNotBlank()) { Text("Save template") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun TemplateExercisePicker(model: LibraryViewModel, onDismiss: () -> Unit, onAdd: (Long, Int) -> Unit) {
    var search by rememberSaveable { mutableStateOf("") }
    var rest by rememberSaveable { mutableStateOf("0") }
    val restValue = rest.toIntOrNull()
    val options = model.state.exercises.filter { !it.isArchived && it.title.contains(search, true) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Choose template exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(search, { search = it }, label = { Text("Search exercises") }, singleLine = true)
                OutlinedTextField(rest, { rest = it }, label = { Text("Rest seconds") }, singleLine = true,
                    isError = restValue == null || restValue < 0, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (options.isEmpty()) Text("No matching exercises. Add exercises in the Exercises tab.")
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(options, key = { it.id }) { exercise ->
                        TextButton(onClick = { onAdd(exercise.id, restValue!!) },
                            enabled = !model.state.loading && restValue != null && restValue >= 0,
                            modifier = Modifier.fillMaxWidth().testTag("template-picker-${exercise.id}")) { Text(exercise.title) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun Confirmation(title: String, message: String, action: String, busy: Boolean,
    onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm, enabled = !busy,
            modifier = Modifier.testTag("confirm-library-action")) { Text(action) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

private fun Enum<*>.displayName() = name.lowercase().replace('_', ' ').replaceFirstChar { it.titlecase() }

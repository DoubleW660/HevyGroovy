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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup

@Composable
private fun LibraryPage(title: String, model: LibraryViewModel, readyTag: String = "library-ready", content: LazyListScope.() -> Unit) {
    val state = model.state
    Scaffold(Modifier.testTag(if (state.loaded && !state.busy && state.error == null) readyTag else "library-loading")) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).testTag("library-list"), contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("YOUR TRAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                }
            }
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
    var guideId by rememberSaveable { mutableStateOf<Long?>(null) }
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
            Button(onClick = { creating = true }, enabled = !state.busy) { Text("New exercise") }
        }
        if (state.loaded && filtered.isEmpty()) item(key = "empty") { Text("No matching exercises.") }
        items(filtered, key = { it.id }) { exercise ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(exercise.title, style = MaterialTheme.typography.titleLarge)
                    Text(exercise.primaryMuscle?.displayName() ?: "Other", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    if (!exercise.description.isNullOrBlank()) Text(exercise.description,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (exercise.isArchived) Text("Archived")
                    Row {
                        if (!exercise.description.isNullOrBlank()) {
                            TextButton(onClick = { guideId = exercise.id },
                                modifier = Modifier.testTag("guide-exercise-${exercise.id}").semantics {
                                    contentDescription = "Read guide for ${exercise.title}"
                                }) { Text("Guide") }
                        }
                        TextButton(onClick = { editingId = exercise.id }, enabled = !state.busy,
                            modifier = Modifier.testTag("edit-exercise-${exercise.id}")) { Text("Edit exercise") }
                        TextButton(onClick = {
                            if (exercise.isArchived) model.archiveExercise(exercise.id, false) else archiveId = exercise.id
                        }, enabled = !state.busy, modifier = Modifier.testTag("archive-exercise-${exercise.id}")) {
                            Text(if (exercise.isArchived) "Restore" else "Archive")
                        }
                    }
                }
            }
        }
    }
    state.exercises.find { it.id == guideId && !it.description.isNullOrBlank() }?.let { exercise ->
        ExerciseGuideDialog(exercise, onDismiss = { guideId = null })
    }
    val editing = state.exercises.find { it.id == editingId }
    if (creating || editing != null) {
        SaveResultEffect(state.savedChanges) { creating = false; editingId = null }
        ExerciseCatalogDialog(editing, state.busy, state.error, onDismiss = { creating = false; editingId = null }) { title, description, primary, secondary ->
            model.saveExercise(editing?.id, title, description, primary, secondary)
        }
    }
    state.exercises.find { it.id == archiveId }?.let { exercise ->
        Confirmation("Archive ${exercise.title}?", "It will be hidden from exercise pickers. Existing workouts keep their saved names.",
            "Archive", state.busy, onDismiss = { archiveId = null }) {
            model.archiveExercise(exercise.id, true); archiveId = null
        }
    }
}

@Composable
private fun ExerciseGuideDialog(exercise: CatalogExercise, onDismiss: () -> Unit) {
    val paragraphs = remember(exercise.description) {
        exercise.description.orEmpty().split(Regex("\\n\\s*\\n"))
    }
    AlertDialog(onDismissRequest = onDismiss, modifier = Modifier.testTag("exercise-guide-dialog"),
        title = { Text(exercise.title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 400.dp).testTag("exercise-guide-instructions"),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                itemsIndexed(paragraphs) { _, paragraph -> Text(paragraph) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("close-exercise-guide")) { Text("Close") } })
}

@Composable
private fun ExerciseCatalogDialog(exercise: CatalogExercise?, busy: Boolean, error: String?, onDismiss: () -> Unit,
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
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
fun TemplateLibraryScreen(model: LibraryViewModel, workoutBlocked: Boolean,
    header: (@Composable () -> Unit)? = null, onStart: (Long) -> Unit) {
    val state = model.state
    if (state.selectedTemplate != null) {
        TemplateEditorScreen(model, state.selectedTemplate, workoutBlocked, onStart)
        return
    }
    if (state.selectedTemplateId != null) {
        LibraryPage("Routine", model) {
            item { Text(if (state.loading) "Loading routine…" else "Routine couldn't be loaded.") }
            item { TextButton(onClick = model::showTemplateList, enabled = !state.loading) { Text("All routines") } }
        }
        return
    }
    var includeArchived by rememberSaveable { mutableStateOf(false) }
    val visible = state.templates.filter { includeArchived || !it.isArchived }
    LibraryPage("Workout", model) {
        if (header != null) item(key = "quick-start") { header() }
        item(key = "controls") {
            Text("My routines", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Button(onClick = model::beginTemplateDetails, enabled = !state.busy,
                modifier = Modifier.testTag(if (state.loaded && !state.busy) "template-list-ready" else "template-list-loading")) { Text("New routine") }
            Row {
                Checkbox(includeArchived, onCheckedChange = { includeArchived = it },
                    modifier = Modifier.semantics { contentDescription = "Show archived routines" })
                Text("Show archived", Modifier.padding(top = 12.dp))
            }
        }
        if (state.loaded && visible.isEmpty()) item(key = "empty") { Text("Build a routine with your favorite exercises.") }
        items(visible, key = { it.id }) { template ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(template.title, style = MaterialTheme.typography.titleLarge)
                    if (!template.description.isNullOrBlank()) Text(template.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (template.isArchived) Text("Archived")
                    TextButton(onClick = { model.openTemplate(template.id) }, enabled = !state.busy,
                        modifier = Modifier.testTag("open-template-${template.id}")) { Text("Open routine") }
                }
            }
        }
    }
    if (model.templateDraft?.kind == "details") TemplateDetailsDialog(model)
}

@Composable
private fun TemplateEditorScreen(model: LibraryViewModel, template: TemplateEditorDetail,
    workoutBlocked: Boolean, onStart: (Long) -> Unit) {
    val busy = model.state.busy
    var archiving by rememberSaveable(template.id) { mutableStateOf(false) }
    var removing by rememberSaveable(template.id) { mutableStateOf<Long?>(null) }
    LibraryPage(template.title, model, readyTag = "template-ready") {
        item(key = "controls") {
            TextButton(onClick = model::showTemplateList, enabled = !busy,
                modifier = Modifier.testTag("template-list-back")) { Text("All routines") }
            if (!template.description.isNullOrBlank()) Text(template.description)
            if (template.isArchived) {
                Text("Archived routine")
                Button(onClick = { model.archiveTemplate(false) }, enabled = !busy) { Text("Restore routine") }
            } else {
                Row {
                    TextButton(onClick = model::beginTemplateDetails, enabled = !busy) { Text("Edit routine") }
                    TextButton(onClick = { archiving = true }, enabled = !busy) { Text("Archive routine") }
                }
                Button(onClick = { onStart(template.id) }, enabled = !busy && !workoutBlocked) { Text("Start this workout") }
                if (workoutBlocked) Text("Finish or discard your current workout before starting another.")
                OutlinedButton(onClick = model::beginTemplateExercise, enabled = !busy) { Text("Add routine exercise") }
            }
        }
        if (template.exercises.isEmpty()) item(key = "empty") { Text("Add exercises to build your routine.") }
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
                            TextButton(onClick = { model.beginTemplateRest(row) }, enabled = !busy,
                                modifier = Modifier.testTag("template-rest-${row.id}")) { Text("Edit rest") }
                        }
                        TextButton(onClick = { removing = row.id }, enabled = !busy,
                            modifier = Modifier.testTag("template-remove-${row.id}")) { Text("Remove") }
                    }
                }
            }
        }
    }
    if (model.templateDraft?.kind == "details") TemplateDetailsDialog(model)
    if (model.templateDraft?.kind == "add") TemplateExercisePicker(model)
    if (archiving) Confirmation("Archive this routine?", "You can restore it later. Existing workouts won't change.",
        "Archive", busy, { archiving = false }) { model.archiveTemplate(true); archiving = false }
    template.exercises.find { it.id == removing }?.let { row ->
        Confirmation("Remove ${row.title}?", "This only removes the exercise from this routine.", "Remove", busy,
            { removing = null }) { model.removeExercise(row.id); removing = null }
    }
    model.templateDraft?.takeIf { it.kind == "rest" }?.let { draft ->
        val seconds = draft.rest
        val value = seconds.toIntOrNull()
        AlertDialog(onDismissRequest = model::closeTemplateDraft, title = { Text("Rest target") },
            text = { Column {
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(seconds, { model.updateTemplateDraft(rest = it) }, label = { Text("Rest seconds") }, singleLine = true,
                modifier = Modifier.testTag("template-rest-input"), isError = value == null || value < 0,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)) } },
            confirmButton = { TextButton(onClick = { model.updateRest(draft.rowId!!, value!!) },
                enabled = !busy && value != null && value >= 0) { Text("Save rest") } },
            dismissButton = { TextButton(onClick = model::closeTemplateDraft) { Text("Cancel") } })
    }
}

@Composable
private fun TemplateDetailsDialog(model: LibraryViewModel) {
    val draft = model.templateDraft ?: return
    AlertDialog(onDismissRequest = model::closeTemplateDraft, title = { Text(if (draft.templateId == null) "New routine" else "Edit routine") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(draft.title, { model.updateTemplateDraft(title = it) }, label = { Text("Routine name") }, singleLine = true,
                    modifier = Modifier.testTag("template-name"))
                OutlinedTextField(draft.description, { model.updateTemplateDraft(description = it) }, label = { Text("Description") },
                    modifier = Modifier.testTag("template-description"))
            }
        },
        confirmButton = { TextButton(onClick = { model.saveTemplate(draft.templateId, draft.title, draft.description) },
            enabled = !model.state.busy && draft.title.isNotBlank()) { Text("Save routine") } },
        dismissButton = { TextButton(onClick = model::closeTemplateDraft) { Text("Cancel") } })
}

@Composable
private fun TemplateExercisePicker(model: LibraryViewModel) {
    val draft = model.templateDraft ?: return
    val search = draft.search
    val rest = draft.rest
    val restValue = rest.toIntOrNull()
    val options = model.state.exercises.filter { !it.isArchived && it.title.contains(search, true) }
    AlertDialog(onDismissRequest = model::closeTemplateDraft, title = { Text("Choose routine exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(search, { model.updateTemplateDraft(search = it) }, label = { Text("Search exercises") }, singleLine = true)
                OutlinedTextField(rest, { model.updateTemplateDraft(rest = it) }, label = { Text("Rest seconds") }, singleLine = true,
                    isError = restValue == null || restValue < 0, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (options.isEmpty()) Text("No matching exercises. Add exercises in the Exercises tab.")
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(options, key = { it.id }) { exercise ->
                        TextButton(onClick = { model.addExercise(exercise.id, restValue!!) },
                            enabled = !model.state.busy && restValue != null && restValue >= 0,
                            modifier = Modifier.fillMaxWidth().testTag("template-picker-${exercise.id}")) { Text(exercise.title) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = model::closeTemplateDraft) { Text("Cancel") } })
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

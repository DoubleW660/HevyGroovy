package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.dto.*
import com.example.hevygroovy.entity.enums.MuscleGroup
import java.text.DateFormat
import java.util.Date

@Composable
private fun LibraryPage(title: String, model: LibraryViewModel, readyTag: String = "library-ready",
    listState: LazyListState = rememberLazyListState(), listModifier: Modifier = Modifier,
    userScrollEnabled: Boolean = true, overlay: @Composable () -> Unit = {}, content: LazyListScope.() -> Unit) {
    val state = model.state
    Scaffold(Modifier.testTag(if (state.loaded && !state.busy && state.error == null) readyTag else "library-loading")) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.fillMaxSize().testTag("library-list").then(listModifier), state = listState,
            userScrollEnabled = userScrollEnabled, contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item(key = "heading") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("YOUR TRAINING", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    Text(title, style = MaterialTheme.typography.headlineLarge)
                }
            }
            if (state.loading && !state.loaded) item(key = "loading") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            state.error?.let { error ->
                item(key = "error") {
                    Text(error, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::refresh, enabled = !state.loading) { Text("Retry") }
                }
            }
            content()
        }
        overlay()
        }
    }
}

@Composable
fun ExerciseLibraryScreen(model: LibraryViewModel, records: ExerciseRecordsViewModel? = null,
    onOpenExercise: ((Long, String, Int) -> Unit)? = null) {
    val state = model.state
    var search by rememberSaveable { mutableStateOf("") }
    var includeArchived by rememberSaveable { mutableStateOf(false) }
    var creating by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    var archiveId by rememberSaveable { mutableStateOf<Long?>(null) }
    var initialDetailTab by rememberSaveable { mutableStateOf(0) }
    val listState = rememberLazyListState()
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    // Restore a selected records sheet after recreation without issuing reads while scrolling.
    val recordExerciseId = records?.state?.selectedExerciseId
    LaunchedEffect(recordExerciseId, records?.state?.loaded) {
        if (recordExerciseId != null && !records.state.loaded && !records.state.loading && records.state.error == null) {
            records.refresh(recordExerciseId)
        }
    }
    val filtered = state.exercises.filter { (includeArchived || !it.isArchived) && it.title.contains(search, true) }
    fun openExercise(exercise: CatalogExercise, initialTab: Int = 0) {
        focus.clearFocus(); keyboard?.hide()
        initialDetailTab = initialTab
        if (onOpenExercise != null) onOpenExercise(exercise.id, exercise.title, initialTab)
        else records?.refresh(exercise.id)
    }
    if (recordExerciseId != null && onOpenExercise == null) {
        ExerciseDetailScreen(records, state.exercises.find { it.id == recordExerciseId }?.title ?: "Exercise",
            onBack = records::close, initialTab = initialDetailTab, library = model)
        return
    }
    val canOpenExercise = onOpenExercise != null || records != null
    LibraryPage("Exercise library", model, listState = listState) {
        item(key = "controls") {
            OutlinedTextField(search, { search = it }, label = { Text("Search exercises") },
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("exercise-search"))
            Row {
                Checkbox(includeArchived, onCheckedChange = { includeArchived = it },
                    modifier = Modifier.semantics { contentDescription = "Show archived exercises" })
                Text("Show archived", Modifier.padding(top = 12.dp))
            }
            Button(onClick = { creating = true }, enabled = !state.busy) { Text("New exercise") }
        }
        if (state.loaded && filtered.isEmpty()) item(key = "empty") { Text("No matching exercises.") }
        items(filtered, key = { it.id }) { exercise ->
            Card(Modifier.fillMaxWidth().testTag("open-library-exercise-" + exercise.id)
                .clickable(enabled = canOpenExercise) { openExercise(exercise) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(exercise.title, style = MaterialTheme.typography.titleLarge)
                    Text(exercise.primaryMuscle?.displayName() ?: "Other", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                    Text((if (exercise.isUnilateral) "Unilateral" else "Bilateral") + " · " + exercise.trackingType.label(),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!exercise.description.isNullOrBlank()) Text(exercise.description,
                        maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (exercise.isArchived) Text("Archived")
                    if (canOpenExercise) {
                        TextButton(onClick = { openExercise(exercise) },
                            modifier = Modifier.testTag("records-exercise-${exercise.id}").semantics {
                                contentDescription = "View records for ${exercise.title}"
                            }) { Text("Records") }
                    }
                    Row {
                        if (canOpenExercise && !exercise.description.isNullOrBlank()) {
                            TextButton(onClick = { openExercise(exercise, 2) },
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
    val editing = state.exercises.find { it.id == editingId }
    if (creating || editing != null) {
        SaveResultEffect(state.savedChanges) { creating = false; editingId = null }
        ExerciseCatalogDialog(editing, state.busy, state.error, onDismiss = { creating = false; editingId = null }) { title, description, primary, secondary, unilateral, tracking ->
            model.saveExercise(editing?.id, title, description, primary, secondary, unilateral, tracking)
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
internal fun ExerciseCatalogDialog(exercise: CatalogExercise?, busy: Boolean, error: String?, onDismiss: () -> Unit,
    initialTitle: String = "",
    onSave: (String, String, MuscleGroup, List<MuscleGroup>, Boolean, com.example.hevygroovy.entity.enums.TrackingType) -> Unit) {
    var title by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.title ?: initialTitle) }
    var description by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.description.orEmpty()) }
    var primary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.primaryMuscle ?: MuscleGroup.OTHER) }
    var secondary by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.secondaryMuscles?.toList() ?: emptyList()) }
    var unilateral by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.isUnilateral ?: false) }
    var tracking by rememberSaveable(exercise?.id) { mutableStateOf(exercise?.trackingType ?: com.example.hevygroovy.entity.enums.TrackingType.WEIGHT_REPS) }
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
                Text("Movement type", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!unilateral, onClick = { unilateral = false }, enabled = !busy, label = { Text("Bilateral") },
                        modifier = Modifier.testTag("catalog-bilateral"))
                    FilterChip(unilateral, onClick = { unilateral = true }, enabled = !busy, label = { Text("Unilateral") },
                        modifier = Modifier.testTag("catalog-unilateral"))
                }
                Text(if (unilateral) "Enter weight and reps per side. Volume = weight × reps × 2."
                    else "Volume = weight × reps.", style = MaterialTheme.typography.bodySmall)
                Text("Applies to existing history and records.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                TrackingTypeSelector(tracking, !busy) { tracking = it }
                Box {
                    OutlinedButton(onClick = { primaryMenu = true }, enabled = !busy,
                        modifier = Modifier.testTag("exercise-major-muscle")) { Text("Major (primary): ${primary.displayName()}") }
                    DropdownMenu(primaryMenu, { primaryMenu = false }) {
                        MuscleGroup.values().forEach { group -> DropdownMenuItem(text = { Text(group.displayName()) },
                            modifier = Modifier.testTag("major-muscle-${group.name}"),
                            onClick = { primary = group; secondary = secondary - group; primaryMenu = false }) }
                    }
                }
                Box {
                    OutlinedButton(onClick = { secondaryMenu = true }, enabled = !busy,
                        modifier = Modifier.testTag("exercise-minor-muscles")) { Text("Minor (secondary) muscles (${secondary.size})") }
                    DropdownMenu(secondaryMenu, { secondaryMenu = false }) {
                        DropdownMenuItem(text = { Text("Done") }, onClick = { secondaryMenu = false })
                        MuscleGroup.values().filter { it != primary }.forEach { group -> DropdownMenuItem(text = { Text(group.displayName()) },
                            modifier = Modifier.testTag("minor-muscle-${group.name}"),
                            trailingIcon = { Checkbox(group in secondary, onCheckedChange = null) },
                            onClick = { secondary = if (group in secondary) secondary - group else secondary + group }) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, description, primary, secondary.filter { it != primary }, unilateral, tracking) },
            enabled = !busy && title.isNotBlank()) { Text("Save exercise") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun TemplateLibraryScreen(model: LibraryViewModel, workoutBlocked: Boolean,
    header: (@Composable () -> Unit)? = null, onOpenExercise: ((Long, String) -> Unit)? = null,
    onStart: (Long) -> Unit) {
    val state = model.state
    if (state.selectedTemplate != null) {
        TemplateEditorScreen(model, state.selectedTemplate, workoutBlocked, onStart, onOpenExercise)
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
    var sortMenu by remember { mutableStateOf(false) }
    val visible = sortedTemplates(state.templates, state.lastCompletedAtByTemplate, model.templateSort)
        .filter { includeArchived || !it.isArchived }
    LibraryPage("Workout", model) {
        if (header != null) item(key = "quick-start") { header() }
        item(key = "controls") {
            Text("My routines", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            Box {
                OutlinedButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("template-sort")) {
                    Text("Sort: " + model.templateSort.label)
                }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    TemplateSort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(sort.label) }, modifier = Modifier.testTag("template-sort-" + sort.name),
                            onClick = { model.selectTemplateSort(sort); sortMenu = false })
                    }
                }
            }
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
            Card(Modifier.fillMaxWidth().testTag("template-card-" + template.id), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp)) {
                    Text(template.title, style = MaterialTheme.typography.titleLarge)
                    val completed = state.lastCompletedAtByTemplate[template.id]
                    Text(if (completed == null) "Never completed" else "Last completed: " +
                        DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(completed)),
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
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
    workoutBlocked: Boolean, onStart: (Long) -> Unit, onOpenExercise: ((Long, String) -> Unit)? = null) {
    val busy = model.state.busy
    var archiving by rememberSaveable(template.id) { mutableStateOf(false) }
    var removing by rememberSaveable(template.id) { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val rowIds = template.exercises.map { it.id }
    val reorder = rememberExerciseReorder(listState, template.id, rowIds, busy)
    val byId = template.exercises.associateBy { it.id }
    val rows = (reorder.pendingOrder ?: rowIds).mapNotNull(byId::get)
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LibraryPage(template.title, model, readyTag = "template-ready", listState = listState,
        listModifier = Modifier.exerciseReorderGestures(reorder, rowIds, !busy && !template.isArchived,
            onBegin = { focus.clearFocus(); keyboard?.hide() }, onMove = model::moveExercise),
        userScrollEnabled = reorder.draggedId == null,
        overlay = { ExerciseDragPreview(reorder, byId[reorder.draggedId]?.title) }) {
        item(key = "progress") { RoutineProgressChart(template.id, model.state.templateHistory) }
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
        itemsIndexed(rows, key = { _, row -> row.id }) { index, row ->
            ExerciseReorderItem(reorder, row.id, index, rows.size) {
            Card(Modifier.fillMaxWidth().testTag("template-exercise-" + row.id)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).testTag("reorder-template-exercise-" + row.id)
                        .exerciseReorderHeader(reorder, row.id).semantics {
                            contentDescription = if (template.isArchived) row.title else "Reorder " + row.title + ". Touch and hold, then drag."
                            if (!template.isArchived) customActions = buildList {
                                if (index > 0) add(CustomAccessibilityAction("Move up") {
                                    if (busy) false else { model.moveExercise(row.id, index - 1); true }
                                })
                                if (index < rows.lastIndex) add(CustomAccessibilityAction("Move down") {
                                    if (busy) false else { model.moveExercise(row.id, index + 1); true }
                                })
                            }
                        }, horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (!template.isArchived) ExerciseDragHandle()
                        Text(row.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f)
                            .testTag("open-template-exercise-" + row.id)
                            .clickable(enabled = onOpenExercise != null) {
                                focus.clearFocus(); keyboard?.hide()
                                onOpenExercise?.invoke(row.exerciseId, row.title)
                            })
                    }
                    Text("Rest: ${row.restSeconds} seconds")
                    if (!template.isArchived) {
                        Row {
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
        RestDurationPicker(draft.rest.toIntOrNull() ?: 0, onDismiss = model::closeTemplateDraft,
            onSave = { model.updateRest(draft.rowId!!, it!!) }, enabled = !busy,
            error = model.state.error, testTag = "template-rest-input")
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
    val restValue = draft.rest.toIntOrNull() ?: 0
    var editRest by rememberSaveable(draft.templateId) { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val options = model.state.exercises.filter { !it.isArchived && it.title.contains(search, true) }
    AlertDialog(onDismissRequest = model::closeTemplateDraft, title = { Text("Choose routine exercise") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                model.state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                OutlinedTextField(search, { model.updateTemplateDraft(search = it) }, label = { Text("Search exercises") }, singleLine = true)
                OutlinedButton(onClick = { focus.clearFocus(); keyboard?.hide(); editRest = true }, enabled = !model.state.busy,
                    modifier = Modifier.testTag("template-new-exercise-rest")) { Text("Rest: " + restDurationLabel(restValue)) }
                if (options.isEmpty()) Text("No matching exercises. Add exercises in the Exercises tab.")
                LazyColumn(Modifier.heightIn(max = 240.dp)) {
                    items(options, key = { it.id }) { exercise ->
                        TextButton(onClick = { model.addExercise(exercise.id, restValue) },
                            enabled = !model.state.busy && restValue >= 0,
                            modifier = Modifier.fillMaxWidth().testTag("template-picker-${exercise.id}")) { Text(exercise.title) }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = model::closeTemplateDraft) { Text("Cancel") } })
    if (editRest) RestDurationPicker(restValue, onDismiss = { editRest = false },
        onSave = { model.updateTemplateDraft(rest = it.toString()); editRest = false }, enabled = !model.state.busy)
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

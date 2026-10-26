package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit as WeightUnit
import java.text.DateFormat
import java.util.Date

@Composable
@OptIn(ExperimentalLayoutApi::class)
fun WorkoutHomeScreen(viewModel: WorkoutHomeViewModel, showHistory: Boolean = true,
    onMinimize: (() -> Unit)? = null, onOpenExercise: ((Long, String) -> Unit)? = null) {
    val state = viewModel.state
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val active = state.activeWorkout
    val rowIds = active?.exercises.orEmpty().map { it.workoutExerciseId }
    val reorder = rememberExerciseReorder(listState, active?.workoutId, rowIds, state.busy)
    var suppressAccessibilityBlurSave by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val inlineDrafts = rememberSaveable(active?.workoutId, saver = InlineSetDraftMapSaver) {
        mutableStateMapOf<Long, InlineSetDraft>()
    }
    val persistedSets = active?.exercises.orEmpty().flatMap { it.sets }.associateBy { it.id }
    val hasUnsavedSets = inlineDrafts.any { (id, draft) -> persistedSets[id]?.let { !draft.matches(it) } == true }
    LaunchedEffect(active?.workoutId, persistedSets.keys) {
        if (state.loaded && active != null) {
            inlineDrafts.keys.toList().filter { it !in persistedSets }.forEach(inlineDrafts::remove)
        }
    }
    val exercisesById = active?.exercises.orEmpty().associateBy { it.workoutExerciseId }
    val orderedExercises = (reorder.pendingOrder ?: rowIds).mapNotNull(exercisesById::get)
    LaunchedEffect(suppressAccessibilityBlurSave) {
        if (suppressAccessibilityBlurSave) {
            // Keep the guard through focus dispatch, including focus events applied after recomposition.
            withFrameNanos { }
            suppressAccessibilityBlurSave = false
        }
    }
    Scaffold(Modifier.testTag(if (state.loaded && !state.busy && state.error == null) "workouts-ready" else "workouts-loading"),
        topBar = {
            if (onMinimize != null) FlowRow(Modifier.fillMaxWidth()
                .padding(horizontal = GroovyLayout.ScreenPadding, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMinimize,
                    modifier = Modifier.heightIn(min = GroovyLayout.ButtonHeight).testTag("minimize-workout")) { Text("Minimize") }
                if (active != null) FinishWorkoutButton(viewModel, active, hasUnsavedSets)
            }
        }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("workout-list")
                .exerciseReorderGestures(reorder, rowIds, !state.busy,
                    onBegin = { focus.clearFocus(); keyboard?.hide() }, onMove = viewModel::moveExercise),
            state = listState,
            userScrollEnabled = reorder.draggedId == null,
            contentPadding = PaddingValues(GroovyLayout.ScreenPadding),
            verticalArrangement = Arrangement.spacedBy(GroovyLayout.SectionGap),
        ) {
            item(key = "title") {
                ScreenHeading(if (showHistory) "HevyGroovy" else "Workout",
                    if (showHistory) "Your workouts" else "One set at a time.")
            }
            if (state.loading && !state.loaded) {
                item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            state.error?.let { message ->
                item(key = "error") {
                    ErrorCard(message, viewModel::refresh, retryEnabled = !state.loading)
                }
            }
            if (state.loaded) {
                item(key = "active-workout") {
                    if (active == null) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(GroovyLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(GroovyLayout.ItemGap)) {
                                Text("Ready for your next session?", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = viewModel::startWorkout, enabled = !state.busy && state.error == null,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = GroovyLayout.ButtonHeight)) {
                                    Text("Start empty workout")
                                }
                            }
                        }
                    } else {
                        ActiveWorkoutHeader(viewModel, active, onDiscard = { confirmDiscard = true },
                            hasUnsavedSets = hasUnsavedSets, showFinish = onMinimize == null)
                    }
                }
                if (active != null) {
                    itemsIndexed(orderedExercises, key = { _, exercise -> "exercise-${exercise.workoutExerciseId}" }) { index, exercise ->
                        val name = exercise.exerciseNameSnapshot ?: "Exercise"
                        ExerciseReorderItem(reorder, exercise.workoutExerciseId, index, active.exercises.size) {
                        WorkoutExerciseCard(viewModel, exercise, index, active.exercises.size,
                            onOpenExercise = onOpenExercise,
                            canPersist = { reorder.draggedId == null && !suppressAccessibilityBlurSave },
                            onDraftChanged = { id, draft -> inlineDrafts[id] = draft },
                            reorderModifier = Modifier.testTag("reorder-exercise-${exercise.workoutExerciseId}")
                                .exerciseReorderHeader(reorder, exercise.workoutExerciseId)
                                .semantics {
                                    contentDescription = "Reorder $name. Touch and hold, then drag."
                                    customActions = buildList {
                                        if (index > 0) add(CustomAccessibilityAction("Move up") {
                                            if (state.busy) false else {
                                                suppressAccessibilityBlurSave = true
                                                focus.clearFocus(); keyboard?.hide()
                                                viewModel.moveExercise(exercise.workoutExerciseId, index - 1); true
                                            }
                                        })
                                        if (index < active.exercises.lastIndex) add(CustomAccessibilityAction("Move down") {
                                            if (state.busy) false else {
                                                suppressAccessibilityBlurSave = true
                                                focus.clearFocus(); keyboard?.hide()
                                                viewModel.moveExercise(exercise.workoutExerciseId, index + 1); true
                                            }
                                        })
                                    }
                                })
                        }
                    }
                }
                if (showHistory) {
                item(key = "recent-heading") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent workouts", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Refresh") }
                    }
                }
                if (state.history.isEmpty()) {
                    item(key = "empty-history") { Text("Your finished workouts will appear here.") }
                }
                items(state.history, key = { it.workoutId }) { workout ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(workout.nameSnapshot ?: "Workout", style = MaterialTheme.typography.titleMedium)
                            Text(DateFormat.getDateInstance().format(Date(workout.startedAtEpochMillis)))
                            Text("${workout.totalExercises} exercises · ${workout.totalSets} completed sets")
                        }
                    }
                }
                }
            }
        }
        ExerciseDragPreview(reorder, exercisesById[reorder.draggedId]?.exerciseNameSnapshot)
        }
    }
    if (confirmDiscard && state.activeWorkout != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("This removes this workout and all its sets. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discardWorkout() }, enabled = !state.busy,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep workout") } },
        )
    }
}

private val InlineSetDraftMapSaver = listSaver<SnapshotStateMap<Long, InlineSetDraft>, String>(
    save = { drafts -> listOf("v2") + drafts.entries.flatMap { (id, draft) ->
        listOf(id.toString(), draft.weightText, draft.repsText, draft.unit.name, draft.type.name,
            draft.durationText ?: "~", draft.distanceText ?: "~")
    } },
    restore = { values -> mutableStateMapOf<Long, InlineSetDraft>().apply {
        val extended = values.firstOrNull() == "v2"
        (if (extended) values.drop(1) else values).chunked(if (extended) 7 else 5).forEach { fields ->
            put(fields[0].toLong(), InlineSetDraft(fields[1], fields[2],
                WeightUnit.valueOf(fields[3]), SetType.valueOf(fields[4]),
                if (extended) fields[5].takeUnless { it == "~" } else null,
                if (extended) fields[6].takeUnless { it == "~" } else null))
        }
    } },
)

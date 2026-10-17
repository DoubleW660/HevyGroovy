package com.example.hevygroovy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.gestures.scrollBy
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
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
import kotlin.math.roundToInt

@Composable
fun WorkoutHomeScreen(viewModel: WorkoutHomeViewModel, showHistory: Boolean = true) {
    val state = viewModel.state
    val latestState = rememberUpdatedState(state)
    var confirmDiscard by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val reorder = remember { WorkoutExerciseReorder() }
    var suppressAccessibilityBlurSave by remember { mutableStateOf(false) }
    val headerBounds = remember { mutableMapOf<Long, Rect>() }
    var listBounds by remember { mutableStateOf(Rect.Zero) }
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val haptic = LocalHapticFeedback.current
    val rowStep = with(LocalDensity.current) { 84.dp.toPx() }
    val scrollEdge = with(LocalDensity.current) { 72.dp.toPx() }
    val scrollStep = with(LocalDensity.current) { 9.dp.toPx() }
    val active = state.activeWorkout
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
    val orderedExercises = active?.exercises.orEmpty().toMutableList().apply {
        val source = indexOfFirst { it.workoutExerciseId == reorder.exerciseId }
        if (source >= 0 && reorder.targetIndex in indices) add(reorder.targetIndex, removeAt(source))
    }
    LaunchedEffect(suppressAccessibilityBlurSave) {
        if (suppressAccessibilityBlurSave) {
            // Keep the guard through focus dispatch, including focus events applied after recomposition.
            withFrameNanos { }
            suppressAccessibilityBlurSave = false
        }
    }
    LaunchedEffect(reorder.exerciseId) {
        while (reorder.exerciseId != null) {
            withFrameNanos { }
            val amount = when {
                reorder.pointerY < scrollEdge -> -scrollStep
                reorder.pointerY > listBounds.height - scrollEdge -> scrollStep
                else -> 0f
            }
            if (amount != 0f) {
                val consumed = listState.scrollBy(amount)
                reorder.move(consumed, rowStep, latestState.value.activeWorkout?.exercises?.size ?: 0)
            }
        }
    }
    Scaffold(Modifier.testTag(if (state.loaded && !state.busy && state.error == null) "workouts-ready" else "workouts-loading")) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).testTag("workout-list")
                .onGloballyPositioned { listBounds = it.boundsInRoot() }
                .pointerInput(active?.workoutId) {
                    // Recognize only exercise headers. The detector stays on this list as cards collapse.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val rowId = headerBounds.entries.firstOrNull {
                            it.value.contains(down.position + listBounds.topLeft)
                        }?.key
                        if (rowId == null || latestState.value.busy) return@awaitEachGesture
                        val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        val rows = latestState.value.activeWorkout?.exercises.orEmpty()
                        val source = rows.indexOfFirst { it.workoutExerciseId == rowId }
                        if (source < 0) return@awaitEachGesture
                        reorder.begin(rowId, source, pressed.position.y)
                        focus.clearFocus(); keyboard?.hide()
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            val dropped = drag(pressed.id) { change ->
                                reorder.pointerY = change.position.y
                                reorder.move(change.positionChange().y, rowStep, rows.size)
                                change.consume()
                            }
                            val target = reorder.targetIndex
                            reorder.cancel()
                            if (dropped && target != source && !latestState.value.busy) {
                                viewModel.moveExercise(rowId, target)
                            }
                        } finally { reorder.cancel() }
                    }
                },
            state = listState,
            userScrollEnabled = reorder.exerciseId == null,
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "title") {
                Text(if (showHistory) "HevyGroovy" else "Workout", style = MaterialTheme.typography.headlineLarge)
                Text(if (showHistory) "Your workouts" else "One set at a time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (state.loading) {
                item(key = "loading") { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
            }
            state.error?.let { message ->
                item(key = "error") {
                    Card {
                        Column(Modifier.padding(16.dp)) {
                            Text(message, color = MaterialTheme.colorScheme.error)
                            TextButton(onClick = viewModel::refresh, enabled = !state.loading) { Text("Retry") }
                        }
                    }
                }
            }
            if (state.loaded) {
                item(key = "active-workout") {
                    if (active == null) {
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text("Ready for your next session?", style = MaterialTheme.typography.titleLarge)
                                Button(onClick = viewModel::startWorkout, enabled = !state.busy && state.error == null) {
                                    Text("Start empty workout")
                                }
                            }
                        }
                    } else if (reorder.exerciseId != null) {
                        Card(Modifier.fillMaxWidth()) {
                            Text("Drag to reorder · release to save", Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        ActiveWorkoutHeader(viewModel, active, onDiscard = { confirmDiscard = true },
                            hasUnsavedSets = hasUnsavedSets)
                    }
                }
                if (active != null) {
                    itemsIndexed(orderedExercises, key = { _, exercise -> "exercise-${exercise.workoutExerciseId}" }) { index, exercise ->
                        DisposableEffect(exercise.workoutExerciseId) {
                            onDispose { headerBounds.remove(exercise.workoutExerciseId) }
                        }
                        val name = exercise.exerciseNameSnapshot ?: "Exercise"
                        WorkoutExerciseCard(viewModel, exercise, index, active.exercises.size,
                            collapsed = reorder.exerciseId != null,
                            canPersist = { reorder.exerciseId == null && !suppressAccessibilityBlurSave },
                            onDraftChanged = { id, draft -> inlineDrafts[id] = draft },
                            reorderModifier = Modifier.testTag("reorder-exercise-${exercise.workoutExerciseId}")
                                .onGloballyPositioned { headerBounds[exercise.workoutExerciseId] = it.boundsInRoot() }
                                .background(if (reorder.exerciseId == exercise.workoutExerciseId)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface)
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
                if (showHistory) {
                item(key = "recent-heading") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Recent workouts", style = MaterialTheme.typography.titleLarge)
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
    }
    if (confirmDiscard && state.activeWorkout != null) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard workout?") },
            text = { Text("This removes this workout and all its sets. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; viewModel.discardWorkout() }, enabled = !state.busy) {
                    Text("Discard")
                }
            },
            dismissButton = { TextButton(onClick = { confirmDiscard = false }) { Text("Keep workout") } },
        )
    }
}

private class WorkoutExerciseReorder {
    var exerciseId by mutableStateOf<Long?>(null)
        private set
    var targetIndex by mutableIntStateOf(0)
        private set
    var pointerY = 0f
    private var sourceIndex = 0
    private var distance = 0f

    fun begin(id: Long, source: Int, y: Float) {
        exerciseId = id; sourceIndex = source; targetIndex = source; distance = 0f; pointerY = y
    }

    fun move(delta: Float, step: Float, count: Int) {
        if (exerciseId == null || count == 0) return
        distance += delta
        targetIndex = (sourceIndex + (distance / step).roundToInt()).coerceIn(0, count - 1)
    }

    fun cancel() { exerciseId = null; distance = 0f }
}

private val InlineSetDraftMapSaver = listSaver<SnapshotStateMap<Long, InlineSetDraft>, String>(
    save = { drafts -> drafts.entries.flatMap { (id, draft) ->
        listOf(id.toString(), draft.weightText, draft.repsText, draft.unit.name, draft.type.name)
    } },
    restore = { values -> mutableStateMapOf<Long, InlineSetDraft>().apply {
        values.chunked(5).forEach { fields ->
            put(fields[0].toLong(), InlineSetDraft(fields[1], fields[2],
                WeightUnit.valueOf(fields[3]), SetType.valueOf(fields[4])))
        }
    } },
)

package com.example.hevygroovy.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/** Drag uses root coordinates and measured card bounds; it never changes layout until drop. */
class ExerciseReorderState internal constructor() {
    var draggedId by mutableStateOf<Long?>(null)
        private set
    var insertionIndex by mutableIntStateOf(0)
        private set
    var pointer by mutableStateOf(Offset.Zero)
        private set
    var grabOffset = Offset.Zero
        private set
    var previewBounds = Rect.Zero
        private set
    internal var pendingOrder by mutableStateOf<List<Long>?>(null)
    var listBounds = Rect.Zero
    internal val headers = mutableMapOf<Long, Rect>()
    internal val cards = mutableMapOf<Long, Rect>()
    private var rows: List<Long> = emptyList()
    private var sourceIndex = 0
    private var moved = false
    private var start = Offset.Zero

    internal fun begin(id: Long, ids: List<Long>, point: Offset) {
        val header = headers[id] ?: return
        sourceIndex = ids.indexOf(id)
        if (sourceIndex < 0) return
        rows = ids.toList(); insertionIndex = sourceIndex
        grabOffset = point - header.topLeft; previewBounds = header
        pointer = point; start = point; moved = false; draggedId = id
    }

    internal fun move(point: Offset) {
        pointer = point
        if ((point - start).getDistance() > 2f) moved = true
        updateTarget()
    }

    internal fun updateTarget() {
        if (draggedId == null || !moved) return
        val visible = rows.mapIndexedNotNull { index, id -> cards[id]?.takeIf {
            it.bottom > listBounds.top && it.top < listBounds.bottom
        }?.let { index to it } }
        if (visible.isEmpty()) return
        insertionIndex = visible.firstOrNull { pointer.y < it.second.center.y }?.first
            ?: (visible.last().first + 1)
    }

    internal fun targetIndex(): Int = (insertionIndex - if (insertionIndex > sourceIndex) 1 else 0)
        .coerceIn(0, (rows.size - 1).coerceAtLeast(0))

    internal fun scrollVelocity(edge: Float, speed: Float): Float {
        if (!moved || draggedId == null) return 0f
        val band = edge.coerceAtMost(listBounds.height / 3).coerceAtLeast(1f)
        return when {
            pointer.y < listBounds.top + band -> -speed * ((listBounds.top + band - pointer.y) / band).coerceIn(0f, 1f)
            pointer.y > listBounds.bottom - band -> speed * ((pointer.y - listBounds.bottom + band) / band).coerceIn(0f, 1f)
            else -> 0f
        }
    }

    internal fun forget(id: Long) { headers.remove(id); cards.remove(id) }
    internal fun cancel() { draggedId = null; moved = false }
}

@Composable
fun rememberExerciseReorder(listState: LazyListState, key: Any?, rows: List<Long>, busy: Boolean): ExerciseReorderState {
    val state = remember(listState, key) { ExerciseReorderState() }
    val edge = with(LocalDensity.current) { 72.dp.toPx() }
    val speed = with(LocalDensity.current) { 720.dp.toPx() }
    LaunchedEffect(rows, busy) { if (!busy) state.pendingOrder = null }
    LaunchedEffect(state.draggedId) {
        var previous = withFrameNanos { it }
        while (state.draggedId != null) {
            val now = withFrameNanos { it }
            val elapsed = ((now - previous) / 1_000_000_000f).coerceAtMost(0.05f)
            previous = now
            val delta = state.scrollVelocity(edge, speed) * elapsed
            if (delta != 0f) {
                listState.scrollBy(delta)
                state.updateTarget()
            }
        }
    }
    return state
}

@Composable
fun Modifier.exerciseReorderGestures(state: ExerciseReorderState, rows: List<Long>, enabled: Boolean,
    onBegin: () -> Unit = {}, onMove: (Long, Int) -> Unit): Modifier {
    val currentRows = rememberUpdatedState(rows)
    val currentEnabled = rememberUpdatedState(enabled)
    val begin = rememberUpdatedState(onBegin)
    val move = rememberUpdatedState(onMove)
    val haptic = LocalHapticFeedback.current
    return onGloballyPositioned { state.listBounds = it.boundsInRoot() }.pointerInput(state) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            if (!currentEnabled.value) return@awaitEachGesture
            val root = down.position + state.listBounds.topLeft
            val id = state.headers.entries.firstOrNull { it.value.contains(root) }?.key ?: return@awaitEachGesture
            val pressed = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
            if (!currentEnabled.value) return@awaitEachGesture
            val originalRows = currentRows.value.toList()
            state.begin(id, originalRows, pressed.position + state.listBounds.topLeft)
            if (state.draggedId == null) return@awaitEachGesture
            begin.value()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            try {
                val dropped = drag(pressed.id) { change ->
                    if (change.positionChange() != Offset.Zero) state.move(change.position + state.listBounds.topLeft)
                    change.consume()
                }
                val target = state.targetIndex()
                val source = currentRows.value.indexOf(id)
                state.cancel()
                if (dropped && currentEnabled.value && source >= 0 && source != target && currentRows.value == originalRows) {
                    state.pendingOrder = originalRows.toMutableList().apply { add(target, removeAt(source)) }
                    move.value(id, target)
                }
            } finally { state.cancel() }
        }
    }
}

fun Modifier.exerciseReorderHeader(state: ExerciseReorderState, id: Long): Modifier =
    onGloballyPositioned { state.headers[id] = it.unclippedRootBounds() }.alpha(if (state.draggedId == id) 0.25f else 1f)

private fun LayoutCoordinates.unclippedRootBounds(): Rect {
    val origin = positionInRoot()
    return Rect(origin.x, origin.y, origin.x + size.width, origin.y + size.height)
}

@Composable
fun ExerciseReorderItem(state: ExerciseReorderState, id: Long, index: Int, count: Int, content: @Composable () -> Unit) {
    DisposableEffect(state, id) { onDispose { state.forget(id) } }
    val color = MaterialTheme.colorScheme.primary
    val before = state.draggedId != null && state.insertionIndex == index
    val after = state.draggedId != null && state.insertionIndex == count && index == count - 1
    Box(Modifier.fillMaxWidth().onGloballyPositioned { state.cards[id] = it.unclippedRootBounds() }
        .then(if (before || after) Modifier.testTag("reorder-drop-" + id) else Modifier)
        .drawWithContent {
            drawContent()
            if (before || after) {
                val y = if (before) -4.dp.toPx() else size.height + 4.dp.toPx()
                drawLine(color, Offset(6.dp.toPx(), y), Offset(size.width, y), 3.dp.toPx(), StrokeCap.Round)
                drawCircle(color, 4.dp.toPx(), Offset(4.dp.toPx(), y))
            }
        }) { content() }
}

@Composable
fun ExerciseDragHandle() {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    Canvas(Modifier.size(width = 18.dp, height = 24.dp)) {
        repeat(3) { row ->
            val y = size.height / 2 + (row - 1) * 5.dp.toPx()
            drawLine(ink, Offset(2.dp.toPx(), y), Offset(size.width - 2.dp.toPx(), y), 2.dp.toPx(), StrokeCap.Round)
        }
    }
}

@Composable
fun ExerciseDragPreview(state: ExerciseReorderState, title: String?) {
    if (state.draggedId == null || title == null) return
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().zIndex(2f)) {
        Surface(Modifier.offset {
            val position = state.pointer - state.grabOffset - state.listBounds.topLeft
            IntOffset(position.x.roundToInt(), position.y.roundToInt())
        }.width(with(density) { state.previewBounds.width.toDp() })
            .height(with(density) { state.previewBounds.height.toDp() })
            .testTag("exercise-drag-preview").semantics { contentDescription = "Moving " + title },
            shape = RoundedCornerShape(10.dp), shadowElevation = 10.dp, tonalElevation = 6.dp,
            color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ExerciseDragHandle()
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

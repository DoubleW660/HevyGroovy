package com.example.hevygroovy.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExerciseReorderStateTest {
    @Test fun previewKeepsTheExactGrabPointInsteadOfSnappingToCardCenter() {
        val state = fixture()
        state.begin(2, listOf(1, 2, 3), Offset(170f, 237f))
        assertEquals(Offset(150f, 17f), state.grabOffset)
        assertEquals(Offset(20f, 220f), state.pointer - state.grabOffset)
        state.move(Offset(182f, 320f))
        assertEquals(Offset(32f, 303f), state.pointer - state.grabOffset)
        assertEquals(Rect(20f, 220f, 380f, 270f), state.previewBounds)
    }

    @Test fun insertionUsesMeasuredCardCentersAcrossDifferentHeightsAndDoesNotMutateRows() {
        val state = fixture()
        val rows = listOf(1L, 2L, 3L)
        state.begin(1, rows, Offset(50f, 120f))
        assertEquals(0, state.targetIndex())
        state.move(Offset(50f, 635f))
        assertEquals(2, state.targetIndex())
        assertEquals(3, state.insertionIndex)
        assertEquals(listOf(1L, 2L, 3L), rows)
        state.cancel()
        state.begin(3, rows, Offset(50f, 620f))
        state.move(Offset(50f, 125f))
        assertEquals(0, state.targetIndex())
        state.cancel()
        assertNull(state.draggedId)
        assertNull(state.pendingOrder)
    }

    @Test fun edgeScrollingRequiresMovementAndUsesViewportRootCoordinates() {
        val state = fixture()
        state.begin(1, listOf(1, 2, 3), Offset(50f, 120f))
        assertEquals(0f, state.scrollVelocity(72f, 720f))
        state.move(Offset(50f, 100f))
        assertEquals(-720f, state.scrollVelocity(72f, 720f))
        state.move(Offset(50f, 900f))
        assertEquals(720f, state.scrollVelocity(72f, 720f))
        state.move(Offset(50f, 400f))
        assertEquals(0f, state.scrollVelocity(72f, 720f))
    }

    private fun fixture() = ExerciseReorderState().apply {
        listBounds = Rect(0f, 100f, 400f, 900f)
        headers[1] = Rect(20f, 110f, 380f, 160f)
        headers[2] = Rect(20f, 220f, 380f, 270f)
        headers[3] = Rect(20f, 610f, 380f, 660f)
        cards[1] = Rect(20f, 110f, 380f, 200f)
        cards[2] = Rect(20f, 220f, 380f, 590f)
        cards[3] = Rect(20f, 610f, 380f, 660f)
    }
}

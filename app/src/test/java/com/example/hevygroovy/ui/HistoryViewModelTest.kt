package com.example.hevygroovy.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import com.example.hevygroovy.app.HistoryGateway
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.WorkoutHistoryDetail
import java.time.Duration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class HistoryViewModelTest {
    @Test fun initialLoadIsExplicitAndDuplicateLoadsAreIgnored() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        assertTrue(gateway.pages.isEmpty())
        model.refresh(); model.refresh(); model.loadMore()
        assertEquals(listOf(0), gateway.pages.map { it.offset })
        assertTrue(model.state.loading)
        gateway.page(0, listOf(3, 2), false, 2)
        assertTrue(model.state.loaded)
        assertFalse(model.state.loading)
        model.loadMore()
        assertEquals(1, gateway.pages.size)
    }

    @Test fun paginationIgnoresDuplicateTapsDeduplicatesOverlapsAndUsesServerOffset() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.refresh(); gateway.page(0, listOf(9, 8), true, 20)
        model.loadMore(); model.loadMore()
        assertEquals(listOf(0, 20), gateway.pages.map { it.offset })
        gateway.page(1, listOf(8, 7), true, 40)
        assertEquals(listOf(9L, 8L, 7L), model.state.items.map { it.workoutId })
        model.loadMore()
        assertEquals(40, gateway.pages.last().offset)
        gateway.page(2, listOf(6), false, 41)
        assertFalse(model.state.hasMore)
    }

    @Test fun failedPagePreservesRowsAndRetryUsesSameOffset() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.refresh(); gateway.page(0, listOf(9, 8), true, 20)
        model.loadMore(); gateway.pages[1].callback.onFailure(IllegalStateException())
        assertEquals(2, model.state.items.size)
        assertNotNull(model.state.moreError)
        assertNull(model.state.error)
        assertFalse(model.state.loadingMore)
        model.loadMore()
        assertEquals(listOf(0, 20, 20), gateway.pages.map { it.offset })
        gateway.page(2, listOf(7), false, 21)
        assertNull(model.state.moreError)
        assertEquals(listOf(9L, 8L, 7L), model.state.items.map { it.workoutId })
    }

    @Test fun refreshReplacesPagesAndIgnoresOldPaginationSuccessOrFailure() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.refresh(); gateway.page(0, listOf(9, 8), true, 20)
        model.loadMore(); model.refresh()
        assertEquals(listOf(0, 20, 0), gateway.pages.map { it.offset })
        gateway.page(2, listOf(10, 9), true, 20)
        gateway.page(1, listOf(7, 6), false, 40)
        gateway.pages[1].callback.onFailure(IllegalStateException())
        assertEquals(listOf(10L, 9L), model.state.items.map { it.workoutId })
        assertNull(model.state.moreError)
        model.loadMore()
        assertEquals(20, gateway.pages.last().offset)
    }

    @Test fun failedRefreshKeepsPriorSnapshotAndCanBeRetried() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.refresh(); gateway.page(0, listOf(9), true, 20)
        model.refresh(); gateway.pages[1].callback.onFailure(IllegalStateException())
        assertEquals(9L, model.state.items.single().workoutId)
        assertNotNull(model.state.error)
        assertNull(model.state.moreError)
        model.refresh(); gateway.page(2, emptyList(), false, 0)
        assertTrue(model.state.items.isEmpty())
        assertNull(model.state.error)
        assertFalse(model.state.hasMore)
    }

    @Test fun detailRequestsIgnoreOldSuccessAndFailureAfterChangingSelection() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.openWorkout(9); model.openWorkout(9)
        assertEquals(1, gateway.details.size)
        model.openWorkout(8)
        gateway.details[0].callback.onSuccess(detail(9))
        gateway.details[0].callback.onFailure(IllegalStateException())
        assertEquals(8L, model.state.selectedWorkoutId)
        assertNull(model.state.detail)
        assertNull(model.state.detailError)
        assertTrue(model.state.detailLoading)
        gateway.details[1].callback.onSuccess(detail(8))
        assertEquals(8L, model.state.detail?.workoutId)
        assertFalse(model.state.detailLoading)
    }

    @Test fun closingDetailIgnoresPendingCallbacksAndClearsSavedSelection() {
        val gateway = FakeGateway()
        val handle = SavedStateHandle()
        val model = HistoryViewModel(gateway, handle)
        model.openWorkout(9); model.closeDetail()
        gateway.details.single().callback.onSuccess(detail(9))
        assertNull(model.state.selectedWorkoutId)
        assertNull(model.state.detail)
        assertNull(handle.get<Long>("historySelectedWorkoutId"))
    }

    @Test fun newViewModelRestoresSelectedWorkoutAndRefreshLoadsBothPageAndDetail() {
        val firstHandle = SavedStateHandle()
        HistoryViewModel(FakeGateway(), firstHandle).openWorkout(9)
        val restoredHandle = SavedStateHandle(firstHandle.keys().associateWith { firstHandle.get<Any?>(it) })
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway, restoredHandle)
        assertEquals(9L, model.state.selectedWorkoutId)
        assertTrue(gateway.details.isEmpty())
        model.refresh()
        assertEquals(9L, gateway.details.single().id)
        gateway.page(0, listOf(9), false, 1)
        gateway.details.single().callback.onSuccess(detail(9))
        assertEquals(9L, model.state.detail?.workoutId)
    }

    @Test fun detailFailureIsIndependentOfPageAndRetryIsGuarded() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        model.refresh(); gateway.page(0, listOf(9), false, 1)
        model.openWorkout(9)
        gateway.details[0].callback.onFailure(IllegalStateException())
        assertNotNull(model.state.detailError)
        assertNull(model.state.error)
        assertEquals(1, model.state.items.size)
        model.retryDetail(); model.retryDetail()
        assertEquals(2, gateway.details.size)
        gateway.details[1].callback.onSuccess(detail(9))
        assertNull(model.state.detailError)
        model.refresh()
        gateway.pages[1].callback.onFailure(IllegalStateException())
        assertEquals(9L, model.state.detail?.workoutId)
        gateway.details[2].callback.onSuccess(detail(9))
        assertNotNull(model.state.error)
    }

    @Test fun clearingViewModelIgnoresLateResults() {
        val gateway = FakeGateway()
        val model = HistoryViewModel(gateway)
        val store = ViewModelStore().apply { put("history", model) }
        model.refresh(); model.openWorkout(9)
        val before = model.state
        store.clear()
        gateway.page(0, listOf(9), false, 1)
        gateway.details[0].callback.onSuccess(detail(9))
        assertSame(before, model.state)
    }

    private class FakeGateway : HistoryGateway {
        data class PageRequest(val offset: Int, val callback: HistoryGateway.PageCallback)
        data class DetailRequest(val id: Long, val callback: HistoryGateway.DetailCallback)
        val pages = mutableListOf<PageRequest>()
        val details = mutableListOf<DetailRequest>()
        override fun loadPage(offset: Int, callback: HistoryGateway.PageCallback) { pages += PageRequest(offset, callback) }
        override fun loadDetail(workoutId: Long, callback: HistoryGateway.DetailCallback) { details += DetailRequest(workoutId, callback) }
        fun page(index: Int, ids: List<Int>, hasMore: Boolean, nextOffset: Int) {
            pages[index].callback.onSuccess(HistoryGateway.HistoryPage(ids.map { workout(it.toLong()) }, hasMore, nextOffset))
        }
    }

    private companion object {
        fun workout(id: Long) = WorkoutHistory(id, "Workout $id", 1_000, 61_000,
            Duration.ofMinutes(1), 1, 1, 5, 100.0)
        fun detail(id: Long) = WorkoutHistoryDetail(workout(id), "Notes", emptyList())
    }
}

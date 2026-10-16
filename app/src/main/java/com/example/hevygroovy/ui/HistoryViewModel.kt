package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.hevygroovy.app.HistoryGateway
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.WorkoutHistoryDetail

data class HistoryState(
    val items: List<WorkoutHistory> = emptyList(),
    val loaded: Boolean = false,
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: String? = null,
    val moreError: String? = null,
    val selectedWorkoutId: Long? = null,
    val detail: WorkoutHistoryDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
)

class HistoryViewModel(
    private val gateway: HistoryGateway,
    private val savedState: SavedStateHandle = SavedStateHandle(),
) : ViewModel() {
    var state by mutableStateOf(HistoryState(selectedWorkoutId = savedState[SELECTED_WORKOUT_ID]))
        private set

    private var nextOffset = 0
    private var pageGeneration = 0L
    private var detailGeneration = 0L
    private var cleared = false

    /** Refresh replaces all pages; older in-flight pagination results are ignored. */
    fun refresh() {
        if (cleared || state.loading) return
        val generation = ++pageGeneration
        state = state.copy(loading = true, loadingMore = false, error = null, moreError = null)
        loadPage(0, generation, append = false)
        state.selectedWorkoutId?.let { loadDetail(it) }
    }

    fun loadMore() {
        if (cleared || state.loading || state.loadingMore || !state.hasMore) return
        state = state.copy(loadingMore = true, moreError = null)
        loadPage(nextOffset, pageGeneration, append = true)
    }

    private fun loadPage(offset: Int, generation: Long, append: Boolean) {
        gateway.loadPage(offset, object : HistoryGateway.PageCallback {
            override fun onSuccess(page: HistoryGateway.HistoryPage) {
                if (cleared || generation != pageGeneration) return
                nextOffset = page.nextOffset
                // Offset follows the server's consumed rows, even when overlapping rows are deduplicated.
                val items = if (append) (state.items + page.items).distinctBy { it.workoutId } else page.items.toList()
                state = state.copy(items = items, loaded = true, loading = false, loadingMore = false,
                    hasMore = page.hasMore(), error = null, moreError = null)
            }

            override fun onFailure(error: Exception) {
                if (cleared || generation != pageGeneration) return
                val message = "Couldn't load workout history. Please try again."
                state = state.copy(loading = false, loadingMore = false,
                    error = if (append) state.error else message,
                    moreError = if (append) message else null)
            }
        })
    }

    fun openWorkout(id: Long) {
        if (cleared || id <= 0) return
        if (state.selectedWorkoutId == id && (state.detailLoading || state.detail != null)) return
        savedState[SELECTED_WORKOUT_ID] = id
        state = state.copy(selectedWorkoutId = id, detail = null, detailError = null)
        loadDetail(id)
    }

    fun closeDetail() {
        ++detailGeneration
        savedState[SELECTED_WORKOUT_ID] = null
        state = state.copy(selectedWorkoutId = null, detail = null, detailLoading = false, detailError = null)
    }

    fun retryDetail() {
        if (cleared || state.detailLoading) return
        state.selectedWorkoutId?.let { loadDetail(it) }
    }

    private fun loadDetail(id: Long) {
        val generation = ++detailGeneration
        state = state.copy(detailLoading = true, detailError = null)
        gateway.loadDetail(id, object : HistoryGateway.DetailCallback {
            override fun onSuccess(detail: WorkoutHistoryDetail) {
                if (cleared || generation != detailGeneration || state.selectedWorkoutId != id) return
                state = state.copy(detail = detail, detailLoading = false, detailError = null)
            }

            override fun onFailure(error: Exception) {
                if (cleared || generation != detailGeneration || state.selectedWorkoutId != id) return
                state = state.copy(detailLoading = false,
                    detailError = "Couldn't load this workout. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true }

    class Factory(private val gateway: HistoryGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == HistoryViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(gateway, extras.createSavedStateHandle()) as T
        }
    }

    private companion object { const val SELECTED_WORKOUT_ID = "historySelectedWorkoutId" }
}

package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.dto.WorkoutHistory

data class WorkoutHomeState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val activeWorkout: ActiveWorkoutDetail? = null,
    val history: List<WorkoutHistory> = emptyList(),
    val error: String? = null,
)

class WorkoutHomeViewModel(private val gateway: WorkoutGateway) : ViewModel() {
    var state by mutableStateOf(WorkoutHomeState())
        private set
    private var cleared = false
    private var requestInFlight = false

    init { refresh() }

    fun refresh() {
        // Initial load is allowed; subsequent calls are serialized by the loading flag.
        if (state.loaded && state.loading) return
        execute { gateway.load(it) }
    }

    fun startWorkout() {
        if (!state.loaded || state.loading || state.activeWorkout != null) return
        execute { gateway.startEmpty(it) }
    }

    fun discardWorkout() {
        if (state.loading) return
        val id = state.activeWorkout?.workoutId ?: return
        execute { gateway.discard(id, it) }
    }

    private fun execute(operation: (WorkoutGateway.Callback) -> Unit) {
        if (requestInFlight || cleared) return
        requestInFlight = true
        state = state.copy(loading = true, error = null)
        operation(object : WorkoutGateway.Callback {
            override fun onSuccess(snapshot: WorkoutGateway.Snapshot) {
                requestInFlight = false
                if (cleared) return
                state = WorkoutHomeState(
                    loading = false,
                    loaded = true,
                    activeWorkout = snapshot.activeWorkout,
                    history = snapshot.history.toList(),
                )
            }

            override fun onFailure(error: Exception) {
                requestInFlight = false
                if (cleared) return
                state = state.copy(loading = false, error = "Couldn't update workouts. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true }

    class Factory(private val gateway: WorkoutGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == WorkoutHomeViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return WorkoutHomeViewModel(gateway) as T
        }
    }
}

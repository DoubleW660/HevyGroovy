package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.ExerciseOption
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.entity.enums.MuscleGroup

data class WorkoutHomeState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val activeWorkout: ActiveWorkoutDetail? = null,
    val history: List<WorkoutHistory> = emptyList(),
    val exercises: List<ExerciseOption> = emptyList(),
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

    fun finishWorkout() {
        val active = state.activeWorkout ?: return
        if (state.loading) return
        if (active.exercises.none { exercise -> exercise.sets.any { it.isCompleted } }) {
            state = state.copy(error = "Complete at least one set before finishing.")
            return
        }
        execute { gateway.finish(active.workoutId, it) }
    }

    fun updateWorkout(name: String, notes: String) {
        val id = state.activeWorkout?.workoutId ?: return
        if (name.isBlank()) { state = state.copy(error = "Enter a workout name."); return }
        execute { gateway.updateWorkout(id, name, notes, it) }
    }

    fun createExercise(title: String, muscle: MuscleGroup) {
        if (title.isBlank()) { state = state.copy(error = "Enter an exercise name."); return }
        execute { gateway.createExercise(title, muscle, it) }
    }

    fun addExercise(exerciseId: Long) {
        val id = state.activeWorkout?.workoutId ?: return
        execute { gateway.addExercise(id, exerciseId, it) }
    }

    fun removeExercise(rowId: Long) = withExercise(rowId) { gateway.removeExercise(rowId, it) }

    fun moveExercise(rowId: Long, position: Int) {
        val active = state.activeWorkout ?: return
        if (position !in active.exercises.indices) return
        withExercise(rowId) { gateway.moveExercise(rowId, position, it) }
    }

    fun updateExercise(rowId: Long, notes: String, restSeconds: Int) {
        if (restSeconds < 0) { state = state.copy(error = "Rest time cannot be negative."); return }
        withExercise(rowId) { gateway.updateExercise(rowId, notes, restSeconds, it) }
    }

    fun addSet(rowId: Long) = withExercise(rowId) { gateway.addSet(rowId, it) }

    fun updateSet(setId: Long, request: UpdateSetEntryRequest) {
        if (state.activeWorkout?.exercises?.none { row -> row.sets.any { it.id == setId } } != false) return
        execute { gateway.updateSet(setId, request, it) }
    }

    fun deleteSet(setId: Long) {
        if (state.activeWorkout?.exercises?.none { row -> row.sets.any { it.id == setId } } != false) return
        execute { gateway.deleteSet(setId, it) }
    }

    private fun withExercise(rowId: Long, action: (WorkoutGateway.Callback) -> Unit) {
        if (state.activeWorkout?.exercises?.none { it.workoutExerciseId == rowId } != false) return
        execute(action)
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
                    exercises = snapshot.exercises.toList(),
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

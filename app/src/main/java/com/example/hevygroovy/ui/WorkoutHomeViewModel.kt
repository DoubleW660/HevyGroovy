package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.app.WorkoutRestTimer
import com.example.hevygroovy.app.CommittedRefreshException
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.dto.WorkoutHistory
import com.example.hevygroovy.dto.ExerciseOption
import com.example.hevygroovy.dto.UpdateSetEntryRequest
import com.example.hevygroovy.dto.PersonalRecord
import com.example.hevygroovy.entity.enums.RecordType
import com.example.hevygroovy.entity.enums.MuscleGroup

data class WorkoutHomeState(
    val loading: Boolean = true,
    val loaded: Boolean = false,
    val activeWorkout: ActiveWorkoutDetail? = null,
    val history: List<WorkoutHistory> = emptyList(),
    val exercises: List<ExerciseOption> = emptyList(),
    val activeRecords: List<PersonalRecord> = emptyList(),
    val recordNotice: List<PersonalRecord> = emptyList(),
    val recordNoticeVersion: Long = 0,
    val savedChanges: Long = 0,
    val refreshRequired: Boolean = false,
    val error: String? = null,
) {
    val busy: Boolean get() = loading || refreshRequired
}

class WorkoutHomeViewModel(private val gateway: WorkoutGateway,
    val performanceTiming: UiPerformanceTiming = UiPerformanceTiming(),
    private val restTimer: WorkoutRestTimer = WorkoutRestTimer.None) : ViewModel() {
    var state by mutableStateOf(WorkoutHomeState())
        private set
    private var cleared = false
    private var requestInFlight = false
    private val observedRecords = mutableMapOf<Pair<Long, RecordType>, Double>()

    init { refresh() }

    fun refresh() {
        // Initial load is allowed; subsequent calls are serialized by the loading flag.
        if (state.loaded && state.loading) return
        execute(mutation = false) { gateway.load(it) }
    }

    fun startWorkout() {
        if (!state.loaded || state.loading || state.activeWorkout != null) return
        execute { gateway.startEmpty(it) }
    }

    fun startFromTemplate(templateId: Long) {
        if (!state.loaded || state.loading || state.activeWorkout != null || templateId <= 0) return
        execute { gateway.startFromTemplate(templateId, it) }
    }

    fun discardWorkout() {
        if (state.loading) return
        val id = state.activeWorkout?.workoutId ?: return
        execute(onCommitted = { restTimer.cancelForWorkout(id) }) { gateway.discard(id, it) }
    }

    fun finishWorkout() {
        val active = state.activeWorkout ?: return
        if (state.loading) return
        if (active.exercises.none { exercise -> exercise.sets.any { it.isCompleted } }) {
            state = state.copy(error = "Complete at least one set before finishing.")
            return
        }
        execute(onCommitted = { restTimer.cancelForWorkout(active.workoutId) }) { gateway.finish(active.workoutId, it) }
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

    fun createExercise(request: com.example.hevygroovy.dto.CreateExerciseRequest) {
        if (request.title.isNullOrBlank()) { state = state.copy(error = "Enter an exercise name."); return }
        execute { gateway.createExercise(request, it) }
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
        val active = state.activeWorkout ?: return
        val exercise = active.exercises.find { row -> row.sets.any { it.id == setId } } ?: return
        val set = exercise.sets.first { it.id == setId }
        val startsRest = request.completed == true && !set.isCompleted
        execute(traceName = "set_save", announceRecords = true, onCommitted = {
            if (startsRest) restTimer.start(active.workoutId, exercise.workoutExerciseId, setId,
                exercise.exerciseNameSnapshot ?: "Exercise", exercise.restTimeSeconds)
        }) { gateway.updateSet(setId, request, it) }
    }

    fun dismissRecordNotice() { state = state.copy(recordNotice = emptyList()) }

    fun deleteSet(setId: Long) {
        if (state.activeWorkout?.exercises?.none { row -> row.sets.any { it.id == setId } } != false) return
        execute { gateway.deleteSet(setId, it) }
    }

    private fun withExercise(rowId: Long, action: (WorkoutGateway.Callback) -> Unit) {
        if (state.activeWorkout?.exercises?.none { it.workoutExerciseId == rowId } != false) return
        execute(operation = action)
    }

    private fun execute(mutation: Boolean = true, traceName: String = if (mutation) "workout_update" else "workout_load",
        announceRecords: Boolean = false,
        onCommitted: () -> Unit = {},
        operation: (WorkoutGateway.Callback) -> Unit) {
        if (requestInFlight || cleared || (mutation && state.refreshRequired)) return
        performanceTiming.begin(traceName)
        requestInFlight = true
        state = state.copy(loading = true, error = null)
        operation(object : WorkoutGateway.Callback {
            override fun onSuccess(snapshot: WorkoutGateway.Snapshot) {
                requestInFlight = false
                if (mutation) onCommitted()
                restTimer.reconcileWorkout(snapshot.activeWorkout?.workoutId)
                if (cleared) return
                val sameWorkout = state.activeWorkout?.workoutId == snapshot.activeWorkout?.workoutId
                val previousModes = state.activeWorkout?.exercises?.associate { it.exerciseId to it.isUnilateral }
                val currentModes = snapshot.activeWorkout?.exercises?.associate { it.exerciseId to it.isUnilateral }
                if (!sameWorkout || previousModes != currentModes) observedRecords.clear()
                val notices = if (announceRecords && sameWorkout && snapshot.activeWorkout != null) {
                    snapshot.activeRecords.filter { record ->
                        record.value > (observedRecords[record.exerciseId to record.type] ?: 0.0) + 0.000001
                    }
                } else emptyList()
                snapshot.activeRecords.forEach { record ->
                    val key = record.exerciseId to record.type
                    observedRecords[key] = maxOf(observedRecords[key] ?: 0.0, record.value)
                }
                state = WorkoutHomeState(
                    loading = false,
                    loaded = true,
                    activeWorkout = snapshot.activeWorkout,
                    history = snapshot.history.toList(),
                    exercises = snapshot.exercises.toList(),
                    activeRecords = snapshot.activeRecords.toList(),
                    recordNotice = notices,
                    recordNoticeVersion = state.recordNoticeVersion + if (notices.isNotEmpty()) 1 else 0,
                    savedChanges = state.savedChanges + if (mutation) 1 else 0,
                )
                performanceTiming.ready()
            }

            override fun onFailure(error: Exception) {
                requestInFlight = false
                performanceTiming.cancel()
                val committed = mutation && error is CommittedRefreshException
                if (committed) onCommitted()
                if (cleared) return
                state = state.copy(loading = false, refreshRequired = state.refreshRequired || committed,
                    savedChanges = state.savedChanges + if (committed) 1 else 0,
                    error = if (state.refreshRequired || committed) "Saved. Reload workouts before making more changes."
                        else if (mutation) "Couldn't save changes. Your draft is still here. Please try again."
                        else "Couldn't load workouts. Please try again.")
            }
        })
    }

    override fun onCleared() { cleared = true; performanceTiming.cancel() }

    class Factory(private val gateway: WorkoutGateway,
        private val timing: UiPerformanceTiming = UiPerformanceTiming(),
        private val restTimer: WorkoutRestTimer = WorkoutRestTimer.None) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass == WorkoutHomeViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return WorkoutHomeViewModel(gateway, timing, restTimer) as T
        }
    }
}

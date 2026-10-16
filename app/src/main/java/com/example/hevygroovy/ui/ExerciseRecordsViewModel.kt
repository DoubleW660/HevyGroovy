package com.example.hevygroovy.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.hevygroovy.app.RecordGateway
import com.example.hevygroovy.dto.PersonalRecord

data class ExerciseRecordsState(
    val selectedExerciseId: Long? = null,
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val records: List<PersonalRecord> = emptyList(),
    val estimatedOneRepMaxKg: Double = 0.0,
    val error: String? = null,
)

class ExerciseRecordsViewModel(private val gateway: RecordGateway,
    private val savedState: SavedStateHandle = SavedStateHandle()) : ViewModel() {
    var state by mutableStateOf(ExerciseRecordsState(selectedExerciseId = savedState[SELECTED_ID]))
        private set
    private var generation = 0L
    private var cleared = false

    fun refresh(exerciseId: Long) {
        if (cleared || exerciseId <= 0 || (state.loading && state.selectedExerciseId == exerciseId)) return
        val request = ++generation
        savedState[SELECTED_ID] = exerciseId
        state = ExerciseRecordsState(selectedExerciseId = exerciseId, loading = true)
        gateway.loadExercise(exerciseId, object : RecordGateway.Callback {
            override fun onSuccess(snapshot: RecordGateway.Snapshot) {
                if (cleared || request != generation) return
                state = state.copy(loading = false, loaded = true, records = snapshot.records.toList(),
                    estimatedOneRepMaxKg = snapshot.estimatedOneRepMaxKg)
            }
            override fun onFailure(error: Exception) {
                if (cleared || request != generation) return
                state = state.copy(loading = false, error = "Couldn't load your records. Please try again.")
            }
        })
    }

    fun retry() { state.selectedExerciseId?.let(::refresh) }
    fun close() {
        ++generation
        savedState[SELECTED_ID] = null
        state = ExerciseRecordsState()
    }
    override fun onCleared() { cleared = true; ++generation }

    class Factory(private val gateway: RecordGateway) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            require(modelClass == ExerciseRecordsViewModel::class.java)
            @Suppress("UNCHECKED_CAST")
            return ExerciseRecordsViewModel(gateway, extras.createSavedStateHandle()) as T
        }
    }
    private companion object { const val SELECTED_ID = "selectedExerciseRecordId" }
}

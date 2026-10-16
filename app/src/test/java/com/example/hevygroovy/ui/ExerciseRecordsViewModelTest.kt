package com.example.hevygroovy.ui

import androidx.lifecycle.SavedStateHandle
import com.example.hevygroovy.app.RecordGateway
import com.example.hevygroovy.dto.PersonalRecord
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExerciseRecordsViewModelTest {
    @Test fun switchingExercisesIgnoresLateResultsAndClosePreventsReopening() {
        val gateway = FakeGateway()
        val model = ExerciseRecordsViewModel(gateway)
        model.refresh(1)
        model.refresh(2)
        gateway.callbacks[0].onSuccess(emptyList())
        assertEquals(2L, model.state.selectedExerciseId)
        assertTrue(model.state.loading)
        model.close()
        gateway.callbacks[1].onSuccess(emptyList())
        assertNull(model.state.selectedExerciseId)
        assertFalse(model.state.loaded)
    }

    @Test fun failuresCanBeRetriedAndRepeatedLoadingTapsDoNotDuplicateRequests() {
        val gateway = FakeGateway()
        val model = ExerciseRecordsViewModel(gateway)
        model.refresh(1); model.refresh(1)
        assertEquals(listOf(1L), gateway.ids)
        gateway.callbacks[0].onFailure(IllegalStateException("Unavailable"))
        assertNotNull(model.state.error)
        model.retry()
        gateway.callbacks[1].onSuccess(emptyList())
        assertTrue(model.state.loaded)
        assertNull(model.state.error)
    }

    @Test fun savedSelectionRestoresWithoutLoadingUntilTheScreenIsVisible() {
        val saved = SavedStateHandle()
        val gateway = FakeGateway()
        val first = ExerciseRecordsViewModel(gateway, saved)
        first.refresh(12)
        val restored = ExerciseRecordsViewModel(gateway, saved)
        assertEquals(12L, restored.state.selectedExerciseId)
        assertFalse(restored.state.loading)
        assertEquals(1, gateway.ids.size)
        restored.close()
        assertNull(ExerciseRecordsViewModel(gateway, saved).state.selectedExerciseId)
    }

    private class FakeGateway : RecordGateway {
        val ids = mutableListOf<Long>()
        val callbacks = mutableListOf<RecordGateway.Callback>()
        override fun loadExercise(exerciseId: Long, callback: RecordGateway.Callback) {
            ids += exerciseId; callbacks += callback
        }
    }
}

package com.example.hevygroovy.ui

import androidx.lifecycle.SavedStateHandle
import com.example.hevygroovy.app.RecordGateway
import com.example.hevygroovy.dto.CatalogExercise
import com.example.hevygroovy.dto.ExercisePerformance
import com.example.hevygroovy.entity.SetEntry
import com.example.hevygroovy.entity.enums.SetType
import com.example.hevygroovy.entity.enums.Unit
import com.example.hevygroovy.model.ExerciseModel
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ExerciseRecordsViewModelTest {
    @Test fun detailHistoryAndSetRecordsLoadTogetherAndNeverLeakAcrossExerciseSelections() {
        val gateway = FakeGateway()
        val model = ExerciseRecordsViewModel(gateway)
        val exercise = CatalogExercise(ExerciseModel().apply {
            exerciseId = 1; title = "Bench"; description = "Saved guide"
        })
        val set = SetEntry().apply {
            id = 10; reps = 5; weight = 60.0; unit = Unit.KG
            setType = SetType.NORMAL; isCompleted = true
        }
        val history = ExercisePerformance(7, 1, "Bench", "Push", 100, 200, listOf(set), null, false)
        model.refresh(1)
        gateway.callbacks[0].onSuccess(RecordGateway.Snapshot(emptyList(), 70.0, exercise, listOf(history)))
        assertEquals("Saved guide", model.state.exercise?.description)
        assertEquals(7L, model.state.history.single().workoutId)
        assertEquals(5, model.state.repRecords.single().reps)
        model.refresh(2)
        assertNull(model.state.exercise)
        assertTrue(model.state.history.isEmpty())
        assertTrue(model.state.repRecords.isEmpty())
        gateway.callbacks[0].onSuccess(RecordGateway.Snapshot(emptyList(), 70.0, exercise, listOf(history)))
        assertNull(model.state.exercise)
        gateway.callbacks[1].onSuccess(RecordGateway.Snapshot(emptyList(), 0.0))
        assertTrue(model.state.loaded)
        assertTrue(model.state.history.isEmpty())
    }

    @Test fun switchingExercisesIgnoresLateResultsAndClosePreventsReopening() {
        val gateway = FakeGateway()
        val model = ExerciseRecordsViewModel(gateway)
        model.refresh(1)
        model.refresh(2)
        gateway.callbacks[0].onSuccess(RecordGateway.Snapshot(emptyList(), 65.0))
        assertEquals(2L, model.state.selectedExerciseId)
        assertTrue(model.state.loading)
        model.close()
        gateway.callbacks[1].onSuccess(RecordGateway.Snapshot(emptyList(), 80.0))
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
        gateway.callbacks[1].onSuccess(RecordGateway.Snapshot(emptyList(), 70.0))
        assertTrue(model.state.loaded)
        assertNull(model.state.error)
        assertEquals(70.0, model.state.estimatedOneRepMaxKg)
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

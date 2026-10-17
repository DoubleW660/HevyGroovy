package com.example.hevygroovy.ui

import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.entity.LoggedWorkout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkoutHomeViewModelTest {
    @Test
    fun changingMovementTypeResetsVolumeNoticeBaselineWithoutAnnouncingTheSettingChange() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(true, true), listOf(volumeRecord(1000.0)))
        model.refresh()
        gateway.succeed(activeWithSet(true, false), listOf(volumeRecord(500.0)))
        assertEquals(0L, model.state.recordNoticeVersion)
        model.updateSet(50, com.example.hevygroovy.dto.UpdateSetEntryRequest().apply { completed = true })
        gateway.succeed(activeWithSet(true, false), listOf(volumeRecord(600.0)))
        assertEquals(1L, model.state.recordNoticeVersion)
        assertEquals(600.0, model.state.recordNotice.single().value)
    }

    private fun volumeRecord(value: Double) = com.example.hevygroovy.dto.PersonalRecord(
        com.example.hevygroovy.entity.enums.RecordType.WORKOUT_VOLUME,
        1L, "Bench", 100L, 10L, 0L, value, 0.0,
        com.example.hevygroovy.entity.enums.Unit.KG, 8, 1000L)
    @Test
    fun recordsAnnounceOnlyHigherSuccessfulSetSavesAndDoNotRepeatAfterUndo() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(false))
        val request = com.example.hevygroovy.dto.UpdateSetEntryRequest().apply { completed = true }
        model.updateSet(50, request)
        gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        assertEquals(1L, model.state.recordNoticeVersion)
        assertEquals(100.0, model.state.recordNotice.single().value)
        model.dismissRecordNotice()
        model.updateSet(50, request)
        gateway.succeed(activeWithSet(false))
        assertTrue(model.state.activeRecords.isEmpty())
        model.updateSet(50, request)
        gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        assertEquals(1L, model.state.recordNoticeVersion)
        assertTrue(model.state.recordNotice.isEmpty())
        model.updateSet(50, request)
        gateway.succeed(activeWithSet(true), listOf(record(102.5)))
        assertEquals(2L, model.state.recordNoticeVersion)
        assertEquals(102.5, model.state.recordNotice.single().value)
    }

    @Test
    fun loadingExistingRecordsAndResavingAnUnchangedSetNeverReplaysACelebration() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        assertEquals(0L, model.state.recordNoticeVersion)
        assertTrue(model.state.recordNotice.isEmpty())
        model.refresh()
        gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        model.updateSet(50, com.example.hevygroovy.dto.UpdateSetEntryRequest().apply { completed = true })
        gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        assertEquals(0L, model.state.recordNoticeVersion)
        assertTrue(model.state.recordNotice.isEmpty())
    }

    @Test
    fun failedSaveDoesNotAnnounceOrAdvanceTheRecordWatermark() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(false))
        val request = com.example.hevygroovy.dto.UpdateSetEntryRequest().apply { completed = true }
        model.updateSet(50, request); gateway.fail()
        assertEquals(0L, model.state.recordNoticeVersion)
        assertTrue(model.state.activeRecords.isEmpty())
        model.updateSet(50, request); gateway.succeed(activeWithSet(true), listOf(record(100.0)))
        assertEquals(1L, model.state.recordNoticeVersion)
    }

    private fun record(value: Double) = com.example.hevygroovy.dto.PersonalRecord(
        com.example.hevygroovy.entity.enums.RecordType.HEAVIEST_WEIGHT,
        1L, "Bench", 100L, 10L, 50L, value, value,
        com.example.hevygroovy.entity.enums.Unit.KG, 8, 1000L)

    @Test
    fun committedWriteClosesFormOnceAndBlocksMutationsUntilReadRecoverySucceeds() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(true))
        model.finishWorkout()
        gateway.fail(com.example.hevygroovy.app.CommittedRefreshException(IllegalStateException("Read failed"), null))
        assertEquals(1L, model.state.savedChanges)
        assertTrue(model.state.busy)
        model.finishWorkout(); model.discardWorkout(); model.addSet(10)
        assertEquals(listOf("load", "finish:100"), gateway.calls)
        model.refresh(); gateway.fail()
        assertTrue(model.state.refreshRequired)
        model.refresh(); gateway.succeed()
        assertFalse(model.state.busy)
        assertNull(model.state.activeWorkout)
        assertEquals(1L, model.state.savedChanges)
    }
    @Test
    fun finishRequiresCompletedSetAndThenCallsGatewayOnce() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(false))
        model.finishWorkout()
        assertEquals(listOf("load"), gateway.calls)
        assertNotNull(model.state.error)
        model.refresh()
        gateway.succeed(activeWithSet(true))
        model.finishWorkout()
        model.finishWorkout()
        assertEquals(listOf("load", "load", "finish:100"), gateway.calls)
        gateway.succeed()
        assertNull(model.state.activeWorkout)
    }

    @Test
    fun setEditsRejectUnknownIdsAndPreventDuplicateSaves() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(false))
        val request = com.example.hevygroovy.dto.UpdateSetEntryRequest().apply { reps = 8; completed = true }
        model.updateSet(999, request)
        assertEquals(listOf("load"), gateway.calls)
        model.updateSet(50, request)
        model.updateSet(50, request)
        assertEquals(listOf("load", "set:50"), gateway.calls)
        assertSame(request, gateway.lastRequest)
    }

    @Test
    fun exerciseEditsRejectUnknownRowsAndInvalidMovePositions() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(activeWithSet(false))
        model.addSet(999)
        model.removeExercise(999)
        model.moveExercise(10, -1)
        model.moveExercise(10, 1)
        assertEquals(listOf("load"), gateway.calls)
        model.addSet(10)
        assertEquals("addSet:10", gateway.calls.last())
    }

    private fun activeWithSet(completed: Boolean, unilateral: Boolean = false): ActiveWorkoutDetail {
        val set = com.example.hevygroovy.entity.SetEntry().apply { id = 50L; setCompleted(completed) }
        val row = com.example.hevygroovy.entity.WorkoutExercise().apply { id = 10L; setUnilateral(unilateral) }
        val workout = LoggedWorkout().apply { id = 100L; userId = 1L }
        return ActiveWorkoutDetail(workout, listOf(com.example.hevygroovy.dto.ActiveWorkoutExercise(
            row, listOf(com.example.hevygroovy.dto.ActiveSetEntry(set)))))
    }
    @Test
    fun recreationReusesViewModelWithoutRepeatingInitialLoad() {
        val gateway = FakeGateway()
        val store = androidx.lifecycle.ViewModelStore()
        val factory = WorkoutHomeViewModel.Factory(gateway)
        val first = androidx.lifecycle.ViewModelProvider(store, factory)[WorkoutHomeViewModel::class.java]
        gateway.succeed(active())
        val recreated = androidx.lifecycle.ViewModelProvider(store, factory)[WorkoutHomeViewModel::class.java]
        assertSame(first, recreated)
        assertEquals(100L, recreated.state.activeWorkout?.workoutId)
        assertEquals(listOf("load"), gateway.calls)
        store.clear()
    }

    @Test
    fun clearedViewModelIgnoresLateCallback() {
        val gateway = FakeGateway()
        val store = androidx.lifecycle.ViewModelStore()
        val model = androidx.lifecycle.ViewModelProvider(store, WorkoutHomeViewModel.Factory(gateway))
            .get(WorkoutHomeViewModel::class.java)
        store.clear()
        gateway.succeed(active())
        assertFalse(model.state.loaded)
    }
    @Test
    fun initialLoadAndRefreshIgnoreRepeatedTapsWhileLoading() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        assertTrue(model.state.loading)
        model.refresh()
        model.startWorkout()
        assertEquals(listOf("load"), gateway.calls)
        gateway.succeed()
        assertFalse(model.state.loading)
        assertTrue(model.state.loaded)
    }

    @Test
    fun failedInitialLoadCanBeRetried() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.fail()
        assertFalse(model.state.loaded)
        assertNotNull(model.state.error)
        assertEquals(0L, model.state.savedChanges)
        model.refresh()
        gateway.succeed()
        assertNull(model.state.error)
        assertTrue(model.state.loaded)
    }

    @Test
    fun startingWorkoutPreventsDuplicateMutationsAndRestoresSavedSnapshot() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed()
        model.startWorkout()
        model.startWorkout()
        assertEquals(listOf("load", "start"), gateway.calls)
        gateway.succeed(active())
        assertEquals(100L, model.state.activeWorkout?.workoutId)
        model.startWorkout()
        assertEquals(2, gateway.calls.size)
    }

    @Test
    fun discardUsesRestoredWorkoutIdAndClearsStateOnSuccess() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(active())
        model.discardWorkout()
        assertEquals("discard:100", gateway.calls.last())
        gateway.succeed()
        assertNull(model.state.activeWorkout)
    }

    @Test
    fun mutationFailurePreservesLastLoadedSession() {
        val gateway = FakeGateway()
        val model = WorkoutHomeViewModel(gateway)
        gateway.succeed(active())
        model.discardWorkout()
        gateway.fail()
        assertEquals(100L, model.state.activeWorkout?.workoutId)
        assertFalse(model.state.loading)
        assertNotNull(model.state.error)
    }

    private fun active(): ActiveWorkoutDetail {
        val workout = LoggedWorkout().apply { id = 100L; userId = 1L }
        return ActiveWorkoutDetail(workout, emptyList())
    }

    private class FakeGateway : WorkoutGateway by org.mockito.Mockito.mock(WorkoutGateway::class.java) {
        var lastRequest: com.example.hevygroovy.dto.UpdateSetEntryRequest? = null
        override fun finish(workoutId: Long, callback: WorkoutGateway.Callback) {
            calls += "finish:$workoutId"; this.callback = callback
        }
        override fun updateSet(setId: Long, request: com.example.hevygroovy.dto.UpdateSetEntryRequest, callback: WorkoutGateway.Callback) {
            calls += "set:$setId"; lastRequest = request; this.callback = callback
        }
        override fun addSet(workoutExerciseId: Long, callback: WorkoutGateway.Callback) {
            calls += "addSet:$workoutExerciseId"; this.callback = callback
        }
        val calls = mutableListOf<String>()
        private lateinit var callback: WorkoutGateway.Callback
        override fun load(callback: WorkoutGateway.Callback) { calls += "load"; this.callback = callback }
        override fun startEmpty(callback: WorkoutGateway.Callback) { calls += "start"; this.callback = callback }
        override fun discard(workoutId: Long, callback: WorkoutGateway.Callback) {
            calls += "discard:$workoutId"; this.callback = callback
        }
        fun succeed(active: ActiveWorkoutDetail? = null, records: List<com.example.hevygroovy.dto.PersonalRecord> = emptyList()) {
            callback.onSuccess(WorkoutGateway.Snapshot(active, emptyList(), emptyList(), records))
        }
        fun fail(error: Exception = IllegalStateException("Test failure")) { callback.onFailure(error) }
    }
}

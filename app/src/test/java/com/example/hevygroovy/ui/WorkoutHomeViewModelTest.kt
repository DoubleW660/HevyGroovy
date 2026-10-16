package com.example.hevygroovy.ui

import com.example.hevygroovy.app.WorkoutGateway
import com.example.hevygroovy.dto.ActiveWorkoutDetail
import com.example.hevygroovy.entity.LoggedWorkout
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class WorkoutHomeViewModelTest {
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

    private class FakeGateway : WorkoutGateway {
        val calls = mutableListOf<String>()
        private lateinit var callback: WorkoutGateway.Callback
        override fun load(callback: WorkoutGateway.Callback) { calls += "load"; this.callback = callback }
        override fun startEmpty(callback: WorkoutGateway.Callback) { calls += "start"; this.callback = callback }
        override fun discard(workoutId: Long, callback: WorkoutGateway.Callback) {
            calls += "discard:$workoutId"; this.callback = callback
        }
        fun succeed(active: ActiveWorkoutDetail? = null) {
            callback.onSuccess(WorkoutGateway.Snapshot(active, emptyList()))
        }
        fun fail() { callback.onFailure(IllegalStateException("Test failure")) }
    }
}

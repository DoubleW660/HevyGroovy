package com.example.hevygroovy.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import com.example.hevygroovy.app.WorkoutGateway
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito

class UiPerformanceTimingTest {
    @Test
    fun drawingSuccessfulResultClosesMatchingTraceExactlyOnce() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        timing.begin("SaveSet")
        val operation = requireNotNull(timing.pending)

        timing.ready()
        timing.drawn(operation.cookie)
        timing.drawn(operation.cookie)
        timing.cancel()
        timing.ready()

        assertEquals(listOf(Event(true, "SaveSet", operation.cookie), Event(false, "SaveSet", operation.cookie)), sink.events)
        assertTrue(timing.idle)
    }

    @Test
    fun successfulCallbackKeepsTraceOpenUntilResultIsDrawn() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        timing.begin("LoadLibrary")
        val operation = requireNotNull(timing.pending)

        timing.ready()
        timing.ready()

        assertFalse(timing.idle)
        assertTrue(requireNotNull(timing.pending).ready)
        assertEquals(operation.cookie, timing.pending?.cookie)
        assertEquals(listOf(Event(true, "LoadLibrary", operation.cookie)), sink.events)

        timing.drawn(operation.cookie)
        assertTrue(timing.idle)
    }

    @Test
    fun lateDrawFromReplacedRequestCannotEndCurrentRequest() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        timing.begin("LoadTemplate")
        timing.ready()
        val previous = requireNotNull(timing.pending)

        timing.begin("LoadTemplate")
        val current = requireNotNull(timing.pending)
        assertNotEquals(previous.cookie, current.cookie)
        timing.ready()
        timing.drawn(previous.cookie)

        assertEquals(current.cookie, timing.pending?.cookie)
        assertEquals(listOf(
            Event(true, "LoadTemplate", previous.cookie),
            Event(false, "LoadTemplate", previous.cookie),
            Event(true, "LoadTemplate", current.cookie),
        ), sink.events)

        timing.drawn(current.cookie)
        assertEquals(Event(false, "LoadTemplate", current.cookie), sink.events.last())
        assertTrue(timing.idle)
    }

    @Test
    fun cancellingFailedRequestEndsItsTraceWithoutWaitingForDrawing() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        timing.begin("SaveSet")
        val operation = requireNotNull(timing.pending)

        timing.cancel()
        timing.drawn(operation.cookie)
        timing.cancel()

        assertEquals(listOf(Event(true, "SaveSet", operation.cookie), Event(false, "SaveSet", operation.cookie)), sink.events)
        assertTrue(timing.idle)
    }

    @Test
    fun disabledSinkDoesNotStartTraceOrRetainPendingUiWork() {
        val sink = RecordingSink(enabled = false)
        val timing = UiPerformanceTiming(sink)

        timing.begin("LoadLibrary")
        timing.ready()
        timing.drawn(123)
        timing.cancel()

        assertTrue(sink.events.isEmpty())
        assertNull(timing.pending)
        assertTrue(timing.idle)
    }

    @Test
    fun replacingRequestAfterTracingStopsClosesEarlierTrace() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        timing.begin("LoadLibrary")
        val operation = requireNotNull(timing.pending)

        sink.enabled = false
        timing.begin("LoadTemplate")

        assertEquals(listOf(Event(true, "LoadLibrary", operation.cookie), Event(false, "LoadLibrary", operation.cookie)), sink.events)
        assertTrue(timing.idle)
    }

    @Test
    fun failedViewModelLoadClosesTraceAndRetryStartsNewOperation() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        val gateway = DeferredGateway()
        val model = WorkoutHomeViewModel(gateway, timing)
        val failed = requireNotNull(timing.pending)

        gateway.callback.onFailure(IllegalStateException("Read failed"))

        assertTrue(timing.idle)
        assertEquals(Event(false, failed.name, failed.cookie), sink.events.last())
        assertNotNull(model.state.error)

        model.refresh()
        val retried = requireNotNull(timing.pending)
        assertNotEquals(failed.cookie, retried.cookie)
        gateway.callback.onSuccess(WorkoutGateway.Snapshot(null, emptyList()))

        assertTrue(requireNotNull(timing.pending).ready)
        assertEquals(Event(true, retried.name, retried.cookie), sink.events.last())
        timing.drawn(retried.cookie)
        assertEquals(Event(false, retried.name, retried.cookie), sink.events.last())
    }

    @Test
    fun clearingViewModelEndsTraceAndLateSuccessCannotReopenIt() {
        val sink = RecordingSink()
        val timing = UiPerformanceTiming(sink)
        val gateway = DeferredGateway()
        val store = ViewModelStore()
        val model = ViewModelProvider(store, WorkoutHomeViewModel.Factory(gateway, timing))
            .get(WorkoutHomeViewModel::class.java)
        val operation = requireNotNull(timing.pending)

        store.clear()
        gateway.callback.onSuccess(WorkoutGateway.Snapshot(null, emptyList()))

        assertEquals(listOf(Event(true, operation.name, operation.cookie), Event(false, operation.name, operation.cookie)), sink.events)
        assertTrue(timing.idle)
        assertFalse(model.state.loaded)
    }

    private data class Event(val begin: Boolean, val name: String, val cookie: Int)

    private class RecordingSink(var enabled: Boolean = true) : UiPerformanceTiming.Sink {
        val events = mutableListOf<Event>()
        override fun enabled() = enabled
        override fun begin(name: String, cookie: Int) { events += Event(true, name, cookie) }
        override fun end(name: String, cookie: Int) { events += Event(false, name, cookie) }
    }

    private class DeferredGateway : WorkoutGateway by Mockito.mock(WorkoutGateway::class.java) {
        lateinit var callback: WorkoutGateway.Callback
        override fun load(callback: WorkoutGateway.Callback) { this.callback = callback }
    }
}

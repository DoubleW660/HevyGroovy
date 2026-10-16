package com.example.hevygroovy.ui

import android.os.Trace
import androidx.compose.runtime.*
import java.util.concurrent.atomic.AtomicInteger

/** Request dispatch through drawing the resulting UI. Disabled unless a trace is collecting. */
class UiPerformanceTiming(private val sink: Sink = Sink.None) {
    interface Sink {
        fun enabled(): Boolean
        fun begin(name: String, cookie: Int)
        fun end(name: String, cookie: Int)

        object None : Sink {
            override fun enabled() = false
            override fun begin(name: String, cookie: Int) {}
            override fun end(name: String, cookie: Int) {}
        }
    }

    internal data class Pending(val name: String, val cookie: Int, val ready: Boolean = false)
    internal var pending by mutableStateOf<Pending?>(null)
        private set
    val idle: Boolean get() = pending == null

    fun begin(name: String) {
        cancel()
        if (!sink.enabled()) return
        val trace = Pending(name, cookies.incrementAndGet())
        sink.begin(trace.name, trace.cookie)
        pending = trace
    }

    fun ready() { pending = pending?.copy(ready = true) }
    fun cancel() {
        pending?.let { sink.end(it.name, it.cookie) }
        pending = null
    }
    internal fun drawn(cookie: Int) { if (pending?.cookie == cookie) cancel() }

    companion object {
        private val cookies = AtomicInteger()
        fun android() = UiPerformanceTiming(object : Sink {
            override fun enabled() = Trace.isEnabled()
            override fun begin(name: String, cookie: Int) = Trace.beginAsyncSection(name, cookie)
            override fun end(name: String, cookie: Int) = Trace.endAsyncSection(name, cookie)
        })
    }
}

@Composable
internal fun ReportOperationDrawn(timing: UiPerformanceTiming) {
    val pending = timing.pending
    LaunchedEffect(pending) {
        if (pending?.ready == true) {
            // Frame callbacks run before drawing. The second callback follows a rendered frame
            // containing the new state, rather than ending at the database callback.
            withFrameNanos { }
            withFrameNanos { }
            timing.drawn(pending.cookie)
        }
    }
}

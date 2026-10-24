package com.example.hevygroovy.resttimer

import android.os.Looper
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.Executor

/** Deliberately pause storage to cover races that a fast real preference file normally hides. */
@RunWith(AndroidJUnit4::class)
class RestTimerPersistenceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val worker = PausedWorker()
    private val alerts = mutableListOf<String>()

    @Test
    fun storageNeverBlocksUiAndReplacingATimerUsesOneWrite() {
        val storage = MemoryStorage(savedTimer("old"))
        lateinit var controller: RestTimerController
        lateinit var first: RestTimerState
        lateinit var replacement: RestTimerState
        onMain {
            controller = controller(storage)
            controller.start(101L, 1L, 1L, "First", 60)
            first = checkNotNull(controller.state.value)
            controller.start(101L, 2L, 2L, "Replacement", 60)
            replacement = checkNotNull(controller.state.value)
            assertTrue(storage.operations.isEmpty())
            assertFalse(first.token == replacement.token)
        }
        try {
            drain()
            assertEquals(listOf("read", "save:${first.token}", "save:${replacement.token}"),
                storage.operations)
            assertEquals(replacement.token, storage.saved?.token)
            assertEquals(replacement.token, controller.state.value?.token)
            assertTrue(storage.mainThreadCalls.isEmpty())
        } finally { stop(controller) }
    }

    @Test
    fun queuedAdjustSkipAndReplacementKeepTheirOrderAndCannotRestoreAnOldTimer() {
        val storage = MemoryStorage(savedTimer("old"))
        lateinit var controller: RestTimerController
        lateinit var replacement: RestTimerState
        onMain {
            controller = controller(storage)
            controller.start(101L, 1L, 1L, "First", 60)
            controller.adjustSeconds(15)
            controller.skip()
            assertNull(controller.state.value)
            controller.start(101L, 2L, 2L, "Replacement", 60)
            replacement = checkNotNull(controller.state.value)
        }
        try {
            drain()
            assertEquals(listOf("read", "save", "save", "clear", "save"),
                storage.operations.map { it.substringBefore(':') })
            assertEquals(15_000L, storage.saves[1].deadlineElapsedMillis -
                storage.saves[0].deadlineElapsedMillis)
            assertEquals(replacement.token, storage.saved?.token)
            assertEquals(replacement.token, controller.state.value?.token)
            assertTrue(alerts.isEmpty())
            assertTrue(storage.mainThreadCalls.isEmpty())
        } finally { stop(controller) }
    }

    @Test
    fun skipDuringInitialReadCannotReviveTheSavedTimer() {
        val storage = MemoryStorage(savedTimer("old"))
        lateinit var controller: RestTimerController
        onMain {
            controller = controller(storage)
            controller.skip()
        }
        drain()
        assertNull(controller.state.value)
        assertNull(storage.saved)
        assertEquals(listOf("read", "clear"), storage.operations)
        assertTrue(storage.mainThreadCalls.isEmpty())
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun reconcileAndWorkoutFinishDuringRestoreDiscardTheSavedTimer() {
        for (finish in listOf(false, true)) {
            val storage = MemoryStorage(savedTimer("old"))
            lateinit var controller: RestTimerController
            onMain {
                controller = controller(storage)
                if (finish) controller.cancelForWorkout(101L) else controller.reconcileWorkout(null)
            }
            drain()
            assertNull(controller.state.value)
            assertNull(storage.saved)
            assertEquals(listOf("read", "clear"), storage.operations)
            assertTrue(alerts.isEmpty())
        }
    }

    @Test
    fun duplicateReceiverCallbacksWaitForOneDurableClearBeforeAlertingAndFinishing() {
        val storage = MemoryStorage(savedTimer("expired", expired = true))
        lateinit var controller: RestTimerController
        var finished = 0
        onMain {
            controller = controller(storage)
            repeat(2) {
                controller.onAlarm("expired") {
                    assertNull(storage.saved)
                    assertEquals(listOf("expired"), alerts)
                    finished++
                }
            }
        }
        assertTrue(worker.runNext()) // Read; applying restoration starts the shared expiry claim.
        instrumentation.waitForIdleSync()
        assertEquals(0, finished)
        assertTrue(alerts.isEmpty())
        assertEquals(listOf("read"), storage.operations)
        drain()
        assertEquals(listOf("read", "clear"), storage.operations)
        assertEquals(listOf("expired"), alerts)
        assertEquals(2, finished)
        assertTrue(storage.mainThreadCalls.isEmpty())
        stop(controller)
    }

    @Test
    fun newTimerSuppressesCompletionWhoseClearIsStillQueued() {
        val storage = MemoryStorage(savedTimer("expired", expired = true))
        lateinit var controller: RestTimerController
        lateinit var replacement: RestTimerState
        onMain { controller = controller(storage) }
        assertTrue(worker.runNext())
        instrumentation.waitForIdleSync()
        onMain {
            controller.start(101L, 2L, 2L, "Replacement", 60)
            replacement = checkNotNull(controller.state.value)
        }
        try {
            drain()
            assertTrue(alerts.isEmpty())
            assertEquals(replacement.token, controller.state.value?.token)
            assertEquals(replacement.token, storage.saved?.token)
            assertEquals(listOf("read", "clear", "save:${replacement.token}"), storage.operations)
        } finally { stop(controller) }
    }

    @Test
    fun failedDurableClearDoesNotAlertButStillReleasesTheReceiver() {
        val storage = MemoryStorage(savedTimer("expired", expired = true), clearSucceeds = false)
        lateinit var controller: RestTimerController
        var finished = false
        onMain {
            controller = controller(storage)
            controller.onAlarm("expired") { finished = true }
        }
        drain()
        assertTrue(finished)
        assertTrue(alerts.isEmpty())
        assertEquals("expired", storage.saved?.token)
        assertEquals(listOf("read", "clear"), storage.operations)
        // No backup alarm was scheduled for the already-expired fixture.
        onMain { controller.skip() }
        drain()
    }

    @Test
    fun workoutFinishAfterLateExpiryCancelsThePendingAlertWithoutAVisibleOverlay() {
        assertWorkoutChangeCancelsPendingCompletion(finish = true)
    }

    @Test
    fun reconcileAfterLateExpiryCancelsThePendingAlertWithoutAVisibleOverlay() {
        assertWorkoutChangeCancelsPendingCompletion(finish = false)
    }

    @Test
    fun throwingReadReleasesTheReceiverWithoutAlertingOrChangingUnreadableState() {
        val storage = MemoryStorage(savedTimer("expired", expired = true), throwOn = setOf("read"))
        lateinit var controller: RestTimerController
        var finished = false
        onMain {
            controller = controller(storage)
            controller.onAlarm("expired") { finished = true }
        }
        drain()
        assertTrue(finished)
        assertNull(controller.state.value)
        assertTrue(alerts.isEmpty())
        assertEquals(listOf("read"), storage.operations)
        assertEquals("expired", storage.saved?.token)
        assertTrue(storage.mainThreadCalls.isEmpty())
        stop(controller)
    }

    @Test
    fun throwingSaveDoesNotPreventAFollowingSkipOrReceiverRelease() {
        val storage = MemoryStorage(null, throwOn = setOf("save"))
        lateinit var controller: RestTimerController
        var finished = false
        onMain {
            controller = controller(storage)
            controller.start(101L, 1L, 1L, "First", 60)
            controller.skip()
            controller.onAlarm("irrelevant") { finished = true }
        }
        drain()
        assertTrue(finished)
        assertNull(storage.saved)
        assertNull(controller.state.value)
        assertTrue(alerts.isEmpty())
        assertEquals(listOf("read", "save", "clear"),
            storage.operations.map { it.substringBefore(':') })
        assertTrue(storage.mainThreadCalls.isEmpty())
    }

    @Test
    fun throwingClearDoesNotAlertAndStillReleasesTheReceiver() {
        val storage = MemoryStorage(savedTimer("expired", expired = true), throwOn = setOf("clear"))
        lateinit var controller: RestTimerController
        var finished = false
        onMain {
            controller = controller(storage)
            controller.onAlarm("expired") { finished = true }
        }
        drain()
        assertTrue(finished)
        assertTrue(alerts.isEmpty())
        assertEquals("expired", storage.saved?.token)
        assertEquals(listOf("read", "clear"), storage.operations)
        assertTrue(storage.mainThreadCalls.isEmpty())
        stop(controller)
    }

    private fun assertWorkoutChangeCancelsPendingCompletion(finish: Boolean) {
        val storage = MemoryStorage(savedTimer("expired", expired = true))
        lateinit var controller: RestTimerController
        onMain { controller = controller(storage) }
        assertTrue(worker.runNext())
        instrumentation.waitForIdleSync()
        assertNull(controller.state.value) // More than a second late: the overlay is already absent.
        assertEquals(listOf("read"), storage.operations)
        onMain {
            if (finish) controller.cancelForWorkout(101L) else controller.reconcileWorkout(null)
        }
        drain()
        assertNull(storage.saved)
        assertTrue(alerts.isEmpty())
        assertTrue(storage.mainThreadCalls.isEmpty())
    }

    private fun controller(storage: MemoryStorage) = RestTimerController(
        instrumentation.targetContext, storage, worker, completionAlert = { alerts += it.token })

    private fun savedTimer(token: String, expired: Boolean = false): RestTimerState {
        val deadline = SystemClock.elapsedRealtime() + if (expired) -2_000L else 120_000L
        return RestTimerState(token, 101L, 1L, 1L, "Saved exercise", deadline,
            if (expired) 0 else 120)
    }

    private fun stop(controller: RestTimerController) {
        onMain { controller.skip() }
        drain()
    }

    private fun onMain(action: () -> Unit) = instrumentation.runOnMainSync(action)

    private fun drain() {
        repeat(30) {
            val ran = worker.runNext()
            instrumentation.waitForIdleSync()
            if (!ran && worker.isEmpty()) return
        }
        error("Timer storage did not become idle")
    }

    private class PausedWorker : Executor {
        private val tasks = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { synchronized(tasks) { tasks.addLast(command) } }
        fun runNext(): Boolean {
            val task = synchronized(tasks) { tasks.removeFirstOrNull() } ?: return false
            check(Looper.myLooper() != Looper.getMainLooper())
            task.run()
            return true
        }
        fun isEmpty() = synchronized(tasks) { tasks.isEmpty() }
    }

    private class MemoryStorage(initial: RestTimerState?, private val clearSucceeds: Boolean = true,
        private val throwOn: Set<String> = emptySet()) :
        RestTimerStorage {
        var saved: RestTimerState? = initial
        val operations = mutableListOf<String>()
        val mainThreadCalls = mutableListOf<String>()
        val saves = mutableListOf<RestTimerState>()

        override fun read(): StoredRestTimer {
            record("read")
            return StoredRestTimer(saved?.token, saved)
        }
        override fun save(timer: RestTimerState): Boolean {
            record("save:${timer.token}")
            saved = timer
            saves += timer
            return true
        }
        override fun clear(): Boolean {
            record("clear")
            if (clearSucceeds) saved = null
            return clearSucceeds
        }
        private fun record(operation: String) {
            operations += operation
            if (Looper.myLooper() == Looper.getMainLooper()) mainThreadCalls += operation
            if (operation.substringBefore(':') in throwOn) throw IllegalStateException("Storage failed")
        }
    }
}

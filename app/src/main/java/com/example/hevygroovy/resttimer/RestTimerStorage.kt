package com.example.hevygroovy.resttimer

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** All methods run on the controller's single storage worker, including the first preference read. */
internal interface RestTimerStorage {
    fun read(): StoredRestTimer
    fun save(timer: RestTimerState): Boolean
    fun clear(): Boolean
}

internal data class StoredRestTimer(val token: String?, val timer: RestTimerState?)

internal class PreferencesRestTimerStorage(context: Context) : RestTimerStorage {
    private val app = context.applicationContext
    private val preferences by lazy { app.getSharedPreferences("rest_timer", Context.MODE_PRIVATE) }

    override fun read(): StoredRestTimer {
        val token = preferences.getString("token", null) ?: return StoredRestTimer(null, null)
        val now = SystemClock.elapsedRealtime()
        val deadline = RestTimerTiming.restoredDeadline(
            preferences.getLong("deadline_elapsed", -1L),
            preferences.getLong("deadline_wall", -1L), preferences.getInt("boot_count", -1),
            preferences.getLong("boot_epoch", Long.MIN_VALUE), bootCount(), now,
            System.currentTimeMillis())
        // A force-stopped rest must not chime when the app opens much later or after a reboot.
        if (deadline == null || now - deadline > MAX_ALERT_LATENESS_MILLIS) {
            return StoredRestTimer(token, null)
        }
        return StoredRestTimer(token, RestTimerState(token, preferences.getLong("workout", 0L),
            preferences.getLong("exercise", 0L), preferences.getLong("set", 0L),
            preferences.getString("name", "Exercise").orEmpty(), deadline,
            RestTimerTiming.remainingSeconds(deadline, now)))
    }

    override fun save(timer: RestTimerState): Boolean {
        val elapsed = SystemClock.elapsedRealtime()
        val wall = System.currentTimeMillis()
        return preferences.edit().putString("token", timer.token)
            .putLong("workout", timer.workoutId).putLong("exercise", timer.exerciseRowId)
            .putLong("set", timer.setId).putString("name", timer.exerciseName)
            .putLong("deadline_elapsed", timer.deadlineElapsedMillis)
            .putLong("deadline_wall", wall + timer.deadlineElapsedMillis - elapsed)
            .putLong("boot_epoch", wall - elapsed).putInt("boot_count", bootCount()).commit()
    }

    override fun clear(): Boolean = preferences.edit().clear().commit()

    private fun bootCount(): Int = try {
        Settings.Global.getInt(app.contentResolver, Settings.Global.BOOT_COUNT, -1)
    } catch (_: SecurityException) { -1 }

    private companion object {
        const val MAX_ALERT_LATENESS_MILLIS = 10 * 60 * 1_000L
    }
}

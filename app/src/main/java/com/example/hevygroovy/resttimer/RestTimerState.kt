package com.example.hevygroovy.resttimer

/** Remaining time is derived from a monotonic deadline, including time the phone spends asleep. */
data class RestTimerState(
    val token: String,
    val workoutId: Long,
    val exerciseRowId: Long,
    val setId: Long,
    val exerciseName: String,
    val deadlineElapsedMillis: Long,
    val remainingSeconds: Int,
    val isComplete: Boolean = false,
)

internal object RestTimerTiming {
    private const val COMPLETION_DISPLAY_MILLIS = 750L

    fun remainingSeconds(deadline: Long, now: Long): Int =
        ((deadline - now).coerceAtLeast(0L).let { (it + 999L) / 1_000L })
            .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun adjustedDeadline(deadline: Long, now: Long, deltaSeconds: Int): Long =
        now + ((deadline - now).coerceAtLeast(0L) + deltaSeconds.toLong() * 1_000L).coerceAtLeast(0L)

    /** Give fresh completion a full pause; returning much later cannot revive the message. */
    fun completionDisplayMillis(deadline: Long, now: Long): Long =
        if (now - deadline in 0L..1_000L) COMPLETION_DISPLAY_MILLIS else 0L

    fun restoredDeadline(savedElapsed: Long, savedWall: Long, savedBootCount: Int,
        savedBootEpoch: Long, currentBootCount: Int, nowElapsed: Long, nowWall: Long): Long? {
        if (savedElapsed < 0L || savedWall <= 0L) return null
        if (savedBootCount >= 0 && currentBootCount >= 0) {
            return savedElapsed.takeIf { savedBootCount == currentBootCount }
        }
        // BOOT_COUNT is normally readable. If unavailable, only restore a deadline when both
        // clocks still describe the same boot; never carry a short rest across a phone reboot.
        if (kotlin.math.abs((nowWall - nowElapsed) - savedBootEpoch) > 60_000L) return null
        return nowElapsed + (savedWall - nowWall)
    }
}

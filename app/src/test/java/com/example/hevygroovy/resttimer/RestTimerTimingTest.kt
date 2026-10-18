package com.example.hevygroovy.resttimer

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RestTimerTimingTest {
    @Test fun `countdown rounds up and reaches zero without going negative`() {
        assertEquals(15, RestTimerTiming.remainingSeconds(20_000L, 5_001L))
        assertEquals(1, RestTimerTiming.remainingSeconds(20_000L, 19_999L))
        assertEquals(0, RestTimerTiming.remainingSeconds(20_000L, 20_000L))
        assertEquals(0, RestTimerTiming.remainingSeconds(20_000L, 80_000L))
    }

    @Test fun `adjustments preserve fractional time and clamp subtraction to now`() {
        assertEquals(50_000L, RestTimerTiming.adjustedDeadline(35_000L, 10_555L, 15))
        assertEquals(20_000L, RestTimerTiming.adjustedDeadline(35_000L, 10_555L, -15))
        assertEquals(10_555L, RestTimerTiming.adjustedDeadline(20_000L, 10_555L, -15))
    }

    @Test fun `same boot restoration uses elapsed clock despite wall clock changes`() {
        assertEquals(80_000L, restore(savedBoot = 7, currentBoot = 7, nowWall = 8_000_000L))
    }

    @Test fun `phone reboot discards the previous short rest`() {
        assertNull(restore(savedBoot = 7, currentBoot = 8))
    }

    @Test fun `wall fallback requires clocks describing the same boot`() {
        assertEquals(80_000L, restore(savedBoot = -1, currentBoot = -1))
        assertNull(restore(savedBoot = -1, currentBoot = -1, nowWall = 8_000_000L))
    }

    private fun restore(savedBoot: Int, currentBoot: Int, nowWall: Long = 1_050_000L) =
        RestTimerTiming.restoredDeadline(80_000L, 1_080_000L, savedBoot,
            1_000_000L, currentBoot, 50_000L, nowWall)
}

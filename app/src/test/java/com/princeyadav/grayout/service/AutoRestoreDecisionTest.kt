package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoRestoreDecisionTest {

    private fun decide(
        interval: Int = 5,
        onlyDuringSchedules: Boolean = false,
        inWindow: Boolean = false,
        excluded: Boolean = false,
        grayOn: Boolean = false,
    ) = shouldAutoRestore(interval, onlyDuringSchedules, inWindow, excluded, grayOn)

    @Test
    fun restoreOffNeverRestores() = assertFalse(decide(interval = 0, inWindow = true))

    @Test
    fun excludedAppBlocksRestore() = assertFalse(decide(excluded = true, inWindow = true))

    @Test
    fun alreadyGrayNeedsNothing() = assertFalse(decide(grayOn = true, inWindow = true))

    @Test
    fun defaultRestoresAnytime() = assertTrue(decide(onlyDuringSchedules = false, inWindow = false))

    @Test
    fun onlyDuringSchedulesRestoresInsideAWindow() = assertTrue(decide(onlyDuringSchedules = true, inWindow = true))

    @Test
    fun onlyDuringSchedulesLeavesGrayscaleOffOutsideAWindow() =
        assertFalse(decide(onlyDuringSchedules = true, inWindow = false))

    @Test
    fun onlyDuringSchedulesDefaultsOffAndRoundTrips() {
        val prefs = EnforcementPrefs(FakeSharedPreferences())
        assertFalse(prefs.isOnlyDuringSchedules())
        prefs.setOnlyDuringSchedules(true)
        assertEquals(true, prefs.isOnlyDuringSchedules())
    }

    @Test
    fun elapsedDeadlinesConvertToWallTimeAndOverdueMeansNow() {
        // Deadline 5 min after "now" on the monotonic clock lands 5 min after wall-clock now.
        assertEquals(1_000_300_000L, restoreWallMillis(elapsedDeadline = 600_000L, nowElapsed = 300_000L, nowWall = 1_000_000_000L))
        // An overdue deadline fires immediately, so coverage is checked at now.
        assertEquals(1_000_000_000L, restoreWallMillis(elapsedDeadline = 100_000L, nowElapsed = 300_000L, nowWall = 1_000_000_000L))
    }
}

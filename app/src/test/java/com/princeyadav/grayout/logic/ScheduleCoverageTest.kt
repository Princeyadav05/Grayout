package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduleCoverageTest {

    private val utc: ZoneId = ZoneOffset.UTC

    private fun s(days: String, sh: Int, sm: Int, eh: Int, em: Int, enabled: Boolean = true) =
        Schedule(name = "x", daysOfWeek = days, startTimeHour = sh, startTimeMinute = sm,
            endTimeHour = eh, endTimeMinute = em, isEnabled = enabled)

    private fun covered(schedules: List<Schedule>, at: String, zone: ZoneId = utc) =
        isCovered(decodeCoverage(encodeCoverage(schedules)), Instant.parse(at), zone)

    @Test
    fun roundTripKeepsOnlyEnabledWindows() {
        val decoded = decodeCoverage(encodeCoverage(listOf(s("MON,WED", 9, 30, 17, 0), s("SUN", 1, 0, 2, 0, enabled = false))))
        assertEquals(1, decoded.size)
        assertEquals("MON,WED", decoded[0].daysOfWeek)
        assertEquals(listOf(9, 30, 17, 0), decoded[0].let { listOf(it.startTimeHour, it.startTimeMinute, it.endTimeHour, it.endTimeMinute) })
    }

    @Test
    fun malformedOrMissingSnapshotMeansNoCoverage() {
        assertEquals(emptyList<Schedule>(), decodeCoverage(null))
        assertEquals(emptyList<Schedule>(), decodeCoverage("garbage;MON|25:00|26:00;MON|9:00"))
    }

    @Test
    fun gapBetweenWindowsIsNotCovered() {
        val day = listOf(s("MON", 9, 0, 10, 0), s("MON", 14, 0, 15, 0))
        assertTrue(covered(day, "2026-04-13T09:30:00Z"))
        assertFalse(covered(day, "2026-04-13T12:00:00Z"))
    }

    @Test
    fun adjacentWindowsLeaveNoHoleAtTheSeam() {
        val day = listOf(s("MON", 9, 0, 10, 0), s("MON", 10, 0, 11, 0))
        assertTrue(covered(day, "2026-04-13T10:00:00Z"))
        assertFalse(covered(day, "2026-04-13T11:00:00Z"))
    }

    @Test
    fun nestedLegacyWindowsStayCoveredOutsideTheInnerOne() {
        val nested = listOf(s("MON", 9, 0, 12, 0), s("MON", 10, 0, 11, 0))
        assertTrue(covered(nested, "2026-04-13T09:30:00Z"))
        assertTrue(covered(nested, "2026-04-13T11:30:00Z"))
    }

    @Test
    fun overnightWindowCoversAfterMidnight() {
        assertTrue(covered(listOf(s("MON", 22, 0, 6, 0)), "2026-04-14T05:00:00Z"))
    }

    @Test
    fun coverageIsAnsweredWithoutAnyAlarmDelivery() {
        // A missed start or end alarm changes nothing: coverage is wall-clock only.
        assertTrue(covered(listOf(s("MON", 9, 0, 10, 0)), "2026-04-13T09:59:00Z"))
        assertFalse(covered(listOf(s("MON", 9, 0, 10, 0)), "2026-04-13T10:01:00Z"))
    }

    @Test
    fun dstDaysUseResolvedInstants() {
        val ny = ZoneId.of("America/New_York")
        // Spring forward: 02:30 does not exist and resolves to 03:30 EDT (07:30Z).
        assertFalse(covered(listOf(s("SUN", 2, 30, 4, 0)), "2026-03-08T07:29:00Z", ny))
        assertTrue(covered(listOf(s("SUN", 2, 30, 4, 0)), "2026-03-08T07:30:00Z", ny))
        // Fall back: the repeated 01:15 is still inside a 01:00-02:00 window.
        assertTrue(covered(listOf(s("SUN", 1, 0, 2, 0)), "2026-11-01T06:15:00Z", ny))
    }

    @Test
    fun sameSnapshotFollowsAZoneChange() {
        val focus = listOf(s("MON", 9, 0, 10, 0))
        assertTrue(covered(focus, "2026-04-13T09:30:00Z", utc))
        assertFalse(covered(focus, "2026-04-13T09:30:00Z", ZoneOffset.ofHours(2)))
    }
}

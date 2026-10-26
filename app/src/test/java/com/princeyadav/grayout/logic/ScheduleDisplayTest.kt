package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

class ScheduleDisplayTest {

    private val utc: ZoneId = ZoneOffset.UTC
    private val monday0859 = Instant.parse("2026-04-13T08:59:00Z") // a Monday

    private fun schedule(id: Long, name: String, days: String, start: Int, end: Int, enabled: Boolean = true) =
        Schedule(id, name, days, start, 0, end, 0, enabled)

    @Test
    fun runningScheduleReportsItsEnd() {
        val line = scheduleLine(listOf(schedule(1, "Focus", "MON", 9, 10)), Instant.parse("2026-04-13T09:30:00Z"), utc)
        assertEquals(ScheduleLine(1, "Focus", LocalDateTime.parse("2026-04-13T10:00"), isActive = true), line)
    }

    @Test
    fun overnightWindowEndsTomorrow() {
        val line = scheduleLine(listOf(schedule(2, "Night", "MON", 22, 6)), Instant.parse("2026-04-13T23:00:00Z"), utc)
        assertEquals(LocalDateTime.parse("2026-04-14T06:00"), line?.at)
        assertEquals(true, line?.isActive)
    }

    @Test
    fun soonestUpcomingStartWinsAcrossSchedules() {
        val schedules = listOf(schedule(1, "Later", "MON", 14, 15), schedule(2, "Sooner", "MON", 9, 10))
        val line = scheduleLine(schedules, monday0859, utc)
        assertEquals(ScheduleLine(2, "Sooner", LocalDateTime.parse("2026-04-13T09:00"), isActive = false), line)
    }

    @Test
    fun disabledAndEmptyGiveNoLine() {
        assertNull(scheduleLine(emptyList(), monday0859, utc))
        assertNull(scheduleLine(listOf(schedule(1, "Off", "MON", 9, 10, enabled = false)), monday0859, utc))
    }

    @Test
    fun lineChangesAtTheNextStartOrEnd() {
        val schedules = listOf(schedule(1, "Focus", "MON", 9, 10))
        assertEquals(Instant.parse("2026-04-13T09:00:00Z"), nextScheduleLineChange(schedules, monday0859, utc))
        assertEquals(
            Instant.parse("2026-04-13T10:00:00Z"),
            nextScheduleLineChange(schedules, Instant.parse("2026-04-13T09:30:00Z"), utc),
        )
    }

    @Test
    fun repeatedDstHourNeverYieldsAPastWakeUp() {
        val ny = ZoneId.of("America/New_York")
        val schedules = listOf(schedule(1, "Early", "SUN", 1, 2))
        val now = Instant.parse("2026-11-01T06:15:00Z") // 01:15 EST, the repeated hour
        val next = nextScheduleLineChange(schedules, now, ny)!!
        assert(next.isAfter(now))
    }

    @Test
    fun colorIndexComesFromTheIdAndCycles() {
        assertEquals(listOf(0, 1, 2, 0), (1L..4L).map { scheduleColorIndex(it) })
    }
}

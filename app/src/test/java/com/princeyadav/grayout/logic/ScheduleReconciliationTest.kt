package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.ArmedScheduleAlarm
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleReconciliationTest {
    private val utc = ZoneId.of("UTC")
    private val schedule = Schedule(1, "Focus", "FRI", 9, 0, 17, 0)
    private val start = LocalDateTime.parse("2026-09-11T09:00")
    private val end = LocalDateTime.parse("2026-09-11T17:00")
    private fun alarm(isStart: Boolean, zone: ZoneId = utc) = ArmedScheduleAlarm.create(
        ScheduleEvent(if (isStart) start else end, isStart, 1, start, end), zone,
    )
    private fun target(armed: ArmedScheduleAlarm?, now: String, zone: ZoneId = utc,
                       schedules: List<Schedule> = listOf(schedule)) =
        scheduleReconciliationTarget(armed, schedules, Instant.parse(now), zone)

    @Test fun `eastward timezone change enters a newly active window`() {
        assertEquals(true, target(alarm(true), "2026-09-11T08:00:00Z", ZoneId.of("UTC+03:00")))
    }
    @Test fun `timezone change closes a previously active window`() {
        assertEquals(false, target(alarm(false), "2026-09-11T15:00:00Z", ZoneId.of("UTC+03:00")))
    }
    @Test fun `westward timezone change before the local start closes old coverage`() {
        assertEquals(false, target(alarm(false), "2026-09-11T10:00:00Z", ZoneId.of("UTC-03:00")))
    }
    @Test fun `same active occurrence leaves manual color unchanged`() {
        assertNull(target(alarm(false), "2026-09-11T12:00:00Z", ZoneId.of("UTC+02:00")))
    }
    @Test fun `outside to outside preserves manual grayscale`() {
        assertNull(target(alarm(true), "2026-09-11T06:00:00Z", ZoneId.of("UTC+02:00")))
        assertNull(target(alarm(true), "2026-09-12T12:00:00Z"))
        assertNull(target(null, "2026-09-12T12:00:00Z"))
    }
    @Test fun `clock jump forward past a pending end closes it`() {
        assertEquals(false, target(alarm(false), "2026-09-11T18:00:00Z"))
    }
    @Test fun `clock jump backward out of the old window closes it`() {
        assertEquals(false, target(alarm(false), "2026-09-11T08:00:00Z"))
    }
    @Test fun `clock jump into another weekly occurrence applies its start`() {
        assertEquals(true, target(alarm(false), "2026-09-18T10:00:00Z"))
    }
    @Test fun `deleted disabled or changed source cannot close manual gray`() {
        val at = "2026-09-11T18:00:00Z"
        assertNull(target(alarm(false), at, schedules = emptyList()))
        assertNull(target(alarm(false), at, schedules = listOf(schedule.copy(isEnabled = false))))
        assertNull(target(alarm(false), at, schedules = listOf(schedule.copy(daysOfWeek = "MON"))))
        assertNull(target(alarm(false), at, schedules = listOf(schedule.copy(startTimeMinute = 30))))
        assertNull(target(alarm(false), at, schedules = listOf(schedule.copy(endTimeHour = 16))))
    }
    @Test fun `continuing overlapping window is active at a crossed closing boundary`() {
        val second = schedule.copy(id = 2, startTimeHour = 16, endTimeHour = 19)
        assertEquals(true, target(alarm(false), "2026-09-11T17:30:00Z", schedules = listOf(schedule, second)))
    }
    @Test fun `interior start without a crossed closing boundary preserves a manual choice`() {
        val second = schedule.copy(id = 2, startTimeHour = 10, endTimeHour = 14)
        assertNull(target(alarm(false), "2026-09-11T12:00:00Z", schedules = listOf(schedule, second)))
    }
    @Test fun `spring gap uses normalized instants for unchanged active coverage`() {
        val zone = ZoneId.of("America/New_York")
        val from = LocalDateTime.parse("2026-03-08T02:30")
        val until = LocalDateTime.parse("2026-03-08T04:00")
        val row = schedule.copy(daysOfWeek = "SUN", startTimeHour = 2, startTimeMinute = 30, endTimeHour = 4)
        val armed = ArmedScheduleAlarm.create(ScheduleEvent(until, false, 1, from, until), zone)
        assertNull(target(armed, "2026-03-08T07:40:00Z", zone, listOf(row)))
    }
    @Test fun `repeated fall hour cannot resurrect an expired occurrence`() {
        val zone = ZoneId.of("America/New_York")
        val from = LocalDateTime.parse("2026-11-01T01:30")
        val until = LocalDateTime.parse("2026-11-01T01:45")
        val row = schedule.copy(daysOfWeek = "SUN", startTimeHour = 1, startTimeMinute = 30, endTimeHour = 1, endTimeMinute = 45)
        val armed = ArmedScheduleAlarm.create(ScheduleEvent(until, false, 1, from, until), zone)
        assertEquals(false, target(armed, "2026-11-01T06:35:00Z", zone, listOf(row)))
        assertNull(target(armed.copy(isStart = true), "2026-11-01T06:35:00Z", zone, listOf(row)))
    }
}

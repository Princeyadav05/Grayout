package com.princeyadav.grayout.logic

import com.princeyadav.grayout.fakes.FakeSharedPreferences
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.ArmedScheduleAlarm
import com.princeyadav.grayout.scheduling.ScheduleAlarmState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleDeliveryDstTest {
    private val zone = ZoneId.of("America/New_York")
    private val days = "MON,TUE,WED,THU,FRI,SAT,SUN"

    private fun persisted(start: String, end: String): ArmedScheduleAlarm {
        val from = LocalDateTime.parse(start)
        val until = LocalDateTime.parse(end)
        val original = ArmedScheduleAlarm.create(ScheduleEvent(from, true, 1, from, until), zone)
        val store = ScheduleAlarmState(FakeSharedPreferences())
        store.save(original)
        return checkNotNull(store.read()).also { assertEquals(original, it) }
    }

    @Test
    fun `spring gap start preserves civil identity while using resolved trigger`() {
        val alarm = persisted("2026-03-08T02:30", "2026-03-08T04:00")
        val schedule = Schedule(1, "Gap", days, 2, 30, 4, 0)
        assertEquals(LocalDateTime.parse("2026-03-08T02:30"), alarm.event().windowStart)
        assertEquals(Instant.parse("2026-03-08T07:30:00Z").toEpochMilli(), alarm.startMillis)
        assertEquals(true, scheduleDeliveryTarget(alarm.event(), listOf(schedule), Instant.parse("2026-03-08T07:40:00Z"), zone))
    }

    @Test
    fun `spring gap end does not invalidate its earlier valid start`() {
        val alarm = persisted("2026-03-08T01:00", "2026-03-08T02:30")
        val schedule = Schedule(1, "Gap end", days, 1, 0, 2, 30)
        assertEquals(true, scheduleDeliveryTarget(alarm.event(), listOf(schedule), Instant.parse("2026-03-08T06:00:00Z"), zone))
        assertTrue(isCurrentlyFiring(schedule, Instant.parse("2026-03-08T07:20:00Z"), zone))
        assertFalse(isCurrentlyFiring(schedule, Instant.parse("2026-03-08T07:30:00Z"), zone))
    }

    @Test
    fun `repeated fall-back hour cannot make an expired start current again`() {
        val alarm = persisted("2026-11-01T01:30", "2026-11-01T01:45")
        val schedule = Schedule(1, "Overlap", days, 1, 30, 1, 45)
        val repeatedHour = Instant.parse("2026-11-01T06:35:00Z")
        assertEquals(Instant.parse("2026-11-01T05:45:00Z").toEpochMilli(), alarm.endMillis)
        assertNull(scheduleDeliveryTarget(alarm.event(), listOf(schedule), repeatedHour, zone))
        assertFalse(isCurrentlyFiring(schedule, repeatedHour, zone))
        val next = checkNotNull(nextScheduleEvent(listOf(schedule), repeatedHour, zone))
        assertEquals(LocalDateTime.parse("2026-11-02T01:30"), next.dateTime)
        assertTrue(next.isStart)
    }

    @Test
    fun `gap-normalized inverted window is skipped instead of scheduling an end before start`() {
        val schedule = Schedule(1, "Collapsed", days, 2, 30, 3, 15)
        val next = checkNotNull(nextScheduleEvent(listOf(schedule), Instant.parse("2026-03-08T06:00:00Z"), zone))
        assertEquals(LocalDateTime.parse("2026-03-09T02:30"), next.dateTime)
        assertTrue(next.isStart)
    }

    @Test
    fun `gap-normalized overlap keeps a closing boundary instead of an interior start`() {
        val a = Schedule(1, "Gap", days, 2, 0, 2, 15)
        val b = Schedule(2, "Overlap after gap", days, 3, 5, 3, 30)
        val rows = listOf(a, b)
        val next = checkNotNull(nextScheduleEvent(rows, Instant.parse("2026-03-08T07:00:00Z"), zone))
        assertFalse(next.isStart)
        assertEquals(a.id, next.scheduleId)
        assertEquals(Instant.parse("2026-03-08T07:15:00Z"), next.dateTime.atZone(zone).toInstant())
        assertEquals(false, scheduleDeliveryTarget(next, rows, Instant.parse("2026-03-08T07:40:00Z"), zone))
    }

    @Test
    fun `zone-changed start must still be inside its civil and originally armed windows`() {
        val start = LocalDateTime.parse("2026-09-11T09:00")
        val end = LocalDateTime.parse("2026-09-11T17:00")
        val event = ScheduleEvent(start, true, 1, start, end)
        val schedule = Schedule(1, "Day", days, 9, 0, 17, 0)
        val from = Instant.parse("2026-09-11T09:00:00Z")
        val until = Instant.parse("2026-09-11T17:00:00Z")
        assertEquals(true, scheduleDeliveryTarget(event, listOf(schedule), from, ZoneId.of("UTC+02:00"), from, until))
        assertNull(scheduleDeliveryTarget(event, listOf(schedule), from, ZoneId.of("UTC-02:00"), from, until))
        assertNull(scheduleDeliveryTarget(event, listOf(schedule), from, ZoneId.of("UTC+10:00"), from, until))
    }
}

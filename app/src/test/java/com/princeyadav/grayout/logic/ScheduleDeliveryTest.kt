package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.testutil.fixedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneOffset

class ScheduleDeliveryTest {
    private val start = fixedDateTime(DayOfWeek.MONDAY, 9, 0)
    private val end = start.plusHours(1)
    private val schedule = Schedule(1, "Morning", "MON", 9, 0, 10, 0)

    private fun event(isStart: Boolean, from: LocalDateTime = start, until: LocalDateTime = end) =
        ScheduleEvent(if (isStart) from else until, isStart, schedule.id, from, until)

    @Test
    fun `late start still inside its occurrence applies grayscale`() {
        assertEquals(true, target(event(true), listOf(schedule), start.plusMinutes(30)))
    }

    @Test
    fun `start at or after its end is ignored`() {
        assertNull(target(event(true), listOf(schedule), end))
        assertNull(target(event(true), listOf(schedule), end.plusMinutes(30)))
    }

    @Test
    fun `expired start cannot borrow a different active window`() {
        val later = schedule.copy(id = 2, startTimeHour = 10, endTimeHour = 11)
        assertNull(target(event(true), listOf(schedule, later), end.plusMinutes(5)))
    }

    @Test
    fun `yesterdays start cannot borrow todays same-clock occurrence`() {
        val daily = schedule.copy(daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN")
        assertNull(target(event(true), listOf(daily), start.plusDays(1).plusMinutes(5)))
    }

    @Test
    fun `early boundary is ignored`() {
        assertNull(target(event(true), listOf(schedule), start.minusSeconds(1)))
        assertNull(target(event(false), listOf(schedule), end.minusSeconds(1)))
    }

    @Test
    fun `disabled deleted or edited origin cannot apply a queued boundary`() {
        val edited = listOf(
            schedule.copy(isEnabled = false), schedule.copy(startTimeMinute = 15),
            schedule.copy(endTimeMinute = 15), schedule.copy(daysOfWeek = "TUE"),
            schedule.copy(id = 2),
        )
        for (replacement in edited) {
            assertNull(target(event(false), listOf(replacement), end.plusMinutes(5)))
        }
        assertNull(target(event(false), emptyList(), end.plusMinutes(5)))
    }

    @Test
    fun `valid delayed end outside all windows turns grayscale off`() {
        assertEquals(false, target(event(false), listOf(schedule), end.plusMinutes(5)))
    }

    @Test
    fun `valid current-chain end catches up directly to a newer active window`() {
        val later = schedule.copy(id = 2, startTimeHour = 10, endTimeHour = 11)
        assertEquals(true, target(event(false), listOf(schedule, later), end.plusMinutes(5)))
    }

    @Test
    fun `overnight occurrence validates against the start day`() {
        val night = schedule.copy(startTimeHour = 22, endTimeHour = 2)
        val from = start.withHour(22)
        val until = start.plusDays(1).withHour(2)
        assertEquals(true, target(event(true, from, until), listOf(night), until.minusMinutes(30)))
        assertEquals(false, target(event(false, from, until), listOf(night), until.plusMinutes(10)))
        assertNull(target(event(true, from, until), listOf(night), until))
    }

    @Test
    fun `touching boundaries retain the closing end regardless of row order`() {
        val later = schedule.copy(id = 2, startTimeHour = 10, endTimeHour = 11)
        for (rows in listOf(listOf(schedule, later), listOf(later, schedule))) {
            val next = checkNotNull(nextScheduleEvent(rows, start.plusMinutes(30)))
            assertFalse(next.isStart)
            assertEquals(schedule.id, next.scheduleId)
            assertEquals(start, next.windowStart)
            assertEquals(end, next.windowEnd)
        }
    }

    @Test
    fun `legacy pending alarms preserve legitimate ends but reject expired starts and missing kinds`() {
        assertNull(legacyTarget(true, listOf(schedule), end.plusMinutes(5)))
        assertEquals(true, legacyTarget(true, listOf(schedule), start.plusMinutes(5)))
        assertEquals(false, legacyTarget(false, listOf(schedule), end.plusMinutes(5)))
        assertNull(legacyTarget(null, listOf(schedule), end.plusMinutes(5)))
        assertNull(legacyTarget(false, emptyList(), end.plusMinutes(5)))
    }
    private fun target(event: ScheduleEvent, schedules: List<Schedule>, now: LocalDateTime): Boolean? =
        scheduleDeliveryTarget(event, schedules, now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

    private fun legacyTarget(isStart: Boolean?, schedules: List<Schedule>, now: LocalDateTime): Boolean? =
        legacyScheduleDeliveryTarget(isStart, schedules, now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)

}

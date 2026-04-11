package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleTimeWindowTest {

    // 2026-04-13 is a Monday — use it as a stable anchor for day-of-week math.
    private val mondayDate = LocalDate.of(2026, 4, 13)

    private fun at(dayOfWeek: DayOfWeek, hour: Int, minute: Int = 0): LocalDateTime {
        val target = mondayDate.plusDays(
            (dayOfWeek.value - DayOfWeek.MONDAY.value).toLong()
        )
        return LocalDateTime.of(target, LocalTime.of(hour, minute))
    }

    private fun schedule(
        daysOfWeek: String = "MON,TUE,WED,THU,FRI",
        startHour: Int = 9,
        startMinute: Int = 0,
        endHour: Int = 17,
        endMinute: Int = 0,
        isEnabled: Boolean = true,
    ) = Schedule(
        id = 1L,
        name = "test",
        daysOfWeek = daysOfWeek,
        startTimeHour = startHour,
        startTimeMinute = startMinute,
        endTimeHour = endHour,
        endTimeMinute = endMinute,
        isEnabled = isEnabled,
    )

    @Test
    fun `isCurrentlyFiring returns false when schedule is disabled`() {
        val s = schedule(isEnabled = false)
        val now = at(DayOfWeek.MONDAY, 12, 0)
        assertFalse(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns false when current day not in schedule days`() {
        val s = schedule(daysOfWeek = "MON,TUE,WED,THU,FRI")
        val now = at(DayOfWeek.SATURDAY, 12, 0)
        assertFalse(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns true when current time within same-day window`() {
        val s = schedule(startHour = 9, endHour = 17)
        val now = at(DayOfWeek.MONDAY, 12, 0)
        assertTrue(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns false when current time before same-day window start`() {
        val s = schedule(startHour = 9, endHour = 17)
        val now = at(DayOfWeek.MONDAY, 8, 30)
        assertFalse(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns false when current time at same-day window end (end exclusive)`() {
        val s = schedule(startHour = 9, endHour = 17)
        val now = at(DayOfWeek.MONDAY, 17, 0)
        assertFalse(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns true for midnight-crossing window at 23-00`() {
        val s = schedule(
            daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startHour = 22, endHour = 2,
        )
        val now = at(DayOfWeek.MONDAY, 23, 0)
        assertTrue(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns true for midnight-crossing window at 01-30`() {
        val s = schedule(
            daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startHour = 22, endHour = 2,
        )
        val now = at(DayOfWeek.MONDAY, 1, 30)
        assertTrue(isCurrentlyFiring(s, now))
    }

    @Test
    fun `isCurrentlyFiring returns false outside midnight-crossing window at 10-00`() {
        val s = schedule(
            daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startHour = 22, endHour = 2,
        )
        val now = at(DayOfWeek.MONDAY, 10, 0)
        assertFalse(isCurrentlyFiring(s, now))
    }
}

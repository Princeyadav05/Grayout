package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleOverlapTest {

    private fun schedule(
        days: String,
        startHour: Int,
        endHour: Int,
        startMinute: Int = 0,
        endMinute: Int = 0,
        isEnabled: Boolean = true,
    ) = Schedule(
        id = 0L,
        name = "s",
        daysOfWeek = days,
        startTimeHour = startHour,
        startTimeMinute = startMinute,
        endTimeHour = endHour,
        endTimeMinute = endMinute,
        isEnabled = isEnabled,
    )

    @Test
    fun `same day overlapping windows overlap`() {
        val a = schedule("MON", 9, 12)
        val b = schedule("MON", 11, 13)
        assertTrue(schedulesOverlap(a, b))
    }

    @Test
    fun `same day back-to-back windows do not overlap`() {
        val a = schedule("MON", 9, 10)
        val b = schedule("MON", 10, 11)
        assertFalse("half-open windows touching at 10:00 must not overlap", schedulesOverlap(a, b))
    }

    @Test
    fun `different days same time do not overlap`() {
        val a = schedule("MON", 9, 17)
        val b = schedule("TUE", 9, 17)
        assertFalse(schedulesOverlap(a, b))
    }

    @Test
    fun `adjacent-day overnight spillover overlaps despite disjoint day sets`() {
        // MON 22:00 -> TUE 02:00 vs TUE 01:00 -> 03:00 share TUE 01:00-02:00.
        val mon = schedule("MON", 22, 2)
        val tue = schedule("TUE", 1, 3)
        assertTrue("MON overnight tail must clash with TUE morning window", schedulesOverlap(mon, tue))
    }

    @Test
    fun `adjacent-day overnight tail ending before next window does not overlap`() {
        // MON 22:00 -> TUE 01:00 vs TUE 01:00 -> 03:00 touch exactly at 01:00 (half-open).
        val mon = schedule("MON", 22, 1)
        val tue = schedule("TUE", 1, 3)
        assertFalse(schedulesOverlap(mon, tue))
    }

    @Test
    fun `sunday overnight window spills into monday and overlaps`() {
        // SUN 23:00 -> MON 01:00 vs MON 00:00 -> 02:00 share MON 00:00-01:00 (week wrap).
        val sun = schedule("SUN", 23, 1)
        val mon = schedule("MON", 0, 2)
        assertTrue("SUN->MON wrap must be detected across the week boundary", schedulesOverlap(sun, mon))
    }

    @Test
    fun `two overnight windows on same day overlap`() {
        val a = schedule("MON", 22, 2)
        val b = schedule("MON", 23, 1)
        assertTrue(schedulesOverlap(a, b))
    }

    @Test
    fun `multi-day schedules overlap on a shared day only`() {
        val a = schedule("MON,WED,FRI", 9, 12)
        val b = schedule("WED", 11, 13)
        assertTrue(schedulesOverlap(a, b))
    }

    @Test
    fun `minute precision boundary does not falsely overlap`() {
        val a = schedule("MON", 9, 10, startMinute = 0, endMinute = 30)
        val b = schedule("MON", 10, 11, startMinute = 30, endMinute = 0)
        assertFalse("09:00-10:30 and 10:30-11:00 touch at 10:30", schedulesOverlap(a, b))
    }

    @Test
    fun `overlap ignores enabled state`() {
        val a = schedule("MON", 9, 12, isEnabled = false)
        val b = schedule("MON", 11, 13, isEnabled = true)
        assertTrue("time geometry must not depend on isEnabled", schedulesOverlap(a, b))
    }
}

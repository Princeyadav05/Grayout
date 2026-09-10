package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

class NextScheduleStartInstantTest {
    private val zone = ZoneId.of("America/New_York")

    @Test
    fun `second occurrence of repeated local time cannot select the passed first start`() {
        val now = Instant.parse("2026-11-01T06:15:00Z")
        val schedule = schedule(1, 30, 2, 0)
        assertEquals(
            LocalDateTime.parse("2026-11-08T01:30:00"),
            nextScheduleStart(listOf(schedule), now, zone),
        )
    }

    @Test
    fun `spring gap ranks starts by their resolved instants not the local time text`() {
        val now = Instant.parse("2026-03-08T06:59:00Z")
        val shifted = schedule(2, 30, 4, 0) // Resolves to 03:30.
        val earlier = schedule(3, 15, 4, 0)
        assertEquals(
            LocalDateTime.parse("2026-03-08T03:15:00"),
            nextScheduleStart(listOf(shifted, earlier), now, zone),
        )
    }

    @Test
    fun `spring gap does not advertise an occurrence that alarms skip as collapsed`() {
        val now = Instant.parse("2026-03-08T06:59:00Z")
        val collapsed = schedule(2, 30, 3, 15)
        assertEquals(
            LocalDateTime.parse("2026-03-15T02:30:00"),
            nextScheduleStart(listOf(collapsed), now, zone),
        )
    }

    @Test
    fun `at a resolved start the next occurrence is strictly in the future`() {
        val now = Instant.parse("2026-03-08T07:30:00Z")
        assertEquals(
            LocalDateTime.parse("2026-03-15T02:30:00"),
            nextScheduleStart(listOf(schedule(2, 30, 4, 0)), now, zone),
        )
    }

    private fun schedule(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int) = Schedule(
        name = "Focus",
        daysOfWeek = "SUN",
        startTimeHour = startHour,
        startTimeMinute = startMinute,
        endTimeHour = endHour,
        endTimeMinute = endMinute,
    )
}

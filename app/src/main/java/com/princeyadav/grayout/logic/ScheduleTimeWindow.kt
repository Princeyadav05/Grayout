package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Single source of truth for schedule window math. Called by both
 * ScheduleViewModel (to decide "is this schedule firing now?") and
 * ScheduleAlarmManager (to decide "should grayscale be on right now?").
 *
 * Previously duplicated between those two call sites — a fix in one
 * silently missed the other. Extracted per spec §5.2.
 */

internal fun isCurrentlyFiring(schedule: Schedule, now: LocalDateTime): Boolean {
    if (!schedule.isEnabled) return false
    val days = schedule.daysOfWeekList
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    val current = now.toLocalTime()

    if (start.isBefore(end)) {
        return now.dayOfWeek in days && isTimeWithinWindow(start, end, current)
    }

    return when {
        !current.isBefore(start) -> now.dayOfWeek in days
        current.isBefore(end) -> now.minusDays(1).dayOfWeek in days
        else -> false
    }
}

internal data class ScheduleEvent(
    val dateTime: LocalDateTime,
    val isStart: Boolean,
)

internal fun nextScheduleEvent(
    schedules: List<Schedule>,
    now: LocalDateTime,
): ScheduleEvent? {
    val today = now.toLocalDate()
    var nextEvent: ScheduleEvent? = null

    for (schedule in schedules) {
        if (!schedule.isEnabled) continue

        val days = schedule.daysOfWeekList
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
        val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)

        for (dayOffset in -1L..7L) {
            val startDate = today.plusDays(dayOffset)
            if (startDate.dayOfWeek !in days) continue

            val startDateTime = LocalDateTime.of(startDate, start)
            nextEvent = nextSoonerEvent(nextEvent, startDateTime, isStart = true, now)

            val endDate = if (start.isBefore(end)) startDate else startDate.plusDays(1)
            val endDateTime = LocalDateTime.of(endDate, end)
            nextEvent = nextSoonerEvent(nextEvent, endDateTime, isStart = false, now)
        }
    }

    return nextEvent
}

private fun nextSoonerEvent(
    currentNext: ScheduleEvent?,
    candidateDateTime: LocalDateTime,
    isStart: Boolean,
    now: LocalDateTime,
): ScheduleEvent? {
    if (!candidateDateTime.isAfter(now)) return currentNext
    if (currentNext != null && !candidateDateTime.isBefore(currentNext.dateTime)) return currentNext
    return ScheduleEvent(candidateDateTime, isStart)
}

/**
 * Returns true if [current] falls within the window [start, end).
 * Handles midnight-crossing windows (where end is before start).
 */
internal fun isTimeWithinWindow(start: LocalTime, end: LocalTime, current: LocalTime): Boolean {
    return if (start.isBefore(end)) {
        !current.isBefore(start) && current.isBefore(end)
    } else {
        // Window crosses midnight, e.g. 22:00 to 02:00
        !current.isBefore(start) || current.isBefore(end)
    }
}

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
    if (now.dayOfWeek !in days) return false
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    return isTimeWithinWindow(start, end, now.toLocalTime())
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

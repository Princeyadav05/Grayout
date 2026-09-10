package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId

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
    val scheduleId: Long,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
)

internal fun nextScheduleEvent(
    schedules: List<Schedule>,
    now: LocalDateTime,
): ScheduleEvent? {
    val today = now.toLocalDate()
    var nextEvent: ScheduleEvent? = null
    val activeNow = schedules.any { isCurrentlyFiring(it, now) }

    for (schedule in schedules) {
        if (!schedule.isEnabled) continue

        val days = schedule.daysOfWeekList
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
        val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)

        for (dayOffset in -1L..7L) {
            val startDate = today.plusDays(dayOffset)
            if (startDate.dayOfWeek !in days) continue

            val startDateTime = LocalDateTime.of(startDate, start)
            val endDate = if (start.isBefore(end)) startDate else startDate.plusDays(1)
            val endDateTime = LocalDateTime.of(endDate, end)
            if (!activeNow) {
                nextEvent = nextSoonerEvent(nextEvent,
                    ScheduleEvent(startDateTime, true, schedule.id, startDateTime, endDateTime), now)
            }
            nextEvent = nextSoonerEvent(nextEvent,
                ScheduleEvent(endDateTime, false, schedule.id, startDateTime, endDateTime), now)
        }
    }

    return nextEvent
}

/** Alarm scheduling compares resolved instants, including repeated/gap local times. */
internal fun nextScheduleEvent(
    schedules: List<Schedule>,
    now: Instant,
    zone: ZoneId,
): ScheduleEvent? {
    val today = now.atZone(zone).toLocalDate()
    var next: ScheduleEvent? = null
    var nextInstant: Instant? = null
    // Keep a closing boundary armed during continuous coverage. A delayed
    // interior start in overlapping legacy/DST windows must not lose that end.
    val activeNow = schedules.any { isCurrentlyFiring(it, now, zone) }
    for (schedule in schedules.filter { it.isEnabled }) {
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
        val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
        for (offset in -1L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in schedule.daysOfWeekList) continue
            val from = date.atTime(start)
            val until = (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end)
            val fromInstant = from.atZone(zone).toInstant()
            val untilInstant = until.atZone(zone).toInstant()
            if (!fromInstant.isBefore(untilInstant)) continue
            for (isStart in listOf(true, false)) {
                if (isStart && activeNow) continue
                val instant = if (isStart) fromInstant else untilInstant
                if (!instant.isAfter(now)) continue
                if (nextInstant == null || instant.isBefore(nextInstant) ||
                    (instant == nextInstant && !isStart && next?.isStart == true)
                ) {
                    next = ScheduleEvent(if (isStart) from else until, isStart, schedule.id, from, until)
                    nextInstant = instant
                }
            }
        }
    }
    return next
}

/** Uses the same gap/overlap resolution as the actual AlarmManager registration. */
internal fun isCurrentlyFiring(schedule: Schedule, now: Instant, zone: ZoneId): Boolean {
    if (!schedule.isEnabled) return false
    val today = now.atZone(zone).toLocalDate()
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    return listOf(today.minusDays(1), today).any { date ->
        if (date.dayOfWeek !in schedule.daysOfWeekList) false else {
            val from = date.atTime(start).atZone(zone).toInstant()
            val until = (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end).atZone(zone).toInstant()
            !now.isBefore(from) && now.isBefore(until)
        }
    }
}

/**
 * The soonest future window-start across [schedules], or null if none is upcoming.
 *
 * Starts are day-anchored (a window always starts on one of its own days), so a
 * plain 0..7 forward scan is correct here — unlike the end-of-window math in
 * [nextScheduleEvent], no yesterday offset is needed. Kept in logic/ so the Home
 * screen's "next schedule" text and any other caller share one implementation.
 */
internal fun nextScheduleStart(
    schedules: List<Schedule>,
    now: LocalDateTime,
): LocalDateTime? {
    val today = now.toLocalDate()
    var soonest: LocalDateTime? = null

    for (schedule in schedules) {
        if (!schedule.isEnabled) continue
        val days = schedule.daysOfWeekList
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)

        for (dayOffset in 0L..7L) {
            val startDate = today.plusDays(dayOffset)
            if (startDate.dayOfWeek !in days) continue
            val startDateTime = LocalDateTime.of(startDate, start)
            if (startDateTime.isAfter(now)) {
                if (soonest == null || startDateTime.isBefore(soonest)) soonest = startDateTime
                break
            }
        }
    }

    return soonest
}

private fun nextSoonerEvent(
    currentNext: ScheduleEvent?,
    candidate: ScheduleEvent,
    now: LocalDateTime,
): ScheduleEvent? {
    if (!candidate.dateTime.isAfter(now)) return currentNext
    if (currentNext == null || candidate.dateTime.isBefore(currentNext.dateTime)) return candidate
    // Keep the closing boundary. Its delivery catches up to an active next window,
    // or turns gray off if both windows have ended before Android delivers it.
    if (candidate.dateTime == currentNext.dateTime && !candidate.isStart && currentNext.isStart) return candidate
    return currentNext
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

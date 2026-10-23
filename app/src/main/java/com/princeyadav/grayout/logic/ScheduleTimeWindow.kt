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

internal data class ScheduleEvent(
    val dateTime: LocalDateTime,
    val isStart: Boolean,
    val scheduleId: Long,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
)

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
        val days = schedule.daysOfWeekList
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
        val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
        for (offset in -1L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) continue
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
    val days = schedule.daysOfWeekList
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    return listOf(today.minusDays(1), today).any { date ->
        if (date.dayOfWeek !in days) false else {
            val from = date.atTime(start).atZone(zone).toInstant()
            val until = (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end).atZone(zone).toInstant()
            !now.isBefore(from) && now.isBefore(until)
        }
    }
}

/**
 * Future starts compared as resolved instants, matching alarm gap/overlap rules.
 * Return the configured local time for display; callers resolve that same value
 * in [zone] for the timer. A repeated local start only fires on its first offset.
 */
internal fun nextScheduleStart(
    schedules: List<Schedule>,
    now: Instant,
    zone: ZoneId,
): LocalDateTime? {
    val today = now.atZone(zone).toLocalDate()
    var soonest: LocalDateTime? = null
    var soonestInstant: Instant? = null
    for (schedule in schedules) {
        if (!schedule.isEnabled) continue
        val days = schedule.daysOfWeekList
        val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
        val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
        for (offset in 0L..7L) {
            val date = today.plusDays(offset)
            if (date.dayOfWeek !in days) continue
            val from = date.atTime(start)
            val until = (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end)
            val fromInstant = from.atZone(zone).toInstant()
            val untilInstant = until.atZone(zone).toInstant()
            // A DST gap can reverse/collapse an otherwise valid local window.
            if (!fromInstant.isBefore(untilInstant) || !fromInstant.isAfter(now)) continue
            if (soonestInstant == null || fromInstant.isBefore(soonestInstant)) {
                soonest = from
                soonestInstant = fromInstant
            }
            break
        }
    }
    return soonest
}

package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * What Home says about schedules: the one running now (with its configured end), or the soonest
 * upcoming start. [at] is the configured local time for display, like [nextScheduleStart].
 */
internal data class ScheduleLine(val scheduleId: Long, val name: String, val at: LocalDateTime, val isActive: Boolean)

/** Index into the theme's 3-color schedule palette. Derived from the id, so it never shifts when others are deleted. */
internal fun scheduleColorIndex(id: Long): Int = Math.floorMod(id - 1, 3L).toInt()

internal fun scheduleLine(schedules: List<Schedule>, now: Instant, zone: ZoneId): ScheduleLine? {
    val enabled = schedules.filter { it.isEnabled }
    for (schedule in enabled) {
        val current = windows(schedule, now, zone).firstOrNull { !now.isBefore(it.from) && now.isBefore(it.until) }
        if (current != null) return ScheduleLine(schedule.id, schedule.name, current.localEnd, isActive = true)
    }
    return enabled
        .mapNotNull { s -> nextScheduleStart(listOf(s), now, zone)?.let { s to it } }
        .minByOrNull { (_, start) -> start.atZone(zone).toInstant() }
        ?.let { (s, start) -> ScheduleLine(s.id, s.name, start, isActive = false) }
}

/**
 * The next instant the [scheduleLine] answer can change: any enabled window's start or end.
 * Per-schedule and instant-based, so overlapping legacy windows and repeated DST hours never
 * produce a past wake-up (which would spin the Home timer).
 */
internal fun nextScheduleLineChange(schedules: List<Schedule>, now: Instant, zone: ZoneId): Instant? =
    schedules.filter { it.isEnabled }
        .flatMap { windows(it, now, zone) }
        .flatMap { listOf(it.from, it.until) }
        .filter { it.isAfter(now) }
        .minOrNull()

private class Window(val from: Instant, val until: Instant, val localEnd: LocalDateTime)

/** Windows from yesterday through a week ahead, resolved like [isCurrentlyFiring]. */
private fun windows(schedule: Schedule, now: Instant, zone: ZoneId): List<Window> {
    val today = now.atZone(zone).toLocalDate()
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    return (-1L..7L).mapNotNull { offset ->
        val date = today.plusDays(offset)
        if (date.dayOfWeek !in schedule.daysOfWeekList) return@mapNotNull null
        val localEnd = (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end)
        val from = date.atTime(start).atZone(zone).toInstant()
        val until = localEnd.atZone(zone).toInstant()
        if (from.isBefore(until)) Window(from, until, localEnd) else null
    }
}

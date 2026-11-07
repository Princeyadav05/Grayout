package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * What Home says about schedules: the one running now (with its configured end), or the soonest
 * upcoming start. [at] is the configured local time for display, like [nextScheduleStart].
 */
internal data class ScheduleLine(val scheduleId: Long, val name: String, val at: LocalDateTime, val isActive: Boolean)

/** Index into the theme's 3-color schedule palette. Derived from the id, so it never shifts when others are deleted. */
internal fun scheduleColorIndex(id: Long): Int = Math.floorMod(id - 1, 3L).toInt()

/** Every enabled schedule running now, each with its configured end, in list order. Legacy windows can overlap. */
internal fun activeScheduleLines(schedules: List<Schedule>, now: Instant, zone: ZoneId): List<ScheduleLine> =
    schedules.filter { it.isEnabled }.mapNotNull { schedule ->
        windows(schedule, now, zone)
            .firstOrNull { !now.isBefore(it.from) && now.isBefore(it.until) }
            ?.let { ScheduleLine(schedule.id, schedule.name, it.localEnd, isActive = true) }
    }

/** "Focus until 10:00 AM, Night until 6:00 AM": each running schedule named with its formatted end. */
internal fun activeSchedulesText(namesAndEnds: List<Pair<String, String>>): String =
    namesAndEnds.joinToString(", ") { (name, end) -> "$name until $end" }

internal fun scheduleLine(schedules: List<Schedule>, now: Instant, zone: ZoneId): ScheduleLine? {
    activeScheduleLines(schedules, now, zone).firstOrNull()?.let { return it }
    val enabled = schedules.filter { it.isEnabled }
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

/**
 * True when a window ends on the next calendar day (22:00 to 07:00). Same rule as [windows]: an end
 * that is not after the start rolls to the next day, so equal times mean a full 24 hours.
 */
internal fun endsNextDay(startHour: Int, startMinute: Int, endHour: Int, endMinute: Int): Boolean =
    endHour * 60 + endMinute <= startHour * 60 + startMinute

private val WEEKDAYS = DayOfWeek.entries.take(5).toSet()
private val WEEKEND = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)

/** "Every day", "Weekdays", "Weekends", or "Mon, Wed, Fri" in Monday-first order. */
internal fun daysInWords(days: Collection<DayOfWeek>): String {
    val set = days.toSet()
    return when {
        set.size == 7 -> "Every day"
        set == WEEKDAYS -> "Weekdays"
        set == WEEKEND -> "Weekends"
        set.isEmpty() -> "No days"
        else -> DayOfWeek.entries.filter { it in set }
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) }
    }
}

/** A schedule's share of today, as fractions of the real day length (23 or 25 hours on DST days). */
internal data class DaySegment(val scheduleId: Long, val start: Float, val end: Float)

/** Everything the Today strip shows: [now] as a fraction of the day, the windows, and the Home-style line. */
internal data class TodayState(
    val date: LocalDate,
    val now: Float,
    val segments: List<DaySegment>,
    val line: ScheduleLine?,
    /** Every schedule running now; more than one only when legacy windows overlap. */
    val active: List<ScheduleLine> = emptyList(),
)

internal fun todayState(schedules: List<Schedule>, now: Instant, zone: ZoneId): TodayState {
    val date = now.atZone(zone).toLocalDate()
    val dayStart = date.atStartOfDay(zone).toInstant()
    val dayEnd = date.plusDays(1).atStartOfDay(zone).toInstant()
    val dayMillis = Duration.between(dayStart, dayEnd).toMillis().toFloat()
    fun fraction(at: Instant) = Duration.between(dayStart, at).toMillis() / dayMillis

    val segments = schedules.filter { it.isEnabled }.flatMap { schedule ->
        windows(schedule, now, zone)
            .filter { it.until.isAfter(dayStart) && it.from.isBefore(dayEnd) }
            .map {
                DaySegment(
                    scheduleId = schedule.id,
                    start = fraction(maxOf(it.from, dayStart)),
                    end = fraction(minOf(it.until, dayEnd)),
                )
            }
    }
    return TodayState(date, fraction(now), segments, scheduleLine(schedules, now, zone), activeScheduleLines(schedules, now, zone))
}

/** A [segment] drawn in lane [lane] of [lanes] equal horizontal lanes; [lanes] is 1 unless it overlaps another. */
internal data class LanedSegment(val segment: DaySegment, val lane: Int, val lanes: Int)

/**
 * Splits the Today strip into lanes only where windows overlap, so no segment covers another.
 * Overlapping segments form a group; each gets the first lane free at its start, and the whole
 * group shares the group's lane count. Touching segments (one ends where the next starts) do not overlap.
 */
internal fun stripLanes(segments: List<DaySegment>): List<LanedSegment> {
    val sorted = segments.sortedWith(compareBy({ it.start }, { it.end }))
    val result = mutableListOf<LanedSegment>()
    var group = mutableListOf<Pair<DaySegment, Int>>()
    val laneEnds = mutableListOf<Float>()
    var groupEnd = Float.NEGATIVE_INFINITY

    fun closeGroup() {
        group.forEach { (segment, lane) -> result += LanedSegment(segment, lane, laneEnds.size) }
        group = mutableListOf()
        laneEnds.clear()
    }

    sorted.forEach { segment ->
        if (segment.start >= groupEnd) closeGroup()
        val free = laneEnds.indexOfFirst { it <= segment.start }
        val lane = if (free >= 0) free else laneEnds.size.also { laneEnds += 0f }
        laneEnds[lane] = segment.end
        group += segment to lane
        groupEnd = maxOf(groupEnd, segment.end)
    }
    closeGroup()
    return result
}

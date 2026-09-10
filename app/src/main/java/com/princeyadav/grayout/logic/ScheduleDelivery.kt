package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneId

/** Null means preserve display/manual/exclusion state, including deferred targets. */
internal fun scheduleDeliveryTarget(
    event: ScheduleEvent,
    schedules: List<Schedule>,
    now: Instant,
    zone: ZoneId,
    startInstant: Instant = event.windowStart.atZone(zone).toInstant(),
    endInstant: Instant = event.windowEnd.atZone(zone).toInstant(),
): Boolean? {
    val origin = schedules.firstOrNull { it.id == event.scheduleId && it.isEnabled } ?: return null
    val start = LocalTime.of(origin.startTimeHour, origin.startTimeMinute)
    val end = LocalTime.of(origin.endTimeHour, origin.endTimeMinute)
    val startDate = event.windowStart.toLocalDate()
    val expectedEnd = (if (start.isBefore(end)) startDate else startDate.plusDays(1)).atTime(end)
    if (event.windowStart != startDate.atTime(start) || event.windowEnd != expectedEnd ||
        startDate.dayOfWeek !in origin.daysOfWeekList
    ) return null
    val boundary = if (event.isStart) event.windowStart else event.windowEnd
    val boundaryInstant = if (event.isStart) startInstant else endInstant
    if (event.dateTime != boundary || !startInstant.isBefore(endInstant) || now.isBefore(boundaryInstant)) return null
    return if (event.isStart) {
        // An expired start cannot borrow a later window to justify changing state.
        // If the zone changed, it must also be inside this civil occurrence in
        // the current zone. This permits valid late starts without enabling early
        // or after the newly interpreted local end.
        val currentStart = event.windowStart.atZone(zone).toInstant()
        val currentEnd = event.windowEnd.atZone(zone).toInstant()
        if (now.isBefore(endInstant) && !now.isBefore(currentStart) && now.isBefore(currentEnd)) true else null
    } else {
        // A valid end may be the only delivered boundary between touching/delayed
        // windows. Resolve directly to ON if one is active, never OFF then ON.
        schedules.any { isCurrentlyFiring(it, now, zone) }
    }
}

/** Compatibility for an already-armed pre-upgrade intent with no occurrence data. */
internal fun legacyScheduleDeliveryTarget(
    isStart: Boolean?,
    schedules: List<Schedule>,
    now: Instant,
    zone: ZoneId,
): Boolean? {
    if (isStart == null || schedules.none { it.isEnabled }) return null
    val active = schedules.any { isCurrentlyFiring(it, now, zone) }
    return if (isStart && !active) null else active
}

package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.scheduling.ArmedScheduleAlarm
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/** System changes act like boundaries, rather than reasserting every active schedule. */
internal fun scheduleReconciliationTarget(
    armed: ArmedScheduleAlarm?,
    schedules: List<Schedule>,
    now: Instant,
    zone: ZoneId,
): Boolean? {
    val active = schedules.any { isCurrentlyFiring(it, now, zone) }
    val origin = armed?.let { alarm -> schedules.firstOrNull { alarm.matches(it) } }
    // A pending end is evidence of previously active coverage. A stale/deleted
    // configuration, upcoming start, or absent registration cannot claim manual gray.
    if (armed == null || armed.isStart || origin == null) return if (active) true else null
    if (!active) return false

    val from = armed.windowStart.atZone(zone).toInstant()
    val until = armed.windowEnd.atZone(zone).toInstant()
    // Keep manual color and deferred exclusion targets during the same occurrence.
    // A crossed closing boundary or a different occurrence is a new schedule decision.
    return if (!now.isBefore(from) && now.isBefore(until) &&
        now.isBefore(Instant.ofEpochMilli(armed.endMillis))) null else true
}

private fun ArmedScheduleAlarm.matches(schedule: Schedule): Boolean {
    if (!schedule.isEnabled || schedule.id != scheduleId) return false
    val start = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val end = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    val date = windowStart.toLocalDate()
    return date.dayOfWeek in schedule.daysOfWeekList && windowStart == date.atTime(start) &&
        windowEnd == (if (start.isBefore(end)) date else date.plusDays(1)).atTime(end)
}

/** Names do not affect schedule behavior; all timing/enabled changes invalidate retries. */
internal fun scheduleConfiguration(schedules: List<Schedule>): String = schedules.sortedBy { it.id }
    .joinToString(";") {
        "${it.id}:${it.isEnabled}:${it.daysOfWeekList.sorted().joinToString(",")}:" +
            "${it.startTimeHour}:${it.startTimeMinute}:${it.endTimeHour}:${it.endTimeMinute}"
    }

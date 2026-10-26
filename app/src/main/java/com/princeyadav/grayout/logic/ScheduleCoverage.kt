package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import java.time.Instant
import java.time.ZoneId

/**
 * A synchronous snapshot of the enabled schedules' windows, so code that runs under
 * GrayscaleStateLock (receivers, the service observer) can ask "is a schedule running
 * at this instant?" without Room. Evaluated in the current zone with the same rules as
 * the alarms, so nested legacy windows, adjacency, overnight windows, DST and missed
 * boundary deliveries are all answered from wall-clock rules, not from alarm delivery.
 */
internal fun encodeCoverage(schedules: List<Schedule>): String =
    schedules.filter { it.isEnabled }.joinToString(";") {
        "${it.daysOfWeek}|${it.startTimeHour}:${it.startTimeMinute}|${it.endTimeHour}:${it.endTimeMinute}"
    }

/** Throwaway enabled schedules (no name/id), as ScheduleRepository builds for overlap checks. Malformed entries are dropped. */
internal fun decodeCoverage(raw: String?): List<Schedule> =
    raw.orEmpty().split(';').filter { it.isNotBlank() }.mapNotNull { entry ->
        val parts = entry.split('|')
        if (parts.size != 3) return@mapNotNull null
        val start = parts[1].split(':').mapNotNull { it.toIntOrNull() }
        val end = parts[2].split(':').mapNotNull { it.toIntOrNull() }
        if (start.size != 2 || end.size != 2) return@mapNotNull null
        if (start[0] !in 0..23 || end[0] !in 0..23 || start[1] !in 0..59 || end[1] !in 0..59) return@mapNotNull null
        Schedule(
            name = "",
            daysOfWeek = parts[0],
            startTimeHour = start[0],
            startTimeMinute = start[1],
            endTimeHour = end[0],
            endTimeMinute = end[1],
            isEnabled = true,
        )
    }

internal fun isCovered(windows: List<Schedule>, at: Instant, zone: ZoneId): Boolean =
    windows.any { isCurrentlyFiring(it, at, zone) }

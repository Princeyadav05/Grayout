package com.princeyadav.grayout.logic

import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList

private const val MINUTES_PER_DAY = 1440
private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY

/**
 * True if two schedules ever cover the same instant on the weekly clock.
 *
 * Each schedule is expanded into half-open arcs [start, start + length) on a
 * circular week of [MINUTES_PER_WEEK] minutes, one arc per selected day. A
 * window that crosses midnight (end <= start) spills into the following day,
 * and Sunday spills into Monday via the circular wrap — so an overnight window
 * on one day can overlap a window on the *next* day even though their day sets
 * share no label (the flaw a literal day-set intersection missed).
 *
 * Half-open comparison means back-to-back windows (one's end == the other's
 * start) do not count as overlapping, matching the editor's adjacency rule.
 *
 * [isEnabled] is intentionally ignored: this is pure time geometry.
 */
internal fun schedulesOverlap(a: Schedule, b: Schedule): Boolean {
    val aArcs = weeklyArcs(a)
    val bArcs = weeklyArcs(b)
    return aArcs.any { arcA -> bArcs.any { arcB -> arcsOverlap(arcA, arcB) } }
}

/** Half-open arc [start, start + length) in absolute minutes-of-week; start in 0 until week, 0 < length <= day. */
private data class WeekArc(val start: Int, val length: Int)

private fun weeklyArcs(schedule: Schedule): List<WeekArc> {
    val startMin = schedule.startTimeHour * 60 + schedule.startTimeMinute
    val endMin = schedule.endTimeHour * 60 + schedule.endTimeMinute
    // end > start: same-day window. Otherwise the window crosses midnight, and
    // start == end is treated as a full 24h window (the runtime's own reading in
    // isCurrentlyFiring); the editor rejects that case before it reaches here.
    val length = if (endMin > startMin) endMin - startMin else MINUTES_PER_DAY - startMin + endMin
    return schedule.daysOfWeekList.map { day ->
        val dayIndex = day.value - 1 // MONDAY(1) -> 0
        val start = (dayIndex * MINUTES_PER_DAY + startMin) % MINUTES_PER_WEEK
        WeekArc(start, length)
    }
}

private fun arcsOverlap(a: WeekArc, b: WeekArc): Boolean {
    // Rebase so a occupies [0, a.length); place b at its circular offset from a.
    // Since arc lengths are <= one day (< a full week), a never wraps once
    // rebased, so only b can spill past the week boundary into a trailing tail.
    val bOffset = Math.floorMod(b.start - a.start, MINUTES_PER_WEEK)
    if (linearOverlap(0, a.length, bOffset, bOffset + b.length)) return true
    val tail = bOffset + b.length - MINUTES_PER_WEEK
    return tail > 0 && linearOverlap(0, a.length, 0, tail)
}

private fun linearOverlap(startA: Int, endA: Int, startB: Int, endB: Int): Boolean =
    startA < endB && startB < endA

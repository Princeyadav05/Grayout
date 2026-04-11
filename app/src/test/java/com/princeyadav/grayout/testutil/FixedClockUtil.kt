package com.princeyadav.grayout.testutil

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Construct a LocalDateTime at a fixed day-of-week + hour/minute, pinned to an arbitrary fixed date. */
fun fixedDateTime(dayOfWeek: DayOfWeek, hour: Int, minute: Int = 0): LocalDateTime {
    // 2026-04-13 is a Monday; offset from there
    val monday = LocalDate.of(2026, 4, 13)
    val target = monday.plusDays((dayOfWeek.value - DayOfWeek.MONDAY.value).toLong())
    return LocalDateTime.of(target, LocalTime.of(hour, minute))
}

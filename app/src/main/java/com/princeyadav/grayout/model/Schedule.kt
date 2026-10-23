package com.princeyadav.grayout.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.DayOfWeek

@Entity(tableName = "schedules")
data class Schedule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val daysOfWeek: String,
    val startTimeHour: Int,
    val startTimeMinute: Int,
    val endTimeHour: Int,
    val endTimeMinute: Int,
    val isEnabled: Boolean = true,
)

val Schedule.daysOfWeekList: List<DayOfWeek>
    get() = daysOfWeek.split(",")
        .mapNotNull { abbr ->
            val trimmed = abbr.trim().uppercase()
            if (trimmed.isEmpty()) null
            else DayOfWeek.entries.firstOrNull { it.name.startsWith(trimmed) }
        }

/** Matches the device 12/24-hour setting, as the editor's time picker does. */
fun formatTime(hour: Int, minute: Int, is24Hour: Boolean): String {
    if (is24Hour) return "%02d:%02d".format(hour, minute)
    val period = if (hour < 12) "AM" else "PM"
    val displayHour = when (hour % 12) {
        0 -> 12
        else -> hour % 12
    }
    return "%d:%02d %s".format(displayHour, minute, period)
}

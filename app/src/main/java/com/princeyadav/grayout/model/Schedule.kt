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

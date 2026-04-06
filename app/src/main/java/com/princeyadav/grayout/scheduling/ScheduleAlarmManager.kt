package com.princeyadav.grayout.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.ScheduleRepository
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ScheduleAlarmManager(private val context: Context) {

    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun reschedule(repository: ScheduleRepository) {
        cancelAll()

        val enabledSchedules = repository.getEnabledSchedules()
        if (enabledSchedules.isEmpty()) return

        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        val currentTime = now.toLocalTime()

        var nextEventTime: LocalDateTime? = null
        var nextIsStart = true

        for (schedule in enabledSchedules) {
            val days = schedule.daysOfWeek.split(",").map { dayStr ->
                DayOfWeek.valueOf(dayStr.trim())
            }
            val schedStart = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
            val schedEnd = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)

            for (dayOffset in 0L..7L) {
                val checkDate = today.plusDays(dayOffset)
                val checkDow = checkDate.dayOfWeek

                if (checkDow !in days) continue

                val startDt = LocalDateTime.of(checkDate, schedStart)
                if (startDt.isAfter(now)) {
                    if (nextEventTime == null || startDt.isBefore(nextEventTime)) {
                        nextEventTime = startDt
                        nextIsStart = true
                    }
                }

                val endDate = if (schedStart.isAfter(schedEnd) || schedStart == schedEnd) {
                    checkDate.plusDays(1)
                } else {
                    checkDate
                }
                val endDt = LocalDateTime.of(endDate, schedEnd)
                if (endDt.isAfter(now)) {
                    if (nextEventTime == null || endDt.isBefore(nextEventTime)) {
                        nextEventTime = endDt
                        nextIsStart = false
                    }
                }
            }
        }

        if (nextEventTime != null) {
            val epochMillis = nextEventTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            setExactAlarm(epochMillis, nextIsStart)
        }

        val isCurrentlyInSchedule = enabledSchedules.any { schedule ->
            val days = schedule.daysOfWeek.split(",").map { DayOfWeek.valueOf(it.trim()) }
            if (now.dayOfWeek !in days) return@any false

            val schedStart = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
            val schedEnd = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)

            if (schedStart.isBefore(schedEnd)) {
                currentTime.isAfter(schedStart) && currentTime.isBefore(schedEnd)
            } else {
                currentTime.isAfter(schedStart) || currentTime.isBefore(schedEnd)
            }
        }

        val stateIntent = Intent(context, ScheduleReceiver::class.java).apply {
            action = if (isCurrentlyInSchedule) ACTION_SCHEDULE_START else ACTION_SCHEDULE_END
        }
        context.sendBroadcast(stateIntent)
    }

    private fun setExactAlarm(triggerAtMillis: Long, isStart: Boolean) {
        val intent = Intent(context, ScheduleReceiver::class.java).apply {
            action = if (isStart) ACTION_SCHEDULE_START else ACTION_SCHEDULE_END
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent,
        )
    }

    private fun cancelAll() {
        val intent = Intent(context, ScheduleReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
        )
        pendingIntent?.let { alarmManager.cancel(it) }
    }

    companion object {
        const val ACTION_SCHEDULE_START = "com.princeyadav.grayout.SCHEDULE_START"
        const val ACTION_SCHEDULE_END = "com.princeyadav.grayout.SCHEDULE_END"
        private const val REQUEST_CODE = 1001
    }
}

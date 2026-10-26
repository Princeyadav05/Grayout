package com.princeyadav.grayout.scheduling

import android.content.Context
import android.content.SharedPreferences
import com.princeyadav.grayout.logic.decodeCoverage
import com.princeyadav.grayout.logic.encodeCoverage
import com.princeyadav.grayout.logic.isCovered
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementPrefs
import java.time.Instant
import com.princeyadav.grayout.logic.ScheduleEvent
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.UUID

/** Persistent provenance for the one alarm that is currently allowed to act. */
internal data class ArmedScheduleAlarm(
    val generation: String,
    val scheduleId: Long,
    val startMillis: Long,
    val endMillis: Long,
    val isStart: Boolean,
    val windowStart: LocalDateTime,
    val windowEnd: LocalDateTime,
    val zoneId: String,
) {
    val triggerMillis: Long get() = if (isStart) startMillis else endMillis

    fun event(): ScheduleEvent = ScheduleEvent(if (isStart) windowStart else windowEnd,
        isStart, scheduleId, windowStart, windowEnd)

    companion object {
        fun create(event: ScheduleEvent, zone: ZoneId = ZoneId.systemDefault()) = ArmedScheduleAlarm(
            UUID.randomUUID().toString(), event.scheduleId,
            event.windowStart.atZone(zone).toInstant().toEpochMilli(),
            event.windowEnd.atZone(zone).toInstant().toEpochMilli(), event.isStart,
            event.windowStart, event.windowEnd, zone.id,
        )
    }
}

internal class ScheduleAlarmState(private val prefs: SharedPreferences) {
    fun isInitialized(): Boolean = prefs.getBoolean(INITIALIZED, false)

    fun read(): ArmedScheduleAlarm? {
        val generation = prefs.getString(GENERATION, null) ?: return null
        return runCatching {
            ArmedScheduleAlarm(generation, prefs.getLong(ID, 0),
                prefs.getLong(START, 0), prefs.getLong(END, 0), prefs.getBoolean(IS_START, false),
                LocalDateTime.parse(prefs.getString(LOCAL_START, null)),
                LocalDateTime.parse(prefs.getString(LOCAL_END, null)),
                checkNotNull(prefs.getString(ZONE, null)))
        }.getOrNull()
    }

    /** Called on IO before registering the system alarm, so process death cannot lose its identity. */
    fun save(alarm: ArmedScheduleAlarm?) {
        // Keep the tombstone even when no future alarm exists. Old kind-only
        // deliveries must not become trusted again after the last schedule is removed.
        val editor = prefs.edit().putBoolean(INITIALIZED, true)
        if (alarm == null) {
            editor.remove(GENERATION).remove(ID).remove(START).remove(END).remove(IS_START)
                .remove(LOCAL_START).remove(LOCAL_END).remove(ZONE)
        } else {
            editor.putString(GENERATION, alarm.generation).putLong(ID, alarm.scheduleId)
                .putLong(START, alarm.startMillis).putLong(END, alarm.endMillis)
                .putBoolean(IS_START, alarm.isStart)
                .putString(LOCAL_START, alarm.windowStart.toString())
                .putString(LOCAL_END, alarm.windowEnd.toString()).putString(ZONE, alarm.zoneId)
        }
        check(editor.commit()) { "Could not persist schedule alarm registration" }
    }

    companion object {
        private const val GENERATION = "schedule_alarm_generation"
        private const val INITIALIZED = "schedule_alarm_state_initialized"
        private const val ID = "schedule_alarm_origin_id"
        private const val START = "schedule_alarm_window_start"
        private const val END = "schedule_alarm_window_end"
        private const val IS_START = "schedule_alarm_is_start"
        private const val LOCAL_START = "schedule_alarm_local_start"
        private const val LOCAL_END = "schedule_alarm_local_end"
        private const val ZONE = "schedule_alarm_zone"
    }
}

private const val KEY_COVERAGE = "schedule_coverage_snapshot"

/** Written by every reschedule/reconcile, before alarms are armed. See logic/ScheduleCoverage.kt. */
internal fun SharedPreferences.saveScheduleCoverage(schedules: List<Schedule>) {
    edit().putString(KEY_COVERAGE, encodeCoverage(schedules)).commit()
}

/** Whether any schedule is enabled at all, for the "waiting" notification copy. */
internal fun Context.hasScheduleCoverage(): Boolean =
    decodeCoverage(getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE).getString(KEY_COVERAGE, null))
        .isNotEmpty()

/** Whether an enabled schedule window covers [wallMillis], in the device's current zone. */
internal fun Context.inScheduleWindowAt(wallMillis: Long): Boolean {
    val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    return isCovered(decodeCoverage(prefs.getString(KEY_COVERAGE, null)), Instant.ofEpochMilli(wallMillis), ZoneId.systemDefault())
}

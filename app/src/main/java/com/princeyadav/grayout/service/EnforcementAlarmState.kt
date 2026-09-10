package com.princeyadav.grayout.service

import android.content.Context
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.os.SystemClock
import java.util.UUID

/** A monotonic countdown belongs to one boot, and survives process/permission restarts. */
internal class EnforcementAlarmState(private val prefs: SharedPreferences) {
    fun deadline(bootCount: Int, intervalMinutes: Int? = null): Long? =
        if (bootCount >= 0 && prefs.getInt(BOOT, -1) == bootCount && prefs.contains(DEADLINE) &&
            (intervalMinutes == null || prefs.getInt(INTERVAL, -1) == intervalMinutes)) {
            prefs.getLong(DEADLINE, 0L)
        } else null

    fun hasRecord(): Boolean = prefs.contains(DEADLINE) || prefs.contains(BOOT) ||
        prefs.contains(INTERVAL) || prefs.contains(GENERATION)

    fun isInitialized(): Boolean = prefs.getBoolean(INITIALIZED, false)

    fun generation(): String? = prefs.getString(GENERATION, null)

    fun acceptsDelivery(generation: String?, bootCount: Int, intervalMinutes: Int, nowElapsedMs: Long): Boolean {
        if (intervalMinutes <= 0) return false
        if (generation == null) {
            // Older APKs had action-only alarms. Once we register or cancel a
            // new-format alarm, those payloads can never become trusted again.
            if (isInitialized()) return false
            val legacyDeadline = deadline(bootCount, intervalMinutes)
            return if (hasRecord()) legacyDeadline != null && nowElapsedMs >= legacyDeadline else true
        }
        if (generation != this.generation()) return false
        val deadline = deadline(bootCount, intervalMinutes) ?: return false
        // PendingIntent extras can be updated after an older OS alarm becomes
        // due. Even a matching identity must never fire before its new deadline.
        return nowElapsedMs >= deadline
    }

    fun save(deadlineElapsedMs: Long, bootCount: Int, intervalMinutes: Int = EnforcementPrefs(prefs).getInterval()): String {
        val generation = generation()?.takeIf { deadline(bootCount, intervalMinutes) == deadlineElapsedMs }
            ?: UUID.randomUUID().toString()
        // Permission revocation can kill the process immediately after registration.
        // These infrequent alarm transitions must reach disk before the OS alarm does.
        // Do not return early for equal in-memory values: an earlier failed
        // commit may have updated memory without making those values durable.
        check(prefs.edit().putLong(DEADLINE, deadlineElapsedMs).putInt(BOOT, bootCount)
            .putInt(INTERVAL, intervalMinutes).putString(GENERATION, generation)
            .putBoolean(INITIALIZED, true).commit()) {
            "Could not persist enforcement countdown"
        }
        return generation
    }

    fun clear() {
        check(prefs.edit().remove(DEADLINE).remove(BOOT).remove(INTERVAL).remove(GENERATION)
            .putBoolean(INITIALIZED, true).commit()) {
            "Could not clear enforcement countdown"
        }
    }

    private companion object {
        const val DEADLINE = "enforcement_alarm_elapsed_deadline"
        const val BOOT = "enforcement_alarm_boot_count"
        const val INTERVAL = "enforcement_alarm_interval_minutes"
        const val GENERATION = "enforcement_alarm_generation"
        const val INITIALIZED = "enforcement_alarm_protocol_initialized"
    }
}

internal fun Context.enforcementAlarmState() = EnforcementAlarmState(
    getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE),
)

internal fun Context.bootCount(): Int = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)

/** Regrant cancels OS alarms, so a surviving PendingIntent alone is not sufficient evidence. */
internal fun restoreEnforcementAlarm(context: Context) = synchronized(GrayscaleStateLock) {
    val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    val interval = EnforcementPrefs(prefs).getInterval()
    val state = context.enforcementAlarmState()
    if (interval <= 0) {
        cancelEnforcementCountdown(context)
        return@synchronized
    }
    val saved = state.deadline(context.bootCount(), interval)
    if (saved != null) {
        // Past deadlines are deliberately immediate. Wall-clock changes do not extend them.
        scheduleEnforcementAlarmAtElapsed(context, saved, interval)
    } else {
        // A different boot/interval or an old record without interval provenance
        // cannot justify an early alarm. Remove its token as well as its metadata.
        if (state.hasRecord()) cancelEnforcementCountdown(context)
        if (!ExclusionPrefs(prefs).isExcludedAppActive() && !GrayscaleManager(context).isGrayscaleEnabled()) {
            scheduleEnforcementAlarmAtElapsed(context, SystemClock.elapsedRealtime() + interval * 60_000L, interval)
        }
    }
}

internal fun cancelEnforcementCountdown(context: Context) = synchronized(GrayscaleStateLock) {
    context.enforcementAlarmState().clear()
    val intent = Intent(context, EnforcementAlarmReceiver::class.java)
        .setAction(GrayoutService.ACTION_ENFORCEMENT_TICK)
    PendingIntent.getBroadcast(context, GrayoutService.ENFORCEMENT_ALARM_REQUEST_CODE, intent,
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
        context.getSystemService(AlarmManager::class.java).cancel(it)
        it.cancel()
    }
}

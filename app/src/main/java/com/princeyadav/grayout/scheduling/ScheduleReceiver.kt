package com.princeyadav.grayout.scheduling

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayoutService
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduleAlarmManager.ACTION_SCHEDULE_FIRE) return

        val db = GrayoutDatabase.getInstance(context)
        val repository = ScheduleRepository(db.scheduleDao())
        val alarmManager = ScheduleAlarmManager(context)

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                val generation = intent.getStringExtra(ScheduleAlarmManager.EXTRA_GENERATION)
                val legacyIsStart = if (intent.hasExtra(ScheduleAlarmManager.EXTRA_IS_START)) {
                    intent.getBooleanExtra(ScheduleAlarmManager.EXTRA_IS_START, false)
                } else null
                val handledStart = alarmManager.handleAlarm(repository, generation, legacyIsStart)
                if (handledStart != null) {
                    val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
                    val interval = serviceIntervalExtraForScheduleEvent(handledStart, EnforcementPrefs(prefs).getInterval())
                    if (interval != null) {
                        context.startForegroundServiceSafely(
                            Intent(context, GrayoutService::class.java)
                                .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

internal fun serviceIntervalExtraForScheduleEvent(
    isStart: Boolean,
    persistedInterval: Int,
): Int? {
    return when {
        isStart && persistedInterval <= 0 -> null
        else -> persistedInterval
    }
}

/**
 * Starts the foreground service, swallowing only the background-start rejection
 * that a schedule alarm can hit when [SCHEDULE_EXACT_ALARM] is revoked on API
 * 31/32 and the alarm is delivered inexactly while the app is backgrounded.
 *
 * The schedule state is already applied or deferred by the caller, so the dropped
 * start only defers the enforcement/exclusion re-arm to the next app launch —
 * far better than letting [ForegroundServiceStartNotAllowedException] escape and
 * crash the receiver, which would also break the reschedule chain. Any other
 * failure is rethrown so real bugs stay visible.
 */
internal fun Context.startForegroundServiceSafely(intent: Intent) {
    try {
        startForegroundService(intent)
    } catch (e: Exception) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            e is ForegroundServiceStartNotAllowedException
        ) {
            Log.w("ScheduleReceiver", "FGS start rejected from background; deferring service re-arm", e)
        } else {
            throw e
        }
    }
}

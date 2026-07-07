package com.princeyadav.grayout.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val grayscaleManager = GrayscaleManager(context)
        val enforcementPrefs = EnforcementPrefs(
            context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        )

        if (intent.action != ScheduleAlarmManager.ACTION_SCHEDULE_FIRE) return

        val isStart = intent.getBooleanExtra(ScheduleAlarmManager.EXTRA_IS_START, false)
        val intervalExtra = serviceIntervalExtraForScheduleEvent(
            isStart = isStart,
            persistedInterval = enforcementPrefs.getInterval(),
        )
        if (isStart) {
            grayscaleManager.setGrayscale(true)
            if (intervalExtra != null) {
                val serviceIntent = Intent(context, GrayoutService::class.java)
                    .putExtra(GrayoutService.EXTRA_INTERVAL, intervalExtra)
                context.startForegroundService(serviceIntent)
            }
        } else {
            grayscaleManager.setGrayscale(false)
            val stopIntent = Intent(context, GrayoutService::class.java)
                .putExtra(GrayoutService.EXTRA_INTERVAL, checkNotNull(intervalExtra))
            context.startForegroundService(stopIntent)
        }

        val db = GrayoutDatabase.getInstance(context)
        val repository = ScheduleRepository(db.scheduleDao())
        val alarmManager = ScheduleAlarmManager(context)

        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                alarmManager.reschedule(repository)
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

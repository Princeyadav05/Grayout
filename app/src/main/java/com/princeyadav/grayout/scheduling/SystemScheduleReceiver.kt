package com.princeyadav.grayout.scheduling

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.restoreEnforcementAlarm
import com.princeyadav.grayout.service.shouldServiceRun
import kotlinx.coroutines.launch

/** Reconcile changed civil time or restored alarm access without replaying active starts. */
class SystemScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_TIME_CHANGED && action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED &&
            action != ACTION_RETRY) return
        val permissionChange = action == AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        // Access can be revoked again before this broadcast is delivered.
        if (permissionChange && (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
                !context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms())) return
        val result = goAsync()
        receiverScope.launch {
            try {
                val repository = ScheduleRepository(GrayoutDatabase.getInstance(context).scheduleDao())
                ScheduleAlarmManager(context).reconcileSystemChange(repository, action == ACTION_RETRY)
                if (permissionChange) {
                    restoreEnforcementAlarm(context)
                    val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
                    val interval = EnforcementPrefs(prefs).getInterval()
                    if (shouldServiceRun(interval, ExclusionPrefs(prefs).getExcludedCount())) {
                        context.startForegroundServiceSafely(Intent(context, GrayoutService::class.java)
                            .putExtra(GrayoutService.EXTRA_INTERVAL, interval))
                    }
                }
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        internal const val ACTION_RETRY = "com.princeyadav.grayout.RETRY_SCHEDULE_RECONCILIATION"
    }
}

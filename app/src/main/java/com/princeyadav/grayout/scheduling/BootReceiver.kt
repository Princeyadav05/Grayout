package com.princeyadav.grayout.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayoutService
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        val enforcementPrefs = EnforcementPrefs(
            context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        )
        val interval = enforcementPrefs.getInterval()
        if (interval > 0) {
            context.startForegroundService(
                Intent(context, GrayoutService::class.java)
                    .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
            )
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

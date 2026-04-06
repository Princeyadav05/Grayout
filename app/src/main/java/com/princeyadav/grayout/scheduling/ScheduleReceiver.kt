package com.princeyadav.grayout.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val grayscaleManager = GrayscaleManager(context.contentResolver)
        val enforcementPrefs = EnforcementPrefs(
            context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        )

        when (intent.action) {
            ScheduleAlarmManager.ACTION_SCHEDULE_START -> {
                grayscaleManager.setGrayscale(true)
                val interval = enforcementPrefs.getInterval()
                if (interval > 0) {
                    val serviceIntent = Intent(context, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                    context.startForegroundService(serviceIntent)
                }
            }
            ScheduleAlarmManager.ACTION_SCHEDULE_END -> {
                grayscaleManager.setGrayscale(false)
            }
        }

        val db = GrayoutDatabase.getInstance(context)
        val repository = ScheduleRepository(db.scheduleDao())
        val alarmManager = ScheduleAlarmManager(context)

        val pendingResult = goAsync()
        GlobalScope.launch(Dispatchers.IO) {
            try {
                alarmManager.reschedule(repository)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

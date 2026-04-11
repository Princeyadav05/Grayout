package com.princeyadav.grayout.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

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

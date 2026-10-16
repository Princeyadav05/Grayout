package com.princeyadav.grayout.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.shouldServiceRun
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val recovery = startupRecoveryFor(intent.action) ?: return

        val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val enforcementPrefs = EnforcementPrefs(prefs)
        val exclusionPrefs = ExclusionPrefs(prefs)
        val interval = enforcementPrefs.getInterval()
        if (shouldServiceRun(interval, exclusionPrefs.getExcludedCount())) {
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
                when (recovery) {
                    StartupRecovery.Boot -> alarmManager.reschedule(repository)
                    StartupRecovery.Update -> alarmManager.reconcileSystemChange(repository)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * An update kills the process and its service but keeps OS alarms, the
 * elapsed-time enforcement deadline and the user's manual choices; only a reboot
 * needs the full resync of an active schedule window.
 */
internal enum class StartupRecovery { Boot, Update }

internal fun startupRecoveryFor(action: String?): StartupRecovery? = when (action) {
    Intent.ACTION_BOOT_COMPLETED -> StartupRecovery.Boot
    Intent.ACTION_MY_PACKAGE_REPLACED -> StartupRecovery.Update
    else -> null
}

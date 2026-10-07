package com.princeyadav.grayout

import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.reconcileExclusionOnStart
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class GrayoutApp : Application() {
    // A launcher activity may finish while recovery waits for Room or the shared
    // schedule mutex. Keep that work alive for the lifetime of the process.
    internal val scheduleRecoveryScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO + CoroutineExceptionHandler { _, error ->
            // A failed recovery must not crash the app or cancel later launch
            // attempts. Existing durable alarm provenance is retained for retry.
            Log.e("GrayoutApp", "Unable to restore schedule alarms on launch", error)
        },
    )

    internal fun recoverScheduleAlarms() {
        scheduleRecoveryScope.launch {
            // Force-stop removes OS alarms but leaves enabled schedules and their
            // last boundary on disk. Rebuild without replaying an active start
            // over a manual color choice in the same schedule window.
            val repository = ScheduleRepository(GrayoutDatabase.getInstance(this@GrayoutApp).scheduleDao())
            ScheduleAlarmManager(this@GrayoutApp).reconcileSystemChange(repository)
        }
    }

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        reconcileExclusionOnStart(
            ExclusionPrefs(prefs),
            GrayscaleManager(this),
            getSystemService(PowerManager::class.java).isInteractive,
        )
    }
}

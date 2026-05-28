package com.princeyadav.grayout.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receives the enforcement countdown alarm and re-applies grayscale.
 *
 * The alarm targets this receiver rather than the foreground service: starting
 * a foreground service from a background-fired alarm is forbidden on API 31+
 * (ForegroundServiceStartNotAllowedException), whereas a broadcast receiver may
 * run and write Settings.Secure freely. Mirrors ScheduleReceiver.
 */
class EnforcementAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        applyEnforcementTick(
            EnforcementPrefs(prefs),
            ExclusionPrefs(prefs),
            GrayscaleManager(context.contentResolver),
        )
    }
}

/**
 * Re-enables grayscale when enforcement is active, no excluded app is in the
 * foreground, and grayscale is currently off. Returns true if it turned
 * grayscale on. A live GrayoutService's ContentObserver reacts to the write and
 * refreshes the notification back to "enforcement active".
 */
fun applyEnforcementTick(
    enforcementPrefs: EnforcementPrefs,
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): Boolean {
    if (enforcementPrefs.getInterval() <= 0) return false
    if (exclusionPrefs.isExcludedAppActive()) return false
    if (grayscale.isGrayscaleEnabled()) return false
    return grayscale.setGrayscale(true)
}

package com.princeyadav.grayout.service

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.R

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
        val enforcementPrefs = EnforcementPrefs(prefs)
        val grayscale = GrayscaleManager(context)
        val result = applyEnforcementTick(enforcementPrefs, ExclusionPrefs(prefs), grayscale)
        // The alarm that fired is spent, but its non-one-shot PendingIntent token
        // survives, and hasPendingEnforcementAlarm() (used by the service's re-arm
        // branch and the exclusion-exit re-enable) would keep reporting a live alarm.
        // On WriteFailed we replace it with a real retry; on any other outcome
        // (Applied, or Skipped because an excluded app is active / grayscale is
        // already on) we cancel the spent token so that check stays honest.
        when (result) {
            EnforcementTickResult.WriteFailed ->
                onWriteFailed(context, enforcementPrefs.getInterval(), grayscale)
            else -> cancelEnforcementAlarm(context)
        }
    }

    private fun cancelEnforcementAlarm(context: Context) {
        val intent = Intent(context, EnforcementAlarmReceiver::class.java)
            .setAction(GrayoutService.ACTION_ENFORCEMENT_TICK)
        PendingIntent.getBroadcast(
            context, GrayoutService.ENFORCEMENT_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    /**
     * A tick tried to re-assert grayscale and the write did not stick, so grayscale
     * is still off and enforcement still wants it on — but the live service's
     * ContentObserver only fires on a *successful* write, so without this the failed
     * attempt would strand enforcement (no retry, no signal). Two things happen:
     *
     * 1. Reschedule a retry one interval out. A transient write contention then
     *    resolves on the next tick, and a genuinely revoked WRITE_SECURE_SETTINGS
     *    auto-recovers as soon as it is restored — without the user reopening the app.
     * 2. If the permission is genuinely gone (the OnePlus OEM-cleanup path in the
     *    README), also replace the frozen "enforcement active" notification with a
     *    "permission lost" state. Tapping opens the app on Home, whose attention
     *    badge (it counts a missing WRITE_SECURE_SETTINGS) routes on to ADB setup;
     *    recovery itself no longer depends on the tap, since the retry auto-recovers.
     */
    private fun onWriteFailed(context: Context, intervalMinutes: Int, grayscale: GrayscaleController) {
        if (intervalMinutes > 0) {
            scheduleEnforcementAlarm(context, System.currentTimeMillis() + intervalMinutes * 60_000L)
        }
        if (!grayscale.canWriteSecureSettings()) postPermissionLost(context)
    }

    private fun postPermissionLost(context: Context) {
        // The channel is guaranteed to exist: this alarm can only have been
        // scheduled by GrayoutService, whose onCreate creates it first.
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, GrayoutService.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Grayscale enforcement paused")
            .setContentText("Permission lost — tap to fix")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
        // Same id as the foreground service notification: this replaces the stale
        // "enforcement active" text in place. Raw NotificationManager (as the service
        // uses); if POST_NOTIFICATIONS is denied the notify() no-ops without throwing.
        context.getSystemService(NotificationManager::class.java)
            .notify(GrayoutService.NOTIFICATION_ID, notification)
    }
}

/** Outcome of one enforcement alarm tick. */
enum class EnforcementTickResult {
    /** Grayscale was off and this tick turned it back on. */
    Applied,

    /** Nothing to do: enforcement off, an excluded app is active, or grayscale already on. */
    Skipped,

    /** A re-enable was warranted but the Settings.Secure write did not stick. */
    WriteFailed,
}

/**
 * Re-enables grayscale when enforcement is active, no excluded app is in the
 * foreground, and grayscale is currently off. On a successful write a live
 * GrayoutService's ContentObserver reacts and refreshes the notification back to
 * "enforcement active"; [EnforcementTickResult.WriteFailed] is surfaced by the
 * receiver instead, since no observer fires when the write fails.
 */
fun applyEnforcementTick(
    enforcementPrefs: EnforcementPrefs,
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): EnforcementTickResult {
    if (enforcementPrefs.getInterval() <= 0) return EnforcementTickResult.Skipped
    if (exclusionPrefs.isExcludedAppActive()) return EnforcementTickResult.Skipped
    if (grayscale.isGrayscaleEnabled()) return EnforcementTickResult.Skipped
    return if (grayscale.setGrayscale(true)) {
        EnforcementTickResult.Applied
    } else {
        EnforcementTickResult.WriteFailed
    }
}

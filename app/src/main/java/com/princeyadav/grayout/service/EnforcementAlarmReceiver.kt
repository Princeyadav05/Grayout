package com.princeyadav.grayout.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.R
import com.princeyadav.grayout.scheduling.inScheduleWindowAt
import com.princeyadav.grayout.scheduling.startForegroundServiceSafely

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
        if (intent.action != GrayoutService.ACTION_ENFORCEMENT_TICK) return
        synchronized(GrayscaleStateLock) {
            val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            val enforcementPrefs = EnforcementPrefs(prefs)
            val alarmState = context.enforcementAlarmState()
            val boot = context.bootCount()
            val interval = enforcementPrefs.getInterval()
            if (!alarmState.acceptsDelivery(
                    intent.getStringExtra(EXTRA_GENERATION), boot, interval, SystemClock.elapsedRealtime(),
                )) {
                // Repair a crash between durable publication and OS registration.
                // The current identity/deadline is retained; cleared, old-boot, or
                // changed-interval records cannot be revived by a stale delivery.
                if (interval > 0 && alarmState.generation() != null) alarmState.deadline(boot, interval)?.let {
                    if (context.autoRestoreAllowedAt(restoreWallMillis(it))) {
                        scheduleEnforcementAlarmAtElapsed(context, it, interval)
                    } else {
                        cancelEnforcementCountdown(context)
                        refreshServiceNotification(context)
                    }
                }
                return
            }
            val grayscale = GrayscaleManager(context)
            val inWindow = context.inScheduleWindowAt(System.currentTimeMillis())
            val result = applyEnforcementTick(
                enforcementPrefs, ExclusionPrefs(prefs), grayscale,
                restoreMomentInWindow = inWindow,
            )
            // The alarm that fired is spent, but its non-one-shot PendingIntent token
            // survives, and hasPendingEnforcementAlarm() (used by the service's re-arm
            // branch and the exclusion-exit re-enable) would keep reporting a live alarm.
            // On WriteFailed we replace it with a real retry; on any other outcome
            // (Applied, or Skipped because an excluded app is active / grayscale is
            // already on) we cancel the spent token so that check stays honest.
            when (result) {
                EnforcementTickResult.WriteFailed ->
                    onWriteFailed(context, enforcementPrefs.getInterval(), grayscale)
                else -> {
                    cancelEnforcementAlarm(context)
                    // A late (inexact/Doze) delivery past the window end: let the service
                    // replace its "comes back around" countdown with the waiting text.
                    if (enforcementPrefs.isOnlyDuringSchedules() && !inWindow) refreshServiceNotification(context)
                }
            }
        }
    }

    private fun cancelEnforcementAlarm(context: Context) = cancelEnforcementCountdown(context)

    /** The service owns the notification; a plain start makes it re-evaluate (branch C). */
    private fun refreshServiceNotification(context: Context) =
        context.startForegroundServiceSafely(Intent(context, GrayoutService::class.java))

    companion object {
        internal const val EXTRA_GENERATION = "enforcement_generation"
    }

    /**
     * A tick tried to re-assert grayscale and the write did not stick, so grayscale
     * is still off and enforcement still wants it on — but the live service's
     * ContentObserver only fires on a *successful* write, so without this the failed
     * attempt would strand enforcement (no retry, no signal). Two things happen:
     *
     * 1. Reschedule a retry (see [writeRetryDelayMs]). A transient write contention
     *    resolves on the next tick, and a genuinely revoked WRITE_SECURE_SETTINGS
     *    auto-recovers on the next retry after it is restored, without reopening the app.
     * 2. If the permission is genuinely gone (the OnePlus OEM-cleanup path in the
     *    README), also replace the frozen "enforcement active" notification with a
     *    "permission lost" state. Tapping opens the app on Home, whose attention
     *    badge (it counts a missing WRITE_SECURE_SETTINGS) routes on to ADB setup;
     *    recovery itself no longer depends on the tap, since the retry auto-recovers.
     */
    private fun onWriteFailed(context: Context, intervalMinutes: Int, grayscale: GrayscaleController) {
        val canWrite = grayscale.canWriteSecureSettings()
        if (intervalMinutes > 0) {
            val delayMs = writeRetryDelayMs(intervalMinutes * 60_000L, canWrite)
            val retryAt = System.currentTimeMillis() + delayMs
            // A retry (including the permission backoff) that would land after the
            // schedule window ends must not be armed when restore is schedule-only.
            if (context.autoRestoreAllowedAt(retryAt)) {
                scheduleEnforcementAlarm(context, retryAt, intervalMinutes)
            } else {
                cancelEnforcementCountdown(context)
                if (canWrite) refreshServiceNotification(context)
            }
        }
        if (!canWrite) postPermissionLost(context)
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
            .setContentTitle("Automatic restore paused")
            .setContentText("Grayscale permission lost. Tap to fix.")
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
    restoreMomentInWindow: Boolean = true,
): EnforcementTickResult = synchronized(GrayscaleStateLock) {
    val restore = shouldAutoRestore(
        intervalMinutes = enforcementPrefs.getInterval(),
        onlyDuringSchedules = enforcementPrefs.isOnlyDuringSchedules(),
        restoreMomentInWindow = restoreMomentInWindow,
        excludedAppActive = exclusionPrefs.isExcludedAppActive(),
        grayscaleOn = grayscale.isGrayscaleEnabled(),
    )
    if (!restore) return@synchronized EnforcementTickResult.Skipped
    if (grayscale.setGrayscale(true)) {
        EnforcementTickResult.Applied
    } else {
        EnforcementTickResult.WriteFailed
    }
}

/**
 * The one automatic-restore rule. [restoreMomentInWindow] is schedule coverage at the
 * instant grayscale would come back (the alarm's deadline when arming, now when firing);
 * it only matters when the user chose "Only during schedules".
 */
fun shouldAutoRestore(
    intervalMinutes: Int,
    onlyDuringSchedules: Boolean,
    restoreMomentInWindow: Boolean,
    excludedAppActive: Boolean,
    grayscaleOn: Boolean,
): Boolean = intervalMinutes > 0 && !excludedAppActive && !grayscaleOn &&
    (!onlyDuringSchedules || restoreMomentInWindow)

/**
 * Schedule-coverage gate for an automatic restore landing at [wallMillis]. Always true
 * unless the user turned on "Only during schedules".
 */
internal fun Context.autoRestoreAllowedAt(wallMillis: Long): Boolean =
    !EnforcementPrefs(getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)).isOnlyDuringSchedules() ||
        inScheduleWindowAt(wallMillis)

/** Wall-clock instant of an elapsed-realtime alarm deadline. Overdue deadlines fire now, so they read as now. */
internal fun restoreWallMillis(
    elapsedDeadline: Long,
    nowElapsed: Long = SystemClock.elapsedRealtime(),
    nowWall: Long = System.currentTimeMillis(),
): Long = nowWall + (elapsedDeadline - nowElapsed).coerceAtLeast(0L)

/**
 * Delay before retrying a grayscale write that did not stick. A transient failure
 * keeps its normal cadence. A revoked WRITE_SECURE_SETTINGS fails every attempt
 * until it is re-granted, so back off instead of waking every minute.
 */
internal fun writeRetryDelayMs(normalMs: Long, canWriteSecureSettings: Boolean): Long =
    if (canWriteSecureSettings) normalMs else maxOf(normalMs, PERMISSION_MISSING_RETRY_MS)

private const val PERMISSION_MISSING_RETRY_MS = 15 * 60_000L

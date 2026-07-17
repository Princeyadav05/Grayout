package com.princeyadav.grayout.service

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class GrayoutService : Service() {

    private lateinit var grayscaleManager: GrayscaleManager
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var detector: ForegroundAppDetector
    private val handler = Handler(Looper.getMainLooper())
    private val detectorScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentInterval = 0
    private var countdownTargetMs = 0L

    // Read by the detector's background poll thread (isScreenOn), written on the main
    // thread from screenReceiver, so it must be @Volatile for cross-thread visibility.
    @Volatile
    private var isScreenInteractive = true

    private val grayscaleObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            // Intentional no-op at interval 0: enforcement must never schedule
            // then, and the detector (not the observer) drives all exclusion
            // side-effects in that mode, so there is no observer<->detector
            // write-feedback loop. Consequence: the static "Watching excluded apps"
            // notification is not refreshed on grayscale changes at interval 0,
            // which is acceptable (the copy is static).
            if (currentInterval <= 0) return

            cancelEnforcementAlarm()
            countdownTargetMs = 0L

            if (exclusionPrefs.isExcludedAppActive()) {
                updateNotification(countdownTarget = null)
                return
            }

            if (!grayscaleManager.isGrayscaleEnabled()) {
                val delayMs = currentInterval * 60_000L
                countdownTargetMs = System.currentTimeMillis() + delayMs
                scheduleEnforcementAlarm(countdownTargetMs)
                updateNotification(countdownTarget = countdownTargetMs)
            } else {
                updateNotification(countdownTarget = null)
            }
        }
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> {
                    isScreenInteractive = true
                    if (exclusionPrefs.getExcludedCount() > 0) detector.start()
                }
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenInteractive = false
                    // Wake gray, never into colour: if a live exclusion session is
                    // suppressing a previously-on grayscale, re-assert it now and reset
                    // the FSM so it re-enters cleanly on the next screen-on.
                    preGrayOnScreenOff(exclusionPrefs, grayscaleManager)
                    detector.stop()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        grayscaleManager = GrayscaleManager(this)
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE)
        enforcementPrefs = EnforcementPrefs(prefs)
        exclusionPrefs = ExclusionPrefs(prefs)
        createNotificationChannel()
        contentResolver.registerContentObserver(
            GrayscaleManager.DALTONIZER_ENABLED_URI, false, grayscaleObserver
        )

        detector = ForegroundAppDetector(
            provider = UsageStatsForegroundProvider(this),
            exclusionPrefs = exclusionPrefs,
            enforcementPrefs = enforcementPrefs,
            grayscale = grayscaleManager,
            ownPackage = packageName,
            // handleExclusionEnded touches startForeground/AlarmManager, which
            // expect the service main thread, so marshal it off the detector's
            // background poll thread onto the main handler.
            onExclusionEnded = { handler.post { handleExclusionEnded() } },
            scope = detectorScope,
            isScreenOn = { isScreenInteractive },
        )

        isScreenInteractive = getSystemService(PowerManager::class.java).isInteractive
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        _isRunning.value = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val interval = intent?.getIntExtra(EXTRA_INTERVAL, -1)
            ?.takeIf { it >= 0 }
            ?: enforcementPrefs.getInterval()

        val intervalChanged = interval != currentInterval
        currentInterval = interval

        val excludedCount = exclusionPrefs.getExcludedCount()

        // (A) Nothing to do: enforcement off and no excluded apps -> stop fully.
        if (!shouldServiceRun(interval, excludedCount)) {
            reconcileStrandedExclusion()
            cancelEnforcementAlarm()
            countdownTargetMs = 0L
            detector.stop()
            startForeground(NOTIFICATION_ID, buildNotification(interval))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // (B) Alive only for exclusions (interval == 0, excludedCount > 0).
        // No enforcement alarm; neutral notification; detector gated on screen.
        if (interval == 0) {
            // Heal a stranded flag ONLY on a sticky/warm process revival (null
            // intent). On an explicit (re)start carrying an intent — app open, an
            // exclusion-list toggle, boot, schedule, tile — a true excludedAppActive
            // is a LIVE session, not stranded; reconciling would clear it and
            // re-gray mid-session (a visible flicker when the user returns to the
            // excluded app). The detector self-heals via a proper Exit on its next
            // non-own, non-excluded tick, and GrayoutApp heals cold starts.
            if (intent == null) reconcileStrandedExclusion()
            cancelEnforcementAlarm()
            countdownTargetMs = 0L
            startForeground(NOTIFICATION_ID, buildNotification(0, exclusionOnly = true))
            if (isScreenInteractive) detector.start() else detector.stop()
            return START_STICKY
        }

        // (C) interval > 0.
        // Heal a stranded flag when the last excluded app was just removed
        // (excludedCount == 0) while a session was active — branches (A)/(B) cover
        // interval 0; this covers interval > 0. Without it, removing the app you
        // are inside leaves excludedAppActive stuck true: grayscale stays off, the
        // detector can no longer Exit (tickOnce no-ops at 0 exclusions), and
        // applyEnforcementTick short-circuits on the flag, blocking all future
        // enforcement until a cold start. Guarded on excludedCount == 0 so a live
        // multi-exclusion session is never cleared, and reconcile self-guards on
        // isExcludedAppActive() (no-op in the normal no-exclusions case). Placed
        // before the scheduling block so a wasOn=false heal re-arms the alarm.
        if (excludedCount == 0) reconcileStrandedExclusion()

        val now = System.currentTimeMillis()
        val hasActiveCountdown = countdownTargetMs > now

        if (hasActiveCountdown && !intervalChanged) {
            startForeground(NOTIFICATION_ID, buildNotification(interval, countdownTargetMs))
        } else if (!intervalChanged && hasPendingEnforcementAlarm()) {
            startForeground(NOTIFICATION_ID, buildNotification(interval))
        } else {
            cancelEnforcementAlarm()
            countdownTargetMs = 0L

            if (!exclusionPrefs.isExcludedAppActive() && !grayscaleManager.isGrayscaleEnabled()) {
                val delayMs = currentInterval * 60_000L
                countdownTargetMs = now + delayMs
                scheduleEnforcementAlarm(countdownTargetMs)
                startForeground(NOTIFICATION_ID, buildNotification(interval, countdownTargetMs))
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(interval))
            }
        }

        if (excludedCount > 0 && isScreenInteractive) detector.start() else detector.stop()
        return START_STICKY
    }

    /**
     * Re-applies grayscale when an excluded app closes and a re-enable is needed.
     * Invoked by the detector (via a [handler] post onto the main thread) when an
     * Exit transition has no saved wasOn but enforcement is active.
     */
    private fun handleExclusionEnded() {
        if (currentInterval <= 0) currentInterval = enforcementPrefs.getInterval()
        val now = System.currentTimeMillis()
        val hasActiveCountdown = countdownTargetMs > now
        val reEnablePending = hasActiveCountdown || hasPendingEnforcementAlarm()

        if (shouldReEnableOnExclusionEnd(
                currentInterval, reEnablePending, grayscaleManager.isGrayscaleEnabled(),
            )
        ) {
            val success = grayscaleManager.setGrayscale(true)
            if (!success) {
                detector.stop()
                startForeground(NOTIFICATION_ID, buildNotification(currentInterval))
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
        }

        if (hasActiveCountdown) {
            startForeground(NOTIFICATION_ID, buildNotification(currentInterval, countdownTargetMs))
        } else {
            startForeground(NOTIFICATION_ID, buildNotification(currentInterval))
        }
    }

    /**
     * Clears a stranded `excludedAppActive` flag and restores grayscale if it was
     * on when the excluded app was entered, covering a screen-off teardown that
     * left the flag set. [GrayoutApp.clearExclusionState] remains the primary
     * cold-start healer; it does not restore wasOn, so this covers warm restart.
     */
    private fun reconcileStrandedExclusion() {
        if (exclusionPrefs.isExcludedAppActive()) {
            val wasOn = exclusionPrefs.wasGrayscaleOnBeforeExclusion()
            exclusionPrefs.clearExclusionState()
            if (wasOn) grayscaleManager.setGrayscale(true)
        }
    }

    override fun onDestroy() {
        _isRunning.value = false
        contentResolver.unregisterContentObserver(grayscaleObserver)
        detector.stop()
        detectorScope.cancel()
        unregisterReceiver(screenReceiver)
        countdownTargetMs = 0L
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Grayout Service",
            NotificationManager.IMPORTANCE_LOW,
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(
        interval: Int,
        countdownTarget: Long? = null,
        exclusionOnly: Boolean = false,
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Grayscale enforcement")
            .setColor(0xFFB5A0D8.toInt())
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (countdownTarget != null && countdownTarget > System.currentTimeMillis()) {
            val targetTime = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT)
                .format(java.util.Date(countdownTarget))
            builder.setContentText("Re-enabling around $targetTime")
                .setShowWhen(false)
        } else if (exclusionOnly) {
            builder.setContentText("Watching excluded apps")
                .setShowWhen(false)
        } else if (interval > 0) {
            builder.setContentText("Enforcement active")
                .setShowWhen(false)
        } else {
            builder.setContentText("Stopping enforcement")
                .setShowWhen(false)
        }

        return builder.build()
    }

    private fun updateNotification(countdownTarget: Long?) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(currentInterval, countdownTarget))
    }

    private fun scheduleEnforcementAlarm(triggerAtMillis: Long) {
        val intent = Intent(this, EnforcementAlarmReceiver::class.java)
            .setAction(ACTION_ENFORCEMENT_TICK)
        val pi = PendingIntent.getBroadcast(
            this, ENFORCEMENT_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = getSystemService(AlarmManager::class.java)
        val elapsedTarget = SystemClock.elapsedRealtime() +
            (triggerAtMillis - System.currentTimeMillis())
        try {
            if (canScheduleExact(am)) {
                am.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, pi,
                )
            } else {
                am.setAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, pi,
                )
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP, elapsedTarget, pi,
            )
        }
    }

    private fun cancelEnforcementAlarm() {
        val intent = Intent(this, EnforcementAlarmReceiver::class.java)
            .setAction(ACTION_ENFORCEMENT_TICK)
        val pi = PendingIntent.getBroadcast(
            this, ENFORCEMENT_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        pi?.let {
            getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private fun hasPendingEnforcementAlarm(): Boolean {
        val intent = Intent(this, EnforcementAlarmReceiver::class.java)
            .setAction(ACTION_ENFORCEMENT_TICK)
        return PendingIntent.getBroadcast(
            this, ENFORCEMENT_ALARM_REQUEST_CODE, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) != null
    }

    private fun canScheduleExact(am: AlarmManager): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()

    companion object {
        private val _isRunning = MutableStateFlow(false)

        /**
         * Whether a [GrayoutService] instance is alive (onCreate..onDestroy),
         * observed for diagnostics on the Settings screen. Reflects actual liveness
         * — including self-stops when enforcement is off with no exclusions, or on a
         * lost WRITE_SECURE_SETTINGS permission — not just whether the service is
         * configured to run. Emits so the UI updates live, not only on screen resume.
         */
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        const val CHANNEL_ID = "grayout_service"
        const val NOTIFICATION_ID = 1
        const val EXTRA_INTERVAL = "enforcement_interval_minutes"
        const val ACTION_ENFORCEMENT_TICK = "com.princeyadav.grayout.ENFORCEMENT_TICK"
        private const val ENFORCEMENT_ALARM_REQUEST_CODE = 2001
    }
}

/**
 * Whether a closing excluded app should re-apply grayscale immediately.
 *
 * If a re-enable is already pending we leave it to fire at its scheduled time
 * instead of snapping grayscale on early. The caller must treat a surviving
 * alarm as pending too: the in-memory countdown is lost across a process
 * restart while the OS-level alarm survives.
 */
fun shouldReEnableOnExclusionEnd(
    interval: Int,
    reEnablePending: Boolean,
    grayscaleEnabled: Boolean,
): Boolean = interval > 0 && !reEnablePending && !grayscaleEnabled

/**
 * Whether SCREEN_OFF should pre-assert grayscale so the device wakes gray rather
 * than leaking colour for the UsageStats detection window.
 *
 * Only when a live exclusion session is suppressing grayscale ([excludedAppActive])
 * AND the user's pre-exclusion state was grayscale-on ([wasGrayscaleOnBeforeExclusion]).
 * If they were intentionally in colour before opening the excluded app, forcing gray
 * on screen-off would override their real state, so we leave it.
 */
fun shouldPreGrayOnScreenOff(
    excludedAppActive: Boolean,
    wasGrayscaleOnBeforeExclusion: Boolean,
): Boolean = excludedAppActive && wasGrayscaleOnBeforeExclusion

/**
 * Applies [shouldPreGrayOnScreenOff] on the SCREEN_OFF edge: re-assert grayscale
 * and reset the exclusion FSM so it re-enters cleanly on the next screen-on. The
 * exclusion state is cleared ONLY if the re-gray write actually stuck, so a lost
 * permission can't strand the FSM with grayscale off but the flags reset. Returns
 * whether it pre-grayed. Testable with fakes, mirroring [applyExclusionTransition].
 */
fun preGrayOnScreenOff(
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): Boolean {
    if (!shouldPreGrayOnScreenOff(
            exclusionPrefs.isExcludedAppActive(),
            exclusionPrefs.wasGrayscaleOnBeforeExclusion(),
        )
    ) {
        return false
    }
    if (!grayscale.setGrayscale(true)) return false
    exclusionPrefs.clearExclusionState()
    return true
}

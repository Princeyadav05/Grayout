package com.princeyadav.grayout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.R

class GrayoutService : Service() {

    private lateinit var grayscaleManager: GrayscaleManager
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var exclusionPrefs: ExclusionPrefs
    private val handler = Handler(Looper.getMainLooper())
    private var currentInterval = 0
    private var countdownTargetMs = 0L

    private val enforcementRunnable = Runnable {
        countdownTargetMs = 0L
        if (!exclusionPrefs.isExcludedAppActive() && !grayscaleManager.isGrayscaleEnabled()) {
            val success = grayscaleManager.setGrayscale(true)
            if (!success) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@Runnable
            }
        }
        updateNotification(countdownTarget = null)
    }

    // Reacts to external grayscale changes instead of polling on a fixed cadence.
    // Self-triggers (from our own setGrayscale calls) are harmless: the ON branch
    // cancels nothing, and the exclusion guard prevents fighting the accessibility service.
    private val grayscaleObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean) {
            if (currentInterval <= 0) return

            handler.removeCallbacks(enforcementRunnable)
            countdownTargetMs = 0L

            if (exclusionPrefs.isExcludedAppActive()) {
                updateNotification(countdownTarget = null)
                return
            }

            if (!grayscaleManager.isGrayscaleEnabled()) {
                val delayMs = currentInterval * 60_000L
                countdownTargetMs = System.currentTimeMillis() + delayMs
                handler.postDelayed(enforcementRunnable, delayMs)
                updateNotification(countdownTarget = countdownTargetMs)
            } else {
                updateNotification(countdownTarget = null)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        grayscaleManager = GrayscaleManager(contentResolver)
        enforcementPrefs = EnforcementPrefs(
            getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE)
        )
        exclusionPrefs = ExclusionPrefs(
            getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE)
        )
        createNotificationChannel()
        contentResolver.registerContentObserver(
            GrayscaleManager.DALTONIZER_ENABLED_URI, false, grayscaleObserver
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getBooleanExtra(EXTRA_EXCLUSION_ENDED, false) == true) {
            if (currentInterval <= 0) currentInterval = enforcementPrefs.getInterval()
            val now = System.currentTimeMillis()
            val hasActiveCountdown = countdownTargetMs > now

            if (!hasActiveCountdown && currentInterval > 0 && !grayscaleManager.isGrayscaleEnabled()) {
                val success = grayscaleManager.setGrayscale(true)
                if (!success) {
                    startForeground(NOTIFICATION_ID, buildNotification(currentInterval))
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    return START_NOT_STICKY
                }
            }

            if (hasActiveCountdown) {
                startForeground(NOTIFICATION_ID, buildNotification(currentInterval, countdownTargetMs))
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(currentInterval))
            }
            return START_STICKY
        }

        val interval = intent?.getIntExtra(EXTRA_INTERVAL, -1)
            ?.takeIf { it >= 0 }
            ?: enforcementPrefs.getInterval()

        val intervalChanged = interval != currentInterval
        currentInterval = interval

        if (interval <= 0) {
            handler.removeCallbacks(enforcementRunnable)
            countdownTargetMs = 0L
            startForeground(NOTIFICATION_ID, buildNotification(interval))
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val now = System.currentTimeMillis()
        val hasActiveCountdown = countdownTargetMs > now

        if (hasActiveCountdown && !intervalChanged) {
            startForeground(NOTIFICATION_ID, buildNotification(interval, countdownTargetMs))
        } else {
            handler.removeCallbacks(enforcementRunnable)
            countdownTargetMs = 0L

            if (!exclusionPrefs.isExcludedAppActive() && !grayscaleManager.isGrayscaleEnabled()) {
                val delayMs = currentInterval * 60_000L
                countdownTargetMs = now + delayMs
                handler.postDelayed(enforcementRunnable, delayMs)
                startForeground(NOTIFICATION_ID, buildNotification(interval, countdownTargetMs))
            } else {
                startForeground(NOTIFICATION_ID, buildNotification(interval))
            }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(enforcementRunnable)
        contentResolver.unregisterContentObserver(grayscaleObserver)
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

        if (countdownTarget != null) {
            builder.setContentText("Re-enabling in")
                .setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setWhen(countdownTarget)
                .setShowWhen(true)
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

    companion object {
        const val CHANNEL_ID = "grayout_service"
        const val NOTIFICATION_ID = 1
        const val EXTRA_INTERVAL = "enforcement_interval_minutes"
        const val EXTRA_EXCLUSION_ENDED = "exclusion_ended"
    }
}

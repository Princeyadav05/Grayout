package com.princeyadav.grayout.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
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

    private val enforcementRunnable = object : Runnable {
        override fun run() {
            if (!exclusionPrefs.isExcludedAppActive() && !grayscaleManager.isGrayscaleEnabled()) {
                grayscaleManager.setGrayscale(true)
            }
            handler.postDelayed(this, currentInterval * 60_000L)
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val interval = intent?.getIntExtra(EXTRA_INTERVAL, -1)
            ?.takeIf { it >= 0 }
            ?: enforcementPrefs.getInterval()

        currentInterval = interval
        handler.removeCallbacks(enforcementRunnable)

        // Always call startForeground first to satisfy the foreground-service
        // start-timeout contract, then tear down immediately if interval is 0.
        startForeground(NOTIFICATION_ID, buildNotification(interval))

        if (interval <= 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        handler.post(enforcementRunnable)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(enforcementRunnable)
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

    private fun buildNotification(interval: Int): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_IMMUTABLE
        )
        val contentText = if (interval > 0) {
            "Re-applies every $interval min"
        } else {
            "Stopping enforcement"
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Grayscale enforcement")
            .setContentText(contentText)
            .setColor(0xFFA0D2C6.toInt())
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "grayout_service"
        const val NOTIFICATION_ID = 1
        const val EXTRA_INTERVAL = "enforcement_interval_minutes"
    }
}

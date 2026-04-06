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
        startForeground(NOTIFICATION_ID, buildNotification(0))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val interval = intent?.getIntExtra(EXTRA_INTERVAL, -1)
            ?.takeIf { it >= 0 }
            ?: enforcementPrefs.getInterval()

        currentInterval = interval
        handler.removeCallbacks(enforcementRunnable)

        if (interval > 0) {
            handler.post(enforcementRunnable)
        }

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(interval))

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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile)
            .setContentTitle("Grayout")
            .setContentText(
                if (interval > 0) "Enforcing grayscale every ${interval}m"
                else "Grayout is idle"
            )
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

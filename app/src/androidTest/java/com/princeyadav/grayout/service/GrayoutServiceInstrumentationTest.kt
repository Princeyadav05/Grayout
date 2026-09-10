package com.princeyadav.grayout.service

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the real service observer and AlarmManager after a mode-only change. */
@RunWith(AndroidJUnit4::class)
class GrayoutServiceInstrumentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val serviceIntent = Intent(context, GrayoutService::class.java)

    private var savedPreferences: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null
    private var serviceWasRunning = false

    @Before
    fun setUp() {
        savedPreferences = prefs.all
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        serviceWasRunning = GrayoutService.isRunning.value
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            "android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND",
        )

        stopService()
        cancelEnforcementAlarm()
        assertTrue(prefs.edit().clear().commit())
        EnforcementPrefs(prefs).setInterval(5)
        Settings.Secure.putInt(context.contentResolver, ENABLED, 1)
        Settings.Secure.putInt(context.contentResolver, MODE, 0)
    }

    @After
    fun tearDown() {
        try {
            stopService()
            cancelEnforcementAlarm()
            Settings.Secure.putString(context.contentResolver, ENABLED, savedEnabled)
            Settings.Secure.putString(context.contentResolver, MODE, savedMode)
            restorePreferences()
            if (serviceWasRunning) {
                context.startForegroundService(serviceIntent)
                awaitCondition("The previously running service should be restored") {
                    GrayoutService.isRunning.value
                }
                instrumentation.waitForIdleSync()
            }
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun modeOnlyChangeArmsEnforcementAndReturningToMonochromeCancelsIt() {
        context.startForegroundService(serviceIntent)
        awaitCondition("The enforcement service should start") { GrayoutService.isRunning.value }
        instrumentation.waitForIdleSync()
        assertNull("Grayscale is already on, so no countdown should exist", enforcementAlarm())

        // Leave enabled=1 untouched. A service observing only the enabled URI
        // misses this change from monochrome to deuteranomaly correction.
        assertTrue(Settings.Secure.putInt(context.contentResolver, MODE, 12))
        awaitCondition("A mode-only change away from grayscale must arm enforcement") {
            enforcementAlarm() != null
        }
        assertEquals(1, Settings.Secure.getInt(context.contentResolver, ENABLED))

        assertTrue(Settings.Secure.putInt(context.contentResolver, MODE, 0))
        awaitCondition("Returning to monochrome must cancel the countdown") {
            enforcementAlarm() == null
        }
    }

    @Test
    fun startupInsideAnExclusionPreservesTheSurvivingEnforcementAlarm() {
        assertStartupPreservesCountdown(serviceIntent)
    }

    @Test
    fun scheduleResynchronizationExtraDoesNotResetTheSurvivingCountdown() {
        assertStartupPreservesCountdown(Intent(serviceIntent).putExtra(GrayoutService.EXTRA_INTERVAL, 5))
    }

    @Test
    fun userIntervalChoiceOnFreshServiceResetsThePriorCountdown() {
        assertStartupPreservesCountdown(
            Intent(serviceIntent).putExtra(GrayoutService.EXTRA_INTERVAL, 5)
                .putExtra(GrayoutService.EXTRA_USER_INTERVAL_CHANGE, true),
            preserve = false,
        )
    }

    private fun assertStartupPreservesCountdown(startIntent: Intent, preserve: Boolean = true) {
        val exclusions = ExclusionPrefs(prefs)
        exclusions.setExcludedPackages(context.packageManager.getInstalledApplications(0)
            .map { it.packageName }.toSet())
        exclusions.setWasGrayscaleOnBeforeExclusion(false)
        exclusions.setExcludedAppActive(true)
        Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        Settings.Secure.putInt(context.contentResolver, MODE, -1)
        scheduleEnforcementAlarm(context, System.currentTimeMillis() + 5 * 60_000)
        val originalAlarm = checkNotNull(enforcementAlarm())

        // A fresh service with no explicit user interval choice must retain the token,
        // just as it does on null-intent sticky revival after process death.
        context.startForegroundService(startIntent)
        awaitCondition("Service should start") { GrayoutService.isRunning.value }
        instrumentation.waitForIdleSync()

        if (preserve) assertEquals(originalAlarm, enforcementAlarm())
        else assertNull(enforcementAlarm())
        assertTrue(exclusions.isExcludedAppActive())
        assertEquals(5, EnforcementPrefs(prefs).getInterval())
        assertEquals(0, Settings.Secure.getInt(context.contentResolver, ENABLED))
    }

    private fun stopService() {
        context.stopService(serviceIntent)
        awaitCondition("The service should stop before restoring test state") {
            !GrayoutService.isRunning.value
        }
        instrumentation.waitForIdleSync()
    }

    private fun enforcementAlarm(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        GrayoutService.ENFORCEMENT_ALARM_REQUEST_CODE,
        Intent(context, EnforcementAlarmReceiver::class.java)
            .setAction(GrayoutService.ACTION_ENFORCEMENT_TICK),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun cancelEnforcementAlarm() {
        enforcementAlarm()?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private fun restorePreferences() {
        val editor = prefs.edit().clear()
        for ((key, value) in savedPreferences) {
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
            }
        }
        assertTrue(editor.commit())
    }

    private fun awaitCondition(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 5_000L
        while (!condition() && SystemClock.uptimeMillis() < deadline) {
            SystemClock.sleep(20L)
        }
        assertTrue(message, condition())
    }

    companion object {
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
    }
}

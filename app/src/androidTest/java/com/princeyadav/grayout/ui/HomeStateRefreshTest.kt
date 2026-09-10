package com.princeyadav.grayout.ui

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.EnforcementAlarmReceiver
import com.princeyadav.grayout.service.GrayoutService
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Requires WRITE_SECURE_SETTINGS, like GrayscaleManagerInstrumentationTest. */
@RunWith(AndroidJUnit4::class)
class HomeStateRefreshTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val enforcementPrefs = EnforcementPrefs(prefs)

    @Before
    fun reset() {
        prefs.edit().clear().commit()
        writeGrayscale(false)
    }

    @After
    fun tearDown() {
        prefs.edit().clear().commit()
        context.stopService(Intent(context, GrayoutService::class.java))
        enforcementAlarm()?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
        writeGrayscale(false)
    }

    @Test
    fun resumeRefreshesChangesMadeWhileStopped() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Grayscale off").assertIsDisplayed()
            scenario.moveToState(Lifecycle.State.CREATED)

            enforcementPrefs.setInterval(15)
            writeGrayscale(true)
            scenario.moveToState(Lifecycle.State.RESUMED)

            composeRule.onNodeWithText("Grayscale on").assertIsDisplayed()
            composeRule.onNode(hasText("15m") and isSelectable()).assertIsSelected()
            assertEquals(15, enforcementPrefs.getInterval())
        }
    }

    @Test
    fun recreationDoesNotSendAnOldIntervalBackToTheService() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            scenario.moveToState(Lifecycle.State.CREATED)
            enforcementPrefs.setInterval(10)

            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)

            composeRule.onNode(hasText("10m") and isSelectable()).assertIsSelected()
            assertEquals(10, enforcementPrefs.getInterval())
            composeRule.waitUntil(5_000) { enforcementAlarm() != null }
        }
    }

    @Test
    fun modeOnlyChangeRefreshesTheVisibleGrayscaleState() {
        writeGrayscale(true)
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Grayscale on").assertIsDisplayed()

            Settings.Secure.putInt(context.contentResolver, MODE, 12)
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            composeRule.onNodeWithText("Grayscale off").assertIsDisplayed()
        }
    }

    private fun writeGrayscale(enabled: Boolean) {
        Settings.Secure.putInt(context.contentResolver, MODE, 0)
        Settings.Secure.putInt(context.contentResolver, ENABLED, if (enabled) 1 else 0)
    }

    private fun enforcementAlarm(): PendingIntent? = PendingIntent.getBroadcast(
        context,
        GrayoutService.ENFORCEMENT_ALARM_REQUEST_CODE,
        Intent(context, EnforcementAlarmReceiver::class.java)
            .setAction(GrayoutService.ACTION_ENFORCEMENT_TICK),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
    )

    companion object {
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
    }
}

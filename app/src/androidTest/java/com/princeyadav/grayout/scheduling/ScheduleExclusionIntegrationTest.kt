package com.princeyadav.grayout.scheduling

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementAlarmReceiver
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.ExclusionTransition
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayscaleController
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.applyExclusionTransition
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Real broadcasts, Room resynchronization, and secure settings on a test device. */
@RunWith(AndroidJUnit4::class)
class ScheduleExclusionIntegrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val exclusions = ExclusionPrefs(prefs)
    private val enforcement = EnforcementPrefs(prefs)
    private val grayscale = GrayscaleManager(context)
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private var savedSchedules = emptyList<Schedule>()
    private var savedPreferences: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null

    @Before
    fun setUp(): Unit = runBlocking {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.WRITE_SECURE_SETTINGS,
            "android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND",
        )
        stopService()
        savedPreferences = prefs.all
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        savedSchedules = dao.getAll()
        savedSchedules.forEach { dao.delete(it) }
        prefs.edit().clear().commit()
        // Any real foreground poll during the receiver's service restart stays
        // inside an exclusion; these tests drive the exit explicitly afterward.
        exclusions.setExcludedPackages(context.packageManager.getInstalledApplications(0)
            .map { it.packageName }.toSet())
        Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        Settings.Secure.putInt(context.contentResolver, MODE, 0)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        try {
            stopService()
            cancelAlarm(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
            cancelAlarm(EnforcementAlarmReceiver::class.java, 2001, GrayoutService.ACTION_ENFORCEMENT_TICK)
            dao.getAll().forEach { dao.delete(it) }
            savedSchedules.forEach { dao.insert(it) }
            Settings.Secure.putString(context.contentResolver, ENABLED, savedEnabled)
            Settings.Secure.putString(context.contentResolver, MODE, savedMode)
            val editor = prefs.edit().clear()
            savedPreferences.forEach { (key, value) ->
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is Long -> editor.putLong(key, value)
                    is Float -> editor.putFloat(key, value)
                    is String -> editor.putString(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
            editor.commit()
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun startBroadcastKeepsExcludedAppColoredThenRestoresGrayOnExit() {
        enterExclusion(wasOn = false)
        fireSchedule(isStart = true)

        assertFalse("Schedule start must not gray an excluded app", grayscale.isGrayscaleEnabled())
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())
        exitExclusion()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun endBroadcastReplacesSavedStateSoExitDoesNotRestoreAnEndedSchedule() {
        enterExclusion(wasOn = true)
        fireSchedule(isStart = false)

        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        exitExclusion()
        assertFalse("Exiting after the schedule ended must stay colored", grayscale.isGrayscaleEnabled())
        assertEquals(0, enforcement.getInterval())
    }

    @Test
    fun endBroadcastPreservesStandingEnforcementWhileKeepingExclusionColored() {
        enforcement.setInterval(5)
        enterExclusion(wasOn = true)
        fireSchedule(isStart = false)

        assertFalse(grayscale.isGrayscaleEnabled())
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        assertEquals(5, enforcement.getInterval())
        stopService()
        var enforcementSignaled = false
        applyExclusionTransition(ExclusionTransition.Exit(false), exclusions, grayscale, 5) {
            enforcementSignaled = true
        }
        assertTrue(enforcementSignaled)
    }

    @Test
    fun endBroadcastRepairsGrayscaleEnabledManuallyDuringAnExclusion() {
        enterExclusion(wasOn = true)
        assertTrue(grayscale.setGrayscale(true))
        fireSchedule(isStart = false)
        assertFalse(grayscale.isGrayscaleEnabled())
        exitExclusion()
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun endBroadcastRepairsAnEntryWhoseColorWriteFailed() {
        assertTrue(grayscale.setGrayscale(true))
        val failedWrite = object : GrayscaleController by grayscale {
            override fun setGrayscale(enabled: Boolean): Boolean = false
        }
        applyExclusionTransition(ExclusionTransition.Enter(true), exclusions, failedWrite, 0) {}
        assertTrue(grayscale.isGrayscaleEnabled())

        fireSchedule(isStart = false)
        assertFalse(grayscale.isGrayscaleEnabled())
        exitExclusion()
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun activeWindowRescheduleDefersGrayscaleUntilExclusionExit() = runBlocking {
        val start = LocalTime.now().minusHours(1)
        val end = start.plusHours(2)
        dao.insert(Schedule(
            name = "Active test window", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute,
        ))
        enterExclusion(wasOn = false)
        repeat(2) { ScheduleAlarmManager(context).reschedule(ScheduleRepository(dao)) }

        assertFalse("Resynchronization must respect the active exclusion", grayscale.isGrayscaleEnabled())
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())
        exitExclusion()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun rescheduleDoesNotRestoreAWindowThatEndedBeforeItsWrite() = runBlocking {
        val beforeEnd = LocalDateTime.now()
        val start = beforeEnd.minusHours(1)
        val end = beforeEnd.plusMinutes(1)
        dao.insert(Schedule(
            name = "Ending test window", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute,
        ))
        enterExclusion(wasOn = false)
        var reads = 0
        ScheduleAlarmManager(context) {
            if (reads++ == 0) beforeEnd else end.plusMinutes(1)
        }.reschedule(ScheduleRepository(dao))

        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        exitExclusion()
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    private fun enterExclusion(wasOn: Boolean) {
        assertTrue(grayscale.setGrayscale(wasOn))
        applyExclusionTransition(ExclusionTransition.Enter(wasOn), exclusions, grayscale, enforcement.getInterval()) {}
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    private fun exitExclusion() {
        stopService()
        applyExclusionTransition(
            ExclusionTransition.Exit(exclusions.wasGrayscaleOnBeforeExclusion()),
            exclusions, grayscale, enforcement.getInterval(),
        ) {}
    }

    private fun fireSchedule(isStart: Boolean) {
        val completed = CountDownLatch(1)
        val intent = Intent(context, ScheduleReceiver::class.java)
            .setAction(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
            .putExtra(ScheduleAlarmManager.EXTRA_IS_START, isStart)
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { completed.countDown() }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_OK, null, null)
        assertTrue("Schedule receiver must complete its async reschedule", completed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun stopService() {
        context.stopService(Intent(context, GrayoutService::class.java))
        instrumentation.waitForIdleSync()
    }

    private fun cancelAlarm(receiver: Class<*>, code: Int, action: String) {
        PendingIntent.getBroadcast(context, code, Intent(context, receiver).setAction(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    companion object {
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
    }
}

package com.princeyadav.grayout.scheduling

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.awaitScheduleStartupRecovery
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.data.scheduleOperationMutex
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementAlarmReceiver
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

/** Launches the real activity after removing the OS registration but retaining schedule data. */
@RunWith(AndroidJUnit4::class)
class ScheduleStartupRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private val repository = ScheduleRepository(dao)
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val state = ScheduleAlarmState(prefs)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val gray = GrayscaleManager(context)
    private var savedRows = emptyList<Schedule>()
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null

    @Before fun setUp(): Unit = runBlocking {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        awaitScheduleStartupRecovery()
        stopService()
        savedRows = dao.getAll()
        savedPrefs = prefs.all
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        savedRows.forEach { dao.delete(it) }
        prefs.edit().clear().commit()
        cancelScheduleAlarm()
        Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        Settings.Secure.putInt(context.contentResolver, MODE, 0)
    }

    @After fun tearDown(): Unit = runBlocking {
        try {
            // Closing an ActivityScenario no longer cancels pending recovery.
            // Finish every launch before restoring the database and preferences.
            awaitScheduleStartupRecovery()
            stopService()
            scheduleOperationMutex.withLock { }
            cancelScheduleAlarm()
            cancel(SystemScheduleReceiver::class.java, 1002, SystemScheduleReceiver.ACTION_RETRY)
            cancel(EnforcementAlarmReceiver::class.java, 2001, GrayoutService.ACTION_ENFORCEMENT_TICK)
            dao.getAll().forEach { dao.delete(it) }
            savedRows.forEach { dao.insert(it) }
            val editor = prefs.edit().clear()
            savedPrefs.forEach { (key, value) ->
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
            Settings.Secure.putString(context.contentResolver, ENABLED, savedEnabled)
            Settings.Secure.putString(context.contentResolver, MODE, savedMode)
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test fun openingAppRestoresCancelledFutureAlarm() = runBlocking {
        seedAndArm(2, 4)
        val old = checkNotNull(state.read())
        assertTrue(old.isStart)
        cancelScheduleAlarm()
        assertNull(scheduleToken())

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitRegistrationAfter(old.generation)
            val restored = checkNotNull(state.read())
            assertEquals(old.scheduleId, restored.scheduleId)
            assertEquals(old.triggerMillis, restored.triggerMillis)
            assertTrue(restored.isStart)
            assertFalse(gray.isGrayscaleEnabled())
        }
    }

    @Test fun closingActivityWhileRecoveryIsWaitingStillRestoresAlarm() = runBlocking {
        seedAndArm(2, 4)
        val old = checkNotNull(state.read())
        cancelScheduleAlarm()

        // An edit or receiver may already own the shared lock when the user
        // launches the app. Destroy the activity before recovery can acquire it.
        scheduleOperationMutex.withLock {
            ActivityScenario.launch(MainActivity::class.java).use {
                instrumentation.waitForIdleSync()
                assertNull(scheduleToken())
            }
            instrumentation.waitForIdleSync()
            assertNull(scheduleToken())
        }

        awaitRegistrationAfter(old.generation)
        assertEquals(old.triggerMillis, checkNotNull(state.read()).triggerMillis)
        assertFalse(gray.isGrayscaleEnabled())
    }

    @Test fun openingAppPreservesManualColorWithinContinuingSchedule() = runBlocking {
        seedAndArm(-2, 2)
        val old = checkNotNull(state.read())
        assertFalse(old.isStart)
        assertTrue(gray.isGrayscaleEnabled())
        assertTrue(gray.setGrayscale(false))
        cancelScheduleAlarm()

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitRegistrationAfter(old.generation)
            assertFalse(checkNotNull(state.read()).isStart)
            assertEquals(old.triggerMillis, checkNotNull(state.read()).triggerMillis)
            assertFalse("Reopening must preserve a manual color choice in the same window", gray.isGrayscaleEnabled())
        }
    }

    @Test fun openingAppPreservesManualGrayscaleOutsideSchedules() = runBlocking {
        seedAndArm(2, 4)
        val old = checkNotNull(state.read())
        assertTrue(gray.setGrayscale(true))
        cancelScheduleAlarm()

        ActivityScenario.launch(MainActivity::class.java).use {
            awaitRegistrationAfter(old.generation)
            assertTrue(gray.isGrayscaleEnabled())
        }
    }

    @Test fun recreationAndResumeDoNotReplayActiveStart() = runBlocking {
        seedAndArm(-2, 2)
        val old = checkNotNull(state.read())
        cancelScheduleAlarm()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            awaitRegistrationAfter(old.generation)
            assertTrue(gray.setGrayscale(false))
            val beforeRecreate = checkNotNull(state.read()).generation
            scenario.recreate()
            awaitRegistrationAfter(beforeRecreate)
            assertFalse(gray.isGrayscaleEnabled())

            val beforeResume = checkNotNull(state.read()).generation
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            instrumentation.waitForIdleSync()
            scheduleOperationMutex.withLock { }
            assertEquals("Resume alone must not rebuild or replay schedule alarms", beforeResume, state.read()?.generation)
            assertFalse(gray.isGrayscaleEnabled())
        }
    }

    private suspend fun seedAndArm(startHours: Long, endHours: Long) {
        val now = LocalDateTime.now()
        val start = now.plusHours(startHours)
        val end = now.plusHours(endHours)
        dao.insert(Schedule(name = "Startup recovery", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute))
        ScheduleAlarmManager(context).reschedule(repository)
        assertNotNull(scheduleToken())
    }

    private suspend fun awaitRegistrationAfter(oldGeneration: String) {
        await("Opening the app must restore the missing schedule alarm") {
            state.read()?.generation != oldGeneration && scheduleToken() != null
        }
        // Persistence precedes the AlarmManager call. Drain the complete recovery.
        awaitScheduleStartupRecovery()
        scheduleOperationMutex.withLock { }
        val descriptor = instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
        val dump = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        val alarmTag = Regex("^\\s+tag=\\*walarm\\*:" +
            Regex.escape(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE) + "\\s*$", RegexOption.MULTILINE)
        assertTrue("AlarmManager must contain the restored registration, not only a PendingIntent", alarmTag.containsMatchIn(dump))
    }

    private fun stopService() {
        context.stopService(Intent(context, GrayoutService::class.java))
        await("Foreground service must stop before resetting its fixture") { !GrayoutService.isRunning.value }
        instrumentation.waitForIdleSync()
    }

    private fun cancelScheduleAlarm() = cancel(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)

    private fun scheduleToken() = PendingIntent.getBroadcast(context, 1001,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(receiver: Class<*>, code: Int, action: String) {
        PendingIntent.getBroadcast(context, code, Intent(context, receiver).setAction(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
    }

    private fun await(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < deadline) SystemClock.sleep(25)
        assertTrue(message, condition())
    }

    private companion object {
        const val ENABLED = "accessibility_display_daltonizer_enabled"
        const val MODE = "accessibility_display_daltonizer"
    }
}

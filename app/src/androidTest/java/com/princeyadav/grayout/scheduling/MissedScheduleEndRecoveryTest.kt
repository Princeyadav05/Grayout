package com.princeyadav.grayout.scheduling

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.GrayscaleController
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset

/** Real Room, AlarmManager and secure settings; the clock models time spent powered off. */
@RunWith(AndroidJUnit4::class)
class MissedScheduleEndRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private val repository = ScheduleRepository(dao)
    private val gray = GrayscaleManager(context)
    private val state = ScheduleAlarmState(prefs)
    private val pending = ScheduleReconciliationState(prefs)
    // Keep registered OS alarms in the future, so only explicit test deliveries run.
    private val date = LocalDate.of(2099, 1, 5)
    private var savedRows = emptyList<Schedule>()
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null

    @Before fun setUp(): Unit = runBlocking {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.stopService(Intent(context, GrayoutService::class.java))
        instrumentation.waitForIdleSync()
        savedRows = dao.getAll()
        savedPrefs = prefs.all
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        savedRows.forEach { dao.delete(it) }
        prefs.edit().clear().commit()
        gray.setGrayscale(false)
    }

    @After fun tearDown(): Unit = runBlocking {
        try {
            cancel(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
            cancel(SystemScheduleReceiver::class.java, 1002, SystemScheduleReceiver.ACTION_RETRY)
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

    @Test fun bootAfterSuccessfulStartAndMissedEndClosesBeforeReplacingItsEvidence() = runBlocking {
        startSchedule()
        val old = checkNotNull(state.read())
        cancel(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
        // BootReceiver uses reschedule on a new manager after power loss removes OS alarms.
        managerAt(10, 30).reschedule(repository)
        assertFalse("Boot must close the successfully started occurrence whose end was missed", gray.isGrayscaleEnabled())
        assertNull(pending.read())
        assertTrue(checkNotNull(state.read()).isStart)
        assertNotEquals(old.generation, state.read()?.generation)
        assertNotNull(token(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE))
    }

    @Test fun failedBootCloseRetainsRetryEvidenceAfterRearmingAndNextRecoveryCompletesIt() = runBlocking {
        startSchedule()
        val failed = object : GrayscaleController by gray {
            override fun setGrayscale(enabled: Boolean) = false
        }
        managerAt(10, 30, failed).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
        assertNotNull("A failed close must survive replacing the end alarm", pending.read())
        assertTrue(checkNotNull(state.read()).isStart)
        assertNotNull(token(SystemScheduleReceiver::class.java, 1002, SystemScheduleReceiver.ACTION_RETRY))
        managerAt(10, 31).reschedule(repository)
        assertFalse(gray.isGrayscaleEnabled())
        assertNull(pending.read())
    }

    @Test fun missedEndClearsDeferredExclusionTarget() = runBlocking {
        startSchedule()
        val exclusions = ExclusionPrefs(prefs)
        exclusions.setWasGrayscaleOnBeforeExclusion(true)
        exclusions.setExcludedAppActive(true)
        gray.setGrayscale(false)
        managerAt(10, 30).reschedule(repository)
        assertFalse(gray.isGrayscaleEnabled())
        assertFalse("Leaving the exclusion must not restore the ended schedule", exclusions.wasGrayscaleOnBeforeExclusion())
        assertNull(pending.read())
    }

    @Test fun bootDuringSameActiveWindowStillSynchronizesGrayscale() = runBlocking {
        startSchedule()
        gray.setGrayscale(false)
        managerAt(9, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
        assertFalse(checkNotNull(state.read()).isStart)
    }

    @Test fun bootDuringAnotherActiveScheduleKeepsGrayscaleOn() = runBlocking {
        startSchedule()
        dao.insert(schedule().copy(name = "Next window", startTimeHour = 10, endTimeHour = 11))
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
        assertFalse(checkNotNull(state.read()).isStart)
    }

    @Test fun interruptedActiveBootWithOldStartStillClosesAfterTheWindow() = runBlocking {
        dao.insert(schedule())
        managerAt(8, 30).reschedule(repository)
        assertTrue(checkNotNull(state.read()).isStart)
        verifyInterruptedActivationCloses()
    }

    @Test fun interruptedActiveBootWithoutAnAlarmStillClosesAfterTheWindow() = runBlocking {
        dao.insert(schedule())
        assertNull(state.read())
        verifyInterruptedActivationCloses()
    }

    @Test fun noPriorRegistrationPreservesManualGrayscaleOutsideSchedule() = runBlocking {
        dao.insert(schedule())
        gray.setGrayscale(true)
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
    }

    @Test fun missedStartWithoutPriorCoveragePreservesManualGrayscale() = runBlocking {
        dao.insert(schedule())
        managerAt(8, 30).reschedule(repository)
        assertTrue(checkNotNull(state.read()).isStart)
        gray.setGrayscale(true)
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
    }

    @Test fun changedScheduleCannotClaimManualGrayscale() = runBlocking {
        val row = startSchedule()
        dao.update(row.copy(endTimeMinute = 15))
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
    }

    @Test fun disabledScheduleCannotClaimManualGrayscale() = runBlocking {
        val row = startSchedule()
        dao.setEnabled(row.id, false)
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
        assertNull(state.read())
    }

    @Test fun deletedScheduleCannotClaimManualGrayscale() = runBlocking {
        val row = startSchedule()
        dao.delete(row)
        managerAt(10, 30).reschedule(repository)
        assertTrue(gray.isGrayscaleEnabled())
        assertNull(state.read())
    }

    private suspend fun startSchedule(): Schedule {
        val id = dao.insert(schedule())
        managerAt(8, 30).reschedule(repository)
        val start = checkNotNull(state.read())
        assertTrue(start.isStart)
        assertEquals(true, managerAt(9, 0).handleAlarm(repository, start.generation, true))
        assertTrue(gray.isGrayscaleEnabled())
        assertFalse(checkNotNull(state.read()).isStart)
        // A successful start deliberately has no failed-write reconciliation record.
        assertNull(pending.read())
        return checkNotNull(dao.getById(id))
    }

    private suspend fun verifyInterruptedActivationCloses() {
        // Interrupt exactly after a successful display write clears its retry
        // record. Only durable alarm provenance can recover after this point.
        val interruptedPrefs = object : SharedPreferences by prefs {
            override fun edit(): SharedPreferences.Editor {
                val delegate = prefs.edit()
                var clearsRecovery = false
                return object : SharedPreferences.Editor by delegate {
                    override fun remove(key: String?): SharedPreferences.Editor {
                        delegate.remove(key)
                        if (key == "schedule_reconciliation_configuration") clearsRecovery = true
                        return this
                    }
                    override fun commit(): Boolean {
                        val success = delegate.commit()
                        if (clearsRecovery) error("Interrupted after successful display write")
                        return success
                    }
                }
            }
        }
        val interruptedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int): SharedPreferences = interruptedPrefs
        }
        try {
            ScheduleAlarmManager(interruptedContext,
                Clock.fixed(date.atTime(9, 30).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), gray)
                .reschedule(repository)
            fail("Expected injected interruption")
        } catch (error: IllegalStateException) {
            assertEquals("Interrupted after successful display write", error.message)
        }
        assertTrue(gray.isGrayscaleEnabled())
        assertNull(pending.read())
        val interruptedAlarm = state.read()
        cancel(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
        managerAt(10, 30).reschedule(repository)
        assertFalse("An interrupted successful activation must still close after restart", gray.isGrayscaleEnabled())
        assertFalse("Closing provenance must be persisted before activation", checkNotNull(interruptedAlarm).isStart)
    }

    private fun schedule() = Schedule(name = "Boot recovery", daysOfWeek = date.dayOfWeek.name.take(3),
        startTimeHour = 9, startTimeMinute = 0, endTimeHour = 10, endTimeMinute = 0)

    private fun managerAt(hour: Int, minute: Int, controller: GrayscaleController = gray) =
        ScheduleAlarmManager(context,
            Clock.fixed(date.atTime(hour, minute).toInstant(ZoneOffset.UTC), ZoneOffset.UTC), controller)

    private fun token(receiver: Class<*>, code: Int, action: String) =
        PendingIntent.getBroadcast(context, code, Intent(context, receiver).setAction(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    private fun cancel(receiver: Class<*>, code: Int, action: String) {
        token(receiver, code, action)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it)
            it.cancel()
        }
    }

    private companion object {
        const val ENABLED = "accessibility_display_daltonizer_enabled"
        const val MODE = "accessibility_display_daltonizer"
    }
}

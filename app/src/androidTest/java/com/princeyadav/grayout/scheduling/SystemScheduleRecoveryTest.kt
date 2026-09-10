package com.princeyadav.grayout.scheduling

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.Intent
import android.os.SystemClock
import android.os.Looper
import android.os.Handler
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.data.scheduleOperationMutex
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/** Uses real system timezone broadcasts, Room, secure settings, and AlarmManager. */
@RunWith(AndroidJUnit4::class)
class SystemScheduleRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private val repository = ScheduleRepository(dao)
    private val gray = GrayscaleManager(context)
    private val alarms = context.getSystemService(AlarmManager::class.java)
    private val state = ScheduleAlarmState(prefs)
    private val pending = ScheduleReconciliationState(prefs)
    private var originalZone = ""
    private var savedRows = emptyList<Schedule>()
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null

    @Before fun setUp(): Unit = runBlocking {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(
            Manifest.permission.WRITE_SECURE_SETTINGS, Manifest.permission.SET_TIME_ZONE,
            "android.permission.START_FOREGROUND_SERVICES_FROM_BACKGROUND",
        )
        context.stopService(Intent(context, GrayoutService::class.java))
        instrumentation.waitForIdleSync()
        originalZone = TimeZone.getDefault().id
        savedRows = dao.getAll()
        savedPrefs = prefs.all
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        savedRows.forEach { dao.delete(it) }
        prefs.edit().clear().commit()
        changeEmptyFixtureZoneAndDrain("Etc/UTC")
        Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        Settings.Secure.putInt(context.contentResolver, MODE, 0)
    }

    @After fun tearDown(): Unit = runBlocking {
        try {
            context.stopService(Intent(context, GrayoutService::class.java))
            await { !GrayoutService.isRunning.value }
            instrumentation.waitForIdleSync()
            cancel(ScheduleReceiver::class.java, 1001, ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
            cancel(SystemScheduleReceiver::class.java, 1002, SystemScheduleReceiver.ACTION_RETRY)
            cancel(EnforcementAlarmReceiver::class.java, 2001, GrayoutService.ACTION_ENFORCEMENT_TICK)
            dao.getAll().forEach { dao.delete(it) }
            prefs.edit().clear().commit()
            changeEmptyFixtureZoneAndDrain(originalZone)
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
        } finally { instrumentation.uiAutomation.dropShellPermissionIdentity() }
    }

    @Test fun realTimezoneBroadcastClosesOldWindowAndRearmsFutureLocalStart() = runBlocking {
        seedWindow(-1, 1)
        ScheduleAlarmManager(context).reschedule(repository)
        val old = checkNotNull(state.read())
        assertFalse(old.isStart)
        assertTrue(gray.isGrayscaleEnabled())
        alarms.setTimeZone("Etc/GMT-4")
        await { state.read()?.let { it.generation != old.generation && it.zoneId == "Etc/GMT-4" } == true }
        scheduleOperationMutex.withLock { }
        assertFalse(gray.isGrayscaleEnabled())
        assertTrue(checkNotNull(state.read()).isStart)
        assertNotNull(scheduleToken())
        assertNull(ScheduleAlarmManager(context).handleAlarm(repository, old.generation, false))
        assertFalse(gray.isGrayscaleEnabled())
    }

    @Test fun realTimezoneBroadcastStartsNewWindow() = runBlocking {
        seedWindow(3, 5)
        ScheduleAlarmManager(context).reschedule(repository)
        val old = checkNotNull(state.read())
        assertTrue(old.isStart)
        alarms.setTimeZone("Etc/GMT-4")
        await { state.read()?.generation != old.generation && gray.isGrayscaleEnabled() }
        scheduleOperationMutex.withLock { }
        assertFalse(checkNotNull(state.read()).isStart)
    }

    @Test fun sameActiveWindowPreservesManualColor() = runBlocking {
        seedWindow(-3, 3)
        ScheduleAlarmManager(context).reschedule(repository)
        val old = checkNotNull(state.read())
        gray.setGrayscale(false)
        alarms.setTimeZone("Etc/GMT-1")
        await { state.read()?.generation != old.generation }
        scheduleOperationMutex.withLock { }
        assertFalse(gray.isGrayscaleEnabled())
    }

    @Test fun timezoneClosureReplacesDeferredExclusionTargetWithoutGrayingDisplay() = runBlocking {
        seedWindow(-1, 1)
        ScheduleAlarmManager(context).reschedule(repository)
        val exclusions = ExclusionPrefs(prefs)
        exclusions.setExcludedPackages(setOf(context.packageName))
        exclusions.setWasGrayscaleOnBeforeExclusion(true)
        exclusions.setExcludedAppActive(true)
        gray.setGrayscale(false)
        val old = checkNotNull(state.read())
        alarms.setTimeZone("Etc/GMT-4")
        await { state.read()?.generation != old.generation }
        scheduleOperationMutex.withLock { }
        assertFalse(gray.isGrayscaleEnabled())
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        assertTrue(exclusions.isExcludedAppActive())
    }

    @Test fun failedCloseRetainsEvidenceAndRealRetryReceiverCompletesIt() = runBlocking {
        seedWindow(-5, -3)
        val now = Instant.now()
        ScheduleAlarmManager(context, Clock.fixed(now.minusSeconds(4 * 3600), ZoneId.of("Etc/UTC")))
            .reschedule(repository)
        val failed = object : GrayscaleController by gray {
            override fun setGrayscale(enabled: Boolean) = false
        }
        ScheduleAlarmManager(context, grayscale = failed).reconcileSystemChange(repository)
        assertNotNull(pending.read())
        assertTrue(gray.isGrayscaleEnabled())
        context.sendBroadcast(Intent(context, SystemScheduleReceiver::class.java)
            .setAction(SystemScheduleReceiver.ACTION_RETRY))
        await { pending.read() == null && !gray.isGrayscaleEnabled() }
        scheduleOperationMutex.withLock { }
        assertNotNull(scheduleToken())
    }

    @Test fun bootStyleReschedulePreservesAndCompletesPendingClosure() = runBlocking {
        seedWindow(-5, -3)
        val now = Instant.now()
        ScheduleAlarmManager(context, Clock.fixed(now.minusSeconds(4 * 3600), ZoneId.of("Etc/UTC")))
            .reschedule(repository)
        val failed = object : GrayscaleController by gray {
            override fun setGrayscale(enabled: Boolean) = false
        }
        ScheduleAlarmManager(context, grayscale = failed).reconcileSystemChange(repository)
        assertNotNull(pending.read())
        // A new instance follows the same path used by BootReceiver after reboot.
        ScheduleAlarmManager(context).reschedule(repository)
        assertNull(pending.read())
        assertFalse(gray.isGrayscaleEnabled())
    }

    @Test fun changedConfigurationCancelsPendingCloseRatherThanOverwritingManualGray() = runBlocking {
        val id = seedWindow(-1, 1)
        ScheduleAlarmManager(context).reschedule(repository)
        val failed = object : GrayscaleController by gray {
            override fun setGrayscale(enabled: Boolean) = false
        }
        ScheduleAlarmManager(context, Clock.fixed(Instant.now().plusSeconds(4 * 3600), ZoneId.of("Etc/UTC")), failed)
            .reconcileSystemChange(repository)
        assertNotNull(pending.read())
        dao.delete(checkNotNull(dao.getById(id)))
        ScheduleAlarmManager(context).reconcileSystemChange(repository, retryOnly = true)
        assertNull(pending.read())
        assertTrue(gray.isGrayscaleEnabled())
    }

    @Test fun exactAlarmRecoveryUsesSameElapsedDeadlineAndRecreatesCanceledOsRegistration() {
        EnforcementPrefs(prefs).setInterval(5)
        val deadline = SystemClock.elapsedRealtime() + 5 * 60_000L
        scheduleEnforcementAlarmAtElapsed(context, deadline)
        val intent = Intent(context, EnforcementAlarmReceiver::class.java).setAction(GrayoutService.ACTION_ENFORCEMENT_TICK)
        val token = checkNotNull(PendingIntent.getBroadcast(context, 2001, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
        alarms.cancel(token)
        token.cancel()
        restoreEnforcementAlarm(context)
        assertEquals(deadline, context.enforcementAlarmState().deadline(context.bootCount()))
        assertNotNull(PendingIntent.getBroadcast(context, 2001, intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE))
    }

    @Test fun expiredEnforcementRecoveryActuallyFiresWithoutStartingANewFullInterval() {
        EnforcementPrefs(prefs).setInterval(5)
        context.enforcementAlarmState().save(SystemClock.elapsedRealtime() - 1_000, context.bootCount())
        restoreEnforcementAlarm(context)
        await { gray.isGrayscaleEnabled() && context.enforcementAlarmState().deadline(context.bootCount()) == null }
    }

    @Test fun coldServiceRecreatesCanceledCountdownAtItsOriginalFutureDeadline() {
        EnforcementPrefs(prefs).setInterval(5)
        val deadline = SystemClock.elapsedRealtime() + 5 * 60_000L
        scheduleEnforcementAlarmAtElapsed(context, deadline)
        cancel(EnforcementAlarmReceiver::class.java, 2001, GrayoutService.ACTION_ENFORCEMENT_TICK)
        context.startForegroundService(Intent(context, GrayoutService::class.java))
        await { GrayoutService.isRunning.value && enforcementToken() != null }
        instrumentation.waitForIdleSync()
        assertEquals(deadline, context.enforcementAlarmState().deadline(context.bootCount()))
    }

    @Test fun coldServiceRecreatesExpiredCountdownForImmediateDelivery() {
        EnforcementPrefs(prefs).setInterval(5)
        context.enforcementAlarmState().save(SystemClock.elapsedRealtime() - 1_000, context.bootCount())
        context.startForegroundService(Intent(context, GrayoutService::class.java))
        await { gray.isGrayscaleEnabled() }
        instrumentation.waitForIdleSync()
        assertNull(context.enforcementAlarmState().deadline(context.bootCount()))
    }

    @Test fun userIntervalChangeCannotBeOverwrittenByAnOlderConcurrentRestore() {
        EnforcementPrefs(prefs).setInterval(5)
        val oldDeadline = SystemClock.elapsedRealtime() + 5 * 60_000L
        scheduleEnforcementAlarmAtElapsed(context, oldDeadline)
        val read = CountDownLatch(1)
        val release = CountDownLatch(1)
        val captured = AtomicBoolean(false)
        val failure = AtomicReference<Throwable>()
        val delayedPrefs = object : SharedPreferences by prefs {
            override fun getLong(key: String?, default: Long): Long {
                val value = prefs.getLong(key, default)
                if (key == "enforcement_alarm_elapsed_deadline" && captured.compareAndSet(false, true)) {
                    read.countDown()
                    check(release.await(5, TimeUnit.SECONDS))
                }
                return value
            }
        }
        val delayedContext = object : ContextWrapper(context) {
            override fun getSharedPreferences(name: String?, mode: Int) = delayedPrefs
        }
        val restoring = Thread {
            try { restoreEnforcementAlarm(delayedContext) } catch (error: Throwable) { failure.set(error) }
        }
        restoring.start()
        try {
            assertTrue(read.await(5, TimeUnit.SECONDS))
            EnforcementPrefs(prefs).setInterval(15)
            context.startForegroundService(Intent(context, GrayoutService::class.java)
                .putExtra(GrayoutService.EXTRA_INTERVAL, 15)
                .putExtra(GrayoutService.EXTRA_USER_INTERVAL_CHANGE, true))
            // Without atomic service transitions, Main can finish the new 15-minute
            // registration while the worker still holds its stale five-minute read.
            await { Looper.getMainLooper().thread.state == Thread.State.BLOCKED ||
                (context.enforcementAlarmState().deadline(context.bootCount()) ?: 0L) > oldDeadline }
        } finally { release.countDown() }
        restoring.join(5_000)
        assertFalse(restoring.isAlive)
        failure.get()?.let { throw AssertionError("Concurrent restore failed", it) }
        instrumentation.waitForIdleSync()
        val deadline = checkNotNull(context.enforcementAlarmState().deadline(context.bootCount()))
        assertTrue(deadline > SystemClock.elapsedRealtime() + 14 * 60_000)
        assertEquals(15, EnforcementPrefs(prefs).getInterval())
        // A delayed schedule/regrant intent carries an old snapshot, not a new user choice.
        context.startForegroundService(Intent(context, GrayoutService::class.java)
            .putExtra(GrayoutService.EXTRA_INTERVAL, 5))
        instrumentation.waitForIdleSync()
        assertEquals(deadline, context.enforcementAlarmState().deadline(context.bootCount()))
    }

    @Test fun changedPreferenceBeforeImplicitStartupCannotKeepAnOldIntervalCountdown() {
        EnforcementPrefs(prefs).setInterval(5)
        val oldDeadline = SystemClock.elapsedRealtime() + 5 * 60_000L
        scheduleEnforcementAlarmAtElapsed(context, oldDeadline)
        val oldToken = checkNotNull(enforcementToken())
        // The process can die after preferences change but before the explicit
        // command reaches the service. An implicit launch must reject the old timer.
        EnforcementPrefs(prefs).setInterval(15)
        context.startForegroundService(Intent(context, GrayoutService::class.java))
        await { GrayoutService.isRunning.value &&
            (context.enforcementAlarmState().deadline(context.bootCount(), 15) ?: 0L) > oldDeadline }
        instrumentation.waitForIdleSync()
        assertNotEquals(oldToken, enforcementToken())
        val implicitToken = checkNotNull(enforcementToken())
        context.startForegroundService(Intent(context, GrayoutService::class.java)
            .putExtra(GrayoutService.EXTRA_INTERVAL, 15)
            .putExtra(GrayoutService.EXTRA_USER_INTERVAL_CHANGE, true))
        instrumentation.waitForIdleSync()
        assertNotEquals(implicitToken, enforcementToken())
        assertTrue(checkNotNull(context.enforcementAlarmState().deadline(context.bootCount(), 15)) >
            SystemClock.elapsedRealtime() + 14 * 60_000)
    }

    @Test fun regrantRejectsDeadlineFromADifferentIntervalAndItsStaleToken() {
        EnforcementPrefs(prefs).setInterval(5)
        val oldDeadline = SystemClock.elapsedRealtime() + 5 * 60_000L
        scheduleEnforcementAlarmAtElapsed(context, oldDeadline)
        val oldToken = checkNotNull(enforcementToken())
        EnforcementPrefs(prefs).setInterval(15)
        restoreEnforcementAlarm(context)
        assertNotEquals(oldToken, enforcementToken())
        assertTrue(checkNotNull(context.enforcementAlarmState().deadline(context.bootCount(), 15)) >
            SystemClock.elapsedRealtime() + 14 * 60_000)
    }

    @Test fun queuedOldEnforcementDeliveryCannotApplyOrCancelANewerUserCountdown() {
        EnforcementPrefs(prefs).setInterval(5)
        val oldGeneration = context.enforcementAlarmState().save(
            SystemClock.elapsedRealtime() - 1_000, context.bootCount(), 5)
        // This captures the payload an already-queued old alarm would deliver.
        EnforcementPrefs(prefs).setInterval(15)
        context.startForegroundService(Intent(context, GrayoutService::class.java)
            .putExtra(GrayoutService.EXTRA_INTERVAL, 15)
            .putExtra(GrayoutService.EXTRA_USER_INTERVAL_CHANGE, true))
        await { context.enforcementAlarmState().deadline(context.bootCount(), 15) != null && enforcementToken() != null }
        instrumentation.waitForIdleSync()
        val deadline = context.enforcementAlarmState().deadline(context.bootCount(), 15)
        val generation = context.enforcementAlarmState().generation()
        val token = enforcementToken()
        deliverEnforcement(oldGeneration)
        // If a reused PendingIntent acquired the new extras, its early delivery
        // still must not consume the future registration.
        deliverEnforcement(generation)
        deliverEnforcement(null)
        assertFalse(gray.isGrayscaleEnabled())
        assertEquals(deadline, context.enforcementAlarmState().deadline(context.bootCount(), 15))
        assertEquals(token, enforcementToken())
    }

    @Test fun staleEnforcementDeliveryRepairsPersistedButUnregisteredCurrentAlarm() {
        EnforcementPrefs(prefs).setInterval(5)
        val old = context.enforcementAlarmState().save(
            SystemClock.elapsedRealtime() - 1_000, context.bootCount(), 5)
        EnforcementPrefs(prefs).setInterval(15)
        val deadline = SystemClock.elapsedRealtime() + 15 * 60_000L
        val current = context.enforcementAlarmState().save(deadline, context.bootCount(), 15)
        // Model process death after the durable write but before AlarmManager.set.
        assertNull(enforcementToken())
        deliverEnforcement(old)
        assertNotNull(enforcementToken())
        assertFalse(gray.isGrayscaleEnabled())
        assertEquals(current, context.enforcementAlarmState().generation())
        assertEquals(deadline, context.enforcementAlarmState().deadline(context.bootCount(), 15))
    }

    @Test fun duplicateConsumedEnforcementDeliveryCannotReapplyAfterManualColor() {
        EnforcementPrefs(prefs).setInterval(5)
        val generation = context.enforcementAlarmState().save(
            SystemClock.elapsedRealtime() - 1_000, context.bootCount(), 5)
        deliverEnforcement(generation)
        assertTrue(gray.isGrayscaleEnabled())
        assertNull(context.enforcementAlarmState().deadline(context.bootCount()))
        gray.setGrayscale(false)
        deliverEnforcement(generation)
        deliverEnforcement(null)
        assertFalse(gray.isGrayscaleEnabled())
        assertNull(enforcementToken())
    }

    @Test fun actionOnlyLegacyEnforcementWorksOnceBeforeProtocolInitialization() {
        EnforcementPrefs(prefs).setInterval(5)
        deliverEnforcement(null)
        assertTrue(gray.isGrayscaleEnabled())
        assertTrue(context.enforcementAlarmState().isInitialized())
        gray.setGrayscale(false)
        deliverEnforcement(null)
        assertFalse(gray.isGrayscaleEnabled())
    }

    private fun deliverEnforcement(generation: String?) {
        val finished = CountDownLatch(1)
        val intent = Intent(context, EnforcementAlarmReceiver::class.java)
            .setAction(GrayoutService.ACTION_ENFORCEMENT_TICK)
        if (generation != null) intent.putExtra(EnforcementAlarmReceiver.EXTRA_GENERATION, generation)
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { finished.countDown() }
        }, Handler(Looper.getMainLooper()), 0, null, null)
        assertTrue("Enforcement receiver should finish", finished.await(10, TimeUnit.SECONDS))
    }

    private fun enforcementToken() = PendingIntent.getBroadcast(context, 2001,
        Intent(context, EnforcementAlarmReceiver::class.java).setAction(GrayoutService.ACTION_ENFORCEMENT_TICK),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)

    private suspend fun changeEmptyFixtureZoneAndDrain(zone: String) {
        if (TimeZone.getDefault().id == zone) return
        val start = LocalDateTime.now().plusDays(1)
        val marker = ArmedScheduleAlarm.create(com.princeyadav.grayout.logic.ScheduleEvent(
            start, true, -1, start, start.plusHours(1)))
        state.save(marker)
        alarms.setTimeZone(zone)
        await { TimeZone.getDefault().id == zone && state.read()?.generation != marker.generation }
        // The generation changes before registration. Acquiring the same mutex
        // drains the complete real receiver operation before fixture restoration.
        scheduleOperationMutex.withLock { }
    }

    private suspend fun seedWindow(startHours: Long, endHours: Long): Long {
        val now = LocalDateTime.now(ZoneId.of("Etc/UTC"))
        val start = now.plusHours(startHours)
        val end = now.plusHours(endHours)
        return dao.insert(Schedule(name = "System recovery", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute))
    }
    private fun scheduleToken() = PendingIntent.getBroadcast(context, 1001,
        Intent(context, ScheduleReceiver::class.java).setAction(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE),
        PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
    private fun cancel(receiver: Class<*>, code: Int, action: String) {
        PendingIntent.getBroadcast(context, code, Intent(context, receiver).setAction(action),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let { alarms.cancel(it); it.cancel() }
    }
    private fun await(condition: () -> Boolean) {
        val until = SystemClock.elapsedRealtime() + 10_000
        while (!condition() && SystemClock.elapsedRealtime() < until) SystemClock.sleep(25)
        assertTrue("Timed out waiting for system schedule reconciliation", condition())
    }
    private companion object {
        const val ENABLED = "accessibility_display_daltonizer_enabled"
        const val MODE = "accessibility_display_daltonizer"
    }
}

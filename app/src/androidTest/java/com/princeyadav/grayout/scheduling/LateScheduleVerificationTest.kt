package com.princeyadav.grayout.scheduling

import android.Manifest
import android.app.AlarmManager
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.data.ScheduleDao
import com.princeyadav.grayout.logic.ScheduleEvent
import com.princeyadav.grayout.logic.isCurrentlyFiring
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.Clock
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Test-only controlled late delivery through the real AlarmManager and receiver. */
@RunWith(AndroidJUnit4::class)
class LateScheduleVerificationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val prefs = context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private val grayscale = GrayscaleManager(context)
    private var savedSchedules = emptyList<Schedule>()
    private var savedPrefs: Map<String, *> = emptyMap<String, Any>()
    private var savedEnabled: String? = null
    private var savedMode: String? = null

    @Before
    fun setUp(): Unit = runBlocking {
        instrumentation.uiAutomation.adoptShellPermissionIdentity(Manifest.permission.WRITE_SECURE_SETTINGS)
        context.stopService(Intent(context, GrayoutService::class.java))
        instrumentation.waitForIdleSync()
        savedPrefs = prefs.all
        savedSchedules = dao.getAll()
        savedEnabled = Settings.Secure.getString(context.contentResolver, ENABLED)
        savedMode = Settings.Secure.getString(context.contentResolver, MODE)
        savedSchedules.forEach { dao.delete(it) }
        assertTrue(prefs.edit().clear().commit())
        Settings.Secure.putInt(context.contentResolver, ENABLED, 0)
        Settings.Secure.putInt(context.contentResolver, MODE, -1)
    }

    @After
    fun tearDown(): Unit = runBlocking {
        try {
            val intent = Intent(context, ScheduleReceiver::class.java)
                .setAction(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
            PendingIntent.getBroadcast(context, 1001, intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                context.getSystemService(AlarmManager::class.java).cancel(it)
                it.cancel()
            }
            dao.getAll().forEach { dao.delete(it) }
            savedSchedules.forEach { dao.insert(it) }
            Settings.Secure.putString(context.contentResolver, ENABLED, savedEnabled)
            Settings.Secure.putString(context.contentResolver, MODE, savedMode)
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
            assertTrue(editor.commit())
        } finally {
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    @Test
    fun expiredStartShouldLeaveDisplayInColor() = runBlocking {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val start = now.minusMinutes(30)
        val end = now.minusMinutes(15)
        val schedule = insertWindow(start, end)
        assertFalse(isCurrentlyFiring(schedule, LocalDateTime.now()))
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, EnforcementPrefs(prefs).getInterval())

        // Only the scheduling clock is controlled. Android delivers the due alarm
        // now; the unmodified receiver and its reschedule use the real current time.
        ScheduleAlarmManager(context, fixedClock(start.minusSeconds(1)))
            .reschedule(ScheduleRepository(dao))
        val nextStart = start.plusDays(1)
        awaitScheduledAlarm(nextStart)
        Log.i(TAG, "EXPIRED: window=$start..$end deliveredAt=${LocalDateTime.now()} " +
            "gray=${grayscale.isGrayscaleEnabled()} nextAlarm=$nextStart interval=0")

        assertFalse(
            "A start delivered after its window ended must not enable grayscale",
            grayscale.isGrayscaleEnabled(),
        )
    }

    @Test
    fun startDeliveredInsideWindowEnablesGrayscale() = runBlocking {
        val now = LocalDateTime.now().withSecond(0).withNano(0)
        val start = now.minusMinutes(15)
        val end = now.plusMinutes(15)
        val schedule = insertWindow(start, end)
        assertTrue(isCurrentlyFiring(schedule, LocalDateTime.now()))
        assertFalse(grayscale.isGrayscaleEnabled())

        ScheduleAlarmManager(context, fixedClock(start.minusSeconds(1)))
            .reschedule(ScheduleRepository(dao))
        awaitScheduledAlarm(end)
        Log.i(TAG, "CONTROL: window=$start..$end deliveredAt=${LocalDateTime.now()} " +
            "gray=${grayscale.isGrayscaleEnabled()} nextAlarm=$end interval=0")
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun expiredStartPreservesManualGrayscale() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(30)
        val end = now.minusMinutes(15)
        val schedule = insertWindow(start, end)
        val alarm = register(schedule, start, end, true)
        assertTrue(grayscale.setGrayscale(true))

        fire(alarm)

        assertTrue(grayscale.isGrayscaleEnabled())
        assertTrue(checkNotNull(ScheduleAlarmState(prefs).read()).isStart)
    }

    @Test
    fun expiredStartInsideLaterWindowDoesNotUndoManualColor() = runBlocking {
        val now = minuteNow()
        val aStart = now.minusMinutes(45)
        val aEnd = now.minusMinutes(30)
        val earlier = insertWindow(aStart, aEnd)
        insertWindow(now.minusMinutes(15), now.plusMinutes(15))
        fire(register(earlier, aStart, aEnd, true))

        assertFalse("Ignored start must not reassert the later active window", grayscale.isGrayscaleEnabled())
        assertFalse(checkNotNull(ScheduleAlarmState(prefs).read()).isStart)
    }

    @Test
    fun expiredStartPreservesBothExclusionTargetsAndPendingWrites() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(30)
        val end = now.minusMinutes(15)
        val schedule = insertWindow(start, end)
        val exclusions = ExclusionPrefs(prefs)
        exclusions.addExcludedPackage("test.excluded")
        for (target in listOf(false, true)) {
            exclusions.setWasGrayscaleOnBeforeExclusion(target)
            exclusions.setExcludedAppActive(true)
            exclusions.setColorRestorePending(true)
            fire(register(schedule, start, end, true))
            assertTrue(exclusions.isExcludedAppActive())
            assertEquals(target, exclusions.wasGrayscaleOnBeforeExclusion())
            assertTrue(exclusions.isColorRestorePending())
            assertFalse(grayscale.isGrayscaleEnabled())
        }
    }

    @Test
    fun validLateEndClosesItsWindowWithoutChangingEnforcementSetting() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(30)
        val end = now.minusMinutes(15)
        val schedule = insertWindow(start, end)
        assertTrue(grayscale.setGrayscale(true))
        fire(register(schedule, start, end, false))
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, EnforcementPrefs(prefs).getInterval())
    }

    @Test
    fun currentChainEndCatchesUpToANewerWindowWithoutTurningGrayOff() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(45)
        val end = now.minusMinutes(30)
        val earlier = insertWindow(start, end)
        insertWindow(now.minusMinutes(15), now.plusMinutes(15))
        assertTrue(grayscale.setGrayscale(true))
        fire(register(earlier, start, end, false))
        assertTrue(grayscale.isGrayscaleEnabled())
        assertFalse(checkNotNull(ScheduleAlarmState(prefs).read()).isStart)
    }

    @Test
    fun supersededEndCannotOverrideManualColorOrReplaceTheNewerAlarm() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(45)
        val end = now.minusMinutes(30)
        val earlier = insertWindow(start, end)
        val old = register(earlier, start, end, false)
        insertWindow(now.minusMinutes(15), now.plusMinutes(15))
        ScheduleAlarmManager(context).reschedule(ScheduleRepository(dao))
        val current = checkNotNull(ScheduleAlarmState(prefs).read())
        assertTrue(grayscale.setGrayscale(false))

        fire(old)

        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(current, ScheduleAlarmState(prefs).read())
    }

    @Test
    fun duplicateAcceptedStartCannotUndoALaterManualToggle() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(15)
        val end = now.plusMinutes(15)
        val schedule = insertWindow(start, end)
        val old = register(schedule, start, end, true)
        fire(old)
        assertTrue(grayscale.isGrayscaleEnabled())
        val next = ScheduleAlarmState(prefs).read()
        assertTrue(grayscale.setGrayscale(false))
        fire(old)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(next, ScheduleAlarmState(prefs).read())
    }

    @Test
    fun disabledDeletedAndEditedOriginsCannotTurnOffManualGrayscale() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(30)
        val end = now.minusMinutes(15)
        val repository = ScheduleRepository(dao)
        for (mutation in listOf("disable", "delete", "edit")) {
            dao.getAll().forEach { dao.delete(it) }
            val schedule = insertWindow(start, end)
            val old = register(schedule, start, end, false)
            when (mutation) {
                "disable" -> repository.setEnabled(schedule.id, false)
                "delete" -> repository.delete(schedule)
                else -> repository.save(schedule.copy(endTimeMinute = (end.minute + 1) % 60))
            }
            assertTrue(grayscale.setGrayscale(true))
            fire(old)
            assertTrue("Invalid $mutation origin must preserve manual gray", grayscale.isGrayscaleEnabled())
        }
    }

    @Test
    fun preUpgradeEndStillRunsAndRegistrationUpgradesToGenerationMetadata() = runBlocking {
        val now = minuteNow()
        insertWindow(now.minusMinutes(30), now.minusMinutes(15))
        assertTrue(grayscale.setGrayscale(true))
        fire(null, legacyKind = false)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertNotNull(ScheduleAlarmState(prefs).read())
        assertTrue(grayscale.setGrayscale(true))
        // A duplicate legacy intent cannot act after a new registration exists.
        fire(null, legacyKind = false)
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun malformedLegacyDeliveryDoesNotSynchronizeAnActiveWindow() = runBlocking {
        val now = minuteNow()
        insertWindow(now.minusMinutes(15), now.plusMinutes(15))
        fire(null, legacyKind = null)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertNotNull(ScheduleAlarmState(prefs).read())
    }

    @Test
    fun clearingTheLastRegistrationDoesNotMakeOldLegacyEventsTrustedAgain() = runBlocking {
        ScheduleAlarmState(prefs).save(null)
        val now = minuteNow()
        insertWindow(now.minusMinutes(15), now.plusMinutes(15))
        fire(null, legacyKind = true)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertTrue(ScheduleAlarmState(prefs).isInitialized())
        assertNotNull(ScheduleAlarmState(prefs).read())
    }

    @Test
    fun delayedTouchingBoundaryClosesTheEarlierScheduleAfterBothWindowsExpire() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(45)
        val boundary = now.minusMinutes(15)
        val first = insertWindow(start, boundary)
        insertWindow(boundary, now.minusMinutes(5))
        val originalStart = register(first, start, boundary, true)

        // Apply A's valid start at its decision time. Its actual next alarm is
        // already due now, so Android delivers the touching boundary after B ended.
        ScheduleAlarmManager(context, fixedClock(start.plusMinutes(1)))
            .handleAlarm(ScheduleRepository(dao), originalStart.generation, true)
        awaitScheduledAlarm(start.plusDays(1))
        assertFalse("A's closing boundary must not be replaced by an expired B start", grayscale.isGrayscaleEnabled())
    }

    @Test
    fun touchingEndStartsTheNextWindowEvenWhenTheEarlierWindowWasManuallyColored() = runBlocking {
        val boundary = minuteNow()
        val start = boundary.minusMinutes(30)
        val first = insertWindow(start, boundary)
        insertWindow(boundary, boundary.plusMinutes(15))
        assertFalse(grayscale.isGrayscaleEnabled())
        fire(register(first, start, boundary, false))
        assertTrue(grayscale.isGrayscaleEnabled())
        assertEquals(boundary.plusMinutes(15), checkNotNull(ScheduleAlarmState(prefs).read()).event().dateTime)
    }

    @Test
    fun delayedOverlappingChainKeepsItsClosingBoundaryAfterBothWindowsExpire() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(45)
        val end = now.minusMinutes(15)
        val first = insertWindow(start, end)
        insertWindow(now.minusMinutes(30), now.minusMinutes(5))
        val originalStart = register(first, start, end, true)
        ScheduleAlarmManager(context, fixedClock(start.plusMinutes(1)))
            .handleAlarm(ScheduleRepository(dao), originalStart.generation, true)
        awaitScheduledAlarm(start.plusDays(1))
        assertFalse("An interior overlapping start must not displace the closing event", grayscale.isGrayscaleEnabled())
    }

    @Test
    fun aDueClosingAlarmStillClosesAfterTheDeviceZoneChanges() = runBlocking {
        val oldZone = ZoneId.of("UTC")
        val newZone = ZoneId.of("UTC+02:00")
        val date = LocalDateTime.now(oldZone).toLocalDate()
        val start = date.atTime(9, 0)
        val end = date.atTime(17, 0)
        val schedule = insertWindow(start, end)
        val alarm = ArmedScheduleAlarm.create(ScheduleEvent(end, false, schedule.id, start, end), oldZone)
        ScheduleAlarmState(prefs).save(alarm)
        assertTrue(grayscale.setGrayscale(true))

        val deliveredAt = end.atZone(oldZone).toInstant().plusSeconds(1)
        val handled = ScheduleAlarmManager(context, Clock.fixed(deliveredAt, newZone))
            .handleAlarm(ScheduleRepository(dao), alarm.generation, false)
        assertEquals(false, handled)
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun aZoneChangedStartStillRunsWhenItsOriginalOccurrenceRemainsCurrent() = runBlocking {
        val oldZone = ZoneId.of("UTC")
        val newZone = ZoneId.of("UTC+02:00")
        val date = LocalDateTime.now(oldZone).toLocalDate()
        val start = date.atTime(9, 0)
        val end = date.atTime(17, 0)
        val schedule = insertWindow(start, end)
        val alarm = ArmedScheduleAlarm.create(ScheduleEvent(start, true, schedule.id, start, end), oldZone)
        ScheduleAlarmState(prefs).save(alarm)
        val handled = ScheduleAlarmManager(context, Clock.fixed(start.atZone(oldZone).toInstant(), newZone))
            .handleAlarm(ScheduleRepository(dao), alarm.generation, true)
        assertEquals(true, handled)
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun startProcessingAcrossItsEndStillArmsThatClosingBoundary() = runBlocking {
        val now = minuteNow()
        val start = now.minusMinutes(15)
        val end = now.plusMinutes(1)
        val schedule = insertWindow(start, end)
        val alarm = register(schedule, start, end, true)
        var reads = 0
        val clock = object : Clock() {
            override fun getZone(): ZoneId = ZoneId.systemDefault()
            override fun withZone(zone: ZoneId): Clock = Clock.fixed(instant(), zone)
            override fun instant(): Instant =
                (if (reads++ == 0) end.minusNanos(1) else end.plusNanos(1)).atZone(zone).toInstant()
        }
        ScheduleAlarmManager(context, clock).handleAlarm(ScheduleRepository(dao), alarm.generation, true)
        val next = checkNotNull(ScheduleAlarmState(prefs).read())
        assertFalse("The just-crossed end must not be skipped for tomorrow's start", next.isStart)
        assertEquals(end, next.event().dateTime)
        awaitScheduledAlarm(end)
    }

    @Test
    fun obsoleteDeliveryRepairsAPersistedButNotYetRegisteredNewAlarmWithoutDisplayChanges() = runBlocking {
        val now = minuteNow()
        val oldStart = now.minusMinutes(30)
        val oldEnd = now.minusMinutes(15)
        val oldSchedule = insertWindow(oldStart, oldEnd)
        val old = register(oldSchedule, oldStart, oldEnd, true)
        val futureStart = now.plusMinutes(30)
        val futureEnd = now.plusMinutes(45)
        val future = insertWindow(futureStart, futureEnd)
        val saved = register(future, futureStart, futureEnd, true)
        // No OS alarm was installed for this saved descriptor, modeling process
        // death immediately after the commit and before AlarmManager registration.
        fire(old)
        assertEquals(saved, ScheduleAlarmState(prefs).read())
        assertFalse(grayscale.isGrayscaleEnabled())
        awaitScheduledAlarm(futureStart)
    }

    @Test
    fun concurrentMutationAndRescheduleCannotInstallAnOlderSnapshotLast() = runBlocking {
        val now = minuteNow()
        val initial = insertWindow(now.plusMinutes(30), now.plusMinutes(45))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val mutationReachedDao = AtomicBoolean(false)
        val slowDao = object : ScheduleDao by dao {
            override suspend fun getEnabledSchedules(): List<Schedule> {
                val snapshot = dao.getEnabledSchedules()
                entered.complete(Unit)
                release.await()
                return snapshot
            }
        }
        val mutationDao = object : ScheduleDao by dao {
            override suspend fun update(schedule: Schedule) {
                mutationReachedDao.set(true)
                dao.update(schedule)
            }
        }
        val updatedStart = now.plusMinutes(60)
        val updatedEnd = now.plusMinutes(75)
        val updated = initial.copy(startTimeHour = updatedStart.hour, startTimeMinute = updatedStart.minute,
            endTimeHour = updatedEnd.hour, endTimeMinute = updatedEnd.minute)
        coroutineScope {
            val first = async(Dispatchers.IO) { ScheduleAlarmManager(context).reschedule(ScheduleRepository(slowDao)) }
            entered.await()
            val second = async(start = CoroutineStart.UNDISPATCHED) {
                val repository = ScheduleRepository(mutationDao)
                repository.save(updated)
                ScheduleAlarmManager(context).reschedule(repository)
            }
            assertFalse("Mutation must wait until the old snapshot finishes applying", mutationReachedDao.get())
            release.complete(Unit)
            first.await()
            second.await()
        }
        val current = checkNotNull(ScheduleAlarmState(prefs).read()).event()
        assertEquals(updatedStart, current.windowStart)
        awaitScheduledAlarm(updatedStart)
    }

    private fun fixedClock(time: LocalDateTime): Clock =
        Clock.fixed(time.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault())

    private fun minuteNow() = LocalDateTime.now().withSecond(0).withNano(0)

    private fun register(schedule: Schedule, start: LocalDateTime, end: LocalDateTime, isStart: Boolean): ArmedScheduleAlarm {
        val alarm = ArmedScheduleAlarm.create(ScheduleEvent(if (isStart) start else end, isStart, schedule.id, start, end))
        ScheduleAlarmState(prefs).save(alarm)
        return alarm
    }

    private fun fire(alarm: ArmedScheduleAlarm?, legacyKind: Boolean? = null) {
        val intent = Intent(context, ScheduleReceiver::class.java).setAction(ScheduleAlarmManager.ACTION_SCHEDULE_FIRE)
        if (alarm != null) {
            intent.putExtra(ScheduleAlarmManager.EXTRA_GENERATION, alarm.generation)
                .putExtra(ScheduleAlarmManager.EXTRA_IS_START, alarm.isStart)
        } else if (legacyKind != null) intent.putExtra(ScheduleAlarmManager.EXTRA_IS_START, legacyKind)
        val completed = CountDownLatch(1)
        context.sendOrderedBroadcast(intent, null, object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { completed.countDown() }
        }, Handler(Looper.getMainLooper()), Activity.RESULT_OK, null, null)
        assertTrue("Receiver must complete", completed.await(15, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private suspend fun insertWindow(start: LocalDateTime, end: LocalDateTime): Schedule {
        val schedule = Schedule(
            name = "Late alarm verification", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute,
        )
        return schedule.copy(id = dao.insert(schedule))
    }

    private fun awaitScheduledAlarm(expected: LocalDateTime) {
        val expectedEpoch = expected.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // Allow-while-idle alarms can be throttled even in this controlled test.
        val deadline = SystemClock.uptimeMillis() + 90_000L
        var observed: Long? = null
        while (SystemClock.uptimeMillis() < deadline) {
            val dump = ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys alarm")
            ).bufferedReader().use { it.readText() }
            val pattern = Regex(
                "(?m)^\\s*RTC_WAKEUP #\\d+: Alarm\\{[^\\n]*origWhen (\\d+) " +
                    "whenElapsed \\d+ com\\.princeyadav\\.grayout\\}\\n" +
                    "\\s+tag=\\*walarm\\*:com\\.princeyadav\\.grayout\\.SCHEDULE_FIRE$"
            )
            observed = pattern.find(dump)?.groupValues?.get(1)?.toLong()
            if (observed == expectedEpoch) return
            SystemClock.sleep(50)
        }
        assertEquals("Receiver must finish and arm the next expected boundary", expectedEpoch, observed)
    }

    companion object {
        private const val TAG = "LateScheduleVerification"
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
    }
}

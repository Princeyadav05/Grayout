package com.princeyadav.grayout.viewmodel

import app.cash.turbine.test
import com.princeyadav.grayout.data.ScheduleDao
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var grayscale: FakeGrayscaleController
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var exclusionPrefs: ExclusionPrefs
    private val serviceRunning = MutableStateFlow(false)
    private val intervalCommands = mutableListOf<Int>()
    private var restoreSettingChanges = 0
    private var iconLoads = 0

    private fun vm(
        canWrite: Boolean = true,
        serviceRunning: Boolean = false,
        clock: () -> Clock = { Clock.systemDefaultZone() },
        is24HourFormat: () -> Boolean = { false },
        notificationsAllowed: Boolean = true,
    ): HomeViewModel {
        grayscale.canWrite = canWrite
        this.serviceRunning.value = serviceRunning
        return HomeViewModel(
            grayscaleManager = grayscale,
            enforcementPrefs = enforcementPrefs,
            exclusionPrefs = exclusionPrefs,
            loadExcludedIcons = { _ ->
                iconLoads++
                emptyList<android.graphics.Bitmap>() to 0
            },
            ioDispatcher = dispatcherRule.dispatcher,
            serviceRunning = this.serviceRunning,
            onEnforcementIntervalChanged = intervalCommands::add,
            clock = clock,
            is24HourFormat = is24HourFormat,
            notificationsAllowed = { notificationsAllowed },
            onRestoreSettingsChanged = { restoreSettingChanges++ },
        )
    }

    @Before
    fun setUp() {
        grayscale = FakeGrayscaleController()
        enforcementPrefs = EnforcementPrefs(FakeSharedPreferences())
        exclusionPrefs = ExclusionPrefs(FakeSharedPreferences())
    }

    @Test
    fun `toggleGrayscale flips state and writes to controller when ADB granted`() = runTest {
        val homeViewModel = vm(canWrite = true)
        advanceUntilIdle()
        assertFalse(homeViewModel.isGrayscaleOn.value)

        homeViewModel.toggleGrayscale()
        advanceUntilIdle()

        assertTrue(grayscale.grayscaleEnabled)
        assertTrue(grayscale.setGrayscaleCallCount >= 1)
        assertTrue(homeViewModel.isGrayscaleOn.value)
    }

    @Test
    fun `toggleGrayscale requests setup and leaves state unchanged when ADB not granted`() = runTest {
        val homeViewModel = vm(canWrite = false)
        advanceUntilIdle()

        homeViewModel.setupRequests.test {
            homeViewModel.toggleGrayscale()
            advanceUntilIdle()
            awaitItem() // asserts one emission
        }
        assertFalse(homeViewModel.isGrayscaleOn.value)
        assertEquals(null, homeViewModel.toggleFailedTarget.value)
    }

    @Test
    fun `failed write with permission granted shows an inline error instead of setup`() = runTest {
        val homeViewModel = vm(canWrite = true)
        grayscale.failWrites = true
        advanceUntilIdle()

        homeViewModel.setupRequests.test {
            homeViewModel.toggleGrayscale()
            advanceUntilIdle()
            expectNoEvents()
        }
        assertEquals(true, homeViewModel.toggleFailedTarget.value)
        assertFalse(homeViewModel.isGrayscaleOn.value)
    }

    @Test
    fun `error clears when something else reaches the failed target`() = runTest {
        val homeViewModel = vm(canWrite = true)
        grayscale.failWrites = true
        homeViewModel.toggleGrayscale()
        advanceUntilIdle()
        assertEquals(true, homeViewModel.toggleFailedTarget.value)

        grayscale.grayscaleEnabled = true // e.g. a schedule start or a restore tick
        homeViewModel.refreshGrayscaleStateFromSystem()

        assertEquals(null, homeViewModel.toggleFailedTarget.value)
        assertTrue(homeViewModel.isGrayscaleOn.value)
    }

    @Test
    fun `retry aims at the failed target, not the opposite of the current state`() = runTest {
        val homeViewModel = vm(canWrite = true)
        grayscale.failWrites = true
        homeViewModel.toggleGrayscale() // tried to turn ON
        advanceUntilIdle()

        grayscale.failWrites = false
        homeViewModel.retryFailedToggle()
        advanceUntilIdle()
        homeViewModel.retryFailedToggle() // no error left: a second tap does nothing
        advanceUntilIdle()

        assertTrue(grayscale.grayscaleEnabled)
        assertEquals(null, homeViewModel.toggleFailedTarget.value)
    }

    @Test
    fun `turnGrayscaleOn never turns grayscale off`() = runTest {
        val homeViewModel = vm(canWrite = true)
        grayscale.grayscaleEnabled = true
        homeViewModel.refreshGrayscaleStateFromSystem()

        homeViewModel.turnGrayscaleOn()
        advanceUntilIdle()

        assertTrue(grayscale.grayscaleEnabled)
    }

    @Test
    fun `successful retry clears the inline error`() = runTest {
        val homeViewModel = vm(canWrite = true)
        grayscale.failWrites = true
        homeViewModel.toggleGrayscale()
        advanceUntilIdle()
        assertEquals(true, homeViewModel.toggleFailedTarget.value)

        grayscale.failWrites = false
        homeViewModel.toggleGrayscale()
        advanceUntilIdle()

        assertEquals(null, homeViewModel.toggleFailedTarget.value)
        assertTrue(homeViewModel.isGrayscaleOn.value)
    }

    @Test
    fun `setEnforcementInterval zero writes zero without checking ADB`() = runTest {
        val homeViewModel = vm(canWrite = false)
        advanceUntilIdle()

        homeViewModel.setEnforcementInterval(0)
        advanceUntilIdle()

        assertEquals(0, enforcementPrefs.getInterval())
        assertEquals(0, homeViewModel.enforcementInterval.value)
        assertEquals(listOf(0), intervalCommands)
    }

    @Test
    fun `setEnforcementInterval five persists value when ADB granted`() = runTest {
        val homeViewModel = vm(canWrite = true)
        advanceUntilIdle()

        homeViewModel.setEnforcementInterval(5)
        advanceUntilIdle()

        assertEquals(5, enforcementPrefs.getInterval())
        assertEquals(5, homeViewModel.enforcementInterval.value)
        assertEquals(listOf(5), intervalCommands)
    }

    @Test
    fun `setEnforcementInterval five requests setup and does not persist when ADB not granted`() = runTest {
        val homeViewModel = vm(canWrite = false)
        advanceUntilIdle()

        homeViewModel.setupRequests.test {
            homeViewModel.setEnforcementInterval(5)
            advanceUntilIdle()
            awaitItem() // asserts one emission
        }
        assertEquals(0, enforcementPrefs.getInterval())
        assertEquals(0, homeViewModel.enforcementInterval.value)
        assertTrue(intervalCommands.isEmpty())
    }

    @Test
    fun `only during schedules persists and tells the service to re-check`() = runTest {
        val home = vm()
        assertFalse(home.onlyDuringSchedules.value)

        home.setOnlyDuringSchedules(true)

        assertTrue(home.onlyDuringSchedules.value)
        assertTrue(enforcementPrefs.isOnlyDuringSchedules())
        assertEquals(1, restoreSettingChanges)
        assertTrue(vm().onlyDuringSchedules.value) // A fresh screen reads the saved choice.
    }

    @Test
    fun `turning restore on asks for notifications once when they are off`() = runTest {
        val home = vm(notificationsAllowed = false)

        home.setEnforcementInterval(5)
        assertTrue(home.showNotificationPrompt.value)

        home.dismissNotificationPrompt()
        assertFalse(home.showNotificationPrompt.value)
        home.setEnforcementInterval(0)
        home.setEnforcementInterval(5)
        assertFalse(home.showNotificationPrompt.value) // Answered once, never again.
        assertTrue(enforcementPrefs.isNotificationPromptDone())
    }

    @Test
    fun `changing an active interval or having notifications on never prompts`() = runTest {
        enforcementPrefs.setInterval(5)
        val off = vm(notificationsAllowed = false)
        off.setEnforcementInterval(10)
        assertFalse(off.showNotificationPrompt.value)

        val on = vm(notificationsAllowed = true)
        on.setEnforcementInterval(0)
        on.setEnforcementInterval(5)
        assertFalse(on.showNotificationPrompt.value)
    }

    @Test
    fun `refreshSystemState picks up external changes without commanding the service`() = runTest {
        val homeViewModel = vm()
        enforcementPrefs.setInterval(15)
        grayscale.grayscaleEnabled = true

        homeViewModel.refreshSystemState()

        assertEquals(15, homeViewModel.enforcementInterval.value)
        assertTrue(homeViewModel.isGrayscaleOn.value)
        assertEquals(15, enforcementPrefs.getInterval())
        assertTrue(intervalCommands.isEmpty())
    }

    @Test
    fun `rapid interval choices are applied in user order`() = runTest {
        val homeViewModel = vm()

        homeViewModel.setEnforcementInterval(5)
        homeViewModel.setEnforcementInterval(0)
        advanceUntilIdle()

        assertEquals(0, enforcementPrefs.getInterval())
        assertEquals(0, homeViewModel.enforcementInterval.value)
        assertEquals(listOf(5, 0), intervalCommands)
    }

    @Test
    fun `home resume owns icon loading without an eager duplicate`() = runTest {
        val homeViewModel = vm()
        advanceUntilIdle()
        assertEquals(0, iconLoads)

        homeViewModel.refreshExcludedAppIcons()
        advanceUntilIdle()

        assertEquals(1, iconLoads)
    }

    @Test
    fun `schedule line is empty when no enabled schedules`() = runTest {
        val homeViewModel = vm()
        advanceUntilIdle()

        val repository = ScheduleRepository(FakeScheduleDao())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeViewModel.observeNextSchedule(repository)
        }
        runCurrent()

        assertEquals(null, homeViewModel.scheduleLine.value)
    }

    @Test
    fun `isServiceRunning reflects the injected flow at construction`() = runTest {
        val running = vm(serviceRunning = true)
        advanceUntilIdle()
        assertTrue(running.isServiceRunning.value)

        val stopped = vm(serviceRunning = false)
        advanceUntilIdle()
        assertFalse(stopped.isServiceRunning.value)
    }

    @Test
    fun `isServiceRunning follows the service flow live without a refresh call`() = runTest {
        val homeViewModel = vm(serviceRunning = true)
        advanceUntilIdle()
        assertTrue(homeViewModel.isServiceRunning.value)

        serviceRunning.value = false
        advanceUntilIdle()

        assertFalse(homeViewModel.isServiceRunning.value)
    }

    @Test
    fun `next start follows the device 24-hour setting on each refresh`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(startHour = 14, endHour = 15))
        var is24Hour = true
        val timeChanges = MutableSharedFlow<Unit>()
        val start = Instant.parse("2026-04-13T08:59:00Z")
        val home = vm(
            clock = { Clock.fixed(start.plusMillis(testScheduler.currentTime), ZoneOffset.UTC) },
            is24HourFormat = { is24Hour },
        )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(dao), timeChanges)
        }
        runCurrent()
        assertEquals("14:00", home.lineText())

        // Android broadcasts a time change when the 12/24-hour setting changes.
        is24Hour = false
        timeChanges.emit(Unit)
        runCurrent()
        assertEquals("2:00 PM", home.lineText())
    }

    @Test
    fun `visible Home advances passed starts without querying the database again`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(startHour = 9, endHour = 10))
        dao.insert(schedule(startHour = 14, endHour = 15))
        var querySubscriptions = 0
        val countingDao = object : ScheduleDao by dao {
            override fun getAllSchedules() = flow {
                querySubscriptions++
                dao.getAllSchedules().collect { emit(it) }
            }
            override suspend fun getEnabledSchedules(): List<Schedule> = error("No timer query allowed")
        }
        val start = Instant.parse("2026-04-13T08:59:00Z")
        val home = vm(clock = { Clock.fixed(start.plusMillis(testScheduler.currentTime), ZoneOffset.UTC) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(countingDao))
        }
        runCurrent()
        assertEquals("9:00 AM", home.lineText())

        advanceTimeBy(60_000) // 09:00, the first window is running.
        runCurrent()
        assertEquals("until 10:00 AM", home.lineText())
        advanceTimeBy(3_600_000) // 10:00, it ended.
        runCurrent()
        assertEquals("2:00 PM", home.lineText())
        advanceTimeBy(5 * 3_600_000) // 15:00, both passed: next Monday.
        runCurrent()
        assertEquals("Mon 9:00 AM", home.lineText())
        assertEquals(1, querySubscriptions)
    }

    @Test
    fun `Home data edits replace the timer and delete clears the line`() = runTest {
        val dao = FakeScheduleDao()
        val id = dao.insert(schedule(startHour = 9, endHour = 10))
        val laterId = dao.insert(schedule(startHour = 14, endHour = 15))
        val start = Instant.parse("2026-04-13T08:59:00Z")
        val home = vm(clock = { Clock.fixed(start.plusMillis(testScheduler.currentTime), ZoneOffset.UTC) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(dao))
        }
        runCurrent()
        dao.update(dao.getById(id)!!.copy(startTimeMinute = 30))
        runCurrent()
        assertEquals("9:30 AM", home.lineText())

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("9:30 AM", home.lineText())
        advanceTimeBy(30 * 60_000) // 09:30, the edited window is running.
        runCurrent()
        assertEquals("until 10:00 AM", home.lineText())

        dao.setEnabled(laterId, false)
        runCurrent()
        assertEquals("until 10:00 AM", home.lineText())
        dao.delete(dao.getById(id)!!)
        runCurrent()
        assertEquals(null, home.lineText())
    }

    @Test
    fun `Home clock and zone changes replace long pending timers without a query restart`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(startHour = 9, endHour = 10))
        dao.insert(schedule(startHour = 14, endHour = 15))
        var subscriptions = 0
        val countingDao = object : ScheduleDao by dao {
            override fun getAllSchedules() = flow {
                subscriptions++
                dao.getAllSchedules().collect { emit(it) }
            }
        }
        var base = Instant.parse("2026-04-13T08:59:00Z")
        var zone: ZoneId = ZoneOffset.UTC
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val home = vm(clock = { Clock.fixed(base.plusMillis(testScheduler.currentTime), zone) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(countingDao), changes)
        }
        runCurrent()
        assertEquals("9:00 AM", home.lineText())

        base = Instant.parse("2026-04-13T13:59:00Z")
        changes.emit(Unit)
        runCurrent()
        assertEquals("2:00 PM", home.lineText())
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("until 3:00 PM", home.lineText())

        base = Instant.parse("2026-04-13T08:00:00Z")
        changes.emit(Unit)
        runCurrent()
        assertEquals("9:00 AM", home.lineText())
        zone = ZoneOffset.ofHours(2)
        changes.emit(Unit)
        runCurrent()
        assertEquals("2:00 PM", home.lineText())
        zone = ZoneOffset.ofHours(-2)
        changes.emit(Unit)
        runCurrent()
        assertEquals("9:00 AM", home.lineText())
        assertEquals(1, subscriptions)
    }

    @Test
    fun `Home repeated local hour never revives an already fired start`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(days = "SUN", startHour = 1, startMinute = 30, endHour = 2))
        dao.insert(schedule(days = "SUN", startHour = 3, endHour = 4))
        val start = Instant.parse("2026-11-01T05:29:00Z")
        val zone = ZoneId.of("America/New_York")
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val home = vm(clock = { Clock.fixed(start.plusMillis(testScheduler.currentTime), zone) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(dao), changes)
        }
        runCurrent()
        assertEquals("1:30 AM", home.lineText())
        advanceTimeBy(60_000) // 01:30 EDT, running.
        runCurrent()
        assertEquals("until 2:00 AM", home.lineText())

        advanceTimeBy(45 * 60_000) // 01:15 on the second offset, still inside the window.
        changes.emit(Unit)
        runCurrent()
        assertEquals("until 2:00 AM", home.lineText())
        advanceTimeBy(45 * 60_000) // 02:00 EST, window over.
        runCurrent()
        assertEquals("3:00 AM", home.lineText())
    }

    @Test
    fun `Home spring gap preserves configured text and waits for the resolved alarm instant`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(days = "SUN", startHour = 2, startMinute = 30, endHour = 4))
        dao.insert(schedule(days = "SUN", startHour = 5, endHour = 6))
        val start = Instant.parse("2026-03-08T07:29:00Z")
        val zone = ZoneId.of("America/New_York")
        val home = vm(clock = { Clock.fixed(start.plusMillis(testScheduler.currentTime), zone) })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(ScheduleRepository(dao))
        }
        runCurrent()
        assertEquals("2:30 AM", home.lineText())
        advanceTimeBy(60_000) // 2:30 falls in the gap and resolves to 3:30 EDT.
        runCurrent()
        assertEquals("until 4:00 AM", home.lineText())
    }

    @Test
    fun `stopped Home releases its query and timer then resume reads current state`() = runTest {
        val dao = FakeScheduleDao()
        dao.insert(schedule(startHour = 9, endHour = 10))
        dao.insert(schedule(startHour = 14, endHour = 15))
        var activeQueries = 0
        var clockReads = 0
        val countingDao = object : ScheduleDao by dao {
            override fun getAllSchedules() = flow {
                activeQueries++
                try {
                    dao.getAllSchedules().collect { emit(it) }
                } finally {
                    activeQueries--
                }
            }
        }
        val start = Instant.parse("2026-04-13T08:59:00Z")
        val home = vm(clock = {
            clockReads++
            Clock.fixed(start.plusMillis(testScheduler.currentTime), ZoneOffset.UTC)
        })
        val repository = ScheduleRepository(countingDao)
        val changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        assertEquals(0, activeQueries)
        assertEquals(0, clockReads)
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(repository, changes)
        }
        runCurrent()
        assertEquals(1, activeQueries)
        observer.cancel()
        runCurrent()
        assertEquals(0, activeQueries)
        assertEquals(0, changes.subscriptionCount.value)
        val beforePause = clockReads

        advanceTimeBy(60_000)
        changes.emit(Unit)
        runCurrent()
        assertEquals(beforePause, clockReads)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            home.observeNextSchedule(repository, changes)
        }
        runCurrent()
        assertEquals("until 10:00 AM", home.lineText())
    }

    /** "until 10:00 AM" while running, "Mon 9:00 AM" for another day, "9:00 AM" later today. */
    private fun HomeViewModel.lineText(): String? = scheduleLine.value?.let {
        (if (it.isActive) "until " else "") + (it.dayPrefix?.let { day -> "$day " } ?: "") + it.time
    }

    private fun schedule(
        days: String = "MON",
        startHour: Int,
        startMinute: Int = 0,
        endHour: Int,
    ) = Schedule(
        name = "Focus",
        daysOfWeek = days,
        startTimeHour = startHour,
        startTimeMinute = startMinute,
        endTimeHour = endHour,
        endTimeMinute = 0,
    )

}

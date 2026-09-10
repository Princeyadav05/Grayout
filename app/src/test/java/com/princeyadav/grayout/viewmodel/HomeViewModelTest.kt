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
    private var batteryOptimized = true // true = exempt/granted
    private val serviceRunning = MutableStateFlow(false)
    private val intervalCommands = mutableListOf<Int>()
    private var iconLoads = 0

    private fun vm(
        canWrite: Boolean = true,
        usageAccessGranted: Boolean = true,
        isBatteryOptimized: Boolean = true,
        serviceRunning: Boolean = false,
        clock: () -> Clock = { Clock.systemDefaultZone() },
    ): HomeViewModel {
        grayscale.canWrite = canWrite
        batteryOptimized = isBatteryOptimized
        this.serviceRunning.value = serviceRunning
        return HomeViewModel(
            grayscaleManager = grayscale,
            enforcementPrefs = enforcementPrefs,
            exclusionPrefs = exclusionPrefs,
            isBatteryOptimized = { batteryOptimized },
            loadExcludedIcons = { _ ->
                iconLoads++
                emptyList<android.graphics.Bitmap>() to 0
            },
            ioDispatcher = dispatcherRule.dispatcher,
            usageAccessProbe = { usageAccessGranted },
            serviceRunning = this.serviceRunning,
            onEnforcementIntervalChanged = intervalCommands::add,
            clock = clock,
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
    fun `toggleGrayscale emits navigateToSetup and leaves state unchanged when ADB not granted`() = runTest {
        val homeViewModel = vm(canWrite = false)
        advanceUntilIdle()

        homeViewModel.navigateToSetup.test {
            homeViewModel.toggleGrayscale()
            advanceUntilIdle()
            awaitItem() // asserts one emission
        }
        assertFalse(homeViewModel.isGrayscaleOn.value)
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
    fun `setEnforcementInterval five emits navigateToSetup and does not persist when ADB not granted`() = runTest {
        val homeViewModel = vm(canWrite = false)
        advanceUntilIdle()

        homeViewModel.navigateToSetup.test {
            homeViewModel.setEnforcementInterval(5)
            advanceUntilIdle()
            awaitItem() // asserts one emission
        }
        assertEquals(0, enforcementPrefs.getInterval())
        assertEquals(0, homeViewModel.enforcementInterval.value)
        assertTrue(intervalCommands.isEmpty())
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
    fun `refreshAttentionCount returns 3 when ADB, usage access, and battery all missing`() = runTest {
        val homeViewModel = vm(
            canWrite = false,
            usageAccessGranted = false,
            isBatteryOptimized = false,
        )
        advanceUntilIdle()

        homeViewModel.refreshAttentionCount()
        advanceUntilIdle()

        assertEquals(3, homeViewModel.needsAttentionCount.value)
    }

    @Test
    fun `refreshAttentionCount returns 0 when all permissions granted`() = runTest {
        val homeViewModel = vm(
            canWrite = true,
            usageAccessGranted = true,
            isBatteryOptimized = true,
        )
        advanceUntilIdle()

        homeViewModel.refreshAttentionCount()
        advanceUntilIdle()

        assertEquals(0, homeViewModel.needsAttentionCount.value)
    }

    @Test
    fun `refreshAttentionCount returns 1 when only battery optimization is restricted`() = runTest {
        val homeViewModel = vm(
            canWrite = true,
            usageAccessGranted = true,
            isBatteryOptimized = false,
        )
        advanceUntilIdle()

        homeViewModel.refreshAttentionCount()
        advanceUntilIdle()

        assertEquals(1, homeViewModel.needsAttentionCount.value)
    }

    @Test
    fun `refreshAttentionCount returns 1 when only usage access missing`() = runTest {
        val homeViewModel = vm(
            canWrite = true,
            usageAccessGranted = false,
            isBatteryOptimized = true,
        )
        advanceUntilIdle()

        homeViewModel.refreshAttentionCount()
        advanceUntilIdle()

        assertEquals(1, homeViewModel.needsAttentionCount.value)
    }

    @Test
    fun `next schedule observer shows No active schedule when no enabled schedules`() = runTest {
        val homeViewModel = vm()
        advanceUntilIdle()

        val repository = ScheduleRepository(FakeScheduleDao())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            homeViewModel.observeNextSchedule(repository)
        }
        runCurrent()

        assertEquals("No active schedule", homeViewModel.nextScheduleText.value)
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
        assertEquals("9:00 AM", home.nextScheduleText.value)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("2:00 PM", home.nextScheduleText.value)
        advanceTimeBy(5 * 3_600_000)
        runCurrent()
        assertEquals("9:00 AM", home.nextScheduleText.value)
        assertEquals(1, querySubscriptions)
    }

    @Test
    fun `Home data edits replace the timer and disable or delete clears the text`() = runTest {
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
        assertEquals("9:30 AM", home.nextScheduleText.value)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("9:30 AM", home.nextScheduleText.value)
        advanceTimeBy(30 * 60_000)
        runCurrent()
        assertEquals("2:00 PM", home.nextScheduleText.value)

        dao.setEnabled(laterId, false)
        runCurrent()
        assertEquals("9:30 AM", home.nextScheduleText.value)
        dao.delete(dao.getById(id)!!)
        runCurrent()
        assertEquals("No active schedule", home.nextScheduleText.value)
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
        assertEquals("9:00 AM", home.nextScheduleText.value)

        base = Instant.parse("2026-04-13T13:59:00Z")
        changes.emit(Unit)
        runCurrent()
        assertEquals("2:00 PM", home.nextScheduleText.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("9:00 AM", home.nextScheduleText.value)

        base = Instant.parse("2026-04-13T08:00:00Z")
        changes.emit(Unit)
        runCurrent()
        assertEquals("9:00 AM", home.nextScheduleText.value)
        zone = ZoneOffset.ofHours(2)
        changes.emit(Unit)
        runCurrent()
        assertEquals("2:00 PM", home.nextScheduleText.value)
        zone = ZoneOffset.ofHours(-2)
        changes.emit(Unit)
        runCurrent()
        assertEquals("9:00 AM", home.nextScheduleText.value)
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
        assertEquals("1:30 AM", home.nextScheduleText.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("3:00 AM", home.nextScheduleText.value)

        advanceTimeBy(45 * 60_000) // 01:15 on the second offset.
        changes.emit(Unit)
        runCurrent()
        assertEquals("3:00 AM", home.nextScheduleText.value)
        advanceTimeBy(105 * 60_000)
        runCurrent()
        assertEquals("1:30 AM", home.nextScheduleText.value) // Next Sunday.
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
        assertEquals("2:30 AM", home.nextScheduleText.value)
        advanceTimeBy(60_000)
        runCurrent()
        assertEquals("5:00 AM", home.nextScheduleText.value)
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
        assertEquals("2:00 PM", home.nextScheduleText.value)
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

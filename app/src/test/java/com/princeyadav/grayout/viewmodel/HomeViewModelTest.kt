package com.princeyadav.grayout.viewmodel

import app.cash.turbine.test
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

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
    fun `refreshNextSchedule shows No active schedule when no enabled schedules`() = runTest {
        val homeViewModel = vm()
        advanceUntilIdle()

        val repository = ScheduleRepository(FakeScheduleDao())
        homeViewModel.refreshNextSchedule(repository)
        advanceUntilIdle()

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
}

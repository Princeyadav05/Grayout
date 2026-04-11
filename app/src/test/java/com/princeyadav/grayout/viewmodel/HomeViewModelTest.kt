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

    private fun vm(
        canWrite: Boolean = true,
        accessibilityEnabled: Boolean = true,
        isBatteryOptimized: Boolean = true,
    ): HomeViewModel {
        grayscale.canWrite = canWrite
        grayscale.accessibilityEnabled = accessibilityEnabled
        batteryOptimized = isBatteryOptimized
        return HomeViewModel(
            grayscaleManager = grayscale,
            enforcementPrefs = enforcementPrefs,
            exclusionPrefs = exclusionPrefs,
            isBatteryOptimized = { batteryOptimized },
            loadExcludedIcons = { _ -> emptyList<android.graphics.Bitmap>() to 0 },
            ioDispatcher = dispatcherRule.dispatcher,
            ownPackageName = "com.princeyadav.grayout",
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
    }

    @Test
    fun `setEnforcementInterval five persists value when ADB granted`() = runTest {
        val homeViewModel = vm(canWrite = true)
        advanceUntilIdle()

        homeViewModel.setEnforcementInterval(5)
        advanceUntilIdle()

        assertEquals(5, enforcementPrefs.getInterval())
        assertEquals(5, homeViewModel.enforcementInterval.value)
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
    }

    @Test
    fun `refreshAttentionCount returns 3 when ADB, accessibility, and battery all missing`() = runTest {
        val homeViewModel = vm(
            canWrite = false,
            accessibilityEnabled = false,
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
            accessibilityEnabled = true,
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
            accessibilityEnabled = true,
            isBatteryOptimized = false,
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
}

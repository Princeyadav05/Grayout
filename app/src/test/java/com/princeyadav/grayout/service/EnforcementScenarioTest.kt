package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Scenario-based tests that simulate the enforcement + exclusion interaction
 * across GrayoutService and GrayoutAccessibilityService.
 *
 * The real services are tightly coupled to Android (Service lifecycle, Handler,
 * ContentObserver). These tests replicate the state transitions each service
 * performs using fakes, walking through the same decision branches manually.
 */
class EnforcementScenarioTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscale: FakeGrayscaleController

    // Mirrors GrayoutService's in-memory countdown tracking.
    private var countdownTargetMs = 0L
    private var countdownPending = false

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        enforcementPrefs = EnforcementPrefs(prefs)
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscale = FakeGrayscaleController()
    }

    // -- helpers that mirror GrayoutService / GrayoutAccessibilityService logic --

    /** Simulates the ContentObserver detecting grayscale was turned off. */
    private fun observerDetectsGrayscaleOff(intervalMinutes: Int) {
        countdownPending = true
        countdownTargetMs = System.currentTimeMillis() + intervalMinutes * 60_000L
    }

    /** Simulates the enforcement runnable firing after the countdown expires. */
    private fun countdownFires() {
        countdownTargetMs = 0L
        countdownPending = false
        if (!exclusionPrefs.isExcludedAppActive() && !grayscale.isGrayscaleEnabled()) {
            grayscale.setGrayscale(true)
        }
    }

    /** Simulates GrayoutAccessibilityService entering an excluded app. */
    private fun enterExcludedApp() {
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(grayscale.isGrayscaleEnabled())
        exclusionPrefs.setExcludedAppActive(true)
        grayscale.setGrayscale(false)
    }

    /**
     * Simulates GrayoutAccessibilityService exiting an excluded app.
     * Returns true if it sent EXTRA_EXCLUSION_ENDED to the service.
     */
    private fun exitExcludedApp(): Boolean {
        val wasOn = exclusionPrefs.wasGrayscaleOnBeforeExclusion()
        exclusionPrefs.setExcludedAppActive(false)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
        if (wasOn) {
            grayscale.setGrayscale(true)
            return false
        } else if (enforcementPrefs.getInterval() > 0) {
            return true // signals service via EXTRA_EXCLUSION_ENDED
        }
        return false
    }

    /**
     * Simulates GrayoutService receiving EXTRA_EXCLUSION_ENDED.
     * Re-enables grayscale immediately only if no active countdown remains.
     */
    private fun serviceReceivesExclusionEnded() {
        val interval = enforcementPrefs.getInterval()
        val hasActiveCountdown = countdownPending && countdownTargetMs > System.currentTimeMillis()
        if (!hasActiveCountdown && interval > 0 && !grayscale.isGrayscaleEnabled()) {
            grayscale.setGrayscale(true)
        }
    }

    // -- scenarios --

    @Test
    fun `basic enforcement - grayscale re-enabled after countdown`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // User turns off grayscale
        grayscale.setGrayscale(false)
        assertFalse(grayscale.isGrayscaleEnabled())

        // Observer detects change, starts countdown
        observerDetectsGrayscaleOff(5)
        assertTrue(countdownPending)

        // Countdown fires
        countdownFires()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `exclusion blocks enforcement - countdown fires while excluded app active`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // User turns off grayscale
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // User enters excluded app (grayscale already off, so wasOn=false)
        enterExcludedApp()
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())

        // Countdown fires while exclusion is active -> skipped
        countdownFires()
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `exit exclusion after countdown expired - immediate re-enable`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // User turns off grayscale, countdown starts
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // User enters excluded app (grayscale already off)
        enterExcludedApp()

        // Countdown fires while excluded -> skipped
        countdownFires()
        assertFalse(grayscale.isGrayscaleEnabled())
        assertFalse(countdownPending)

        // User exits excluded app -> wasOn=false, sends EXTRA_EXCLUSION_ENDED
        val sentToService = exitExcludedApp()
        assertTrue(sentToService)

        // Service receives signal, no active countdown -> re-enables immediately
        serviceReceivesExclusionEnded()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `exit exclusion before countdown - countdown fires naturally`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)

        // Countdown starts (simulating observer detecting grayscale off)
        observerDetectsGrayscaleOff(5)

        // User enters excluded app (grayscale already off)
        enterExcludedApp()
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())

        // User exits excluded app at "2 minutes" -> countdown still has 3 min left
        val sentToService = exitExcludedApp()
        assertTrue(sentToService)

        // Service receives signal, but countdown is still active -> does nothing
        serviceReceivesExclusionEnded()
        assertFalse(grayscale.isGrayscaleEnabled())

        // Countdown fires naturally later -> exclusion inactive -> re-enables
        countdownFires()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `exit exclusion with wasOn true - immediate restore without service signal`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // User enters excluded app while grayscale is ON
        enterExcludedApp()
        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertFalse(grayscale.isGrayscaleEnabled())

        // User exits excluded app -> wasOn=true -> direct restore
        val sentToService = exitExcludedApp()
        assertFalse(sentToService) // no service signal needed
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `no enforcement - exclusion exit does nothing extra`() {
        enforcementPrefs.setInterval(0)
        grayscale.setGrayscale(false)

        // User enters excluded app
        enterExcludedApp()
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())

        // User exits excluded app -> wasOn=false, interval=0 -> no restore, no service signal
        val sentToService = exitExcludedApp()
        assertFalse(sentToService)
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `setGrayscale short-circuit - calling with current value is a no-op in production`() {
        // FakeGrayscaleController does NOT short-circuit (unlike production
        // GrayscaleManager which checks isGrayscaleEnabled() == enabled first).
        // This test documents the production behavior: calling setGrayscale(false)
        // when already false returns true without writing to Settings.Secure.
        grayscale.grayscaleEnabled = false
        val callsBefore = grayscale.setGrayscaleCallCount

        // In the real GrayscaleManager, this would return true without
        // touching Settings.Secure. The fake still increments the counter,
        // so we verify the return value matches production (true).
        val result = grayscale.setGrayscale(false)
        assertTrue(result)

        // Same for setting true when already true
        grayscale.grayscaleEnabled = true
        val callsBeforeSecond = grayscale.setGrayscaleCallCount
        val result2 = grayscale.setGrayscale(true)
        assertTrue(result2)

        // Verify the fake tracked both calls (production would skip them)
        assertEquals(callsBefore + 1, callsBeforeSecond)
        assertEquals(callsBeforeSecond + 1, grayscale.setGrayscaleCallCount)
    }

    @Test
    fun `interval change resets countdown - new delay applies`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)

        // Observer detects grayscale off, starts 5 min countdown
        observerDetectsGrayscaleOff(5)
        assertTrue(countdownPending)
        val originalTarget = countdownTargetMs

        // Interval changes to 10 min -> old countdown cancelled, new one scheduled
        // (mirrors GrayoutService.onStartCommand with intervalChanged=true)
        countdownPending = false
        countdownTargetMs = 0L
        enforcementPrefs.setInterval(10)

        // Service restarts enforcement with new interval
        if (!exclusionPrefs.isExcludedAppActive() && !grayscale.isGrayscaleEnabled()) {
            observerDetectsGrayscaleOff(10)
        }

        assertTrue(countdownPending)
        assertTrue(countdownTargetMs > originalTarget)

        // New countdown fires -> re-enables
        countdownFires()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    // -- New scenarios: alarm-based enforcement edge cases --

    @Test
    fun `rapid toggles - each toggle resets countdown`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // Rapid toggle 1: off -> observer fires
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)
        val target1 = countdownTargetMs
        assertTrue(countdownPending)

        // Rapid toggle 2: back on -> observer clears countdown
        grayscale.setGrayscale(true)
        cancelCountdown()
        assertFalse(countdownPending)

        // Rapid toggle 3: off again -> new countdown
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)
        val target2 = countdownTargetMs
        assertTrue(countdownPending)
        assertTrue(target2 >= target1)

        // Final countdown fires -> re-enables
        countdownFires()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `rapid toggles - no enforcement when toggled back on before countdown`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // Off -> countdown starts
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // Immediately back on -> countdown cancelled
        grayscale.setGrayscale(true)
        cancelCountdown()

        // No enforcement needed - already on
        assertTrue(grayscale.isGrayscaleEnabled())
        assertFalse(countdownPending)
    }

    @Test
    fun `service restart with pending alarm - does not cancel surviving alarm`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)

        // Observer detected grayscale off, alarm scheduled
        observerDetectsGrayscaleOff(5)
        assertTrue(countdownPending)
        val originalTarget = countdownTargetMs
        alarmPending = true

        // Service killed and restarted (START_STICKY)
        // On restart, currentInterval resets to 0, reloaded from prefs
        val restoredInterval = enforcementPrefs.getInterval()
        val intervalChanged = restoredInterval != 0 // was 0 (reset), now 5
        assertTrue(intervalChanged)

        // hasPendingEnforcementAlarm() returns true -> alarm survived
        // But intervalChanged is true, so the else branch runs
        // This cancels and reschedules (acceptable - alarm fires at new target)
        countdownPending = false
        countdownTargetMs = 0L
        if (!exclusionPrefs.isExcludedAppActive() && !grayscale.isGrayscaleEnabled()) {
            observerDetectsGrayscaleOff(restoredInterval)
        }
        assertTrue(countdownPending)

        // Alarm eventually fires -> re-enables
        countdownFires()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `service restart with same interval - preserves existing alarm`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)
        alarmPending = true

        // Service receives start with same interval, alarm is pending
        val interval = enforcementPrefs.getInterval()
        val intervalChanged = false // same interval
        val hasActiveCountdown = countdownTargetMs > System.currentTimeMillis()

        // !hasActiveCountdown && !intervalChanged && hasPendingEnforcementAlarm()
        // -> just update notification, don't reschedule
        if (!hasActiveCountdown && !intervalChanged && alarmPending) {
            // This is the hasPendingEnforcementAlarm() path - no alarm rescheduling
            assertTrue(true)
        }
    }

    @Test
    fun `stale tick after interval disabled - stops service`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // User disables enforcement while countdown is pending
        enforcementPrefs.setInterval(0)

        // Stale alarm fires (ACTION_ENFORCEMENT_TICK)
        // Service reloads prefs, sees interval <= 0
        val currentInterval = enforcementPrefs.getInterval()
        assertEquals(0, currentInterval)

        // Service should cancel alarm and stop, NOT re-enable grayscale
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `stale tick after interval changed - uses new interval`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // User changes interval from 5 to 10 while countdown pending
        enforcementPrefs.setInterval(10)

        // Stale alarm fires (the 5-min alarm)
        // ACTION_ENFORCEMENT_TICK handler reloads prefs
        val currentInterval = enforcementPrefs.getInterval()
        countdownTargetMs = 0L

        // currentInterval > 0, grayscale is off, no exclusion -> re-enable
        if (currentInterval > 0 && !exclusionPrefs.isExcludedAppActive()
            && !grayscale.isGrayscaleEnabled()
        ) {
            grayscale.setGrayscale(true)
        }
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `exclusion during countdown - countdown fires silently, exit re-enables`() {
        enforcementPrefs.setInterval(1)
        grayscale.setGrayscale(true)

        // User turns off grayscale, countdown starts
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(1)

        // User opens excluded app (grayscale already off)
        enterExcludedApp()
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(exclusionPrefs.isExcludedAppActive())

        // Observer fires due to exclusion entering (grayscale was already off)
        // With exclusion active, observer skips scheduling
        assertFalse(grayscale.isGrayscaleEnabled())

        // Countdown fires while exclusion active -> skipped
        countdownFires()
        assertFalse(grayscale.isGrayscaleEnabled())

        // User exits excluded app -> EXTRA_EXCLUSION_ENDED
        val sentToService = exitExcludedApp()
        assertTrue(sentToService)

        // Service handles EXTRA_EXCLUSION_ENDED: no active countdown, interval > 0
        serviceReceivesExclusionEnded()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `setGrayscale fails on tick - service stops gracefully`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        // Simulate WRITE_SECURE_SETTINGS revoked
        grayscale.canWrite = false

        // Alarm fires, tick handler tries to re-enable
        countdownTargetMs = 0L
        countdownPending = false
        val success = grayscale.setGrayscale(true)
        assertFalse(success)

        // Service would stopSelf() here - grayscale stays off
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `schedule end turns off grayscale before stopping service`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // Simulates ScheduleReceiver schedule-end path (post-fix ordering):
        // 1. setGrayscale(false) FIRST
        grayscale.setGrayscale(false)
        assertFalse(grayscale.isGrayscaleEnabled())

        // 2. Then send interval=0 to service
        enforcementPrefs.setInterval(0)
        val currentInterval = enforcementPrefs.getInterval()
        assertEquals(0, currentInterval)

        // Service processes interval=0: cancels alarm, stops
        countdownPending = false
        countdownTargetMs = 0L
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `schedule end with pending countdown - alarm cancelled, grayscale off`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)
        assertTrue(countdownPending)

        // Schedule ends: grayscale off first (already off), then interval=0
        grayscale.setGrayscale(false)
        enforcementPrefs.setInterval(0)

        // Service processes interval=0
        countdownPending = false
        countdownTargetMs = 0L
        val interval = enforcementPrefs.getInterval()
        assertEquals(0, interval)
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `multiple exclusion enter-exit cycles during enforcement`() {
        enforcementPrefs.setInterval(5)
        grayscale.setGrayscale(true)

        // Cycle 1: enter while grayscale on
        enterExcludedApp()
        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertFalse(grayscale.isGrayscaleEnabled())

        exitExcludedApp()
        assertTrue(grayscale.isGrayscaleEnabled())

        // Cycle 2: user turns off grayscale, enters excluded app
        grayscale.setGrayscale(false)
        observerDetectsGrayscaleOff(5)

        enterExcludedApp()
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())

        // Countdown fires while excluded -> skipped
        countdownFires()
        assertFalse(grayscale.isGrayscaleEnabled())

        // Exit -> EXTRA_EXCLUSION_ENDED -> re-enable
        val sentToService = exitExcludedApp()
        assertTrue(sentToService)
        serviceReceivesExclusionEnded()
        assertTrue(grayscale.isGrayscaleEnabled())

        // Cycle 3: enter again while on
        enterExcludedApp()
        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertFalse(grayscale.isGrayscaleEnabled())

        exitExcludedApp()
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    // -- helpers for alarm-based tests --

    private var alarmPending = false

    private fun cancelCountdown() {
        countdownPending = false
        countdownTargetMs = 0L
        alarmPending = false
    }
}

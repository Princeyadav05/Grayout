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
}

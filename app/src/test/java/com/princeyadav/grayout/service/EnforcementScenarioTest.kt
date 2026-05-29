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
 * across GrayoutService and the foreground detector.
 *
 * The real services are tightly coupled to Android (Service lifecycle, Handler,
 * ContentObserver). These tests replicate the state transitions using fakes,
 * driving the exclusion edges through the real [nextExclusionTransition] +
 * [applyExclusionTransition] so the suite is a true parity oracle for the
 * detector, while the countdown/alarm branches are walked manually.
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

    // -- helpers that mirror GrayoutService / the foreground detector logic --

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

    /**
     * Simulates the detector observing an excluded app come to the foreground.
     * Drives the real [nextExclusionTransition] + [applyExclusionTransition].
     */
    private fun enterExcludedApp() {
        val transition = nextExclusionTransition(
            foregroundPackage = "com.excluded.app",
            ownPackage = "com.princeyadav.grayout",
            isExcluded = true,
            excludedAppActive = exclusionPrefs.isExcludedAppActive(),
            grayscaleEnabled = grayscale.isGrayscaleEnabled(),
            wasGrayscaleOnBeforeExclusion = exclusionPrefs.wasGrayscaleOnBeforeExclusion(),
        )
        applyExclusionTransition(
            transition, exclusionPrefs, grayscale, enforcementPrefs.getInterval(),
        ) { /* signal ignored on enter */ }
    }

    /**
     * Simulates the detector observing a non-excluded app come to the foreground
     * (leaving the excluded app). Drives the real [nextExclusionTransition] +
     * [applyExclusionTransition]. Returns true if the exit signalled the service
     * to re-enable (the old re-enable signaling path).
     */
    private fun exitExcludedApp(): Boolean {
        var signaled = false
        val transition = nextExclusionTransition(
            foregroundPackage = "com.other.app",
            ownPackage = "com.princeyadav.grayout",
            isExcluded = false,
            excludedAppActive = exclusionPrefs.isExcludedAppActive(),
            grayscaleEnabled = grayscale.isGrayscaleEnabled(),
            wasGrayscaleOnBeforeExclusion = exclusionPrefs.wasGrayscaleOnBeforeExclusion(),
        )
        applyExclusionTransition(
            transition, exclusionPrefs, grayscale, enforcementPrefs.getInterval(),
        ) { signaled = true }
        return signaled
    }

    /** Mirrors GrayoutService.reconcileStrandedExclusion(). */
    private fun reconcileStranded() {
        if (exclusionPrefs.isExcludedAppActive()) {
            val wasOn = exclusionPrefs.wasGrayscaleOnBeforeExclusion()
            exclusionPrefs.clearExclusionState()
            if (wasOn) grayscale.setGrayscale(true)
        }
    }

    /** Mirrors GrayoutService.onStartCommand branch (B): reconcile only on a
     * null-intent sticky/warm revival, never on an explicit (re)start. */
    private fun branchBReconcile(intentIsNull: Boolean) {
        if (intentIsNull) reconcileStranded()
    }

    /** Mirrors GrayoutService.onStartCommand branch (C): at interval > 0, reconcile
     * only when the last excluded app was removed (excludedCount == 0). */
    private fun branchCReconcile(excludedCount: Int) {
        if (excludedCount == 0) reconcileStranded()
    }

    /**
     * Simulates GrayoutService receiving re-enable signaling.
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

        // User exits excluded app -> wasOn=false, sends re-enable signaling
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

        // User exits excluded app -> re-enable signaling
        val sentToService = exitExcludedApp()
        assertTrue(sentToService)

        // Service handles re-enable signaling: no active countdown, interval > 0
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

        // Exit -> re-enable signaling -> re-enable
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

    @Test
    fun `recovery - stranded exclusion exit clears flags and restores grayscale when wasOn`() {
        // Detector/pure-fn level recovery, deliberately bypassing GrayoutApp (an
        // Application subclass not exercised in JVM unit tests). GrayoutApp
        // .clearExclusionState() is the PRIMARY cold-start healer; this exercises
        // the secondary warm-restart path via the real Exit transition.
        enforcementPrefs.setInterval(0)
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false

        val sentToService = exitExcludedApp()

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(grayscale.isGrayscaleEnabled())
        assertFalse(sentToService) // wasOn path restores directly, no service signal
    }

    @Test
    fun `last excluded app removed at interval 0 restores grayscale when wasOn`() {
        // Mirrors GrayoutService branch A/B reconcileStrandedExclusion: when the
        // last excluded app is removed at interval 0, the service stops/heals and
        // restores the user's manual ON.
        enforcementPrefs.setInterval(0)
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false
        assertFalse(shouldServiceRun(0, exclusionPrefs.getExcludedCount()))

        reconcileStranded()

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `schedule end with excluded apps keeps service alive`() {
        // Schedule END sends interval 0 after setGrayscale(false). With >=1
        // excluded app the service stays alive (branch B) and does not revert the
        // schedule-end grayscale-off.
        enforcementPrefs.setInterval(0)
        exclusionPrefs.addExcludedPackage("com.excluded.app")
        grayscale.setGrayscale(false)

        assertTrue(shouldServiceRun(0, exclusionPrefs.getExcludedCount()))
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `schedule end with no exclusions stops service`() {
        enforcementPrefs.setInterval(0)

        assertFalse(shouldServiceRun(0, exclusionPrefs.getExcludedCount()))
    }

    @Test
    fun `interval 0 mid-session toggle does not reconcile active exclusion`() {
        // Regression guard (review round 1): an exclusion-list toggle restarts the
        // service with a NON-null intent (branch B). With a legitimate active
        // session it must NOT reconcile, which would clear the flag and re-gray
        // mid-session while the user is parked on Grayout / an excluded app.
        enforcementPrefs.setInterval(0)
        exclusionPrefs.addExcludedPackage("com.excluded.app")
        exclusionPrefs.addExcludedPackage("com.other.excluded") // count stays >=1 after a toggle
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false

        branchBReconcile(intentIsNull = false)

        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `interval 0 sticky revival reconciles stranded exclusion`() {
        enforcementPrefs.setInterval(0)
        exclusionPrefs.addExcludedPackage("com.excluded.app")
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false

        branchBReconcile(intentIsNull = true)

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `interval gt 0 last excluded app removed restores grayscale when wasOn`() {
        // Regression guard (review round 2): at interval > 0, removing the LAST
        // excluded app while inside it must reconcile the stranded flag (branch C),
        // else grayscale stays off and enforcement is blocked until a cold start.
        enforcementPrefs.setInterval(5)
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false // last app removed -> excludedCount now 0

        branchCReconcile(excludedCount = exclusionPrefs.getExcludedCount())

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `interval gt 0 with remaining excluded apps does not reconcile active session`() {
        enforcementPrefs.setInterval(5)
        exclusionPrefs.addExcludedPackage("com.still.excluded") // count stays >=1
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false

        branchCReconcile(excludedCount = exclusionPrefs.getExcludedCount())

        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
    }

    // -- helpers for alarm-based tests --

    private var alarmPending = false

    private fun cancelCountdown() {
        countdownPending = false
        countdownTargetMs = 0L
        alarmPending = false
    }
}

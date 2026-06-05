package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeForegroundAppProvider
import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Direct [ForegroundAppDetector.tickOnce] coverage — the glue the pure-function
 * tests structurally cannot reach: [ForegroundAppDetector.lastKnownPkg]
 * carry-forward across empty reads, own-package handling, the no-op-when-zero-
 * exclusions guard, and read-grayscale-once threading. No Robolectric; the scope
 * is unused because we call [ForegroundAppDetector.tickOnce] synchronously.
 */
class ForegroundAppDetectorTest {

    private val own = "com.princeyadav.grayout"

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var grayscale: FakeGrayscaleController
    private lateinit var provider: FakeForegroundAppProvider
    private var endedCount = 0

    private fun detector(isScreenOn: () -> Boolean = { true }): ForegroundAppDetector =
        ForegroundAppDetector(
            provider = provider,
            exclusionPrefs = exclusionPrefs,
            enforcementPrefs = enforcementPrefs,
            grayscale = grayscale,
            ownPackage = own,
            onExclusionEnded = { endedCount++ },
            scope = CoroutineScope(Dispatchers.Unconfined),
            isScreenOn = isScreenOn,
        )

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        exclusionPrefs = ExclusionPrefs(prefs)
        enforcementPrefs = EnforcementPrefs(prefs)
        grayscale = FakeGrayscaleController()
        provider = FakeForegroundAppProvider()
        endedCount = 0
    }

    @Test
    fun `lastKnownPkg carry-forward across empty read does not flap`() {
        exclusionPrefs.addExcludedPackage("com.excluded")
        grayscale.grayscaleEnabled = false
        val detector = detector()

        // Enter the excluded app.
        provider.foregroundPackage = "com.excluded"
        detector.tickOnce()
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())

        // Empty read: must retain lastKnownPkg and NOT Exit.
        provider.foregroundPackage = null
        detector.tickOnce()
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertEquals(0, endedCount)

        // Genuine move to a non-excluded app: Exit now fires.
        provider.foregroundPackage = "com.other"
        detector.tickOnce()
        assertFalse(exclusionPrefs.isExcludedAppActive())
    }

    @Test
    fun `own package read does not clear active and does not Exit`() {
        exclusionPrefs.addExcludedPackage("com.excluded")
        // Simulate already inside an excluded app.
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
        grayscale.grayscaleEnabled = false
        val detector = detector()

        provider.foregroundPackage = own
        detector.tickOnce()

        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, endedCount)
    }

    @Test
    fun `tickOnce no-ops when no excluded apps configured`() {
        // Empty exclusion set.
        grayscale.grayscaleEnabled = true
        val detector = detector()

        provider.foregroundPackage = "com.anything"
        detector.tickOnce()

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertEquals(0, grayscale.setGrayscaleCallCount)
        assertEquals(0, endedCount)
        assertTrue(grayscale.isGrayscaleEnabled()) // untouched
    }

    @Test
    fun `tickOnce does not apply a transition when the screen is off`() {
        exclusionPrefs.addExcludedPackage("com.excluded")
        grayscale.grayscaleEnabled = true
        // Screen has gone off; an in-flight tick must not mutate the FSM, or it could
        // re-Enter and undo a SCREEN_OFF pre-gray (the detector/receiver race).
        val detector = detector(isScreenOn = { false })

        provider.foregroundPackage = "com.excluded" // would otherwise Enter
        detector.tickOnce()

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertEquals(0, grayscale.setGrayscaleCallCount)
        assertTrue(grayscale.isGrayscaleEnabled()) // untouched
    }

    @Test
    fun `Enter reads grayscale state exactly once and threads it`() {
        exclusionPrefs.addExcludedPackage("com.excluded")
        grayscale.grayscaleEnabled = true
        val detector = detector()

        provider.foregroundPackage = "com.excluded"
        detector.tickOnce()

        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
    }
}

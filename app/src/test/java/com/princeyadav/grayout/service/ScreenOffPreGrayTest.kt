package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage of the SCREEN_OFF pre-gray decision + applier. Mirrors
 * [ApplyExclusionTransitionTest]: real [ExclusionPrefs] on [FakeSharedPreferences]
 * + [FakeGrayscaleController], no Android Service.
 */
class ScreenOffPreGrayTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscale: FakeGrayscaleController

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscale = FakeGrayscaleController()
    }

    // --- shouldPreGrayOnScreenOff (pure predicate) ---

    @Test
    fun `pre-gray when in an exclusion session that was grayscale-on`() {
        assertTrue(shouldPreGrayOnScreenOff(excludedAppActive = true, wasGrayscaleOnBeforeExclusion = true))
    }

    @Test
    fun `no pre-gray when the pre-exclusion state was colour`() {
        assertFalse(shouldPreGrayOnScreenOff(excludedAppActive = true, wasGrayscaleOnBeforeExclusion = false))
    }

    @Test
    fun `no pre-gray when not in an exclusion session`() {
        assertFalse(shouldPreGrayOnScreenOff(excludedAppActive = false, wasGrayscaleOnBeforeExclusion = true))
        assertFalse(shouldPreGrayOnScreenOff(excludedAppActive = false, wasGrayscaleOnBeforeExclusion = false))
    }

    // --- preGrayOnScreenOff (applier) ---

    @Test
    fun `applier re-grays and clears the FSM on a was-on session`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false // suppressed inside the excluded app

        val didPreGray = preGrayOnScreenOff(exclusionPrefs, grayscale)

        assertTrue(didPreGray)
        assertTrue(grayscale.isGrayscaleEnabled())
        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `applier does not clear the FSM when the re-gray write fails`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false
        grayscale.canWrite = false // e.g. WRITE_SECURE_SETTINGS was revoked

        val didPreGray = preGrayOnScreenOff(exclusionPrefs, grayscale)

        assertFalse(didPreGray)
        assertFalse(grayscale.isGrayscaleEnabled())
        // State preserved so existing recovery paths can still heal it.
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `applier is a no-op when the pre-exclusion state was colour`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
        grayscale.grayscaleEnabled = false

        val didPreGray = preGrayOnScreenOff(exclusionPrefs, grayscale)

        assertFalse(didPreGray)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals0Writes()
        assertTrue(exclusionPrefs.isExcludedAppActive())
    }

    @Test
    fun `applier is a no-op when no exclusion session is active`() {
        grayscale.grayscaleEnabled = false

        val didPreGray = preGrayOnScreenOff(exclusionPrefs, grayscale)

        assertFalse(didPreGray)
        assertEquals0Writes()
    }

    private fun assertEquals0Writes() {
        org.junit.Assert.assertEquals(0, grayscale.setGrayscaleCallCount)
    }
}

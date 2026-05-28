package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the enforcement tick that runs when the countdown alarm fires.
 *
 * Unlike EnforcementScenarioTest (which simulates the service's branch logic by
 * hand), these call the real production function [applyEnforcementTick] with
 * fakes, so they exercise the exact code path EnforcementAlarmReceiver runs.
 */
class EnforcementTickTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscale: FakeGrayscaleController

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        enforcementPrefs = EnforcementPrefs(prefs)
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscale = FakeGrayscaleController()
    }

    @Test
    fun `re-enables grayscale when enforcement on, not excluded, grayscale off`() {
        enforcementPrefs.setInterval(5)
        grayscale.grayscaleEnabled = false

        val applied = applyEnforcementTick(enforcementPrefs, exclusionPrefs, grayscale)

        assertTrue(applied)
        assertTrue(grayscale.isGrayscaleEnabled())
    }

    @Test
    fun `does nothing when enforcement disabled`() {
        enforcementPrefs.setInterval(0)
        grayscale.grayscaleEnabled = false

        val applied = applyEnforcementTick(enforcementPrefs, exclusionPrefs, grayscale)

        assertFalse(applied)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, grayscale.setGrayscaleCallCount)
    }

    @Test
    fun `does not re-enable while an excluded app is active`() {
        enforcementPrefs.setInterval(5)
        exclusionPrefs.setExcludedAppActive(true)
        grayscale.grayscaleEnabled = false

        val applied = applyEnforcementTick(enforcementPrefs, exclusionPrefs, grayscale)

        assertFalse(applied)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, grayscale.setGrayscaleCallCount)
    }

    @Test
    fun `does not rewrite when grayscale already on`() {
        enforcementPrefs.setInterval(5)
        grayscale.grayscaleEnabled = true

        val applied = applyEnforcementTick(enforcementPrefs, exclusionPrefs, grayscale)

        assertFalse(applied)
        assertTrue(grayscale.isGrayscaleEnabled())
        assertEquals(0, grayscale.setGrayscaleCallCount)
    }

    @Test
    fun `returns false when secure-settings write fails`() {
        enforcementPrefs.setInterval(5)
        grayscale.grayscaleEnabled = false
        grayscale.canWrite = false

        val applied = applyEnforcementTick(enforcementPrefs, exclusionPrefs, grayscale)

        assertFalse(applied)
        assertFalse(grayscale.isGrayscaleEnabled())
    }
}

package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Coverage of [reconcileStrandedExclusion], the healer shared by GrayoutService
 * (warm restart) and GrayoutApp (cold start). Mirrors [ScreenOffPreGrayTest]:
 * real [ExclusionPrefs] on [FakeSharedPreferences] + [FakeGrayscaleController].
 */
class StrandedExclusionReconcileTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscale: FakeGrayscaleController

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscale = FakeGrayscaleController()
    }

    @Test
    fun `restores grayscale and clears the FSM on a stranded was-on session`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false // suppressed inside the excluded app

        val restored = reconcileStrandedExclusion(exclusionPrefs, grayscale)

        assertTrue(restored)
        assertTrue(grayscale.isGrayscaleEnabled())
        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `clears the FSM without writing when the pre-exclusion state was colour`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
        grayscale.grayscaleEnabled = false

        val restored = reconcileStrandedExclusion(exclusionPrefs, grayscale)

        assertFalse(restored)
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, grayscale.setGrayscaleCallCount)
        assertFalse(exclusionPrefs.isExcludedAppActive())
    }

    @Test
    fun `preserves the FSM when the restore write fails`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false
        grayscale.canWrite = false // e.g. WRITE_SECURE_SETTINGS revoked at cold start

        val restored = reconcileStrandedExclusion(exclusionPrefs, grayscale)

        assertFalse(restored)
        assertFalse(grayscale.isGrayscaleEnabled())
        // Recovery evidence kept so a later heal (permission restored) can still act.
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `no-op when no exclusion session is active`() {
        grayscale.grayscaleEnabled = true

        val restored = reconcileStrandedExclusion(exclusionPrefs, grayscale)

        assertFalse(restored)
        assertEquals(0, grayscale.setGrayscaleCallCount)
    }
}

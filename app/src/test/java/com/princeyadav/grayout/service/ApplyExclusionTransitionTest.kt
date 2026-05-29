package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Direct coverage of [applyExclusionTransition] — the write-order-sensitive
 * side-effect applier. Mirrors [EnforcementTickTest]'s use of real prefs on
 * [FakeSharedPreferences] + [FakeGrayscaleController].
 */
class ApplyExclusionTransitionTest {

    private lateinit var prefs: FakeSharedPreferences
    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscale: FakeGrayscaleController
    private var endedCount = 0

    @Before
    fun setUp() {
        prefs = FakeSharedPreferences()
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscale = FakeGrayscaleController()
        endedCount = 0
    }

    @Test
    fun `Enter writes wasOn then active then grayscale off`() {
        grayscale.grayscaleEnabled = true

        applyExclusionTransition(
            transition = ExclusionTransition.Enter(wasGrayscaleOn = true),
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = 0,
            onExclusionEnded = { endedCount++ },
        )

        assertTrue(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, endedCount)
    }

    @Test
    fun `Exit with wasOn true restores grayscale and clears flags`() {
        exclusionPrefs.setExcludedAppActive(true)
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(true)
        grayscale.grayscaleEnabled = false

        applyExclusionTransition(
            transition = ExclusionTransition.Exit(wasGrayscaleOn = true),
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = 5,
            onExclusionEnded = { endedCount++ },
        )

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(exclusionPrefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(grayscale.isGrayscaleEnabled())
        assertEquals(0, endedCount)
    }

    @Test
    fun `Exit with wasOn false and interval positive calls onExclusionEnded`() {
        exclusionPrefs.setExcludedAppActive(true)
        grayscale.grayscaleEnabled = false
        val callsBefore = grayscale.setGrayscaleCallCount

        applyExclusionTransition(
            transition = ExclusionTransition.Exit(wasGrayscaleOn = false),
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = 5,
            onExclusionEnded = { endedCount++ },
        )

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(callsBefore, grayscale.setGrayscaleCallCount) // grayscale untouched
        assertEquals(1, endedCount)
    }

    @Test
    fun `Exit with wasOn false and interval zero does nothing extra`() {
        exclusionPrefs.setExcludedAppActive(true)
        grayscale.grayscaleEnabled = false

        applyExclusionTransition(
            transition = ExclusionTransition.Exit(wasGrayscaleOn = false),
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = 0,
            onExclusionEnded = { endedCount++ },
        )

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertFalse(grayscale.isGrayscaleEnabled())
        assertEquals(0, endedCount)
    }

    @Test
    fun `None does nothing`() {
        applyExclusionTransition(
            transition = ExclusionTransition.None,
            exclusionPrefs = exclusionPrefs,
            grayscale = grayscale,
            enforcementInterval = 5,
            onExclusionEnded = { endedCount++ },
        )

        assertFalse(exclusionPrefs.isExcludedAppActive())
        assertEquals(0, grayscale.setGrayscaleCallCount)
        assertEquals(0, endedCount)
    }
}

package com.princeyadav.grayout.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [nextExclusionTransition] and [shouldServiceRun] — the pure
 * core of the exclusion state machine, with no Android deps and no fakes.
 * Mirrors [ExclusionEndDecisionTest]'s style for [shouldReEnableOnExclusionEnd].
 */
class ExclusionTransitionTest {

    private val own = "com.princeyadav.grayout"

    @Test
    fun `null foreground package returns None`() {
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = null,
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = false,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `own package returns None even when excluded app active`() {
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = own,
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = true,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = true,
            ),
        )
    }

    @Test
    fun `own package returns None when no excluded app active`() {
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = own,
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = false,
                grayscaleEnabled = true,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `own package returns None even when a non-excluded app was the previous lastKnownPkg`() {
        // Finding major #1: the FSM is the single own->None mapper. Even with the
        // exclusion active (as if a stale non-excluded package had been carried),
        // an own-package read must NOT Exit (which would prematurely re-gray while
        // the user is on Grayout). The provider passes own through verbatim.
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = own,
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = true,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `excluded foreground and none active returns Enter with grayscale on`() {
        assertEquals(
            ExclusionTransition.Enter(wasGrayscaleOn = true),
            nextExclusionTransition(
                foregroundPackage = "com.excluded",
                ownPackage = own,
                isExcluded = true,
                excludedAppActive = false,
                grayscaleEnabled = true,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `excluded foreground and none active returns Enter with grayscale off`() {
        assertEquals(
            ExclusionTransition.Enter(wasGrayscaleOn = false),
            nextExclusionTransition(
                foregroundPackage = "com.excluded",
                ownPackage = own,
                isExcluded = true,
                excludedAppActive = false,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `excluded foreground and already active returns None`() {
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = "com.excluded",
                ownPackage = own,
                isExcluded = true,
                excludedAppActive = true,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `non-excluded foreground and excluded was active returns Exit with saved wasOn true`() {
        assertEquals(
            ExclusionTransition.Exit(wasGrayscaleOn = true),
            nextExclusionTransition(
                foregroundPackage = "com.other",
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = true,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = true,
            ),
        )
    }

    @Test
    fun `non-excluded foreground and excluded was active returns Exit with saved wasOn false`() {
        assertEquals(
            ExclusionTransition.Exit(wasGrayscaleOn = false),
            nextExclusionTransition(
                foregroundPackage = "com.other",
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = true,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `non-excluded foreground and none active returns None`() {
        assertEquals(
            ExclusionTransition.None,
            nextExclusionTransition(
                foregroundPackage = "com.other",
                ownPackage = own,
                isExcluded = false,
                excludedAppActive = false,
                grayscaleEnabled = false,
                wasGrayscaleOnBeforeExclusion = false,
            ),
        )
    }

    @Test
    fun `shouldServiceRun true when interval positive and no exclusions`() {
        assertTrue(shouldServiceRun(interval = 5, excludedCount = 0))
    }

    @Test
    fun `shouldServiceRun true when interval zero but exclusions exist`() {
        assertTrue(shouldServiceRun(interval = 0, excludedCount = 1))
    }

    @Test
    fun `shouldServiceRun false when interval zero and no exclusions`() {
        assertFalse(shouldServiceRun(interval = 0, excludedCount = 0))
    }
}

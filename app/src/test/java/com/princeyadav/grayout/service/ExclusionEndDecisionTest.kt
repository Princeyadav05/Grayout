package com.princeyadav.grayout.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [shouldReEnableOnExclusionEnd], the decision GrayoutService makes
 * when an excluded app closes: re-apply grayscale immediately, or let an
 * already-pending countdown handle it.
 */
class ExclusionEndDecisionTest {

    @Test
    fun `re-enables when nothing pending, enforcement on, grayscale off`() {
        assertTrue(
            shouldReEnableOnExclusionEnd(
                interval = 5, reEnablePending = false, grayscaleEnabled = false,
            )
        )
    }

    @Test
    fun `does not re-enable when a re-enable is already pending`() {
        // Guards the restart bug: a surviving alarm (reEnablePending=true) means
        // the countdown will re-apply grayscale at its scheduled time, so we must
        // not snap it on early just because the in-memory countdown was lost.
        assertFalse(
            shouldReEnableOnExclusionEnd(
                interval = 5, reEnablePending = true, grayscaleEnabled = false,
            )
        )
    }

    @Test
    fun `does not re-enable when enforcement disabled`() {
        assertFalse(
            shouldReEnableOnExclusionEnd(
                interval = 0, reEnablePending = false, grayscaleEnabled = false,
            )
        )
    }

    @Test
    fun `does not re-enable when grayscale already on`() {
        assertFalse(
            shouldReEnableOnExclusionEnd(
                interval = 5, reEnablePending = false, grayscaleEnabled = true,
            )
        )
    }
}

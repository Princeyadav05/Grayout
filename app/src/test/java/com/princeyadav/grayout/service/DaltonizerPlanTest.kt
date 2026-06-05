package com.princeyadav.grayout.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-function coverage of the daltonizer decision logic. Mirrors
 * [ApplyExclusionTransitionTest] / the [nextExclusionTransition] tests: no Android
 * deps, no Settings.Secure. The Settings.Secure round-trip is covered separately
 * by GrayscaleManagerInstrumentationTest.
 */
class DaltonizerPlanTest {

    // --- isGrayscaleOn ---

    @Test
    fun `isGrayscaleOn true only for enabled monochromacy`() {
        assertTrue(isGrayscaleOn(DaltonizerState(enabled = 1, mode = 0)))
    }

    @Test
    fun `isGrayscaleOn false when disabled`() {
        assertFalse(isGrayscaleOn(DaltonizerState(enabled = 0, mode = -1)))
    }

    @Test
    fun `isGrayscaleOn false for an enabled colour-correction mode`() {
        // A colour-blind user's deuteranomaly correction: enabled, but NOT grayscale.
        assertFalse(isGrayscaleOn(DaltonizerState(enabled = 1, mode = 12)))
    }

    @Test
    fun `isGrayscaleOn false when mode 0 but disabled`() {
        assertFalse(isGrayscaleOn(DaltonizerState(enabled = 0, mode = 0)))
    }

    // --- daltonizerPlan: enable ---

    @Test
    fun `enable from off captures off baseline and writes grayscale on`() {
        val plan = daltonizerPlan(
            enable = true,
            current = DaltonizerState(0, -1),
            baseline = null,
        )
        assertEquals(
            DaltonizerPlan(
                write = DaltonizerState(1, 0),
                captureBaseline = DaltonizerState(0, -1),
            ),
            plan,
        )
    }

    @Test
    fun `enable while colour correction active captures the correction as baseline`() {
        val plan = daltonizerPlan(
            enable = true,
            current = DaltonizerState(1, 12),
            baseline = null,
        )
        assertEquals(
            DaltonizerPlan(
                write = DaltonizerState(1, 0),
                captureBaseline = DaltonizerState(1, 12),
            ),
            plan,
        )
    }

    @Test
    fun `enable while already grayscale on does not recapture baseline`() {
        val plan = daltonizerPlan(
            enable = true,
            current = DaltonizerState(1, 0),
            baseline = DaltonizerState(1, 12),
        )
        assertEquals(
            DaltonizerPlan(write = DaltonizerState(1, 0), captureBaseline = null),
            plan,
        )
    }

    @Test
    fun `enable from off re-snapshots a stale baseline`() {
        // User previously had a correction captured, then turned it off; enabling now
        // should snapshot their genuine current (off) state, not keep the stale one.
        val plan = daltonizerPlan(
            enable = true,
            current = DaltonizerState(0, -1),
            baseline = DaltonizerState(1, 12),
        )
        assertEquals(
            DaltonizerPlan(
                write = DaltonizerState(1, 0),
                captureBaseline = DaltonizerState(0, -1),
            ),
            plan,
        )
    }

    // --- daltonizerPlan: disable ---

    @Test
    fun `disable restores the captured correction baseline`() {
        val plan = daltonizerPlan(
            enable = false,
            current = DaltonizerState(1, 0),
            baseline = DaltonizerState(1, 12),
        )
        assertEquals(
            DaltonizerPlan(write = DaltonizerState(1, 12), captureBaseline = null),
            plan,
        )
    }

    @Test
    fun `disable with no baseline writes neutral off`() {
        val plan = daltonizerPlan(
            enable = false,
            current = DaltonizerState(1, 0),
            baseline = null,
        )
        assertEquals(
            DaltonizerPlan(write = DaltonizerState(0, -1), captureBaseline = null),
            plan,
        )
    }

    @Test
    fun `disable with a plain off baseline writes off`() {
        val plan = daltonizerPlan(
            enable = false,
            current = DaltonizerState(1, 0),
            baseline = DaltonizerState(0, -1),
        )
        assertEquals(
            DaltonizerPlan(write = DaltonizerState(0, -1), captureBaseline = null),
            plan,
        )
    }
}

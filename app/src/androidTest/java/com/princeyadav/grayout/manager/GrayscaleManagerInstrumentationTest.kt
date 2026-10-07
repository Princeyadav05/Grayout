package com.princeyadav.grayout.manager

import android.content.Context
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.ExclusionTransition
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.applyExclusionTransition
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for GrayscaleManager — proves the Settings.Secure
 * round-trip, the verified write, and that a colour-blind user's daltonizer
 * correction is preserved rather than wiped. Requires WRITE_SECURE_SETTINGS:
 *   adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
 *
 * The pure decision logic is covered on the JVM by DaltonizerPlanTest; these
 * tests only exercise the parts that genuinely touch Settings.Secure.
 */
@RunWith(AndroidJUnit4::class)
class GrayscaleManagerInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver = context.contentResolver
    private val manager = GrayscaleManager(context)

    private fun read(key: String, default: Int) =
        Settings.Secure.getInt(contentResolver, key, default)

    private fun writeDaltonizer(enabled: Int, mode: Int) {
        Settings.Secure.putInt(contentResolver, ENABLED, enabled)
        Settings.Secure.putInt(contentResolver, MODE, mode)
    }

    private fun clearBaseline() {
        context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_BASELINE_CAPTURED)
            .remove(KEY_BASELINE_ENABLED)
            .remove(KEY_BASELINE_MODE)
            .apply()
    }

    /** Start every test from a known off state with no captured baseline. */
    @Before
    fun reset() {
        clearBaseline()
        writeDaltonizer(0, -1)
    }

    @After
    fun tearDown() {
        clearBaseline()
        writeDaltonizer(0, -1)
    }

    @Test
    fun setGrayscale_true_writes_enabled_1_and_mode_0() {
        manager.setGrayscale(true)

        assertEquals(1, read(ENABLED, -1))
        assertEquals(0, read(MODE, -999))
    }

    @Test
    fun setGrayscale_false_restores_off_when_no_correction_baseline() {
        manager.setGrayscale(true)
        manager.setGrayscale(false)

        assertEquals(0, read(ENABLED, -1))
    }

    @Test
    fun isGrayscaleEnabled_roundtrips_state_written_by_setGrayscale() {
        manager.setGrayscale(true)
        assertTrue(manager.isGrayscaleEnabled())

        manager.setGrayscale(false)
        assertFalse(manager.isGrayscaleEnabled())
    }

    @Test
    fun setGrayscale_preserves_a_colorblind_correction_baseline() {
        // User runs deuteranomaly correction (enabled, mode 12) before Grayout ever ran.
        writeDaltonizer(1, 12)
        clearBaseline()

        manager.setGrayscale(true)
        // Grayscale on is monochromacy, not their correction.
        assertEquals(1, read(ENABLED, -1))
        assertEquals(0, read(MODE, -999))
        assertTrue(manager.isGrayscaleEnabled())

        manager.setGrayscale(false)
        // Their correction is restored, not wiped to a hardcoded off.
        assertEquals(1, read(ENABLED, -1))
        assertEquals(12, read(MODE, -999))
    }

    @Test
    fun isGrayscaleEnabled_false_for_color_correction_only() {
        // A non-monochromacy daltonizer mode is enabled, but that is NOT grayscale.
        writeDaltonizer(1, 12)

        assertFalse(manager.isGrayscaleEnabled())
    }

    @Test
    fun firstExclusionPreservesExistingCorrectionWhenGrayscaleWasNeverEnabled() {
        writeDaltonizer(1, 12)
        val exclusions = ExclusionPrefs(
            context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE),
        )
        try {
            applyExclusionTransition(ExclusionTransition.Enter(false), exclusions, manager, 0) {}

            assertTrue(exclusions.isExcludedAppActive())
            assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
            assertFalse(exclusions.isColorRestorePending())
            assertEquals(1, read(ENABLED, -1))
            assertEquals(12, read(MODE, -999))

            applyExclusionTransition(ExclusionTransition.Exit(false), exclusions, manager, 0) {}
            assertEquals(1, read(ENABLED, -1))
            assertEquals(12, read(MODE, -999))
        } finally {
            exclusions.clearExclusionState()
        }
    }

    @Test
    fun disablePreservesExternalCorrectionInsteadOfRestoringAStaleBaseline() {
        writeDaltonizer(1, 12)
        assertTrue(manager.setGrayscale(true))
        assertTrue(manager.setGrayscale(false))
        writeDaltonizer(1, 13)

        // A recreated manager must not interpret the persisted old baseline as
        // ownership of a newer correction selected outside Grayout.
        val restartedManager = GrayscaleManager(context)
        assertTrue(restartedManager.setGrayscale(false))
        assertEquals(1, read(ENABLED, -1))
        assertEquals(13, read(MODE, -999))

        assertTrue(restartedManager.setGrayscale(true))
        assertTrue(restartedManager.setGrayscale(false))
        assertEquals(1, read(ENABLED, -1))
        assertEquals(13, read(MODE, -999))
    }

    @Test
    fun disableFinishesPartialRestoreAfterEnabledFlagWasAlreadyWritten() {
        writeDaltonizer(0, 12)
        assertTrue(manager.setGrayscale(true))
        // Model process interruption or a rejected mode write after the first
        // half of restoring the disabled correction baseline succeeded.
        Settings.Secure.putInt(contentResolver, ENABLED, 0)
        assertFalse(manager.isGrayscaleEnabled())

        assertTrue(GrayscaleManager(context).setGrayscale(false))
        assertEquals(0, read(ENABLED, -1))
        assertEquals(12, read(MODE, -999))
    }

    @Test
    fun disablePreservesAnExternallyDisabledCorrection() {
        writeDaltonizer(1, 12)
        assertTrue(manager.setGrayscale(true))
        assertTrue(manager.setGrayscale(false))
        writeDaltonizer(0, 12)

        assertTrue(manager.setGrayscale(false))
        assertEquals(0, read(ENABLED, -1))
        assertEquals(12, read(MODE, -999))
    }

    @Test
    fun disableRetriesAfterEnabledFlagWritesWereSilentlyDropped() {
        writeDaltonizer(0, 12)
        var dropEnabledWrites = false
        val unreliableManager = GrayscaleManager(context) { resolver, key, value ->
            // Model a provider acknowledging writes without applying them.
            if (dropEnabledWrites && key == ENABLED) true
            else Settings.Secure.putInt(resolver, key, value)
        }
        assertTrue(unreliableManager.setGrayscale(true))
        dropEnabledWrites = true

        assertFalse(unreliableManager.setGrayscale(false))
        // Do not change mode while its enabled flag is still wrong: that would
        // make a failed restore look like a newer external color correction.
        assertEquals(1, read(ENABLED, -1))
        assertEquals(0, read(MODE, -999))

        assertTrue(GrayscaleManager(context).setGrayscale(false))
        assertEquals(0, read(ENABLED, -1))
        assertEquals(12, read(MODE, -999))
    }

    @Test
    fun failedDisableDoesNotGiveOwnershipOfANewerExternalCorrection() {
        writeDaltonizer(0, 12)
        assertTrue(manager.setGrayscale(true))
        val unreliableManager = GrayscaleManager(context) { resolver, key, value ->
            if (key == ENABLED) true else Settings.Secure.putInt(resolver, key, value)
        }
        assertFalse(unreliableManager.setGrayscale(false))

        writeDaltonizer(1, 13)
        assertTrue(GrayscaleManager(context).setGrayscale(false))
        assertEquals(1, read(ENABLED, -1))
        assertEquals(13, read(MODE, -999))
    }

    companion object {
        private const val ENABLED = "accessibility_display_daltonizer_enabled"
        private const val MODE = "accessibility_display_daltonizer"
        private const val KEY_BASELINE_CAPTURED = "daltonizer_baseline_captured"
        private const val KEY_BASELINE_ENABLED = "daltonizer_baseline_enabled"
        private const val KEY_BASELINE_MODE = "daltonizer_baseline_mode"
    }
}

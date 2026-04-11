package com.princeyadav.grayout.manager

import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.service.GrayscaleManager
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation tests for GrayscaleManager — proves Settings.Secure round-trip.
 * Requires WRITE_SECURE_SETTINGS granted via:
 *   adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS
 */
@RunWith(AndroidJUnit4::class)
class GrayscaleManagerInstrumentationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val contentResolver = context.contentResolver
    private val manager = GrayscaleManager(contentResolver)

    @After
    fun resetGrayscale() {
        // Always leave the device in a known-off state so tests are independent
        // and don't leave the screen visually gray after a test run.
        manager.setGrayscale(false)
    }

    @Test
    fun setGrayscale_true_writes_daltonizer_enabled_1_and_daltonizer_0() {
        manager.setGrayscale(true)

        val enabled = Settings.Secure.getInt(
            contentResolver,
            "accessibility_display_daltonizer_enabled",
            -1,
        )
        val mode = Settings.Secure.getInt(
            contentResolver,
            "accessibility_display_daltonizer",
            -999,
        )
        assertEquals(1, enabled)
        assertEquals(0, mode)
    }

    @Test
    fun setGrayscale_false_writes_daltonizer_enabled_0() {
        manager.setGrayscale(true)
        manager.setGrayscale(false)

        val enabled = Settings.Secure.getInt(
            contentResolver,
            "accessibility_display_daltonizer_enabled",
            -1,
        )
        assertEquals(0, enabled)
    }

    @Test
    fun isGrayscaleEnabled_roundtrips_state_written_by_setGrayscale() {
        manager.setGrayscale(true)
        assertTrue(manager.isGrayscaleEnabled())

        manager.setGrayscale(false)
        assertFalse(manager.isGrayscaleEnabled())
    }
}

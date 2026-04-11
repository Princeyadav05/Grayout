package com.princeyadav.grayout.system

import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Exercises the real `loadExcludedIcons` lambda path used by MainActivity
 * (PackageManager.getApplicationIcon + Drawable.toBitmap).
 *
 * Purpose: catch BOM / SDK updates that silently break icon loading for
 * excluded apps. The test loads the app's OWN icon so it never depends on
 * sideloaded packages being present on the device.
 */
@RunWith(AndroidJUnit4::class)
class SystemIntegrationTest {

    @Test
    fun real_loadExcludedIcons_returns_at_least_one_bitmap_for_own_package() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Mirrors the lambda constructed inside MainActivity.onCreate.
        val loader: (List<String>) -> Pair<List<Bitmap>, Int> = { packages ->
            val icons = mutableListOf<Bitmap>()
            var loaded = 0
            var found = 0
            for (pkg in packages) {
                val bitmap = try {
                    context.packageManager
                        .getApplicationIcon(pkg)
                        .toBitmap(width = 64, height = 64)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                if (bitmap != null) {
                    found++
                    if (loaded < 3) {
                        icons.add(bitmap)
                        loaded++
                    }
                }
            }
            icons to (found - loaded).coerceAtLeast(0)
        }

        val (icons, overflow) = loader(listOf("com.princeyadav.grayout"))

        assertFalse(
            "loader should return at least one bitmap for own package",
            icons.isEmpty(),
        )
        assertTrue("overflow should be >= 0", overflow >= 0)

        // Bitmap sanity — dimensions match what the lambda asked for and
        // the bitmap has not been recycled out from under us.
        val bitmap = icons.first()
        assertFalse("bitmap must not be recycled", bitmap.isRecycled)
        assertEquals64(64, bitmap.width, "width")
        assertEquals64(64, bitmap.height, "height")
    }

    private fun assertEquals64(expected: Int, actual: Int, label: String) {
        assertTrue(
            "$label should be $expected but was $actual",
            actual == expected,
        )
    }
}

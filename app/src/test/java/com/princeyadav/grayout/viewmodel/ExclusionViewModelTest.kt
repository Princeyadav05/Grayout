package com.princeyadav.grayout.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import app.cash.turbine.test
import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * JVM-safe stub of [ImageBitmap]. Compose's real `ImageBitmap(w, h)` factory
 * calls Android's native Bitmap pipeline which explodes in unit tests. Since
 * [AppInfo.icon] is a non-null [ImageBitmap] and the VM never reads it, this
 * stub lets tests construct AppInfo objects without the framework.
 */
private class StubImageBitmap(
    override val width: Int = 1,
    override val height: Int = 1,
) : ImageBitmap {
    override val colorSpace: ColorSpace = ColorSpaces.Srgb
    override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
    override val hasAlpha: Boolean = true

    override fun prepareToDraw() {
        // no-op
    }

    override fun readPixels(
        buffer: IntArray,
        startX: Int,
        startY: Int,
        width: Int,
        height: Int,
        bufferOffset: Int,
        stride: Int,
    ) {
        // no-op
    }
}

private fun fakeAppInfo(
    packageName: String,
    appName: String,
    isExcluded: Boolean = false,
): AppInfo = AppInfo(
    packageName = packageName,
    appName = appName,
    icon = StubImageBitmap(),
    isExcluded = isExcluded,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExclusionViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var grayscale: FakeGrayscaleController
    private lateinit var exclusionPrefs: ExclusionPrefs
    private var fakeApps: List<AppInfo> = emptyList()

    private fun vm(apps: List<AppInfo> = emptyList()): ExclusionViewModel {
        fakeApps = apps
        return ExclusionViewModel(
            exclusionPrefs = exclusionPrefs,
            grayscaleManager = grayscale,
            ownPackage = "com.princeyadav.grayout",
            loadApps = { fakeApps },
            ioDispatcher = dispatcherRule.dispatcher,
        )
    }

    @Before
    fun setUp() {
        grayscale = FakeGrayscaleController()
        exclusionPrefs = ExclusionPrefs(FakeSharedPreferences())
    }

    @Test
    fun `checkAccessibilityService sets isAccessibilityEnabled true when service enabled`() = runTest {
        grayscale.accessibilityEnabled = true
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.checkAccessibilityService()

        assertTrue(viewModel.isAccessibilityEnabled.value)
    }

    @Test
    fun `checkAccessibilityService sets isAccessibilityEnabled false when service disabled`() = runTest {
        grayscale.accessibilityEnabled = false
        val viewModel = vm()
        advanceUntilIdle()

        viewModel.checkAccessibilityService()

        assertFalse(viewModel.isAccessibilityEnabled.value)
    }

    @Test
    fun `toggleExclusion adds package to prefs when not currently excluded`() = runTest {
        val viewModel = vm(
            apps = listOf(fakeAppInfo("com.example.test", "Test", isExcluded = false)),
        )
        advanceUntilIdle()

        viewModel.toggleExclusion("com.example.test")

        assertTrue(exclusionPrefs.isExcluded("com.example.test"))
    }

    @Test
    fun `toggleExclusion removes package from prefs when currently excluded`() = runTest {
        exclusionPrefs.addExcludedPackage("com.example.test")
        val viewModel = vm(
            apps = listOf(fakeAppInfo("com.example.test", "Test", isExcluded = true)),
        )
        advanceUntilIdle()

        viewModel.toggleExclusion("com.example.test")

        assertFalse(exclusionPrefs.isExcluded("com.example.test"))
    }

    @Test
    fun `setSearchQuery filters apps case-insensitively`() = runTest {
        val viewModel = vm(
            apps = listOf(
                fakeAppInfo("a.pkg", "Apple"),
                fakeAppInfo("b.pkg", "Banana"),
                fakeAppInfo("c.pkg", "Cherry"),
            ),
        )
        advanceUntilIdle()

        viewModel.filteredApps.test {
            // WhileSubscribed stateIn replays initial empty value, then emits
            // the actual apps list once the upstream combine evaluates.
            skipItems(1)
            // Drain any pending emissions (initial full list) before applying filter.
            advanceUntilIdle()
            viewModel.setSearchQuery("APPLE")
            advanceUntilIdle()

            // The most recent item is the filtered result.
            var latest = expectMostRecentItem()
            // In case no new emission occurred yet, wait for one.
            if (latest.size != 1 || latest.first().appName != "Apple") {
                latest = awaitItem()
            }
            assertEquals(1, latest.size)
            assertEquals("Apple", latest.first().appName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}

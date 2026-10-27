package com.princeyadav.grayout.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import app.cash.turbine.test
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import com.princeyadav.grayout.model.AppCategory
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.testutil.MainDispatcherRule
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import kotlin.coroutines.CoroutineContext

/** Separates background completion from Main publication without real threads. */
private class QueuedAppLoaderDispatcher : CoroutineDispatcher() {
    private val tasks = ArrayDeque<Runnable>()

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        tasks.addLast(block)
    }

    fun completeLoad() {
        check(tasks.isNotEmpty()) { "No app load was queued" }
        tasks.removeFirst().run()
    }
}

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
    categories: Set<AppCategory> = emptySet(),
): AppInfo = AppInfo(
    packageName = packageName,
    appName = appName,
    icon = StubImageBitmap(),
    isExcluded = isExcluded,
    categories = categories,
)

/** Keeps WhileSubscribed flows collected so their `.value` stays current. */
private fun TestScope.keepCollecting(vararg flows: Flow<*>) {
    flows.forEach { flow -> backgroundScope.launch { flow.collect {} } }
}

private val camera = fakeAppInfo("cam.pkg", "Camera", categories = setOf(AppCategory.CAMERA))
private val gallery = fakeAppInfo("gal.pkg", "Gallery", categories = setOf(AppCategory.PHOTOS))
private val photos = fakeAppInfo("pho.pkg", "Google Photos", categories = setOf(AppCategory.PHOTOS))
private val chrome = fakeAppInfo("web.pkg", "Chrome")

@OptIn(ExperimentalCoroutinesApi::class)
class ExclusionViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var exclusionPrefs: ExclusionPrefs
    private var fakeApps: List<AppInfo> = emptyList()
    private var changedCount = 0

    private fun vm(
        apps: List<AppInfo> = emptyList(),
        usageGranted: Boolean = true,
    ): ExclusionViewModel {
        fakeApps = apps
        return ExclusionViewModel(
            exclusionPrefs = exclusionPrefs,
            usageAccessProbe = { usageGranted },
            onExclusionListChanged = { changedCount++ },
            loadApps = { fakeApps },
            ioDispatcher = dispatcherRule.dispatcher,
        )
    }

    @Before
    fun setUp() {
        exclusionPrefs = ExclusionPrefs(FakeSharedPreferences())
        changedCount = 0
    }

    @Test
    fun `checkUsageAccess sets isUsageAccessGranted true when granted`() = runTest {
        val viewModel = vm(usageGranted = true)
        advanceUntilIdle()

        viewModel.checkUsageAccess()

        assertTrue(viewModel.isUsageAccessGranted.value)
    }

    @Test
    fun `checkUsageAccess sets isUsageAccessGranted false when not granted`() = runTest {
        val viewModel = vm(usageGranted = false)
        advanceUntilIdle()

        viewModel.checkUsageAccess()

        assertFalse(viewModel.isUsageAccessGranted.value)
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
    fun `toggleExclusion invokes onExclusionListChanged`() = runTest {
        val viewModel = vm(
            apps = listOf(fakeAppInfo("com.example.test", "Test", isExcluded = false)),
        )
        advanceUntilIdle()

        viewModel.toggleExclusion("com.example.test") // add
        viewModel.toggleExclusion("com.example.test") // remove

        assertEquals(2, changedCount)
    }

    @Test
    fun `construction loads once, the first resume does not repeat it and later resumes reload`() = runTest {
        var loadCount = 0
        val loaderDispatcher = QueuedAppLoaderDispatcher()
        val viewModel = ExclusionViewModel(
            exclusionPrefs = exclusionPrefs,
            usageAccessProbe = { true },
            onExclusionListChanged = {},
            loadApps = { loadCount++; emptyList() },
            ioDispatcher = loaderDispatcher,
        )
        runCurrent()
        loaderDispatcher.completeLoad()
        runCurrent()
        assertEquals(1, loadCount)

        // The enter transition ends after a fast load has already finished.
        viewModel.onScreenResumed()
        runCurrent()
        assertEquals("The first resume must not repeat the construction load", 1, loadCount)

        // A later resume must reload packages to detect installs and removals,
        // and refreshes overlapping it are coalesced.
        viewModel.onScreenResumed()
        runCurrent()
        viewModel.refreshApps()
        viewModel.refreshApps()
        loaderDispatcher.completeLoad()
        runCurrent()
        assertEquals(2, loadCount)
    }

    @Test
    fun `cached apps show at once with current exclusions until the refresh replaces them`() = runTest {
        exclusionPrefs.addExcludedPackage(gallery.packageName)
        val loaderDispatcher = QueuedAppLoaderDispatcher()
        val viewModel = ExclusionViewModel(
            exclusionPrefs = exclusionPrefs,
            usageAccessProbe = { true },
            onExclusionListChanged = {},
            loadApps = { listOf(camera) },
            ioDispatcher = loaderDispatcher,
            initialApps = listOf(camera, gallery),
        )
        keepCollecting(viewModel.filteredApps)
        runCurrent()

        // The cache never stores exclusion flags; they come from the preferences.
        assertEquals(
            listOf("cam.pkg" to false, "gal.pkg" to true),
            viewModel.filteredApps.value.map { it.packageName to it.isExcluded },
        )

        loaderDispatcher.completeLoad()
        runCurrent()
        assertEquals(listOf("cam.pkg"), viewModel.filteredApps.value.map { it.packageName })
    }

    @Test
    fun `completed background load preserves exclusion toggled before publication`() = runTest {
        val loaderDispatcher = QueuedAppLoaderDispatcher()
        val app = fakeAppInfo("com.example.test", "Test")
        val viewModel = ExclusionViewModel(
            exclusionPrefs = exclusionPrefs,
            usageAccessProbe = { true },
            onExclusionListChanged = {},
            loadApps = { listOf(app) },
            ioDispatcher = loaderDispatcher,
        )

        viewModel.filteredApps.test {
            assertTrue(awaitItem().isEmpty())
            runCurrent()
            loaderDispatcher.completeLoad()

            // IO has captured isExcluded=false, but its result is still queued
            // for Main. The user changes the preference before it is published.
            viewModel.toggleExclusion(app.packageName)
            runCurrent()

            assertTrue(awaitItem().single().isExcluded)
            assertTrue(exclusionPrefs.isExcluded(app.packageName))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `refresh reapplies exclusions after a package is installed or removed`() = runTest {
        val first = fakeAppInfo("first.pkg", "First")
        val installed = fakeAppInfo("new.pkg", "New")
        val viewModel = vm(apps = listOf(first))

        viewModel.filteredApps.test {
            assertTrue(awaitItem().isEmpty())
            advanceUntilIdle()
            assertEquals(listOf(first.packageName), awaitItem().map { it.packageName })

            fakeApps = listOf(installed)
            exclusionPrefs.addExcludedPackage(installed.packageName)
            viewModel.refreshApps()
            advanceUntilIdle()

            val refreshed = awaitItem().single()
            assertEquals(installed.packageName, refreshed.packageName)
            assertTrue(refreshed.isExcluded)
            cancelAndIgnoreRemainingEvents()
        }
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

    @Test
    fun `categoryCounts lists only categories with apps, in chip order, ignoring search`() = runTest {
        val viewModel = vm(apps = listOf(chrome, photos, camera, gallery))
        keepCollecting(viewModel.categoryCounts)
        viewModel.setSearchQuery("chrome")
        advanceUntilIdle()

        val counts = viewModel.categoryCounts.value
        assertEquals(listOf(AppCategory.CAMERA, AppCategory.PHOTOS), counts.keys.toList())
        assertEquals(1, counts[AppCategory.CAMERA])
        assertEquals(2, counts[AppCategory.PHOTOS])
    }

    @Test
    fun `selecting a category narrows filteredApps and selecting null shows all again`() = runTest {
        val viewModel = vm(apps = listOf(camera, chrome, gallery, photos))
        keepCollecting(viewModel.filteredApps)

        viewModel.selectCategory(AppCategory.PHOTOS)
        advanceUntilIdle()
        assertEquals(setOf("gal.pkg", "pho.pkg"), viewModel.filteredApps.value.map { it.packageName }.toSet())

        viewModel.selectCategory(null)
        advanceUntilIdle()
        assertEquals(4, viewModel.filteredApps.value.size)
    }

    @Test
    fun `selectedCategory falls back to All when a refresh leaves it empty`() = runTest {
        val viewModel = vm(apps = listOf(camera, chrome))
        keepCollecting(viewModel.selectedCategory, viewModel.filteredApps)
        viewModel.selectCategory(AppCategory.CAMERA)
        advanceUntilIdle()
        assertEquals(AppCategory.CAMERA, viewModel.selectedCategory.value)

        fakeApps = listOf(chrome)
        viewModel.refreshApps()
        advanceUntilIdle()

        assertNull(viewModel.selectedCategory.value)
        assertEquals(listOf("web.pkg"), viewModel.filteredApps.value.map { it.packageName })
    }

    @Test
    fun `excludeAllVisible adds only visible not-yet-excluded apps in one write`() = runTest {
        exclusionPrefs.addExcludedPackage(gallery.packageName)
        val viewModel = vm(apps = listOf(camera, gallery, photos, chrome))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)

        viewModel.excludeAllVisible()

        assertEquals(setOf("gal.pkg", "pho.pkg"), exclusionPrefs.getExcludedPackages())
        assertEquals(1, changedCount)
        assertEquals(setOf("pho.pkg"), viewModel.pendingUndo.value?.packages)
    }

    @Test
    fun `excludeAllVisible respects the search query`() = runTest {
        val viewModel = vm(apps = listOf(gallery, photos))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)
        viewModel.setSearchQuery("goo")

        viewModel.excludeAllVisible()

        assertEquals(setOf("pho.pkg"), exclusionPrefs.getExcludedPackages())
    }

    @Test
    fun `excludeAllVisible does nothing when every visible app is already excluded`() = runTest {
        exclusionPrefs.setExcludedPackages(setOf(gallery.packageName, photos.packageName))
        val viewModel = vm(apps = listOf(gallery, photos))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)

        viewModel.excludeAllVisible()

        assertEquals(0, changedCount)
        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun `excludeAllVisible does nothing in All mode`() = runTest {
        val viewModel = vm(apps = listOf(camera, chrome))
        advanceUntilIdle()

        viewModel.excludeAllVisible()

        assertTrue(exclusionPrefs.getExcludedPackages().isEmpty())
        assertEquals(0, changedCount)
    }

    @Test
    fun `a double tap on Exclude all writes once`() = runTest {
        val viewModel = vm(apps = listOf(gallery, photos))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)

        viewModel.excludeAllVisible()
        viewModel.excludeAllVisible()

        assertEquals(1, changedCount)
    }

    @Test
    fun `undo removes only the bulk-added apps and keeps earlier exclusions`() = runTest {
        exclusionPrefs.addExcludedPackage(gallery.packageName)
        val viewModel = vm(apps = listOf(gallery, photos))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)
        viewModel.excludeAllVisible()
        val op = viewModel.pendingUndo.value!!

        viewModel.undoBulkExclusion(op)

        assertEquals(setOf("gal.pkg"), exclusionPrefs.getExcludedPackages())
        assertEquals(2, changedCount)
        assertNull(viewModel.pendingUndo.value)
    }

    @Test
    fun `a stale snackbar cannot undo or clear a newer bulk action`() = runTest {
        val viewModel = vm(apps = listOf(camera, photos))
        advanceUntilIdle()
        viewModel.selectCategory(AppCategory.PHOTOS)
        viewModel.excludeAllVisible()
        val first = viewModel.pendingUndo.value!!
        viewModel.selectCategory(AppCategory.CAMERA)
        viewModel.excludeAllVisible()
        val second = viewModel.pendingUndo.value!!

        viewModel.undoBulkExclusion(first)
        viewModel.clearPendingUndo(first)

        assertEquals(setOf("cam.pkg", "pho.pkg"), exclusionPrefs.getExcludedPackages())
        assertSame(second, viewModel.pendingUndo.value)
        assertEquals(2, changedCount)
    }

    @Test
    fun `visibleApps applies search and category together`() {
        val apps = listOf(camera, gallery, photos, chrome)

        assertEquals(apps, visibleApps(apps, "", null))
        assertEquals(listOf(gallery, photos), visibleApps(apps, "", AppCategory.PHOTOS))
        assertEquals(listOf(photos), visibleApps(apps, "goo", AppCategory.PHOTOS))
        assertEquals(listOf(chrome), visibleApps(apps, "CHR", null))
    }
}

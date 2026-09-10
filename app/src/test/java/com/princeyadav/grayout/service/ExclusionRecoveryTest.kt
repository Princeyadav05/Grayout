package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeForegroundAppProvider
import com.princeyadav.grayout.fakes.FakeGrayscaleController
import com.princeyadav.grayout.fakes.FakeSharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExclusionRecoveryTest {
    private val storage = FakeSharedPreferences()
    private val prefs = ExclusionPrefs(storage).apply { addExcludedPackage("excluded") }
    private val enforcement = EnforcementPrefs(storage)
    private val grayscale = FakeGrayscaleController()
    private val foreground = FakeForegroundAppProvider()

    private fun detector(onEnded: () -> Unit = {}) = ForegroundAppDetector(
        foreground, ExclusionPrefs(storage), enforcement, grayscale, "own", onEnded,
        CoroutineScope(Dispatchers.Unconfined),
    )

    private fun savedSession(wasOn: Boolean) {
        prefs.setWasGrayscaleOnBeforeExclusion(wasOn)
        prefs.setExcludedAppActive(true)
        grayscale.grayscaleEnabled = false
    }

    @Test
    fun `application and sticky service startups preserve successful suppression without writes`() {
        savedSession(wasOn = true)
        repeat(2) { reconcileExclusionOnStart(ExclusionPrefs(storage), grayscale, true) }

        assertTrue(prefs.isExcludedAppActive())
        assertTrue(prefs.wasGrayscaleOnBeforeExclusion())
        assertFalse(grayscale.grayscaleEnabled)
        assertEquals(0, grayscale.setGrayscaleCallCount)

        val restarted = detector()
        for (pkg in arrayOf(null, "excluded", "own", null)) {
            foreground.foregroundPackage = pkg
            restarted.tickOnce()
            assertTrue(prefs.wasGrayscaleOnBeforeExclusion())
            assertFalse(grayscale.grayscaleEnabled)
        }
        foreground.foregroundPackage = "outside"
        restarted.tickOnce()
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `already nonexcluded foreground restores saved grayscale after startup`() {
        savedSession(wasOn = true)
        reconcileExclusionOnStart(prefs, grayscale, true)
        foreground.foregroundPackage = "outside"
        detector().tickOnce()
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `repeated restarts retain manual color through an eventual exit`() {
        savedSession(wasOn = false)
        repeat(3) {
            reconcileExclusionOnStart(ExclusionPrefs(storage), grayscale, true)
            foreground.foregroundPackage = "excluded"
            detector().tickOnce()
        }
        foreground.foregroundPackage = "outside"
        detector().tickOnce()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
        assertEquals(0, grayscale.setGrayscaleCallCount)
    }

    @Test
    fun `failed grayscale restoration remains pending across startup and retries on exit`() {
        savedSession(wasOn = true)
        grayscale.canWrite = false
        foreground.foregroundPackage = "outside"
        detector().tickOnce()
        assertTrue(prefs.hasPendingDisplayWrite())

        reconcileExclusionOnStart(ExclusionPrefs(storage), grayscale, true)
        assertTrue(prefs.hasPendingDisplayWrite())
        assertTrue(prefs.wasGrayscaleOnBeforeExclusion())
        grayscale.canWrite = true
        detector().tickOnce()
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(prefs.hasPendingDisplayWrite())
    }

    @Test
    fun `failed color write survives startup until the detector retries it`() {
        savedSession(wasOn = false)
        grayscale.grayscaleEnabled = true
        prefs.setColorRestorePending(true)
        reconcileExclusionOnStart(ExclusionPrefs(storage), grayscale, true)
        foreground.foregroundPackage = "excluded"
        detector().tickOnce()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(prefs.isColorRestorePending())
        assertFalse(prefs.wasGrayscaleOnBeforeExclusion())
    }

    @Test
    fun `screen off startup restores grayscale before wake`() {
        savedSession(wasOn = true)
        reconcileExclusionOnStart(prefs, grayscale, false)
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `screen off recovery retains target after a failed write`() {
        savedSession(wasOn = true)
        grayscale.canWrite = false
        reconcileExclusionOnStart(prefs, grayscale, false)
        assertTrue(prefs.isExcludedAppActive())
        assertTrue(prefs.wasGrayscaleOnBeforeExclusion())
        assertTrue(prefs.hasPendingDisplayWrite())
    }

    @Test
    fun `removing all exclusions recovers a saved target even with screen on`() {
        savedSession(wasOn = true)
        prefs.setExcludedPackages(emptySet())
        reconcileExclusionOnStart(prefs, grayscale, true)
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `schedule end after restart supersedes the retained target`() {
        savedSession(wasOn = true)
        reconcileExclusionOnStart(prefs, grayscale, true)
        applyScheduleGrayscale(false, prefs, grayscale)
        foreground.foregroundPackage = "outside"
        detector().tickOnce()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(prefs.isExcludedAppActive())
    }

    @Test
    fun `standing enforcement remains configured and receives the recovered color exit`() {
        enforcement.setInterval(5)
        savedSession(wasOn = false)
        reconcileExclusionOnStart(prefs, grayscale, true)
        foreground.foregroundPackage = "outside"
        var notified = false
        detector { notified = true }.tickOnce()
        assertTrue(notified)
        assertEquals(5, enforcement.getInterval())
    }

    @Test
    fun `restored service interval does not reset an existing countdown`() {
        assertFalse(enforcementIntervalChanged(null, 5, false))
        assertFalse(enforcementIntervalChanged(5, 5, false))
        assertTrue(enforcementIntervalChanged(5, 5, true))
        assertTrue(enforcementIntervalChanged(null, 5, true))
        assertTrue(enforcementIntervalChanged(5, 10, true))
    }
}

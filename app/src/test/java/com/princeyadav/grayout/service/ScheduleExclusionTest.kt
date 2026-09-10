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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ScheduleExclusionTest {
    private val prefs = FakeSharedPreferences()
    private val exclusions = ExclusionPrefs(prefs)
    private val enforcement = EnforcementPrefs(prefs)
    private val grayscale = FakeGrayscaleController()
    private val provider = FakeForegroundAppProvider()
    private var ended = 0

    private fun detector(controller: GrayscaleController = grayscale) = ForegroundAppDetector(
        provider, exclusions, enforcement, controller, "own.pkg", { ended++ },
        CoroutineScope(Dispatchers.Unconfined),
    )

    private fun enter(detector: ForegroundAppDetector = detector(), wasOn: Boolean) {
        exclusions.addExcludedPackage("excluded.pkg")
        grayscale.grayscaleEnabled = wasOn
        provider.foregroundPackage = "excluded.pkg"
        detector.tickOnce()
    }

    private fun exit(detector: ForegroundAppDetector = detector()) {
        provider.foregroundPackage = "other.pkg"
        detector.tickOnce()
    }

    @Test
    fun `schedule start keeps an excluded app colored and restores gray on exit`() {
        val detector = detector()
        enter(detector, wasOn = false)
        val writes = grayscale.setGrayscaleCallCount

        assertTrue(applyScheduleGrayscale(true, ExclusionPrefs(prefs), grayscale))
        repeat(3) { detector.tickOnce() }

        assertFalse(grayscale.grayscaleEnabled)
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())
        assertEquals(writes + 1, grayscale.setGrayscaleCallCount)
        exit(detector)
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
    }

    @Test
    fun `schedule end replaces the saved on state so exiting stays colored`() {
        enter(wasOn = true)
        assertTrue(applyScheduleGrayscale(false, exclusions, grayscale))
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        exit()

        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
        assertEquals(0, ended)
    }

    @Test
    fun `latest boundary wins across repeated start end and resync requests`() {
        enter(wasOn = false)
        repeat(3) { applyScheduleGrayscale(true, exclusions, grayscale) }
        applyScheduleGrayscale(false, exclusions, grayscale)
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        applyScheduleGrayscale(true, exclusions, grayscale)
        assertFalse(grayscale.grayscaleEnabled)
        exit()
        assertTrue(grayscale.grayscaleEnabled)
    }

    @Test
    fun `schedule boundaries outside an exclusion still apply immediately`() {
        assertTrue(applyScheduleGrayscale(true, exclusions, grayscale))
        assertTrue(grayscale.grayscaleEnabled)
        assertTrue(applyScheduleGrayscale(false, exclusions, grayscale))
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
    }

    @Test
    fun `schedule end preserves standing enforcement and signals it only after exit`() {
        enforcement.setInterval(5)
        enter(wasOn = true)
        applyScheduleGrayscale(false, exclusions, grayscale)

        assertEquals(EnforcementTickResult.Skipped, applyEnforcementTick(enforcement, exclusions, grayscale))
        assertFalse(grayscale.grayscaleEnabled)
        assertEquals(5, enforcement.getInterval())
        assertEquals(0, ended)

        exit()
        assertEquals(1, ended)
        assertEquals(5, enforcement.getInterval())
        assertEquals(EnforcementTickResult.Applied, applyEnforcementTick(enforcement, exclusions, grayscale))
    }

    @Test
    fun `failed restoration of a deferred start retries with its request intact`() {
        enter(wasOn = false)
        applyScheduleGrayscale(true, exclusions, grayscale)
        grayscale.canWrite = false
        exit()
        assertTrue(exclusions.isExcludedAppActive())
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())

        grayscale.canWrite = true
        exit()
        assertTrue(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
    }

    @Test
    fun `schedule end supersedes a failed restore before permission recovers`() {
        enter(wasOn = true)
        grayscale.canWrite = false
        exit()
        applyScheduleGrayscale(false, exclusions, grayscale)
        grayscale.canWrite = true
        exit()

        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
    }

    @Test
    fun `failed immediate write reports failure without inventing an exclusion`() {
        grayscale.canWrite = false
        assertFalse(applyScheduleGrayscale(true, exclusions, grayscale))
        assertFalse(exclusions.isExcludedAppActive())
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
        grayscale.canWrite = true
        assertTrue(applyScheduleGrayscale(true, exclusions, grayscale))
    }

    @Test
    fun `schedule end repairs an exclusion whose entry color write failed`() {
        grayscale.canWrite = false
        enter(wasOn = true)
        assertTrue(grayscale.grayscaleEnabled)
        assertTrue(exclusions.isColorRestorePending())

        grayscale.canWrite = true
        assertTrue(applyScheduleGrayscale(false, exclusions, grayscale))
        exit()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isColorRestorePending())
    }

    @Test
    fun `failed entry retries color without replacing the original restoration target`() {
        grayscale.canWrite = false
        enter(wasOn = true)
        grayscale.canWrite = true
        detector().tickOnce()

        assertFalse(grayscale.grayscaleEnabled)
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())
        assertFalse(exclusions.isColorRestorePending())
        exit()
        assertTrue(grayscale.grayscaleEnabled)
    }

    @Test
    fun `schedule end corrects grayscale manually enabled during an exclusion`() {
        enter(wasOn = true)
        grayscale.setGrayscale(true)
        applyScheduleGrayscale(false, exclusions, grayscale)
        assertFalse(grayscale.grayscaleEnabled)
        exit()
        assertFalse(grayscale.grayscaleEnabled)
    }

    @Test
    fun `failed schedule color write retries while staying inside the excluded app`() {
        enter(wasOn = true)
        grayscale.setGrayscale(true)
        grayscale.canWrite = false
        assertFalse(applyScheduleGrayscale(false, exclusions, grayscale))
        assertTrue(exclusions.isColorRestorePending())

        grayscale.canWrite = true
        detector().tickOnce()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isColorRestorePending())
        exit()
        assertFalse(grayscale.grayscaleEnabled)
    }

    @Test
    fun `failed schedule color write retains evidence across exit until recovery`() {
        enter(wasOn = true)
        grayscale.setGrayscale(true)
        grayscale.canWrite = false
        applyScheduleGrayscale(false, exclusions, grayscale)
        exit()
        assertTrue(exclusions.isExcludedAppActive())
        assertTrue(exclusions.isColorRestorePending())

        grayscale.canWrite = true
        exit()
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
        assertFalse(exclusions.isColorRestorePending())
    }

    @Test
    fun `restart recovery honors a failed off request from schedule end`() {
        enter(wasOn = true)
        grayscale.setGrayscale(true)
        grayscale.canWrite = false
        applyScheduleGrayscale(false, exclusions, grayscale)
        reconcileStrandedExclusion(exclusions, grayscale)
        assertTrue(exclusions.isColorRestorePending())

        grayscale.canWrite = true
        reconcileStrandedExclusion(ExclusionPrefs(prefs), grayscale)
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
        assertFalse(exclusions.isColorRestorePending())
    }

    @Test
    fun `screen off honors the latest deferred schedule state`() {
        enter(wasOn = false)
        applyScheduleGrayscale(true, exclusions, grayscale)
        assertTrue(preGrayOnScreenOff(exclusions, grayscale))
        assertTrue(grayscale.grayscaleEnabled)

        enter(wasOn = true)
        applyScheduleGrayscale(false, exclusions, grayscale)
        assertFalse(preGrayOnScreenOff(exclusions, grayscale))
        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(reconcileStrandedExclusion(exclusions, grayscale))
        assertFalse(exclusions.isExcludedAppActive())
    }

    @Test
    fun `schedule start cannot interleave between entry snapshot and application`() {
        val inRead = CountDownLatch(1)
        val finishRead = CountDownLatch(1)
        val controller = object : GrayscaleController by grayscale {
            override fun isGrayscaleEnabled(): Boolean {
                val state = grayscale.grayscaleEnabled
                inRead.countDown()
                assertTrue(finishRead.await(5, TimeUnit.SECONDS))
                return state
            }
        }
        val detector = detector(controller)
        exclusions.addExcludedPackage("excluded.pkg")
        provider.foregroundPackage = "excluded.pkg"

        runContended(
            first = { detector.tickOnce() }, entered = inRead, release = finishRead,
            second = { applyScheduleGrayscale(true, ExclusionPrefs(prefs), grayscale) },
        )

        assertFalse(grayscale.grayscaleEnabled)
        assertTrue(exclusions.wasGrayscaleOnBeforeExclusion())
        exit(detector)
        assertTrue(grayscale.grayscaleEnabled)
    }

    @Test
    fun `schedule end cannot be overwritten by an exit already restoring grayscale`() {
        enter(wasOn = true)
        val inWrite = CountDownLatch(1)
        val finishWrite = CountDownLatch(1)
        val detector = detector(object : GrayscaleController by grayscale {
            override fun setGrayscale(enabled: Boolean): Boolean {
                inWrite.countDown()
                assertTrue(finishWrite.await(5, TimeUnit.SECONDS))
                return grayscale.setGrayscale(enabled)
            }
        })

        runContended(
            first = { exit(detector) }, entered = inWrite, release = finishWrite,
            second = { applyScheduleGrayscale(false, ExclusionPrefs(prefs), grayscale) },
        )

        assertFalse(grayscale.grayscaleEnabled)
        assertFalse(exclusions.isExcludedAppActive())
        assertFalse(exclusions.wasGrayscaleOnBeforeExclusion())
    }

    /** Hold one production operation at its read/write seam and prove the other waits. */
    private fun runContended(
        first: () -> Unit,
        entered: CountDownLatch,
        release: CountDownLatch,
        second: () -> Unit,
    ) {
        val executor = Executors.newFixedThreadPool(2)
        try {
            val firstResult = executor.submit(first)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val contender = AtomicReference<Thread>()
            val secondResult = executor.submit {
                contender.set(Thread.currentThread())
                second()
            }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (contender.get()?.state != Thread.State.BLOCKED &&
                !secondResult.isDone && System.nanoTime() < deadline
            ) {
                Thread.sleep(1)
            }
            assertEquals("The whole transition must serialize, including its reads", Thread.State.BLOCKED, contender.get()?.state)
            release.countDown()
            firstResult.get(5, TimeUnit.SECONDS)
            secondResult.get(5, TimeUnit.SECONDS)
        } finally {
            release.countDown()
            executor.shutdownNow()
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        }
    }
}

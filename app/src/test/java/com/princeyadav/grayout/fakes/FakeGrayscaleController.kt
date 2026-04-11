package com.princeyadav.grayout.fakes

import com.princeyadav.grayout.service.GrayscaleController

/**
 * Fake implementation of [GrayscaleController] for JVM unit tests.
 *
 * Production short-circuits NOT modeled here (by design):
 * - Real GrayscaleManager catches SecurityException silently; this fake
 *   models the same via the `canWrite` flag, but does NOT simulate partial
 *   failure modes (e.g., writing daltonizer_enabled but not daltonizer).
 * - Real isGrayscaleEnabled reads Settings.Secure every call; this fake
 *   returns in-memory state, so stale system reads are not reproducible.
 *
 * If a test needs production parity on either of these, use instrumentation.
 */
class FakeGrayscaleController : GrayscaleController {
    var grayscaleEnabled = false
    var canWrite = true
    var accessibilityEnabled = false
    var setGrayscaleCallCount = 0

    override fun isGrayscaleEnabled() = grayscaleEnabled

    override fun setGrayscale(enabled: Boolean): Boolean {
        setGrayscaleCallCount++
        if (!canWrite) return false
        grayscaleEnabled = enabled
        return true
    }

    override fun canWriteSecureSettings() = canWrite

    override fun isAccessibilityServiceEnabled(packageName: String) = accessibilityEnabled
}

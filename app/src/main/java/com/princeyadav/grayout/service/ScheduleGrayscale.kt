package com.princeyadav.grayout.service

/**
 * Applies a schedule boundary or active-window resynchronization. While an app
 * exclusion is active, change the state to restore on exit and ensure the display
 * is colored. A failed entry write or a manual toggle may have left it gray.
 * A later boundary replaces the request, so an ended schedule cannot be restored.
 *
 * The independent enforcement interval is deliberately untouched. Returns true
 * when the display write succeeded; failed writes are retained for retry. While
 * the screen is off there is no live exclusion to defer to: apply the requested
 * state and clear the suspended session only after a successful write.
 */
fun applyScheduleGrayscale(
    enabled: Boolean,
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
    isScreenOn: () -> Boolean = { true },
): Boolean = synchronized(GrayscaleStateLock) {
    if (exclusionPrefs.isExcludedAppActive()) {
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(enabled)
        if (isScreenOn()) {
            restoreExclusionColor(exclusionPrefs, grayscale)
        } else {
            // With the screen off, this is a suspended session. Apply the new
            // schedule target so the device wakes in the correct state, then let
            // the detector capture that state on the next screen-on entry.
            val success = if (enabled) {
                restoreExclusionGrayscale(exclusionPrefs, grayscale)
            } else {
                restoreExclusionColor(exclusionPrefs, grayscale)
            }
            if (success) exclusionPrefs.clearExclusionState()
            success
        }
    } else {
        grayscale.setGrayscale(enabled)
    }
}

/** Keep failed color writes visible to the detector, including across process death. */
internal fun restoreExclusionColor(
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): Boolean {
    exclusionPrefs.setColorRestorePending(true)
    val success = grayscale.setGrayscale(false)
    if (success) exclusionPrefs.setColorRestorePending(false)
    return success
}

/** A failed on-write is also recovery evidence, including on a screen-on cold start. */
internal fun restoreExclusionGrayscale(
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): Boolean {
    exclusionPrefs.setGrayscaleRestorePending(true)
    val success = grayscale.setGrayscale(true)
    if (success) exclusionPrefs.setGrayscaleRestorePending(false)
    return success
}

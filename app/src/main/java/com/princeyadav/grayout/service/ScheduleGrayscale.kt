package com.princeyadav.grayout.service

/**
 * Applies a schedule boundary or active-window resynchronization. While an app
 * exclusion is active, change the state to restore on exit and ensure the display
 * is colored. A failed entry write or a manual toggle may have left it gray.
 * A later boundary replaces the request, so an ended schedule cannot be restored.
 *
 * The independent enforcement interval is deliberately untouched. Returns true
 * when the display write succeeded; a failed color write is retained for retry.
 */
fun applyScheduleGrayscale(
    enabled: Boolean,
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
): Boolean = synchronized(GrayscaleStateLock) {
    if (exclusionPrefs.isExcludedAppActive()) {
        exclusionPrefs.setWasGrayscaleOnBeforeExclusion(enabled)
        restoreExclusionColor(exclusionPrefs, grayscale)
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

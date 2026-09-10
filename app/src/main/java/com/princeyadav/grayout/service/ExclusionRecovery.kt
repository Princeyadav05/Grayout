package com.princeyadav.grayout.service

/**
 * Screen-on startup cannot distinguish a live exclusion from a departed app yet.
 * Keep its restoration target until the detector resolves the foreground. This
 * applies to successful suppression as well as failed display writes.
 *
 * While screen-off, or with no configured exclusions, no live session can need
 * color: reconcile immediately, retaining recovery evidence if the write fails.
 * Shared by Application cold starts and service (including sticky) restarts.
 */
fun reconcileExclusionOnStart(
    exclusionPrefs: ExclusionPrefs,
    grayscale: GrayscaleController,
    isScreenOn: Boolean,
): Unit = synchronized(GrayscaleStateLock) {
    if (!isScreenOn || exclusionPrefs.getExcludedCount() == 0) {
        reconcileStrandedExclusion(exclusionPrefs, grayscale)
    }
}

/** A restored service's default field value is not a user-requested interval change. */
internal fun enforcementIntervalChanged(
    previousInterval: Int?,
    interval: Int,
    explicitlyRequested: Boolean,
): Boolean = explicitlyRequested || previousInterval?.let { it != interval } == true

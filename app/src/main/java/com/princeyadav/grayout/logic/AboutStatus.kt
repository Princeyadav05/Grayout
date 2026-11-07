package com.princeyadav.grayout.logic

/**
 * The Settings About line. It starts with the same grayscale state Home shows, so the two screens
 * never disagree, then says what the background service is doing. "Idle" names the service, not
 * grayscale: grayscale can be on while the service has nothing to watch.
 */
internal fun aboutStatusLine(isGrayscaleOn: Boolean, isServiceRunning: Boolean, isServiceExpected: Boolean): String {
    val grayscale = if (isGrayscaleOn) "Grayscale on." else "Grayscale off."
    val service = when {
        isServiceRunning -> "Background service running."
        !isServiceExpected -> "Background service idle, nothing to watch."
        else -> "Background service not running."
    }
    return "$grayscale $service"
}

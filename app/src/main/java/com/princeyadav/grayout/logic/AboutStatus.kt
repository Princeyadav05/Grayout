package com.princeyadav.grayout.logic

/**
 * The Settings About line, in plain words. It starts with the same grayscale state Home shows, so the
 * two screens never disagree, then says whether Grayout runs in the background. The background service
 * is needed only for automatic restore or kept-in-color apps ([com.princeyadav.grayout.service.shouldServiceRun]),
 * so "not running" is normal when neither is set and must not read as grayscale being off.
 */
internal fun aboutStatusLine(isGrayscaleOn: Boolean, isServiceRunning: Boolean, isServiceExpected: Boolean): String {
    val grayscale = if (isGrayscaleOn) "Grayscale is on." else "Grayscale is off."
    val service = when {
        isServiceRunning -> "Grayout is running in the background."
        !isServiceExpected ->
            "Grayout runs in the background only when automatic restore or a kept-in-color app needs it."
        else -> "Grayout should be running in the background, but it stopped."
    }
    return "$grayscale $service"
}

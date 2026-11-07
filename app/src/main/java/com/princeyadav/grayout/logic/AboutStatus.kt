package com.princeyadav.grayout.logic

/**
 * The Settings About line: one short line in plain words. It starts with the same grayscale state Home
 * shows, so the two screens never disagree, then says whether Grayout runs in the background. The
 * background service is needed only for automatic restore or kept-in-color apps
 * ([com.princeyadav.grayout.service.shouldServiceRun]), so "not running" is normal when neither is set
 * and must not read as grayscale being off. Schedules run on system alarms (AlarmManager), not on the
 * service, so with a schedule enabled the idle line says that instead of "nothing runs".
 */
internal fun aboutStatusLine(
    isGrayscaleOn: Boolean,
    isServiceRunning: Boolean,
    isServiceExpected: Boolean,
    isAnyScheduleEnabled: Boolean,
): String {
    val grayscale = if (isGrayscaleOn) "Grayscale is on." else "Grayscale is off."
    val service = when {
        isServiceRunning -> "Running in the background."
        !isServiceExpected && isAnyScheduleEnabled -> "Schedules run on system alarms."
        !isServiceExpected -> "Nothing runs in the background."
        else -> "Background work stopped unexpectedly."
    }
    return "$grayscale $service"
}

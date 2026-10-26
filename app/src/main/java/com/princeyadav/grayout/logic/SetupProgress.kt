package com.princeyadav.grayout.logic

/** Where the user is in the three-step ADB setup guide. */
enum class SetupStepState { Done, Current, Upcoming }

/**
 * How many guide steps are finished: developer options on, USB debugging on, permission granted.
 * A grant finishes everything, even if the user has since turned the toggles back off.
 */
internal fun setupStepsDone(developerOptionsOn: Boolean, usbDebuggingOn: Boolean, granted: Boolean): Int = when {
    granted -> 3
    developerOptionsOn && usbDebuggingOn -> 2
    developerOptionsOn -> 1
    else -> 0
}

/** State of the step at [index] (0-based) given [stepsDone]. */
internal fun setupStepState(index: Int, stepsDone: Int): SetupStepState = when {
    index < stepsDone -> SetupStepState.Done
    index == stepsDone -> SetupStepState.Current
    else -> SetupStepState.Upcoming
}

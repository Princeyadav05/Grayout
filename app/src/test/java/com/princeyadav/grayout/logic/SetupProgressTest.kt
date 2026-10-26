package com.princeyadav.grayout.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupProgressTest {

    @Test
    fun nothingOnMeansNoStepsDone() {
        assertEquals(0, setupStepsDone(developerOptionsOn = false, usbDebuggingOn = false, granted = false))
    }

    @Test
    fun developerOptionsAloneFinishesStepOne() {
        assertEquals(1, setupStepsDone(developerOptionsOn = true, usbDebuggingOn = false, granted = false))
    }

    @Test
    fun usbDebuggingWithoutDeveloperOptionsDoesNotCount() {
        assertEquals(0, setupStepsDone(developerOptionsOn = false, usbDebuggingOn = true, granted = false))
    }

    @Test
    fun bothTogglesFinishStepTwo() {
        assertEquals(2, setupStepsDone(developerOptionsOn = true, usbDebuggingOn = true, granted = false))
    }

    @Test
    fun grantFinishesEverythingEvenWithTogglesOff() {
        assertEquals(3, setupStepsDone(developerOptionsOn = false, usbDebuggingOn = false, granted = true))
    }

    @Test
    fun stepStatesFollowProgress() {
        assertEquals(
            listOf(SetupStepState.Done, SetupStepState.Current, SetupStepState.Upcoming),
            (0..2).map { setupStepState(it, stepsDone = 1) },
        )
        assertEquals(
            listOf(SetupStepState.Done, SetupStepState.Done, SetupStepState.Done),
            (0..2).map { setupStepState(it, stepsDone = 3) },
        )
    }
}

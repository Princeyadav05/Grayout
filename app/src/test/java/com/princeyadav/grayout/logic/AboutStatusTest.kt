package com.princeyadav.grayout.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutStatusTest {
    @Test
    fun `idle service with grayscale on still says grayscale is on`() {
        assertEquals(
            "Grayscale is on. Grayout runs in the background only when automatic restore or a kept-in-color app needs it.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = false),
        )
    }

    @Test
    fun `running service`() {
        assertEquals(
            "Grayscale is off. Grayout is running in the background.",
            aboutStatusLine(isGrayscaleOn = false, isServiceRunning = true, isServiceExpected = true),
        )
    }

    @Test
    fun `expected but stopped service`() {
        assertEquals(
            "Grayscale is on. Grayout should be running in the background, but it stopped.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = true),
        )
    }
}

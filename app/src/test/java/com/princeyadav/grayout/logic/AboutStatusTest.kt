package com.princeyadav.grayout.logic

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutStatusTest {
    @Test
    fun `idle service with grayscale on still says grayscale on`() {
        assertEquals(
            "Grayscale on. Background service idle, nothing to watch.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = false),
        )
    }

    @Test
    fun `running service`() {
        assertEquals(
            "Grayscale off. Background service running.",
            aboutStatusLine(isGrayscaleOn = false, isServiceRunning = true, isServiceExpected = true),
        )
    }

    @Test
    fun `expected but stopped service`() {
        assertEquals(
            "Grayscale on. Background service not running.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = true),
        )
    }
}

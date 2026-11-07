package com.princeyadav.grayout.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutStatusTest {
    @Test
    fun `idle service with grayscale on still says grayscale is on`() {
        assertEquals(
            "Grayscale is on. Nothing runs in the background.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = false, isAnyScheduleEnabled = false),
        )
    }

    @Test
    fun `running service`() {
        assertEquals(
            "Grayscale is off. Running in the background.",
            aboutStatusLine(isGrayscaleOn = false, isServiceRunning = true, isServiceExpected = true, isAnyScheduleEnabled = false),
        )
    }

    @Test
    fun `expected but stopped service`() {
        assertEquals(
            "Grayscale is on. Background work stopped unexpectedly.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = true, isAnyScheduleEnabled = false),
        )
    }

    @Test
    fun `idle service with a schedule enabled says schedules run on alarms`() {
        assertEquals(
            "Grayscale is on. Schedules run on system alarms.",
            aboutStatusLine(isGrayscaleOn = true, isServiceRunning = false, isServiceExpected = false, isAnyScheduleEnabled = true),
        )
    }

    @Test
    fun `every line stays short`() {
        val flags = listOf(true, false)
        for (on in flags) for (running in flags) for (expected in flags) for (schedules in flags) {
            val line = aboutStatusLine(on, running, expected, schedules)
            assertTrue(line, line.length <= 60)
        }
    }
}

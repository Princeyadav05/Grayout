package com.princeyadav.grayout.model

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTimeTest {

    @Test
    fun `24-hour setting shows zero-padded 24-hour times`() {
        assertEquals("00:30", formatTime(0, 30, is24Hour = true))
        assertEquals("09:05", formatTime(9, 5, is24Hour = true))
        assertEquals("21:00", formatTime(21, 0, is24Hour = true))
    }

    @Test
    fun `12-hour setting keeps AM and PM times`() {
        assertEquals("12:30 AM", formatTime(0, 30, is24Hour = false))
        assertEquals("12:00 PM", formatTime(12, 0, is24Hour = false))
        assertEquals("9:05 AM", formatTime(9, 5, is24Hour = false))
    }
}

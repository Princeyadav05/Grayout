package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeSharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Test

class EnforcementPrefsTest {

    private val prefs = EnforcementPrefs(FakeSharedPreferences())

    @Test
    fun `default interval is 0`() {
        assertEquals(0, prefs.getInterval())
    }

    @Test
    fun `setInterval then getInterval returns the value`() {
        prefs.setInterval(15)
        assertEquals(15, prefs.getInterval())
    }

    @Test
    fun `multiple setInterval calls - last one wins`() {
        prefs.setInterval(5)
        prefs.setInterval(30)
        prefs.setInterval(10)
        assertEquals(10, prefs.getInterval())
    }

    @Test
    fun `PREFS_NAME is grayout_prefs`() {
        assertEquals("grayout_prefs", EnforcementPrefs.PREFS_NAME)
    }

    @Test
    fun `setInterval 0 disables enforcement`() {
        prefs.setInterval(15)
        prefs.setInterval(0)
        assertEquals(0, prefs.getInterval())
    }

    @Test
    fun `negative interval stored and retrieved correctly`() {
        prefs.setInterval(-1)
        assertEquals(-1, prefs.getInterval())
    }
}

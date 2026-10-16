package com.princeyadav.grayout.scheduling

import org.junit.Assert.assertEquals
import org.junit.Test

class ApplyActivationTest {

    @Test
    fun `an activation arms its closing boundary before the display write`() {
        val calls = mutableListOf<String>()

        applyActivation(true, armClosingBoundary = { calls += "arm" }) { calls += "write"; true }
        assertEquals(listOf("arm", "write"), calls)

        calls.clear()
        applyActivation(false, armClosingBoundary = { calls += "arm" }) { calls += "write"; true }
        assertEquals(listOf("write"), calls)
    }
}

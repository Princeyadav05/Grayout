package com.princeyadav.grayout.scheduling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleReceiverTest {

    @Test
    fun `start event starts service when enforcement is on`() {
        assertTrue(shouldStartServiceForScheduleEvent(isStart = true, persistedInterval = 5))
    }

    @Test
    fun `start event does not start service when enforcement is off`() {
        assertFalse(shouldStartServiceForScheduleEvent(isStart = true, persistedInterval = 0))
    }

    @Test
    fun `end event starts service when enforcement is on`() {
        assertTrue(shouldStartServiceForScheduleEvent(isStart = false, persistedInterval = 5))
    }

    @Test
    fun `end event starts service even when enforcement is off`() {
        assertTrue(shouldStartServiceForScheduleEvent(isStart = false, persistedInterval = 0))
    }
}

package com.princeyadav.grayout.scheduling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleReceiverTest {

    @Test
    fun `start event starts service with persisted interval when enforcement is on`() {
        assertEquals(
            5,
            serviceIntervalExtraForScheduleEvent(isStart = true, persistedInterval = 5),
        )
    }

    @Test
    fun `start event does not start service when enforcement is off`() {
        assertNull(serviceIntervalExtraForScheduleEvent(isStart = true, persistedInterval = 0))
    }

    @Test
    fun `end event preserves persisted interval when enforcement is on`() {
        assertEquals(
            5,
            serviceIntervalExtraForScheduleEvent(isStart = false, persistedInterval = 5),
        )
    }

    @Test
    fun `end event sends zero only when persisted interval is zero`() {
        assertEquals(
            0,
            serviceIntervalExtraForScheduleEvent(isStart = false, persistedInterval = 0),
        )
    }
}

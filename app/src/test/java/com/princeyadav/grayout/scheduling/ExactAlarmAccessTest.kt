package com.princeyadav.grayout.scheduling

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmAccessTest {

    @Test
    fun `only Android 12 and 12L can be left without exact alarms`() {
        assertTrue(needsExactAlarmAccess(sdkInt = 31, canScheduleExact = false))
        assertTrue(needsExactAlarmAccess(sdkInt = 32, canScheduleExact = false))
        assertFalse(needsExactAlarmAccess(sdkInt = 32, canScheduleExact = true))
        // 30 and below need no grant; 33+ holds the non-revocable USE_EXACT_ALARM.
        assertFalse(needsExactAlarmAccess(sdkInt = 30, canScheduleExact = false))
        assertFalse(needsExactAlarmAccess(sdkInt = 33, canScheduleExact = false))
    }
}

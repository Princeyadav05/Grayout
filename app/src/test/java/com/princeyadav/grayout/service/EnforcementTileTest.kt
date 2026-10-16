package com.princeyadav.grayout.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EnforcementTileTest {

    @Test
    fun `without permission the tile only turns enforcement off or opens setup`() {
        assertEquals(1, enforcementTileTarget(current = 0, canWrite = true))
        assertNull(enforcementTileTarget(current = 0, canWrite = false))
        assertEquals(0, enforcementTileTarget(current = 5, canWrite = false))
    }
}

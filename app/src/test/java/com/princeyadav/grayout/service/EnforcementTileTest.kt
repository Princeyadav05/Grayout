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

    @Test
    fun `tile cycles the shared interval options in order and wraps to off`() {
        val cycle = generateSequence(0) { enforcementTileTarget(it, canWrite = true) }
            .take(7).toList()
        assertEquals(listOf(0, 1, 5, 10, 15, 30, 0), cycle)
        assertEquals(0, enforcementTileTarget(current = 7, canWrite = true))
    }
}

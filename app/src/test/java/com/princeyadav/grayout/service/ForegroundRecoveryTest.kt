package com.princeyadav.grayout.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ForegroundRecoveryTest {
    @Test
    fun `first empty recent query uses history once then returns to normal polling`() {
        val calls = mutableListOf<Triple<Long, Long, Boolean>>()
        val end = RECOVERY_LOOKBACK_MS * 2
        val lookup = ForegroundRecoveryLookup({ begin, until, history ->
            calls.add(Triple(begin, until, history))
            if (history) "already.open" else null
        }, { end })

        assertEquals("already.open", lookup.currentForegroundPackage())
        repeat(3) { assertNull(lookup.currentForegroundPackage()) }
        assertEquals(1, calls.count { it.third })
        assertEquals(Triple(end - RECOVERY_LOOKBACK_MS, end, true), calls[1])
        assertEquals(4, calls.count { it == Triple(end - LOOKBACK_MS, end, false) })
    }

    @Test
    fun `fresh own package passes through without recovering older history`() {
        val historyFlags = mutableListOf<Boolean>()
        val lookup = ForegroundRecoveryLookup({ _, _, history ->
            historyFlags.add(history)
            "own"
        }, { 100_000 })
        assertEquals("own", lookup.currentForegroundPackage())
        assertEquals(listOf(false), historyFlags)
    }

    @Test
    fun `empty or denied history remains unknown and is not repeatedly scanned`() {
        var calls = 0
        val lookup = ForegroundRecoveryLookup({ begin, _, _ ->
            check(begin >= 0)
            calls++
            null
        }, { 1_000 })
        repeat(3) { assertNull(lookup.currentForegroundPackage()) }
        assertEquals(4, calls)
    }

    @Test
    fun `historical foreground is invalidated when that activity pauses`() {
        val history = ForegroundHistory()
        history.record(ForegroundHistoryEvent.Resumed, "outside", "Activity")
        assertEquals("outside", history.packageName)
        history.record(ForegroundHistoryEvent.Backgrounded, "outside", "Activity")
        assertNull(history.packageName)
    }

    @Test
    fun `old activity stopping does not erase a newer activity from the same package`() {
        val history = ForegroundHistory()
        history.record(ForegroundHistoryEvent.Resumed, "pkg", "OldActivity")
        history.record(ForegroundHistoryEvent.Resumed, "pkg", "CurrentActivity")
        history.record(ForegroundHistoryEvent.Backgrounded, "pkg", "OldActivity")
        assertEquals("pkg", history.packageName)
    }

    @Test
    fun `lock screen or restart invalidates history and unlock alone does not reestablish it`() {
        val history = ForegroundHistory()
        history.record(ForegroundHistoryEvent.Resumed, "outside", "Activity")
        history.record(ForegroundHistoryEvent.Reset, null, null)
        history.record(ForegroundHistoryEvent.Other, null, null)
        assertNull(history.packageName)
        history.record(ForegroundHistoryEvent.Resumed, "excluded", "Activity")
        assertEquals("excluded", history.packageName)
    }

    @Test
    fun `latest own package replaces previous excluded history verbatim`() {
        val history = ForegroundHistory()
        history.record(ForegroundHistoryEvent.Resumed, "excluded", "Activity")
        history.record(ForegroundHistoryEvent.Resumed, "own", "Activity")
        history.record(ForegroundHistoryEvent.Backgrounded, "excluded", "Activity")
        assertEquals("own", history.packageName)
    }
}

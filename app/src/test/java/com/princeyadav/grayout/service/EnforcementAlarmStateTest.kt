package com.princeyadav.grayout.service

import com.princeyadav.grayout.fakes.FakeSharedPreferences
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EnforcementAlarmStateTest {
    @Test fun `elapsed deadline survives new wrapper and is independent of wall clock`() {
        val prefs = FakeSharedPreferences()
        EnforcementAlarmState(prefs).save(123_456L, 7, 5)
        assertEquals(123_456L, EnforcementAlarmState(prefs).deadline(7, 5))
    }
    @Test fun `a new boot or unavailable boot identity cannot reuse the previous deadline`() {
        val state = EnforcementAlarmState(FakeSharedPreferences())
        state.save(123_456L, 7, 5)
        assertNull(state.deadline(8, 5))
        assertNull(state.deadline(-1, 5))
    }
    @Test fun `a changed interval or missing provenance cannot reuse an early deadline`() {
        val prefs = FakeSharedPreferences()
        val state = EnforcementAlarmState(prefs)
        state.save(123_456L, 7, 5)
        assertNull(state.deadline(7, 15))
        prefs.edit().remove("enforcement_alarm_interval_minutes").commit()
        assertNull(state.deadline(7, 5))
    }
    @Test fun `canceling or spending the alarm clears its recoverable deadline`() {
        val state = EnforcementAlarmState(FakeSharedPreferences())
        state.save(123_456L, 7, 5)
        state.clear()
        assertNull(state.deadline(7, 5))
    }

    @Test fun `restoring one deadline preserves identity while a reset replaces it`() {
        val prefs = FakeSharedPreferences()
        val state = EnforcementAlarmState(prefs)
        val original = state.save(123_456L, 7, 5)
        assertEquals(original, EnforcementAlarmState(prefs).save(123_456L, 7, 5))
        assertNotEquals(original, state.save(999_000L, 7, 15))
        state.clear()
        assertNotEquals(original, state.save(123_456L, 7, 5))
    }

    @Test fun `only the current due deadline for this boot and interval can act`() {
        val state = EnforcementAlarmState(FakeSharedPreferences())
        val old = state.save(100L, 7, 5)
        val current = state.save(900L, 7, 15)
        assertFalse(state.acceptsDelivery(old, 7, 15, 1_000L))
        assertFalse(state.acceptsDelivery(current, 7, 15, 899L))
        assertFalse(state.acceptsDelivery(current, 8, 15, 1_000L))
        assertFalse(state.acceptsDelivery(current, 7, 5, 1_000L))
        assertTrue(state.acceptsDelivery(current, 7, 15, 900L))
        state.clear()
        assertFalse(state.acceptsDelivery(current, 7, 15, 1_000L))
    }

    @Test fun `legacy delivery is allowed only before new protocol initialization`() {
        val state = EnforcementAlarmState(FakeSharedPreferences())
        assertTrue(state.acceptsDelivery(null, 7, 5, 1_000L))
        state.save(900L, 7, 5)
        assertFalse(state.acceptsDelivery(null, 7, 5, 1_000L))
        state.clear()
        assertFalse(state.acceptsDelivery(null, 7, 5, 1_000L))
    }

    @Test fun `legacy record with timing metadata cannot justify an early or mismatched tick`() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putLong("enforcement_alarm_elapsed_deadline", 900L)
            .putInt("enforcement_alarm_boot_count", 7)
            .putInt("enforcement_alarm_interval_minutes", 5).commit()
        val state = EnforcementAlarmState(prefs)
        assertFalse(state.acceptsDelivery(null, 7, 5, 899L))
        assertFalse(state.acceptsDelivery(null, 7, 15, 1_000L))
        assertTrue(state.acceptsDelivery(null, 7, 5, 900L))
    }

    @Test fun `failed durable save is retried even when memory already has the new deadline`() {
        val state = EnforcementAlarmState(failedDisk(FakeSharedPreferences()))
        repeat(2) {
            assertTrue(runCatching { state.save(123_456L, 7, 5) }.exceptionOrNull() is IllegalStateException)
        }
    }

    @Test fun `failed durable clear is retried even when memory no longer has the deadline`() {
        val prefs = FakeSharedPreferences()
        EnforcementAlarmState(prefs).save(123_456L, 7, 5)
        val state = EnforcementAlarmState(failedDisk(prefs))
        repeat(2) {
            assertTrue(runCatching { state.clear() }.exceptionOrNull() is IllegalStateException)
        }
    }

    private fun failedDisk(prefs: SharedPreferences) = object : SharedPreferences by prefs {
        override fun edit(): SharedPreferences.Editor {
            val editor = prefs.edit()
            return object : SharedPreferences.Editor by editor {
                override fun putLong(key: String, value: Long): SharedPreferences.Editor {
                    editor.putLong(key, value)
                    return this
                }
                override fun putInt(key: String, value: Int): SharedPreferences.Editor {
                    editor.putInt(key, value)
                    return this
                }
                override fun putString(key: String, value: String?): SharedPreferences.Editor {
                    editor.putString(key, value)
                    return this
                }
                override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
                    editor.putBoolean(key, value)
                    return this
                }
                override fun remove(key: String): SharedPreferences.Editor {
                    editor.remove(key)
                    return this
                }
                override fun commit(): Boolean {
                    // Android publishes to memory before a disk commit reports failure.
                    editor.commit()
                    return false
                }
            }
        }
    }
}

package com.princeyadav.grayout.fakes

import android.content.SharedPreferences

/**
 * In-memory [SharedPreferences] implementation for JVM unit tests.
 *
 * Backs the real [com.princeyadav.grayout.service.EnforcementPrefs] and
 * [com.princeyadav.grayout.service.ExclusionPrefs] in tests — construct the
 * real pref wrapper with an instance of this fake instead of wiring a separate
 * FakeEnforcementPrefs / FakeExclusionPrefs. This keeps behavior parity with
 * production: the prefs wrapper's edit/commit code runs unchanged.
 *
 * Caveats:
 * - [apply] and [commit] are synchronous; production [apply] is async. Tests
 *   that rely on the async write window will not reproduce here — prefer
 *   behavioral assertions over write-ordering ones.
 * - [registerOnSharedPreferenceChangeListener] is a no-op; no listener
 *   notifications are dispatched.
 */
class FakeSharedPreferences : SharedPreferences {
    private val store = mutableMapOf<String, Any?>()

    override fun getAll(): Map<String, *> = store.toMap()

    override fun getString(key: String, defValue: String?): String? =
        store[key] as? String ?: defValue

    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?): MutableSet<String>? =
        (store[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun getInt(key: String, defValue: Int): Int = store[key] as? Int ?: defValue

    override fun getLong(key: String, defValue: Long): Long = store[key] as? Long ?: defValue

    override fun getFloat(key: String, defValue: Float): Float = store[key] as? Float ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        store[key] as? Boolean ?: defValue

    override fun contains(key: String): Boolean = store.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor()

    override fun registerOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        // No-op: tests do not rely on listener notifications.
    }

    override fun unregisterOnSharedPreferenceChangeListener(
        listener: SharedPreferences.OnSharedPreferenceChangeListener?,
    ) {
        // No-op
    }

    private inner class FakeEditor : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removals = mutableSetOf<String>()
        private var clear = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putStringSet(
            key: String,
            values: MutableSet<String>?,
        ): SharedPreferences.Editor = apply { pending[key] = values?.toSet() }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun remove(key: String): SharedPreferences.Editor =
            apply { removals.add(key) }

        override fun clear(): SharedPreferences.Editor = apply { clear = true }

        override fun commit(): Boolean {
            flush()
            return true
        }

        override fun apply() {
            flush()
        }

        private fun flush() {
            if (clear) store.clear()
            removals.forEach { store.remove(it) }
            pending.forEach { (k, v) -> if (v == null) store.remove(k) else store[k] = v }
            pending.clear()
            removals.clear()
            clear = false
        }
    }
}

package com.princeyadav.grayout.service

import android.content.SharedPreferences

class EnforcementPrefs(private val prefs: SharedPreferences) {

    fun getInterval(): Int =
        prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun setInterval(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply()
    }

    companion object {
        const val PREFS_NAME = "grayout_prefs"
        /** Minutes offered by Home and cycled by the tile, in order. 0 is off. */
        val INTERVAL_OPTIONS = listOf(0, 1, 5, 10, 15, 30)
        private const val KEY_INTERVAL = "enforcement_interval_minutes"
        private const val DEFAULT_INTERVAL = 0
    }
}

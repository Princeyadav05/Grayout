package com.princeyadav.grayout.service

import android.content.SharedPreferences

class EnforcementPrefs(private val prefs: SharedPreferences) {

    fun getInterval(): Int =
        prefs.getInt(KEY_INTERVAL, DEFAULT_INTERVAL)

    fun setInterval(minutes: Int) {
        prefs.edit().putInt(KEY_INTERVAL, minutes).apply()
    }

    /** "Only during schedules": outside a schedule window, automatic restore leaves grayscale off. */
    fun isOnlyDuringSchedules(): Boolean = prefs.getBoolean(KEY_ONLY_DURING_SCHEDULES, false)

    fun setOnlyDuringSchedules(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ONLY_DURING_SCHEDULES, enabled).apply()
    }

    /** True once the user answered the in-app notification prompt (either button), so it never nags. */
    fun isNotificationPromptDone(): Boolean = prefs.getBoolean(KEY_NOTIFICATION_PROMPT_DONE, false)

    fun setNotificationPromptDone() {
        prefs.edit().putBoolean(KEY_NOTIFICATION_PROMPT_DONE, true).apply()
    }

    companion object {
        const val PREFS_NAME = "grayout_prefs"
        /** Minutes offered by Home and cycled by the tile, in order. 0 is off. */
        val INTERVAL_OPTIONS = listOf(0, 1, 5, 10, 15, 30)
        private const val KEY_INTERVAL = "enforcement_interval_minutes"
        private const val DEFAULT_INTERVAL = 0
        private const val KEY_NOTIFICATION_PROMPT_DONE = "notification_prompt_done"
        private const val KEY_ONLY_DURING_SCHEDULES = "restore_only_during_schedules"
    }
}

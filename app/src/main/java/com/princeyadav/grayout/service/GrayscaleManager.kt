package com.princeyadav.grayout.service

import android.content.ContentResolver
import android.provider.Settings

class GrayscaleManager(private val contentResolver: ContentResolver) {

    fun isGrayscaleEnabled(): Boolean {
        return Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0) == 1
    }

    fun setGrayscale(enabled: Boolean) {
        if (isGrayscaleEnabled() == enabled) return
        if (enabled) {
            Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, 1)
            Settings.Secure.putInt(contentResolver, DALTONIZER_MODE, 0)
        } else {
            Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, 0)
            Settings.Secure.putInt(contentResolver, DALTONIZER_MODE, -1)
        }
    }

    fun isAccessibilityServiceEnabled(packageName: String): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(":").any { it.startsWith("$packageName/") }
    }

    companion object {
        private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER_MODE = "accessibility_display_daltonizer"
    }
}

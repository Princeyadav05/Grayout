package com.princeyadav.grayout.service

import android.content.ContentResolver
import android.net.Uri
import android.provider.Settings

class GrayscaleManager(private val contentResolver: ContentResolver) : GrayscaleController {

    override fun isGrayscaleEnabled(): Boolean {
        return Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0) == 1
    }

    override fun setGrayscale(enabled: Boolean): Boolean {
        if (isGrayscaleEnabled() == enabled) return true
        return try {
            if (enabled) {
                Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, 1)
                Settings.Secure.putInt(contentResolver, DALTONIZER_MODE, 0)
            } else {
                Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, 0)
                Settings.Secure.putInt(contentResolver, DALTONIZER_MODE, -1)
            }
            true
        } catch (_: SecurityException) {
            false
        }
    }

    override fun isAccessibilityServiceEnabled(packageName: String): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(":").any { it.startsWith("$packageName/") }
    }

    override fun canWriteSecureSettings(): Boolean = try {
        val current = Settings.Secure.getInt(contentResolver, DALTONIZER_ENABLED, 0)
        Settings.Secure.putInt(contentResolver, DALTONIZER_ENABLED, current)
        true
    } catch (_: SecurityException) {
        false
    }

    companion object {
        private const val DALTONIZER_ENABLED = "accessibility_display_daltonizer_enabled"
        private const val DALTONIZER_MODE = "accessibility_display_daltonizer"
        val DALTONIZER_ENABLED_URI: Uri = Settings.Secure.getUriFor(DALTONIZER_ENABLED)
    }
}

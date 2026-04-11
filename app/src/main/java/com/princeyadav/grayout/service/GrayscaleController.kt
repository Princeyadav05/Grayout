package com.princeyadav.grayout.service

/**
 * Abstraction over grayscale state management.
 *
 * Implemented by [GrayscaleManager] in production. Test fakes implement this
 * interface directly without touching Settings.Secure.
 */
interface GrayscaleController {
    fun isGrayscaleEnabled(): Boolean
    fun setGrayscale(enabled: Boolean): Boolean
    fun isAccessibilityServiceEnabled(packageName: String): Boolean
    fun canWriteSecureSettings(): Boolean
}

package com.princeyadav.grayout.service

import android.content.SharedPreferences

class ExclusionPrefs(private val prefs: SharedPreferences) {

    fun getExcludedPackages(): Set<String> =
        prefs.getStringSet(KEY_EXCLUDED_PACKAGES, emptySet()) ?: emptySet()

    fun setExcludedPackages(packages: Set<String>) {
        prefs.edit().putStringSet(KEY_EXCLUDED_PACKAGES, packages).apply()
    }

    fun addExcludedPackage(packageName: String) {
        val current = HashSet(getExcludedPackages())
        current.add(packageName)
        setExcludedPackages(current)
    }

    fun removeExcludedPackage(packageName: String) {
        val current = HashSet(getExcludedPackages())
        current.remove(packageName)
        setExcludedPackages(current)
    }

    fun isExcluded(packageName: String): Boolean =
        getExcludedPackages().contains(packageName)

    fun isExcludedAppActive(): Boolean =
        prefs.getBoolean(KEY_EXCLUDED_APP_ACTIVE, false)

    fun setExcludedAppActive(active: Boolean) {
        prefs.edit().putBoolean(KEY_EXCLUDED_APP_ACTIVE, active).apply()
    }

    fun getExcludedCount(): Int = getExcludedPackages().size

    fun wasGrayscaleOnBeforeExclusion(): Boolean =
        prefs.getBoolean(KEY_WAS_GRAYSCALE_ON, false)

    fun setWasGrayscaleOnBeforeExclusion(wasOn: Boolean) {
        prefs.edit().putBoolean(KEY_WAS_GRAYSCALE_ON, wasOn).apply()
    }

    fun clearExclusionState() {
        prefs.edit()
            .putBoolean(KEY_EXCLUDED_APP_ACTIVE, false)
            .putBoolean(KEY_WAS_GRAYSCALE_ON, false)
            .apply()
    }

    companion object {
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_EXCLUDED_APP_ACTIVE = "excluded_app_active"
        private const val KEY_WAS_GRAYSCALE_ON = "was_grayscale_on_before_exclusion"
    }
}

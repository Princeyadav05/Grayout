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

    companion object {
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_EXCLUDED_APP_ACTIVE = "excluded_app_active"
    }
}

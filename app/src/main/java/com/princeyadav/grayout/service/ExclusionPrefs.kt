package com.princeyadav.grayout.service

import android.content.SharedPreferences
import android.content.pm.ApplicationInfo

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

    /** Drops packages that are no longer installed. Returns whether any were removed. */
    fun removeUninstalledPackages(isInstalled: (String) -> Boolean): Boolean {
        val current = getExcludedPackages()
        val kept = current.filterTo(HashSet(), isInstalled)
        if (kept.size == current.size) return false
        setExcludedPackages(kept)
        return true
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

    fun isColorRestorePending(): Boolean = prefs.getBoolean(KEY_COLOR_RESTORE_PENDING, false)

    fun setColorRestorePending(pending: Boolean) {
        prefs.edit().putBoolean(KEY_COLOR_RESTORE_PENDING, pending).apply()
    }

    fun clearExclusionState() {
        prefs.edit()
            .putBoolean(KEY_EXCLUDED_APP_ACTIVE, false)
            .putBoolean(KEY_WAS_GRAYSCALE_ON, false)
            .putBoolean(KEY_COLOR_RESTORE_PENDING, false)
            .apply()
    }

    companion object {
        private const val KEY_EXCLUDED_PACKAGES = "excluded_packages"
        private const val KEY_EXCLUDED_APP_ACTIVE = "excluded_app_active"
        private const val KEY_WAS_GRAYSCALE_ON = "was_grayscale_on_before_exclusion"
        private const val KEY_COLOR_RESTORE_PENDING = "exclusion_color_restore_pending"
    }
}

/**
 * Whether an excluded package is still installed. [appFlags] comes from a lookup that
 * includes uninstalled packages (null when PackageManager no longer knows it), so an
 * app on temporarily unmounted storage is kept and only a real uninstall is dropped.
 * An archived app (Android 15) keeps its data and comes back on tap.
 */
internal fun keepsExclusion(appFlags: Int?, isArchived: Boolean): Boolean =
    appFlags != null && (appFlags and ApplicationInfo.FLAG_INSTALLED != 0 || isArchived)

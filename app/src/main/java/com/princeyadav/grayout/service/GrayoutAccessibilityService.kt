package com.princeyadav.grayout.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo

class GrayoutAccessibilityService : AccessibilityService() {

    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var enforcementPrefs: EnforcementPrefs
    private lateinit var grayscaleManager: GrayscaleManager

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE)
        exclusionPrefs = ExclusionPrefs(prefs)
        enforcementPrefs = EnforcementPrefs(prefs)
        grayscaleManager = GrayscaleManager(contentResolver)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Derive the current foreground app from the windows list rather than
        // trusting event.packageName. event.packageName can point to an IME,
        // system dialog, or stale window; using the active TYPE_APPLICATION
        // window gives us the actual app the user is looking at.
        val pkg = currentForegroundAppPackage() ?: return
        if (pkg == "com.princeyadav.grayout") return

        val isExcluded = exclusionPrefs.isExcluded(pkg)
        val wasActive = exclusionPrefs.isExcludedAppActive()

        if (isExcluded && !wasActive) {
            exclusionPrefs.setWasGrayscaleOnBeforeExclusion(grayscaleManager.isGrayscaleEnabled())
            exclusionPrefs.setExcludedAppActive(true)
            grayscaleManager.setGrayscale(false)
        } else if (!isExcluded && wasActive) {
            val wasOn = exclusionPrefs.wasGrayscaleOnBeforeExclusion()
            exclusionPrefs.setExcludedAppActive(false)
            exclusionPrefs.setWasGrayscaleOnBeforeExclusion(false)
            if (wasOn) {
                grayscaleManager.setGrayscale(true)
            } else if (enforcementPrefs.getInterval() > 0) {
                startForegroundService(
                    Intent(this, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_EXCLUSION_ENDED, true)
                )
            }
        }
    }

    private fun currentForegroundAppPackage(): String? {
        val appWindows = windows?.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            ?: return null
        val active = appWindows.firstOrNull { it.isActive } ?: appWindows.firstOrNull() ?: return null
        return active.root?.packageName?.toString()
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        exclusionPrefs.setExcludedAppActive(false)
        super.onDestroy()
    }
}

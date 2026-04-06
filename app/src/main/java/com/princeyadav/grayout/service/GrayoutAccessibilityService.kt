package com.princeyadav.grayout.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class GrayoutAccessibilityService : AccessibilityService() {

    private lateinit var exclusionPrefs: ExclusionPrefs
    private lateinit var grayscaleManager: GrayscaleManager

    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE)
        exclusionPrefs = ExclusionPrefs(prefs)
        grayscaleManager = GrayscaleManager(contentResolver)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return

        if (pkg == "com.princeyadav.grayout") return
        if (pkg == "com.android.systemui") return
        if (pkg == "com.android.launcher" || pkg.startsWith("com.android.launcher")) return

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
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        exclusionPrefs.setExcludedAppActive(false)
        super.onDestroy()
    }
}

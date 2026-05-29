package com.princeyadav.grayout.service

import android.app.AppOpsManager
import android.content.Context
import android.os.Build
import android.os.Process

/**
 * Checks whether the user has granted "Usage access" (`PACKAGE_USAGE_STATS`) to
 * this app. The per-app exclusion feature needs it to read the foreground app
 * via [UsageStatsManager][android.app.usage.UsageStatsManager]. Usage access is
 * never a runtime permission dialog — only the system settings screen
 * (`ACTION_USAGE_ACCESS_SETTINGS`) grants it.
 */
object UsageAccess {

    fun isGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        // The op MUST be checked for THIS app's own identity. Passing any other
        // uid makes the op read MODE_ERRORED/MODE_DEFAULT, so the gate would
        // report "not granted" forever even after the user grants it.
        val uid = Process.myUid()
        val pkg = context.packageName
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // unsafeCheckOpNoThrow is @Deprecated at compileSdk 36 but remains the
            // correct call on API 29-35; the deprecation warning is suppressed here.
            @Suppress("DEPRECATION")
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, uid, pkg)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }
}

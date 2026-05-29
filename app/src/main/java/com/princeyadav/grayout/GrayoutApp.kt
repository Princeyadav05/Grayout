package com.princeyadav.grayout

import android.app.Application
import android.content.Context
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs

class GrayoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reset volatile exclusion state after any process cold start (system kill,
        // force-stop) so a stale flag never leaks into the detector's first poll or
        // GrayoutService's first tick on START_STICKY revival.
        ExclusionPrefs(
            getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        ).clearExclusionState()
    }
}

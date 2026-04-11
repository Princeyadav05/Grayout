package com.princeyadav.grayout

import android.app.Application
import android.content.Context
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs

class GrayoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reset volatile exclusion state after any process cold start (system kill,
        // force-stop) so stale flags don't leak into GrayoutService's first tick
        // on START_STICKY revival. See spec §5.8.
        ExclusionPrefs(
            getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        ).clearExclusionState()
    }
}

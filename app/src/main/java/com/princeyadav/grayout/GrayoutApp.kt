package com.princeyadav.grayout

import android.app.Application
import android.content.Context
import android.os.PowerManager
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.reconcileStrandedExclusion

class GrayoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Reset volatile exclusion state after any process cold start (system kill,
        // force-stop) so a stale flag never leaks into the detector's first poll or
        // GrayoutService's first tick on START_STICKY revival.
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        val exclusionPrefs = ExclusionPrefs(prefs)
        // While the screen is off there is no live excluded session, so also restore
        // grayscale if the excluded app had suppressed a previously-on state — the
        // "wake gray, not colour" contract (mirrors GrayoutService's screen-off
        // pre-gray). While the screen is ON we cannot tell a stranded flag from a
        // live session (the user may be inside the excluded app right now), so we
        // only clear and let the detector re-establish state rather than flashing
        // gray over an app in use.
        if (getSystemService(PowerManager::class.java).isInteractive) {
            exclusionPrefs.clearExclusionState()
        } else {
            reconcileStrandedExclusion(exclusionPrefs, GrayscaleManager(this))
        }
    }
}

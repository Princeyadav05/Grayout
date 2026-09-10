package com.princeyadav.grayout

import android.app.Application
import android.content.Context
import android.os.PowerManager
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.reconcileExclusionOnStart

class GrayoutApp : Application() {
    override fun onCreate() {
        super.onCreate()
        val prefs = getSharedPreferences(EnforcementPrefs.PREFS_NAME, Context.MODE_PRIVATE)
        reconcileExclusionOnStart(
            ExclusionPrefs(prefs),
            GrayscaleManager(this),
            getSystemService(PowerManager::class.java).isInteractive,
        )
    }
}

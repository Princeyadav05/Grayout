package com.princeyadav.grayout.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.R

class GrayscaleToggleTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val manager = GrayscaleManager(this)
        val wrote = manager.setGrayscale(!manager.isGrayscaleEnabled())
        // A discarded failure left the tile dead and silent before ADB setup. If the
        // write failed because WRITE_SECURE_SETTINGS is missing, open the app (Home),
        // whose attention badge routes on to ADB setup. A rare non-permission write
        // failure just refreshes.
        if (!wrote && !hasWritePermission()) {
            launchSetup()
            return
        }
        updateTile()
    }

    // Read-only grant check, not GrayscaleManager.canWriteSecureSettings(): this runs
    // on every onStartListening (QS panel open), and the write-probe would touch
    // Settings.Secure on that hot path. The grant status is enough to pick the state.
    private fun hasWritePermission(): Boolean =
        checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private fun updateTile() {
        val tile = qsTile ?: return
        val manager = GrayscaleManager(this)
        // Kept STATE_INACTIVE (not STATE_UNAVAILABLE) when permission is missing so the
        // tile stays clickable and onClick can route to setup; the subtitle is the cue.
        val canWrite = hasWritePermission()
        val isEnabled = canWrite && manager.isGrayscaleEnabled()
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Grayscale"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                !canWrite -> "Setup needed"
                isEnabled -> "On"
                else -> "Off"
            }
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    private fun launchSetup() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // startActivityAndCollapse(Intent) throws on API 34+; use the PendingIntent overload there.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
            )
        } else {
            // The PendingIntent overload only exists on API 34+; the Intent overload is
            // the only option on 26-33, deprecated-in-34 notwithstanding.
            @Suppress("DEPRECATION")
            @SuppressLint("StartActivityAndCollapseDeprecated")
            startActivityAndCollapse(intent)
        }
    }
}

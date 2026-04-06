package com.princeyadav.grayout.service

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.princeyadav.grayout.R

class GrayscaleToggleTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val manager = GrayscaleManager(contentResolver)
        val isEnabled = manager.isGrayscaleEnabled()
        manager.setGrayscale(!isEnabled)
        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val isEnabled = GrayscaleManager(contentResolver).isGrayscaleEnabled()
        tile.state = if (isEnabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Grayscale"
        tile.subtitle = if (isEnabled) "On" else "Off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_grayout_foreground)
        tile.updateTile()
    }
}

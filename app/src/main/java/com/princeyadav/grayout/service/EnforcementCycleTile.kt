package com.princeyadav.grayout.service

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.princeyadav.grayout.R

class EnforcementCycleTile : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        val prefs = EnforcementPrefs(
            applicationContext.getSharedPreferences(
                EnforcementPrefs.PREFS_NAME,
                MODE_PRIVATE,
            )
        )
        val current = prefs.getInterval()
        val newInterval = nextInterval(current)
        prefs.setInterval(newInterval)

        val intent = Intent(this, GrayoutService::class.java)
            .putExtra(GrayoutService.EXTRA_INTERVAL, newInterval)
        startForegroundService(intent)

        updateTile()
    }

    private fun updateTile() {
        val tile = qsTile ?: return
        val prefs = EnforcementPrefs(
            applicationContext.getSharedPreferences(
                EnforcementPrefs.PREFS_NAME,
                MODE_PRIVATE,
            )
        )
        val interval = prefs.getInterval()
        tile.state = if (interval > 0) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Enforcement"
        tile.subtitle = if (interval > 0) "Every ${interval}m" else "Off"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile)
        tile.updateTile()
    }

    companion object {
        private val INTERVALS = intArrayOf(0, 5, 15, 30)

        private fun nextInterval(current: Int): Int {
            val index = INTERVALS.indexOf(current)
            return if (index == -1) INTERVALS[0]
            else INTERVALS[(index + 1) % INTERVALS.size]
        }
    }
}

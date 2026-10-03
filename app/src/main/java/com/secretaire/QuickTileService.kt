package com.secretaire

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Tuile des réglages rapides pour activer / mettre en pause la lecture. */
class QuickTileService : TileService() {

    override fun onStartListening() = refresh()

    override fun onClick() {
        Toggle.flip(this)
        refresh()
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val enabled = Settings(this).enabled
        tile.state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = "Secrétaire"
        tile.icon = Icon.createWithResource(this, R.drawable.ic_notification)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (enabled) "Lecture activée" else "En pause"
        }
        tile.updateTile()
    }
}

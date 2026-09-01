package com.feldman.clock.app.tiles

import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.feldman.clock.R
import com.feldman.clock.ui.standby.StandbyActivity

/** Quick Settings tile that drops straight into standby, without waiting for a dock or charger. */
class StandbyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            label = getString(R.string.shortcut_start_standby_short)
            icon = Icon.createWithResource(this@StandbyTileService, R.drawable.shortcut_standby)
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, StandbyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        // Collapses the shade and unlocks first if needed; plain startActivity() from a tile is
        // ignored on modern Android.
        startActivityAndCollapse(
            android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
        )
    }
}

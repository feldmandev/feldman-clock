package com.feldman.clock.app.tiles

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.text.format.DateUtils
import com.feldman.clock.R
import com.feldman.clock.core.data.DataModel

/**
 * Quick Settings tile that starts and pauses the stopwatch.
 *
 * The subtitle shows elapsed time, but a collapsed tile only redraws when the panel is open,
 * so it is a snapshot rather than a live counter.
 */
class StopwatchTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        val stopwatch = DataModel.dataModel.stopwatch
        if (stopwatch.isRunning) {
            DataModel.dataModel.pauseStopwatch()
        } else {
            DataModel.dataModel.startStopwatch()
        }
        render()
    }

    private fun render() {
        val tile = qsTile ?: return
        val stopwatch = DataModel.dataModel.stopwatch
        tile.state = if (stopwatch.isRunning) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.stopwatch_label)
        tile.icon = Icon.createWithResource(
            this,
            if (stopwatch.isRunning) R.drawable.ic_fab_pause else R.drawable.ic_fab_play
        )
        tile.subtitle = when {
            stopwatch.isReset -> getString(R.string.stopwatch_tile_start)
            else -> DateUtils.formatElapsedTime(stopwatch.totalTime / 1000L)
        }
        tile.updateTile()
    }
}

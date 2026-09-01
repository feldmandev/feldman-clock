package com.feldman.clock.app.tiles

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.feldman.clock.R
import com.feldman.clock.app.MainActivity
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.Timer
import com.feldman.clock.timer.TimerService

/**
 * Quick Settings tile for the timer.
 *
 * With a timer running it pauses, and with one paused it resumes — the single most useful
 * action from the shade. With no timer at all it opens the timer screen, since picking a
 * duration needs the UI.
 */
class TimerTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        render()
    }

    override fun onClick() {
        when (val timer = activeTimer()) {
            null -> openTimers()
            else -> {
                if (timer.isRunning) {
                    DataModel.dataModel.pauseTimer(timer)
                } else {
                    DataModel.dataModel.startTimer(timer)
                }
                render()
            }
        }
    }

    /** The timer the tile acts on: the running one, else the first paused one. */
    private fun activeTimer(): Timer? {
        val timers = DataModel.dataModel.timers
        return timers.firstOrNull { it.isRunning } ?: timers.firstOrNull { it.isPaused }
    }

    private fun openTimers() {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(TimerService.ACTION_SHOW_TIMER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivityAndCollapse(
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
    }

    private fun render() {
        val tile = qsTile ?: return
        val timer = activeTimer()
        tile.state = if (timer?.isRunning == true) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.timer_tile_label)
        tile.icon = Icon.createWithResource(
            this,
            when {
                timer == null -> R.drawable.ic_hourglass
                timer.isRunning -> R.drawable.ic_fab_pause
                else -> R.drawable.ic_fab_play
            }
        )
        tile.subtitle = timer?.let { formatRemaining(it.remainingTime) }
            ?: getString(R.string.timer_tile_none)
        tile.updateTile()
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = (millis / 1000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }
}

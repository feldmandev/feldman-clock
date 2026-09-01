package com.feldman.clock.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.text.format.DateUtils
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.feldman.clock.R
import com.feldman.clock.app.MainActivity
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.stopwatch.StopwatchService
import kotlin.math.min
import kotlin.math.roundToInt

/** Home-screen controls and live elapsed time for the app's single stopwatch. */
class StopwatchWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) = update(context, appWidgetManager, appWidgetIds)

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        update(context, appWidgetManager, intArrayOf(appWidgetId))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_TOGGLE -> {
                if (DataModel.dataModel.stopwatch.isRunning) {
                    DataModel.dataModel.pauseStopwatch()
                } else {
                    DataModel.dataModel.startStopwatch()
                }
            }
            ACTION_RESET -> {
                DataModel.dataModel.resetStopwatch()
            }
            ACTION_LAP -> {
                DataModel.dataModel.addLap()
            }
            else -> super.onReceive(context, intent)
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val stopwatch = DataModel.dataModel.stopwatch
        ids.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, views(context, appWidgetId, stopwatch))
        }
    }

    private fun views(
        context: Context,
        appWidgetId: Int,
        stopwatch: com.feldman.clock.core.data.Stopwatch
    ): RemoteViews {
        val faceSizePx = faceSizePx(context, appWidgetId)
        val fabSizePx = (faceSizePx * 0.32f).roundToInt().coerceAtLeast(36)
        val fabPaddingPx = (fabSizePx * 0.28f).roundToInt()
        return RemoteViews(context.packageName, R.layout.widget_stopwatch).apply {
            setViewLayoutWidth(R.id.stopwatch_widget_face, faceSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutHeight(R.id.stopwatch_widget_face, faceSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutWidth(R.id.stopwatch_widget_toggle, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutHeight(R.id.stopwatch_widget_toggle, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutWidth(R.id.stopwatch_widget_reset, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutHeight(R.id.stopwatch_widget_reset, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewPadding(
                R.id.stopwatch_widget_toggle,
                fabPaddingPx,
                fabPaddingPx,
                fabPaddingPx,
                fabPaddingPx
            )
            setViewPadding(
                R.id.stopwatch_widget_reset,
                fabPaddingPx,
                fabPaddingPx,
                fabPaddingPx,
                fabPaddingPx
            )
            setTextViewTextSize(
                R.id.stopwatch_widget_time,
                TypedValue.COMPLEX_UNIT_PX,
                faceSizePx * 0.26f
            )
            // Chronometer derives its text from `elapsedRealtime() - base`, and truncates the
            // result to whole seconds — the same floor-to-second rule the stopwatch screen uses.
            val base = SystemClock.elapsedRealtime() - stopwatch.totalTime
            setChronometer(R.id.stopwatch_widget_time, base, null, stopwatch.isRunning)
            if (!stopwatch.isRunning) {
                // setChronometer() records a setBase() action, and the host replays every
                // recorded action each time it re-applies these views — on launcher restart,
                // relayout, or a memory trim — without asking us for fresh ones. setBase()
                // recomputes the text against the *current* elapsedRealtime, so a paused
                // widget would silently grow by however long the host had been idle.
                //
                // Overwriting the text afterwards pins it: the replay still re-bases the
                // chronometer, then immediately overwrites it with this fixed string.
                setTextViewText(R.id.stopwatch_widget_time, formatElapsed(stopwatch.totalTime))
            }
            setImageViewResource(
                R.id.stopwatch_widget_toggle,
                if (stopwatch.isRunning) R.drawable.ic_fab_pause else R.drawable.ic_fab_play
            )
            setViewVisibility(
                R.id.stopwatch_widget_reset,
                if (stopwatch.isReset) View.GONE else View.VISIBLE
            )
            setImageViewResource(
                R.id.stopwatch_widget_reset,
                if (stopwatch.isRunning) R.drawable.ic_stopwatch_lap else R.drawable.ic_reset
            )
            setContentDescription(
                R.id.stopwatch_widget_reset,
                context.getString(
                    if (stopwatch.isRunning) R.string.stopwatch_widget_lap else R.string.stopwatch_widget_reset
                )
            )
            setOnClickPendingIntent(
                R.id.stopwatch_widget_reset,
                actionPendingIntent(context, if (stopwatch.isRunning) ACTION_LAP else ACTION_RESET)
            )
            setViewVisibility(
                R.id.stopwatch_widget_lap,
                if (stopwatch.isRunning) View.VISIBLE else View.GONE
            )
            if (stopwatch.isRunning) {
                setTextViewText(
                    R.id.stopwatch_widget_lap,
                    context.getString(R.string.sw_notification_lap_number, DataModel.dataModel.laps.size + 1)
                )
            }
            setOnClickPendingIntent(R.id.stopwatch_widget_root, openStopwatchPendingIntent(context))
            setOnClickPendingIntent(R.id.stopwatch_widget_toggle, actionPendingIntent(context, ACTION_TOGGLE))
        }
    }

    /**
     * Formats a paused elapsed time exactly as the running [android.widget.Chronometer] would,
     * so pausing never changes the number on screen.
     */
    private fun formatElapsed(millis: Long): String =
        DateUtils.formatElapsedTime((millis / 1000L).coerceAtLeast(0L))

    private fun faceSizePx(context: Context, appWidgetId: Int): Int {
        val options = AppWidgetManager.getInstance(context)?.getAppWidgetOptions(appWidgetId)
        val density = context.resources.displayMetrics.density
        val widthPx = ((options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110) ?: 110) * density)
            .roundToInt()
        val heightPx = ((options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 110) ?: 110) * density)
            .roundToInt()
        return min(widthPx, heightPx)
    }

    private fun actionPendingIntent(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, StopwatchWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openStopwatchPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(StopwatchService.ACTION_SHOW_STOPWATCH)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val ACTION_TOGGLE = "com.feldman.clock.action.STOPWATCH_WIDGET_TOGGLE"
        private const val ACTION_RESET = "com.feldman.clock.action.STOPWATCH_WIDGET_RESET"
        private const val ACTION_LAP = "com.feldman.clock.action.STOPWATCH_WIDGET_LAP"

        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, StopwatchWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                StopwatchWidgetProvider().update(context, manager, ids)
            }
        }
    }
}

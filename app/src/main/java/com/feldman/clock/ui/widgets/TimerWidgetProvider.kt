package com.feldman.clock.ui.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.feldman.clock.R
import com.feldman.clock.app.MainActivity
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.Timer
import com.feldman.clock.timer.TimerService
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Home-screen countdown for the timer the user is most likely to care about: the running one,
 * or failing that the first paused one.
 */
class TimerWidgetProvider : AppWidgetProvider() {

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
        val timer = activeTimer()
        when (intent.action) {
            ACTION_TOGGLE -> timer?.let {
                if (it.isRunning) DataModel.dataModel.pauseTimer(it)
                else DataModel.dataModel.startTimer(it)
            }
            ACTION_ADD_MINUTE -> timer?.let { DataModel.dataModel.addCustomTimeToTimer(it) }
            else -> super.onReceive(context, intent)
        }
        if (intent.action == ACTION_TOGGLE || intent.action == ACTION_ADD_MINUTE) {
            updateAll(context)
        }
    }

    private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val timer = activeTimer()
        ids.forEach { appWidgetId ->
            manager.updateAppWidget(appWidgetId, views(context, appWidgetId, timer))
        }
    }

    private fun views(context: Context, appWidgetId: Int, timer: Timer?): RemoteViews {
        val faceSizePx = faceSizePx(context, appWidgetId)
        val fabSizePx = (faceSizePx * 0.32f).roundToInt().coerceAtLeast(36)
        val fabPaddingPx = (fabSizePx * 0.28f).roundToInt()
        val running = timer?.isRunning == true

        return RemoteViews(context.packageName, R.layout.widget_timer).apply {
            setViewLayoutWidth(R.id.timer_widget_face, faceSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            setViewLayoutHeight(R.id.timer_widget_face, faceSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
            listOf(R.id.timer_widget_toggle, R.id.timer_widget_add).forEach { id ->
                setViewLayoutWidth(id, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setViewLayoutHeight(id, fabSizePx.toFloat(), TypedValue.COMPLEX_UNIT_PX)
                setViewPadding(id, fabPaddingPx, fabPaddingPx, fabPaddingPx, fabPaddingPx)
            }
            setTextViewTextSize(
                R.id.timer_widget_time,
                TypedValue.COMPLEX_UNIT_PX,
                faceSizePx * 0.24f
            )

            val remaining = timer?.remainingTime ?: 0L
            // Counting down, so the base is the moment the timer reaches zero.
            setChronometer(
                R.id.timer_widget_time,
                SystemClock.elapsedRealtime() + remaining,
                null,
                running
            )
            setChronometerCountDown(R.id.timer_widget_time, true)
            if (!running) {
                // setChronometer() records a setBase() action that the host replays on every
                // re-apply, recomputing the text against the current elapsedRealtime. For a
                // paused timer that would make the countdown drain while nothing is running,
                // so pin the text with a value that replays identically. Same reason as
                // StopwatchWidgetProvider.
                setTextViewText(R.id.timer_widget_time, formatRemaining(remaining))
            }

            setImageViewResource(
                R.id.timer_widget_toggle,
                if (running) R.drawable.ic_fab_pause else R.drawable.ic_fab_play
            )
            setViewVisibility(
                R.id.timer_widget_toggle,
                if (timer == null) View.GONE else View.VISIBLE
            )
            setViewVisibility(
                R.id.timer_widget_add,
                if (timer == null) View.GONE else View.VISIBLE
            )

            val label = timer?.label
            setViewVisibility(
                R.id.timer_widget_label,
                if (label.isNullOrBlank()) View.GONE else View.VISIBLE
            )
            if (!label.isNullOrBlank()) {
                setTextViewText(R.id.timer_widget_label, label)
            }

            setOnClickPendingIntent(R.id.timer_widget_root, openTimersPendingIntent(context))
            setOnClickPendingIntent(R.id.timer_widget_toggle, actionPendingIntent(context, ACTION_TOGGLE))
            setOnClickPendingIntent(R.id.timer_widget_add, actionPendingIntent(context, ACTION_ADD_MINUTE))
        }
    }

    /** Matches the countdown Chronometer, which truncates to whole seconds. */
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
            Intent(context, TimerWidgetProvider::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun openTimersPendingIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java)
                .setAction(TimerService.ACTION_SHOW_TIMER)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val ACTION_TOGGLE = "com.feldman.clock.action.TIMER_WIDGET_TOGGLE"
        private const val ACTION_ADD_MINUTE = "com.feldman.clock.action.TIMER_WIDGET_ADD_MINUTE"

        /** The running timer, else the first paused one, else nothing. */
        private fun activeTimer(): Timer? {
            val timers = DataModel.dataModel.timers
            return timers.firstOrNull { it.isRunning } ?: timers.firstOrNull { it.isPaused }
        }

        /** Called from TimerModel (Java), so it needs to be a real static. */
        @JvmStatic
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, TimerWidgetProvider::class.java))
            if (ids.isNotEmpty()) {
                TimerWidgetProvider().update(context, manager, ids)
            }
        }
    }
}

package com.feldman.clock.ui.widgets

import android.app.AlarmManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.os.SystemClock

internal object LawnchairWidgetTheme {
    private const val PREFS = "lawnchair_widget_theme"
    private const val DARK = "dark"
    private const val UPDATED_AT = "updated_at"
    private const val ACTIVE_WINDOW_MS = 2 * 60_000L

    fun update(context: Context, dark: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(DARK, dark)
            .putLong(UPDATED_AT, SystemClock.elapsedRealtime())
            .apply()
    }

    fun renderContext(context: Context): Context {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val updatedAt = prefs.getLong(UPDATED_AT, -1L)
        val now = SystemClock.elapsedRealtime()
        if (updatedAt < 0L || updatedAt > now || now - updatedAt > ACTIVE_WINDOW_MS) return context

        val configuration = Configuration(context.resources.configuration).apply {
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or if (
                prefs.getBoolean(DARK, false)
            ) {
                Configuration.UI_MODE_NIGHT_YES
            } else {
                Configuration.UI_MODE_NIGHT_NO
            }
        }
        return context.createConfigurationContext(configuration)
    }
}

/**
 * Digital clock for the home screen: date, the time, and the next alarm.
 *
 * The time is laid out on one line or stacked as hour over minute, chosen automatically from the
 * widget's proportions unless the user pins it - see [WidgetPrefs.ClockLayout].
 *
 * Every entry point calls [ClockWidgetTicker.sync] so the repaint chain gets re-armed by anything
 * that reaches this provider, which is what stops a single missed alarm from freezing the widget.
 */
class DigitalClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            ClockWidgetRenderer.refresh(context, appWidgetId)
        }
        ClockWidgetTicker.sync(context)
    }

    /**
     * Resizing calls this rather than onUpdate. It matters more here than elsewhere: the automatic
     * layout depends on the widget's proportions, so a resize can flip it between one line and
     * stacked.
     */
    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        ClockWidgetRenderer.refresh(context, appWidgetId)
        ClockWidgetTicker.sync(context)
    }

    override fun onEnabled(context: Context) {
        ClockWidgetTicker.sync(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        WidgetPrefs.delete(context, appWidgetIds)
        ClockWidgetTicker.sync(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_LAUNCHER_THEME_REFRESH) {
            if (intent.hasExtra(EXTRA_DARK_THEME)) {
                LawnchairWidgetTheme.update(
                    context,
                    intent.getBooleanExtra(EXTRA_DARK_THEME, false)
                )
            }
            ClockWidgetTicker.refreshAll(context, relayToLawnchair = true)
            ClockWidgetTicker.sync(context)
            return
        }
        super.onReceive(context, intent)

        // The next alarm text is the only part the host cannot refresh by itself, so redraw
        // whenever it - or the formatting the rest of the widget uses - may have changed.
        // These are all exempted implicit broadcasts, so a manifest receiver really does get them.
        if (intent.action in REFRESH_ACTIONS) {
            val appWidgetManager = AppWidgetManager.getInstance(context) ?: return
            val ids = appWidgetManager.getAppWidgetIds(ComponentName(context, javaClass))
            if (ids.isNotEmpty()) {
                onUpdate(context, appWidgetManager, ids)
            }
        }
    }

    private companion object {
        const val ACTION_LAUNCHER_THEME_REFRESH = "com.feldman.clock.action.REFRESH_WIDGETS"
        const val EXTRA_DARK_THEME = "dark_theme"
        val REFRESH_ACTIONS = setOf(
            AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED
        )
    }
}

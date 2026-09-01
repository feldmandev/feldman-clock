package com.feldman.clock.ui.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/**
 * Material You analog clock for the home screen.
 *
 * The hands are drawn by [android.widget.AnalogClock], which ticks on its own inside the launcher.
 * Only the date is ours to keep current, and it is drawn as a bitmap so it can use the Feldman
 * font - see [ClockWidgetRenderer].
 *
 * Every entry point calls [ClockWidgetTicker.sync] so the repaint chain gets re-armed by anything
 * that reaches this provider, which is what stops a single missed alarm from freezing the widget.
 */
class AnalogClockWidgetProvider : AppWidgetProvider() {

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

    /** Resizing calls this rather than onUpdate, so the widget must be repainted here too. */
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
}

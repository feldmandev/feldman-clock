package com.feldman.clock.ui.widgets

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Redraws the bitmap-rendered widget text on a timer.
 *
 * A bitmap cannot tick by itself the way TextClock does, so the date text is repainted every
 * minute. The analog clock itself rotates the combined date-and-second-hand drawable every second.
 *
 * The tick is a chain - each firing arms the next - which means a single missed alarm would
 * otherwise stop the widgets forever. Alarms do get missed: Doze defers them, and replacing the
 * app cancels every pending alarm it owns. Two things guard against that:
 *
 *  - [sync] is called from every provider entry point, so anything that reaches the provider
 *    (an update, a resize, a boot, a time change) re-arms the chain.
 *  - The widgets declare a 30 minute `updatePeriodMillis`, which calls `onUpdate` and therefore
 *    [sync] even if every other path fails. That is the worst-case recovery time.
 *
 * The widgets always use the Feldman font, including in power saving mode, so this chain runs
 * whenever a widget is placed.
 */
object ClockWidgetTicker {

    private const val TAG = "ClockWidgetTicker"
    const val ACTION_TICK = "com.feldman.clock.action.WIDGET_TICK"
    private const val REQUEST_CODE = 0x0C10

    private const val MINUTE_MILLIS = 60_000L

    private val PROVIDERS = listOf(
        AnalogClockWidgetProvider::class.java,
        DigitalClockWidgetProvider::class.java
    )

    /** Every clock widget currently on a home screen. */
    fun placedWidgetIds(context: Context): List<Int> {
        val manager = AppWidgetManager.getInstance(context) ?: return emptyList()
        return PROVIDERS.flatMap { provider ->
            manager.getAppWidgetIds(ComponentName(context, provider)).toList()
        }
    }

    /** Repaints every placed widget. */
    fun refreshAll(context: Context, relayToLawnchair: Boolean = false) {
        placedWidgetIds(context).forEach { appWidgetId ->
            runCatching { ClockWidgetRenderer.refresh(context, appWidgetId, relayToLawnchair) }
                .onFailure { Log.e(TAG, "Could not refresh widget $appWidgetId", it) }
        }
    }

    /**
     * Arms the next repaint. Safe to call as often as you like: it always leaves exactly one
     * pending alarm, and re-arming an already-armed chain simply replaces it.
     */
    fun sync(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val pendingIntent = tickIntent(context)

        if (placedWidgetIds(context).isEmpty()) {
            alarmManager.cancel(pendingIntent)
            return
        }

        val now = System.currentTimeMillis()
        // Fire on the minute boundary so the displayed time flips when it actually changes.
        val nextTick = (now / MINUTE_MILLIS + 1) * MINUTE_MILLIS

        try {
            alarmManager.setExact(AlarmManager.RTC, nextTick, pendingIntent)
        } catch (e: SecurityException) {
            // Exact alarms can be revoked by the user; an inexact alarm still keeps the widget
            // roughly current rather than freezing it.
            Log.w(TAG, "Exact alarm denied, falling back to an inexact tick", e)
            alarmManager.set(AlarmManager.RTC, nextTick, pendingIntent)
        }
    }

    private fun tickIntent(context: Context): PendingIntent {
        val intent = Intent(context, ClockWidgetTickReceiver::class.java).setAction(ACTION_TICK)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

/**
 * Repaints every placed clock widget, then arms the next tick.
 *
 * Also handles boot, which is an exempted implicit broadcast, so the chain restarts after a
 * reboot without waiting for the widgets' own update period.
 */
class ClockWidgetTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        try {
            ClockWidgetTicker.refreshAll(context)
        } finally {
            ClockWidgetTicker.sync(context)
        }
    }
}

package com.feldman.clock.ui.widgets

import android.content.Context
import androidx.core.content.edit
import com.feldman.clock.R

/**
 * Per-widget-instance settings.
 *
 * Every value is keyed by the widget's own id, so two copies of the same widget on the home screen
 * can be configured independently. Kept in a dedicated file rather than the app's default
 * preferences because the lifetime of these entries is tied to the widget, not the app.
 */
object WidgetPrefs {

    private const val FILE = "clock_widget_prefs"

    /** Dial artwork for the analog widget. */
    enum class DialShape(val label: String, val drawableRes: Int) {
        FLOWER("Flower", R.drawable.material_you_analog_clock_dial_flower),
        SUN("Sun", R.drawable.material_you_analog_clock_dial_sun),
        CIRCLE("Circle", R.drawable.widget_analog_dial_circle);

        companion object {
            val labels get() = entries.map { it.label }
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: FLOWER
            fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: FLOWER
        }
    }

    /** Background corner treatment for the digital widget. */
    enum class BackgroundShape(val label: String, val drawableRes: Int) {
        PILL("Pill", R.drawable.material_you_vertical_digital_widget_background),
        ROUNDED("Rounded", R.drawable.widget_digital_background_rounded),
        SQUARE("Square", R.drawable.widget_digital_background_square);

        companion object {
            val labels get() = entries.map { it.label }
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: PILL
            fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: PILL
        }
    }

    /** How the digital widget arranges the time. */
    enum class ClockLayout(val label: String) {
        /** Pick whichever arrangement renders the time larger at the widget's current size. */
        AUTO("Auto"),
        ONE_LINE("One line"),
        STACKED("Stacked"),
        SPLIT_12("12h split"),
        SPLIT_24("24h split");

        companion object {
            val labels get() = entries.map { it.label }
            fun fromLabel(label: String) = entries.firstOrNull { it.label == label } ?: AUTO
            fun fromName(name: String?) = entries.firstOrNull { it.name == name } ?: AUTO
        }
    }

    /** Date patterns offered in the configuration screen, as label to SimpleDateFormat pattern. */
    val DateFormats = listOf(
        "Sat 22" to "EEE d",
        "Sat, Jun 22" to "EEE, MMM d",
        "Saturday" to "EEEE",
        "June 22" to "MMMM d",
        "22 June" to "d MMMM",
        "22/06" to "dd/MM"
    )

    /** Clock face patterns for the digital widget's stacked hour and minute. */
    const val HOUR_FORMAT_12 = "hh"
    const val HOUR_FORMAT_24 = "HH"

    private fun prefs(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun key(appWidgetId: Int, name: String) = "${appWidgetId}_$name"

    fun dialShape(context: Context, appWidgetId: Int): DialShape =
        DialShape.fromName(prefs(context).getString(key(appWidgetId, "dial"), null))

    fun setDialShape(context: Context, appWidgetId: Int, value: DialShape) =
        prefs(context).edit { putString(key(appWidgetId, "dial"), value.name) }

    fun backgroundShape(context: Context, appWidgetId: Int): BackgroundShape =
        BackgroundShape.fromName(prefs(context).getString(key(appWidgetId, "shape"), null))

    fun setBackgroundShape(context: Context, appWidgetId: Int, value: BackgroundShape) =
        prefs(context).edit { putString(key(appWidgetId, "shape"), value.name) }

    fun showDate(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "show_date"), true)

    fun setShowDate(context: Context, appWidgetId: Int, value: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "show_date"), value) }

    fun dateFormat(context: Context, appWidgetId: Int, default: String): String =
        prefs(context).getString(key(appWidgetId, "date_format"), null) ?: default

    fun setDateFormat(context: Context, appWidgetId: Int, value: String) =
        prefs(context).edit { putString(key(appWidgetId, "date_format"), value) }

    fun showSecondHand(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "show_seconds"), true)

    fun setShowSecondHand(context: Context, appWidgetId: Int, value: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "show_seconds"), value) }

    fun showNextAlarm(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "show_next_alarm"), true)

    fun setShowNextAlarm(context: Context, appWidgetId: Int, value: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "show_next_alarm"), value) }

    fun clockLayout(context: Context, appWidgetId: Int): ClockLayout =
        ClockLayout.fromName(prefs(context).getString(key(appWidgetId, "clock_layout"), null))

    fun setClockLayout(context: Context, appWidgetId: Int, value: ClockLayout) =
        prefs(context).edit { putString(key(appWidgetId, "clock_layout"), value.name) }

    fun automaticWidth(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "automatic_width"), true)

    fun setAutomaticWidth(context: Context, appWidgetId: Int, value: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "automatic_width"), value) }

    /** Widens the font's variable width axis, for a chunkier clock face. */
    fun wideText(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "wide_text"), false)

    fun setWideText(context: Context, appWidgetId: Int, value: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "wide_text"), value) }

    /** Drops every entry for widgets the user has removed from the home screen. */
    fun delete(context: Context, appWidgetIds: IntArray) {
        prefs(context).edit {
            appWidgetIds.forEach { id ->
                listOf(
                    "dial", "shape", "show_date", "date_format", "show_seconds",
                    "show_next_alarm", "wide_text", "clock_layout", "automatic_width"
                ).forEach { remove(key(id, it)) }
            }
        }
    }
}

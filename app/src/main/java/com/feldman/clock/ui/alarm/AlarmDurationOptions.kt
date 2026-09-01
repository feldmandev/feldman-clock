package com.feldman.clock.ui.alarm

import com.feldman.clock.settings.PreferencesDefaultValues

/**
 * Option lists shared by the global alarm settings and the per-alarm editor sheet, so that the
 * default a new alarm inherits is always spelled exactly the same way as the override that
 * replaces it.
 */
object AlarmDurationOptions {

    /** Snooze length in minutes. */
    val Snooze = listOf(
        "None" to PreferencesDefaultValues.ALARM_SNOOZE_DURATION_DISABLED,
        "1 minute" to 1,
        "3 minutes" to 3,
        "5 minutes" to 5,
        "10 minutes" to 10,
        "15 minutes" to 15,
        "20 minutes" to 20,
        "25 minutes" to 25,
        "30 minutes" to 30
    )

    /** How long an alarm may ring before it counts as missed, in seconds. */
    val AutoSilence = listOf(
        "At the end of the ringtone" to PreferencesDefaultValues.TIMEOUT_END_OF_RINGTONE,
        "1 minute" to 60,
        "5 minutes" to 300,
        "10 minutes" to 600,
        "15 minutes" to 900,
        "20 minutes" to 1200,
        "25 minutes" to 1500,
        "30 minutes" to 1800,
        "Never" to PreferencesDefaultValues.TIMEOUT_NEVER
    )

    /** Volume ramp-up time in seconds. */
    val Crescendo = listOf(
        "Off" to 0,
        "5 seconds" to 5,
        "10 seconds" to 10,
        "15 seconds" to 15,
        "20 seconds" to 20,
        "30 seconds" to 30,
        "45 seconds" to 45,
        "60 seconds" to 60
    )

    /** Label for [value], falling back to a generated one for values not in the list. */
    fun List<Pair<String, Int>>.labelFor(value: Int): String =
        firstOrNull { it.second == value }?.first ?: when {
            value % 60 == 0 -> "${value / 60} minutes"
            else -> "$value seconds"
        }

    /** Value for [label], falling back to [fallback] when the label is unknown. */
    fun List<Pair<String, Int>>.valueFor(label: String, fallback: Int): Int =
        firstOrNull { it.first == label }?.second ?: fallback
}

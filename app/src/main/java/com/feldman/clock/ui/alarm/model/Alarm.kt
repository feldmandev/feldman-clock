package com.feldman.clock.ui.alarm.model

import com.feldman.clock.core.data.Weekdays
import com.feldman.clock.settings.PreferencesDefaultValues
import java.util.Calendar

data class AlarmUi(
    val id: Long,
    val hour: Int,
    val minutes: Int,
    val label: String?,
    val enabled: Boolean,
    val daysOfWeek: Weekdays,
    val vibrate: Boolean,
    val year: Int = 0,
    val month: Int = 0,
    val day: Int = 0,
    val ringtoneUri: String? = null,
    val snoozingUntil: String? = null,
    val upcomingInstanceTime: Calendar? = null,
    val pauseStartTime: Long = 0,
    val pauseEndTime: Long = 0,
    /** Pulse the camera flash while this alarm rings. */
    val flash: Boolean = false,
    /** Remove this alarm once it has fired. Only meaningful for non-repeating alarms. */
    val deleteAfterUse: Boolean = false,
    /**
     * Seconds the alarm may ring before it is marked missed, or
     * [PreferencesDefaultValues.TIMEOUT_NEVER] / [PreferencesDefaultValues.TIMEOUT_END_OF_RINGTONE].
     */
    val autoSilenceDuration: Int = PreferencesDefaultValues.DEFAULT_AUTO_SILENCE_DURATION,
    /** Minutes between snoozes, or [PreferencesDefaultValues.ALARM_SNOOZE_DURATION_DISABLED]. */
    val snoozeDuration: Int = PreferencesDefaultValues.DEFAULT_ALARM_SNOOZE_DURATION,
    /** How many times a missed alarm re-notifies, or -1 for unlimited. */
    val missedAlarmRepeatLimit: Int = -1,
    /** Seconds the volume takes to ramp up to [alarmVolume]. 0 disables the ramp. */
    val crescendoDuration: Int = PreferencesDefaultValues.DEFAULT_VOLUME_CRESCENDO_DURATION,
    /** STREAM_ALARM volume index this alarm rings at. */
    val alarmVolume: Int = PreferencesDefaultValues.DEFAULT_ALARM_VOLUME
)

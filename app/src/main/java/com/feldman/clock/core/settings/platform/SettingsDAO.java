/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.settings;

import static android.text.format.DateUtils.HOUR_IN_MILLIS;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;

import static com.feldman.clock.core.data.DataModel.PowerButtonBehavior.DISMISS;
import static com.feldman.clock.core.data.DataModel.PowerButtonBehavior.NOTHING;
import static com.feldman.clock.core.data.DataModel.PowerButtonBehavior.SNOOZE;
import static com.feldman.clock.core.data.DataModel.VolumeButtonBehavior.CHANGE_VOLUME;
import static com.feldman.clock.core.data.DataModel.VolumeButtonBehavior.DISMISS_ALARM;
import static com.feldman.clock.core.data.DataModel.VolumeButtonBehavior.DO_NOTHING;
import static com.feldman.clock.core.data.DataModel.VolumeButtonBehavior.SNOOZE_ALARM;
import static com.feldman.clock.settings.PreferencesDefaultValues.*;
import static com.feldman.clock.settings.PreferencesKeys.*;
import static com.feldman.clock.core.data.Weekdays.Order.MON_TO_SUN;
import static com.feldman.clock.core.data.Weekdays.Order.SAT_TO_FRI;
import static com.feldman.clock.core.data.Weekdays.Order.SUN_TO_SAT;

import static java.util.Calendar.MONDAY;
import static java.util.Calendar.SATURDAY;
import static java.util.Calendar.SUNDAY;

import com.feldman.clock.R;
import com.feldman.clock.core.data.DataModel;
import com.feldman.clock.core.data.DataModel.CitySort;
import com.feldman.clock.core.data.TimeZones;
import com.feldman.clock.core.data.Weekdays;
import com.feldman.clock.core.util.Utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * This class encapsulates the storage of application preferences in {@link SharedPreferences}.
 */
public final class SettingsDAO {

    /**
     * Key to a preference that stores the preferred sort order of world cities.
     */
    private static final String KEY_SORT_PREFERENCE = "sort_preference";

    /**
     * Key to a preference that stores the ringtone of an existing alarm.
     */
    public static final String KEY_SELECTED_ALARM_RINGTONE_URI = "selected_alarm_ringtone_uri";

    /**
     * Key to a preference that stores the global broadcast id.
     */
    private static final String KEY_ALARM_GLOBAL_ID = "intent.extra.alarm.global.id";

    /**
     * Key to a preference that indicates whether restore (of backup and restore) has completed.
     */
    private static final String KEY_RESTORE_BACKUP_FINISHED = "restore_finished";


    /**
     * @return the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    public static int getGlobalIntentId(SharedPreferences prefs) {
        return prefs.getInt(KEY_ALARM_GLOBAL_ID, -1);
    }


    /**
     * Update the id used to discriminate relevant AlarmManager callbacks from defunct ones
     */
    public static void updateGlobalIntentId(SharedPreferences prefs) {
        final int globalId = prefs.getInt(KEY_ALARM_GLOBAL_ID, -1) + 1;
        prefs.edit().putInt(KEY_ALARM_GLOBAL_ID, globalId).apply();
    }

    /**
     * @return an enumerated value indicating the order in which cities are ordered
     */
    public static CitySort getCitySort(SharedPreferences prefs) {
        final int defaultSortOrdinal = CitySort.NAME.ordinal();
        final int citySortOrdinal = prefs.getInt(KEY_SORT_PREFERENCE, defaultSortOrdinal);
        return CitySort.values()[citySortOrdinal];
    }

    /**
     * Adjust the sort order of cities.
     */
    public static void toggleCitySort(SharedPreferences prefs) {
        final CitySort oldSort = getCitySort(prefs);
        final CitySort newSort = oldSort == CitySort.NAME ? CitySort.UTC_OFFSET : CitySort.NAME;
        prefs.edit().putInt(KEY_SORT_PREFERENCE, newSort.ordinal()).apply();
    }

    /**
     * @return sorting of cities by time zone in ascending order, by name or manually.
     */
    public static String getCitySorting(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_clock.xml
        return prefs.getString(KEY_SORT_CITIES, DEFAULT_SORT_CITIES_BY_ASCENDING_TIME_ZONE);
    }

    /**
     * @return {@code true} if a clock for the user's home timezone should be automatically
     * displayed when it doesn't match the current timezone
     */
    public static boolean getAutoShowHomeClock(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_clock.xml
        return prefs.getBoolean(KEY_AUTO_HOME_CLOCK, DEFAULT_AUTO_HOME_CLOCK);
    }

    /**
     * @return the user's home timezone
     */
    public static TimeZone getHomeTimeZone(Context context, SharedPreferences prefs, TimeZone defaultTZ) {
        String timeZoneId = prefs.getString(KEY_HOME_TIME_ZONE, DEFAULT_HOME_TIME_ZONE);

        // If the recorded home timezone is legal, use it.
        final TimeZones timeZones = getTimeZones(context, System.currentTimeMillis());
        if (timeZones.contains(timeZoneId)) {
            return TimeZone.getTimeZone(timeZoneId);
        }

        // No legal home timezone has yet been recorded, attempt to record the default.
        timeZoneId = defaultTZ.getID();
        if (timeZones.contains(timeZoneId)) {
            prefs.edit().putString(KEY_HOME_TIME_ZONE, timeZoneId).apply();
        }

        // The timezone returned here may be valid or invalid. When it matches TimeZone.getDefault()
        // the Home city will not show, regardless of its validity.
        return defaultTZ;
    }

    /**
     * @return the time format: "12", "24", or "system" (default).
     */
    public static String getTimeFormat(SharedPreferences prefs) {
        return prefs.getString(KEY_TIME_FORMAT, "system");
    }

    /**
     * @return the specific date format pattern. Default to "E, MMM d".
     */
    public static String getDateFormat(SharedPreferences prefs) {
        return prefs.getString(KEY_DATE_FORMAT, "E, MMM d");
    }

    /**
     * @return the theme applied.
     */
    public static String getTheme(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getString(KEY_THEME, SYSTEM_THEME);
    }

    /**
     * @return the accent color applied.
     */
    public static String getAccentColor(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getString(KEY_ACCENT_COLOR, DEFAULT_ACCENT_COLOR);
    }

    /**
     * @return {@code true} if auto night accent color is enabled. {@code false} otherwise.
     */
    public static boolean isAutoNightAccentColorEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getBoolean(KEY_AUTO_NIGHT_ACCENT_COLOR, DEFAULT_AUTO_NIGHT_ACCENT_COLOR);
    }

    /**
     * @return the night accent color applied.
     */
    public static String getNightAccentColor(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getString(KEY_NIGHT_ACCENT_COLOR, DEFAULT_NIGHT_ACCENT_COLOR);
    }

    /**
     * @return the dark mode of the applied theme.
     */
    public static String getDarkMode(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getString(KEY_DARK_MODE, DEFAULT_DARK_MODE);
    }

    /**
     * @return {@code true} if the background should be displayed in a view. {@code false} otherwise.
     */
    public static boolean isCardBackgroundDisplayed(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getBoolean(KEY_CARD_BACKGROUND, DEFAULT_CARD_BACKGROUND);
    }

    /**
     * @return {@code true} if the border should be displayed in a view. {@code false} otherwise.
     */
    public static boolean isCardBorderDisplayed(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getBoolean(KEY_CARD_BORDER, DEFAULT_CARD_BORDER);
    }

    /**
     * @return the custom language code.
     */
    public static String getCustomLanguageCode(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getString(KEY_CUSTOM_LANGUAGE_CODE, DEFAULT_SYSTEM_LANGUAGE_CODE);
    }

    public static int getTabToDisplay(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        final String tabToDisplay = prefs.getString(KEY_TAB_TO_DISPLAY, DEFAULT_TAB_TO_DISPLAY);
        return Integer.parseInt(tabToDisplay);
    }

    /**
     * @return {@code true} if the vibrations are enabled for the buttons. {@code false} otherwise.
     */
    public static boolean isVibrationsEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getBoolean(KEY_VIBRATIONS, DEFAULT_VIBRATIONS);
    }

    /**
     * @return {@code true} if the fade transitions are enabled. {@code false} otherwise.
     */
    public static boolean isFadeTransitionsEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_interface_customization.xml
        return prefs.getBoolean(KEY_FADE_TRANSITIONS, DEFAULT_FADE_TRANSITIONS);
    }

    /**
     * @return a value indicating whether analog or digital clocks are displayed on the standby.
     */
    public static DataModel.ClockStyle getStandbyClockStyle(SharedPreferences prefs) {
        DataModel.ClockStyle style = getClockStyle(prefs, KEY_STANDBY_CLOCK_STYLE);
        if (style == DataModel.ClockStyle.DIGITAL) {
            style = DataModel.ClockStyle.DIGITAL_SPLIT_24;
            prefs.edit()
                    .putInt(KEY_STANDBY_CLOCK_STYLE, style.ordinal())
                    .apply();
        }
        return style;
    }

    /**
     * @return a value indicating the color of the clock of the standby
     */
    public static int getStandbyClockColorPicker(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getInt(KEY_STANDBY_CLOCK_COLOR_PICKER, DEFAULT_STANDBY_CUSTOM_COLOR);
    }

    public static int getStandbyHourColorPicker(SharedPreferences prefs) {
        migrateStandbySplitColorOrder(prefs);
        return prefs.getInt(KEY_STANDBY_HOUR_COLOR_PICKER, DEFAULT_STANDBY_HOUR_COLOR);
    }

    public static int getStandbyMinuteColorPicker(SharedPreferences prefs) {
        migrateStandbySplitColorOrder(prefs);
        return prefs.getInt(KEY_STANDBY_MINUTE_COLOR_PICKER, DEFAULT_STANDBY_MINUTE_COLOR);
    }

    private static void migrateStandbySplitColorOrder(SharedPreferences prefs) {
        if (prefs.getBoolean(KEY_STANDBY_SPLIT_COLOR_ORDER_MIGRATED, false)) {
            return;
        }

        int oldHourColor = prefs.getInt(
                KEY_STANDBY_HOUR_COLOR_PICKER,
                DEFAULT_STANDBY_MINUTE_COLOR
        );
        int oldMinuteColor = prefs.getInt(
                KEY_STANDBY_MINUTE_COLOR_PICKER,
                DEFAULT_STANDBY_HOUR_COLOR
        );
        prefs.edit()
                .putInt(KEY_STANDBY_HOUR_COLOR_PICKER, oldMinuteColor)
                .putInt(KEY_STANDBY_MINUTE_COLOR_PICKER, oldHourColor)
                .putBoolean(KEY_STANDBY_SPLIT_COLOR_ORDER_MIGRATED, true)
                .apply();
    }

    /**
     * @return a value indicating the color of the date of the standby
     */
    public static int getStandbyDateColorPicker(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getInt(KEY_STANDBY_DATE_COLOR_PICKER, DEFAULT_STANDBY_CUSTOM_COLOR);
    }

    public static boolean getStandbyShowDate(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_STANDBY_SHOW_DATE, DEFAULT_STANDBY_SHOW_DATE);
    }

    public static String getStandbyDateFormat(SharedPreferences prefs) {
        return prefs.getString(KEY_STANDBY_DATE_FORMAT, DEFAULT_STANDBY_DATE_FORMAT);
    }

    public static int getStandbyDateSizePercent(SharedPreferences prefs) {
        return prefs.getInt(KEY_STANDBY_DATE_SIZE_PERCENT, DEFAULT_STANDBY_DATE_SIZE_PERCENT);
    }

    public static int getStandbyDateSpacingDp(SharedPreferences prefs) {
        return prefs.getInt(KEY_STANDBY_DATE_SPACING_DP, DEFAULT_STANDBY_DATE_SPACING_DP);
    }

    public static boolean getStandbyShowNextAlarm(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_STANDBY_SHOW_NEXT_ALARM, DEFAULT_STANDBY_SHOW_NEXT_ALARM);
    }

    /**
     * @return a value indicating the color of the next alarm of the standby
     */
    public static int getStandbyNextAlarmColorPicker(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getInt(KEY_STANDBY_NEXT_ALARM_COLOR_PICKER, DEFAULT_STANDBY_CUSTOM_COLOR);
    }

    /**
     * @return {@code int} the standby brightness level at night
     */
    public static int getStandbyBrightness(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getInt(KEY_STANDBY_BRIGHTNESS, DEFAULT_STANDBY_BRIGHTNESS);
    }

    /**
     * @return {@code true} if the seconds are displayed on the analog or digital clock in the standby.
     * {@code false} otherwise.
     */
    public static boolean areStandbyClockSecondsDisplayed(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_DISPLAY_STANDBY_CLOCK_SECONDS, DEFAULT_DISPLAY_STANDBY_CLOCK_SECONDS);
    }

    /**
     * @return {@code true} if the standby should show the clock in bold. {@code false} otherwise.
     */
    public static boolean isStandbyDigitalClockInBold(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_DIGITAL_CLOCK_IN_BOLD, DEFAULT_STANDBY_DIGITAL_CLOCK_IN_BOLD);
    }

    /**
     * @return {@code true} if the standby should show the clock in italic. {@code false} otherwise.
     */
    public static boolean isStandbyDigitalClockInItalic(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_DIGITAL_CLOCK_IN_ITALIC, DEFAULT_STANDBY_DIGITAL_CLOCK_IN_ITALIC);
    }

    /**
     * @return {@code true} if the standby should show the date in bold. {@code false} otherwise.
     */
    public static boolean isStandbyDateInBold(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_DATE_IN_BOLD, DEFAULT_STANDBY_DATE_IN_BOLD);
    }

    /**
     * @return {@code true} if the standby should show the date in italic. {@code false} otherwise.
     */
    public static boolean isStandbyDateInItalic(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_DATE_IN_ITALIC, DEFAULT_STANDBY_DATE_IN_ITALIC);
    }

    /**
     * @return {@code true} if the standby should show the next alarm in bold. {@code false} otherwise.
     */
    public static boolean isStandbyNextAlarmInBold(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_NEXT_ALARM_IN_BOLD, DEFAULT_STANDBY_NEXT_ALARM_IN_BOLD);
    }

    /**
     * @return {@code true} if the standby should show the next alarm in italic. {@code false} otherwise.
     */
    public static boolean isStandbyNextAlarmInItalic(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(KEY_STANDBY_NEXT_ALARM_IN_ITALIC, DEFAULT_STANDBY_NEXT_ALARM_IN_ITALIC);
    }

    /**
     * @return the standby rotation mode.
     */
    public static int getStandbyRotationMode(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_ROTATION_MODE,
                DEFAULT_STANDBY_ROTATION_MODE
        );
    }

    /**
     * @return the standby scale percent (0–100).
     */
    public static int getStandbyScalePercent(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_SCALE_PERCENT, DEFAULT_STANDBY_SCALE_PERCENT
        );
    }

    public static int getStandbyClockWeightPercent(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_CLOCK_WEIGHT_PERCENT, DEFAULT_STANDBY_CLOCK_WEIGHT_PERCENT
        );
    }

    public static int getStandbyClockWidthPercent(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_CLOCK_WIDTH_PERCENT, DEFAULT_STANDBY_CLOCK_WIDTH_PERCENT
        );
    }

    public static boolean getStandbyAutomaticClockWidth(SharedPreferences prefs) {
        return prefs.getBoolean(
                KEY_STANDBY_CLOCK_WIDTH_AUTO, DEFAULT_STANDBY_CLOCK_WIDTH_AUTO
        );
    }

    public static int getStandbyClockRoundnessPercent(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_CLOCK_ROUNDNESS_PERCENT, DEFAULT_STANDBY_CLOCK_ROUNDNESS_PERCENT
        );
    }

    public static int getStandbyTimeSpacingDp(SharedPreferences prefs) {
        return prefs.getInt(KEY_STANDBY_TIME_SPACING_DP, DEFAULT_STANDBY_TIME_SPACING_DP);
    }

    /**
     * @return the font weight for the digital standby clock.
     */
    public static int getStandbyFontWeight(SharedPreferences prefs) {
        return prefs.getInt(
                KEY_STANDBY_FONT_WEIGHT,
                DEFAULT_STANDBY_FONT_WEIGHT
        );
    }


    /**
     * @return the uri of the selected ringtone or the {@code defaultUri} if no explicit selection
     * has yet been made.
     */
    public static Uri getTimerRingtoneUri(SharedPreferences prefs, Uri defaultUri) {
        final String uriString = prefs.getString(KEY_TIMER_RINGTONE, null);
        return uriString == null ? defaultUri : Uri.parse(uriString);
    }

    /**
     * @return the duration for which a timer can ring before expiring and being reset.
     */
    public static int getTimerAutoSilenceDuration(SharedPreferences prefs) {
        return prefs.getInt(KEY_TIMER_AUTO_SILENCE_DURATION, DEFAULT_TIMER_AUTO_SILENCE_DURATION);
    }

    /**
     * @return {@code true} if the timer vibrations are enabled. {@code false} otherwise.
     */
    public static boolean isTimerVibrate(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_timer.xml
        return prefs.getBoolean(KEY_TIMER_VIBRATE, DEFAULT_TIMER_VIBRATE);
    }

    /**
     * @return {@code true} if the flip action for timers is enabled. {@code false} otherwise.
     */
    public static boolean isFlipActionForTimersEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_timer.xml
        return prefs.getBoolean(KEY_TIMER_FLIP_ACTION, DEFAULT_TIMER_FLIP_ACTION);
    }

    /**
     * @return {@code true} if the shake action for timers is enabled. {@code false} otherwise.
     */
    public static boolean isShakeActionForTimersEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_timer.xml
        return prefs.getBoolean(KEY_TIMER_SHAKE_ACTION, DEFAULT_TIMER_SHAKE_ACTION);
    }

    /**
     * @return the shake intensity value for timers.
     */
    public static int getTimerShakeIntensity(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_timer.xml
        return pref.getInt(KEY_TIMER_SHAKE_INTENSITY, DEFAULT_TIMER_SHAKE_INTENSITY);
    }

    /**
     * @return the timer sorting manually, in ascending order of duration, in descending order of duration or by name
     */
    public static String getTimerSortingPreference(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_timer.xml
        return prefs.getString(KEY_SORT_TIMER, DEFAULT_SORT_TIMER_MANUALLY);
    }

    /**
     * @return the default duration in seconds to add to timer when the "Add Minute" button is clicked.
     */
    public static int getDefaultTimeToAddToTimer(SharedPreferences prefs) {
        return prefs.getInt(KEY_TIMER_ADD_TIME_BUTTON_VALUE, DEFAULT_TIMER_ADD_TIME_BUTTON_VALUE);
    }

    /**
     * @param uri the uri of the ringtone to play for all timers
     */
    public static void setTimerRingtoneUri(SharedPreferences prefs, Uri uri) {
        prefs.edit().putString(KEY_TIMER_RINGTONE, uri.toString()).apply();
    }

    /**
     * @return the uri of the ringtone from the settings to play for all alarms
     */
    public static Uri getAlarmRingtoneUriFromSettings(SharedPreferences prefs, Uri defaultUri) {
        final String uriString = prefs.getString(KEY_DEFAULT_ALARM_RINGTONE, null);
        return uriString == null ? defaultUri : Uri.parse(uriString);
    }

    /**
     * @param uri the uri of the ringtone from the settings to play for all alarms
     */
    public static void setAlarmRingtoneUriFromSettings(SharedPreferences prefs, Uri uri) {
        prefs.edit().putString(KEY_DEFAULT_ALARM_RINGTONE, uri.toString()).apply();
    }

    /**
     * @param uri identifies the ringtone to play of an existing alarm
     */
    public static void setSelectedAlarmRingtoneUri(SharedPreferences prefs, Uri uri) {
        prefs.edit().putString(KEY_SELECTED_ALARM_RINGTONE_URI, uri.toString()).apply();
    }

    /**
     * @return {@code true} if a custom volume can be set for each alarm.
     * {@code false} otherwise.
     */
    public static boolean isPerAlarmVolumeEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_ENABLE_PER_ALARM_VOLUME, DEFAULT_ENABLE_PER_ALARM_VOLUME);
    }

    /**
     * @return {@code true} if a custom volume increase duration can be set for each alarm.
     * {@code false} otherwise.
     */
    public static boolean isPerAlarmCrescendoDurationEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_ENABLE_PER_ALARM_VOLUME_CRESCENDO_DURATION,
                DEFAULT_ENABLE_PER_ALARM_VOLUME_CRESCENDO_DURATION);
    }

    /**
     * @return the duration, in seconds, of the crescendo to apply to alarm ringtone playback;
     * {@code 0} implies no crescendo should be applied.
     */
    public static int getAlarmVolumeCrescendoDuration(SharedPreferences prefs) {
        return prefs.getInt(KEY_ALARM_VOLUME_CRESCENDO_DURATION, DEFAULT_VOLUME_CRESCENDO_DURATION);
    }

    /**
     * @return {@code true} if advanced audio playback is enabled for the ringtone.
     * {@code false} otherwise.
     */
    public static boolean isAdvancedAudioPlaybackEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_ADVANCED_AUDIO_PLAYBACK, DEFAULT_ADVANCED_AUDIO_PLAYBACK);
    }

    /**
     * @return {@code true} if the ringtone should be automatically routed to Bluetooth devices.
     * {@code false} otherwise.
     */
    public static boolean isAutoRoutingToBluetoothDeviceEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_AUTO_ROUTING_TO_BLUETOOTH_DEVICE, DEFAULT_AUTO_ROUTING_TO_BLUETOOTH_DEVICE);
    }

    /**
     * @return {@code true} if a custom media volume should be applied instead of the
     * system media volume. {@code false} otherwise.
     */
    public static boolean shouldUseCustomMediaVolume(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return !prefs.getBoolean(KEY_SYSTEM_MEDIA_VOLUME, DEFAULT_SYSTEM_MEDIA_VOLUME);
    }

    /**
     * @return the volume applied to the ringtone when a Bluetooth device is connected.
     */
    public static int getBluetoothVolumeValue(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getInt(KEY_BLUETOOTH_VOLUME, DEFAULT_BLUETOOTH_VOLUME);
    }

    /**
     * @return the duration, in seconds, of the crescendo to apply to timer ringtone playback;
     * {@code 0} implies no crescendo should be applied.
     */
    public static int getTimerVolumeCrescendoDuration(SharedPreferences prefs) {
        return prefs.getInt(KEY_TIMER_VOLUME_CRESCENDO_DURATION, DEFAULT_VOLUME_CRESCENDO_DURATION);
    }

    /**
     * @return the alarm sorting by time, by time of next alarm and by name.
     */
    public static String getAlarmSorting(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_timer.xml
        return prefs.getString(KEY_SORT_ALARM, DEFAULT_SORT_BY_ALARM_TIME);
    }

    /**
     * @return {@code true} if the enabled alarms are displayed first; {@code false} otherwise.
     */
    public static boolean areEnabledAlarmsDisplayedFirst(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_DISPLAY_ENABLED_ALARMS_FIRST, DEFAULT_DISPLAY_ENABLED_ALARMS_FIRST);
    }

    /**
     * @return the display order of the weekdays, which can start with {@link Calendar#SATURDAY},
     * {@link Calendar#SUNDAY} or {@link Calendar#MONDAY}
     */
    public static Weekdays.Order getWeekdayOrder(SharedPreferences prefs) {
        final String value = prefs.getString(KEY_WEEK_START, DEFAULT_WEEK_START);
        final int firstCalendarDay = Integer.parseInt(value);
        return switch (firstCalendarDay) {
            case SATURDAY -> SAT_TO_FRI;
            case SUNDAY -> SUN_TO_SAT;
            case MONDAY -> MON_TO_SUN;
            default -> throw new IllegalArgumentException("Unknown weekday: " + firstCalendarDay);
        };
    }

    /**
     * @return {@code true} if the restore process (of backup and restore) has completed. {@code false} otherwise.
     */
    public static boolean isRestoreBackupFinished(SharedPreferences prefs) {
        return prefs.getBoolean(KEY_RESTORE_BACKUP_FINISHED, false);
    }

    /**
     * @param finished {@code true} means the restore process (of backup and restore) has completed
     */
    public static void setRestoreBackupFinished(SharedPreferences prefs, boolean finished) {
        if (finished) {
            prefs.edit().putBoolean(KEY_RESTORE_BACKUP_FINISHED, true).apply();
        } else {
            prefs.edit().remove(KEY_RESTORE_BACKUP_FINISHED).apply();
        }
    }

    /**
     * @return the behavior to execute when volume button is pressed while firing an alarm
     */
    public static DataModel.VolumeButtonBehavior getAlarmVolumeButtonBehavior(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String value = prefs.getString(KEY_VOLUME_BUTTONS, DEFAULT_VOLUME_BEHAVIOR);
        return switch (value) {
            case DEFAULT_VOLUME_BEHAVIOR -> DO_NOTHING;
            case VOLUME_BEHAVIOR_CHANGE_VOLUME -> CHANGE_VOLUME;
            case VOLUME_BEHAVIOR_SNOOZE -> SNOOZE_ALARM;
            case VOLUME_BEHAVIOR_DISMISS -> DISMISS_ALARM;
            default ->
                    throw new IllegalArgumentException("Unknown volume button behavior: " + value);
        };
    }

    /**
     * @return the behavior to execute when power button is pressed while firing an alarm
     */
    public static DataModel.PowerButtonBehavior getAlarmPowerButtonBehavior(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String value = prefs.getString(KEY_POWER_BUTTON, DEFAULT_POWER_BEHAVIOR);
        return switch (value) {
            case DEFAULT_POWER_BEHAVIOR -> NOTHING;
            case POWER_BEHAVIOR_SNOOZE -> SNOOZE;
            case POWER_BEHAVIOR_DISMISS -> DISMISS;
            default ->
                    throw new IllegalArgumentException("Unknown power button behavior: " + value);
        };
    }

    /**
     * @return {@code true} if a custom auto silence duration can be set for each alarm.
     * {@code false} otherwise.
     */
    public static boolean isPerAlarmAutoSilenceEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_ENABLE_PER_ALARM_AUTO_SILENCE, DEFAULT_ENABLE_PER_ALARM_AUTO_SILENCE);
    }

    /**
     * @return the number of minutes an alarm may ring before it has timed out
     */
    public static int getAlarmTimeout(SharedPreferences prefs) {
        return prefs.getInt(KEY_AUTO_SILENCE_DURATION, DEFAULT_AUTO_SILENCE_DURATION);
    }

    /**
     * @return the number of minutes an alarm will remain snoozed before it rings again
     */
    public static int getSnoozeLength(SharedPreferences prefs) {
        return prefs.getInt(KEY_ALARM_SNOOZE_DURATION, DEFAULT_ALARM_SNOOZE_DURATION);
    }

    /**
     * @return {@code true} if a custom repeat limit can be set for each missed alarm.
     * {@code false} otherwise.
     */
    public static boolean isPerAlarmMissedRepeatLimitEnabled(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getBoolean(KEY_ENABLE_PER_ALARM_MISSED_REPEAT_LIMIT, DEFAULT_ENABLE_PER_ALARM_MISSED_REPEAT_LIMIT);
    }

    /**
     * @return the number of times a missed alarm can be repeated.
     */
    public static int getMissedAlarmRepeatLimit(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String string = prefs.getString(KEY_MISSED_ALARM_REPEAT_LIMIT, DEFAULT_MISSED_ALARM_REPEAT_LIMIT);
        return Integer.parseInt(string);
    }

    /**
     * @param currentTime timezone offsets created relative to this time
     * @return a description of the time zones available for selection
     */
    public static TimeZones getTimeZones(Context context, long currentTime) {
        final Locale locale = Locale.getDefault();
        final Context localizedContext = Utils.getLocalizedContext(context);
        final Resources resources = localizedContext.getResources();
        final String[] timeZoneIds = resources.getStringArray(R.array.timezone_values);
        final String[] timeZoneNames = resources.getStringArray(R.array.timezone_labels);

        // Verify the data is consistent.
        if (timeZoneIds.length != timeZoneNames.length) {
            final String message = String.format(Locale.US,
                    "id count (%d) does not match name count (%d) for locale %s",
                    timeZoneIds.length, timeZoneNames.length, locale);
            throw new IllegalStateException(message);
        }

        // Create TimeZoneDescriptors for each TimeZone so they can be sorted.
        final TimeZoneDescriptor[] descriptors = new TimeZoneDescriptor[timeZoneIds.length];
        for (int i = 0; i < timeZoneIds.length; i++) {
            final String id = timeZoneIds[i];
            final String name = timeZoneNames[i].replaceAll("\"", "");
            descriptors[i] = new TimeZoneDescriptor(locale, id, name, currentTime);
        }
        Arrays.sort(descriptors);

        // Transfer the TimeZoneDescriptors into parallel arrays for easy consumption by the caller.
        final CharSequence[] tzIds = new CharSequence[descriptors.length];
        final CharSequence[] tzNames = new CharSequence[descriptors.length];
        for (int i = 0; i < descriptors.length; i++) {
            final TimeZoneDescriptor descriptor = descriptors[i];
            tzIds[i] = descriptor.mTimeZoneId;
            tzNames[i] = descriptor.mTimeZoneName;
        }

        return new TimeZones(tzIds, tzNames);
    }

    /**
     * @return the action to be performed after flipping the device.
     */
    public static int getFlipAction(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String string = prefs.getString(KEY_FLIP_ACTION, DEFAULT_FLIP_ACTION);
        return Integer.parseInt(string);
    }

    /**
     * @return the action to be performed after shaking the device.
     */
    public static int getShakeAction(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String string = prefs.getString(KEY_SHAKE_ACTION, DEFAULT_SHAKE_ACTION);
        return Integer.parseInt(string);
    }

    /**
     * @return the shake intensity value.
     */
    public static int getShakeIntensity(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getInt(KEY_SHAKE_INTENSITY, DEFAULT_SHAKE_INTENSITY);
    }

    /**
     * @return {@code true} if the Dismiss button should appear as soon as the alarm is enabled.
     * {@code false} otherwise.
     */
    public static boolean isDismissButtonDisplayedWhenAlarmEnabled(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getBoolean(KEY_DISPLAY_DISMISS_BUTTON, DEFAULT_DISPLAY_DISMISS_BUTTON);
    }

    /**
     * @return the number of minutes before the upcoming alarm notification appears
     */
    public static int getAlarmNotificationReminderTime(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        final String string = prefs.getString(KEY_ALARM_NOTIFICATION_REMINDER_TIME, DEFAULT_ALARM_NOTIFICATION_REMINDER_TIME);
        return Integer.parseInt(string);
    }

    /**
     * @return the vibration pattern applied to alarms.
     */
    public static String getVibrationPattern(SharedPreferences prefs) {
        return prefs.getString(KEY_VIBRATION_PATTERN, DEFAULT_VIBRATION_PATTERN);
    }

    /**
     * @return the vibration start delay applied to alarms.
     */
    public static int getVibrationStartDelay(SharedPreferences prefs) {
        return prefs.getInt(KEY_VIBRATION_START_DELAY, DEFAULT_VIBRATION_START_DELAY);
    }

    /**
     * @return {@code true} if alarm vibrations are enabled when creating alarms. {@code false} otherwise.
     */
    public static boolean areAlarmVibrationsEnabledByDefault(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getBoolean(KEY_ENABLE_ALARM_VIBRATIONS_BY_DEFAULT, DEFAULT_ENABLE_ALARM_VIBRATIONS_BY_DEFAULT);
    }

    /**
     * @return {@code true} if vibrations are enabled to indicate whether the alarm is snoozed or dismissed.
     * {@code false} otherwise.
     */
    public static boolean areSnoozedOrDismissedAlarmVibrationsEnabled(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getBoolean(KEY_ENABLE_SNOOZED_OR_DISMISSED_ALARM_VIBRATIONS, DEFAULT_ENABLE_SNOOZED_OR_DISMISSED_ALARM_VIBRATIONS);
    }

    /**
     * @return {@code true} if the back flash should turn on when the alarm is triggered.
     * {@code false} otherwise.
     */
    public static boolean shouldTurnOnBackFlashForTriggeredAlarm(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getBoolean(KEY_TURN_ON_BACK_FLASH_FOR_TRIGGERED_ALARM, DEFAULT_TURN_ON_BACK_FLASH_FOR_TRIGGERED_ALARM);
    }

    /**
     * @return {@code true} if occasional alarm should be deleted by default. {@code false} otherwise.
     */
    public static boolean isOccasionalAlarmDeletedByDefault(SharedPreferences pref) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return pref.getBoolean(KEY_ENABLE_DELETE_OCCASIONAL_ALARM_BY_DEFAULT, DEFAULT_ENABLE_DELETE_OCCASIONAL_ALARM_BY_DEFAULT);
    }

    /**
     * @return the time picker style.
     */
    public static String getMaterialTimePickerStyle(SharedPreferences prefs) {
        // Default value must match the one in res/xml/settings_alarm.xml
        return prefs.getString(KEY_MATERIAL_TIME_PICKER_STYLE, DEFAULT_TIME_PICKER_STYLE);
    }

    public static DataModel.ClockStyle getClockStyle(SharedPreferences prefs, String key) {
        // 1) Try the new, correct storage: INT (ordinal)
        try {
            int ordinal = prefs.getInt(
                    key,
                    DataModel.ClockStyle.DIGITAL.ordinal()
            );

            DataModel.ClockStyle[] values = DataModel.ClockStyle.values();
            if (ordinal >= 0 && ordinal < values.length) {
                return values[ordinal];
            }
        } catch (ClassCastException ignored) {
            // Falls through to legacy string handling
        }

        // 2) Legacy fallback: STRING (enum name)
        String clockStyle = prefs.getString(
                key,
                DataModel.ClockStyle.DIGITAL.name()
        );

        DataModel.ClockStyle style;
        try {
            style = DataModel.ClockStyle.valueOf(
                    clockStyle.toUpperCase(Locale.US)
            );
        } catch (Exception e) {
            style = DataModel.ClockStyle.DIGITAL;
        }

        // 3) Migrate to INT so this never happens again
        prefs.edit()
                .putInt(key, style.ordinal())
                .apply();

        return style;
    }

    /**
     * @return {@code true} if the standby should run only while wireless charging.
     */
    public static boolean isStandbyWirelessOnly(SharedPreferences prefs) {
        // Default value must match the one in res/xml/standby_settings.xml
        return prefs.getBoolean(
                KEY_STANDBY_WIRELESS_ONLY,
                DEFAULT_STANDBY_WIRELESS_ONLY
        );
    }


    /**
     * @return the set of app widget ids used by the standby
     */
    public static Set<String> getStandbyWidgetIds(SharedPreferences prefs) {
        return new HashSet<>(
                prefs.getStringSet(
                        PreferencesKeys.KEY_STANDBY_WIDGET_IDS,
                        PreferencesDefaultValues.DEFAULT_STANDBY_WIDGET_IDS
                )
        );
    }

    /**
     * Adds a widget id to the standby widget set.
     */
    public static void addStandbyWidgetId(SharedPreferences prefs, int widgetId) {
        Set<String> ids = getStandbyWidgetIds(prefs);
        ids.add(String.valueOf(widgetId));
        prefs.edit()
                .putStringSet(PreferencesKeys.KEY_STANDBY_WIDGET_IDS, ids)
                .apply();
    }

    public static void removeStandbyWidgetId(SharedPreferences prefs, int widgetId) {
        Set<String> current = getStandbyWidgetIds(prefs);

        if (current.isEmpty()) return;

        Set<String> updated = new HashSet<>(current);
        updated.remove(String.valueOf(widgetId));

        prefs.edit()
                .putStringSet(
                        PreferencesKeys.KEY_STANDBY_WIDGET_IDS,
                        updated
                )
                .apply();
    }

    /**
     * @return the JSON configuration string for the standby widgets.
     */
    public static String getStandbyWidgetsConfig(SharedPreferences prefs) {
        return prefs.getString(PreferencesKeys.KEY_STANDBY_WIDGETS_CONFIG, "");
    }

    /**
     * Saves the JSON configuration string for the standby widgets.
     */
    public static void setStandbyWidgetsConfig(SharedPreferences prefs, String config) {
        prefs.edit()
                .putString(PreferencesKeys.KEY_STANDBY_WIDGETS_CONFIG, config)
                .apply();
    }

    public static int getStandbyMaxSlant(SharedPreferences prefs) {
        return prefs.getInt(
                PreferencesKeys.KEY_STANDBY_MAX_SLANT,
                PreferencesDefaultValues.DEFAULT_STANDBY_MAX_SLANT
        );
    }

    public static int getStandbyFontWidth(SharedPreferences prefs) {
        return prefs.getInt(
                PreferencesKeys.KEY_STANDBY_FONT_WIDTH,
                PreferencesDefaultValues.DEFAULT_STANDBY_FONT_WIDTH
        );
    }

    public static boolean getStandbyVibrantPalette(SharedPreferences prefs) {
        return prefs.getBoolean(
                KEY_STANDBY_VIBRANT_PALETTE,
                PreferencesDefaultValues.DEFAULT_STANDBY_VIBRANT_PALETTE
        );
    }

    public static boolean getStandbyDynamicColor(SharedPreferences prefs) {
        return prefs.getBoolean(
                KEY_STANDBY_DYNAMIC_COLOR,
                PreferencesDefaultValues.DEFAULT_STANDBY_DYNAMIC_COLOR
        );
    }

    public static boolean getStandbySingleColorMode(SharedPreferences prefs) {
        return prefs.getBoolean(
                KEY_STANDBY_SINGLE_COLOR_MODE,
                PreferencesDefaultValues.DEFAULT_STANDBY_SINGLE_COLOR_MODE
        );
    }
    
    public static DataModel.StandbyPageLayout getStandbyPageLayout(SharedPreferences prefs) {
        int ordinal = prefs.getInt(KEY_STANDBY_PAGE_LAYOUT, DEFAULT_STANDBY_PAGE_LAYOUT);
        DataModel.StandbyPageLayout[] values = DataModel.StandbyPageLayout.values();
        if (ordinal >= 0 && ordinal < values.length) {
            return values[ordinal];
        }
        return DataModel.StandbyPageLayout.PAGER;
    }
    
    public static void setStandbyPageLayout(SharedPreferences prefs, DataModel.StandbyPageLayout layout) {
        prefs.edit().putInt(KEY_STANDBY_PAGE_LAYOUT, layout.ordinal()).apply();
    }

    public static float getStandbySplitRatio(SharedPreferences prefs) {
        return prefs.getFloat(KEY_STANDBY_SPLIT_RATIO, DEFAULT_STANDBY_SPLIT_RATIO);
    }

    public static void setStandbySplitRatio(SharedPreferences prefs, float ratio) {
        prefs.edit().putFloat(KEY_STANDBY_SPLIT_RATIO, ratio).apply();
    }

    /**
     * These descriptors have a natural order from furthest ahead of GMT to furthest behind GMT.
     */
    public static class TimeZoneDescriptor implements Comparable<TimeZoneDescriptor> {

        private final int mOffset;
        private final String mTimeZoneId;
        private final String mTimeZoneName;

        private TimeZoneDescriptor(Locale locale, String id, String name, long currentTime) {
            mTimeZoneId = id;

            final TimeZone tz = TimeZone.getTimeZone(id);
            mOffset = tz.getOffset(currentTime);

            final char sign = mOffset < 0 ? '-' : '+';
            final int absoluteGMTOffset = Math.abs(mOffset);
            final long hour = absoluteGMTOffset / HOUR_IN_MILLIS;
            final long minute = (absoluteGMTOffset / MINUTE_IN_MILLIS) % 60;
            mTimeZoneName = String.format(locale, "(GMT%s%d:%02d) %s", sign, hour, minute, name);
        }

        @Override
        public int compareTo(@NonNull TimeZoneDescriptor other) {
            return mOffset - other.mOffset;
        }
    }
}

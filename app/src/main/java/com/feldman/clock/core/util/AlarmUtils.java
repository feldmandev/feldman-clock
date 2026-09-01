/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.util;

import com.feldman.clock.app.ClockApplication;

import static com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences;

import android.app.AlarmManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.widget.Toast;
import androidx.annotation.VisibleForTesting;

import com.feldman.clock.R;
import com.feldman.clock.alarm.AlarmStateManager;
import com.feldman.clock.settings.SettingsDAO;
import com.feldman.clock.core.storage.provider.AlarmInstance;
import com.feldman.clock.ui.standby.Standby;
import com.feldman.clock.ui.standby.StandbyActivity;
import com.feldman.clock.ui.standby.ClockStandbyService;
import com.feldman.clock.ui.components.toast.ToastManager;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Static utility methods for Alarms.
 */
public class AlarmUtils {

    /**
     * Intent action sent when the alarm has been either created or updated in the Clock app.
     * <p>
     * This action will display the next alarm of this app only in the clock tab and standby.
     */
    public static final String ACTION_NEXT_ALARM_CHANGED_BY_CLOCK = "com.feldman.clock.NEXT_ALARM_CHANGED_BY_CLOCK";

    /**
     * @return The text of the next alarm.
     */
    public static String getNextAlarm(Context context) {
        AlarmInstance instance = AlarmStateManager.getNextFiringAlarm(context);
        if (instance != null) {
            Calendar alarmCalendar = Calendar.getInstance();
            long alarmTime = instance.getAlarmTime().getTimeInMillis();
            alarmCalendar.setTimeInMillis(alarmTime);
            return getFormattedTime(context, alarmCalendar);
        }

        return null;
    }

    /** Returns the next system alarm using the same compact format as the digital widget. */
    public static String getNextAlarmClockText(Context context) {
        final AlarmManager alarmManager = context.getSystemService(AlarmManager.class);
        if (alarmManager == null || alarmManager.getNextAlarmClock() == null) {
            return null;
        }

        final long triggerTime = alarmManager.getNextAlarmClock().getTriggerTime();
        final String skeleton = DateFormat.is24HourFormat(context) ? "EEEHm" : "EEEhma";
        final String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        return DateFormat.format(pattern, new Date(triggerTime)).toString();
    }

    public static String getAlarmText(Context context, AlarmInstance instance, boolean includeLabel) {
        String alarmTimeStr = getFormattedTime(context, instance.getAlarmTime());
        assert instance.mLabel != null;
        return (instance.mLabel.isEmpty() || !includeLabel)
                ? alarmTimeStr
                : alarmTimeStr + " - " + instance.mLabel;
    }

    public static String getFormattedTime(Context context, Calendar alarmTime) {
        final Calendar now = Calendar.getInstance();
        final Calendar today = (Calendar) now.clone();
        final Calendar tomorrow = (Calendar) now.clone();
        tomorrow.add(Calendar.DAY_OF_YEAR, 1);

        final boolean is24HourFormat = DateFormat.is24HourFormat(context);
        String skeleton = is24HourFormat ? "Hm" : "hma";

        String prefix = "";

        if (isSameDayAndTimeZone(alarmTime, today)) {
            prefix = context.getString(R.string.alarm_today) + " ";
        } else if (isSameDayAndTimeZone(alarmTime, tomorrow)) {
            prefix = context.getString(R.string.alarm_tomorrow) + " ";
        } else {
            // Beyond tomorrow: show day or full date if distant
            long diffInMillis = alarmTime.getTimeInMillis() - now.getTimeInMillis();
            long diffInDays = TimeUnit.MILLISECONDS.toDays(diffInMillis);

            if (diffInDays >= 6) {
                // e.g., "Sat Oct 28 20:30"
                skeleton = is24HourFormat ? "EEE MMM d Hm" : "EEE MMM d hma";
            } else {
                // e.g., "Wed 20:30"
                skeleton = is24HourFormat ? "EEE Hm" : "EEE hma";
            }
        }

        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton);
        if (context instanceof StandbyActivity || context instanceof Standby || context instanceof ClockStandbyService) {
            final SharedPreferences prefs = getDefaultSharedPreferences(context);
            // Add a "Thin Space" (\u2009) at the end of the next alarm to prevent its display from being cut off on some devices.
            // (The display of the next alarm is only cut off at the end if it is defined in italics in the standby settings).
            if (SettingsDAO.isStandbyDateInItalic(prefs)) {
                // A "Thin Space" (\u2009) is also added at the beginning to correctly center the date,
                // alarm icon and next alarm only when the date is in italics.
                pattern = "\u2009" + DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton) + "\u2009";
            } else if (SettingsDAO.isStandbyNextAlarmInItalic(prefs)) {
                pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), skeleton) + "\u2009";
            }
        }
        return prefix + DateFormat.format(pattern, alarmTime);
    }

    /**
     * Checks if two Calendar instances represent the same calendar day, taking into account their
     * respective time zones.
     * <p>
     * This method returns false if the two calendars use different time zones, even if the year
     * and day of year fields are numerically equal.</p>
     *
     * @param cal1 the first calendar instance
     * @param cal2 the second calendar instance
     *
     * @return {@code true} if both calendars are in the same time zone and represent the same day;
     * {@code false} otherwise.
     */
    private static boolean isSameDayAndTimeZone(Calendar cal1, Calendar cal2) {
        // Normalize both calendars to their respective time zones
        if (!cal1.getTimeZone().equals(cal2.getTimeZone())) {
            return false;
        }

        return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
                cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR);
    }


    public static String getFormattedTime(Context context, long timeInMillis) {
        final Calendar c = Calendar.getInstance();
        c.setTimeInMillis(timeInMillis);
        return getFormattedTime(context, c);
    }

    /**
     * format "Alarm set for 2 days, 7 hours, and 53 minutes from now."
     */
    @VisibleForTesting
    public static String formatElapsedTimeUntilAlarm(Context context, long delta) {
        // If the alarm will ring within 60 seconds, just report "less than a minute."
        final String[] formats = context.getResources().getStringArray(R.array.alarm_set);
        if (delta < DateUtils.MINUTE_IN_MILLIS) {
            return formats[0];
        }

        // Otherwise, format the remaining time until the alarm rings.

        // Round delta upwards to the nearest whole minute. (e.g. 7m 58s -> 8m)
        final long remainder = delta % DateUtils.MINUTE_IN_MILLIS;
        delta += remainder == 0 ? 0 : (DateUtils.MINUTE_IN_MILLIS - remainder);

        long days = delta / (1000 * 60 * 60 * 24);
        long remainingMillis = delta % (1000 * 60 * 60 * 24);

        long hours = remainingMillis / (1000 * 60 * 60);
        remainingMillis %= (1000 * 60 * 60);

        long minutes = remainingMillis / (1000 * 60);

        String daySeq = Utils.getNumberFormattedQuantityString(context, R.plurals.days, (int) days);
        String hourSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.hours, (int) hours);
        String minSeq = Utils.getNumberFormattedQuantityString(context, R.plurals.minutes, (int) minutes);

        final boolean showDays = days > 0;
        final boolean showHours = hours > 0;
        final boolean showMinutes = minutes > 0;

        // Compute the index of the most appropriate time format based on the time delta.
        final int index = (showDays ? 1 : 0) | (showHours ? 2 : 0) | (showMinutes ? 4 : 0);

        return String.format(formats[index], daySeq, hourSeq, minSeq);
    }

    public static void popAlarmSetToast(Context context, long alarmTime) {
        final long alarmTimeDelta = alarmTime - System.currentTimeMillis();
        final String text = formatElapsedTimeUntilAlarm(context, alarmTimeDelta);
        Toast toast = Toast.makeText(context, text, Toast.LENGTH_LONG);
        ToastManager.setToast(toast);
        toast.show();
    }


    /**
     * @return {@code true} if the device has a back flash. {@code false} otherwise.
     */
    public static boolean hasBackFlash(Context context) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
                Integer lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);

                if (lensFacing != null && lensFacing == CameraCharacteristics.LENS_FACING_BACK && hasFlash != null && hasFlash) {
                    return true;
                }
            }
        } catch (CameraAccessException e) {
        }
        return false;
    }
}

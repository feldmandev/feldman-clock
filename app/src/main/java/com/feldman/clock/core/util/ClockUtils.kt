/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.feldman.clock.core.util

import com.feldman.clock.R

import android.content.Context
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.format.DateFormat
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.view.View
import android.widget.TextClock
import android.widget.TextView
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.Standby
import com.feldman.clock.ui.standby.StandbyActivity
import com.feldman.clock.ui.standby.ClockStandbyService
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object ClockUtils {

    /**
     * Formats the time in the TextClock according to the Locale with a special
     * formatting treatment for the am/pm label.
     *
     * @param clock          TextClock to format
     * @param includeSeconds whether or not to include seconds in the clock's time
     */
    @JvmStatic
    fun setTimeFormat(clock: TextClock?, includeSeconds: Boolean) {
        if (clock != null) {
            // Get the best format for 12 hours mode according to the locale
            clock.format12Hour = get12ModeFormat(clock.context, 0.4f, includeSeconds)
            // Get the best format for 24 hours mode according to the locale
            clock.format24Hour = get24ModeFormat(clock.context, includeSeconds)
        }
    }

    /**
     * @param amPmRatio      a value between 0 and 1 that is the ratio of the relative size of the
     * am/pm string to the time string
     * @param includeSeconds whether or not to include seconds in the time string
     * @return format string for 12 hours mode time, not including seconds
     */
    @JvmStatic
    fun get12ModeFormat(context: Context, amPmRatio: Float, includeSeconds: Boolean): CharSequence {
        var pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), if (includeSeconds) "hmsa" else "hma")

        // Replace spaces with "Hair Space"
        pattern = pattern.replace("\\s".toRegex(), "\u200A")

        if (amPmRatio <= 0) {
            pattern = pattern.replace("\u200Aa", "").trim()
        } else {
            if (context is StandbyActivity || context is Standby || context is ClockStandbyService) {
                if (SettingsDAO.isStandbyDigitalClockInItalic(ClockApplication.getDefaultSharedPreferences(context))) {
                    // For standby, add a "Hair Space" (\u200A) at the end of the AM/PM to prevent
                    // its display from being cut off on some devices when in italic.
                    pattern = pattern.replace("a", "a\u200A")
                }
            }
        }

        // Build a spannable so that the am/pm will be formatted
        val amPmPos = pattern.indexOf('a')
        if (amPmPos == -1) {
            return pattern
        }

        val sp = SpannableString(pattern)
        sp.setSpan(RelativeSizeSpan(amPmRatio), amPmPos, amPmPos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(StyleSpan(Typeface.NORMAL), amPmPos, amPmPos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        sp.setSpan(TypefaceSpan("sans-serif-bold"), amPmPos, amPmPos + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        return sp
    }

    @JvmStatic
    fun get24ModeFormat(context: Context, includeSeconds: Boolean): CharSequence {
        if (context is StandbyActivity || context is Standby || context is ClockStandbyService) {
            if (SettingsDAO.isStandbyDigitalClockInItalic(ClockApplication.getDefaultSharedPreferences(context))) {
                // For standby, add a "Thin Space" (\u2009) at the end of the time to prevent
                // its display from being cut off on some devices when in italic.
                return DateFormat.getBestDateTimePattern(Locale.getDefault(), if (includeSeconds) "Hms" else "Hm") + "\u2009"
            }
        }
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), if (includeSeconds) "Hms" else "Hm")
    }

    /**
     * Clock views can call this to refresh their date.
     */
    @JvmStatic
    fun updateDate(dateSkeleton: String, descriptionSkeleton: String, clock: View) {
        val dateDisplay = clock.findViewById<TextView>(R.id.date) ?: return

        val l = Locale.getDefault()
        var datePattern = DateFormat.getBestDateTimePattern(l, dateSkeleton)
        if (dateDisplay.context is StandbyActivity || dateDisplay.context is Standby || dateDisplay.context is ClockStandbyService) {
            val prefs = ClockApplication.getDefaultSharedPreferences(clock.context)
            // Add a "Thin Space" (\u2009) at the end of the date to prevent its display from being cut off on some devices.
            // (The display of the date is only cut off at the end if it is defined in italics in the standby settings).
            if (SettingsDAO.isStandbyDateInItalic(prefs)) {
                datePattern = "\u2009${DateFormat.getBestDateTimePattern(l, dateSkeleton)}\u2009"
            } else if (SettingsDAO.isStandbyNextAlarmInItalic(prefs)) {
                datePattern = "\u2009${DateFormat.getBestDateTimePattern(l, dateSkeleton)}"
            }
        }

        val descriptionPattern = DateFormat.getBestDateTimePattern(l, descriptionSkeleton)
        val now = Date()
        dateDisplay.text = SimpleDateFormat(datePattern, l).format(now)
        dateDisplay.visibility = View.VISIBLE
        dateDisplay.contentDescription = SimpleDateFormat(descriptionPattern, l).format(now)
    }

    /**
     * Given a point in time, return the subsequent moment any of the time zones changes days.
     * e.g. Given 8:00pm on 1/1/2016 and time zones in LA and NY this method would return a Date for
     * midnight on 1/2/2016 in the NY timezone since it changes days first.
     *
     * @param time  a point in time from which to compute midnight on the subsequent day
     * @param zones a collection of time zones
     * @return the nearest point in the future at which any of the time zones changes days
     */
    @JvmStatic
    fun getNextDay(time: Date, zones: Collection<TimeZone>): Date? {
        var next: Calendar? = null
        for (tz in zones) {
            val c = Calendar.getInstance(tz)
            c.time = time

            // Advance to the next day.
            c.add(Calendar.DAY_OF_YEAR, 1)

            // Reset the time to midnight.
            c.set(Calendar.HOUR_OF_DAY, 0)
            c.set(Calendar.MINUTE, 0)
            c.set(Calendar.SECOND, 0)
            c.set(Calendar.MILLISECOND, 0)

            if (next == null || c < next) {
                next = c
            }
        }

        return next?.time
    }

    /**
     * Apply the clock icon font to the next alarm view.
     */
    @JvmStatic
    fun setClockIconTypeface(clock: View) {
        val nextAlarmIconView = clock.findViewById<TextView>(R.id.nextAlarmIcon)
        nextAlarmIconView?.typeface = getAlarmIconTypeface(clock.context)
    }

    /**
     * To display the alarm clock in this font, use the character [R.string.clock_emoji].
     *
     * @return a special font containing a glyph that draws an alarm clock
     */
    @JvmStatic
    fun getAlarmIconTypeface(context: Context): Typeface {
        return Typeface.createFromAsset(context.assets, "fonts/clock.ttf")
    }
}

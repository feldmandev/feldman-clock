/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import com.feldman.clock.R

import android.content.Context
import android.text.TextUtils
import androidx.annotation.VisibleForTesting
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Calendar.*

/**
 * This class is responsible for encoding a weekly repeat cycle in a [mBits] bitset. It
 * also converts between those bits and the [Calendar.DAY_OF_WEEK] values for easier mutation
 * and querying.
 *
 * @param bits An encoded form of a weekly repeat schedule (will be masked with ALL_DAYS).
 */
class Weekdays(bits: Int) {

    // Mask off the unused bits (same as Java version)
    val mBits: Int = ALL_DAYS and bits

    companion object {
        /**
         * An instance with no weekdays in the weekly repeat cycle.
         */
        @JvmField
        val NONE = fromBits(0)

        /**
         * All valid bits set.
         */
        private const val ALL_DAYS = 0x7F

        /**
         * Maps calendar weekdays to the bit masks that represent them in this class.
         */
        private val sCalendarDayToBit: Map<Int, Int> = mapOf(
            MONDAY to 0x01,
            TUESDAY to 0x02,
            WEDNESDAY to 0x04,
            THURSDAY to 0x08,
            FRIDAY to 0x10,
            SATURDAY to 0x20,
            SUNDAY to 0x40
        )

        /**
         * @param bits bits representing the encoded weekly repeat schedule
         * @return a Weekdays instance representing the same repeat schedule as the [bits]
         */
        @JvmStatic
        fun fromBits(bits: Int): Weekdays {
            return Weekdays(bits)
        }

        /**
         * @param calendarDays an array containing any or all of the following values
         *                     - [Calendar.SUNDAY]
         *                     - [Calendar.MONDAY]
         *                     - [Calendar.TUESDAY]
         *                     - [Calendar.WEDNESDAY]
         *                     - [Calendar.THURSDAY]
         *                     - [Calendar.FRIDAY]
         *                     - [Calendar.SATURDAY]
         * @return a Weekdays instance representing the given [calendarDays]
         */
        @JvmStatic
        fun fromCalendarDays(vararg calendarDays: Int): Weekdays {
            var bits = 0
            for (calendarDay in calendarDays) {
                val bit = sCalendarDayToBit[calendarDay]
                if (bit != null) {
                    bits = bits or bit
                }
            }
            return Weekdays(bits)
        }
    }

    /**
     * @param calendarDay any of the following values:
     *                    - [Calendar.SUNDAY]
     *                    - [Calendar.MONDAY]
     *                    - [Calendar.TUESDAY]
     *                    - [Calendar.WEDNESDAY]
     *                    - [Calendar.THURSDAY]
     *                    - [Calendar.FRIDAY]
     *                    - [Calendar.SATURDAY]
     * @param on          `true` if the [calendarDay] is on; `false` otherwise
     * @return a WeekDays instance with the [calendarDay] mutated
     */
    fun setBit(calendarDay: Int, on: Boolean): Weekdays {
        val bit = sCalendarDayToBit[calendarDay] ?: return this
        return Weekdays(if (on) (mBits or bit) else (mBits and bit.inv()))
    }

    /**
     * @param calendarDay any of the following values:
     *                    - [Calendar.SUNDAY]
     *                    - [Calendar.MONDAY]
     *                    - [Calendar.TUESDAY]
     *                    - [Calendar.WEDNESDAY]
     *                    - [Calendar.THURSDAY]
     *                    - [Calendar.FRIDAY]
     *                    - [Calendar.SATURDAY]
     * @return `true` if the given [calendarDay] is enabled
     */
    fun isBitOn(calendarDay: Int): Boolean {
        val bit = sCalendarDayToBit[calendarDay]
            ?: throw IllegalArgumentException("$calendarDay is not a valid weekday")
        return (mBits and bit) > 0
    }

    /**
     * @return the weekly repeat schedule encoded as an integer
     * Note: For Kotlin code, prefer using the [mBits] property directly
     */
    fun getBits(): Int = mBits

    /**
     * @return `true` if at least one weekday is enabled in the repeat schedule
     */
    fun isRepeating(): Boolean = mBits != 0

    /**
     * @return `true` if all days of the week are selected; `false` otherwise.
     */
    fun isAllDaysSelected(): Boolean = mBits == ALL_DAYS

    /**
     * Note: only the day-of-week is read from the [time]. The time fields
     * are not considered in this computation.
     *
     * @param time a timestamp relative to which the answer is given
     * @return the number of days between the given [time] and the previous enabled weekday
     * which is always between 1 and 7 inclusive; `-1` if no weekdays are enabled
     */
    fun getDistanceToPreviousDay(time: Calendar): Int {
        var calendarDay = time.get(DAY_OF_WEEK)
        for (count in 1..7) {
            calendarDay--
            if (calendarDay < SUNDAY) {
                calendarDay = SATURDAY
            }
            if (isBitOn(calendarDay)) {
                return count
            }
        }
        return -1
    }

    /**
     * Note: only the day-of-week is read from the [time]. The time fields
     * are not considered in this computation.
     *
     * @param time a timestamp relative to which the answer is given
     * @return the number of days between the given [time] and the next enabled weekday which
     * is always between 0 and 6 inclusive; `-1` if no weekdays are enabled
     */
    fun getDistanceToNextDay(time: Calendar): Int {
        var calendarDay = time.get(DAY_OF_WEEK)
        for (count in 0..6) {
            if (isBitOn(calendarDay)) {
                return count
            }
            calendarDay++
            if (calendarDay > SATURDAY) {
                calendarDay = SUNDAY
            }
        }
        return -1
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || javaClass !=other.javaClass) return false
        val weekdays = other as Weekdays
        return mBits == weekdays.mBits
    }

    override fun hashCode(): Int {
        return mBits
    }

    override fun toString(): String {
        val builder = StringBuilder(19)
        builder.append("[")
        if (isBitOn(MONDAY)) {
            builder.append(if (builder.length > 1) " M" else "M")
        }
        if (isBitOn(TUESDAY)) {
            builder.append(if (builder.length > 1) " T" else "T")
        }
        if (isBitOn(WEDNESDAY)) {
            builder.append(if (builder.length > 1) " W" else "W")
        }
        if (isBitOn(THURSDAY)) {
            builder.append(if (builder.length > 1) " Th" else "Th")
        }
        if (isBitOn(FRIDAY)) {
            builder.append(if (builder.length > 1) " F" else "F")
        }
        if (isBitOn(SATURDAY)) {
            builder.append(if (builder.length > 1) " Sa" else "Sa")
        }
        if (isBitOn(SUNDAY)) {
            builder.append(if (builder.length > 1) " Su" else "Su")
        }
        builder.append("]")
        return builder.toString()
    }

    /**
     * @param context for accessing resources
     * @param order   the order in which to present the weekdays
     * @return the enabled weekdays in the given [order]
     */
    fun toString(context: Context, order: Order): String {
        return toString(context, order, false)
    }

    /**
     * @param context for accessing resources
     * @param order   the order in which to present the weekdays
     * @return the enabled weekdays in the given [order] in a manner that
     * is most appropriate for talk-back
     */
    fun toAccessibilityString(context: Context, order: Order): String {
        return toString(context, order, true)
    }

    @VisibleForTesting
    fun getCount(): Int {
        var count = 0
        for (calendarDay in SUNDAY..SATURDAY) {
            if (isBitOn(calendarDay)) {
                count++
            }
        }
        return count
    }

    /**
     * @param context        for accessing resources
     * @param order          the order in which to present the weekdays
     * @param forceLongNames if `true` the un-abbreviated weekdays are used
     * @return the enabled weekdays in the given [order]
     */
    private fun toString(context: Context, order: Order, forceLongNames: Boolean): String {
        if (!isRepeating()) {
            return ""
        }

        if (mBits == ALL_DAYS) {
            return context.getString(R.string.every_day)
        }

        val longNames = forceLongNames || getCount() <= 1
        val dfs = DateFormatSymbols()
        val weekdays = if (longNames) dfs.weekdays else dfs.shortWeekdays

        val separator = context.getString(R.string.day_concat)

        val builder = StringBuilder(40)
        for (calendarDay in order.getCalendarDays()) {
            if (isBitOn(calendarDay)) {
                if (!TextUtils.isEmpty(builder)) {
                    builder.append(separator)
                }
                builder.append(weekdays[calendarDay])
            }
        }
        return builder.toString()
    }

    /**
     * The preferred starting day of the week can differ by locale. This enumerated value is used to
     * describe the preferred ordering.
     */
    enum class Order(private vararg val calendarDays: Int) {
        SAT_TO_FRI(SATURDAY, SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY),
        SUN_TO_SAT(SUNDAY, MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY),
        MON_TO_SUN(MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY);

        fun getCalendarDays(): List<Int> = calendarDays.toList()
    }
}

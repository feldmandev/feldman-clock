/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package com.feldman.clock.core.storage.provider

import com.feldman.clock.R

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.media.RingtoneManager
import android.net.Uri
import android.provider.BaseColumns
import android.util.Log
import com.feldman.clock.app.ClockApplication.Companion.getDefaultSharedPreferences
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.core.util.RingtoneUtils
import java.util.Calendar
import java.util.LinkedList

class AlarmInstance : ClockContract.InstancesColumns {
    // Public fields
    @JvmField
    var mId: Long = 0
    var mYear: Int = 0
    var mMonth: Int = 0
    var mDay: Int = 0
    var mHour: Int = 0
    var mMinute: Int = 0
    @JvmField
    var mLabel: String? = null
    @JvmField
    var mVibrate: Boolean = false
    @JvmField
    var mFlash: Boolean = false
    @JvmField
    var mRingtone: Uri? = null
    @JvmField
    var mAlarmId: Long? = null
    @JvmField
    var mAlarmState: Int
    @JvmField
    var mAutoSilenceDuration: Int = 0
    @JvmField
    var mSnoozeDuration: Int = 0
    @JvmField
    var mMissedAlarmCurrentCount: Int = 0
    @JvmField
    var mMissedAlarmRepeatLimit: Int = 0
    @JvmField
    var mCrescendoDuration: Int = 0

    // Alarm volume level in steps; not a percentage
    @JvmField
    var mAlarmVolume: Int = 0

    constructor(calendar: Calendar, alarmId: Long?) : this(calendar) {
        mAlarmId = alarmId
    }

    constructor(calendar: Calendar) {
        mId = INVALID_ID
        this.alarmTime = calendar
        mLabel = ""
        mVibrate = false
        mFlash = false
        mRingtone = null
        mAlarmState = ClockContract.InstancesColumns.SILENT_STATE
        mAutoSilenceDuration = PreferencesDefaultValues.DEFAULT_AUTO_SILENCE_DURATION
        mSnoozeDuration = PreferencesDefaultValues.DEFAULT_ALARM_SNOOZE_DURATION
        mMissedAlarmCurrentCount = 0
        mMissedAlarmRepeatLimit = PreferencesDefaultValues.DEFAULT_MISSED_ALARM_REPEAT_LIMIT.toInt()
        mCrescendoDuration = PreferencesDefaultValues.DEFAULT_VOLUME_CRESCENDO_DURATION
        mAlarmVolume = PreferencesDefaultValues.DEFAULT_ALARM_VOLUME
    }

    constructor(instance: AlarmInstance) {
        this.mId = instance.mId
        this.mYear = instance.mYear
        this.mMonth = instance.mMonth
        this.mDay = instance.mDay
        this.mHour = instance.mHour
        this.mMinute = instance.mMinute
        this.mLabel = instance.mLabel
        this.mVibrate = instance.mVibrate
        this.mFlash = instance.mFlash
        this.mRingtone = instance.mRingtone
        this.mAlarmId = instance.mAlarmId
        this.mAlarmState = instance.mAlarmState
        this.mAutoSilenceDuration = instance.mAutoSilenceDuration
        this.mSnoozeDuration = instance.mSnoozeDuration
        this.mMissedAlarmCurrentCount = instance.mMissedAlarmCurrentCount
        this.mMissedAlarmRepeatLimit = instance.mMissedAlarmRepeatLimit
        this.mCrescendoDuration = instance.mCrescendoDuration
        this.mAlarmVolume = instance.mAlarmVolume
    }

    constructor(c: Cursor, joinedTable: Boolean) {
        if (joinedTable) {
            mId = c.getLong(Alarm.INSTANCE_ID_INDEX)
            mYear = c.getInt(Alarm.INSTANCE_YEAR_INDEX)
            mMonth = c.getInt(Alarm.INSTANCE_MONTH_INDEX)
            mDay = c.getInt(Alarm.INSTANCE_DAY_INDEX)
            mHour = c.getInt(Alarm.INSTANCE_HOUR_INDEX)
            mMinute = c.getInt(Alarm.INSTANCE_MINUTE_INDEX)
            mLabel = c.getString(Alarm.INSTANCE_LABEL_INDEX)
            mVibrate = c.getInt(Alarm.INSTANCE_VIBRATE_INDEX) == 1
            mFlash = c.getInt(Alarm.INSTANCE_FLASH_INDEX) == 1
            mAutoSilenceDuration = c.getInt(Alarm.INSTANCE_AUTO_SILENCE_DURATION_INDEX)
            mSnoozeDuration = c.getInt(Alarm.INSTANCE_SNOOZE_DURATION_INDEX)
            mMissedAlarmCurrentCount = c.getInt(Alarm.INSTANCE_MISSED_ALARM_REPEAT_COUNT_INDEX)
            mMissedAlarmRepeatLimit = c.getInt(Alarm.INSTANCE_MISSED_ALARM_REPEAT_LIMIT_INDEX)
            mCrescendoDuration = c.getInt(Alarm.INSTANCE_CRESCENDO_DURATION_INDEX)
            mAlarmVolume = c.getInt(Alarm.INSTANCE_ALARM_VOLUME_INDEX)
        } else {
            mId = c.getLong(ID_INDEX)
            mYear = c.getInt(YEAR_INDEX)
            mMonth = c.getInt(MONTH_INDEX)
            mDay = c.getInt(DAY_INDEX)
            mHour = c.getInt(HOUR_INDEX)
            mMinute = c.getInt(MINUTES_INDEX)
            mLabel = c.getString(LABEL_INDEX)
            mVibrate = c.getInt(VIBRATE_INDEX) == 1
            mFlash = c.getInt(FLASH_INDEX) == 1
            mAutoSilenceDuration = c.getInt(AUTO_SILENCE_DURATION_INDEX)
            mSnoozeDuration = c.getInt(SNOOZE_DURATION_INDEX)
            mMissedAlarmCurrentCount = c.getInt(MISSED_ALARM_REPEAT_COUNT_INDEX)
            mMissedAlarmRepeatLimit = c.getInt(MISSED_ALARM_MAX_COUNT_INDEX)
            mCrescendoDuration = c.getInt(CRESCENDO_DURATION_INDEX)
            mAlarmVolume = c.getInt(ALARM_VOLUME_INDEX)
        }
        if (c.isNull(RINGTONE_INDEX)) {
            // Should we be saving this with the current ringtone or leave it null
            // so it changes when user changes default ringtone?
            mRingtone = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        } else {
            mRingtone = Uri.parse(c.getString(RINGTONE_INDEX))
        }

        if (!c.isNull(ALARM_ID_INDEX)) {
            mAlarmId = c.getLong(ALARM_ID_INDEX)
        }
        mAlarmState = c.getInt(ALARM_STATE_INDEX)
    }

    fun getLabelOrDefault(context: Context): String {
        val label = mLabel
        return if (label.isNullOrEmpty()) context.getString(R.string.default_label) else label
    }

    var alarmTime: Calendar
        /**
         * Return the time when a alarm should fire.
         *
         * @return the time
         */
        get() {
            val calendar = Calendar.getInstance()
            calendar.set(Calendar.YEAR, mYear)
            calendar.set(Calendar.MONTH, mMonth)
            calendar.set(Calendar.DAY_OF_MONTH, mDay)
            calendar.set(Calendar.HOUR_OF_DAY, mHour)
            calendar.set(Calendar.MINUTE, mMinute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar
        }
        set(calendar) {
            mYear = calendar.get(Calendar.YEAR)
            mMonth = calendar.get(Calendar.MONTH)
            mDay = calendar.get(Calendar.DAY_OF_MONTH)
            mHour = calendar.get(Calendar.HOUR_OF_DAY)
            mMinute = calendar.get(Calendar.MINUTE)
        }

    /**
     * Return the time when the notification should be shown.
     *
     * @return the time
     */
    fun getNotificationTime(context: Context): Calendar {
        val calendar = this.alarmTime
        val getAlarmNotificationReminderTime =
            SettingsDAO.getAlarmNotificationReminderTime(getDefaultSharedPreferences(context))
        calendar.add(Calendar.MINUTE, -getAlarmNotificationReminderTime)
        return calendar
    }

    val missedTimeToLive: Calendar
        /**
         * Return the time when a missed notification should be removed.
         *
         * @return the time
         */
        get() {
            val calendar = this.alarmTime
            calendar.add(
                Calendar.HOUR,
                MISSED_TIME_TO_LIVE_HOUR_OFFSET
            )
            return calendar
        }

    /**
     * Return the time when the alarm should stop firing and be marked as missed.
     *
     * @return the time when alarm should be silence, or null if never
     */
    fun getTimeout(context: Context?): Calendar? {
        val calendar = this.alarmTime

        if (mAutoSilenceDuration == PreferencesDefaultValues.TIMEOUT_NEVER) {
            // Alarm silence has been set to "Never"
            return null
        } else if (mAutoSilenceDuration == PreferencesDefaultValues.TIMEOUT_END_OF_RINGTONE) {
            // Alarm silence has been set to "At the end of the ringtone"
            // or "Dismiss alarm when ringtone ends" has been ticked in the expanded alarm view
            val milliSeconds = RingtoneUtils.getRingtoneDuration(context, mRingtone)
            calendar.add(Calendar.MILLISECOND, milliSeconds)
        } else {
            calendar.add(Calendar.SECOND, mAutoSilenceDuration)
        }

        return calendar
    }

    override fun equals(other: Any?): Boolean {
        if (other !is AlarmInstance) return false
        return mId == other.mId
    }

    override fun hashCode(): Int {
        return mId.hashCode()
    }

    override fun toString(): String {
        return "AlarmInstance{" +
                "mId=" + mId +
                ", mYear=" + mYear +
                ", mMonth=" + mMonth +
                ", mDay=" + mDay +
                ", mHour=" + mHour +
                ", mMinute=" + mMinute +
                ", mLabel=" + mLabel +
                ", mVibrate=" + mVibrate +
                ", mFlash=" + mFlash +
                ", mRingtone=" + mRingtone +
                ", mAlarmId=" + mAlarmId +
                ", mAlarmState=" + mAlarmState +
                ", mAutoSilenceDuration=" + mAutoSilenceDuration +
                ", mSnoozeDuration=" + mSnoozeDuration +
                ", mMissedAlarmCurrentCount=" + mMissedAlarmCurrentCount +
                ", mMissedAlarmRepeatLimit=" + mMissedAlarmRepeatLimit +
                ", mCrescendoDuration=" + mCrescendoDuration +
                ", mAlarmVolume=" + mAlarmVolume +
                '}'
    }

    companion object {
        /**
         * AlarmInstances start with an invalid id when it hasn't been saved to the database.
         */
        val INVALID_ID: Long = -1

        /**
         * Offset from alarm time to stop showing missed notification.
         */
        private const val MISSED_TIME_TO_LIVE_HOUR_OFFSET = 12

        private val QUERY_COLUMNS = arrayOf<String?>(
            BaseColumns._ID,
            ClockContract.InstancesColumns.YEAR,
            ClockContract.InstancesColumns.MONTH,
            ClockContract.InstancesColumns.DAY,
            ClockContract.InstancesColumns.HOUR,
            ClockContract.InstancesColumns.MINUTES,
            "label",  // AlarmSettingColumns.LABEL
            "vibrate",  // AlarmSettingColumns.VIBRATE
            "flash",  // AlarmSettingColumns.FLASH
            "ringtone",  // AlarmSettingColumns.RINGTONE
            ClockContract.InstancesColumns.ALARM_ID,
            ClockContract.InstancesColumns.ALARM_STATE,
            "autoSilenceDuration",  // AlarmSettingColumns.AUTO_SILENCE_DURATION
            "snoozeDuration",  // AlarmSettingColumns.SNOOZE_DURATION
            ClockContract.InstancesColumns.MISSED_ALARM_REPEAT_COUNT,
            "missed_alarm_repeat_limit",  // AlarmSettingColumns.MISSED_ALARM_REPEAT_LIMIT
            "crescendoDuration",  // AlarmSettingColumns.CRESCENDO_DURATION
            "alarmVolume"  // AlarmSettingColumns.ALARM_VOLUME
        )

        /**
         * These save calls to cursor.getColumnIndexOrThrow()
         * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
         */
        private const val ID_INDEX = 0
        private const val YEAR_INDEX = 1
        private const val MONTH_INDEX = 2
        private const val DAY_INDEX = 3
        private const val HOUR_INDEX = 4
        private const val MINUTES_INDEX = 5
        private const val LABEL_INDEX = 6
        private const val VIBRATE_INDEX = 7
        private const val FLASH_INDEX = 8
        private const val RINGTONE_INDEX = 9
        private const val ALARM_ID_INDEX = 10
        private const val ALARM_STATE_INDEX = 11
        private const val AUTO_SILENCE_DURATION_INDEX = 12
        private const val SNOOZE_DURATION_INDEX = 13
        private const val MISSED_ALARM_REPEAT_COUNT_INDEX = 14
        private const val MISSED_ALARM_MAX_COUNT_INDEX = 15
        private const val CRESCENDO_DURATION_INDEX = 16
        private const val ALARM_VOLUME_INDEX = 17

        private val COLUMN_COUNT: Int = ALARM_VOLUME_INDEX + 1
        @JvmStatic
        fun createContentValues(instance: AlarmInstance): ContentValues {
            val values: ContentValues = ContentValues(COLUMN_COUNT)
            if (instance.mId != INVALID_ID) {
                values.put(BaseColumns._ID, instance.mId)
            }

            values.put(ClockContract.InstancesColumns.YEAR, instance.mYear)
            values.put(ClockContract.InstancesColumns.MONTH, instance.mMonth)
            values.put(ClockContract.InstancesColumns.DAY, instance.mDay)
            values.put(ClockContract.InstancesColumns.HOUR, instance.mHour)
            values.put(ClockContract.InstancesColumns.MINUTES, instance.mMinute)
            values.put("label", instance.mLabel)  // AlarmSettingColumns.LABEL
            values.put("vibrate", if (instance.mVibrate) 1 else 0)  // AlarmSettingColumns.VIBRATE
            values.put("flash", if (instance.mFlash) 1 else 0)  // AlarmSettingColumns.FLASH
            if (instance.mRingtone == null) {
                // We want to put null in the database, so we'll be able
                // to pick up on changes to the default alarm
                values.putNull("ringtone")  // AlarmSettingColumns.RINGTONE
            } else {
                values.put("ringtone", instance.mRingtone.toString())  // AlarmSettingColumns.RINGTONE
            }
            values.put(ClockContract.InstancesColumns.ALARM_ID, instance.mAlarmId)
            values.put(ClockContract.InstancesColumns.ALARM_STATE, instance.mAlarmState)
            values.put("autoSilenceDuration", instance.mAutoSilenceDuration)  // AlarmSettingColumns.AUTO_SILENCE_DURATION
            values.put("snoozeDuration", instance.mSnoozeDuration)  // AlarmSettingColumns.SNOOZE_DURATION
            values.put(
                ClockContract.InstancesColumns.MISSED_ALARM_REPEAT_COUNT,
                instance.mMissedAlarmCurrentCount
            )
            values.put(
                "missed_alarm_repeat_limit",  // AlarmSettingColumns.MISSED_ALARM_REPEAT_LIMIT
                instance.mMissedAlarmRepeatLimit
            )
            values.put("crescendoDuration", instance.mCrescendoDuration)  // AlarmSettingColumns.CRESCENDO_DURATION
            values.put("alarmVolume", instance.mAlarmVolume)  // AlarmSettingColumns.ALARM_VOLUME

            return values
        }

        @JvmStatic
        fun createIntent(context: Context?, cls: Class<*>?, instanceId: Long): Intent {
            return Intent(context, cls).setData(getContentUri(instanceId))
        }

        @JvmStatic
        fun getId(contentUri: Uri): Long {
            return ContentUris.parseId(contentUri)
        }

        /**
         * @return the [Uri] identifying the alarm instance
         */
        @JvmStatic
        fun getContentUri(instanceId: Long): Uri {
            return ContentUris.withAppendedId(ClockContract.InstancesColumns.CONTENT_URI, instanceId)
        }

        /**
         * Get alarm instance from instanceId.
         *
         * @param cr         provides access to the content model
         * @param instanceId for the desired instance.
         * @return instance if found, null otherwise
         */
        @JvmStatic
        fun getInstance(cr: ContentResolver, instanceId: Long): AlarmInstance? {
            cr.query(getContentUri(instanceId), QUERY_COLUMNS, null, null, null).use { cursor ->
                if (cursor != null && cursor.moveToFirst()) {
                    return AlarmInstance(cursor, false)
                }
            }
            return null
        }

        /**
         * Get an alarm instances by alarmId.
         *
         * @param contentResolver provides access to the content model
         * @param alarmId         of instances desired.
         * @return list of alarms instances that are owned by alarmId.
         */
        @JvmStatic
        fun getInstancesByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): MutableList<AlarmInstance> {
            return getInstances(contentResolver, ClockContract.InstancesColumns.ALARM_ID + "=" + alarmId)
        }

        /**
         * Get the next instance of an alarm given its alarmId
         *
         * @param contentResolver provides access to the content model
         * @param alarmId         of instance desired
         * @return the next instance of an alarm by alarmId.
         */
        @JvmStatic
        fun getNextUpcomingInstanceByAlarmId(
            contentResolver: ContentResolver,
            alarmId: Long
        ): AlarmInstance? {
            val alarmInstances: MutableList<AlarmInstance> =
                getInstancesByAlarmId(contentResolver, alarmId)
            if (alarmInstances.isEmpty()) {
                return null
            }
            var nextAlarmInstance = alarmInstances.get(0)
            for (instance in alarmInstances) {
                if (instance.alarmTime.before(nextAlarmInstance.alarmTime)) {
                    nextAlarmInstance = instance
                }
            }
            return nextAlarmInstance
        }

        /**
         * Get alarm instances in the specified state.
         */
        @JvmStatic
        fun getInstancesByState(
            contentResolver: ContentResolver, state: Int
        ): MutableList<AlarmInstance> {
            return getInstances(contentResolver, ClockContract.InstancesColumns.ALARM_STATE + "=" + state)
        }

        /**
         * Get a list of instances given selection.
         *
         * @param cr            provides access to the content model
         * @param selection     A filter declaring which rows to return, formatted as an
         * SQL WHERE clause (excluding the WHERE itself). Passing null will
         * return all rows for the given URI.
         * @param selectionArgs You may include ?s in selection, which will be
         * replaced by the values from selectionArgs, in the order that they
         * appear in the selection. The values will be bound as Strings.
         * @return list of alarms matching where clause or empty list if none found.
         */
        @JvmStatic
        fun getInstances(
            cr: ContentResolver, selection: String?,
            vararg selectionArgs: String?
        ): MutableList<AlarmInstance> {
            val result: MutableList<AlarmInstance> = LinkedList<AlarmInstance>()
            cr.query(ClockContract.InstancesColumns.CONTENT_URI, QUERY_COLUMNS, selection, selectionArgs, null)
                .use { cursor ->
                    if (cursor != null && cursor.moveToFirst()) {
                        do {
                            result.add(AlarmInstance(cursor, false))
                        } while (cursor.moveToNext())
                    }
                }
            return result
        }

        @JvmStatic
        fun addInstance(
            contentResolver: ContentResolver,
            instance: AlarmInstance
        ) {
            // Make sure we are not adding a duplicate instances. This is not a
            // fix and should never happen. This is only a safe guard against bad code, and you
            // should fix the root issue if you see the error message.
            val dupSelector = ClockContract.InstancesColumns.ALARM_ID + " = " + instance.mAlarmId
            for (otherInstances in getInstances(contentResolver, dupSelector)) {
                if (otherInstances.alarmTime == instance.alarmTime) {
                    Log.i("ShortcutController", "Detected duplicate instance in DB. Updating " + otherInstances + " to " + instance)
                    // Copy over the new instance values and update the db
                    instance.mId = otherInstances.mId
                    updateInstance(contentResolver, instance)
                    return
                }
            }

            val values: ContentValues = createContentValues(instance)
            val uri = contentResolver.insert(ClockContract.InstancesColumns.CONTENT_URI, values)
            instance.mId = Companion.getId(uri!!)
        }

        @JvmStatic
        fun updateInstance(contentResolver: ContentResolver, instance: AlarmInstance) {
            if (instance.mId == INVALID_ID) return
            val values: ContentValues = createContentValues(instance)
            contentResolver.update(getContentUri(instance.mId), values, null, null)
        }

        @JvmStatic
        fun deleteInstance(contentResolver: ContentResolver, instanceId: Long) {
            if (instanceId == INVALID_ID) return
            contentResolver.delete(getContentUri(instanceId), "", null)
        }

        @JvmStatic
        fun deleteOtherInstances(
            context: Context?, contentResolver: ContentResolver,
            alarmId: Long, instanceId: Long
        ) {
            val instances: MutableList<AlarmInstance> =
                getInstancesByAlarmId(contentResolver, alarmId)
            for (instance in instances) {
                if (instance.mId != instanceId) {
                    AlarmStateManager.unregisterInstance(context, instance)
                    deleteInstance(contentResolver, instance.mId)
                }
            }
        }
    }
}

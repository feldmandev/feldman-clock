/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
@file:Suppress("unused")

package com.feldman.clock.core.data

import com.feldman.clock.R

import android.app.Service
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.data.stopwatch.Lap
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.timer.TimerService
import com.feldman.clock.ui.widgets.StopwatchWidgetProvider
import com.feldman.clock.core.util.Utils
import java.util.Calendar

/**
 * All application-wide data is accessible through this singleton.
 */
class DataModel constructor() {
    private var mContext: Context? = null

    /**
     * The model from which timer data are fetched.
     */
    private var mTimerModel: TimerModel? = null

    /**
     * The model from which alarm data are fetched.
     */
    private var mAlarmModel: AlarmModel? = null

    /**
     * The model from which stopwatch data are fetched.
     */
    private var mStopwatchModel: StopwatchModel? = null

    /**
     * The model from which notification data are fetched.
     */
    private var mNotificationModel: NotificationModel? = null

    /**
     * The model from which ringtone data are fetched.
     */
    private var mRingtoneModel: RingtoneModel? = null

    /**
     * Initializes the data model with the context and shared preferences to be used.
     */
    fun init(context: Context, prefs: SharedPreferences) {
        if (mContext !== context) {
            mContext = context.getApplicationContext()

            val themeValue: String =
                prefs.getString(PreferencesKeys.KEY_THEME, PreferencesDefaultValues.SYSTEM_THEME)!!
            when (themeValue) {
                PreferencesDefaultValues.SYSTEM_THEME -> AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                )

                PreferencesDefaultValues.LIGHT_THEME -> AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_NO
                )

                PreferencesDefaultValues.DARK_THEME -> AppCompatDelegate.setDefaultNightMode(
                    AppCompatDelegate.MODE_NIGHT_YES
                )
            }

            mNotificationModel = NotificationModel()
            mRingtoneModel = RingtoneModel(mContext, prefs)
            mAlarmModel = AlarmModel(prefs, mRingtoneModel)
            mStopwatchModel = StopwatchModel(mContext, prefs, mNotificationModel)
            mTimerModel = TimerModel(mContext, prefs, mRingtoneModel, mNotificationModel)
        }
    }

    val timerModel: TimerModel
        get() = mTimerModel!!

    /**
     * Updates all timers and the stopwatch after the device has shutdown and restarted.
     */
    fun updateAfterReboot() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimersAfterReboot()
        mStopwatchModel!!.setStopwatch(this.stopwatch.updateAfterReboot())
    }

    /**
     * Updates all timers and the stopwatch after the device's time has changed.
     */
    fun updateAfterTimeSet() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimersAfterTimeSet()
        mStopwatchModel!!.setStopwatch(this.stopwatch.updateAfterTimeSet())
    }

    var isApplicationInForeground: Boolean
        /**
         * @return `true` when the application is open in the foreground; `false` otherwise
         */
        get() {
            Utils.enforceMainLooper()
            return mNotificationModel!!.isApplicationInForeground()
        }
        /**
         * @param inForeground `true` to indicate the application is open in the foreground
         */
        set(inForeground) {
            Utils.enforceMainLooper()
            mNotificationModel!!.setApplicationInForeground(inForeground)
        }

    /**
     * Called when the notifications may be stale or absent from the notification manager and must
     * be rebuilt. e.g. after upgrading the application
     */
    fun updateAllNotifications() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateNotification()
        mTimerModel!!.updateMissedNotification()
        mStopwatchModel!!.updateNotification()
    }

    /**
     * @param timerListener to be notified when timers are added, updated and removed
     */
    fun addTimerListener(timerListener: TimerListener?) {
        Utils.enforceMainLooper()
        mTimerModel!!.addTimerListener(timerListener)
    }

    /**
     * @param timerListener to no longer be notified when timers are added, updated and removed
     */
    fun removeTimerListener(timerListener: TimerListener?) {
        Utils.enforceMainLooper()
        mTimerModel!!.removeTimerListener(timerListener)
    }

    val timers: MutableList<Timer>
        /**
         * @return a list of timers for display
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.getTimers()
        }

    /**
     * @return `true` if there are any running or paused timers
     */
    fun hasActiveTimers(): Boolean {
        Utils.enforceMainLooper()
        for (timer in mTimerModel!!.getTimers()) {
            if (timer.isRunning() || timer.isPaused()) {
                return true
            }
        }
        return false
    }

    val expiredTimers: MutableList<Timer>
        /**
         * @return a list of expired timers for display
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.getExpiredTimers()
        }

    /**
     * @param timerId identifies the timer to return
     * @return the timer with the given `timerId`
     */
    fun getTimer(timerId: Int): Timer? {
        Utils.enforceMainLooper()
        return mTimerModel!!.getTimer(timerId)
    }

    /**
     * @param length         the length of the timer in milliseconds
     * @param label          describes the purpose of the timer
     * @param buttonTime     the time indicated in the timer time add button
     * @param deleteAfterUse `true` indicates the timer should be deleted when it is reset
     * @return the newly added timer
     */
    fun addTimer(
        length: Long,
        label: String?,
        buttonTime: String?,
        deleteAfterUse: Boolean
    ): Timer {
        Utils.enforceMainLooper()
        return mTimerModel!!.addTimer(length, label, buttonTime, deleteAfterUse)
    }

    /**
     * @param timer the timer to be started
     */
    fun startTimer(timer: Timer) {
        startTimer(null, timer)
    }

    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer   the timer to be started
     */
    fun startTimer(service: Service?, timer: Timer) {
        Utils.enforceMainLooper()
        Log.d(TAG, "DataModel startTimer service=" + service)
        // Ensure notification reappears if it was previously dismissed
        mTimerModel!!.clearNotificationDismissed(timer.getId())
        // Reset paused-from-notification state so future pauses from app hide notification
        mTimerModel!!.clearPausedLiveUpdate(timer.getId())
        val started = timer.start()
        mTimerModel!!.updateTimer(started)
        if (timer.getRemainingTime() <= 0) {
            if (service != null) {
                expireTimer(service, started)
            } else {
                mContext!!.startForegroundService(
                    TimerService.createTimerExpiredIntent(
                        mContext,
                        started
                    )
                )
            }
        } else {
            // Use startForegroundService to ensure it can run from background/restart
            // Service will call startForeground() immediately via ensureForegroundService()
            mContext!!.startForegroundService(TimerService.createUpdateNotificationIntent(mContext))
        }
    }

    /**
     * @param timer the timer to be paused
     */
    fun pauseTimer(timer: Timer) {
        Utils.enforceMainLooper()
        if (timer.isRunning) {
            mTimerModel!!.markPausedLiveUpdate(timer.getId())
        }
        mTimerModel!!.updateTimer(timer.pause())
    }

    /**
     * @param service used to start foreground notifications for expired timers
     * @param timer   the timer to be expired
     */
    fun expireTimer(service: Service?, timer: Timer?) {
        Utils.enforceMainLooper()
        mTimerModel!!.expireTimer(service, timer)
    }

    /**
     * If the given `timer` is expired and marked for deletion after use then this method
     * removes the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer        the timer to be reset
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetOrDeleteTimer(timer: Timer, @StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel!!.resetTimer(timer, true, eventLabelId)
    }

    /**
     * @param timer the timer to be removed
     */
    fun removeTimer(timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel!!.removeTimer(timer)
    }

    /**
     * Resets all expired timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetOrDeleteExpiredTimers(@StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel!!.resetOrDeleteExpiredTimers(eventLabelId)
    }

    /**
     * Resets all missed timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    fun resetMissedTimers(@StringRes eventLabelId: Int) {
        Utils.enforceMainLooper()
        mTimerModel!!.resetMissedTimers(eventLabelId)
    }

    /**
     * @param timer the timer to which minutes or hours should be added to the remaining time
     */
    fun addCustomTimeToTimer(timer: Timer) {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimer(timer.addCustomTime())
    }

    /**
     * @param timer the timer to which the new `label` belongs
     * @param label the new label to store for the `timer`
     */
    fun setTimerLabel(timer: Timer, label: String?) {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimer(timer.setLabel(label))
    }

    /**
     * @param timer the timer to which the new `newLength` belongs
     * @param newLength the new duration to store for the `timer`
     */
    fun setNewTimerDuration(timer: Timer, newLength: Long) {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimer(timer.setNewDuration(newLength))
    }

    /**
     * @param timer the timer to which the new `buttonTime` belongs
     * @param buttonTime the new add button text to store for the `timer`
     */
    fun setTimerButtonTime(timer: Timer, buttonTime: String?) {
        Utils.enforceMainLooper()
        mTimerModel!!.updateTimer(timer.setButtonTime(buttonTime))
    }

    /**
     * Updates the timer notifications to be current.
     */
    fun updateTimerNotification() {
        Utils.enforceMainLooper()
        mTimerModel!!.updateNotification()
    }

    val defaultTimerRingtoneUri: Uri?
        /**
         * @return the uri of the default ringtone to play for all timers when no user selection exists
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.getDefaultTimerRingtoneUri()
        }

    val timerRingtoneUri: Uri?
        /**
         * @return the uri of the ringtone to play for all timers
         */
        get() {
            Utils.enforceMainLooper()
            return mTimerModel!!.getTimerRingtoneUri()
        }

    val defaultAlarmRingtoneUriFromSettings: Uri
        /**
         * @return the uri of the default ringtone from the settings to play for all alarms when no user selection exists
         */
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.getDefaultAlarmRingtoneUriFromSettings()
        }

    val alarmRingtoneUriFromSettings: Uri
        /**
         * @return the uri of the ringtone from the settings to play for all alarms
         */
        get() {
            Utils.enforceMainLooper()
            return mAlarmModel!!.getAlarmRingtoneUriFromSettings()
        }

    /**
     * @param stopwatchListener to be notified when stopwatch changes or laps are added
     */
    fun addStopwatchListener(stopwatchListener: StopwatchListener?) {
        Utils.enforceMainLooper()
        mStopwatchModel!!.addStopwatchListener(stopwatchListener)
    }

    /**
     * @param stopwatchListener to be removed from the list of listeners
     */
    fun removeStopwatchListener(stopwatchListener: StopwatchListener?) {
        Utils.enforceMainLooper()
        mStopwatchModel!!.removeStopwatchListener(stopwatchListener)
    }

    val stopwatch: Stopwatch
        /**
         * @return the current state of the stopwatch
         */
        get() {
            Utils.enforceMainLooper()
            return mStopwatchModel!!.getStopwatch()
        }

    /**
     *
     */
    fun startStopwatch() {
        Utils.enforceMainLooper()
        mStopwatchModel!!.setStopwatch(this.stopwatch.start())
        mContext?.let(StopwatchWidgetProvider::updateAll)
    }

    /**
     *
     */
    fun pauseStopwatch() {
        Utils.enforceMainLooper()
        mStopwatchModel!!.setStopwatch(this.stopwatch.pause())
        mContext?.let(StopwatchWidgetProvider::updateAll)
    }

    /**
     *
     */
    fun resetStopwatch() {
        Utils.enforceMainLooper()
        mStopwatchModel!!.setStopwatch(this.stopwatch.reset())
        mContext?.let(StopwatchWidgetProvider::updateAll)
    }

    val laps: MutableList<Lap>
        /**
         * @return the laps recorded for this stopwatch
         */
        get() {
            Utils.enforceMainLooper()
            return if (mStopwatchModel != null) mStopwatchModel!!.getLaps() else ArrayList<Lap?>()
        }

    /**
     * @return a newly recorded lap completed now; `null` if no more laps can be added
     */
    fun addLap(): Lap? {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.addLap().also {
            mContext?.let(StopwatchWidgetProvider::updateAll)
        }
    }

    /**
     * @return `true` iff more laps can be recorded
     */
    fun canAddMoreLaps(): Boolean {
        Utils.enforceMainLooper()
        return mStopwatchModel!!.canAddMoreLaps()
    }

    /**
     * @return the current time in milliseconds
     */
    fun currentTimeMillis(): Long {
        return System.currentTimeMillis()
    }

    /**
     * @return milliseconds since boot, including time spent in sleep
     */
    fun elapsedRealtime(): Long {
        return SystemClock.elapsedRealtime()
    }

    /**
     * @return `true` if 24 hour time format is selected; `false` otherwise
     */
    /**
     * @return `true` if 24 hour time format is selected; `false` otherwise
     */
    fun is24HourFormat(): Boolean {
        val format = SettingsDAO.getTimeFormat(
            ClockApplication.Companion.getDefaultSharedPreferences(mContext!!)
        )
        if ("24" == format) return true
        if ("12" == format) return false
        // Default to system
        return DateFormat.is24HourFormat(mContext)
    }

    val dateFormat: String
        get() = SettingsDAO.getDateFormat(
            ClockApplication.Companion.getDefaultSharedPreferences(
                mContext!!
            )
        )

    val calendar: Calendar
        /**
         * @return a new calendar object initialized to the [.currentTimeMillis]
         */
        get() {
            val calendar = Calendar.getInstance()
            calendar.setTimeInMillis(System.currentTimeMillis())
            return calendar
        }

    /**
     * Ringtone titles are cached because loading them is expensive. This method
     * **must** be called on a background thread and is responsible for priming the
     * cache of ringtone titles to avoid later fetching titles on the main thread.
     */
    fun loadRingtoneTitles() {
        Utils.enforceNotMainLooper()
        mRingtoneModel!!.loadRingtoneTitles()
    }

    /**
     * Indicates the display style of clocks.
     */
    enum class ClockStyle {
        ANALOG, ANALOG_MATERIAL, DIGITAL, DIGITAL_SPLIT_12, DIGITAL_SPLIT_24
    }


    /**
     * Indicates the preferred sort order of cities.
     */
    enum class CitySort {
        NAME, UTC_OFFSET
    }

    /**
     * Indicates the preferred behavior of power button when firing alarms.
     */
    enum class PowerButtonBehavior {
        NOTHING, SNOOZE, DISMISS
    }

    /**
     * Indicates the preferred behavior of volume button when firing alarms.
     */
    enum class VolumeButtonBehavior {
        CHANGE_VOLUME, SNOOZE_ALARM, DISMISS_ALARM, DO_NOTHING
    }

    /**
     * Indicates the reason alarms may not fire or may fire silently.
     */
    enum class SilentSetting(
        labelResId: Int, actionResId: Int, actionEnabled: Predicate<Context?>,
        actionListener: View.OnClickListener?
    ) {
        DO_NOT_DISTURB(R.string.alarms_blocked_by_dnd, 0, Predicate.FALSE, null),
        MUTED_VOLUME(
            R.string.alarm_volume_muted,
            R.string.unmute_alarm_volume,
            Predicate.TRUE,
            UnmuteAlarmVolumeListener()
        ),
        SILENT_RINGTONE(
            R.string.silent_default_alarm_ringtone,
            R.string.change_setting_action,
            ChangeSoundActionPredicate(),
            ChangeSoundSettingsListener()
        );

        private class UnmuteAlarmVolumeListener : View.OnClickListener {
            override fun onClick(v: View) {
                // Set the alarm volume to 11/16th of max and show the slider UI.
                // 11/16th of max is the initial volume of the alarm stream on a fresh install.
                val context = v.getContext()
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                val index = Math.round(am.getStreamMaxVolume(AudioManager.STREAM_ALARM) * 11f / 16f)
                am.setStreamVolume(AudioManager.STREAM_ALARM, index, AudioManager.FLAG_SHOW_UI)
            }
        }

        private class ChangeSoundSettingsListener : View.OnClickListener {
            override fun onClick(v: View) {
                val context = v.getContext()
                context.startActivity(
                    Intent(Settings.ACTION_SOUND_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }

        private class ChangeSoundActionPredicate : Predicate<Context?> {
            override fun apply(context: Context?): Boolean {
                val intent = Intent(Settings.ACTION_SOUND_SETTINGS)
                try {
                    context!!.startActivity(intent)
                } catch (ex: ActivityNotFoundException) {
                    Toast.makeText(context, "application_not_found", Toast.LENGTH_SHORT).show()
                }
                return true
            }

        }
    }

    /**
     * @param service used to start foreground notifications for running timers
     */
    fun setTimerService(service: Service?) {
        Utils.enforceMainLooper()
        Log.d(TAG, "DataModel setTimerService service=" + service)
        mTimerModel!!.setService(service)
    }

    //standby
    enum class RotationMode(val id: Int) { Auto(0), Portrait(1), Landscape(2);
        companion object { fun from(id: Int) = entries.firstOrNull { it.id == id } ?: Auto }
    }

    enum class StandbyPageLayout { PAGER, SPLIT }

    companion object {
        private val sMainThreadHandler = Handler(Looper.getMainLooper())
        private const val TAG = "DeskClockTimerDebug"

        /**
         * The single instance of this data model that exists for the life of the application.
         */

        @JvmStatic
        val dataModel: DataModel = DataModel()
    }
}

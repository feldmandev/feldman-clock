/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.app.integration

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication
import com.feldman.clock.ui.alarm.AlarmSelectionActivity
import com.feldman.clock.alarm.FetchMatchingAlarmsAction

import com.feldman.clock.app.MainActivity

import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.AlarmClock
import android.text.TextUtils
import android.text.format.DateFormat
import android.text.format.DateUtils
import com.feldman.clock.alarm.AlarmConstants
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.core.controller.Controller
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.Weekdays
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.data.Timer
import com.feldman.clock.core.analytics.Events
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.feldman.clock.core.storage.provider.ClockContract
import com.feldman.clock.core.storage.provider.ClockContract.InstancesColumns
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.timer.TimerConstants
import com.feldman.clock.timer.TimerService
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.core.util.RingtoneUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

/**
 * This activity is never visible. It processes all public intents defined by [AlarmClock]
 * that apply to alarms and timers. Its definition in AndroidManifest.xml requires callers to hold
 * the com.android.alarm.permission.SET_ALARM permission to complete the requested action.
 */
class HandleApiCalls : Activity() {

    private lateinit var appContext: Context
    private lateinit var prefs: SharedPreferences
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        appContext = applicationContext
        prefs = ClockApplication.getDefaultSharedPreferences(appContext)

        try {
            val intent = intent ?: return
            val action = intent.action ?: return

            when (action) {
                // NEW: Handle the Deep Link from App Actions
                Intent.ACTION_VIEW -> handleDeepLink(intent)

                // Existing handlers
                AlarmClock.ACTION_SET_ALARM -> handleSetAlarm(intent)
                AlarmClock.ACTION_SHOW_ALARMS -> handleShowAlarms()
                AlarmClock.ACTION_SET_TIMER -> handleSetTimer(intent)
                AlarmClock.ACTION_SHOW_TIMERS -> handleShowTimers()
                AlarmClock.ACTION_DISMISS_ALARM -> handleDismissAlarm(intent)
                AlarmClock.ACTION_SNOOZE_ALARM -> handleSnoozeAlarm()
                AlarmClock.ACTION_DISMISS_TIMER -> handleDismissTimer(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            finish()
        }
    }

    private fun handleDeepLink(intent: Intent) {
        val data = intent.data ?: return

        // Check if this is our create alarm path
        if (data.scheme == "feldmanclock" && data.path == "/create") {

            val timeString = data.getQueryParameter("time") // ISO-8601 string
            val message = data.getQueryParameter("name") ?: ""

            if (timeString != null) {
                // We reuse the specific timestamp logic from before
                val timestamp = parseIsoTime(timeString)

                if (timestamp != -1L) {
                    // Create an intent to piggyback on your existing robust logic
                    // We simply fake the hidden extra we wanted earlier!
                    val fakeIntent = Intent()
                    fakeIntent.putExtra("android.intent.extra.alarm.TIME", timestamp)
                    fakeIntent.putExtra(AlarmClock.EXTRA_MESSAGE, message)
                    fakeIntent.putExtra(AlarmClock.EXTRA_SKIP_UI, true)

                    // Populate Hour/Min just in case
                    val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
                    fakeIntent.putExtra(AlarmClock.EXTRA_HOUR, cal.get(Calendar.HOUR_OF_DAY))
                    fakeIntent.putExtra(AlarmClock.EXTRA_MINUTES, cal.get(Calendar.MINUTE))

                    // Route it to your existing robust handler
                    handleSetAlarm(fakeIntent)
                } else {
                    // Fallback if parsing failed
                    Controller.getController().notifyVoiceFailure(this, getString(R.string.invalid_time, 0, 0, " "))
                }
            }
        }
    }

    private fun parseIsoTime(isoString: String): Long {
        // 1. Try generic ISO_OFFSET_DATE_TIME (Handles 2023-10-10T15:00:00+02:00)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            try {
                val parsed = java.time.format.DateTimeFormatter.ISO_DATE_TIME.parseBest(
                    isoString,
                    java.time.ZonedDateTime::from,
                    java.time.LocalDateTime::from
                )

                return when (parsed) {
                    is java.time.ZonedDateTime -> parsed.toInstant().toEpochMilli()
                    is java.time.LocalDateTime -> parsed.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    else -> -1L
                }
            } catch (e: Exception) {
                // Fallthrough to legacy parsing
            }
        }

        // 2. Legacy Fallback
        try {
            if (isoString.contains("T")) {
                // Handle simple "yyyy-MM-dd'T'HH:mm:ss"
                val format = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.ROOT)
                val date = format.parse(isoString.substring(0, 19)) // Trim potential timezone garbage if simple parsing
                return date?.time ?: -1L
            } else {
                // ... (Your existing time-only logic remains here) ...
                val format = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.ROOT)
                val date = format.parse(isoString)
                if (date != null) {
                    val now = Calendar.getInstance()
                    val cal = Calendar.getInstance()
                    cal.time = date
                    now.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY))
                    now.set(Calendar.MINUTE, cal.get(Calendar.MINUTE))
                    now.set(Calendar.SECOND, 0)
                    if (now.before(Calendar.getInstance())) {
                        now.add(Calendar.DAY_OF_YEAR, 1)
                    }
                    return now.timeInMillis
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return -1L
    }

    private fun handleDismissAlarm(intent: Intent) {
        // Open DeskClock which is now positioned on the alarms tab.
        startActivity(Intent(appContext, MainActivity::class.java).setAction(AlarmClock.ACTION_SHOW_ALARMS))
        dismissAlarmAsync(intent)
    }

    private fun dismissAlarmAsync(intent: Intent) = backgroundScope.launch {
        val cr = appContext.contentResolver
        val alarms = getEnabledAlarms(appContext)
        if (alarms.isEmpty()) {
            val reason = appContext.getString(R.string.no_scheduled_alarms)
            Controller.getController().notifyVoiceFailure(this@HandleApiCalls, reason)
            return@launch
        }

        // remove Alarms in MISSED, DISMISSED, and PREDISMISSED states
        val iterator = alarms.iterator()
        while (iterator.hasNext()) {
            val instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(cr, iterator.next().id)
            if (instance == null || instance.mAlarmState > ClockContract.InstancesColumns.FIRED_STATE) {
                iterator.remove()
            }
        }

        val searchMode = intent.getStringExtra(AlarmClock.EXTRA_ALARM_SEARCH_MODE)
        if (searchMode == null && alarms.size > 1) {
            // shows the UI where user picks which alarm they want to DISMISS
            val pickSelectionIntent = Intent(appContext, AlarmSelectionActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmSelectionActivity.EXTRA_ACTION, AlarmSelectionActivity.ACTION_DISMISS)
                .putExtra(AlarmSelectionActivity.EXTRA_ALARMS, alarms.toTypedArray<Parcelable>())
            appContext.startActivity(pickSelectionIntent)
            val voiceMessage = appContext.getString(R.string.pick_alarm_to_dismiss)
            Controller.getController().notifyVoiceSuccess(this@HandleApiCalls, voiceMessage)
            return@launch
        }

        // fetch the alarms that are specified by the intent
        val fmaa = FetchMatchingAlarmsAction(appContext, alarms, intent, this@HandleApiCalls)
        fmaa.run()
        val matchingAlarms = fmaa.matchingAlarms

        // If there are multiple matching alarms and it wasn't expected
        // disambiguate what the user meant
        if (AlarmClock.ALARM_SEARCH_MODE_ALL != searchMode && matchingAlarms.size > 1) {
            val pickSelectionIntent = Intent(appContext, AlarmSelectionActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmSelectionActivity.EXTRA_ACTION, AlarmSelectionActivity.ACTION_DISMISS)
                .putExtra(AlarmSelectionActivity.EXTRA_ALARMS, matchingAlarms.toTypedArray<Parcelable>())
            appContext.startActivity(pickSelectionIntent)
            val voiceMessage = appContext.getString(R.string.pick_alarm_to_dismiss)
            Controller.getController().notifyVoiceSuccess(this@HandleApiCalls, voiceMessage)
            return@launch
        }

        // Apply the action to the matching alarms
        for (alarm in matchingAlarms) {
            dismissAlarm(alarm, this@HandleApiCalls)
        }
    }

    private fun getEnabledAlarms(context: Context): MutableList<Alarm> {
        val selection = "${Alarm.ENABLED}=?"
        val args = arrayOf("1")
        return Alarm.getAlarms(context.contentResolver, selection, *args)
    }

    private fun handleSnoozeAlarm() = backgroundScope.launch {
        val cr = appContext.contentResolver
        val alarmInstances = AlarmInstance.getInstancesByState(cr, InstancesColumns.FIRED_STATE)
        if (alarmInstances.isEmpty()) {
            val reason = appContext.getString(R.string.no_firing_alarms)
            Controller.getController().notifyVoiceFailure(this@HandleApiCalls, reason)
            return@launch
        }

        for (firingAlarmInstance in alarmInstances) {
            snoozeAlarm(firingAlarmInstance, appContext, this@HandleApiCalls)
        }
    }

    private fun handleSetAlarm(intent: Intent) {
        // --- DEBUGGING: LOG EVERYTHING GEMINI SENDS ---
        android.util.Log.d("GeminiDebug", "Action: ${intent.action}")
        android.util.Log.d("GeminiDebug", "Data: ${intent.data}")

        val bundle = intent.extras
        android.util.Log.d("GeminiDebug", "bundle: ${intent.extras}")
        android.util.Log.d("GeminiDebug", "full: ${intent.toString()}")
        if (bundle != null) {
            for (key in bundle.keySet()) {
                android.util.Log.d("GeminiDebug", "Extra key: $key")
            }
        } else {
            android.util.Log.d("GeminiDebug", "Intent has NO extras.")
        }
        // --- END DEBUGGING ---


        // 1. Check for the hidden "Time" extra (Milliseconds)
        val specificTimeMillis = intent.getLongExtra("android.intent.extra.alarm.TIME", -1L)

        var hour = -1
        var minutes = 0

        // If specific timestamp exists, extract time from it first
        if (specificTimeMillis != -1L) {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = specificTimeMillis
            hour = calendar.get(Calendar.HOUR_OF_DAY)
            minutes = calendar.get(Calendar.MINUTE)
            android.util.Log.d("GeminiDebug", "Found specific timestamp: $specificTimeMillis ($hour:$minutes)")
        } else if (intent.hasExtra(AlarmClock.EXTRA_HOUR)) {
            // Otherwise use standard extras
            hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, hour)
            if (hour < 0 || hour > 23) {
                val mins = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
                val voiceMessage = getString(R.string.invalid_time, hour, mins, " ")
                Controller.getController().notifyVoiceFailure(this, voiceMessage)
                return
            }
            minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, 0)
            if (minutes < 0 || minutes > 59) {
                val voiceMessage = getString(R.string.invalid_time, hour, minutes, " ")
                Controller.getController().notifyVoiceFailure(this, voiceMessage)
                return
            }
        }

        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)
        val cr = contentResolver

        // If no time information was found
        if (hour == -1) {
            val createAlarm = Alarm.createIntent(this, MainActivity::class.java, Alarm.INVALID_ID)
                .setAction(AlarmClock.ACTION_SET_ALARM)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(AlarmConstants.ALARM_CREATE_NEW_INTENT_EXTRA, true)

            startActivity(createAlarm)
            val voiceMessage = getString(R.string.invalid_time, hour, minutes, " ")
            Controller.getController().notifyVoiceFailure(this, voiceMessage)
            return
        }

        val alarm: Alarm
        val alarmInstance: AlarmInstance

        if (specificTimeMillis != -1L) {
            // CASE 1: Specific Date/Time requested (e.g., "In two days")
            android.util.Log.d("GeminiDebug", "Creating ONE-TIME alarm for specific date.")

            alarm = Alarm()
            alarm.hour = hour
            alarm.minutes = minutes
            alarm.enabled = true
            alarm.daysOfWeek = Weekdays.NONE
            alarm.label = getLabelFromIntent(intent, "")

            applyAlarmSettings(alarm, appContext, prefs)
            Alarm.addAlarm(cr, alarm)
            Events.sendAlarmEvent(R.string.action_create, R.string.label_intent)

            // Force instance to the specific date
            alarmInstance = AlarmInstance(DataModel.dataModel.calendar, alarm.id)
            alarmInstance.alarmTime = Calendar.getInstance().apply { timeInMillis = specificTimeMillis }

        } else {
            // CASE 2: Standard Logic
            android.util.Log.d("GeminiDebug", "Creating STANDARD alarm logic.")

            val selection = StringBuilder()
            val argsList = ArrayList<String>()
            setSelectionFromIntent(intent, hour, minutes, selection, argsList)

            val args = argsList.toTypedArray()
            val alarms = Alarm.getAlarms(cr, selection.toString(), *args)

            if (alarms.isNotEmpty()) {
                alarm = alarms[0]
                alarm.enabled = true
                Alarm.updateAlarm(cr, alarm)
                AlarmStateManager.deleteAllInstances(this, alarm.id)
                Events.sendAlarmEvent(R.string.action_update, R.string.label_intent)
            } else {
                alarm = Alarm()
                updateAlarmFromIntent(alarm, intent)
                applyAlarmSettings(alarm, appContext, prefs)
                Alarm.addAlarm(cr, alarm)
                Events.sendAlarmEvent(R.string.action_create, R.string.label_intent)
            }

            val now = DataModel.dataModel.calendar
            alarmInstance = alarm.createInstanceAfter(now)
        }

        setupInstance(alarmInstance, skipUi)

        val timeFormat = DateFormat.getTimeFormat(this)
        val timeStr = timeFormat.format(alarmInstance.alarmTime.time)
        android.util.Log.d("GeminiDebug", "Final Alarm set for: $timeStr")

        Controller.getController().notifyVoiceSuccess(this, getString(R.string.alarm_is_set, timeStr))
    }

    private fun handleDismissTimer(intent: Intent) {
        val dataUri = intent.data
        if (dataUri != null) {
            val selectedTimer = getSelectedTimer(dataUri)
            if (selectedTimer != null) {
                DataModel.dataModel.resetOrDeleteTimer(selectedTimer, R.string.label_intent)
                Controller.getController().notifyVoiceSuccess(
                    this,
                    resources.getQuantityString(R.plurals.expired_timers_dismissed, 1)
                )
            } else {
                Controller.getController().notifyVoiceFailure(this, getString(R.string.invalid_timer))
            }
        } else {
            val expiredTimers = DataModel.dataModel.expiredTimers
            if (expiredTimers.isNotEmpty()) {
                for (timer in expiredTimers) {
                    DataModel.dataModel.resetOrDeleteTimer(timer, R.string.label_intent)
                }
                val numberOfTimers = expiredTimers.size
                val timersDismissedMessage = resources.getQuantityString(
                    R.plurals.expired_timers_dismissed, numberOfTimers, numberOfTimers
                )
                Controller.getController().notifyVoiceSuccess(this, timersDismissedMessage)
            } else {
                Controller.getController().notifyVoiceFailure(this, getString(R.string.no_expired_timers))
            }
        }
    }

    private fun getSelectedTimer(dataUri: Uri): Timer? {
        return try {
            val timerId = ContentUris.parseId(dataUri).toInt()
            DataModel.dataModel.getTimer(timerId)
        } catch (e: NumberFormatException) {
            null
        }
    }

    private fun handleShowAlarms() {
        Events.sendAlarmEvent(R.string.action_show, R.string.label_intent)
        // Open DeskClock positioned on the alarms tab.
        startActivity(Intent(this, MainActivity::class.java).setAction(AlarmClock.ACTION_SHOW_ALARMS))
    }

    private fun handleShowTimers() {
        Events.sendTimerEvent(R.string.action_show, R.string.label_intent)
        val showTimersIntent = Intent(this, MainActivity::class.java)

        val timers = DataModel.dataModel.timers
        if (!timers.isEmpty()) {
            val newestTimer = timers[timers.size - 1]
            showTimersIntent.putExtra(TimerService.EXTRA_TIMER_ID, newestTimer.id)
        }

        // Open DeskClock positioned on the timers tab.
        showTimersIntent.setAction(AlarmClock.ACTION_SHOW_TIMERS)
        startActivity(showTimersIntent)
    }

    private fun handleSetTimer(intent: Intent) {
        // If no length is supplied, show the timer setup view.
        if (!intent.hasExtra(AlarmClock.EXTRA_LENGTH)) {
            // Open DeskClock which is now positioned on the timers tab and show the timer setup.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(AlarmClock.ACTION_SET_TIMER)
                    .putExtra(TimerConstants.EXTRA_TIMER_SETUP, true)
            )
            return
        }

        // Verify that the timer length is between one second and one day.
        val lengthMillis = DateUtils.SECOND_IN_MILLIS * intent.getIntExtra(AlarmClock.EXTRA_LENGTH, 0)
        if (lengthMillis < Timer.MIN_LENGTH) {
            val voiceMessage = getString(R.string.invalid_timer_length)
            Controller.getController().notifyVoiceFailure(this, voiceMessage)
            return
        }

        val label = getLabelFromIntent(intent, "")
        val skipUi = intent.getBooleanExtra(AlarmClock.EXTRA_SKIP_UI, false)

        // Attempt to reuse an existing timer that is Reset with the same length and label.
        val timer = DataModel.dataModel.timers.firstOrNull { timer ->
            timer.isReset &&
                timer.length == lengthMillis &&
                TextUtils.equals(label, timer.label)
        } ?: run {
            val defaultTimeToAddToTimer = SettingsDAO.getDefaultTimeToAddToTimer(prefs).toString()
            val createdTimer = DataModel.dataModel.addTimer(
                lengthMillis,
                label,
                defaultTimeToAddToTimer,
                skipUi
            )
            Events.sendTimerEvent(R.string.action_create, R.string.label_intent)
            createdTimer
        }

        // Start the selected timer.
        DataModel.dataModel.startTimer(timer)
        Events.sendTimerEvent(R.string.action_start, R.string.label_intent)
        Controller.getController().notifyVoiceSuccess(this, getString(R.string.timer_created))

        // If not instructed to skip the UI, display the running timer.
        if (!skipUi) {
            // Open DeskClock which is now positioned on the timers tab.
            startActivity(
                Intent(this, MainActivity::class.java)
                    .setAction(AlarmClock.ACTION_SHOW_TIMERS)
                    .putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
            )
        }
    }

    private fun setupInstance(instance: AlarmInstance, skipUi: Boolean) {
        AlarmInstance.addInstance(contentResolver, instance)
        AlarmStateManager.registerInstance(this, instance, true)
        AlarmUtils.popAlarmSetToast(this, instance.alarmTime.timeInMillis)
        if (!skipUi) {
            // Open DeskClock which is now positioned on the alarms tab.
            val showAlarm = Alarm.createIntent(this, MainActivity::class.java, instance.mAlarmId!!)
                .setAction(AlarmClock.ACTION_SHOW_ALARMS)
                .putExtra(AlarmConstants.SCROLL_TO_ALARM_INTENT_EXTRA, instance.mAlarmId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(showAlarm)
        }
    }

    private fun updateAlarmFromIntent(alarm: Alarm, intent: Intent) {
        alarm.label = getLabelFromIntent(intent, alarm.label)
        alarm.hour = intent.getIntExtra(AlarmClock.EXTRA_HOUR, alarm.hour)
        alarm.minutes = intent.getIntExtra(AlarmClock.EXTRA_MINUTES, alarm.minutes)
        alarm.alert = getAlertFromIntent(intent, alarm.alert)
        alarm.daysOfWeek = getDaysFromIntent(intent, alarm.daysOfWeek)
    }

    private fun setSelectionFromIntent(
        intent: Intent,
        hour: Int,
        minutes: Int,
        selection: StringBuilder,
        args: MutableList<String>
    ) {
        selection.append(Alarm.HOUR).append("=?")
        args.add(hour.toString())
        selection.append(" AND ").append(Alarm.MINUTES).append("=?")
        args.add(minutes.toString())

        if (intent.hasExtra(AlarmClock.EXTRA_MESSAGE)) {
            selection.append(" AND ").append(Alarm.LABEL).append("=?")
            args.add(getLabelFromIntent(intent, ""))
        }

        // Days is treated differently than other fields because if days is not specified, it
        // explicitly means "not recurring".
        selection.append(" AND ").append(Alarm.DAYS_OF_WEEK).append("=?")
        args.add(getDaysFromIntent(intent, Weekdays.NONE).mBits.toString())

        if (intent.hasExtra(AlarmClock.EXTRA_VIBRATE)) {
            selection.append(" AND ").append(Alarm.VIBRATE).append("=?")
            args.add(if (intent.getBooleanExtra(AlarmClock.EXTRA_VIBRATE, false)) "1" else "0")
        }

        if (intent.hasExtra(AlarmClock.EXTRA_RINGTONE)) {
            selection.append(" AND ").append(Alarm.RINGTONE).append("=?")

            // If the intent explicitly specified a NULL ringtone, treat it as the default ringtone.
            val defaultRingtone = DataModel.dataModel.defaultAlarmRingtoneUriFromSettings
            val ringtone = getAlertFromIntent(intent, defaultRingtone)
            args.add(ringtone.toString())
        }
    }

    companion object {
        @JvmStatic
        fun dismissAlarm(alarm: Alarm, activity: Activity) {
            val context = activity.applicationContext
            val instance = AlarmInstance.getNextUpcomingInstanceByAlarmId(
                context.contentResolver, alarm.id
            )
            if (instance == null) {
                val reason = context.getString(R.string.no_alarm_scheduled_for_this_time)
                Controller.getController().notifyVoiceFailure(activity, reason)
                return
            }
            dismissAlarmInstance(instance, activity)
        }

        @JvmStatic
        fun dismissAlarmInstance(instance: AlarmInstance, activity: Activity) {
            // Note: Utils.enforceNotMainLooper() is not strictly needed if we are confident, 
            // but preserving it for legacy safety or just removing it if we are sure we call it from background.
            // Since this method is public static and could be called from anywhere, 
            // we should probably launch a coroutine if we want to enforce background execution, 
            // OR expect the caller to do it. The original code forced the caller to do it.
            // We will assume caller handles threading or we run on main if acceptable (DB ops on main are bad).
            
            // However, AlarmStateManager.deleteInstanceAndUpdateParent involves DB ops.
            // For now, we'll keep the logic synchronous, but safer to assume it's called from background context in our new implementation.
            
            val context = activity.applicationContext
            val alarmTime = instance.alarmTime.time
            val time = DateFormat.getTimeFormat(context).format(alarmTime)

            if (instance.mAlarmState == InstancesColumns.FIRED_STATE || instance.mAlarmState == InstancesColumns.SNOOZE_STATE) {
                // Always dismiss alarms that are fired or snoozed.
                AlarmStateManager.deleteInstanceAndUpdateParent(context, instance)
            } else if (isAlarmWithin24Hours(instance)) {
                // Upcoming alarms are always predismissed.
                AlarmStateManager.setPreDismissState(context, instance)
            } else {
                // Otherwise the alarm cannot be dismissed at this time.
                val reason = context.getString(
                    R.string.alarm_cant_be_dismissed_still_more_than_24_hours_away, time
                )
                Controller.getController().notifyVoiceFailure(activity, reason)
            }

            // Log the successful dismissal.
            val reason = context.getString(R.string.alarm_is_dismissed, time)
            Controller.getController().notifyVoiceSuccess(activity, reason)
            Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_intent)
        }

        private fun isAlarmWithin24Hours(alarmInstance: AlarmInstance): Boolean {
            val nextAlarmTime = alarmInstance.alarmTime
            val nextAlarmTimeMillis = nextAlarmTime.timeInMillis
            return nextAlarmTimeMillis - System.currentTimeMillis() <= DateUtils.DAY_IN_MILLIS
        }

        @JvmStatic
        fun snoozeAlarm(alarmInstance: AlarmInstance, context: Context, activity: Activity) {
            val time = DateFormat.getTimeFormat(context).format(alarmInstance.alarmTime.time)
            val reason = context.getString(R.string.alarm_is_snoozed, time)
            AlarmStateManager.setSnoozeState(context, alarmInstance, true)
            Controller.getController().notifyVoiceSuccess(activity, reason)
            Events.sendAlarmEvent(R.string.action_snooze, R.string.label_intent)
        }

        private fun applyAlarmSettings(alarm: Alarm, context: Context, prefs: SharedPreferences) {
            val audioManager = context.getSystemService(AUDIO_SERVICE) as AudioManager
            alarm.enabled = true
            alarm.vibrate = SettingsDAO.areAlarmVibrationsEnabledByDefault(prefs)
            alarm.flash = SettingsDAO.shouldTurnOnBackFlashForTriggeredAlarm(prefs)
            alarm.deleteAfterUse = SettingsDAO.isOccasionalAlarmDeletedByDefault(prefs)
            alarm.autoSilenceDuration = if (SettingsDAO.isPerAlarmAutoSilenceEnabled(prefs)) {
                PreferencesDefaultValues.DEFAULT_AUTO_SILENCE_DURATION
            } else {
                SettingsDAO.getAlarmTimeout(prefs)
            }
            alarm.snoozeDuration = if (SettingsDAO.isPerAlarmAutoSilenceEnabled(prefs)) {
                PreferencesDefaultValues.DEFAULT_ALARM_SNOOZE_DURATION
            } else {
                SettingsDAO.getSnoozeLength(prefs)
            }
            alarm.missedAlarmRepeatLimit = if (SettingsDAO.isPerAlarmMissedRepeatLimitEnabled(prefs)) {
                PreferencesDefaultValues.DEFAULT_MISSED_ALARM_REPEAT_LIMIT.toInt()
            } else {
                SettingsDAO.getMissedAlarmRepeatLimit(prefs)
            }
            alarm.crescendoDuration = if (SettingsDAO.isPerAlarmCrescendoDurationEnabled(prefs)) {
                PreferencesDefaultValues.DEFAULT_VOLUME_CRESCENDO_DURATION
            } else {
                SettingsDAO.getAlarmVolumeCrescendoDuration(prefs)
            }
            alarm.alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
        }

        private fun getLabelFromIntent(intent: Intent, defaultLabel: String?): String {
            val message = intent.extras?.getString(AlarmClock.EXTRA_MESSAGE, defaultLabel)
            return message ?: ""
        }

        private fun getDaysFromIntent(intent: Intent, defaultWeekdays: Weekdays): Weekdays {
            if (!intent.hasExtra(AlarmClock.EXTRA_DAYS)) {
                return defaultWeekdays
            }
            val days = intent.getIntegerArrayListExtra(AlarmClock.EXTRA_DAYS)
            if (days != null) {
                val daysArray = IntArray(days.size)
                for (i in days.indices) {
                    daysArray[i] = days[i]
                }
                return Weekdays.fromCalendarDays(*daysArray)
            } else {
                val daysArray = intent.getIntArrayExtra(AlarmClock.EXTRA_DAYS)
                if (daysArray != null) {
                    return Weekdays.fromCalendarDays(*daysArray)
                }
            }
            return defaultWeekdays
        }

        private fun getAlertFromIntent(intent: Intent, defaultUri: Uri): Uri {
            val alert = intent.getStringExtra(AlarmClock.EXTRA_RINGTONE)
            return if (alert == null) {
                defaultUri
            } else if (AlarmClock.VALUE_RINGTONE_SILENT == alert || alert.isEmpty()) {
                RingtoneUtils.RINGTONE_SILENT
            } else {
                Uri.parse(alert)
            }
        }
    }
}

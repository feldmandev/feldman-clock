package com.feldman.clock.ui.alarm

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication

import android.app.Application
import android.content.Context
import com.feldman.clock.alarm.AlarmStateManager
import android.database.ContentObserver
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.text.format.DateFormat.getTimeFormat
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feldman.clock.core.alarm.AlarmChallengePrefs
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.ClockContract
import com.feldman.clock.ui.alarm.model.AlarmUi
import com.feldman.clock.core.storage.provider.AlarmInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import androidx.core.net.toUri
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.settings.SettingsDAO

class AlarmViewModel(application: Application) : AndroidViewModel(application) {

    private val _alarms = MutableStateFlow<List<AlarmUi>>(emptyList())
    val alarms: StateFlow<List<AlarmUi>> = _alarms

    private val contentResolver = application.contentResolver

    // Observer for Alarm table changes (add/delete/update alarms)
    private val alarmContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadAlarms()
        }
    }

    // Observer for AlarmInstance table changes (snooze status updates)
    private val instanceContentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadAlarms()
        }
    }

    private val _snackbarChannel = Channel<String>(Channel.CONFLATED)
    val snackbarFlow = _snackbarChannel.receiveAsFlow()

    init {
        // Watch for alarm changes
        contentResolver.registerContentObserver(
            Alarm.CONTENT_URI,
            true,
            alarmContentObserver
        )
        // Watch for alarm instance changes (e.g., snooze)
        contentResolver.registerContentObserver(
            ("content://" + ClockContract.AUTHORITY + "/instances").toUri(),  // InstancesColumns.CONTENT_URI
            true,
            instanceContentObserver
        )
        loadAlarms()
    }

    private fun loadAlarms() {
        viewModelScope.launch(Dispatchers.IO) {
            val alarmsList = Alarm.getAlarms(contentResolver, null)

            val instances = AlarmInstance.getInstances(contentResolver, null)

            // Map alarmId -> List<AlarmInstance>
            val instanceMap = instances.groupBy { it.mAlarmId }

            withContext(Dispatchers.Main) {
                _alarms.value = alarmsList.map { alarm ->
                    val alarmInstances = instanceMap[alarm.id] ?: emptyList()
                    
                    // Logic to find snoozed time
                    val snoozedInstance = alarmInstances.firstOrNull { it.mAlarmState == ClockContract.InstancesColumns.SNOOZE_STATE }
                    val snoozeStr = if (snoozedInstance != null) {
                        getTimeFormat(getApplication()).format(snoozedInstance.alarmTime.time)
                    } else null

                    // Logic to find upcoming time (if not snoozed)
                    // We look for SILENT(0), NOTIFICATION(1), PREDISMISSED(6) is effectively "skipped" but might be shown?
                    // Actually, if it's PREDISMISSED, it shouldn't show as upcoming time, because it IS dismissed.
                    // So we filter for SILENT or NOTIFICATION.
                    val upcomingInstance = alarmInstances.firstOrNull { 
                        it.mAlarmState == ClockContract.InstancesColumns.SILENT_STATE ||
                        it.mAlarmState == ClockContract.InstancesColumns.NOTIFICATION_STATE
                    }
                    
                    alarm.toUi(snoozeStr, upcomingInstance?.alarmTime)
                }
            }
        }
    }

    fun dismissUpcoming(alarm: AlarmUi) {
        viewModelScope.launch(Dispatchers.IO) {
             val instances = AlarmInstance.getInstancesByAlarmId(contentResolver, alarm.id)
             val upcoming = instances.firstOrNull { 
                it.mAlarmState == ClockContract.InstancesColumns.SILENT_STATE ||
                it.mAlarmState == ClockContract.InstancesColumns.NOTIFICATION_STATE
             }
             
             if (upcoming != null) {
                 val providerAlarm = alarm.toProvider(getApplication())
                 if (providerAlarm.daysOfWeek.isRepeating()) {
                     // Mark current instance as PREDISMISSED (dismissed before firing)
                     upcoming.mAlarmState = ClockContract.InstancesColumns.PREDISMISSED_STATE
                     AlarmInstance.updateInstance(contentResolver, upcoming)
                     
                     // Create a NEW instance for the next occurrence
                     val reference = upcoming.alarmTime.clone() as Calendar
                     reference.add(Calendar.MINUTE, 1)
                     val nextInstance = providerAlarm.createInstanceAfter(reference)
                     
                     AlarmInstance.addInstance(contentResolver, nextInstance)
                     AlarmStateManager.registerInstance(getApplication(), nextInstance, true)
                 } else {
                     // One-time: just disable
                     toggleAlarm(alarm, false)
                 }
                 loadAlarms()
             }
        }
    }
    
    fun addAlarm(hour: Int, minute: Int) {
        val newAlarm = Alarm()
        newAlarm.hour = hour
        newAlarm.minutes = minute
        newAlarm.enabled = true

        // Seed the per-alarm settings from the global defaults, the same way alarms created
        // through the AlarmClock API are seeded (see HandleApiCalls.applyAlarmSettings).
        applyDefaultAlarmSettings(newAlarm)

        // Ensure ringtone is not silent by default
        if (Uri.EMPTY == newAlarm.alert) {
            newAlarm.alert = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        }

        // Leave year, month, day as 0 (from default constructor)
        // The system will calculate the next occurrence in getNextAlarmTime()
        
        viewModelScope.launch(Dispatchers.IO) {
            Alarm.addAlarm(contentResolver, newAlarm)
            // Create and schedule the alarm instance
            setupAlarmInstance(newAlarm)
            showTimeUntilAlarm(newAlarm)
        }
    }

    fun updateAlarm(updatedAlarm: AlarmUi) {
        optimisticUpdate(updatedAlarm)
        viewModelScope.launch {
            val providerAlarm = updatedAlarm.toProvider(getApplication())
            withContext(Dispatchers.IO) {
                // 1. Update the Alarm definition in DB
                Alarm.updateAlarm(contentResolver, providerAlarm)
                
                // 2. Clear ALL existing instances (snoozes, dismisses, predismissed)
                // This resets the alarm state completely.
                AlarmStateManager.deleteAllInstances(getApplication(), providerAlarm.id)
                
                if (providerAlarm.enabled) {
                    // 3. Create fresh instance
                    setupAlarmInstance(providerAlarm)
                    showTimeUntilAlarm(providerAlarm)
                }
            }
        }
    }


    fun toggleAlarm(alarm: AlarmUi, isEnabled: Boolean) {
        val updatedUi = alarm.copy(enabled = isEnabled)
        optimisticUpdate(updatedUi)

        viewModelScope.launch(Dispatchers.IO) {
            val providerAlarm = updatedUi.toProvider(getApplication())
            withContext(Dispatchers.IO) {
                Alarm.updateAlarm(contentResolver, providerAlarm)
                
                // Cleanup old instances
                AlarmStateManager.deleteAllInstances(getApplication(), providerAlarm.id)
                
                if (isEnabled) {
                    setupAlarmInstance(providerAlarm)
                    showTimeUntilAlarm(providerAlarm)
                }
            }
        }
    }



    private fun optimisticUpdate(updatedAlarm: AlarmUi) {
        val currentList = _alarms.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == updatedAlarm.id }
        if (index != -1) {
            currentList[index] = updatedAlarm
            _alarms.value = currentList.toList()
        }
    }


    private suspend fun showTimeUntilAlarm(alarm: Alarm) {
        val now = Calendar.getInstance()
        val alarmTime = alarm.getNextAlarmTime(now)
        val delta = alarmTime.timeInMillis - now.timeInMillis
        val message = AlarmUtils.formatElapsedTimeUntilAlarm(getApplication(), delta)
        _snackbarChannel.send(message)
    }

    fun deleteAlarm(alarm: AlarmUi) {
        Log.d("AlarmScreen", "deleting alarm")
        viewModelScope.launch(Dispatchers.IO) {
            AlarmStateManager.deleteAllInstances(getApplication(), alarm.id)
            Alarm.deleteAlarm(contentResolver, alarm.id)
            // The challenge lives outside the alarms table, so it has to be cleaned up here or
            // it would be inherited by whichever alarm the provider hands the id to next.
            AlarmChallengePrefs.clear(getApplication(), alarm.id)
            _alarms.value = _alarms.value.filterNot { it.id == alarm.id }
            loadAlarms()
        }
    }

    /**
     * Re-creates an alarm removed by [deleteAlarm], for the snackbar's undo action.
     *
     * The provider assigns a fresh row id on insert, so the restored alarm is equivalent but not
     * identical to the original. Everything the user configured is carried over by [toProvider].
     */
    fun restoreAlarm(alarm: AlarmUi) {
        viewModelScope.launch {
            val providerAlarm = alarm.toProvider(getApplication())
            withContext(Dispatchers.IO) {
                val restored = Alarm.addAlarm(contentResolver, providerAlarm)
                if (restored.enabled) {
                    setupAlarmInstance(restored)
                }
                loadAlarms()
            }
        }
    }


    private fun setupAlarmInstance(alarm: Alarm): AlarmInstance {
        val newInstance = alarm.createInstanceAfter(Calendar.getInstance())
        AlarmInstance.addInstance(contentResolver, newInstance)

        AlarmStateManager.registerInstance(getApplication(), newInstance, true)
        return newInstance
    }


    fun dismissSnooze(alarm: AlarmUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val instances = AlarmInstance.getInstancesByAlarmId(contentResolver, alarm.id)
            val snoozedInstance = instances.firstOrNull { it.mAlarmState == 2 }  // InstancesColumns.SNOOZE_STATE
            if (snoozedInstance != null) {
                AlarmStateManager.deleteInstanceAndUpdateParent(getApplication(), snoozedInstance)
            }
            loadAlarms()
        }
    }

    fun undismissAlarm(alarm: AlarmUi) {
        viewModelScope.launch(Dispatchers.IO) {
            val instances = AlarmInstance.getInstancesByAlarmId(contentResolver, alarm.id)
            // Find and delete the PREDISMISSED instance
            val predismissedInstance = instances.firstOrNull { 
                it.mAlarmState == ClockContract.InstancesColumns.PREDISMISSED_STATE
            }
            
            if (predismissedInstance != null) {
                Log.d("AlarmViewModel", "Undismissing alarm ${alarm.id}, deleting PREDISMISSED instance ${predismissedInstance.mId}")
                AlarmInstance.deleteInstance(contentResolver, predismissedInstance.mId)
                
                // The active instance should now fire at the originally dismissed time
                // We need to re-create the instance for that time
                val providerAlarm = alarm.toProvider(getApplication())
                val newInstance = providerAlarm.createInstanceAfter(Calendar.getInstance())
                
                AlarmInstance.addInstance(contentResolver, newInstance)
                AlarmStateManager.registerInstance(getApplication(), newInstance, true)
                
                loadAlarms()
            }
        }
    }

    /**
     * Fills [alarm] with the per-alarm settings a freshly created alarm should start from.
     *
     * Every value is seeded from the matching global setting, so whatever is configured in Alarm
     * settings is what a new alarm starts with. The per-alarm rows in the editor then override it
     * for that one alarm only.
     */
    private fun applyDefaultAlarmSettings(alarm: Alarm) {
        val context = getApplication<Application>()
        val prefs = ClockApplication.getDefaultSharedPreferences(context)
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        alarm.vibrate = SettingsDAO.areAlarmVibrationsEnabledByDefault(prefs)
        alarm.flash = SettingsDAO.shouldTurnOnBackFlashForTriggeredAlarm(prefs)
        alarm.deleteAfterUse = SettingsDAO.isOccasionalAlarmDeletedByDefault(prefs)
        alarm.autoSilenceDuration = SettingsDAO.getAlarmTimeout(prefs)
        alarm.snoozeDuration = SettingsDAO.getSnoozeLength(prefs)
        alarm.missedAlarmRepeatLimit = SettingsDAO.getMissedAlarmRepeatLimit(prefs)
        alarm.crescendoDuration = SettingsDAO.getAlarmVolumeCrescendoDuration(prefs)
        alarm.alarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
    }

    fun getDefaultRingtoneUri(): Uri {
        return com.feldman.clock.core.data.DataModel.dataModel.alarmRingtoneUriFromSettings
    }

    override fun onCleared() {
        super.onCleared()
        contentResolver.unregisterContentObserver(alarmContentObserver)
        contentResolver.unregisterContentObserver(instanceContentObserver)
    }
    fun is24HourFormat(): Boolean {
        return com.feldman.clock.core.data.DataModel.dataModel.is24HourFormat()
    }
}
internal fun Alarm.toUi(snoozingUntil: String? = null, upcomingInstanceTime: Calendar? = null): AlarmUi {
    // DEBUG: Log database values
    Log.d("AlarmViewModel", "toUi: alarm_id=$id DB year=$year month=$month day=$day hour=$hour min=$minutes enabled=$enabled")
    
    // Determine if the stored date matches the "natural" next occurrence (Today or Tomorrow)
    // If it does, we treat it as a "Normal" alarm (year=0 in UI) so the UI doesn't show "Specific Date".
    // If the stored date is different, it means the user explicitly selected a specific date.
    val now = Calendar.getInstance()
    var isNatural = false
    if (!daysOfWeek.isRepeating()) {
         val naturalNext = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minutes)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= now.timeInMillis) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
        
        // Check if the stored date matches the natural next occurrence
        if (year == naturalNext.get(Calendar.YEAR) &&
            month == naturalNext.get(Calendar.MONTH) &&
            day == naturalNext.get(Calendar.DAY_OF_MONTH)) {
            isNatural = true
        }
    }

    val uiYear = if (isNatural) 0 else year
    val uiMonth = if (isNatural) 0 else month
    val uiDay = if (isNatural) 0 else day
    
    Log.d("AlarmViewModel", "toUi: alarm_id=$id UI year=$uiYear month=$uiMonth day=$uiDay isNatural=$isNatural")

    return AlarmUi(
        id = id,
        hour = hour,
        minutes = minutes,
        label = label,
        enabled = enabled,
        daysOfWeek = daysOfWeek,
        vibrate = vibrate,
        year = uiYear,
        month = uiMonth,
        day = uiDay,
        ringtoneUri = alert?.toString(),
        snoozingUntil = snoozingUntil,
        upcomingInstanceTime = upcomingInstanceTime,
        pauseStartTime = pauseStartTime,
        pauseEndTime = pauseEndTime,
        flash = flash,
        deleteAfterUse = deleteAfterUse,
        autoSilenceDuration = autoSilenceDuration,
        snoozeDuration = snoozeDuration,
        missedAlarmRepeatLimit = missedAlarmRepeatLimit,
        crescendoDuration = crescendoDuration,
        alarmVolume = alarmVolume
    )
}

suspend fun AlarmUi.toProvider(
    context: Context
): Alarm = withContext(Dispatchers.Main) {
    // DEBUG: Log what we're converting
    Log.d("AlarmViewModel", "toProvider INPUT: alarm_id=$id year=$year month=$month day=$day hour=$hour min=$minutes")
    
    Alarm().apply {
        id = this@toProvider.id
        hour = this@toProvider.hour
        minutes = this@toProvider.minutes
        label = this@toProvider.label
        enabled = this@toProvider.enabled
        daysOfWeek = this@toProvider.daysOfWeek
        vibrate = this@toProvider.vibrate
        alert = this@toProvider.ringtoneUri?.let { Uri.parse(it) } ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        pauseStartTime = this@toProvider.pauseStartTime
        pauseEndTime = this@toProvider.pauseEndTime
        flash = this@toProvider.flash
        deleteAfterUse = this@toProvider.deleteAfterUse
        autoSilenceDuration = this@toProvider.autoSilenceDuration
        snoozeDuration = this@toProvider.snoozeDuration
        missedAlarmRepeatLimit = this@toProvider.missedAlarmRepeatLimit
        crescendoDuration = this@toProvider.crescendoDuration
        alarmVolume = this@toProvider.alarmVolume

        // Handle date setting:
        // - If year=0 in UI: keep as 0 (natural alarm without specific date)
        // - If year!=0 in UI: use the specific date provided
        if (this@toProvider.year != 0) {
            // User specified a specific date
            year = this@toProvider.year
            month = this@toProvider.month
            day = this@toProvider.day
            Log.d("AlarmViewModel", "toProvider: Using specific date year=$year month=$month day=$day")
        } else {
            // Keep as year=0 (natural alarm)
            // The system will calculate next occurrence in getNextAlarmTime()
            year = 0
            month = 0
            day = 0
            Log.d("AlarmViewModel", "toProvider: Natural alarm - keeping year=0")
        }
        
        Log.d("AlarmViewModel", "toProvider OUTPUT: alarm will save year=$year month=$month day=$day")
    }
}


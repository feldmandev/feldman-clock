package com.feldman.clock.ui.alarm

import android.content.Context
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.alarmui.ClockAlarmController
import com.feldman.clock.alarmui.ClockAlarmItem
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class DirectClockAlarmController(context: Context) : ClockAlarmController {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver

    override suspend fun load(): List<ClockAlarmItem> = withContext(Dispatchers.IO) {
        Alarm.getAlarms(resolver, null).map { alarm ->
            ClockAlarmItem(
                id = alarm.id,
                hour = alarm.hour,
                minute = alarm.minutes,
                enabled = alarm.enabled,
                label = alarm.label.orEmpty(),
                repeatDays = alarm.daysOfWeek.mBits
            )
        }
    }

    override suspend fun create(hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        val alarm = Alarm().apply {
            this.hour = hour
            minutes = minute
            enabled = true
        }
        Alarm.addAlarm(resolver, alarm)
        schedule(alarm)
    }

    override suspend fun updateTime(id: Long, hour: Int, minute: Int) = withContext(Dispatchers.IO) {
        val alarm = Alarm.getAlarm(resolver, id) ?: return@withContext
        alarm.hour = hour
        alarm.minutes = minute
        alarm.enabled = true
        saveAndReschedule(alarm)
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) = withContext(Dispatchers.IO) {
        val alarm = Alarm.getAlarm(resolver, id) ?: return@withContext
        alarm.enabled = enabled
        saveAndReschedule(alarm)
    }

    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        AlarmStateManager.deleteAllInstances(appContext, id)
        Alarm.deleteAlarm(resolver, id)
        Unit
    }

    private fun saveAndReschedule(alarm: Alarm) {
        Alarm.updateAlarm(resolver, alarm)
        AlarmStateManager.deleteAllInstances(appContext, alarm.id)
        if (alarm.enabled) schedule(alarm)
    }

    private fun schedule(alarm: Alarm) {
        val instance = alarm.createInstanceAfter(Calendar.getInstance())
        AlarmInstance.addInstance(resolver, instance)
        AlarmStateManager.registerInstance(appContext, instance, true)
    }
}

package com.feldman.clock.app.integration

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import com.feldman.clock.ui.alarm.DirectClockAlarmController
import com.feldman.clock.alarmui.ClockAlarmBridgeContract
import kotlinx.coroutines.runBlocking

class AlarmBridgeProvider : ContentProvider() {
    private val controller by lazy { DirectClockAlarmController(requireNotNull(context)) }

    override fun onCreate() = true

    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = runBlocking {
        when (method) {
            ClockAlarmBridgeContract.METHOD_LIST -> Bundle().apply {
                putParcelableArrayList(
                    ClockAlarmBridgeContract.KEY_ALARMS,
                    ArrayList(controller.load().map { alarm ->
                        Bundle().apply {
                            putLong(ClockAlarmBridgeContract.KEY_ID, alarm.id)
                            putInt(ClockAlarmBridgeContract.KEY_HOUR, alarm.hour)
                            putInt(ClockAlarmBridgeContract.KEY_MINUTE, alarm.minute)
                            putBoolean(ClockAlarmBridgeContract.KEY_ENABLED, alarm.enabled)
                            putString(ClockAlarmBridgeContract.KEY_LABEL, alarm.label)
                            putInt(ClockAlarmBridgeContract.KEY_REPEAT_DAYS, alarm.repeatDays)
                        }
                    })
                )
            }
            ClockAlarmBridgeContract.METHOD_CREATE -> {
                controller.create(extras.hour(), extras.minute())
                Bundle.EMPTY
            }
            ClockAlarmBridgeContract.METHOD_UPDATE_TIME -> {
                controller.updateTime(extras.id(), extras.hour(), extras.minute())
                Bundle.EMPTY
            }
            ClockAlarmBridgeContract.METHOD_SET_ENABLED -> {
                controller.setEnabled(extras.id(), extras?.getBoolean(ClockAlarmBridgeContract.KEY_ENABLED) == true)
                Bundle.EMPTY
            }
            ClockAlarmBridgeContract.METHOD_DELETE -> {
                controller.delete(extras.id())
                Bundle.EMPTY
            }
            else -> super.call(method, arg, extras) ?: Bundle.EMPTY
        }
    }

    private fun Bundle?.id() = this?.getLong(ClockAlarmBridgeContract.KEY_ID, -1L) ?: -1L
    private fun Bundle?.hour() = this?.getInt(ClockAlarmBridgeContract.KEY_HOUR, 0) ?: 0
    private fun Bundle?.minute() = this?.getInt(ClockAlarmBridgeContract.KEY_MINUTE, 0) ?: 0

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0

}

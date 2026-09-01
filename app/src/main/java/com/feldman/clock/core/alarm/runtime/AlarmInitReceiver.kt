/*
 * Copyright (C) 2007 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.alarm

import com.feldman.clock.R

import com.feldman.clock.app.ClockBackupAgent
import com.feldman.clock.core.concurrent.AsyncHandler

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.feldman.clock.app.ClockApplication.Companion.getDefaultSharedPreferences
import com.feldman.clock.alarm.AlarmNotifications
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.core.controller.Controller
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.storage.provider.AlarmInstance
import android.app.AlarmManager
import com.feldman.clock.core.util.NotificationUtils
import java.util.Calendar

class AlarmInitReceiver : BroadcastReceiver() {

    /**
     * This receiver handles a variety of actions:
     *
     * - Clean up backup data that was recently restored to this device on ACTION_COMPLETE_RESTORE.
     * - Reset timers and stopwatch on ACTION_BOOT_COMPLETED
     * - Fix alarm states on ACTION_BOOT_COMPLETED, TIME_SET, TIMEZONE_CHANGED, and LOCALE_CHANGED
     * - Rebuild notifications on MY_PACKAGE_REPLACED
     */
    @SuppressLint("WakelockTimeout", "Wakelock")
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        com.feldman.clock.core.util.FileLog.i("AlarmInitReceiver", "onReceive: $action")

        val result = goAsync()
        val wl = AlarmAlertWakeLock.createPartialWakeLock(context)
        wl.acquire()

        // We need to increment the global id out of the async task to prevent race conditions
        SettingsDAO.updateGlobalIntentId(getDefaultSharedPreferences(context))

        // Updates stopwatch and timer data after a device reboot so they are as accurate as
        // possible.
        if (ACTION_BOOT_COMPLETED == action) {
            DataModel.dataModel.updateAfterReboot()
            // Stopwatch and timer data need to be updated on time change so the reboot
            // functionality works as expected.
        } else if (Intent.ACTION_TIME_CHANGED == action) {
            DataModel.dataModel.updateAfterTimeSet()
        }

        // Update shortcuts so they exist for the user.
        if (Intent.ACTION_BOOT_COMPLETED == action || Intent.ACTION_LOCALE_CHANGED == action) {
            Controller.getController().updateShortcuts()
            NotificationUtils.updateNotificationChannels(context)

        }

        // Notifications are canceled by the system on application upgrade. This broadcast signals
        // that the new app is free to rebuild the notifications using the existing data.
        // Additionally on new app installs, make sure to enable shortcuts immediately as opposed
        // to waiting for system reboot.
        if (Intent.ACTION_MY_PACKAGE_REPLACED == action) {
            DataModel.dataModel.updateAllNotifications()
            Controller.getController().updateShortcuts()
        }

        // Update alarm status once receive the status update broadcast
        if (AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED == action) {
            AlarmStateManager.fixAlarmInstances(context)
            return
        }

        if (ACTION_UPDATE_ALARM_STATUS == action) {
            val alarmTime = intent.getLongExtra(TIME, 0L)
            val alarmStatus = intent.getIntExtra(STATUS, 0)

            if (alarmTime != 0L) {
                val cr = context.contentResolver
                val alarmInstances = AlarmInstance.getInstances(cr, null)
                var alarmInstance: AlarmInstance? = null
                for (instance in alarmInstances) {
                    if (instance.alarmTime.timeInMillis == alarmTime) {
                        alarmInstance = instance
                        break
                    }
                }

                alarmInstance?.let { instance ->
                    // Update alarm status if the alarm instance is not null
                    when (alarmStatus) {
                        DISMISS_STATUS -> AlarmStateManager.setDismissState(context, instance)
                        SNOOZE_STATUS -> {
                            val snoozeTime = intent.getLongExtra(SNOOZE_TIME, 0L)
                            if (snoozeTime > System.currentTimeMillis()) {
                                AlarmNotifications.clearNotification(context, instance)
                                val c = Calendar.getInstance()
                                c.timeInMillis = snoozeTime
                                instance.alarmTime = c
                                instance.mAlarmState = 2 // InstancesColumns.SNOOZE_STATE
                                AlarmInstance.updateInstance(cr, instance)
                            }
                        }
                    }
                }
            }
        }

        AsyncHandler.post {
            try {
                // Process restored data if any exists
                if (!ClockBackupAgent.Companion.processRestoredData(context)) {
                    // Update all the alarm instances on time change event
                    AlarmStateManager.fixAlarmInstances(context)
                }
            } finally {
                result.finish()
                wl.release()
                Log.v("AlarmInitReceiver" ,"AlarmInitReceiver finished")
            }
        }
    }

    companion object {
        private const val ACTION_UPDATE_ALARM_STATUS =
            "org.codeaurora.poweroffalarm.action.UPDATE_ALARM"

        private const val SNOOZE_STATUS = 2
        private const val DISMISS_STATUS = 3

        private const val STATUS = "status"
        private const val TIME = "time"
        private const val SNOOZE_TIME = "snooze_time"

        /**
         * When running on N devices, we're interested in the boot completed event that is sent while
         * the user is still locked, so that we can schedule alarms.
         */
        private const val ACTION_BOOT_COMPLETED = Intent.ACTION_LOCKED_BOOT_COMPLETED
    }
}

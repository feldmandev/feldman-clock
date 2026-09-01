/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.app

import com.feldman.clock.R

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.backup.BackupAgent
import android.app.backup.BackupDataInput
import android.app.backup.BackupDataOutput
import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.feldman.clock.app.ClockApplication.Companion.getDefaultSharedPreferences
import com.feldman.clock.alarm.AlarmInitReceiver
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import java.io.File
import java.io.IOException
import java.util.Calendar

class ClockBackupAgent : BackupAgent() {

    override fun onBackup(
        oldState: ParcelFileDescriptor,
        data: BackupDataOutput,
        newState: ParcelFileDescriptor
    ) {
    }

    override fun onRestore(
        data: BackupDataInput,
        appVersionCode: Int,
        newState: ParcelFileDescriptor
    ) {
    }

    @Throws(IOException::class)
    override fun onRestoreFile(
        data: ParcelFileDescriptor,
        size: Long,
        destination: File,
        type: Int,
        mode: Long,
        mtime: Long
    ) {
        // The preference file on the backup device may not be the same on the restore device.
        // Massage the file name here before writing it.
        val finalDestination = if (destination.name.endsWith("_preferences.xml")) {
            val prefFileName = packageName + "_preferences.xml"
            File(destination.parentFile, prefFileName)
        } else {
            destination
        }

        super.onRestoreFile(data, size, finalDestination, type, mode, mtime)
    }

    /**
     * When this method is called during backup/restore, the application is executing in a
     * "minimalist" state. Because of this, the application's ContentResolver cannot be used.
     * Consequently, the work of scheduling alarms on the restore device cannot be done here.
     * Instead, a future callback to DeskClock is used as a signal to reschedule the alarms. The
     * future callback may take the form of ACTION_BOOT_COMPLETED if the device is not yet fully
     * booted (i.e. the restore occurred as part of the setup wizard). If the device is booted, an
     * ACTION_COMPLETE_RESTORE broadcast is scheduled 10 seconds in the future to give
     * backup/restore enough time to kill the Clock process. Both of these future callbacks result
     * in the execution of [processRestoredData].
     */
    override fun onRestoreFinished() {
        // TODO: migrate restored database and preferences over into
        // the device-encrypted storage area

        // Indicate a data restore has been completed.
        SettingsDAO.setRestoreBackupFinished(getDefaultSharedPreferences(this), true)

        // Create an Intent to send into DeskClock indicating restore is complete.
        val restoreIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_COMPLETE_RESTORE).setClass(this, AlarmInitReceiver::class.java),
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Deliver the Intent 10 seconds from now.
        val triggerAtMillis = SystemClock.elapsedRealtime() + 10000

        // Schedule the Intent delivery in AlarmManager.
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMillis, restoreIntent)

        Log.i("Waiting for %s to complete the data restore", ACTION_COMPLETE_RESTORE)
    }

    companion object {
        const val ACTION_COMPLETE_RESTORE = "com.feldman.clock.action.COMPLETE_RESTORE"

        /**
         * @param context a context to access resources and services
         * @return `true` if restore data was processed; `false` otherwise.
         */
        fun processRestoredData(context: Context): Boolean {
            val prefs = getDefaultSharedPreferences(context)
            // If data was not recently restored, there is nothing to do.
            if (!SettingsDAO.isRestoreBackupFinished(prefs)) {
                return false
            }

            Log.i("ClockBackupAgent", "processRestoredData() started")

            // Now that alarms have been restored, schedule new instances in AlarmManager.
            val contentResolver = context.contentResolver
            val alarms = Alarm.getAlarms(contentResolver, null)

            val now = Calendar.getInstance()
            for (alarm in alarms) {
                // Remove any instances that may currently exist for the alarm;
                // these aren't relevant on the restore device and we'll recreate them below.
                AlarmStateManager.deleteAllInstances(context, alarm.id)

                if (alarm.enabled) {
                    // Create the next alarm instance to schedule.
                    val alarmInstance = alarm.createInstanceAfter(now)

                    // Add the next alarm instance to the database.
                    AlarmInstance.addInstance(contentResolver, alarmInstance)

                    // Schedule the next alarm instance in AlarmManager.
                    AlarmStateManager.registerInstance(context, alarmInstance, true)
                    Log.i("ClockBackupAgent scheduled alarm instance: %s", alarmInstance.toString())
                }
            }

            // Remove the preference to avoid executing this logic multiple times.
            SettingsDAO.setRestoreBackupFinished(prefs, false)

            Log.i("ClockBackupAgent", "processRestoredData() completed")
            return true
        }
    }
}

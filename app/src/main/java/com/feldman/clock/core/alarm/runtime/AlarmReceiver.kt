package com.feldman.clock.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.feldman.clock.core.util.FileLog
import com.feldman.clock.alarm.AlarmAlertWakeLock

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        try {
            FileLog.i("AlarmReceiver", "Received intent: " + intent.action)

            // Acquire WakeLock immediately
            FileLog.v("AlarmReceiver", "Acquiring WakeLock...")
            AlarmAlertWakeLock.acquireScreenCpuWakeLock(context)
            FileLog.v("AlarmReceiver", "WakeLock acquired")

            // Propagate the intent to the service
            val serviceIntent = Intent(context, AlarmService::class.java)
            serviceIntent.action = intent.action
            serviceIntent.data = intent.data
            serviceIntent.putExtras(intent)

            // Start as Foreground Service to bypass background restrictions
            try {
                FileLog.v("AlarmReceiver", "Starting AlarmService...")
                ContextCompat.startForegroundService(context, serviceIntent)
                FileLog.i("AlarmReceiver", "Started AlarmService as Foreground Service")
            } catch (e: Exception) {
                FileLog.e("AlarmReceiver", "Failed to start AlarmService", e)
                // Release lock if start fails
                AlarmAlertWakeLock.releaseCpuLock()
            }
        } catch (t: Throwable) {
            FileLog.e("AlarmReceiver", "Fatal error in onReceive", t)
        }
    }
}

/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.alarm

import com.feldman.clock.R

import android.annotation.SuppressLint
import android.content.Context
import android.os.PowerManager

/**
 * Utility class to hold wake lock in app.
 */
object AlarmAlertWakeLock {

    private const val TAG = "com.best.deskclock:AlarmAlertWakeLock"

    private var cpuWakeLock: PowerManager.WakeLock? = null

    @JvmStatic
    fun createPartialWakeLock(context: Context): PowerManager.WakeLock {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG)
    }

    @JvmStatic
    @SuppressLint("WakelockTimeout")
    fun acquireCpuWakeLock(context: Context) {
        if (cpuWakeLock != null) {
            return
        }

        cpuWakeLock = createPartialWakeLock(context)
        cpuWakeLock?.acquire()
    }

    @JvmStatic
    @SuppressLint("WakelockTimeout")
    fun acquireScreenCpuWakeLock(context: Context) {
        if (cpuWakeLock != null) {
            return
        }

        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        com.feldman.clock.core.util.FileLog.v(TAG, "Creating new partial wake lock")
        cpuWakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, TAG
        )
        cpuWakeLock?.acquire()
        com.feldman.clock.core.util.FileLog.v(TAG, "Wake lock acquired")
    }

    @JvmStatic
    fun releaseCpuLock() {
        cpuWakeLock?.let {
            if (it.isHeld) {
                it.release()
                cpuWakeLock = null
                com.feldman.clock.core.util.FileLog.v(TAG, "Wake lock released")
            }
        }
    }
}

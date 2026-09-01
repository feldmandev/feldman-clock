/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.timer

import com.feldman.clock.R

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.media.ringtone.AsyncRingtonePlayer
import com.feldman.clock.core.media.ringtone.RingtonePlayer
import com.feldman.clock.core.util.RingtoneUtils

/**
 * Manages playing the timer ringtone and vibrating the device.
 */
object TimerRinger {

    private val VIBRATE_PATTERN = longArrayOf(500, 500)

    private var started = false

    private var asyncRingtonePlayer: AsyncRingtonePlayer? = null

    private var ringtonePlayer: RingtonePlayer? = null

    @JvmStatic
    fun stop(context: Context, prefs: SharedPreferences) {
        if (started) {
            started = false
            if (SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
                getRingtonePlayer(context).stop()
            } else {
                getAsyncRingtonePlayer(context).stop()
            }
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator.cancel()
        }
    }

    @JvmStatic
    fun start(context: Context, prefs: SharedPreferences) {
        // Make sure we are stopped before starting
        stop(context, prefs)

        var uri = DataModel.dataModel.timerRingtoneUri

        // Look up user-selected timer ringtone.
        if (RingtoneUtils.RINGTONE_SILENT == uri) {
            // Special case: Silent ringtone
        } else {
            if (RingtoneUtils.isRandomRingtone(uri)) {
                uri = RingtoneUtils.getRandomRingtoneUri()
            } else if (RingtoneUtils.isRandomCustomRingtone(uri)) {
                uri = RingtoneUtils.getRandomCustomRingtoneUri()
            }

            // Crescendo duration always in milliseconds
            val crescendoDuration = SettingsDAO.getTimerVolumeCrescendoDuration(prefs) * 1000L

            if (SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
                getRingtonePlayer(context).play(uri, crescendoDuration)
            } else {
                getAsyncRingtonePlayer(context).play(uri, crescendoDuration)
            }
        }

        if (SettingsDAO.isTimerVibrate(prefs)) {
            val vibrator = context.getSystemService(Vibrator::class.java)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val vibrationAttributes = VibrationAttributes.Builder()
                .setUsage(VibrationAttributes.USAGE_ALARM)
                .build()
            val vibrationEffect = VibrationEffect.createWaveform(VIBRATE_PATTERN, 0)
            vibrator.vibrate(vibrationEffect, vibrationAttributes)
        }

        started = true
    }

    @JvmStatic
    fun deactivateRingtonePlayback(prefs: SharedPreferences) {
        if (SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
            stopListeningToPreferences()
        } else {
            releaseResources()
        }
    }

    // MediaPlayer
    @Synchronized
    private fun getAsyncRingtonePlayer(context: Context): AsyncRingtonePlayer {
        if (asyncRingtonePlayer == null) {
            asyncRingtonePlayer = AsyncRingtonePlayer(context.applicationContext)
        }
        return asyncRingtonePlayer!!
    }

    @JvmStatic
    @Synchronized
    fun releaseResources() {
        if (asyncRingtonePlayer != null) {
            asyncRingtonePlayer!!.shutdown()
            asyncRingtonePlayer = null
        }
    }

    // ExoPlayer
    @Synchronized
    private fun getRingtonePlayer(context: Context): RingtonePlayer {
        if (ringtonePlayer == null) {
            ringtonePlayer = RingtonePlayer(context.applicationContext)
        }
        return ringtonePlayer!!
    }

    @JvmStatic
    @Synchronized
    fun stopListeningToPreferences() {
        if (ringtonePlayer != null) {
            ringtonePlayer!!.stopListeningToPreferences()
            ringtonePlayer = null
        }
    }
}

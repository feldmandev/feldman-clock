/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.alarm

import com.feldman.clock.R

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.feldman.clock.core.media.ringtone.AsyncRingtonePlayer
import com.feldman.clock.core.media.ringtone.RingtonePlayer
import com.feldman.clock.core.util.DeviceUtils
import com.feldman.clock.core.util.RingtoneUtils
import com.feldman.clock.core.util.Utils

/**
 * Manages playing alarm ringtones and vibrating the device.
 */
object AlarmRinger {

    private const val TAG = "AlarmRinger"
    private var started = false
    private var asyncRingtonePlayer: AsyncRingtonePlayer? = null
    private var ringtonePlayer: RingtonePlayer? = null
    private var handler: Handler? = null
    private var vibrationRunnable: Runnable? = null
    private var previousAlarmVolume = -1

    @JvmStatic
    fun stop(context: Context, prefs: SharedPreferences) {
        if (handler != null && vibrationRunnable != null) {
            handler!!.removeCallbacks(vibrationRunnable!!)
            vibrationRunnable = null
            handler = null
        }

        if (started) {
            started = false
            if (DeviceUtils.isUserUnlocked(context) && SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
                Log.v(TAG, "AlarmRinger.stop() ExoPlayer")
                getRingtonePlayer(context).stop()
            } else {
                Log.v(TAG, "AlarmRinger.stop() MediaPlayer")
                getAsyncRingtonePlayer(context).stop()

                if (SettingsDAO.isPerAlarmVolumeEnabled(prefs) && previousAlarmVolume != -1) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    // Restore the original alarm volume only if it was changed
                    if (currentVolume != previousAlarmVolume) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
                    }

                    previousAlarmVolume = -1
                }
            }

            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator.cancel()
        }
    }

    @JvmStatic
    fun start(context: Context, prefs: SharedPreferences, instance: AlarmInstance) {
        // Make sure we are stopped before starting
        stop(context, prefs)

        if (RingtoneUtils.RINGTONE_SILENT != instance.mRingtone) {
            // Crescendo duration always in milliseconds
            val crescendoDuration = instance.mCrescendoDuration * 1000L
            if (DeviceUtils.isUserUnlocked(context) && SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
                Log.v(TAG, "AlarmRinger.start() with ExoPlayer")
                getRingtonePlayer(context).play(instance.mRingtone, crescendoDuration)
            } else {
                Log.v(TAG, "AlarmRinger.start() with MediaPlayer")
                if (SettingsDAO.isPerAlarmVolumeEnabled(prefs)) {
                    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

                    if (previousAlarmVolume == -1) {
                        previousAlarmVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    }

                    val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_ALARM)
                    if (currentVolume != instance.mAlarmVolume) {
                        audioManager.setStreamVolume(AudioManager.STREAM_ALARM, instance.mAlarmVolume, 0)
                    }
                }

                getAsyncRingtonePlayer(context).play(instance.mRingtone, crescendoDuration)
            }
        }

        if (instance.mVibrate) {
            var delayInMillis = SettingsDAO.getVibrationStartDelay(prefs) * 1000L
            // Add a small safety margin in case the vibration pattern starts with 0 ms,
            // to prevent any vibration if the alarm stops right at the delay limit.
            val SAFETY_MARGIN_MS = 300L
            delayInMillis += SAFETY_MARGIN_MS

            handler = Handler(Looper.getMainLooper())

            vibrationRunnable = Runnable {
                val vibrator = context.getSystemService(Vibrator::class.java)
                val audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()

                val patternKey = SettingsDAO.getVibrationPattern(prefs)
                val pattern = Utils.getVibrationPatternForKey(patternKey)

                val vibrationAttributes = VibrationAttributes.Builder()
                    .setUsage(VibrationAttributes.USAGE_ALARM)
                    .build()
                val vibrationEffect = VibrationEffect.createWaveform(pattern, 0)
                vibrator.vibrate(vibrationEffect, vibrationAttributes)
            }

            if (delayInMillis > 0) {
                Log.v(TAG, "AlarmRinger: vibration scheduled in $delayInMillis ms")
                handler?.postDelayed(vibrationRunnable!!, delayInMillis)
            } else {
                Log.v(TAG, "AlarmRinger: vibration started immediately")
                vibrationRunnable?.run()
            }
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
            asyncRingtonePlayer?.shutdown()
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
            ringtonePlayer?.stopListeningToPreferences()
            ringtonePlayer = null
        }
    }
}

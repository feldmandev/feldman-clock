/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.media.ringtone;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import com.feldman.clock.settings.SettingsDAO;
public final class RingtonePreviewKlaxon {

    private static AsyncRingtonePlayer sAsyncRingtonePlayer;

    private static RingtonePlayer sRingtonePlayer;

    private RingtonePreviewKlaxon() {
    }

    public static void stop(Context context, SharedPreferences prefs) {
        if (SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
            getRingtonePlayer(context).stop();
        } else {
            getAsyncRingtonePlayer(context).stop();
        }
    }

    public static void stopPreviewFromSpeakers(Context context) {
        getAsyncRingtonePlayer(context).stop();
    }

    public static void start(Context context, SharedPreferences prefs, Uri uri) {
        stop(context, prefs);
        if (SettingsDAO.isAdvancedAudioPlaybackEnabled(prefs)) {
            getRingtonePlayer(context).play(uri, 0);
        } else {
            getAsyncRingtonePlayer(context).play(uri, 0);
        }
    }

    public static void startPreviewOnlyFromSpeakers(Context context, Uri uri) {
        stopPreviewFromSpeakers(context);
        getAsyncRingtonePlayer(context).play(uri, 0);
    }

    // MediaPlayer
    private static synchronized AsyncRingtonePlayer getAsyncRingtonePlayer(Context context) {
        if (sAsyncRingtonePlayer == null) {
            sAsyncRingtonePlayer = new AsyncRingtonePlayer(context.getApplicationContext());
        }

        return sAsyncRingtonePlayer;
    }

    public static synchronized void releaseResources() {
        if (sAsyncRingtonePlayer != null) {
            sAsyncRingtonePlayer.shutdown();
            sAsyncRingtonePlayer = null;
        }
    }

    // ExoPlayer
    private static synchronized RingtonePlayer getRingtonePlayer(Context context) {
        if (sRingtonePlayer == null) {
            sRingtonePlayer = new RingtonePlayer(context.getApplicationContext());
        }

        return sRingtonePlayer;
    }

    public static synchronized void stopListeningToPreferences() {
        if (sRingtonePlayer != null) {
            sRingtonePlayer.stopListeningToPreferences();
            sRingtonePlayer = null;
        }
    }
}

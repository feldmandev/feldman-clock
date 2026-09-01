/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import static android.media.AudioManager.STREAM_ALARM;
import static android.media.RingtoneManager.TITLE_COLUMN_INDEX;
import static com.feldman.clock.core.util.RingtoneUtils.RANDOM_CUSTOM_RINGTONE;
import static com.feldman.clock.core.util.RingtoneUtils.RANDOM_RINGTONE;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.Log;

import com.feldman.clock.R;
import com.feldman.clock.core.util.RingtoneUtils;
import com.feldman.clock.core.util.Utils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * All ringtone data is accessed via this model.
 */
public final class RingtoneModel {

    private final Context mContext;

    private final SharedPreferences mPrefs;

    /**
     * Maps ringtone uri to ringtone title; looking up a title from scratch is expensive.
     */
    private final Map<Uri, String> mRingtoneTitles = new ArrayMap<>(16);

    /**
     * Clears data structures containing data that is locale-sensitive.
     */
    private final BroadcastReceiver mLocaleChangedReceiver = new LocaleChangedReceiver();

    /**
     * Observer for changes to system settings affecting the default alarm ringtone.
     * <p>
     * This observer listens for modifications to system settings, such as changes to the default
     * alarm ringtone.
     * When a change is detected, it triggers the clearing of the ringtone title cache to ensure
     * that the displayed information remains up-to-date. This prevents outdated titles
     * (like those of default ringtones) from being used after a change in system settings.
     * <p>
     * The observer is registered to listen for changes to the URI
     * `Settings.System.DEFAULT_ALARM_ALERT_URI`, which corresponds to the default alarm ringtone
     *  on the device.
     */
    private final ContentObserver mSystemObserver = new SystemAlarmAlertChangeObserver();

    /**
     * A mutable copy of the custom ringtones.
     */
    private List<CustomRingtone> mCustomRingtones;

    public RingtoneModel(Context context, SharedPreferences prefs) {
        mContext = Utils.getSafeStorageContext(context);

        mPrefs = prefs;

        // Clear caches affected by system settings when system settings change.
        final ContentResolver cr = mContext.getContentResolver();
        cr.registerContentObserver(Settings.System.DEFAULT_ALARM_ALERT_URI, false, mSystemObserver);

        // Clear caches affected by locale when locale changes.
        final IntentFilter localeBroadcastFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter, Context.RECEIVER_NOT_EXPORTED);

    }

    private CustomRingtone getCustomRingtone(Uri uri) {
        for (CustomRingtone ringtone : getMutableCustomRingtones()) {
            if (ringtone.getUri().equals(uri)) {
                return ringtone;
            }
        }

        return null;
    }

    public List<CustomRingtone> getCustomRingtones() {
        return Collections.unmodifiableList(getMutableCustomRingtones());
    }

    void loadRingtoneTitles() {
        // Early return if the cache is already primed.
        if (!mRingtoneTitles.isEmpty()) {
            return;
        }

        final RingtoneManager ringtoneManager = new RingtoneManager(mContext);
        ringtoneManager.setType(STREAM_ALARM);

        // Cache a title for each system ringtone.
        try (Cursor cursor = ringtoneManager.getCursor()) {
            for (cursor.moveToFirst(); !cursor.isAfterLast(); cursor.moveToNext()) {
                final String ringtoneTitle = cursor.getString(TITLE_COLUMN_INDEX);
                final Uri ringtoneUri = ringtoneManager.getRingtoneUri(cursor.getPosition());
                mRingtoneTitles.put(ringtoneUri, ringtoneTitle);
            }
        } catch (Throwable throwable) {
            Log.e("67","Error loading ringtone title cache", throwable);
        }
    }

    String getRingtoneTitle(Uri uri) {
        final Context localizedContext = Utils.getLocalizedContext(mContext);

        // Special case: no ringtone has a title of "random" or "random_custom.
        if (RANDOM_RINGTONE.equals(uri) || RANDOM_CUSTOM_RINGTONE.equals(uri)) {
            return localizedContext.getString(R.string.random_ringtone_title);
        }

        // Special case: no ringtone has a title of "Silent".
        if (RingtoneUtils.RINGTONE_SILENT.equals(uri)) {
            return localizedContext.getString(R.string.silent_ringtone_title);
        }

        // If the ringtone is custom, it has its own title.
        final CustomRingtone customRingtone = getCustomRingtone(uri);
        if (customRingtone != null) {
            return customRingtone.getTitle();
        }

        // Check the cache.
        String title = mRingtoneTitles.get(uri);

        if (title == null) {
            // This is slow because a media player is created during Ringtone object creation.
            final Ringtone ringtone = RingtoneManager.getRingtone(mContext, uri);
            if (ringtone == null) {
                Log.e("67","No ringtone for uri: %s");
                return localizedContext.getString(R.string.unknown_ringtone_title);
            }

            // Cache the title for later use.
            title = ringtone.getTitle(mContext);
            mRingtoneTitles.put(uri, title);
        }
        return title;
    }

    private List<CustomRingtone> getMutableCustomRingtones() {
        if (mCustomRingtones == null) {
            mCustomRingtones = CustomRingtoneDAO.getCustomRingtones(mPrefs);
            Collections.sort(mCustomRingtones);
        }

        return mCustomRingtones;
    }

    /**
     * Releases the resources used by the ringtone model, including observers and receivers.
     * <p>
     * Calling this method is crucial to avoid memory leaks, especially when the `RingtoneModel`
     * instance is no longer in use, and to ensure that the application does not retain unnecessary
     * references to system resources.
     */
    public void releaseResources() {
        try {
            mContext.getContentResolver().unregisterContentObserver(mSystemObserver);
        } catch (Exception e) {
            Log.e("67","Failed to unregister ContentObserver", e);
        }
        try {
            mContext.unregisterReceiver(mLocaleChangedReceiver);
        } catch (Exception e) {
            Log.e("67","Failed to unregister BroadcastReceiver", e);
        }
    }

    /**
     * This receiver is notified when system settings change. Cached information built on
     * those system settings must be cleared.
     */
    private final class SystemAlarmAlertChangeObserver extends ContentObserver {

        private SystemAlarmAlertChangeObserver() {
            super(new Handler(Looper.getMainLooper()));
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);

            // Titles such as "Default ringtone (Oxygen)" are wrong after default ringtone changes.
            mRingtoneTitles.clear();
        }
    }

    /**
     * Cached information that is locale-sensitive must be cleared in response to locale changes.
     */
    private final class LocaleChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Titles such as "Default ringtone (Oxygen)" are wrong after locale changes.
            mRingtoneTitles.clear();
        }
    }
}

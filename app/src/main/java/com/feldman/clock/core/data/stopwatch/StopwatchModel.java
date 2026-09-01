/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import android.Manifest;
import android.app.Notification;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.SystemClock;
import androidx.annotation.VisibleForTesting;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;

import com.feldman.clock.core.data.stopwatch.Lap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * All {@link Stopwatch} data is accessed via this model.
 */
final class StopwatchModel {

    private static final long PAUSED_LIVE_UPDATE_TIMEOUT_MILLIS = 60_000L;

    private final Context mContext;

    private final SharedPreferences mPrefs;

    /**
     * The model from which notification data are fetched.
     */
    private final NotificationModel mNotificationModel;

    /**
     * Used to create and destroy system notifications related to the stopwatch.
     */
    private final NotificationManagerCompat mNotificationManager;

    /**
     * Update stopwatch notification when locale changes.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final BroadcastReceiver mLocaleChangedReceiver = new LocaleChangedReceiver();

    /**
     * The listeners to notify when the stopwatch or its laps change.
     */
    private final List<StopwatchListener> mStopwatchListeners = new ArrayList<>();

    /**
     * Delegate that builds platform-specific stopwatch notifications.
     */
    private final StopwatchNotificationBuilder mNotificationBuilder =
            new StopwatchNotificationBuilder();

    /**
     * The current state of the stopwatch.
     */
    private Stopwatch mStopwatch;

    private long mPausedLiveUpdateExpiresAt;

    /**
     * A mutable copy of the recorded stopwatch laps.
     */
    private List<Lap> mLaps;

    StopwatchModel(Context context, SharedPreferences prefs, NotificationModel notificationModel) {
        mContext = context;
        mPrefs = prefs;
        mNotificationModel = notificationModel;
        mNotificationManager = NotificationManagerCompat.from(context);

        // Update stopwatch notification when locale changes.
        final IntentFilter localeBroadcastFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    /**
     * @param stopwatchListener to be notified when stopwatch changes or laps are added
     */
    void addStopwatchListener(StopwatchListener stopwatchListener) {
        mStopwatchListeners.add(stopwatchListener);
    }

    /**
     * @param stopwatchListener to be removed from the list of listeners
     */
    void removeStopwatchListener(StopwatchListener stopwatchListener) {
        mStopwatchListeners.remove(stopwatchListener);
    }

    /**
     * @return the current state of the stopwatch
     */
    Stopwatch getStopwatch() {
        if (mStopwatch == null) {
            mStopwatch = StopwatchDAO.getStopwatch(mPrefs);
        }

        return mStopwatch;
    }

    /**
     * @param stopwatch the new state of the stopwatch
     */
    void setStopwatch(Stopwatch stopwatch) {
        final Stopwatch before = getStopwatch();
        if (before != stopwatch) {
            if (before.isRunning() && stopwatch.isPaused()) {
                mPausedLiveUpdateExpiresAt = SystemClock.elapsedRealtime()
                        + PAUSED_LIVE_UPDATE_TIMEOUT_MILLIS;
            } else if (!stopwatch.isPaused()) {
                mPausedLiveUpdateExpiresAt = 0L;
            }
            StopwatchDAO.setStopwatch(mPrefs, stopwatch);
            mStopwatch = stopwatch;

            // Refresh the stopwatch notification to reflect the latest stopwatch state.
            if (!mNotificationModel.isApplicationInForeground()) {
                updateNotification();
            }

            // Resetting the stopwatch implicitly clears the recorded laps.
            if (stopwatch.isReset()) {
                clearLaps();
            }

            // Notify listeners of the stopwatch change.
            for (StopwatchListener stopwatchListener : mStopwatchListeners) {
                stopwatchListener.stopwatchUpdated(stopwatch);
            }
        }
    }

    /**
     * @return the laps recorded for this stopwatch
     */
    List<Lap> getLaps() {
        return Collections.unmodifiableList(getMutableLaps());
    }

    /**
     * @return a newly recorded lap completed now; {@code null} if no more laps can be added
     */
    Lap addLap() {
        if (!mStopwatch.isRunning() || !canAddMoreLaps()) {
            return null;
        }

        final long now = getStopwatch().getTotalTime();
        final List<Lap> laps = getMutableLaps();

        // 1️⃣ Finalize the previous lap (if it exists)
        if (!laps.isEmpty()) {
            final Lap previous = laps.get(0); // most recent (open) lap
            final Lap finalized = new Lap(
                    previous.getLapNumber(),
                    now - previous.getAccumulatedTime(), // duration = now - start
                    previous.getAccumulatedTime()
            );
            laps.set(0, finalized);
        }

        // 2️⃣ Create a new open lap
        final int lapNumber = laps.size() + 1;
        StopwatchDAO.addLap(mPrefs, lapNumber, now);

        final Lap newLap = new Lap(
                lapNumber,
                0L,     // open lap, duration not known yet
                now     // start time of this lap
        );

        laps.add(0, newLap);

        // Refresh notification
        if (!mNotificationModel.isApplicationInForeground()) {
            updateNotification();
        }

        // Notify listeners
        for (StopwatchListener stopwatchListener : mStopwatchListeners) {
            stopwatchListener.lapAdded(newLap);
        }

        return newLap;
    }


    /**
     * Clears the laps recorded for this stopwatch.
     */
    @VisibleForTesting
    void clearLaps() {
        StopwatchDAO.clearLaps(mPrefs);
        getMutableLaps().clear();
    }

    /**
     * @return {@code true} iff more laps can be recorded
     */
    boolean canAddMoreLaps() {
        return getLaps().size() < 98;
    }

    /**
     * Updates the notification to reflect the latest state of the stopwatch and recorded laps.
     */
    void updateNotification() {
        final Stopwatch stopwatch = getStopwatch();
        final long pausedLiveUpdateTimeoutMillis = Math.max(0L,
                mPausedLiveUpdateExpiresAt - SystemClock.elapsedRealtime());

        // Notification should be hidden if the stopwatch has no time, the app is open, or it was
        // paused before this process started.
        if (stopwatch.isReset() || mNotificationModel.isApplicationInForeground()
                || (!stopwatch.isRunning() && pausedLiveUpdateTimeoutMillis == 0L)) {
            mNotificationManager.cancel(mNotificationModel.getStopwatchNotificationId());
            return;
        }

        // Otherwise build and post a notification reflecting the latest stopwatch state.
        final Notification notification = mNotificationBuilder.build(
                mContext, mNotificationModel, stopwatch, pausedLiveUpdateTimeoutMillis);

        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Always false, because notification activation is always checked when the application is started.
            return;
        }

        mNotificationManager.notify(mNotificationModel.getStopwatchNotificationId(), notification);
    }

    private List<Lap> getMutableLaps() {
        if (mLaps == null) {
            mLaps = StopwatchDAO.getLaps(mPrefs);
        }

        return mLaps;
    }

    /**
     * Update the stopwatch notification in response to a locale change.
     */
    private final class LocaleChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateNotification();
        }
    }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.timer;

import com.feldman.clock.app.ClockApplication;

import static com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.IBinder;
import android.util.Log;

import com.feldman.clock.R;
import com.feldman.clock.core.data.DataModel;
import com.feldman.clock.settings.SettingsDAO;
import com.feldman.clock.core.data.Timer;
import com.feldman.clock.core.analytics.Events;

import java.util.Arrays;

/**
 * <p>This service exists solely to allow {@link android.app.AlarmManager} and timer notifications
 * to alter the state of timers without disturbing the notification shade. If an activity were used
 * instead (even one that is not displayed) the notification manager implicitly closes the
 * notification shade which clashes with the use case of starting/pausing/resetting timers without
 * disturbing the notification shade.</p>
 *
 * <p>The service has a second benefit. It is used to start heads-up notifications for expired
 * timers in the foreground. This keeps the entire application in the foreground and thus prevents
 * the operating system from killing it while expired timers are firing.</p>
 */
public final class TimerService extends Service {

    /**
     * Extra for many actions specific to a given timer.
     */
    public static final String EXTRA_TIMER_ID = "com.feldman.clock.extra.TIMER_ID";
    private static final String ACTION_PREFIX = "com.feldman.clock.action.";

    /**
     * Shows the tab with timers; scrolls to a specific timer.
     */
    public static final String ACTION_SHOW_TIMER = ACTION_PREFIX + "SHOW_TIMER";

    /**
     * Pauses running timers; resets expired timers.
     */
    public static final String ACTION_PAUSE_TIMER = ACTION_PREFIX + "PAUSE_TIMER";

    /**
     * Starts the sole timer.
     */
    public static final String ACTION_START_TIMER = ACTION_PREFIX + "START_TIMER";

    /**
     * Resets the timer.
     */
    public static final String ACTION_RESET_TIMER = ACTION_PREFIX + "RESET_TIMER";

    /**
     * Adds minutes or hour to the timer.
     */
    public static final String ACTION_ADD_CUSTOM_TIME_TO_TIMER = ACTION_PREFIX + "ADD_CUSTOM_TIME_TO_TIMER";
    private static final String ACTION_TIMER_EXPIRED = ACTION_PREFIX + "TIMER_EXPIRED";
    private static final String ACTION_UPDATE_NOTIFICATION = ACTION_PREFIX + "UPDATE_NOTIFICATION";
    public static final String ACTION_DISMISS_NOTIFICATION = ACTION_PREFIX + "DISMISS_NOTIFICATION";
    public static final String ACTION_RESET_EXPIRED_TIMERS = ACTION_PREFIX + "RESET_EXPIRED_TIMERS";
    private static final String ACTION_RESET_MISSED_TIMERS = ACTION_PREFIX + "RESET_MISSED_TIMERS";

    public static Intent createTimerExpiredIntent(Context context, Timer timer) {
        final int timerId = timer == null ? -1 : timer.getId();
        return new Intent(context, TimerService.class)
                .setAction(ACTION_TIMER_EXPIRED)
                .putExtra(EXTRA_TIMER_ID, timerId);
    }

    public static Intent createResetExpiredTimersIntent(Context context) {
        return new Intent(context, TimerService.class)
                .setAction(ACTION_RESET_EXPIRED_TIMERS);
    }

    public static Intent createResetMissedTimersIntent(Context context) {
        return new Intent(context, TimerService.class)
                .setAction(ACTION_RESET_MISSED_TIMERS);
    }

    public static Intent createAddCustomTimeToTimerIntent(Context context, int timerId) {
        return new Intent(context, TimerService.class)
                .setAction(ACTION_ADD_CUSTOM_TIME_TO_TIMER)
                .putExtra(EXTRA_TIMER_ID, timerId);
    }

    public static Intent createUpdateNotificationIntent(Context context) {
        return new Intent(context, TimerService.class)
                .setAction(ACTION_UPDATE_NOTIFICATION);
    }

    private SharedPreferences mPrefs;

    private SensorManager mSensorManager;
    private boolean mIsFlipActionEnabled;
    private boolean mIsShakeActionEnabled;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private static final String TAG = "DeskClockTimerDebug";

    @Override
    public void onCreate() {
        Log.d(TAG, "TimerService onCreate");
        super.onCreate();

        mPrefs = getDefaultSharedPreferences(this);
        // Set up for flip and shake actions
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        mIsFlipActionEnabled = SettingsDAO.isFlipActionForTimersEnabled(mPrefs);
        mIsShakeActionEnabled = SettingsDAO.isShakeActionForTimersEnabled(mPrefs);

        DataModel.getDataModel().setTimerService(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            final String action = intent == null ? null : intent.getAction();
            final int label = intent == null ? R.string.label_intent : intent.getIntExtra(Events.EXTRA_EVENT_LABEL, R.string.label_intent);

            Log.d(TAG, "TimerService onStartCommand action=" + action);
            
            // Always ensure notification is updated when service starts
            // BUT skip for Dismiss action to avoid reposting the notification the user just swiped away!
            if (!ACTION_DISMISS_NOTIFICATION.equals(action)) {
                DataModel.getDataModel().updateTimerNotification();
            }

            if (action != null) {
                switch (action) {
                    case ACTION_UPDATE_NOTIFICATION -> {
                        DataModel.getDataModel().updateTimerNotification();
                        return START_STICKY;
                    }
                    case ACTION_DISMISS_NOTIFICATION -> {
                        final int dismissedTimerId = intent.getIntExtra(EXTRA_TIMER_ID, -1);
                        if (dismissedTimerId != -1) {
                            DataModel.getDataModel().getTimerModel().markNotificationDismissed(dismissedTimerId);
                            // Verify state after dismissal (e.g. stop service if all dismissed?)
                            DataModel.getDataModel().updateTimerNotification();
                        }
                        return START_STICKY;
                    }
                    case ACTION_RESET_EXPIRED_TIMERS -> {
                        DataModel.getDataModel().resetOrDeleteExpiredTimers(label);
                        // Cancel the heads-up notification after resetting
                        DataModel.getDataModel().updateTimerNotification();
                        return START_STICKY;
                    }
                    case ACTION_RESET_MISSED_TIMERS -> {
                        DataModel.getDataModel().resetMissedTimers(label);
                        return START_STICKY;
                    }
                }
            }

            // Look up the timer in question (only if intent is not null)
            if (intent == null) {
                return START_STICKY;
            }
            
            final int timerId = intent.getIntExtra(EXTRA_TIMER_ID, -1);
            final Timer timer = DataModel.getDataModel().getTimer(timerId);

            // If the timer cannot be located, ignore the action.
            if (timer == null) {
                return START_STICKY;
            }

            // Perform the action on the timer.
            if (action != null) {
                switch (action) {
                    case ACTION_START_TIMER -> {
                        Events.sendTimerEvent(R.string.action_start, label);
                        DataModel.getDataModel().getTimerModel().clearPausedLiveUpdate(timerId);
                        DataModel.getDataModel().startTimer(this, timer);
                    }
                    case ACTION_PAUSE_TIMER -> {
                        Events.sendTimerEvent(R.string.action_pause, label);
                        DataModel.getDataModel().pauseTimer(timer);
                    }
                    case ACTION_ADD_CUSTOM_TIME_TO_TIMER -> {
                        Events.sendTimerEvent(R.string.action_add_custom_time_to_timer, label);
                        DataModel.getDataModel().addCustomTimeToTimer(timer);
                        // Update notification after adding time (timer will restart)
                        DataModel.getDataModel().updateTimerNotification();
                    }
                    case ACTION_RESET_TIMER -> {
                        DataModel.getDataModel().resetOrDeleteTimer(timer, label);
                        detachListeners();
                    }
                    case ACTION_TIMER_EXPIRED -> {
                        Events.sendTimerEvent(R.string.action_fire, label);
                        DataModel.getDataModel().expireTimer(this, timer);
                        attachListeners();
                    }
                }
            }
        } finally {
            // Keep service running if there are any running/paused timers or expired timers
            final boolean hasExpiredTimers = !DataModel.getDataModel().getExpiredTimers().isEmpty();
            final boolean hasActiveTimers = DataModel.getDataModel().hasActiveTimers();
            
            if (!hasExpiredTimers && !hasActiveTimers) {
                Log.d(TAG, "TimerService stopping self (no active/expired timers)");
                stopSelf();
            } else {
                Log.d(TAG, "TimerService staying alive (has " + 
                    (hasExpiredTimers ? "expired" : "") + 
                    (hasExpiredTimers && hasActiveTimers ? " and " : "") +
                    (hasActiveTimers ? "active" : "") + " timers)");
            }
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "TimerService onDestroy");
        DataModel.getDataModel().setTimerService(null);
        detachListeners();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "TimerService onTaskRemoved");
        if (DataModel.getDataModel().getExpiredTimers().isEmpty() && 
            !DataModel.getDataModel().hasActiveTimers()) {
            Log.d(TAG, "TimerService onTaskRemoved: stopping self");
            stopSelf();
        } else {
            Log.d(TAG, "TimerService onTaskRemoved: staying alive");
        }
    }

    private final ResettableSensorEventListener mFlipListener = new ResettableSensorEventListener() {

        // Accelerometers are not quite accurate.
        private static final float GRAVITY_UPPER_THRESHOLD = 1.3f * SensorManager.STANDARD_GRAVITY;
        private static final float GRAVITY_LOWER_THRESHOLD = 0.7f * SensorManager.STANDARD_GRAVITY;
        private static final int SENSOR_SAMPLES = 3;
        private final boolean[] mSamples = new boolean[SENSOR_SAMPLES];
        private boolean mStopped;
        private boolean mWasFaceUp;
        private int mSampleIndex;

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        @Override
        public void reset() {
            mWasFaceUp = false;
            mStopped = false;
            Arrays.fill(mSamples, false);
        }

        private boolean filterSamples() {
            boolean allPass = true;
            for (boolean sample : mSamples) {
                allPass = allPass && sample;
            }
            return allPass;
        }

        @Override
        public void onSensorChanged(SensorEvent event) {
            // Add a sample overwriting the oldest one. Several samples
            // are used to avoid the erroneous values the sensor sometimes
            // returns.
            float z = event.values[2];

            if (mStopped) {
                return;
            }

            if (!mWasFaceUp) {
                // Check if its face up enough.
                mSamples[mSampleIndex] = (z > GRAVITY_LOWER_THRESHOLD) && (z < GRAVITY_UPPER_THRESHOLD);

                // face up
                if (filterSamples()) {
                    mWasFaceUp = true;
                    Arrays.fill(mSamples, false);
                }
            } else {
                // Check if its face down enough.
                mSamples[mSampleIndex] = (z < -GRAVITY_LOWER_THRESHOLD) && (z > -GRAVITY_UPPER_THRESHOLD);

                // face down
                if (filterSamples()) {
                    mStopped = true;
                    handleAction(mIsFlipActionEnabled);
                }
            }

            mSampleIndex = ((mSampleIndex + 1) % SENSOR_SAMPLES);
        }
    };

    private final SensorEventListener mShakeListener = new SensorEventListener() {
        private static final int BUFFER = 5;
        private final float[] gravity = new float[3];
        private float average = 0;
        private int fill = 0;

        @Override
        public void onAccuracyChanged(Sensor sensor, int acc) {
        }

        public void onSensorChanged(SensorEvent event) {
            final float alpha = 0.8F;

            for (int i = 0; i < 3; i++) {
                gravity[i] = alpha * gravity[i] + (1 - alpha) * event.values[i];
            }

            float x = event.values[0] - gravity[0];
            float y = event.values[1] - gravity[1];
            float z = event.values[2] - gravity[2];

            float sensitivity = SettingsDAO.getTimerShakeIntensity(mPrefs);

            if (fill <= BUFFER) {
                average += Math.abs(x) + Math.abs(y) + Math.abs(z);
                fill++;
            } else {
                if (average / BUFFER >= sensitivity) {
                    handleAction(mIsShakeActionEnabled);
                }
                average = 0;
                fill = 0;
            }
        }
    };

    private void attachListeners() {
        if (mIsFlipActionEnabled) {
            mFlipListener.reset();
            mSensorManager.registerListener(mFlipListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_NORMAL,
                    300 * 1000); //batch every 300 milliseconds
        }

        if (mIsShakeActionEnabled) {
            mSensorManager.registerListener(mShakeListener,
                    mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                    SensorManager.SENSOR_DELAY_GAME,
                    50 * 1000); //batch every 50 milliseconds
        }
    }

    private void detachListeners() {
        if (mIsFlipActionEnabled) {
            mSensorManager.unregisterListener(mFlipListener);
        }

        if (mIsShakeActionEnabled) {
            mSensorManager.unregisterListener(mShakeListener);
        }
    }

    private void handleAction(boolean actionIsEnabled) {
        if (actionIsEnabled) {
            startService(createResetExpiredTimersIntent(this));
        }
    }

    private interface ResettableSensorEventListener extends SensorEventListener {
        void reset();
    }
}

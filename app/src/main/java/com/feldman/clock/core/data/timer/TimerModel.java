/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import static android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP;
import static android.text.format.DateUtils.MINUTE_IN_MILLIS;
import static com.feldman.clock.core.data.Timer.State.EXPIRED;
import static com.feldman.clock.core.data.Timer.State.RESET;
import static com.feldman.clock.settings.PreferencesDefaultValues.TIMEOUT_END_OF_RINGTONE;
import static com.feldman.clock.settings.PreferencesDefaultValues.TIMEOUT_NEVER;
import static com.feldman.clock.settings.PreferencesKeys.KEY_TIMER_RINGTONE;
import android.Manifest;
import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.Log;
import android.util.SparseLongArray;
import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import com.feldman.clock.alarm.AlarmAlertWakeLock;
import com.feldman.clock.R;
import com.feldman.clock.settings.SettingsDAO;
import com.feldman.clock.core.analytics.Events;
import com.feldman.clock.timer.TimerRinger;
import com.feldman.clock.timer.TimerService;
import com.feldman.clock.core.util.NotificationUtils;
import com.feldman.clock.core.util.RingtoneUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * All {@link Timer} data is accessed via this model.
 */
public final class TimerModel {

    private static final long PAUSED_LIVE_UPDATE_TIMEOUT_MILLIS = 60_000L;

    /**
     * Running timers less than this threshold are left running/expired; greater than this
     * threshold are considered missed.
     */
    private static final long MISSED_THRESHOLD = -MINUTE_IN_MILLIS;

    private final Context mContext;

    private final SharedPreferences mPrefs;

    /**
     * The alarm manager system service that calls back when timers expire.
     */
    private final AlarmManager mAlarmManager;

    /**
     * The model from which notification data are fetched.
     */
    private final NotificationModel mNotificationModel;

    /**
     * The model from which ringtone data are fetched.
     */
    private final RingtoneModel mRingtoneModel;

    /**
     * Used to create and destroy system notifications related to timers.
     */
    private final NotificationManagerCompat mNotificationManager;

    /**
     * Update timer notification when locale changes.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final BroadcastReceiver mLocaleChangedReceiver = new LocaleChangedReceiver();

    /**
     * Retain a hard reference to the shared preference observer to prevent it from being garbage
     * collected. See {@link SharedPreferences#registerOnSharedPreferenceChangeListener} for detail.
     */
    @SuppressWarnings("FieldCanBeLocal")
    private final OnSharedPreferenceChangeListener mPreferenceListener = new PreferenceListener();

    /**
     * The listeners to notify when a timer is added, updated or removed.
     */
    private final List<TimerListener> mTimerListeners = new ArrayList<>();

    /**
     * Delegate that builds platform-specific timer notifications.
     */
    private final TimerNotificationBuilder mNotificationBuilder = new TimerNotificationBuilder();

    /** Handler for fast updates when service is active */
    private final Handler mNotificationUpdateHandler = new Handler(Looper.getMainLooper());
    
    /** Runnable for 1-second notification updates */
    private final Runnable mNotificationUpdateRunnable = new Runnable() {
        @Override
        public void run() {
            updateNotification();
            // Continue if there are still running timers
            if (hasRunningTimer()) {
                mNotificationUpdateHandler.postDelayed(this, 1000);
            }
        }
    };

    private final Runnable mPausedLiveUpdateExpirationRunnable = this::expirePausedLiveUpdates;

    private void expirePausedLiveUpdates() {
        updateNotification();
        com.feldman.clock.app.integration.AtAGlanceProvider.notifyChanged(mContext);
    }

    private boolean hasRunningTimer() {
        for (Timer timer : mTimers) {
            if (timer.isRunning()) {
                return true;
            }
        }
        return false;
    }

    private boolean hasPausedLiveUpdate() {
        for (Timer timer : getMutableTimers()) {
            if (timer.isPaused() && getPausedLiveUpdateTimeoutMillis(timer.getId()) > 0L) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Track if the service has been started in foreground mode.
     */
    private boolean mIsForeground = false;

    /**
     * Ensure the foreground service is started to keep the app alive.
     * We use the system chronometer for updates, so no manual 1-second loop is needed!
     */
    private void ensureForegroundService() {
        // Start foreground service to keep it alive even when app is closed
        // Use SPECIAL_USE type which has no timeout (unlike SHORT_SERVICE)
        if (mService != null && hasRunningTimer()) {
            // Get the first running timer's notification to use for foreground
            for (Timer timer : getMutableTimers()) {
                if (timer.isRunning()) {
                    // Only start foreground if not already started or if forced
                    // Note: Update notification loop will handle content updates via notify()
                    // This method is primarily to ensuring the "Foreground" state is set.
                    if (!mIsForeground) {
                        try {
                            int notificationId = mNotificationModel.getUnexpiredTimerNotificationId(timer.getId());
                            Notification notification = mNotificationBuilder.build(
                                    mContext, mNotificationModel, timer, null, 0L);
                            
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                mService.startForeground(notificationId, notification, 
                                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                            } else {
                                mService.startForeground(notificationId, notification);
                            }
                            mIsForeground = true;
                            Log.d(TAG, "Started foreground service with SPECIAL_USE for timer " + timer.getId());
                        } catch (Exception e) {
                            Log.e(TAG, "FAILED to start foreground service! This will cause updates to stop when app is closed.", e);
                        }
                    } else {
                        // Already foreground - no need to spam startForeground
                        Log.v(TAG, "Service already foreground, skipping startForeground()");
                    }
                    break;  // Only need one notification for foreground
                }
            }
        } else {
            Log.w(TAG, "Cannot start foreground: service=" + mService + " hasRunning=" + hasRunningTimer());
        }
    }
    
    private void stopNotificationUpdates() {
        mNotificationUpdateHandler.removeCallbacks(mNotificationUpdateRunnable);
        mNotificationUpdateHandler.removeCallbacks(mPausedLiveUpdateExpirationRunnable);
        cancelAlarmManagerUpdates();
        // Keep the existing foreground-service grant while switching from the running-timer
        // notification to the expired-timer heads-up notification.
        if (mService != null && getExpiredTimers().isEmpty()) {
            mService.stopForeground(true);
            mIsForeground = false;
        }
        Log.d(TAG, "Stopped all notification updates");
    }

    private void schedulePausedLiveUpdateExpiry() {
        long nextExpiryMillis = Long.MAX_VALUE;
        for (Timer timer : getMutableTimers()) {
            if (timer.isPaused()) {
                final long timeoutMillis = getPausedLiveUpdateTimeoutMillis(timer.getId());
                if (timeoutMillis > 0L) {
                    nextExpiryMillis = Math.min(nextExpiryMillis, timeoutMillis);
                }
            }
        }

        mNotificationUpdateHandler.removeCallbacks(mPausedLiveUpdateExpirationRunnable);
        if (nextExpiryMillis != Long.MAX_VALUE) {
            mNotificationUpdateHandler.postDelayed(
                    mPausedLiveUpdateExpirationRunnable, nextExpiryMillis);
        }
    }
    
    /**
     * Schedule AlarmManager updates as a backup (not used actively anymore).
     * We now use Handler for 1-second updates when timers are running.
     */
    private void scheduleAlarmManagerUpdate() {
        if (!hasRunningTimer()) {
            // No running timers, cancel any pending updates
            cancelAlarmManagerUpdates();
            return;
        }
        
        // Schedule update for 1 second from now
        final long nextUpdateTime = System.currentTimeMillis() + 1000;
        final Intent intent = TimerService.createUpdateNotificationIntent(mContext);
        final PendingIntent pendingIntent = PendingIntent.getService(
            mContext,
            0,  // Request code
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // Use setExact for precise 1-second intervals
        // Note: setExactAndAllowWhileIdle has rate limits and causes 5-second delays
        mAlarmManager.setExact(
            AlarmManager.RTC_WAKEUP,
            nextUpdateTime,
            pendingIntent
        );
    }
    
    private void cancelAlarmManagerUpdates() {
        final Intent intent = TimerService.createUpdateNotificationIntent(mContext);
        final PendingIntent pendingIntent = PendingIntent.getService(
            mContext,
            0,
            intent,
            PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE
        );
        if (pendingIntent != null) {
            mAlarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    /** Track last notification content to avoid unnecessary updates */
    private final java.util.Map<Integer, String> mLastNotificationContent = new java.util.HashMap<>();

    private String getNotificationContentKey(Timer timer) {
        return timer.isRunning() + "|" + (timer.getRemainingTime() / 1000) + "|" + timer.getLabel();
    }

    /** Track timers whose notifications were dismissed by user */
    private final Set<Integer> mDismissedTimerNotifications = new ArraySet<>();

    public void markNotificationDismissed(int timerId) {
        mDismissedTimerNotifications.add(timerId);
        Log.d(TAG, "markNotificationDismissed: Timer " + timerId + " notification dismissed by user");
    }

    public void clearNotificationDismissed(int timerId) {
        mDismissedTimerNotifications.remove(timerId);
        Log.d(TAG, "clearNotificationDismissed: Timer " + timerId + " notification can be shown again");
    }

    /** The expiry time for a paused timer's temporary live update. */
    private final SparseLongArray mPausedLiveUpdateExpiresAt = new SparseLongArray();

    public void markPausedLiveUpdate(int timerId) {
        mPausedLiveUpdateExpiresAt.put(timerId,
                SystemClock.elapsedRealtime() + PAUSED_LIVE_UPDATE_TIMEOUT_MILLIS);
    }

    public void clearPausedLiveUpdate(int timerId) {
        mPausedLiveUpdateExpiresAt.delete(timerId);
    }

    public boolean isPausedLiveUpdateActive(int timerId) {
        return getPausedLiveUpdateTimeoutMillis(timerId) > 0L;
    }

    private long getPausedLiveUpdateTimeoutMillis(int timerId) {
        final long timeoutMillis = mPausedLiveUpdateExpiresAt.get(timerId)
                - SystemClock.elapsedRealtime();
        if (timeoutMillis <= 0L) {
            mPausedLiveUpdateExpiresAt.delete(timerId);
            return 0L;
        }
        return timeoutMillis;
    }

    /**
     * The ids of expired timers for which the ringer is ringing. Not all expired timers have their
     * ids in this collection. If a timer was already expired when the app was started its id will
     * be absent from this collection.
     */
    @SuppressLint("NewApi")
    private final Set<Integer> mRingingIds = new ArraySet<>();

    /**
     * The uri of the default ringtone to use for timers until the user explicitly chooses one.
     */
    private Uri mDefaultTimerRingtoneUri;

    /**
     * The uri of the ringtone to play for timers.
     */
    private Uri mTimerRingtoneUri;

    /**
     * The title of the ringtone to play for timers.
     */
    private String mTimerRingtoneTitle;

    /**
     * A mutable copy of the timers.
     */
    private List<Timer> mTimers;

    /**
     * A mutable copy of the expired timers.
     */
    private List<Timer> mExpiredTimers;

    /**
     * A mutable copy of the missed timers.
     */
    private List<Timer> mMissedTimers;

    /**
     * The service that keeps this application in the foreground while a heads-up timer
     * notification is displayed. Marking the service as foreground prevents the operating system
     * from killing this application while expired timers are actively firing.
     */
    private static final String TAG = "DeskClockTimerDebug";

    /**
     * The service to notify when the timer state changes.
     */
    private Service mService;

    void setService(Service service) {
        Log.d(TAG, "TimerModel setService: " + service);
        mService = service;
        if (mService == null) {
            mIsForeground = false;
        }
        updateNotification();
        updateHeadsUpNotification();
    }

    TimerModel(Context context, SharedPreferences prefs, RingtoneModel ringtoneModel,
               NotificationModel notificationModel) {

        mContext = context;
        mPrefs = prefs;
        mRingtoneModel = ringtoneModel;
        mNotificationModel = notificationModel;
        mNotificationManager = NotificationManagerCompat.from(context);

        mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);

        // Clear caches affected by preferences when preferences change.
        prefs.registerOnSharedPreferenceChangeListener(mPreferenceListener);

        // Update timer notification when locale changes.
        final IntentFilter localeBroadcastFilter = new IntentFilter(Intent.ACTION_LOCALE_CHANGED);
        mContext.registerReceiver(mLocaleChangedReceiver, localeBroadcastFilter, Context.RECEIVER_NOT_EXPORTED);
    }

    static void schedulePendingIntent(AlarmManager am, long triggerTime, PendingIntent pi) {
        // Ensure the timer fires even if the device is dozing.
        am.setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, triggerTime, pi);
    }

    /**
     * @param timerListener to be notified when timers are added, updated and removed
     */
    void addTimerListener(TimerListener timerListener) {
        mTimerListeners.add(timerListener);
    }

    /**
     * @param timerListener to no longer be notified when timers are added, updated and removed
     */
    void removeTimerListener(TimerListener timerListener) {
        mTimerListeners.remove(timerListener);
    }

    /**
     * @return all defined timers in their creation order
     */
    List<Timer> getTimers() {
        return getMutableTimers();
    }

    /**
     * @return all expired timers in their expiration order
     */
    List<Timer> getExpiredTimers() {
        return getMutableExpiredTimers();
    }

    /**
     * @return all missed timers in their expiration order
     */
    private List<Timer> getMissedTimers() {
        return getMutableMissedTimers();
    }

    /**
     * @param timerId identifies the timer to return
     * @return the timer with the given {@code timerId}
     */
    Timer getTimer(int timerId) {
        for (Timer timer : getMutableTimers()) {
            if (timer.getId() == timerId) {
                return timer;
            }
        }

        return null;
    }

    /**
     * @param length         the length of the timer in milliseconds
     * @param label          describes the purpose of the timer
     * @param buttonTime     the time indicated in the timer add time button
     * @param deleteAfterUse {@code true} indicates the timer should be deleted when it is reset
     * @return the newly added timer
     */
    Timer addTimer(long length, String label, String buttonTime, boolean deleteAfterUse) {
        // Create the timer instance.
        Timer timer = new Timer(-1, RESET, length, length, Timer.UNUSED, Timer.UNUSED, length,
                label, buttonTime, deleteAfterUse);

        // Add the timer to permanent storage.
        timer = TimerDAO.addTimer(mPrefs, timer);

        // Add the timer to the cache.
        getMutableTimers().add(0, timer);

        // Update the timer notification.
        updateNotification();
        // Heads-Up notification is unaffected by this change

        // Notify listeners of the change.
        for (TimerListener timerListener : mTimerListeners) {
            timerListener.timerAdded(timer);
        }
        com.feldman.clock.app.integration.AtAGlanceProvider.notifyChanged(mContext);

        return timer;
    }

    /**
     * @param service used to start foreground notifications related to expired timers
     * @param timer   the timer to be expired
     */
    void expireTimer(Service service, Timer timer) {
        if (mService == null) {
            // If this is the first expired timer, retain the service that will be used to start
            // the heads-up notification in the foreground.
            mService = service;
        } else if (mService != service) {
            // If this is not the first expired timer, the service should match the one given when
            // the first timer expired.
            Log.wtf("67","Expected TimerServices to be identical");
        }

        updateTimer(timer.expire());
    }

    /**
     * @param timer an updated timer to store
     */
    void updateTimer(Timer timer) {
        final Timer before = doUpdateTimer(timer);

        // Update the notification after updating the timer data.
        updateNotification();

        // Every timer mutation funnels through here, so this is the one place the home-screen
        // countdown needs to be repainted from.
        com.feldman.clock.ui.widgets.TimerWidgetProvider.updateAll(mContext);

        // If the timer started or stopped being expired, update the heads-up notification.
        if (before.getState() != timer.getState()) {
            if (before.isExpired() || timer.isExpired()) {
                updateHeadsUpNotification();
            }
        }
    }

    /**
     * @param timer an existing timer to be removed
     */
    void removeTimer(Timer timer) {
        mNotificationManager.cancel(mNotificationModel.getUnexpiredTimerNotificationId(timer.getId()));
        mLastNotificationContent.remove(timer.getId());
        clearPausedLiveUpdate(timer.getId());
        doRemoveTimer(timer);
        com.feldman.clock.ui.widgets.TimerWidgetProvider.updateAll(mContext);

        // Update the timer notifications after removing the timer data.
        if (timer.isExpired()) {
            updateHeadsUpNotification();
        } else {
            updateNotification();
        }
    }

    /**
     * If the given {@code timer} is expired and marked for deletion after use then this method
     * removes the the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer        the timer to be reset
     * @param allowDelete  {@code true} if the timer is allowed to be deleted instead of reset
     *                     (e.g. one use timers)
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    public void resetTimer(Timer timer, boolean allowDelete, @StringRes int eventLabelId) {
        mNotificationManager.cancel(mNotificationModel.getUnexpiredTimerNotificationId(timer.getId()));
        mLastNotificationContent.remove(timer.getId());
        clearPausedLiveUpdate(timer.getId());
        doResetOrDeleteTimer(timer, allowDelete, eventLabelId);

        // Update the notification after updating the timer data.
        if (timer.isMissed()) {
            updateMissedNotification();
        } else if (timer.isExpired()) {
            updateHeadsUpNotification();
        } else {
            updateNotification();
        }
    }

    /**
     * Update timers after system reboot.
     */
    void updateTimersAfterReboot() {
        final List<Timer> timers = new ArrayList<>(getTimers());
        for (Timer timer : timers) {
            doUpdateAfterRebootTimer(timer);
        }

        // Update the notifications once after all timers are updated.
        updateNotification();
        updateMissedNotification();
        updateHeadsUpNotification();
    }

    /**
     * Update timers after time set.
     */
    void updateTimersAfterTimeSet() {
        final List<Timer> timers = new ArrayList<>(getTimers());
        for (Timer timer : timers) {
            doUpdateAfterTimeSetTimer(timer);
        }

        // Update the notifications once after all timers are updated.
        updateNotification();
        updateMissedNotification();
        updateHeadsUpNotification();
    }

    /**
     * Reset all expired timers. Exactly one parameter should be filled, with preference given to
     * eventLabelId.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    void resetOrDeleteExpiredTimers(@StringRes int eventLabelId) {
        final List<Timer> timers = new ArrayList<>(getTimers());
        for (Timer timer : timers) {
            if (timer.isExpired()) {
                doResetOrDeleteTimer(timer, true, eventLabelId);
            }
        }

        // Update the notifications once after all timers are updated.
        updateHeadsUpNotification();
    }

    /**
     * Reset all missed timers.
     *
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    void resetMissedTimers(@StringRes int eventLabelId) {
        final List<Timer> timers = new ArrayList<>(getTimers());
        for (Timer timer : timers) {
            if (timer.isMissed()) {
                doResetOrDeleteTimer(timer, true, eventLabelId);
            }
        }

        // Update the notifications once after all timers are updated.
        updateMissedNotification();
    }

    /**
     * @return the uri of the default ringtone to play for all timers when no user selection exists
     */
    Uri getDefaultTimerRingtoneUri() {
        if (mDefaultTimerRingtoneUri == null) {
            mDefaultTimerRingtoneUri = RingtoneUtils.getResourceUri(mContext, R.raw.timer_expire);
        }

        return mDefaultTimerRingtoneUri;
    }

    /**
     * @return the uri of the ringtone to play for all timers
     */
    Uri getTimerRingtoneUri() {
        if (mTimerRingtoneUri == null) {
            mTimerRingtoneUri = SettingsDAO.getTimerRingtoneUri(mPrefs, getDefaultTimerRingtoneUri());
        }

        return mTimerRingtoneUri;
    }

    /**
     * @param uri the uri of the ringtone to play for all timers
     */
    void setTimerRingtoneUri(Uri uri) {
        SettingsDAO.setTimerRingtoneUri(mPrefs, uri);
    }

    /**
     * @return the title of the ringtone that is played for all timers
     */
    String getTimerRingtoneTitle() {
        if (mTimerRingtoneTitle == null) {
            if (RingtoneUtils.RINGTONE_SILENT.equals(getTimerRingtoneUri())) {
                // Special case: no ringtone has a title of "Silent".
                mTimerRingtoneTitle = mContext.getString(R.string.silent_ringtone_title);
            } else {
                final Uri defaultUri = getDefaultTimerRingtoneUri();
                final Uri uri = getTimerRingtoneUri();

                if (defaultUri.equals(uri)) {
                    // Special case: default ringtone has a title of "Timer Expired".
                    mTimerRingtoneTitle = mContext.getString(R.string.default_timer_ringtone_title);
                } else {
                    mTimerRingtoneTitle = mRingtoneModel.getRingtoneTitle(uri);
                }
            }
        }

        return mTimerRingtoneTitle;
    }

    /**
     * @return the duration for which a timer can ring before expiring and being reset
     */
    long getTimerAutoSilenceDuration() {
        return SettingsDAO.getTimerAutoSilenceDuration(mPrefs);
    }

    private List<Timer> getMutableTimers() {
        if (mTimers == null) {
            mTimers = TimerDAO.getTimers(mPrefs);
        }

        return mTimers;
    }

    /**
     * Load timers from SharedPreferences after a restore or reset of settings
     */
    public void loadTimers() {
        mTimers = TimerDAO.getTimers(mPrefs);
    }

    private List<Timer> getMutableExpiredTimers() {
        if (mExpiredTimers == null) {
            mExpiredTimers = new ArrayList<>();

            for (Timer timer : getMutableTimers()) {
                if (timer.isExpired()) {
                    mExpiredTimers.add(timer);
                }
            }
            Collections.sort(mExpiredTimers, Timer.createTimerStateComparator(mContext));
        }

        return mExpiredTimers;
    }

    private List<Timer> getMutableMissedTimers() {
        if (mMissedTimers == null) {
            mMissedTimers = new ArrayList<>();

            for (Timer timer : getMutableTimers()) {
                if (timer.isMissed()) {
                    mMissedTimers.add(timer);
                }
            }
            Collections.sort(mMissedTimers, Timer.createTimerStateComparator(mContext));
        }

        return mMissedTimers;
    }

    /**
     * This method updates timer data without updating notifications. This is useful in bulk-update
     * scenarios so the notifications are only rebuilt once.
     *
     * @param timer an updated timer to store
     * @return the state of the timer prior to the update
     */
    private Timer doUpdateTimer(Timer timer) {
        // Retrieve the cached form of the timer.
        final List<Timer> timers = getMutableTimers();
        final int index = timers.indexOf(timer);
        final Timer before = timers.get(index);

        // If no change occurred, ignore this update.
        if (timer == before) {
            return timer;
        }

        // Update the timer in permanent storage.
        TimerDAO.updateTimer(mPrefs, timer);

        // Update the timer in the cache.
        final Timer oldTimer = timers.set(index, timer);

        // Clear the cache of expired timers if the timer changed to/from expired.
        if (before.isExpired() || timer.isExpired()) {
            mExpiredTimers = null;
        }
        // Clear the cache of missed timers if the timer changed to/from missed.
        if (before.isMissed() || timer.isMissed()) {
            mMissedTimers = null;
        }

        // Update the timer expiration callback.
        updateAlarmManager();

        // Update the timer ringer.
        updateRinger(before, timer);

        // Notify listeners of the change.
        for (TimerListener timerListener : mTimerListeners) {
            timerListener.timerUpdated(before, timer);
        }
        com.feldman.clock.app.integration.AtAGlanceProvider.notifyChanged(mContext);

        return oldTimer;
    }

    /**
     * This method removes timer data without updating notifications. This is useful in bulk-remove
     * scenarios so the notifications are only rebuilt once.
     *
     * @param timer an existing timer to be removed
     */
    private void doRemoveTimer(Timer timer) {
        // Remove the timer from permanent storage.
        TimerDAO.removeTimer(mPrefs, timer);

        // Remove the timer from the cache.
        final List<Timer> timers = getMutableTimers();
        final int index = timers.indexOf(timer);

        // If the timer cannot be located there is nothing to remove.
        if (index == -1) {
            return;
        }

        timer = timers.remove(index);

        // Clear the cache of expired timers if a new expired timer was added.
        if (timer.isExpired()) {
            mExpiredTimers = null;
        }

        // Clear the cache of missed timers if a new missed timer was added.
        if (timer.isMissed()) {
            mMissedTimers = null;
        }

        // Update the timer expiration callback.
        updateAlarmManager();

        // Update the timer ringer.
        updateRinger(timer, null);

        // Notify listeners of the change.
        for (TimerListener timerListener : mTimerListeners) {
            timerListener.timerRemoved(timer);
        }
        com.feldman.clock.app.integration.AtAGlanceProvider.notifyChanged(mContext);
    }

    /**
     * This method updates/removes timer data without updating notifications. This is useful in
     * bulk-update scenarios so the notifications are only rebuilt once.
     * <p>
     * If the given {@code timer} is expired and marked for deletion after use then this method
     * removes the the timer. The timer is otherwise transitioned to the reset state and continues
     * to exist.
     *
     * @param timer        the timer to be reset
     * @param allowDelete  {@code true} if the timer is allowed to be deleted instead of reset
     *                     (e.g. one use timers)
     * @param eventLabelId the label of the timer event to send; 0 if no event should be sent
     */
    private void doResetOrDeleteTimer(Timer timer, boolean allowDelete, @StringRes int eventLabelId) {
        if (allowDelete && (timer.isExpired()
                || timer.isMissed()) && timer.getDeleteAfterUse()) {
            doRemoveTimer(timer);
            if (eventLabelId != 0) {
                Events.sendTimerEvent(R.string.action_delete, eventLabelId);
            }
        } else if (!timer.isReset()) {
            final Timer reset = timer.reset();
            doUpdateTimer(reset);
            if (eventLabelId != 0) {
                Events.sendTimerEvent(R.string.action_reset, eventLabelId);
            }
        }
    }

    /**
     * This method updates/removes timer data after a reboot without updating notifications.
     *
     * @param timer the timer to be updated
     */
    private void doUpdateAfterRebootTimer(Timer timer) {
        Timer updated = timer.updateAfterReboot();
        if (updated.getRemainingTime() < MISSED_THRESHOLD && updated.isRunning()) {
            updated = updated.miss();
        }
        doUpdateTimer(updated);
    }

    private void doUpdateAfterTimeSetTimer(Timer timer) {
        final Timer updated = timer.updateAfterTimeSet();
        doUpdateTimer(updated);
    }

    /**
     * Updates the callback given to this application from the {@link AlarmManager} that signals the
     * expiration of the next timer. If no timers are currently set to expire (i.e. no running
     * timers exist) then this method clears the expiration callback from AlarmManager.
     */
    private void updateAlarmManager() {
        // Locate the next firing timer if one exists.
        Timer nextExpiringTimer = null;
        for (Timer timer : getMutableTimers()) {
            if (timer.isRunning()) {
                if (nextExpiringTimer == null) {
                    nextExpiringTimer = timer;
                } else if (timer.getExpirationTime() < nextExpiringTimer.getExpirationTime()) {
                    nextExpiringTimer = timer;
                }
            }
        }

        // Build the intent that signals the timer expiration.
        final Intent intent = TimerService.createTimerExpiredIntent(mContext, nextExpiringTimer);

        if (nextExpiringTimer == null) {
            // Cancel the existing timer expiration callback.
            final PendingIntent pi = PendingIntent.getService(mContext,
                    0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pi != null) {
                mAlarmManager.cancel(pi);
                pi.cancel();
            }
        /* TODO: we can consider that issue #5 is solved as indicated in the discussion here: https://github.com/BlackyHawky/Clock/issues/5).
            Added out of curiosity to see how it will be solved in the LineageOS clock app (https://gitlab.com/LineageOS/issues/android/-/issues/5579). */
        } else if (nextExpiringTimer.getRemainingTime() <= 0) {
            mContext.startForegroundService(intent);

        } else if (nextExpiringTimer.getRemainingTime() < 5000) {
            PowerManager.WakeLock wl = AlarmAlertWakeLock.createPartialWakeLock(mContext);
            wl.acquire(nextExpiringTimer.getRemainingTime());
            new Handler(Looper.getMainLooper()).postDelayed(this::updateAlarmManager, nextExpiringTimer.getRemainingTime());
        } else {
            // Update the existing timer expiration callback.
            final PendingIntent pi = PendingIntent.getService(mContext,
                    0, intent, PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            schedulePendingIntent(mAlarmManager, nextExpiringTimer.getExpirationTime(), pi);
        }
    }

    /**
     * Starts and stops the ringer for timers if the change to the timer demands it.
     *
     * @param before the state of the timer before the change; {@code null} indicates added
     * @param after  the state of the timer after the change; {@code null} indicates delete
     */
    private void updateRinger(Timer before, Timer after) {
        // Retrieve the states before and after the change.
        final Timer.State beforeState = before == null ? null : before.getState();
        final Timer.State afterState = after == null ? null : after.getState();

        // If the timer state did not change, the ringer state is unchanged.
        if (beforeState == afterState) {
            return;
        }

        // If the timer is the first to expire, start ringing.
        if (afterState == EXPIRED && mRingingIds.add(after.getId()) && mRingingIds.size() == 1) {
            AlarmAlertWakeLock.acquireCpuWakeLock(mContext);
            TimerRinger.start(mContext, mPrefs);
            stopRingtoneAfterDelay();
        }

        // If the expired timer was the last to reset, stop ringing.
        if (beforeState == EXPIRED && mRingingIds.remove(before.getId()) && mRingingIds.isEmpty()) {
            TimerRinger.stop(mContext, mPrefs);
            TimerRinger.deactivateRingtonePlayback(mPrefs);
            AlarmAlertWakeLock.releaseCpuLock();
        }
    }

    /**
     * Stop timer ringing after a duration selected in Timers settings.
     */
    private void stopRingtoneAfterDelay() {
        Handler handler = new Handler(Looper.getMainLooper());
        long duration;

        // Timer silence has been set to "Never"
        if (getTimerAutoSilenceDuration() == TIMEOUT_NEVER) {
            return;
        }

        // Timer silence has been set to "At the end of the ringtone"
        if (getTimerAutoSilenceDuration() == TIMEOUT_END_OF_RINGTONE) {
            duration = RingtoneUtils.getRingtoneDuration(mContext, mTimerRingtoneUri);
        } else {
            duration = getTimerAutoSilenceDuration() * 1000;
        }

        handler.postDelayed(() -> {
            TimerRinger.stop(mContext, mPrefs);
            TimerRinger.deactivateRingtonePlayback(mPrefs);
            resetOrDeleteExpiredTimers(R.string.label_deskclock);
            AlarmAlertWakeLock.releaseCpuLock();
        }, duration);
    }

    /**
     * Updates the notification controlling unexpired timers. This notification is only displayed
     * when the application is not open.
     */
    void updateNotification() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d(TAG, "updateNotification: POST_NOTIFICATIONS permission not granted");
            return;
        }

        boolean areNotifsEnabled = mNotificationManager.areNotificationsEnabled();
        int channelImportance = -1;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
             android.app.NotificationChannel channel = mNotificationManager.getNotificationChannel(NotificationUtils.TIMER_MODEL_NOTIFICATION_CHANNEL_ID);
             if (channel != null) {
                 channelImportance = channel.getImportance();
             }
        }
        Log.d(TAG, "updateNotification: areNotifsEnabled=" + areNotifsEnabled + " channelImportance=" + channelImportance);

        final List<Timer> unexpired = getMutableTimers();

        // Notifications should be hidden if the app is open.
        /*if (mNotificationModel.isApplicationInForeground()) {
            for (Timer timer : unexpired) {
                mNotificationManager.cancel(mNotificationModel.getUnexpiredTimerNotificationId(timer.getId()));
                mNotificationManager.cancel(mNotificationModel.getSummaryNotificationId());
            }
            return;
        }*/

        // Identify the timer to use for the foreground service (priority: running, then paused)
        Timer foregroundTimer = null;
        for (Timer timer : unexpired) {
            if (timer.isRunning()) {
                foregroundTimer = timer;
                break;
            }
        }
        if (foregroundTimer == null) {
            for (Timer timer : unexpired) {
                if (timer.isPaused()) {
                    foregroundTimer = timer;
                    break;
                }
            }
        }

        // Create notifications for each unexpired timers
        for (Timer timer : unexpired) {
            int notificationId = mNotificationModel.getUnexpiredTimerNotificationId(timer.getId());

            final long pausedLiveUpdateTimeoutMillis = timer.isPaused()
                    ? getPausedLiveUpdateTimeoutMillis(timer.getId()) : 0L;
            if (timer.isRunning() || pausedLiveUpdateTimeoutMillis > 0L) {
                // Skip if user dismissed this notification
                if (mDismissedTimerNotifications.contains(timer.getId())) {
                    Log.d(TAG, "updateNotification: Skipping timer " + timer.getId() + " - notification dismissed by user");
                    continue;
                }
                
                Notification notification = mNotificationBuilder.build(
                        mContext, mNotificationModel, timer, null, pausedLiveUpdateTimeoutMillis);
                
                Log.d(TAG, "updateNotification: Built notification for timer " + timer.getId() + 
                    " channel=" + notification.getChannelId() + 
                    " smallIcon=" + notification.icon + 
                    " when=" + notification.when + 
                    " flags=" + notification.flags + 
                    " priority=" + notification.priority);
                
                // Check if content actually changed before updating
                String contentKey = getNotificationContentKey(timer);
                String lastContent = mLastNotificationContent.get(timer.getId());
                
                if (!contentKey.equals(lastContent)) {
                    // Content changed - update notification
                    Log.d(TAG, "updateNotification: Content changed, updating notification for timer " + timer.getId());
                    mNotificationManager.notify(notificationId, notification);
                    mLastNotificationContent.put(timer.getId(), contentKey);
                    
                    // DON'T use startForeground for running timers - SHORT_SERVICE has 3-minute timeout
                    // Running timers can last indefinitely, so we just use notify() and rely on
                    // the notification + Handler to keep things running
                    
                    // Handler provides smooth 1-second updates
                    // No need to do anything here - updates continue via Handler loop
                } else {
                    Log.d(TAG, "updateNotification: No change in content for timer " + timer.getId() + ", skipping update");
                }
                
            } else if (timer.isPaused()) {
                // Do not create a live update for a timer that was already paused.
                mNotificationManager.cancel(notificationId);
                mLastNotificationContent.remove(timer.getId());
                Log.d(TAG, "updateNotification: Cancelled notification for paused timer " + timer.getId());
            } else {
                mNotificationManager.cancel(notificationId);
            }
        }

        // Start or stop Handler updates based on running timers
        if (hasRunningTimer()) {
            // Ensure foreground service is running for chronometer updates
            ensureForegroundService();
        } else if (hasPausedLiveUpdate()) {
            schedulePausedLiveUpdateExpiry();
        } else {
            // No running timers, stop all updates
            stopNotificationUpdates();
        }

        // Always cancel summary notification as we are not using it
        mNotificationManager.cancel(mNotificationModel.getSummaryNotificationId());
    }

    /**
     * Updates the notification controlling missed timers. This notification is only displayed when
     * the application is not open.
     */
    void updateMissedNotification() {
        if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            // Always false, because notification activation is always checked when the application is started.
            return;
        }

        // Notifications should be hidden if the app is open.
        if (mNotificationModel.isApplicationInForeground()) {
            mNotificationManager.cancel(mNotificationModel.getMissedTimerNotificationId());
            return;
        }

        final List<Timer> missed = getMissedTimers();

        if (missed.isEmpty()) {
            mNotificationManager.cancel(mNotificationModel.getMissedTimerNotificationId());
            return;
        }

        final Notification notification = mNotificationBuilder.buildMissed(mContext,
                mNotificationModel, missed);
        final int notificationId = mNotificationModel.getMissedTimerNotificationId();

        mNotificationManager.notify(notificationId, notification);
    }

    /**
     * Updates the heads-up notification controlling expired timers. This heads-up notification is
     * displayed whether the application is open or not.
     */
    private void updateHeadsUpNotification() {
        // Nothing can be done with the heads-up notification without a valid service reference.
        if (mService == null) {
            return;
        }

        final List<Timer> expired = getExpiredTimers();

        // If no expired timers exist, stop the service if no active timers exist either.
        if (expired.isEmpty()) {
            boolean hasActiveTimers = false;
            for (Timer timer : getMutableTimers()) {
                if (timer.isRunning() || timer.isPaused()) {
                    hasActiveTimers = true;
                    break;
                }
            }
            if (!hasActiveTimers) {
                mService.stopSelf();
                mService = null;
                mIsForeground = false;
            }
            return;
        }

        // Otherwise build and post a foreground notification reflecting the latest expired timers.
        final Notification notification = mNotificationBuilder.buildHeadsUp(mContext, expired);
        final int notificationId = mNotificationModel.getExpiredTimerNotificationId();
        mService.startForeground(notificationId, notification);
    }

    /**
     * Update the timer notification in response to a locale change.
     */
    private final class LocaleChangedReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            mTimerRingtoneTitle = null;
            updateNotification();
            updateMissedNotification();
            updateHeadsUpNotification();
        }
    }

    /**
     * This receiver is notified when shared preferences change. Cached information built on
     * preferences must be cleared.
     */
    private final class PreferenceListener implements OnSharedPreferenceChangeListener {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
            if (KEY_TIMER_RINGTONE.equals(key)) {
                mTimerRingtoneUri = null;
                mTimerRingtoneTitle = null;
            }
        }
    }
}

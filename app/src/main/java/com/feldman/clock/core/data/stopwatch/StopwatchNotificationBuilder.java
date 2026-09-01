/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static com.feldman.clock.core.util.NotificationUtils.STOPWATCH_NOTIFICATION_CHANNEL_ID;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationCompat.Action;
import androidx.core.app.NotificationCompat.Builder;
import com.feldman.clock.R;
import com.feldman.clock.app.MainActivity;
import com.feldman.clock.core.analytics.Events;
import com.feldman.clock.stopwatch.StopwatchService;
import com.feldman.clock.core.util.NotificationUtils;
import com.feldman.clock.core.util.Utils;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds notification to reflect the latest state of the stopwatch and recorded laps.
 */
class StopwatchNotificationBuilder {

    public Notification build(Context context, NotificationModel nm, Stopwatch stopwatch,
            long pausedLiveUpdateTimeoutMillis) {
        @StringRes final int eventLabel = R.string.label_notification;

        // Intent to load the app when the notification is tapped.
        final Intent showApp = new Intent(context, MainActivity.class)
                .setAction(StopwatchService.ACTION_SHOW_STOPWATCH)
                .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

        final PendingIntent pendingShowApp = Utils.pendingActivityIntent(context, showApp);

        // Compute some values required below.
        final boolean running = stopwatch.isRunning();
        final boolean paused = stopwatch.isPaused() && pausedLiveUpdateTimeoutMillis > 0;
        final boolean showLiveUpdate = Build.VERSION.SDK_INT >= 37 && (running || paused);
        // Chronometer rounds to the nearest second. Shift the base by half a second so its
        // displayed seconds use the stopwatch's floor-to-second rule.
        final long stopwatchStartTime = SystemClock.elapsedRealtime() - stopwatch.getTotalTime() + 500L;

        final List<Action> actions = new ArrayList<>(2);
        CharSequence contentText = null;

        if (running) {
            // Left button: Pause
            final Intent pause = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_PAUSE_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            @DrawableRes final int icon1 = R.drawable.ic_fab_pause;
            final CharSequence title1 = context.getText(R.string.sw_pause_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, pause);
            actions.add(new Action.Builder(icon1, title1, intent1).build());

            // Right button: Add Lap
            if (DataModel.getDataModel().canAddMoreLaps()) {
                final Intent lap = new Intent(context, StopwatchService.class)
                        .setAction(StopwatchService.ACTION_LAP_STOPWATCH)
                        .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

                @DrawableRes final int icon2 = R.drawable.ic_stopwatch_lap;
                final CharSequence title2 = context.getText(R.string.sw_lap_button);
                final PendingIntent intent2 = Utils.pendingServiceIntent(context, lap);
                actions.add(new Action.Builder(icon2, title2, intent2).build());
            }

            // Show the current lap number if any laps have been recorded.
            final int lapCount = DataModel.getDataModel().getLaps().size();
            if (lapCount > 0) {
                final int lapNumber = lapCount + 1;
                contentText = context.getString(R.string.sw_notification_lap_number, lapNumber);
            }
        } else {
            // Left button: Start
            final Intent start = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_START_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            @DrawableRes final int icon1 = R.drawable.ic_fab_play;
            final CharSequence title1 = context.getText(R.string.sw_start_button);
            final PendingIntent intent1 = Utils.pendingServiceIntent(context, start);
            actions.add(new Action.Builder(icon1, title1, intent1).build());

            // Right button: Reset (dismisses notification and resets stopwatch)
            final Intent reset = new Intent(context, StopwatchService.class)
                    .setAction(StopwatchService.ACTION_RESET_STOPWATCH)
                    .putExtra(Events.EXTRA_EVENT_LABEL, eventLabel);

            @DrawableRes final int icon2 = R.drawable.ic_reset;
            final CharSequence title2 = context.getText(R.string.reset);
            final PendingIntent intent2 = Utils.pendingServiceIntent(context, reset);
            actions.add(new Action.Builder(icon2, title2, intent2).build());

            contentText = context.getString(R.string.swn_paused);
        }

        final Builder notification = new Builder(context, STOPWATCH_NOTIFICATION_CHANNEL_ID)
                .setLocalOnly(true)
                .setOngoing(running || showLiveUpdate)
                .setContentTitle(context.getString(R.string.stopwatch_channel))
                .setContentText(contentText)
                .setContentIntent(pendingShowApp)
                .setAutoCancel(false)
                .setPriority(NotificationManager.IMPORTANCE_LOW)
                .setSmallIcon(R.drawable.ic_tab_stopwatch_static)
                .setColor(context.getColor(R.color.md_theme_primary))
                .setGroup(nm.getStopwatchNotificationGroupKey());

        if (showLiveUpdate) {
            final NotificationCompat.Metric.TimeDifference timeDifference = running
                    ? NotificationCompat.Metric.TimeDifference.forStopwatch(
                            stopwatchStartTime,
                            NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER)
                    : NotificationCompat.Metric.TimeDifference.forPausedStopwatch(
                            Duration.ofMillis(Math.max(0L, stopwatch.getTotalTime() - 500L)),
                            NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER);
            notification
                    .setShowWhen(false)
                    .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                    .setStyle(new NotificationCompat.MetricStyle()
                            .addMetric(new NotificationCompat.Metric(
                                    timeDifference,
                                    context.getString(R.string.stopwatch_channel))))
                    .setRequestPromotedOngoing(true);
            if (paused) {
                notification.setTimeoutAfter(pausedLiveUpdateTimeoutMillis);
            }
        } else if (running) {
            final RemoteViews content = new RemoteViews(
                    context.getPackageName(), R.layout.chronometer_notif_content);
            content.setTextViewText(R.id.title, context.getString(R.string.stopwatch_channel));
            content.setChronometer(R.id.chronometer, stopwatchStartTime, null, true);
            if (contentText == null) {
                content.setViewVisibility(R.id.state, GONE);
            } else {
                content.setTextViewText(R.id.state, contentText);
                content.setViewVisibility(R.id.state, VISIBLE);
            }
            notification
                    .setCustomContentView(content)
                    .setStyle(new NotificationCompat.DecoratedCustomViewStyle());
        } else {
            notification.setShowWhen(false);
            if (paused) {
                notification.setTimeoutAfter(pausedLiveUpdateTimeoutMillis);
            }
        }

        for (Action action : actions) {
            notification.addAction(action);
        }

        NotificationUtils.createChannel(context, STOPWATCH_NOTIFICATION_CHANNEL_ID);

        return notification.build();
    }
}

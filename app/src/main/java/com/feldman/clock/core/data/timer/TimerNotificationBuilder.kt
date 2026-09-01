/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.data;

import com.feldman.clock.R

import com.feldman.clock.app.MainActivity

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import com.feldman.clock.core.analytics.Events
import com.feldman.clock.timer.TimerConstants
import com.feldman.clock.timer.TimerService
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.core.util.NotificationUtils
import com.feldman.clock.core.util.Utils
import java.time.Duration

/**
 * Builds notifications to reflect the latest state of the timers.
 */
internal class TimerNotificationBuilder {

    /**
     * @param timer the timer on which to base the chronometer display
     * @return the elapsed-realtime time at which the timer will/did reach 0:00
     */
    private fun getChronometerBase(timer: Timer): Long {
        // The in-app timer display rounds *up* to the next second for positive timer values. Mirror
        // that behavior in the notification's Chronometer by padding in an extra second as needed.
        val remaining = timer.remainingTime
        val adjustedRemaining = if (remaining < 0) remaining else remaining + android.text.format.DateUtils.SECOND_IN_MILLIS

        return SystemClock.elapsedRealtime() + adjustedRemaining
    }

    fun build(
        context: Context,
        nm: NotificationModel,
        timer: Timer,
        groupKey: String?,
        pausedLiveUpdateTimeoutMillis: Long
    ): Notification {
        Log.d("DeskClockTimerDebug", "Builder.build: timerId=" + timer.id + " running=" + timer.isRunning + " label=" + timer.label)
        // Compute some values required below.
        val running = timer.isRunning
        val paused = timer.isPaused && pausedLiveUpdateTimeoutMillis > 0
        val showLiveUpdate = Build.VERSION.SDK_INT >= 37 && (running || paused)
        val base = getChronometerBase(timer)
        val actions = ArrayList<NotificationCompat.Action>(2)

        val timerLabel: CharSequence = timer.label.ifEmpty { context.getString(R.string.timer_notification_label) }
        val timerId = timer.id


        if (running) {
            // Left button: Pause
            val pause = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_PAUSE_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timerId)

            @DrawableRes val icon1 = R.drawable.ic_fab_pause
            val title1 = context.getText(R.string.timer_pause)
            val intent1 = Utils.pendingServiceIntent(context, pause, timerId)
            actions.add(NotificationCompat.Action.Builder(icon1, title1, intent1).build())

            // Right Button: +x Minutes
            val addMinute = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_ADD_CUSTOM_TIME_TO_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timerId)

            @DrawableRes val icon2 = R.drawable.ic_add
            val customTimeToAdd = Integer.parseInt(timer.buttonTime)
            val minutesToAdd = customTimeToAdd / 60
            val secondsToAdd = customTimeToAdd % 60

            val title2 = if (secondsToAdd == 0) {
                context.getString(
                    R.string.timer_add_custom_time_for_notification,
                    minutesToAdd.toString()
                )
            } else {
                context.getString(
                    R.string.timer_add_custom_time_with_seconds_for_notification,
                    minutesToAdd.toString(),
                    secondsToAdd.toString()
                )
            }

            val intent2 = Utils.pendingServiceIntent(context, addMinute, timerId)
            actions.add(NotificationCompat.Action.Builder(icon2, title2, intent2).build())
        } else {
            // Timer is paused.
            // Left button: Start
            val start = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_START_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timerId)

            @DrawableRes val icon1 = R.drawable.ic_fab_play
            val title1 = context.getText(R.string.sw_resume_button)
            val intent1 = Utils.pendingServiceIntent(context, start, timerId)
            actions.add(NotificationCompat.Action.Builder(icon1, title1, intent1).build())

            // Right Button: Reset
            val reset = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_RESET_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timerId)

            @DrawableRes val icon2 = R.drawable.ic_reset
            val title2 = context.getText(R.string.reset)
            val intent2 = Utils.pendingServiceIntent(context, reset, timerId)
            actions.add(NotificationCompat.Action.Builder(icon2, title2, intent2).build())
        }

        // Intent to load the app and show the timer when the notification is tapped.
        val showApp = Intent(context, MainActivity::class.java)
            .setAction(TimerService.ACTION_SHOW_TIMER)
            .putExtra(TimerService.EXTRA_TIMER_ID, timerId)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_notification)

        val pendingShowApp = Utils.pendingActivityIntent(context, showApp)

        // Format time as hh:mm:ss, mm:ss, or ss depending on duration
        val totalSeconds = timer.remainingTime / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        
        val timeFormatted = when {
            hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format("%d:%02d", minutes, seconds)
            else -> String.format("%d", seconds)
        }
        
        val contentText = if (timer.isPaused) "Paused - $timeFormatted" else null
        
        // Create delete intent to track user dismissal
        val deleteIntent = Intent(context, TimerService::class.java).apply {
            action = TimerService.ACTION_DISMISS_NOTIFICATION
            putExtra(TimerService.EXTRA_TIMER_ID, timer.id)
        }
        val deletePendingIntent = Utils.pendingServiceIntent(context, deleteIntent, timer.id)
        
        val builder = NotificationCompat.Builder(context, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hourglass_bottom)
            .setContentTitle(timerLabel)
            .setContentText(contentText)
            .setContentIntent(pendingShowApp)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(running || showLiveUpdate)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setDeleteIntent(deletePendingIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(context.getColor(if (running) R.color.md_theme_primary else R.color.md_theme_secondary))
            
        if (showLiveUpdate) {
            val timeDifference = if (running) {
                NotificationCompat.Metric.TimeDifference.forTimer(
                    base,
                    NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER
                )
            } else {
                NotificationCompat.Metric.TimeDifference.forPausedTimer(
                    Duration.ofMillis(timer.remainingTime.coerceAtLeast(0L)),
                    NotificationCompat.Metric.TimeDifference.FORMAT_CHRONOMETER
                )
            }
            builder
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
                .setStyle(
                    NotificationCompat.MetricStyle()
                        .addMetric(
                            NotificationCompat.Metric(
                                timeDifference,
                                context.getString(R.string.timer_notification_label)
                            )
                        )
                )
                .setRequestPromotedOngoing(true)
            if (paused) {
                builder.setTimeoutAfter(pausedLiveUpdateTimeoutMillis)
            }
        } else if (running) {
            val remoteViews = RemoteViews(context.packageName, R.layout.chronometer_notification_content)
            remoteViews.setTextViewText(R.id.notification_title, timerLabel)
            remoteViews.setChronometer(R.id.notification_chronometer, base, null, true)
            remoteViews.setChronometerCountDown(R.id.notification_chronometer, true)
            builder
                .setCustomContentView(remoteViews)
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
        } else {
            builder.setShowWhen(false)
            builder.setTimeoutAfter(5000)
        }
        
        for (action in actions) {
            builder.addAction(action)
        }

        NotificationUtils.createChannel(context, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID)

        return builder.build()
    }

    fun buildHeadsUp(context: Context, expired: List<Timer>): Notification {
        val timer = expired[0]
        val timerId = timer.id

        // First action intent is to reset all timers.
        @DrawableRes val icon1 = R.drawable.ic_fab_stop
        val reset = TimerService.createResetExpiredTimersIntent(context)
        val intent1 = Utils.pendingServiceIntent(context, reset)

        val titleText: CharSequence
        val label = timer.label

        val count = expired.size
        val actions = ArrayList<NotificationCompat.Action>(2)

        if (count == 1) {
            if (TextUtils.isEmpty(label)) {
                titleText = context.getString(R.string.timer_notification_label)
            } else {
                titleText = label!!
            }

            // Left button: Reset single timer
            val title1 = context.getString(R.string.timer_stop)
            actions.add(NotificationCompat.Action.Builder(icon1, title1, intent1).build())

            // Right Button: +x Minutes
            val addTime = TimerService.createAddCustomTimeToTimerIntent(context, timerId)
            val intent2 = Utils.pendingServiceIntent(context, addTime, timerId)
            @DrawableRes val icon2 = R.drawable.ic_add
            val customTimeToAdd = Integer.parseInt(timer.buttonTime)
            val minutesToAdd = customTimeToAdd / 60
            val secondsToAdd = customTimeToAdd % 60

            val title2 = if (secondsToAdd == 0) {
                context.getString(
                    R.string.timer_add_custom_time_for_notification,
                    minutesToAdd.toString()
                )
            } else {
                context.getString(
                    R.string.timer_add_custom_time_with_seconds_for_notification,
                    minutesToAdd.toString(),
                    secondsToAdd.toString()
                )
            }

            actions.add(NotificationCompat.Action.Builder(icon2, title2, intent2).build())
        } else {
            titleText = context.getString(R.string.timer_multi_times_up, count)

            // Left button: Reset all timers
            val title1 = context.getString(R.string.timer_stop_all)
            actions.add(NotificationCompat.Action.Builder(icon1, title1, intent1).build())
        }

        val base = getChronometerBase(timer)

        // Content intent shows the timer full screen when clicked.
        val content = Intent(context, MainActivity::class.java)
        val contentIntent = Utils.pendingActivityIntent(context, content)

        // Full screen intent
        val fullScreen = Intent(context, MainActivity::class.java)
            .setAction(TimerConstants.ACTION_SHOW_EXPIRED_TIMERS)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
        val pendingFullScreen = Utils.pendingActivityIntent(context, fullScreen)

        val builder = NotificationCompat.Builder(context, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID)
            .setOngoing(true)
            .setLocalOnly(true)
            .setShowWhen(true)
            .setAutoCancel(false)
            .setContentIntent(contentIntent)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setDefaults(Notification.DEFAULT_LIGHTS)
            .setSmallIcon(R.drawable.ic_hourglass_bottom)
            .setFullScreenIntent(pendingFullScreen, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setColor(context.getColor(R.color.md_theme_primary))

        for (action in actions) {
            builder.addAction(action)
        }
        
        builder.setContentTitle(titleText)
        builder.setContentText(context.getString(R.string.timer_times_up))

        NotificationUtils.createChannel(context, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID)

        // Stop and reset the timer if user clears notification.
        val dismissIntent = Intent(context, TimerService::class.java)
        dismissIntent.setAction(TimerService.ACTION_RESET_EXPIRED_TIMERS)
        dismissIntent.putExtra(TimerService.EXTRA_TIMER_ID, timerId)
        val deletePendingIntent = Utils.pendingServiceIntent(context, dismissIntent, timerId)
        builder.setDeleteIntent(deletePendingIntent)

        return builder.build()
    }

    fun buildMissed(context: Context, nm: NotificationModel, missedTimers: List<Timer>): Notification {
        val timer = missedTimers[0]
        val count = missedTimers.size
        val timerId = timer.id

        val base = getChronometerBase(timer)
        val res = context.resources

        val action: NotificationCompat.Action
        val titleText: CharSequence
        val label = timer.label

        if (count == 1) {
            // Single timer is missed.
            if (TextUtils.isEmpty(label)) {
                titleText = context.getString(R.string.timer_notification_label)
            } else {
                titleText = label!!
            }

            // Reset button
            val reset = Intent(context, TimerService::class.java)
                .setAction(TimerService.ACTION_RESET_TIMER)
                .putExtra(TimerService.EXTRA_TIMER_ID, timerId)

            @DrawableRes val icon1 = R.drawable.ic_reset
            val title1 = res.getText(R.string.reset)
            val intent1 = Utils.pendingServiceIntent(context, reset)
            action = NotificationCompat.Action.Builder(icon1, title1, intent1).build()
        } else {
            // Multiple missed timers.
            titleText = res.getString(R.string.timer_multi_missed, count)

            val reset = TimerService.createResetMissedTimersIntent(context)

            @DrawableRes val icon1 = R.drawable.ic_reset
            val title1 = res.getText(R.string.timer_reset_all)
            val intent1 = Utils.pendingServiceIntent(context, reset)
            action = NotificationCompat.Action.Builder(icon1, title1, intent1).build()
        }

        // Intent to load the app and show the timer when the notification is tapped.
        val showApp = Intent(context, MainActivity::class.java)
            .setAction(TimerService.ACTION_SHOW_TIMER)
            .putExtra(TimerService.EXTRA_TIMER_ID, timerId)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_notification)

        val pendingShowApp = Utils.pendingActivityIntent(context, showApp)

        val builder = NotificationCompat.Builder(context, NotificationUtils.TIMER_MODEL_NOTIFICATION_CHANNEL_ID)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setAutoCancel(false)
            .setContentIntent(pendingShowApp)
            .setPriority(NotificationManager.IMPORTANCE_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSmallIcon(R.drawable.ic_hourglass_bottom)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setSortKey(nm.getTimerNotificationMissedSortKey())
            .addAction(action)
            .setColor(context.getColor(R.color.md_theme_primary))
            .setContentTitle(titleText)
            .setContentText(AlarmUtils.getFormattedTime(context, timer.wallClockExpirationTime))

        NotificationUtils.createChannel(context, NotificationUtils.TIMER_MODEL_NOTIFICATION_CHANNEL_ID)

        return builder.build()
    }

    fun buildSummaryNotification(context: Context, nm: NotificationModel): Notification {
        // Intent to load the app and show the timer when the notification is tapped.
        val showApp = Intent(context, MainActivity::class.java)
            .setAction(TimerService.ACTION_SHOW_TIMER)
            .putExtra(TimerService.EXTRA_TIMER_ID, -1)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_notification)

        val pendingShowApp = Utils.pendingActivityIntent(context, showApp)

        NotificationUtils.createChannel(context, NotificationUtils.TIMER_MODEL_NOTIFICATION_CHANNEL_ID)

        return NotificationCompat.Builder(context, NotificationUtils.TIMER_MODEL_NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_hourglass_bottom)
            .setGroup(nm.timerNotificationGroupKey)
            .setGroupSummary(true)
            .setOngoing(true)
            .setContentIntent(pendingShowApp)
            .setPriority(NotificationManager.IMPORTANCE_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setContentTitle(context.getString(R.string.timer_notification_label))
            .setColor(context.getColor(R.color.md_theme_primary))
            .build()
    }
}

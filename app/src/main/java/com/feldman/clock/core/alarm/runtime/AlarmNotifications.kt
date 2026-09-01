/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.alarm

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication

import com.feldman.clock.app.MainActivity

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.feldman.clock.core.storage.provider.ClockContract.InstancesColumns
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.core.util.NotificationUtils
import java.text.SimpleDateFormat
import java.util.Locale

object AlarmNotifications {
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    /**
     * Formats times such that chronological order and lexicographical order agree.
     */
    private val SORT_KEY_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * This value is coordinated with group ids from
     * {link com.feldman.clock.core.data.NotificationModel}
     */
    private const val UPCOMING_GROUP_KEY = "1"

    /**
     * This value is coordinated with group ids from
     * {link com.feldman.clock.core.data.NotificationModel}
     */
    private const val MISSED_GROUP_KEY = "4"

    /**
     * This value is coordinated with notification ids from
     * {link com.feldman.clock.core.data.NotificationModel}
     */
    private const val ALARM_GROUP_NOTIFICATION_ID = Int.MAX_VALUE - 4

    /**
     * This value is coordinated with notification ids from
     * {link com.feldman.clock.core.data.NotificationModel}
     */
    private const val ALARM_GROUP_MISSED_NOTIFICATION_ID = Int.MAX_VALUE - 5

    /**
     * This value is coordinated with notification ids from
     * {link com.feldman.clock.core.data.NotificationModel}
     */
    private const val ALARM_FIRING_NOTIFICATION_ID = Int.MAX_VALUE - 7

    @JvmStatic
    @Synchronized
    fun showUpcomingNotification(context: Context, instance: AlarmInstance) {
        val prefs = com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(
                PreferencesKeys.KEY_SHOW_UPCOMING_ALARM_NOTIFICATION,
                PreferencesDefaultValues.DEFAULT_SHOW_UPCOMING_ALARM_NOTIFICATION
            )
        ) {
            return
        }

        val alarm = Alarm.getAlarm(context.contentResolver, instance.mAlarmId!!) ?: return

        val contentTitle = if (!alarm.daysOfWeek.isRepeating()) {
            if (alarm.deleteAfterUse) {
                context.getString(R.string.occasional_alarm_alert_predismiss_title)
            } else {
                context.getString(R.string.alarm_alert_predismiss_title)
            }
        } else {
            context.getString(R.string.alarm_alert_predismiss_title)
        }

        val builder = NotificationCompat.Builder(
            context, NotificationUtils.ALARM_UPCOMING_NOTIFICATION_CHANNEL_ID
        )
            .setShowWhen(false)
            .setContentTitle(contentTitle)
            .setContentText(AlarmUtils.getAlarmText(context, instance, true))
            .setColor(context.getColor(R.color.md_theme_primary))
            .setSmallIcon(R.drawable.ic_tab_alarm_static)
            .setAutoCancel(false)
            .setSortKey(createSortKey(instance))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setOngoing(true)
            .setGroup(UPCOMING_GROUP_KEY)

        // Setup up dismiss action
        val id = instance.hashCode()
        val dismissActionTitle = if (!alarm.daysOfWeek.isRepeating() && alarm.deleteAfterUse) {
            context.getString(R.string.alarm_alert_dismiss_and_delete_text)
        } else {
            context.getString(R.string.alarm_alert_dismiss_text)
        }

        val dismissIntent = AlarmStateManager.createStateChangeIntent(
            context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.PREDISMISSED_STATE
        )
        builder.addAction(
            R.drawable.ic_alarm_off, dismissActionTitle,
            PendingIntent.getBroadcast(
                context, id, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT
                        or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Setup content action if instance is owned by alarm
        val viewAlarmIntent = createViewAlarmIntent(context, instance)
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, id,
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val nm = NotificationManagerCompat.from(context)
        NotificationUtils.createChannel(
            context,
            NotificationUtils.ALARM_UPCOMING_NOTIFICATION_CHANNEL_ID
        )

        val notification = builder.build()


        nm.notify(id, notification)
        updateUpcomingAlarmGroupNotification(context, -1, notification)
    }

    private fun isGroupSummary(n: Notification): Boolean {
        return (n.flags and Notification.FLAG_GROUP_SUMMARY) == Notification.FLAG_GROUP_SUMMARY
    }

    /**
     * Method which returns the first active notification for a given group. If a notification was
     * just posted, provide it to make sure it is included as a potential result. If a notification
     * was just canceled, provide the id so that it is not included as a potential result. These
     * extra parameters are needed due to a race condition which exists in
     * [NotificationManager.getActiveNotifications].
     *
     * @param context                Context from which to grab the NotificationManager
     * @param group                  The group key to query for notifications
     * @param canceledNotificationId The id of the just-canceled notification (-1 if none)
     * @param postedNotification     The notification that was just posted
     * @return The first active notification for the group
     */
    private fun getFirstActiveNotification(
        context: Context, group: String,
        canceledNotificationId: Int, postedNotification: Notification?
    ): Notification? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications
        var firstActiveNotification = postedNotification
        for (statusBarNotification in notifications) {
            val n = statusBarNotification.notification
            if (!isGroupSummary(n) && group == n.group && statusBarNotification.id != canceledNotificationId) {
                if (firstActiveNotification == null || n.sortKey.compareTo(firstActiveNotification.sortKey) < 0) {
                    firstActiveNotification = n
                }
            }
        }
        return firstActiveNotification
    }

    private fun getActiveGroupSummaryNotification(context: Context, group: String): Notification? {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notifications = nm.activeNotifications
        for (statusBarNotification in notifications) {
            val n = statusBarNotification.notification
            if (isGroupSummary(n) && group == n.group) {
                return n
            }
        }
        return null
    }

    private fun updateUpcomingAlarmGroupNotification(
        context: Context, canceledNotificationId: Int,
        postedNotification: Notification?
    ) {
        val nm = NotificationManagerCompat.from(context)
        val firstUpcoming = getFirstActiveNotification(
            context, UPCOMING_GROUP_KEY,
            canceledNotificationId, postedNotification
        )
        if (firstUpcoming == null) {
            nm.cancel(ALARM_GROUP_NOTIFICATION_ID)
            return
        }

        val summary = getActiveGroupSummaryNotification(context, UPCOMING_GROUP_KEY)
        if (summary == null || summary.contentIntent != firstUpcoming.contentIntent) {
            NotificationUtils.createChannel(
                context,
                NotificationUtils.ALARM_UPCOMING_NOTIFICATION_CHANNEL_ID
            )

            val updatedSummary = NotificationCompat.Builder(
                context,
                NotificationUtils.ALARM_UPCOMING_NOTIFICATION_CHANNEL_ID
            )
                .setShowWhen(false)
                .setContentIntent(firstUpcoming.contentIntent)
                .setColor(context.getColor(R.color.md_theme_primary))
                .setSmallIcon(R.drawable.ic_tab_alarm_static)
                .setGroup(UPCOMING_GROUP_KEY)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .build()


            nm.notify(ALARM_GROUP_NOTIFICATION_ID, updatedSummary)
        }
    }

    private fun updateMissedAlarmGroupNotification(
        context: Context, canceledNotificationId: Int,
        postedNotification: Notification?
    ) {
        val nm = NotificationManagerCompat.from(context)
        val firstMissed = getFirstActiveNotification(
            context, MISSED_GROUP_KEY,
            canceledNotificationId, postedNotification
        )
        if (firstMissed == null) {
            nm.cancel(ALARM_GROUP_MISSED_NOTIFICATION_ID)
            return
        }

        val summary = getActiveGroupSummaryNotification(context, MISSED_GROUP_KEY)
        if (summary == null || summary.contentIntent != firstMissed.contentIntent) {
            NotificationUtils.createChannel(
                context,
                NotificationUtils.ALARM_MISSED_NOTIFICATION_CHANNEL_ID
            )

            val updatedSummary = NotificationCompat.Builder(
                context,
                NotificationUtils.ALARM_MISSED_NOTIFICATION_CHANNEL_ID
            )
                .setShowWhen(false)
                .setContentIntent(firstMissed.contentIntent)
                .setColor(context.getColor(R.color.md_theme_primary))
                .setSmallIcon(R.drawable.ic_tab_alarm_static)
                .setGroup(MISSED_GROUP_KEY)
                .setGroupSummary(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_EVENT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setLocalOnly(true)
                .build()


            nm.notify(ALARM_GROUP_MISSED_NOTIFICATION_ID, updatedSummary)
        }
    }

    @JvmStatic
    @Synchronized
    fun showSnoozeNotification(context: Context, instance: AlarmInstance) {
        val builder = NotificationCompat.Builder(
            context, NotificationUtils.ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID
        )
            .setShowWhen(false)
            .setContentTitle(instance.getLabelOrDefault(context))
            .setContentText(
                context.getString(
                    R.string.alarm_alert_snooze_until,
                    AlarmUtils.getFormattedTime(context, instance.alarmTime)
                )
            )
            .setColor(context.getColor(R.color.md_theme_primary))
            .setSmallIcon(R.drawable.ic_tab_alarm_static)
            .setAutoCancel(false)
            .setSortKey(createSortKey(instance))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setGroup(UPCOMING_GROUP_KEY)

        // Setup up dismiss action
        val dismissIntent = AlarmStateManager.createStateChangeIntent(
            context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE
        )

        val id = instance.hashCode()

        builder.addAction(
            R.drawable.ic_alarm_off,
            context.getString(R.string.alarm_alert_dismiss_text),
            // The state-change intent targets AlarmReceiver (a broadcast receiver);
            // getService silently matched nothing, leaving the button dead.
            PendingIntent.getBroadcast(
                context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Setup content action if instance is owned by alarm
        val viewAlarmIntent = createViewAlarmIntent(context, instance)
        builder.setContentIntent(
            PendingIntent.getActivity(
                context, id,
                viewAlarmIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val nm = NotificationManagerCompat.from(context)
        NotificationUtils.createChannel(
            context,
            NotificationUtils.ALARM_SNOOZE_NOTIFICATION_CHANNEL_ID
        )

        val notification = builder.build()


        nm.notify(id, notification)
        updateUpcomingAlarmGroupNotification(context, -1, notification)
    }

    @JvmStatic
    @SuppressLint("LaunchActivityFromNotification")
    @Synchronized
    fun showMissedNotification(context: Context, instance: AlarmInstance) {
        val prefs = com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences(context)
        if (!prefs.getBoolean(
                PreferencesKeys.KEY_SHOW_MISSED_ALARM_NOTIFICATION,
                PreferencesDefaultValues.DEFAULT_SHOW_MISSED_ALARM_NOTIFICATION
            )
        ) {
            return
        }
        val label = instance.mLabel
        val alarmTime = AlarmUtils.getFormattedTime(context, instance.alarmTime)
        val builder = NotificationCompat.Builder(
            context, NotificationUtils.ALARM_MISSED_NOTIFICATION_CHANNEL_ID
        )
            .setShowWhen(false)
            .setContentTitle(context.getString(R.string.alarm_missed_title))
            .setContentText(
                if (instance.mLabel!!.isEmpty()) alarmTime else context.getString(
                    R.string.alarm_missed_text,
                    alarmTime,
                    label
                )
            )
            .setColor(context.getColor(R.color.md_theme_primary))
            .setSortKey(createSortKey(instance))
            .setSmallIcon(R.drawable.ic_tab_alarm_static)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)
            .setGroup(MISSED_GROUP_KEY)

        val id = instance.hashCode()

        // Setup dismiss intent
        val dismissIntent = AlarmStateManager.createStateChangeIntent(
            context,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE
        )
        builder.setDeleteIntent(
            PendingIntent.getBroadcast(
                context, id,
                dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Setup content intent
        val showAndDismiss =
            AlarmInstance.createIntent(context, AlarmStateManager::class.java, instance.mId)
        showAndDismiss.putExtra(EXTRA_NOTIFICATION_ID, id)
        showAndDismiss.action = AlarmStateManager.SHOW_AND_DISMISS_ALARM_ACTION
        builder.setContentIntent(
            PendingIntent.getBroadcast(
                context, id,
                showAndDismiss, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        val nm = NotificationManagerCompat.from(context)
        NotificationUtils.createChannel(
            context,
            NotificationUtils.ALARM_MISSED_NOTIFICATION_CHANNEL_ID
        )
        val notification = builder.build()


        nm.notify(id, notification)
        updateMissedAlarmGroupNotification(context, -1, notification)
    }

    @JvmStatic
    @Synchronized
    fun showAlarmNotification(service: Service, instance: AlarmInstance) {
        val resources = service.resources
        val notification = NotificationCompat.Builder(
            service, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID
        )
            .setContentTitle(instance.getLabelOrDefault(service))
            .setContentText(AlarmUtils.getFormattedTime(service, instance.alarmTime))
            .setColor(service.getColor(R.color.md_theme_primary))
            .setSmallIcon(R.drawable.ic_tab_alarm_static)
            .setOngoing(true)
            .setAutoCancel(false)
            .setDefaults(NotificationCompat.DEFAULT_LIGHTS)
            .setWhen(0)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setLocalOnly(true)

        // Setup Snooze Action only if snooze duration has NOT been set to "None" in the settings
        // or if "Enable alarm snooze actions" is enabled in the expanded alarm view
        if (instance.mSnoozeDuration != PreferencesDefaultValues.ALARM_SNOOZE_DURATION_DISABLED) {
            val snoozeIntent = AlarmStateManager.createStateChangeIntent(
                service,
                AlarmStateManager.ALARM_SNOOZE_TAG, instance, InstancesColumns.SNOOZE_STATE
            )
            snoozeIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true)
            val snoozePendingIntent = PendingIntent.getBroadcast(
                service,
                ALARM_FIRING_NOTIFICATION_ID,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            notification.addAction(
                R.drawable.ic_snooze,
                resources.getString(R.string.alarm_alert_snooze_text),
                snoozePendingIntent
            )
        }

        // Setup Dismiss Action
        val dismissActionTitle: String
        val alarm = Alarm.getAlarm(service.contentResolver, instance.mAlarmId!!)

        if (alarm == null) {
            return
        }

        // Setup up dismiss action
        if (!alarm.daysOfWeek.isRepeating()) {
            if (alarm.deleteAfterUse) {
                dismissActionTitle =
                    resources.getString(R.string.alarm_alert_dismiss_and_delete_text)
            } else {
                dismissActionTitle = resources.getString(R.string.alarm_alert_dismiss_text)
            }
        } else {
            dismissActionTitle = resources.getString(R.string.alarm_alert_dismiss_text)
        }

        val dismissIntent = AlarmStateManager.createStateChangeIntent(
            service,
            AlarmStateManager.ALARM_DISMISS_TAG, instance, InstancesColumns.DISMISSED_STATE
        )
        dismissIntent.putExtra(AlarmStateManager.FROM_NOTIFICATION_EXTRA, true)
        val dismissPendingIntent = PendingIntent.getBroadcast(
            service,
            ALARM_FIRING_NOTIFICATION_ID,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        notification.addAction(
            R.drawable.ic_alarm_off,
            dismissActionTitle,
            dismissPendingIntent
        )
        // Stop alarm if user clears notification.
        notification.setDeleteIntent(dismissPendingIntent)

        // Setup Content Action
        val contentIntent =
            AlarmInstance.createIntent(service, AlarmFiringActivity::class.java, instance.mId)
        notification.setContentIntent(
            PendingIntent.getActivity(
                service,
                ALARM_FIRING_NOTIFICATION_ID,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )

        // Setup fullscreen intent
        val prefs = com.feldman.clock.app.ClockApplication.getDefaultSharedPreferences(service)
        val autoOpen = prefs.getBoolean(
            com.feldman.clock.settings.PreferencesKeys.KEY_AUTO_OPEN_FIRING_SCREEN,
            com.feldman.clock.settings.PreferencesDefaultValues.DEFAULT_AUTO_OPEN_FIRING_SCREEN
        )

        // The full-page takeover is only for "nightstand" situations: screen off AND the
        // device unlocked (e.g. docked with Extend Unlock). When the device is locked, the
        // alarm should present as a notification on the lock screen — like Google Clock —
        // so no full-screen intent there; instead the screen is woken below so the
        // lock-screen heads-up is visible.
        val powerManager = service.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        val keyguardManager = service.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        val screenOff = !powerManager.isInteractive
        val locked = keyguardManager.isKeyguardLocked
        if (autoOpen) {
            val fullScreenIntent =
                AlarmInstance.createIntent(service, AlarmFiringActivity::class.java, instance.mId)
            // set action, so we can be different then content pending intent
            fullScreenIntent.action = "fullscreen_activity"
            fullScreenIntent.flags =
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION
            notification.setFullScreenIntent(
                PendingIntent.getActivity(
                    service, ALARM_FIRING_NOTIFICATION_ID, fullScreenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                ), true
            )
        }
        notification.priority = NotificationCompat.PRIORITY_HIGH
        notification.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        // notification.defaults = Notification.DEFAULT_LIGHTS
        // set sound to null to avoid default notification sound
        NotificationUtils.createChannel(service, NotificationUtils.FIRING_NOTIFICATION_CHANNEL_ID)

        clearNotification(service, instance)
        service.startForeground(ALARM_FIRING_NOTIFICATION_ID, notification.build())

        // Locked + screen off: light the screen so the lock-screen notification is seen
        // (a notification alone does not wake the display).
        if (screenOff && locked) {
            @Suppress("DEPRECATION")
            val wakeLock = powerManager.newWakeLock(
                android.os.PowerManager.SCREEN_BRIGHT_WAKE_LOCK
                        or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP
                        or android.os.PowerManager.ON_AFTER_RELEASE,
                "clock:alarmScreenWake"
            )
            wakeLock.acquire(15_000L)
        }
    }

    @JvmStatic
    @Synchronized
    fun clearNotification(context: Context, instance: AlarmInstance) {
        val nm = NotificationManagerCompat.from(context)
        val id = instance.hashCode()
        nm.cancel(id)
        updateUpcomingAlarmGroupNotification(context, id, null)
        updateMissedAlarmGroupNotification(context, id, null)
    }

    /**
     * Updates the notification for an existing alarm.
     */
    @JvmStatic
    fun updateNotification(context: Context, instance: AlarmInstance) {
        when (instance.mAlarmState) {
            InstancesColumns.NOTIFICATION_STATE -> showUpcomingNotification(context, instance)
            InstancesColumns.SNOOZE_STATE -> showSnoozeNotification(context, instance)
            InstancesColumns.MISSED_STATE -> showMissedNotification(context, instance)
            else -> Log.d("AlarmNotifications", "No notification to update")
        }
    }

    @JvmStatic
    fun createViewAlarmIntent(context: Context, instance: AlarmInstance): Intent {
        val alarmId = instance.mAlarmId ?: Alarm.INVALID_ID
        return Alarm.createIntent(context, MainActivity::class.java, alarmId)
            .putExtra(AlarmConstants.SCROLL_TO_ALARM_INTENT_EXTRA, alarmId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Alarm notifications are sorted chronologically. Missed alarms are sorted chronologically
     * **after** all upcoming/snoozed alarms by including the "MISSED" prefix on the
     * sort key.
     *
     * @param instance the alarm instance for which the notification is generated
     * @return the sort key that specifies the order of this alarm notification
     */
    private fun createSortKey(instance: AlarmInstance): String {
        val timeKey = SORT_KEY_FORMAT.format(instance.alarmTime.time)
        val missedAlarm = instance.mAlarmState == InstancesColumns.MISSED_STATE
        return if (missedAlarm) "MISSED $timeKey" else timeKey
    }
}

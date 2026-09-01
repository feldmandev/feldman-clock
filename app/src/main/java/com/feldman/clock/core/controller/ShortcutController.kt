/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.core.controller

import com.feldman.clock.R

import com.feldman.clock.app.MainActivity

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.provider.AlarmClock
import android.util.Log
import androidx.annotation.StringRes
import com.feldman.clock.app.integration.HandleApiCalls
import com.feldman.clock.app.integration.HandleShortcuts
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.Stopwatch
import com.feldman.clock.core.data.StopwatchListener
import com.feldman.clock.core.data.stopwatch.Lap
import com.feldman.clock.core.analytics.Events
import com.feldman.clock.core.analytics.ShortcutEventTracker
import com.feldman.clock.ui.standby.StandbyActivity
import com.feldman.clock.stopwatch.StopwatchService

import com.feldman.clock.core.util.DeviceUtils

internal class ShortcutController(private val context: Context) {

    private val componentName = ComponentName(context, MainActivity::class.java)
    private val shortcutManager = context.getSystemService(ShortcutManager::class.java)

    init {
        Controller.getController().addEventTracker(ShortcutEventTracker(context))
        DataModel.dataModel.addStopwatchListener(StopwatchWatcher())
    }

    fun updateShortcuts() {
        if (!DeviceUtils.isUserUnlocked(context)) {
            return
        }
        try {
            val alarm = createNewAlarmShortcut()
            val timer = createNewTimerShortcut()
            val stopwatch = createStopwatchShortcut()
            val standby = createStandbyShortcut()
            shortcutManager.setDynamicShortcuts(listOf(alarm, timer, stopwatch, standby))
        } catch (e: IllegalStateException) {
            Log.wtf("ShortcutController", e)
        }
    }

    private fun createNewAlarmShortcut(): ShortcutInfo {
        val intent = Intent(AlarmClock.ACTION_SET_ALARM)
            .setClass(context, HandleApiCalls::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val setAlarmShortcut = getShortcutId(R.string.category_alarm, R.string.action_create)
        return ShortcutInfo.Builder(context, setAlarmShortcut)
            .setIcon(Icon.createWithResource(context, R.drawable.shortcut_new_alarm))
            .setActivity(componentName)
            .setShortLabel(context.getString(R.string.shortcut_new_alarm_short))
            .setLongLabel(context.getString(R.string.shortcut_new_alarm_long))
            .setIntent(intent)
            .setRank(0)
            .build()
    }

    private fun createNewTimerShortcut(): ShortcutInfo {
        val intent = Intent(AlarmClock.ACTION_SET_TIMER)
            .setClass(context, HandleApiCalls::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val setTimerShortcut = getShortcutId(R.string.category_timer, R.string.action_create)
        return ShortcutInfo.Builder(context, setTimerShortcut)
            .setIcon(Icon.createWithResource(context, R.drawable.shortcut_new_timer))
            .setActivity(componentName)
            .setShortLabel(context.getString(R.string.shortcut_new_timer_short))
            .setLongLabel(context.getString(R.string.shortcut_new_timer_long))
            .setIntent(intent)
            .setRank(1)
            .build()
    }

    private fun createStopwatchShortcut(): ShortcutInfo {
        @StringRes val action: Int = if (DataModel.dataModel.stopwatch.isRunning) {
            R.string.action_pause
        } else {
            R.string.action_start
        }
        val shortcutId = getShortcutId(R.string.category_stopwatch, action)
        val shortcut = ShortcutInfo.Builder(context, shortcutId)
            .setIcon(Icon.createWithResource(context, R.drawable.shortcut_stopwatch))
            .setActivity(componentName)
            .setRank(2)

        val intent: Intent
        if (DataModel.dataModel.stopwatch.isRunning) {
            intent = Intent(StopwatchService.ACTION_PAUSE_STOPWATCH)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
            shortcut.setShortLabel(context.getString(R.string.shortcut_pause_stopwatch_short))
                .setLongLabel(context.getString(R.string.shortcut_pause_stopwatch_long))
        } else {
            intent = Intent(StopwatchService.ACTION_START_STOPWATCH)
                .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
            shortcut.setShortLabel(context.getString(R.string.shortcut_start_stopwatch_short))
                .setLongLabel(context.getString(R.string.shortcut_start_stopwatch_long))
        }
        intent.setClass(context, HandleShortcuts::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return shortcut
            .setIntent(intent)
            .build()
    }

    private fun createStandbyShortcut(): ShortcutInfo {
        val intent = Intent(Intent.ACTION_MAIN)
            .setClass(context, StandbyActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(Events.EXTRA_EVENT_LABEL, R.string.label_shortcut)
        val standbyShortcut = getShortcutId(R.string.category_standby, R.string.action_show)
        return ShortcutInfo.Builder(context, standbyShortcut)
            .setIcon(Icon.createWithResource(context, R.drawable.shortcut_standby))
            .setActivity(componentName)
            .setShortLabel(context.getString(R.string.shortcut_start_standby_short))
            .setLongLabel(context.getString(R.string.shortcut_start_standby_long))
            .setIntent(intent)
            .setRank(3)
            .build()
    }

    private fun getShortcutId(@StringRes category: Int, @StringRes action: Int): String {
        return if (category == R.string.category_stopwatch) {
            context.getString(category)
        } else {
            context.getString(category) + "_" + context.getString(action)
        }
    }

    private inner class StopwatchWatcher : StopwatchListener {
        override fun stopwatchUpdated(after: Stopwatch) {
            if (!DeviceUtils.isUserUnlocked(context)) {
                return
            }
            try {
                shortcutManager.updateShortcuts(listOf(createStopwatchShortcut()))
            } catch (e: IllegalStateException) {
                Log.wtf("ShortcutController", e)
            }
        }

        override fun lapAdded(lap: Lap) {}
    }
}

/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.feldman.clock.ui.alarm

import com.feldman.clock.R

import com.feldman.clock.app.ClockApplication
import com.feldman.clock.app.integration.HandleApiCalls

import android.os.Bundle
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.util.ClockUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class AlarmSelectionActivity : ComponentActivity() {

    companion object {
        /**
         * Action used to signify alarm should be dismissed on selection.
         */
        const val ACTION_DISMISS = 0
        const val EXTRA_ACTION = "com.best.deskclock.EXTRA_ACTION"
        const val EXTRA_ALARMS = "com.best.deskclock.EXTRA_ALARMS"
        /**
         * Used by default when an invalid action provided.
         */
        private const val ACTION_INVALID = -1
    }

    private var action = ACTION_INVALID
    private val backgroundScope = CoroutineScope(Dispatchers.IO)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        val alarmsFromIntent = intent.getParcelableArrayExtra(EXTRA_ALARMS, Alarm::class.java)

        action = intent.getIntExtra(EXTRA_ACTION, ACTION_INVALID)

        val selections = alarmsFromIntent?.map { it as Alarm } ?: emptyList()

        setContent {
            MaterialTheme {
                Surface {
                    AlarmSelectionScreen(
                        alarms = selections,
                        onAlarmClick = { alarm ->
                            processAlarmActionAsync(alarm)
                            finish()
                        },
                        onCancelClick = {
                            finish()
                        }
                    )
                }
            }
        }
    }

    private fun processAlarmActionAsync(alarm: Alarm) {
        backgroundScope.launch {
            when (action) {
                ACTION_DISMISS -> HandleApiCalls.Companion.dismissAlarm(alarm, this@AlarmSelectionActivity)
                ACTION_INVALID -> Log.i("AlarmService", "Invalid action")
            }
        }
    }
}

@Composable
fun AlarmSelectionScreen(
    alarms: List<Alarm>,
    onAlarmClick: (Alarm) -> Unit,
    onCancelClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 20.dp, start = 30.dp, end = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.dismiss_alarm),
            fontSize = 24.sp,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(alarms) { alarm ->
                AlarmSelectionRow(alarm = alarm, onClick = { onAlarmClick(alarm) })
            }
        }

        Button(
            onClick = onCancelClick,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Text(text = stringResource(android.R.string.cancel))
        }
    }
}

@Composable
fun AlarmSelectionRow(alarm: Alarm, onClick: () -> Unit) {
    val context = LocalContext.current
    val settings = ClockApplication.getDefaultSharedPreferences(context)
    val clockUtils = remember { ClockUtils }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp)
    ) {
        // Digital Clock Text
        val calendar = remember { Calendar.getInstance(TimeZone.getTimeZone("UTC")) }
        calendar.set(Calendar.HOUR_OF_DAY, alarm.hour)
        calendar.set(Calendar.MINUTE, alarm.minutes)
        
        // This is a simplified version of TextTime logic for Compose
        val is24HourMode = DataModel.dataModel.is24HourFormat()
        val format = if (is24HourMode) {
            ClockUtils.get24ModeFormat(context, false)
        } else {
            ClockUtils.get12ModeFormat(context, 0.45f, false)
        }
        
        val timeText = DateFormat.format(format, calendar)

        Text(
            text = timeText.toString(),
            fontSize = 40.sp, // Approximate size
            color = MaterialTheme.colorScheme.onSurface
        )

        // Label
        if (alarm.label.isNotEmpty()) {
            Text(
                text = alarm.label,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        }

        // Days of Week or Status
        val statusText = if (alarm.daysOfWeek.isRepeating()) {
            val weekdayOrder = SettingsDAO.getWeekdayOrder(settings)
            alarm.daysOfWeek.toString(context, weekdayOrder)
        } else {
             val today = Calendar.getInstance()
             if (Alarm.isTomorrow(alarm, today) && !alarm.isSpecifiedDate) {
                 stringResource(R.string.alarm_tomorrow)
             } else if (alarm.isSpecifiedDate) {
                 if (Alarm.isSpecifiedDateTomorrow(alarm.year, alarm.month, alarm.day)) {
                     stringResource(R.string.alarm_tomorrow)
                 } else if (alarm.isDateInThePast) {
                     // Determine if it will be scheduled for today or tomorrow based on time
                     if (alarm.hour < today.get(Calendar.HOUR_OF_DAY)
                                || (alarm.hour == today.get(Calendar.HOUR_OF_DAY) && alarm.minutes < today.get(Calendar.MINUTE))
                                || (alarm.hour == today.get(Calendar.HOUR_OF_DAY) && alarm.minutes == today.get(Calendar.MINUTE))) {
                         stringResource(R.string.alarm_tomorrow)
                     } else {
                         stringResource(R.string.alarm_today)
                     }
                 } else {
                     val dateCalendar = Calendar.getInstance()
                     dateCalendar.set(alarm.year, alarm.month, alarm.day)
                     val pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMMMd")
                     val dateFormat = SimpleDateFormat(pattern, Locale.getDefault())
                     stringResource(R.string.alarm_scheduled_for, dateFormat.format(dateCalendar.time))
                 }
             } else {
                 stringResource(R.string.alarm_today)
             }
        }
        
        Text(
            text = statusText,
             fontSize = 14.sp,
             color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
    }
}

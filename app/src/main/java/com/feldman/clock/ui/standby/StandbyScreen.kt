package com.feldman.clock.ui.standby

import com.feldman.clock.R

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.core.util.ClockUtils
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.settings.SettingsDAO
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.random.Random
import androidx.compose.ui.res.stringResource

@Composable
fun StandbyScreen(
    onScreenTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val settings = ClockApplication.getDefaultSharedPreferences(context)
    
    var time by remember { mutableStateOf(Calendar.getInstance()) }
    var nextAlarm by remember { mutableStateOf<String?>(null) }
    
    // Preferences
    val isBoldTime = remember { SettingsDAO.isStandbyDigitalClockInBold(settings) }
    val isItalicTime = remember { SettingsDAO.isStandbyDigitalClockInItalic(settings) }
    val isBoldDate = remember { SettingsDAO.isStandbyDateInBold(settings) }
    val isItalicDate = remember { SettingsDAO.isStandbyDateInItalic(settings) }
    val isBoldNextAlarm = remember { SettingsDAO.isStandbyNextAlarmInBold(settings) }
    val isItalicNextAlarm = remember { SettingsDAO.isStandbyNextAlarmInItalic(settings) }
    val showSeconds = remember { SettingsDAO.areStandbyClockSecondsDisplayed(settings) }
    
    // Dimming colors
    val clockColor = remember { Color(SettingsDAO.getStandbyClockColorPicker(settings)) }
    val dateColor = remember { Color(SettingsDAO.getStandbyDateColorPicker(settings)) }
    val nextAlarmColor = remember { Color(SettingsDAO.getStandbyNextAlarmColorPicker(settings)) }
    
    val brightness = remember { SettingsDAO.getStandbyBrightness(settings) }

    // Position state for burn-in protection
    var offsetX by remember { mutableStateOf(0) }
    var offsetY by remember { mutableStateOf(0) }

    // Update time every second or minute based on settings
    LaunchedEffect(showSeconds) {
        while (true) {
            time = Calendar.getInstance()
            delay(if (showSeconds) 1000L else 60000L)
        }
    }

    // Listen for next alarm changes
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                nextAlarm = getNextAlarmString(context)
            }
        }
        val filter = IntentFilter(AlarmUtils.ACTION_NEXT_ALARM_CHANGED_BY_CLOCK)
        context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)

        
        // Initial fetch
        nextAlarm = getNextAlarmString(context)

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                onClick = onScreenTap, 
                indication = null, 
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            )
    ) {
        val maxWidth = constraints.maxWidth
        val maxHeight = constraints.maxHeight
        
        // Move every minute (updates when minute changes)
        LaunchedEffect(time.get(Calendar.MINUTE)) {
             // Safe margins (assumes content is somewhat centered or smaller than screen)
             // We can use a random offset within a reasonable range.
             // Let's assume content takes up at most half screen width/height for safety.
             val rangeX = (maxWidth / 2).coerceAtLeast(1)
             val rangeY = (maxHeight / 2).coerceAtLeast(1)
             
             // Center is (maxWidth/2, maxHeight/2).
             // We want to offset from top-left (0,0) or center?
             // Column is TopStart by default (implied by offset).
             // If we start at 0,0, we can move up to maxWidth - contentWidth.
             // Since we don't know content width, we'll try to center it plus random offset.
             // Actually, simplest burn-in is just random placement in 80% of screen.
             
             val safeX = (maxWidth * 0.1).toInt()
             val safeY = (maxHeight * 0.1).toInt()
             val endX = (maxWidth * 0.6).toInt() // ensuring ample space
             val endY = (maxHeight * 0.6).toInt()

             offsetX = Random.nextInt(safeX, endX.coerceAtLeast(safeX + 1))
             offsetY = Random.nextInt(safeY, endY.coerceAtLeast(safeY + 1))
        }

        Column(
            modifier = Modifier
                .offset { IntOffset(offsetX, offsetY) },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Clock
            val clockFormat = getClockFormat(context, showSeconds)
            // DateFormat.format returns CharSequence. Text expects String.
            val clockText = DateFormat.format(clockFormat, time).toString()
            
            Text(
                text = clockText,
                fontSize = 80.sp,
                fontWeight = if (isBoldTime) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalicTime) FontStyle.Italic else FontStyle.Normal,
                color = adjustColorBrightness(clockColor, brightness)
            )

            // Date
            val dateText = DateFormat.format(stringResource(R.string.abbrev_wday_month_day_no_year), time).toString()
            Text(
                text = dateText,
                fontSize = 20.sp,
                fontWeight = if (isBoldDate) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (isItalicDate) FontStyle.Italic else FontStyle.Normal,
                color = adjustColorBrightness(dateColor, brightness)
            )

            // Next Alarm
            if (!nextAlarm.isNullOrEmpty()) {
                Text(
                    text = nextAlarm!!,
                    fontSize = 16.sp,
                    fontWeight = if (isBoldNextAlarm) FontWeight.Bold else FontWeight.Normal,
                    fontStyle = if (isItalicNextAlarm) FontStyle.Italic else FontStyle.Normal,
                    color = adjustColorBrightness(nextAlarmColor, brightness)
                )
            }
        }
    }
}

private fun getClockFormat(context: Context, showSeconds: Boolean): CharSequence {
    return if (DataModel.dataModel.is24HourFormat()) {
         ClockUtils.get24ModeFormat(context, showSeconds)
    } else {
         ClockUtils.get12ModeFormat(context, 0.4f, showSeconds)
    }
}

private fun getNextAlarmString(context: Context?): String? {
    if (context == null) return null
    return AlarmUtils.getNextAlarm(context)
}

private fun adjustColorBrightness(color: Color, brightnessPercentage: Int): Color {
    val alpha = (brightnessPercentage / 100f).coerceIn(0f, 1f)
    return color.copy(alpha = alpha)
}

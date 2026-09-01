package com.feldman.clock.ui.alarm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionButtonState
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.feldman.motion.MotionTheme
import com.feldman.motion.feldmanFont
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun AlarmFiringScreen(
    alarmLabel: String,
    onSnooze: () -> Unit,
    onDismiss: () -> Unit
) {
    var currentTime by remember { mutableStateOf(Calendar.getInstance()) }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = Calendar.getInstance()
            delay(1000.milliseconds)
        }
    }

    val timeFormat = SimpleDateFormat("h:mm", Locale.getDefault())
    val amPmFormat = SimpleDateFormat("a", Locale.getDefault())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Spacer(modifier = Modifier.weight(1f))

        // Alarm Label
        Text(
            text = alarmLabel.ifEmpty { "Alarm" },
            style = typography.displaySmall,
            fontFamily = feldmanFont(weight = 700, round = 200f),
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // Large Clock
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = timeFormat.format(currentTime.time),
                style = typography.displayLarge.copy(fontSize = 112.sp),
                fontFamily = feldmanFont(weight = 700, round = 200f),
                color = colorScheme.onBackground
            )
            Text(
                text = amPmFormat.format(currentTime.time),
                style = typography.headlineMedium,
                fontFamily = feldmanFont(weight = 600, round = 200f),
                color = colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MotionButton(
                onClick = onSnooze,
                modifier = Modifier.weight(1f),
                height = 72.dp,
                text = "Snooze",
                fontSize = 18.sp,
                defaultState = MotionButtonState(
                    backgroundColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    cornerRadius = 50f
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    cornerRadius = 30f
                )
            )
            Spacer(Modifier.width(8.dp))
            MotionButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                height = 72.dp,
                text = "Stop",
                fontSize = 18.sp,
                defaultState = MotionButtonState(
                    backgroundColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    cornerRadius = 50f
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    cornerRadius = 30f
                )
            )
        }
        
        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Preview(
    name = "Alarm Firing Screen",
    showBackground = true,
    backgroundColor = 0xFF000000
)
@Composable
fun AlarmFiringScreenPreview() {
    MotionTheme {
        AlarmFiringScreen(
            alarmLabel = "Morning Alarm",
            onSnooze = {},
            onDismiss = {}
        )
    }
}

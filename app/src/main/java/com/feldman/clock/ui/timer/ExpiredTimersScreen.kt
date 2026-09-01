package com.feldman.clock.ui.timer

import com.feldman.clock.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.core.data.Timer
import com.feldman.clock.core.data.TimerListener
import kotlinx.coroutines.delay
import java.util.Locale
import com.feldman.clock.app.navigation.Dest
import androidx.compose.foundation.layout.Box
import com.feldman.motion.feldmanFont
import kotlin.math.abs

@Composable
fun ExpiredTimersScreen(
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean
) {
    // State to hold the list of expired timers
    var expiredTimers by remember { mutableStateOf(DataModel.dataModel.expiredTimers) }
    // State to trigger recomposition for time updates
    var timeUpdateTrigger by remember { mutableStateOf(0L) }

    DisposableEffect(Unit) {
        val listener = object : TimerListener {
            override fun timerAdded(timer: Timer) {
                expiredTimers = DataModel.dataModel.expiredTimers
            }
            override fun timerUpdated(before: Timer, after: Timer) {
                expiredTimers = DataModel.dataModel.expiredTimers
            }
            override fun timerRemoved(timer: Timer) {
                expiredTimers = DataModel.dataModel.expiredTimers
            }
        }
        DataModel.dataModel.addTimerListener(listener)
        onDispose {
            DataModel.dataModel.removeTimerListener(listener)
        }
    }

    // Update time every 500ms
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            timeUpdateTrigger = System.currentTimeMillis()
        }
    }

    if (expiredTimers.isEmpty()) {
        // Should ideally navigate back, but for now show empty or handle via navigation effect elsewhere
        // If this screen is shown, it expects expired timers.
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Expired Timers") },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigate(Dest.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            ExpiredTimersContent(expiredTimers = expiredTimers, timeTrigger = timeUpdateTrigger)
        }
    }
}

@Composable
fun ExpiredTimersContent(expiredTimers: List<Timer>, timeTrigger: Long) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(expiredTimers) { timer ->
                    ExpiredTimerItem(timer)
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            if (expiredTimers.size > 1) {
                Button(
                    onClick = { DataModel.dataModel.resetOrDeleteExpiredTimers(R.string.label_deskclock) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(text = stringResource(id = R.string.timer_stop_all))
                }
            }
        }
    }
}

@Composable
fun ExpiredTimerItem(timer: Timer) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(8.dp)
    ) {
        Text(
            text = if (timer.label.isNullOrEmpty()) stringResource(R.string.timer_times_up) else timer.label,
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = feldmanFont(weight = 350),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = formatNegativeDuration(timer.remainingTime),
            style = MaterialTheme.typography.displayMedium,
            fontFamily = feldmanFont
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { DataModel.dataModel.resetOrDeleteExpiredTimers(R.string.label_deskclock); },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(text = stringResource(R.string.timer_stop))
            }

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { DataModel.dataModel.addCustomTimeToTimer(timer) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)

            ) {
                Text(text = "+ 1:00")
            }
        }
    }
}

fun formatNegativeDuration(remainingTime: Long): String {
    val absTime = abs(remainingTime)
    val seconds = (absTime / 1000) % 60
    val minutes = (absTime / (1000 * 60)) % 60
    val hours = (absTime / (1000 * 60 * 60))

    return if (hours > 0) {
        String.format(Locale.getDefault(), "-%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "-%02d:%02d", minutes, seconds)
    }
}

package com.feldman.clock.ui.alarm

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.feldman.clock.core.alarm.AlarmChallenge
import kotlin.math.sqrt
import kotlin.random.Random

/** How many distinct shakes the shake challenge asks for. */
private const val SHAKES_REQUIRED = 12

/** Acceleration past gravity, in m/s², that counts as a shake. */
private const val SHAKE_THRESHOLD = 12f

/** Ignore samples this close together so one shake is not counted many times. */
private const val SHAKE_DEBOUNCE_MS = 250L

private val TYPING_PHRASES = listOf(
    "wide awake",
    "good morning",
    "out of bed",
    "rise and shine"
)

/**
 * Blocks dismissal until the challenge is completed.
 *
 * Shown as a dialog over the firing screen, so the alarm keeps ringing behind it — the point
 * is to make dismissing deliberate, not to give the user a quiet moment to fall back asleep.
 * [onGiveUp] returns to the firing screen without dismissing.
 */
@Composable
fun AlarmChallengeGate(
    challenge: AlarmChallenge,
    onSolved: () -> Unit,
    onGiveUp: () -> Unit
) {
    when (challenge) {
        AlarmChallenge.NONE -> onSolved()
        AlarmChallenge.MATH -> MathChallenge(onSolved, onGiveUp)
        AlarmChallenge.SHAKE -> ShakeChallenge(onSolved, onGiveUp)
        AlarmChallenge.TYPE -> TypingChallenge(onSolved, onGiveUp)
    }
}

@Composable
private fun MathChallenge(onSolved: () -> Unit, onGiveUp: () -> Unit) {
    // Two-digit operands so it needs actual thought, but stays solvable half-awake.
    val a = remember { Random.nextInt(11, 40) }
    val b = remember { Random.nextInt(11, 40) }
    val c = remember { Random.nextInt(2, 9) }
    val answer = remember { a + b * c }
    var entry by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }

    ChallengeDialog(
        title = "Solve to dismiss",
        onGiveUp = onGiveUp,
        confirmEnabled = entry.isNotBlank(),
        onConfirm = {
            if (entry.trim().toIntOrNull() == answer) onSolved() else {
                wrong = true
                entry = ""
            }
        }
    ) {
        Text("$a + $b × $c = ?", style = typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it.filter(Char::isDigit); wrong = false },
            singleLine = true,
            isError = wrong,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            supportingText = if (wrong) {
                { Text("Not quite — try again", color = colorScheme.error) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun TypingChallenge(onSolved: () -> Unit, onGiveUp: () -> Unit) {
    val phrase = remember { TYPING_PHRASES.random() }
    var entry by remember { mutableStateOf("") }
    val matches = entry.trim().equals(phrase, ignoreCase = true)

    ChallengeDialog(
        title = "Type to dismiss",
        onGiveUp = onGiveUp,
        confirmEnabled = matches,
        onConfirm = onSolved
    ) {
        Text(phrase, style = typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = entry,
            onValueChange = { entry = it },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ShakeChallenge(onSolved: () -> Unit, onGiveUp: () -> Unit) {
    val context = LocalContext.current
    var shakes by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeAt = 0L

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val (x, y, z) = Triple(event.values[0], event.values[1], event.values[2])
                val magnitude = sqrt(x * x + y * y + z * z) - SensorManager.GRAVITY_EARTH
                val now = System.currentTimeMillis()
                if (magnitude > SHAKE_THRESHOLD && now - lastShakeAt > SHAKE_DEBOUNCE_MS) {
                    lastShakeAt = now
                    shakes++
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        if (sensor != null) {
            manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            // No accelerometer, so the challenge is impossible — do not trap the user.
            shakes = SHAKES_REQUIRED
        }
        onDispose { manager?.unregisterListener(listener) }
    }

    if (shakes >= SHAKES_REQUIRED) onSolved()

    ChallengeDialog(
        title = "Shake to dismiss",
        onGiveUp = onGiveUp,
        confirmEnabled = false,
        onConfirm = {}
    ) {
        Text(
            "${(SHAKES_REQUIRED - shakes).coerceAtLeast(0)} to go",
            style = typography.headlineMedium
        )
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { shakes.toFloat() / SHAKES_REQUIRED },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun ChallengeDialog(
    title: String,
    onGiveUp: () -> Unit,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable () -> Unit
) {
    AlertDialog(
        // Not dismissible by tapping outside; the whole point is that it must be completed.
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) { content() }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = confirmEnabled) { Text("Dismiss") }
        },
        dismissButton = {
            TextButton(onClick = onGiveUp) { Text("Back") }
        }
    )
}

package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.rememberBooleanPreference
import com.feldman.clock.ui.settings.rememberIntStringPreference
import com.feldman.clock.ui.settings.rememberStringPreference
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current
    var timerRingtone by rememberStringPreference(
        PreferencesKeys.KEY_TIMER_RINGTONE,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    )
    var vibrate by rememberBooleanPreference(PreferencesKeys.KEY_TIMER_VIBRATE, false)
    var autoSilence by rememberIntStringPreference(PreferencesKeys.KEY_TIMER_AUTO_SILENCE_DURATION, 30)
    var crescendo by rememberIntStringPreference(PreferencesKeys.KEY_TIMER_VOLUME_CRESCENDO_DURATION, 0)
    var flipAction by rememberBooleanPreference(PreferencesKeys.KEY_TIMER_FLIP_ACTION, false)
    var shakeAction by rememberBooleanPreference(PreferencesKeys.KEY_TIMER_SHAKE_ACTION, false)
    var addTimeValue by rememberIntStringPreference(PreferencesKeys.KEY_TIMER_ADD_TIME_BUTTON_VALUE, 60)

    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI,
                    Uri::class.java
                )
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            timerRingtone = uri?.toString() ?: ""
        }
    }

    val ringtoneTitle = remember(timerRingtone) {
        if (timerRingtone.isNotEmpty()) {
            try {
                RingtoneManager.getRingtone(context, timerRingtone.toUri())
                    ?.getTitle(context) ?: "Unknown"
            } catch (_: Exception) {
                "Unknown"
            }
        } else {
            "Silent"
        }
    }

    fun formatDuration(seconds: Int): String = when {
        seconds == 0 -> "None"
        seconds == -1 -> "Never"
        seconds < 60 -> "$seconds seconds"
        seconds == 60 -> "1 minute"
        else -> "${seconds / 60} minutes"
    }

    fun parseDuration(label: String): Int = when (label) {
        "None" -> 0
        "Never" -> -1
        "30 seconds" -> 30
        "15 seconds" -> 15
        "1 minute" -> 60
        "2 minutes" -> 120
        "5 minutes" -> 300
        "10 minutes" -> 600
        else -> 60
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Timers Settings",
                chromeColor = SettingsCategoryColor.TIMER.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Sound & Vibration")
        section {
            pageItem(
                title = "Timer Sound",
                description = ringtoneTitle,
                icon = painterResource(R.drawable.ic_music_note),
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        val existingUri = if (timerRingtone.isNotEmpty()) timerRingtone.toUri() else null
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                    }
                    ringtoneLauncher.launch(intent)
                }
            )
            switchItem(
                title = "Vibrate",
                description = "Vibrate when timer ends",
                checked = vibrate,
                onCheckedChange = { vibrate = it }
            )
            dropdownItem(
                label = "Auto-silence",
                options = listOf("30 seconds", "1 minute", "5 minutes", "10 minutes", "Never"),
                selected = formatDuration(autoSilence),
                onSelected = { autoSilence = parseDuration(it) }
            )
            dropdownItem(
                label = "Volume Crescendo",
                options = listOf("None", "5 seconds", "10 seconds", "30 seconds", "1 minute"),
                selected = formatDuration(crescendo),
                onSelected = { crescendo = parseDuration(it) }
            )
        }

        title("Quick Actions")
        section {
            switchItem(
                title = "Flip to stop",
                description = "Flip the phone to stop the timer",
                checked = flipAction,
                onCheckedChange = { flipAction = it }
            )
            switchItem(
                title = "Shake to stop",
                description = "Shake the phone to stop the timer",
                checked = shakeAction,
                onCheckedChange = { shakeAction = it }
            )
        }

        title("General")
        section {
            dropdownItem(
                label = "Add time button value",
                options = listOf("15 seconds", "30 seconds", "1 minute", "2 minutes", "5 minutes"),
                selected = formatDuration(addTimeValue),
                onSelected = { addTimeValue = parseDuration(it) }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

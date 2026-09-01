package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.ui.alarm.AlarmDurationOptions
import com.feldman.clock.ui.alarm.AlarmDurationOptions.labelFor
import com.feldman.clock.ui.alarm.AlarmDurationOptions.valueFor
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.rememberBooleanPreference
import com.feldman.clock.ui.settings.rememberIntPreference
import com.feldman.clock.ui.settings.rememberIntStringPreference
import com.feldman.clock.ui.settings.rememberStringPreference
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current
    val isDark = isDarkTheme()

    var flashlightEnabled by rememberBooleanPreference(PreferencesKeys.KEY_TURN_ON_BACK_FLASH_FOR_TRIGGERED_ALARM, false)
    var autoOpen by rememberBooleanPreference(PreferencesKeys.KEY_AUTO_OPEN_FIRING_SCREEN, PreferencesDefaultValues.DEFAULT_AUTO_OPEN_FIRING_SCREEN)
    var flipAction by rememberStringPreference(PreferencesKeys.KEY_FLIP_ACTION, "0")
    var shakeAction by rememberStringPreference(PreferencesKeys.KEY_SHAKE_ACTION, "0")
    var notificationTime by rememberIntStringPreference(PreferencesKeys.KEY_ALARM_NOTIFICATION_REMINDER_TIME, 10)
    var perAlarmAutoSilence by rememberBooleanPreference(
        PreferencesKeys.KEY_ENABLE_PER_ALARM_AUTO_SILENCE,
        PreferencesDefaultValues.DEFAULT_ENABLE_PER_ALARM_AUTO_SILENCE
    )
    var perAlarmCrescendo by rememberBooleanPreference(
        PreferencesKeys.KEY_ENABLE_PER_ALARM_VOLUME_CRESCENDO_DURATION,
        PreferencesDefaultValues.DEFAULT_ENABLE_PER_ALARM_VOLUME_CRESCENDO_DURATION
    )
    var perAlarmVolume by rememberBooleanPreference(
        PreferencesKeys.KEY_ENABLE_PER_ALARM_VOLUME,
        PreferencesDefaultValues.DEFAULT_ENABLE_PER_ALARM_VOLUME
    )

    // Defaults that every newly created alarm starts from.
    var defaultSnooze by rememberIntPreference(
        PreferencesKeys.KEY_ALARM_SNOOZE_DURATION,
        PreferencesDefaultValues.DEFAULT_ALARM_SNOOZE_DURATION
    )
    var defaultAutoSilence by rememberIntPreference(
        PreferencesKeys.KEY_AUTO_SILENCE_DURATION,
        PreferencesDefaultValues.DEFAULT_AUTO_SILENCE_DURATION
    )
    var defaultCrescendo by rememberIntPreference(
        PreferencesKeys.KEY_ALARM_VOLUME_CRESCENDO_DURATION,
        PreferencesDefaultValues.DEFAULT_VOLUME_CRESCENDO_DURATION
    )
    var defaultRingtone by rememberStringPreference(
        PreferencesKeys.KEY_DEFAULT_ALARM_RINGTONE,
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM).toString()
    )

    val actionOptions = listOf("Nothing", "Snooze", "Dismiss")
    fun actionIdToName(id: String): String = when (id) {
        "1" -> "Snooze"
        "2" -> "Dismiss"
        else -> "Nothing"
    }
    fun actionNameToId(name: String): String = when (name) {
        "Snooze" -> "1"
        "Dismiss" -> "2"
        else -> "0"
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Alarms Settings",
                chromeColor = SettingsCategoryColor.ALARM.container(isDark),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Behavior")
        section {
            switchItem(
                title = "Flashlight",
                checked = flashlightEnabled,
                onCheckedChange = { flashlightEnabled = it }
            )
            switchItem(
                title = "Auto open firing screen",
                checked = autoOpen,
                onCheckedChange = { autoOpen = it }
            )
        }

        title("Defaults for New Alarms")
        section {
            dropdownItem(
                label = "Snooze duration",
                options = AlarmDurationOptions.Snooze.map { it.first },
                selected = AlarmDurationOptions.Snooze.labelFor(defaultSnooze),
                onSelected = {
                    defaultSnooze = AlarmDurationOptions.Snooze.valueFor(it, defaultSnooze)
                }
            )
            dropdownItem(
                label = "Silence after",
                options = AlarmDurationOptions.AutoSilence.map { it.first },
                selected = AlarmDurationOptions.AutoSilence.labelFor(defaultAutoSilence),
                onSelected = {
                    defaultAutoSilence =
                        AlarmDurationOptions.AutoSilence.valueFor(it, defaultAutoSilence)
                }
            )
            dropdownItem(
                label = "Increase volume gradually",
                options = AlarmDurationOptions.Crescendo.map { it.first },
                selected = AlarmDurationOptions.Crescendo.labelFor(defaultCrescendo),
                onSelected = {
                    defaultCrescendo = AlarmDurationOptions.Crescendo.valueFor(it, defaultCrescendo)
                }
            )
        }

        title("Per-alarm Overrides")
        section {
            switchItem(
                title = "Snooze and silence duration",
                checked = perAlarmAutoSilence,
                onCheckedChange = { perAlarmAutoSilence = it }
            )
            switchItem(
                title = "Gradual volume increase",
                checked = perAlarmCrescendo,
                onCheckedChange = { perAlarmCrescendo = it }
            )
            switchItem(
                title = "Alarm volume",
                checked = perAlarmVolume,
                onCheckedChange = { perAlarmVolume = it }
            )
        }

        title("Quick Actions")
        section {
            dropdownItem(
                label = "Flip Action",
                options = actionOptions,
                selected = actionIdToName(flipAction),
                onSelected = { flipAction = actionNameToId(it) }
            )
            dropdownItem(
                label = "Shake Action",
                options = actionOptions,
                selected = actionIdToName(shakeAction),
                onSelected = { shakeAction = actionNameToId(it) }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }

        title("Notifications")
        section {
            switchItem(
                title = "Pre-alarm notification",
                checked = notificationTime > 0,
                onCheckedChange = { notificationTime = if (it) 10 else 0 }
            )
            item(visible = notificationTime > 0) {
                Column(Modifier.fillMaxWidth()) {
                    Text(
                        "Time before: $notificationTime min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = notificationTime.toFloat(),
                        onValueChange = { notificationTime = it.toInt() },
                        valueRange = 1f..60f,
                        steps = 59
                    )
                }
            }
        }

        title("Sound")
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
                defaultRingtone = uri?.toString() ?: ""
            }
        }

        val ringtoneTitle = remember(defaultRingtone) {
            if (defaultRingtone.isNotEmpty()) {
                RingtoneManager.getRingtone(context, defaultRingtone.toUri())
                    ?.getTitle(context) ?: "Unknown"
            } else {
                "Silent"
            }
        }

        section {
            pageItem(
                title = "Default Alarm Sound",
                description = ringtoneTitle,
                onClick = {
                    val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                        putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        val existingUri = if (defaultRingtone.isNotEmpty()) defaultRingtone.toUri() else null
                        putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, existingUri)
                    }
                    ringtoneLauncher.launch(intent)
                }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.rememberStringPreference
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    var volumeButtons by rememberStringPreference(
        PreferencesKeys.KEY_VOLUME_BUTTONS,
        PreferencesDefaultValues.DEFAULT_VOLUME_BEHAVIOR
    )

    fun formatVolumeBehavior(id: String): String = when (id) {
        "0" -> "Change Volume"
        "1" -> "Snooze"
        "2" -> "Dismiss"
        else -> "Do nothing"
    }

    fun parseVolumeBehavior(label: String): String = when (label) {
        "Change Volume" -> "0"
        "Snooze" -> "1"
        "Dismiss" -> "2"
        else -> "-1"
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Stopwatch Settings",
                chromeColor = SettingsCategoryColor.STOPWATCH.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Behavior")
        section {
            dropdownItem(
                label = "Volume Buttons",
                options = listOf("Change Volume", "Snooze", "Dismiss", "Do nothing"),
                selected = formatVolumeBehavior(volumeButtons),
                onSelected = { volumeButtons = parseVolumeBehavior(it) }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

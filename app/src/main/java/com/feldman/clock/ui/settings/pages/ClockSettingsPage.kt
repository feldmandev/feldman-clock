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
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.rememberBooleanPreference
import com.feldman.clock.ui.settings.rememberStringPreference
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    var timeFormat by rememberStringPreference(PreferencesKeys.KEY_TIME_FORMAT, "system")
    var dateFormat by rememberStringPreference(PreferencesKeys.KEY_DATE_FORMAT, "E, MMM d")
    var showSeconds by rememberBooleanPreference(PreferencesKeys.KEY_DISPLAY_CLOCK_SECONDS, true)

    val dateOptions = listOf(
        "Sun, Jan 11" to "E, MMM d",
        "Sun, Jan 11, 2025" to "E, MMM d, yyyy",
        "11/1/2025" to "M/d/yyyy",
        "11/1" to "M/d",
        "1/11/2025" to "d/M/yyyy",
        "2025-01-11" to "yyyy-MM-dd"
    )

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Clock Settings",
                chromeColor = SettingsCategoryColor.CLOCK.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Time Format")
        section {
            dropdownItem(
                label = "Time Format",
                options = listOf("12-hour (1:00 PM)", "24-hour (13:00)", "System default"),
                selected = when (timeFormat) {
                    "12" -> "12-hour (1:00 PM)"
                    "24" -> "24-hour (13:00)"
                    else -> "System default"
                },
                onSelected = {
                    timeFormat = when (it) {
                        "12-hour (1:00 PM)" -> "12"
                        "24-hour (13:00)" -> "24"
                        else -> "system"
                    }
                }
            )
        }

        title("Date Format")
        section {
            dropdownItem(
                label = "Date Format",
                options = dateOptions.map { it.first },
                selected = dateOptions.find { it.second == dateFormat }?.first ?: dateOptions[0].first,
                onSelected = { label ->
                    dateOptions.find { it.first == label }?.let { dateFormat = it.second }
                }
            )
        }

        title("Display Options")
        section {
            switchItem(
                title = "Display Seconds",
                description = "Show seconds in the digital clock",
                checked = showSeconds,
                onCheckedChange = { showSeconds = it }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

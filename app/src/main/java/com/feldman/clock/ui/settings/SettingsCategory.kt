package com.feldman.clock.ui.settings

import com.feldman.clock.R

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.feldman.clock.app.navigation.Dest
import com.feldman.clock.ui.theme.isDarkTheme

internal enum class SettingsCategoryColor(
    private val darkContainer: Long,
    private val darkContent: Long,
    private val lightContainer: Long,
    private val lightContent: Long
) {
    THEME(0xFF7D5260, 0xFFFFD8E4, 0xFFFFD8E4, 0xFF631835),
    ALARM(0xFF7D2E2E, 0xFFFFDAD6, 0xFFFFDAD6, 0xFF93000A),
    TIMER(0xFF6D3A00, 0xFFFFDCBE, 0xFFFFDCBE, 0xFF7C2E00),
    STOPWATCH(0xFF004D40, 0xFFA8F2E0, 0xFFA8F2E0, 0xFF006B5B),
    WORD_CLOCK(0xFF5B3F72, 0xFFEBD7FF, 0xFFEBD7FF, 0xFF4D2670),
    CLOCK(0xFF004A77, 0xFFC2E7FF, 0xFFD7E3FF, 0xFF005AC1),
    NOTIFICATIONS(0xFF3E4C63, 0xFFD7E3FF, 0xFFD7E3FF, 0xFF253347),
    STANDBY(0xFF324F34, 0xFFCBEFD0, 0xFFCBEFD0, 0xFF042106),
    ABOUT(0xFF004E5B, 0xFFB7EAF4, 0xFFB7EAF4, 0xFF00363F);

    fun container(isDark: Boolean): Color = Color(if (isDark) darkContainer else lightContainer)
    fun content(isDark: Boolean): Color = Color(if (isDark) darkContent else lightContent)
}

@Composable
internal fun settingsChromeColor(destination: Dest): Color? {
    val isDark = isDarkTheme()
    return when (destination) {
        Dest.Settings -> colorScheme.surfaceVariant
        Dest.SettingsAppearance -> SettingsCategoryColor.THEME.container(isDark)
        Dest.SettingsAlarms -> SettingsCategoryColor.ALARM.container(isDark)
        Dest.SettingsTimers -> SettingsCategoryColor.TIMER.container(isDark)
        Dest.SettingsStopwatch -> SettingsCategoryColor.STOPWATCH.container(isDark)
        Dest.SettingsWorldClock -> SettingsCategoryColor.WORD_CLOCK.container(isDark)
        Dest.SettingsClock -> SettingsCategoryColor.CLOCK.container(isDark)
        Dest.SettingsNotifications -> SettingsCategoryColor.NOTIFICATIONS.container(isDark)
        Dest.StandbySettings -> SettingsCategoryColor.STANDBY.container(isDark)
        Dest.SettingsAbout -> SettingsCategoryColor.ABOUT.container(isDark)
        Dest.SettingsWidgets -> colorScheme.surfaceVariant
        else -> null
    }
}

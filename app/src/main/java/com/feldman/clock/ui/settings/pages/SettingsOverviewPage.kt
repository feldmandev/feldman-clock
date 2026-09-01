package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.navigation.SettingsDestination
import com.feldman.motion.MotionNavigator
import com.feldman.motion.SettingsScaffold
import com.feldman.clock.ui.theme.isDarkTheme

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SettingsOverviewPage(
    onNavigate: MotionNavigator,
    onBack: () -> Unit
) {
    val isDark = isDarkTheme()

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Settings",
                chromeColor = colorScheme.surfaceContainer,
                onBack = onBack
            )
        }
    ) {
        title("Personalization")
        section {
            pageItem(
                title = "Appearance",
                description = "Customize the app look",
                icon = painterResource(R.drawable.ic_brightness_auto),
                backgroundColor = SettingsCategoryColor.THEME.container(isDark),
                iconColor = SettingsCategoryColor.THEME.content(isDark),
                iconMorphShape = MaterialShapes.Cookie9Sided,
                paneDestination = SettingsDestination.Appearance,
                onClick = { onNavigate(SettingsDestination.Appearance) }
            )
        }

        title("Clock Features")
        section {
            pageItem(
                title = "Alarms",
                description = "Alarm preferences",
                icon = painterResource(R.drawable.ic_tab_alarm_static),
                backgroundColor = SettingsCategoryColor.ALARM.container(isDark),
                iconColor = SettingsCategoryColor.ALARM.content(isDark),
                paneDestination = SettingsDestination.Alarms,
                onClick = { onNavigate(SettingsDestination.Alarms) }
            )
            pageItem(
                title = "Timers",
                description = "Timer preferences",
                icon = painterResource(R.drawable.ic_tab_timer_static),
                backgroundColor = SettingsCategoryColor.TIMER.container(isDark),
                iconColor = SettingsCategoryColor.TIMER.content(isDark),
                iconMorphShape = MaterialShapes.Diamond,
                paneDestination = SettingsDestination.Timers,
                onClick = { onNavigate(SettingsDestination.Timers) }
            )
            pageItem(
                title = "Stopwatch",
                description = "Stopwatch preferences",
                icon = painterResource(R.drawable.ic_tab_stopwatch_static),
                backgroundColor = SettingsCategoryColor.STOPWATCH.container(isDark),
                iconColor = SettingsCategoryColor.STOPWATCH.content(isDark),
                paneDestination = SettingsDestination.Stopwatch,
                onClick = { onNavigate(SettingsDestination.Stopwatch) }
            )
            pageItem(
                title = "World Clock",
                description = "City list and home time zone",
                icon = painterResource(R.drawable.ic_clock),
                backgroundColor = SettingsCategoryColor.WORD_CLOCK.container(isDark),
                iconColor = SettingsCategoryColor.WORD_CLOCK.content(isDark),
                paneDestination = SettingsDestination.WorldClock,
                onClick = { onNavigate(SettingsDestination.WorldClock) }
            )
            pageItem(
                title = "Clock",
                description = "Time and date format settings",
                icon = painterResource(R.drawable.ic_tab_clock_static),
                backgroundColor = SettingsCategoryColor.CLOCK.container(isDark),
                iconColor = SettingsCategoryColor.CLOCK.content(isDark),
                paneDestination = SettingsDestination.Clock,
                onClick = { onNavigate(SettingsDestination.Clock) }
            )
        }

        title("Home Screen")
        section {
            pageItem(
                title = "Widgets",
                description = "Customize your clock widgets",
                icon = painterResource(R.drawable.ic_widget),
                backgroundColor = SettingsCategoryColor.CLOCK.container(isDark),
                iconColor = SettingsCategoryColor.CLOCK.content(isDark),
                paneDestination = SettingsDestination.Widgets,
                onClick = { onNavigate(SettingsDestination.Widgets) }
            )
        }

        title("General")
        section {
            pageItem(
                title = "Notifications",
                description = "Manage notifications",
                icon = painterResource(R.drawable.ic_notifications),
                backgroundColor = SettingsCategoryColor.NOTIFICATIONS.container(isDark),
                iconColor = SettingsCategoryColor.NOTIFICATIONS.content(isDark),
                iconMorphShape = MaterialShapes.Ghostish,
                paneDestination = SettingsDestination.Notifications,
                onClick = { onNavigate(SettingsDestination.Notifications) }
            )
            pageItem(
                title = "Backup & Restore",
                description = "Export your alarms and settings to a file",
                icon = painterResource(R.drawable.ic_save),
                backgroundColor = SettingsCategoryColor.ABOUT.container(isDark),
                iconColor = SettingsCategoryColor.ABOUT.content(isDark),
                paneDestination = SettingsDestination.Backup,
                onClick = { onNavigate(SettingsDestination.Backup) }
            )
        }

        title("Standby")
        section {
            pageItem(
                title = "Standby",
                description = "Configure standby clock display",
                icon = painterResource(R.drawable.ic_mobile_standby),
                backgroundColor = SettingsCategoryColor.STANDBY.container(isDark),
                iconColor = SettingsCategoryColor.STANDBY.content(isDark),
                paneDestination = SettingsDestination.Standby,
                onClick = { onNavigate(SettingsDestination.Standby) }
            )
        }

        title("System")
        section {
            pageItem(
                title = "About",
                description = "App info, version, and more",
                icon = painterResource(R.drawable.ic_info),
                backgroundColor = SettingsCategoryColor.ABOUT.container(isDark),
                iconColor = SettingsCategoryColor.ABOUT.content(isDark),
                paneDestination = SettingsDestination.About,
                onClick = { onNavigate(SettingsDestination.About) }
            )
        }
        item {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}

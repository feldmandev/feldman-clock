package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.settings.PreferencesDefaultValues
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.settings.rememberBooleanPreference
import com.feldman.clock.ui.settings.rememberStringPreference
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current
    var showUpcoming by rememberBooleanPreference(
        PreferencesKeys.KEY_SHOW_UPCOMING_ALARM_NOTIFICATION,
        PreferencesDefaultValues.DEFAULT_SHOW_UPCOMING_ALARM_NOTIFICATION
    )
    var reminderTime by rememberStringPreference(
        PreferencesKeys.KEY_ALARM_NOTIFICATION_REMINDER_TIME,
        PreferencesDefaultValues.DEFAULT_ALARM_NOTIFICATION_REMINDER_TIME
    )
    var showMissed by rememberBooleanPreference(
        PreferencesKeys.KEY_SHOW_MISSED_ALARM_NOTIFICATION,
        PreferencesDefaultValues.DEFAULT_SHOW_MISSED_ALARM_NOTIFICATION
    )

    fun formatReminderTime(id: String): String = when (id) {
        "15" -> "15 minutes"
        "30" -> "30 minutes"
        "60" -> "1 hour"
        "120" -> "2 hours"
        else -> "$id minutes"
    }

    fun parseReminderTime(label: String): String = when (label) {
        "15 minutes" -> "15"
        "30 minutes" -> "30"
        "1 hour" -> "60"
        "2 hours" -> "120"
        else -> "30"
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Notifications Settings",
                chromeColor = SettingsCategoryColor.NOTIFICATIONS.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Alarms")
        section {
            switchItem(
                title = "Upcoming Alarm Notification",
                description = "Notify before alarm fires",
                checked = showUpcoming,
                onCheckedChange = { showUpcoming = it }
            )
            dropdownItem(
                label = "Reminder Time",
                options = listOf("15 minutes", "30 minutes", "1 hour", "2 hours"),
                selected = formatReminderTime(reminderTime),
                onSelected = { reminderTime = parseReminderTime(it) }
            )
            switchItem(
                title = "Missed Alarm Notification",
                description = "Notify when alarm was missed",
                checked = showMissed,
                onCheckedChange = { showMissed = it }
            )
        }

        title("System")
        section {
            pageItem(
                title = "System Notification Settings",
                description = "Open Android notification channels",
                icon = painterResource(R.drawable.ic_notifications),
                onClick = {
                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    }
                    context.startActivity(intent)
                }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

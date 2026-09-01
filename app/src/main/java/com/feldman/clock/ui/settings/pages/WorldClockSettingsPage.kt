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
fun WorldClockSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    var sortCities by rememberStringPreference(
        PreferencesKeys.KEY_SORT_CITIES,
        PreferencesDefaultValues.DEFAULT_SORT_CITIES_BY_ASCENDING_TIME_ZONE
    )
    var autoHomeClock by rememberBooleanPreference(
        PreferencesKeys.KEY_AUTO_HOME_CLOCK,
        PreferencesDefaultValues.DEFAULT_AUTO_HOME_CLOCK
    )
    var homeTimeZone by rememberStringPreference(PreferencesKeys.KEY_HOME_TIME_ZONE, "")

    fun formatCitySort(id: String): String = when (id) {
        "0" -> "Ascending Time Zone"
        "1" -> "Descending Time Zone"
        "2" -> "Name"
        "3" -> "Manually"
        else -> "Name"
    }

    fun parseCitySort(label: String): String = when (label) {
        "Ascending Time Zone" -> "0"
        "Descending Time Zone" -> "1"
        "Name" -> "2"
        "Manually" -> "3"
        else -> "2"
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "World Clock Settings",
                chromeColor = SettingsCategoryColor.WORD_CLOCK.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("City Sorting")
        section {
            dropdownItem(
                label = "Sort Cities",
                options = listOf("Ascending Time Zone", "Descending Time Zone", "Name", "Manually"),
                selected = formatCitySort(sortCities),
                onSelected = { sortCities = parseCitySort(it) }
            )
        }

        title("Home Clock")
        section {
            switchItem(
                title = "Automatic Home Clock",
                description = "Show head clock when in new city",
                checked = autoHomeClock,
                onCheckedChange = { autoHomeClock = it }
            )
            pageItem(
                title = "Home Time Zone",
                description = if (homeTimeZone.isEmpty()) "Not set" else homeTimeZone,
                icon = painterResource(R.drawable.ic_public),
                onClick = { }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }
}

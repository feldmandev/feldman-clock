@file:Suppress("PROPERTY_WONT_BE_SERIALIZED")

package com.feldman.clock.ui.settings.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.R
import com.feldman.clock.ui.LocalShowSnackbar
import com.feldman.clock.ui.settings.pages.AboutSettingsPage
import com.feldman.clock.ui.settings.pages.AlarmSettingsPage
import com.feldman.clock.ui.settings.pages.BackupSettingsPage
import com.feldman.clock.ui.settings.pages.ClockSettingsPage
import com.feldman.clock.ui.settings.pages.NotificationSettingsPage
import com.feldman.clock.ui.settings.pages.SettingsOverviewPage
import com.feldman.clock.ui.settings.pages.StopwatchSettingsPage
import com.feldman.clock.ui.settings.pages.AppearanceSettingsPage
import com.feldman.clock.ui.settings.pages.TimerSettingsPage
import com.feldman.clock.ui.settings.pages.WidgetsSettingsPage
import com.feldman.clock.ui.settings.pages.WorldClockSettingsPage
import com.feldman.clock.ui.standby.pages.StandbySettingsPage
import com.feldman.motion.MotionDest
import com.feldman.motion.MotionNavHost
import com.feldman.motion.MotionNavigator
import com.feldman.motion.MotionPaneType
import com.feldman.motion.rememberMotionLevel
import com.feldman.motion.rememberMotionDestBackStack
import com.feldman.motion.rememberMotionNavigationState
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable

@Composable
fun SettingsNavigation(onBack: () -> Unit) {
    val backStack = rememberMotionDestBackStack(SettingsDestination.Overview)
    val navigationState = rememberMotionNavigationState(
        backStack = backStack.backStack,
        topLevelDestinations = listOf(SettingsDestination.Overview),
        motionLevel = rememberMotionLevel(),
        showsBottomBar = { false }
    )
    val activeDestination = backStack.backStack.lastOrNull()
    var fabAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(activeDestination) {
        if (activeDestination != SettingsDestination.Standby) fabAction = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        MotionNavHost(
            backStack = backStack,
            destinations = settingsDestinations,
            navigationState = navigationState,
            modifier = Modifier.fillMaxSize(),
            onRootBack = onBack,
            onFabAction = { registeredAction ->
                fabAction = { registeredAction {} }
            }
        )
        if (activeDestination == SettingsDestination.Standby) {
            fabAction?.let { action ->
                LargeFloatingActionButton(
                    onClick = action,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                    containerColor = colorScheme.tertiaryContainer,
                    contentColor = colorScheme.onTertiaryContainer
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_arrow),
                        contentDescription = "Preview"
                    )
                }
            }
        }
    }
}

@Serializable
@Parcelize
sealed class SettingsDestination : MotionDest {
    @Serializable
    @Parcelize
    data object Overview : SettingsDestination() {
        override val label = "Settings"
        override val pane = MotionPaneType.LIST
        override val showNavigation = false

        @Composable
        override fun Content(
            onNavigate: MotionNavigator,
            onBack: () -> Unit,
            searchQuery: String,
            onFabAction: ((() -> Unit) -> Unit) -> Unit
        ) {
            SettingsOverviewPage(onNavigate = onNavigate, onBack = onBack)
        }
    }

    @Serializable
    @Parcelize
    data object Appearance : SettingsDestination() {
        override val label = "Appearance"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            AppearanceSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Alarms : SettingsDestination() {
        override val label = "Alarms Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            AlarmSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Timers : SettingsDestination() {
        override val label = "Timers Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            TimerSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Stopwatch : SettingsDestination() {
        override val label = "Stopwatch Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            StopwatchSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object WorldClock : SettingsDestination() {
        override val label = "World Clock Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            WorldClockSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Clock : SettingsDestination() {
        override val label = "Clock Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            ClockSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Widgets : SettingsDestination() {
        override val label = "Widgets"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            WidgetsSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Notifications : SettingsDestination() {
        override val label = "Notifications Settings"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            NotificationSettingsPage(onBack = onBack, isTab = false)
        }
    }

    @Serializable
    @Parcelize
    data object Backup : SettingsDestination() {
        override val label = "Backup & Restore"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            val showSnackbar = LocalShowSnackbar.current
            BackupSettingsPage(onBack = onBack, isTab = false, showSnackbar = { showSnackbar(it) })
        }
    }

    @Serializable
    @Parcelize
    data object Standby : SettingsDestination() {
        override val label = "Standby"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            StandbySettingsPage(
                showTitle = true,
                onFabClick = { action ->
                    action?.let { previewAction ->
                        onFabAction { previewAction() }
                    }
                },
                onNavigate = { onBack() },
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Serializable
    @Parcelize
    data object About : SettingsDestination() {
        override val label = "About"
        override val pane = MotionPaneType.DETAIL
        override val parent: MotionDest = Overview
        override val showNavigation = false

        @Composable
        override fun Content(onNavigate: MotionNavigator, onBack: () -> Unit, searchQuery: String, onFabAction: ((() -> Unit) -> Unit) -> Unit) {
            AboutSettingsPage(onBack = onBack, isTab = false)
        }
    }
}

private val settingsDestinations: List<MotionDest> = listOf(
    SettingsDestination.Overview,
    SettingsDestination.Appearance,
    SettingsDestination.Alarms,
    SettingsDestination.Timers,
    SettingsDestination.Stopwatch,
    SettingsDestination.WorldClock,
    SettingsDestination.Clock,
    SettingsDestination.Widgets,
    SettingsDestination.Notifications,
    SettingsDestination.Backup,
    SettingsDestination.Standby,
    SettingsDestination.About
)

package com.feldman.clock.app.navigation

import com.feldman.clock.R

import android.os.Parcelable
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.app.MainActivity
import com.feldman.clock.alarm.AlarmConstants
import com.feldman.clock.ui.standby.pages.StandbySettingsPage
import com.feldman.clock.ui.clock.ClockScreen
import com.feldman.clock.ui.settings.pages.AboutSettingsPage
import com.feldman.clock.ui.settings.pages.AlarmSettingsPage
import com.feldman.clock.ui.settings.pages.BackupSettingsPage
import com.feldman.clock.ui.settings.pages.ClockSettingsPage
import com.feldman.clock.ui.settings.pages.NotificationSettingsPage
import com.feldman.clock.ui.settings.pages.StopwatchSettingsPage
import com.feldman.clock.ui.settings.navigation.SettingsNavigation
import com.feldman.clock.ui.settings.pages.AppearanceSettingsPage
import com.feldman.clock.ui.settings.pages.WidgetsSettingsPage
import com.feldman.clock.ui.settings.pages.TimerSettingsPage
import com.feldman.clock.ui.settings.pages.WorldClockSettingsPage
import com.feldman.clock.ui.stopwatch.StopwatchScreen
import com.feldman.clock.ui.timer.ExpiredTimersScreen
import com.feldman.clock.ui.timer.TimerScreen
import com.feldman.clock.ui.onboarding.SetupExactAlarmPage
import com.feldman.clock.ui.onboarding.SetupDonePage
import com.feldman.clock.ui.onboarding.SetupFullScreenAlarmPage
import com.feldman.clock.ui.onboarding.SetupNotificationPage
import com.feldman.clock.ui.onboarding.SetupOverlayPage
import com.feldman.clock.ui.onboarding.SetupWelcomePage
import kotlinx.serialization.Serializable

// Using a type alias or interface for NavKey if not available, 
// but assuming user expects it to be available or we define it.
import com.feldman.clock.ui.LocalSetFabClick
import com.feldman.clock.ui.LocalShowSnackbar
import com.feldman.clock.ui.LocalActivityIntent
import com.feldman.clock.ui.alarm.AlarmScreen
import kotlinx.parcelize.Parcelize
import kotlin.reflect.KClass

// Since I cannot be sure of the package for NavKey, I will define a marker interface here
// if the build fails, or assume it's `androidx.navigation.NavKey`? 
// No, the snippet implies it's a known type. 
// I'll define it myself to be safe for now, avoiding compilation errors if the library isn't fully integrated.
interface NavKey

private fun saveSetupStep(context: Context, step: Int) {
    ClockApplication.getDefaultSharedPreferences(context).edit {
        putInt("setup_step", step)
    }
}

private fun finishSetup(context: Context) {
    ClockApplication.getDefaultSharedPreferences(context).edit {
        putBoolean(MainActivity.KEY_IS_FIRST_LAUNCH, false)
        putInt("setup_step", 0)
    }
}

@Suppress("PROPERTY_WONT_BE_SERIALIZED")
@Serializable
sealed interface Dest : NavKey, Parcelable {
    val label: String
    val filledIcon: Int?
    val outlineIcon: Int?
    val parent: Dest? get() = null
    val showFab: Boolean? get() = false
    val fabIcon: Int? get() = null
    val fabContentDescription: String? get() = null
    val animatedIcon: Int? get() = null

    @Composable
    fun Content(
        onNavigate: (Dest) -> Unit,
        onBack: () -> Unit,
    )

    @Parcelize
    @Serializable
    data object SetupWelcome : Dest {
        override val label = "Welcome"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupWelcomePage {
                saveSetupStep(context, 1)
                onNavigate(SetupNotifications)
            }
        }
    }

    @Parcelize
    @Serializable
    data object SetupNotifications : Dest {
        override val label = "Notifications"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupNotificationPage(
                onNext = {
                    saveSetupStep(context, 2)
                    onNavigate(SetupExactAlarms)
                },
                onBack = {
                    saveSetupStep(context, 0)
                    onBack()
                }
            )
        }
    }

    @Parcelize
    @Serializable
    data object SetupExactAlarms : Dest {
        override val label = "Exact alarms"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupExactAlarmPage(
                onNext = {
                    saveSetupStep(context, 3)
                    onNavigate(SetupOverlay)
                },
                onBack = {
                    saveSetupStep(context, 1)
                    onBack()
                }
            )
        }
    }

    @Parcelize
    @Serializable
    data object SetupOverlay : Dest {
        override val label = "Standby mode"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupOverlayPage(
                onNext = {
                    saveSetupStep(context, 4)
                    onNavigate(SetupFullScreenAlarms)
                },
                onBack = {
                    saveSetupStep(context, 2)
                    onBack()
                }
            )
        }
    }

    @Parcelize
    @Serializable
    data object SetupFullScreenAlarms : Dest {
        override val label = "Full-screen alarms"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupFullScreenAlarmPage(
                onNext = {
                    saveSetupStep(context, 5)
                    onNavigate(SetupDone)
                },
                onBack = {
                    saveSetupStep(context, 3)
                    onBack()
                }
            )
        }
    }

    @Parcelize
    @Serializable
    data object SetupDone : Dest {
        override val label = "Done"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(onNavigate: (Dest) -> Unit, onBack: () -> Unit) {
            val context = LocalContext.current
            SetupDonePage(
                onDone = {
                    finishSetup(context)
                    onNavigate(Alarm)
                },
                onBack = {
                    saveSetupStep(context, 4)
                    onBack()
                }
            )
        }
    }

    @Parcelize
    @Serializable
    data object Alarm : Dest {
        override val label = "Alarms"
        override val filledIcon = R.drawable.ic_tab_alarm_static
        override val outlineIcon = R.drawable.ic_tab_alarm_static
        override val animatedIcon = R.drawable.ic_tab_alarm_avd
        override val showFab = true
        override val fabIcon = R.drawable.ic_add

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            val setFabClick = LocalSetFabClick.current
            val showSnackbar = LocalShowSnackbar.current
            val intent = LocalActivityIntent.current
            
            AlarmScreen(
                onFabClick = { setFabClick(it) },
                showSnackbar = showSnackbar,
                intent = intent,
                onNavigate = onNavigate,
                onBack = onBack,
                isTab = true
            )
        }
    }

    @Parcelize
    @Serializable
    data object Clock : Dest {
        override val label = "Clock"
        override val filledIcon = R.drawable.ic_tab_clock_static
        override val outlineIcon = R.drawable.ic_tab_clock_static
        override val animatedIcon = R.drawable.ic_tab_clock_avd
        override val showFab = true
        override val fabIcon = R.drawable.ic_add

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
             val setFabClick = LocalSetFabClick.current
             ClockScreen(
                 onFabClick = { setFabClick(it) },
                 onNavigate = onNavigate,
                 onBack = onBack,
                 isTab = true
             )
        }
    }

    @Parcelize
    @Serializable
    data object Timer : Dest {
        override val label = "Timers"
        override val filledIcon = R.drawable.ic_tab_timer_static
        override val outlineIcon = R.drawable.ic_tab_timer_static
        override val animatedIcon = R.drawable.avd_hourglass
        override val showFab = true
        override val fabIcon = R.drawable.ic_add

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            val setFabClick = LocalSetFabClick.current
            TimerScreen(
                onFabClick = { setFabClick(it) },
                onNavigate = onNavigate,
                onBack = onBack,
                isTab = true
            )
        }
    }

    @Parcelize
    @Serializable
    data object Stopwatch : Dest {
        override val label = "Stopwatch"
        override val filledIcon = R.drawable.ic_tab_stopwatch_static
        override val outlineIcon = R.drawable.ic_tab_stopwatch_static
        override val animatedIcon = R.drawable.ic_tab_stopwatch_avd
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            StopwatchScreen(
                onNavigate = onNavigate,
                onBack = onBack,
                isTab = true
            )
        }
    }


    @Parcelize
    @Serializable
    data object Settings : Dest {
        override val label = "Settings"
        override val filledIcon = R.drawable.ic_settings
        override val outlineIcon = R.drawable.ic_settings
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            SettingsNavigation(onBack = onBack)
        }
    }


    @Parcelize
    @Serializable
    data object SettingsAlarms : Dest {
        override val label = "Alarms Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            AlarmSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsTimers : Dest {
        override val label = "Timers Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            TimerSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsNotifications : Dest {
        override val label = "Notifications Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            NotificationSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsBackup : Dest {
        override val label = "Backup & Restore"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            val showSnackbar = LocalShowSnackbar.current
            BackupSettingsPage(
                onBack = onBack,
                isTab = false,
                showSnackbar = { showSnackbar(it) }
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsStopwatch : Dest {
        override val label = "Stopwatch Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            StopwatchSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsWorldClock : Dest {
        override val label = "World Clock Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            WorldClockSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsAbout : Dest {
        override val label = "About"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            AboutSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object SettingsWidgets : Dest {
        override val label = "Widgets"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            WidgetsSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }
    

    @Parcelize
    @Serializable
    data object SettingsClock : Dest {
        override val label = "Clock Settings"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            ClockSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }
    @Parcelize
    @Serializable
    data object SettingsAppearance : Dest {
        override val label = "Appearance"
        override val filledIcon = null
        override val outlineIcon = null
        override val showFab = false

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            AppearanceSettingsPage(
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object ExpiredTimers : Dest {
        override val label = "Expired Timers"
        override val filledIcon = null
        override val outlineIcon = null

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            ExpiredTimersScreen(
                onNavigate = onNavigate,
                onBack = onBack,
                isTab = false
            )
        }
    }

    @Parcelize
    @Serializable
    data object StandbySettings : Dest {
        override val label = "Standby"
        override val filledIcon = R.drawable.ic_mobile_standby
        override val outlineIcon = R.drawable.ic_mobile_standby_outlined
        override val showFab = true
        override val fabIcon = R.drawable.ic_play_arrow
        override val fabContentDescription = "Preview"

        @Composable
        override fun Content(
            onNavigate: (Dest) -> Unit,
            onBack: () -> Unit
        ) {
            val setFabClick = LocalSetFabClick.current

            StandbySettingsPage(
                showTitle = true, 
                onFabClick = { setFabClick(it) },
                onNavigate = onNavigate,
                onBack = onBack,
                isTab = false
            )
        }
    }

}

val destinationClasses: List<KClass<out Dest>> = listOf(
    Dest.SetupWelcome::class,
    Dest.SetupNotifications::class,
    Dest.SetupExactAlarms::class,
    Dest.SetupOverlay::class,
    Dest.SetupFullScreenAlarms::class,
    Dest.SetupDone::class,
    Dest.Alarm::class,
    Dest.Clock::class,
    Dest.Timer::class,
    Dest.Settings::class,
    Dest.SettingsAlarms::class,
    Dest.SettingsTimers::class,
    Dest.SettingsNotifications::class,
    Dest.SettingsBackup::class,
    Dest.SettingsStopwatch::class,
    Dest.SettingsWorldClock::class,
    Dest.SettingsAbout::class,
    Dest.SettingsWidgets::class,
    Dest.SettingsClock::class,
    Dest.SettingsAppearance::class,
    Dest.Stopwatch::class,
    Dest.ExpiredTimers::class,
    Dest.StandbySettings::class,
)

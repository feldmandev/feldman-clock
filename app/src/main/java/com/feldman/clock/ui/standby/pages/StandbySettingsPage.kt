package com.feldman.clock.ui.standby.pages

import com.feldman.clock.R

import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.core.data.DataModel.*
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.activities.PreviewActivity
import com.feldman.clock.ui.standby.util.color.toComposeColor
import com.feldman.clock.settings.PreferencesKeys
import com.feldman.motion.SettingsScaffold
import androidx.core.content.edit
import com.feldman.clock.app.navigation.Dest
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.theme.isDarkTheme
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandbySettingsPage(
    showTitle: Boolean = true,
    onFabClick: ((() -> Unit)?) -> Unit,
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean
) {

    val context = LocalContext.current
    val isDark = isDarkTheme()
    val categoryChromeColor = SettingsCategoryColor.STANDBY.container(isDark)

    LaunchedEffect(Unit) {
        onFabClick {
            context.startActivity(
                Intent(
                    context,
                    PreviewActivity::class.java
                )
            )
        }
    }

    val prefs = remember {
        ClockApplication.getDefaultSharedPreferences(context)
    }
    val rotationOptions = listOf(
        RotationMode.Auto,
        RotationMode.Portrait,
        RotationMode.Landscape
    )

    class StandbySettingsState(prefs: SharedPreferences) {
        var currentStyle by mutableStateOf(SettingsDAO.getStandbyClockStyle(prefs))
        var currentRotation by mutableStateOf(RotationMode.from(SettingsDAO.getStandbyRotationMode(prefs)))
        var showSeconds by mutableStateOf(SettingsDAO.areStandbyClockSecondsDisplayed(prefs))
        var dynamicColor by mutableStateOf(SettingsDAO.getStandbyDynamicColor(prefs))
        var vibrantPalette by mutableStateOf(SettingsDAO.getStandbyVibrantPalette(prefs))
        var singleColorMode by mutableStateOf(SettingsDAO.getStandbySingleColorMode(prefs))
        var selectedColor by mutableStateOf(SettingsDAO.getStandbyClockColorPicker(prefs).toComposeColor())
        var selectedHourColor by mutableStateOf(SettingsDAO.getStandbyHourColorPicker(prefs).toComposeColor())
        var selectedMinuteColor by mutableStateOf(SettingsDAO.getStandbyMinuteColorPicker(prefs).toComposeColor())
        var selectedDateColor by mutableStateOf(SettingsDAO.getStandbyDateColorPicker(prefs).toComposeColor())
        var selectedNextAlarmColor by mutableStateOf(SettingsDAO.getStandbyNextAlarmColorPicker(prefs).toComposeColor())
        var showDate by mutableStateOf(SettingsDAO.getStandbyShowDate(prefs))
        var showNextAlarm by mutableStateOf(SettingsDAO.getStandbyShowNextAlarm(prefs))
        var dateFormat by mutableStateOf(SettingsDAO.getStandbyDateFormat(prefs))
        var dateSize by mutableFloatStateOf(SettingsDAO.getStandbyDateSizePercent(prefs).toFloat())
        var dateSpacing by mutableFloatStateOf(SettingsDAO.getStandbyDateSpacingDp(prefs).toFloat())

        var scale by mutableFloatStateOf(SettingsDAO.getStandbyScalePercent(prefs).toFloat())
        var clockWeight by mutableFloatStateOf(SettingsDAO.getStandbyClockWeightPercent(prefs).toFloat())
        var clockWidth by mutableFloatStateOf(SettingsDAO.getStandbyClockWidthPercent(prefs).toFloat())
        var automaticClockWidth by mutableStateOf(SettingsDAO.getStandbyAutomaticClockWidth(prefs))
        var clockRoundness by mutableFloatStateOf(SettingsDAO.getStandbyClockRoundnessPercent(prefs).toFloat())
        var timeSpacing by mutableFloatStateOf(SettingsDAO.getStandbyTimeSpacingDp(prefs).toFloat())
        var currentLayout by mutableStateOf(SettingsDAO.getStandbyPageLayout(prefs))
    }

    val state = remember { StandbySettingsState(prefs) }

    val options = listOf(
        ClockStyle.DIGITAL_SPLIT_12,
        ClockStyle.DIGITAL_SPLIT_24,
        ClockStyle.ANALOG
    )
    val isSplitClock = state.currentStyle == ClockStyle.DIGITAL_SPLIT_12 ||
        state.currentStyle == ClockStyle.DIGITAL_SPLIT_24

    SettingsScaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Standby") },
                navigationIcon = {
                    if (!isTab) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = categoryChromeColor
                ),
                actions = {
                    IconButton(onClick = { onNavigate(Dest.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        contentWindowInsets = WindowInsets(0)
    ) {

        //Clock Style
        title("Clock style")

        section {


            segmentedPickerItem(
                options = listOf(
                    "12h stacked",
                    "24h side",
                    "Analog"
                ),
                icons = listOf(
                    painterResource(R.drawable.ic_123),
                    painterResource(R.drawable.ic_123),
                    painterResource(R.drawable.ic_clock)
                ),
                selectedIndex = options.indexOf(state.currentStyle).coerceAtLeast(0),
                onSelected = { index ->
                    val newStyle = options[index]
                    state.currentStyle = newStyle

                    prefs.edit {
                        putInt(
                            PreferencesKeys.KEY_STANDBY_CLOCK_STYLE,
                            newStyle.ordinal
                        )
                    }
                }
            )

        }
        
        // Layout
        title("Layout")
        val layouts = listOf(
            StandbyPageLayout.PAGER,
            StandbyPageLayout.SPLIT
        )
        section {
            segmentedPickerItem(
                options = listOf(
                    "Pager",
                    "Split"
                ),
                icons = listOf(
                    painterResource(R.drawable.ic_auto_rotate),
                    painterResource(R.drawable.ic_horizontal)
                ),
                selectedIndex = layouts.indexOf(state.currentLayout).coerceAtLeast(0),
                onSelected = { index ->
                    val newLayout = layouts[index]
                    state.currentLayout = newLayout
                    SettingsDAO.setStandbyPageLayout(prefs, newLayout)
                }
            )
        }


        // ───────────────────── Display ─────────────────────
        title("Display")

        section {
            switchItem(
                title = "Show seconds",
                description = "Display seconds on the clock",
                visible = !isSplitClock,
                checked = state.showSeconds,
                onCheckedChange = { enabled ->
                    state.showSeconds = enabled
                    prefs.edit {
                        putBoolean(
                            PreferencesKeys.KEY_DISPLAY_STANDBY_CLOCK_SECONDS,
                            enabled
                        )
                    }
                }
            )
            switchItem(
                title = "Show day and date",
                description = "Dock the day and date below the time",
                visible = isSplitClock,
                checked = state.showDate,
                onCheckedChange = { enabled ->
                    state.showDate = enabled
                    prefs.edit { putBoolean(PreferencesKeys.KEY_STANDBY_SHOW_DATE, enabled) }
                }
            )
            switchItem(
                title = "Show next alarm",
                description = "Show the next scheduled alarm below the time",
                visible = isSplitClock,
                checked = state.showNextAlarm,
                onCheckedChange = { enabled ->
                    state.showNextAlarm = enabled
                    prefs.edit { putBoolean(PreferencesKeys.KEY_STANDBY_SHOW_NEXT_ALARM, enabled) }
                }
            )
        }

        // ───────────────────── Appearance ─────────────────────
        title("Appearance")
        section {


            segmentedPickerItem(
                options = listOf("Auto", "Portrait", "Landscape"),
                icons = listOf(
                    painterResource(R.drawable.ic_auto_rotate),
                    painterResource(R.drawable.ic_vertical),
                    painterResource(R.drawable.ic_horizontal)
                ),
                selectedIndex = rotationOptions.indexOf(state.currentRotation).coerceAtLeast(0),
                onSelected = { index ->
                    val newMode = rotationOptions[index]
                    state.currentRotation = newMode

                    prefs.edit {
                        putInt(
                            PreferencesKeys.KEY_STANDBY_ROTATION_MODE,
                            newMode.ordinal
                        )
                    }
                }
            )

            switchItem(
                title = "Single color mode",
                description = "Use one color for all digits instead of multiple colors",
                visible = state.currentStyle == ClockStyle.DIGITAL,
                checked = state.singleColorMode,
                onCheckedChange = { enabled ->
                    state.singleColorMode = enabled
                    prefs.edit {
                        putBoolean(
                            PreferencesKeys.KEY_STANDBY_SINGLE_COLOR_MODE,
                            enabled
                        )
                    }
                }
            )

        }

        if (!isSplitClock) {
            title("Color")
            section {
                colorPickerItem(
                    key = PreferencesKeys.KEY_STANDBY_CLOCK_COLOR_PICKER,
                    colors = TEXT_SWATCHES.map { it.second },
                    selectedIndex = if (state.dynamicColor) -1 else {
                        TEXT_SWATCHES.indexOfFirst { it.second == state.selectedColor }
                    },
                    onSelected = { index ->
                        val color = TEXT_SWATCHES[index].second
                        state.selectedColor = color
                        state.dynamicColor = false
                        prefs.edit {
                            putInt(PreferencesKeys.KEY_STANDBY_CLOCK_COLOR_PICKER, color.toArgb())
                            putBoolean(PreferencesKeys.KEY_STANDBY_DYNAMIC_COLOR, false)
                        }
                    },
                    dynamicColor = colorScheme.primary,
                    dynamicColorSelected = state.dynamicColor,
                    onDynamicColorSelected = {
                        state.dynamicColor = true
                        prefs.edit { putBoolean(PreferencesKeys.KEY_STANDBY_DYNAMIC_COLOR, true) }
                    },
                    dynamicColorIcon = painterResource(R.drawable.ic_palette)
                )
                switchItem(
                    title = "Vibrant colors",
                    description = "Use more saturated, expressive colors",
                    checked = state.vibrantPalette,
                    onCheckedChange = { enabled ->
                        state.vibrantPalette = enabled
                        prefs.edit {
                            putBoolean(PreferencesKeys.KEY_STANDBY_VIBRANT_PALETTE, enabled)
                        }
                    },
                    visible = !state.dynamicColor
                )
            }
        } else {
            title("Hour color")
            section {
                colorPickerItem(
                    key = PreferencesKeys.KEY_STANDBY_HOUR_COLOR_PICKER,
                    colors = TEXT_SWATCHES.map { it.second },
                    selectedIndex = TEXT_SWATCHES.indexOfFirst { it.second == state.selectedHourColor },
                    onSelected = { index ->
                        val color = TEXT_SWATCHES[index].second
                        state.selectedHourColor = color
                        prefs.edit { putInt(PreferencesKeys.KEY_STANDBY_HOUR_COLOR_PICKER, color.toArgb()) }
                    }
                )
            }

            title("Minute color")
            section {
                colorPickerItem(
                    key = PreferencesKeys.KEY_STANDBY_MINUTE_COLOR_PICKER,
                    colors = TEXT_SWATCHES.map { it.second },
                    selectedIndex = TEXT_SWATCHES.indexOfFirst { it.second == state.selectedMinuteColor },
                    onSelected = { index ->
                        val color = TEXT_SWATCHES[index].second
                        state.selectedMinuteColor = color
                        prefs.edit { putInt(PreferencesKeys.KEY_STANDBY_MINUTE_COLOR_PICKER, color.toArgb()) }
                    }
                )
            }

            if (state.showDate) {
                title("Day and date")
                section {
                    dropdownItem(
                        label = "Format",
                        options = STANDBY_DATE_FORMATS.map { it.first },
                        selected = STANDBY_DATE_FORMATS
                            .firstOrNull { it.second == state.dateFormat }
                            ?.first
                            ?: STANDBY_DATE_FORMATS.first().first,
                        onSelected = { label ->
                            state.dateFormat = STANDBY_DATE_FORMATS
                                .firstOrNull { it.first == label }
                                ?.second
                                ?: STANDBY_DATE_FORMATS.first().second
                            prefs.edit {
                                putString(PreferencesKeys.KEY_STANDBY_DATE_FORMAT, state.dateFormat)
                            }
                        }
                    )
                    colorPickerItem(
                        key = PreferencesKeys.KEY_STANDBY_DATE_COLOR_PICKER,
                        colors = TEXT_SWATCHES.map { it.second },
                        selectedIndex = TEXT_SWATCHES.indexOfFirst { it.second == state.selectedDateColor },
                        onSelected = { index ->
                            val color = TEXT_SWATCHES[index].second
                            state.selectedDateColor = color
                            prefs.edit { putInt(PreferencesKeys.KEY_STANDBY_DATE_COLOR_PICKER, color.toArgb()) }
                        }
                    )
                    item {
                        ClockAppearanceSlider(
                            title = "Date size",
                            value = state.dateSize,
                            valueRange = 60f..140f,
                            onValueChange = { state.dateSize = it },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(PreferencesKeys.KEY_STANDBY_DATE_SIZE_PERCENT, state.dateSize.toInt())
                                }
                            }
                        )
                    }
                    item {
                        ClockAppearanceSlider(
                            title = "Space below time",
                            value = state.dateSpacing,
                            valueRange = 0f..24f,
                            suffix = " dp",
                            onValueChange = { state.dateSpacing = it },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(PreferencesKeys.KEY_STANDBY_DATE_SPACING_DP, state.dateSpacing.toInt())
                                }
                            }
                        )
                    }
                }
            }

            if (state.showNextAlarm) {
                title("Next alarm")
                section {
                    colorPickerItem(
                        key = PreferencesKeys.KEY_STANDBY_NEXT_ALARM_COLOR_PICKER,
                        colors = TEXT_SWATCHES.map { it.second },
                        selectedIndex = TEXT_SWATCHES.indexOfFirst { it.second == state.selectedNextAlarmColor },
                        onSelected = { index ->
                            val color = TEXT_SWATCHES[index].second
                            state.selectedNextAlarmColor = color
                            prefs.edit {
                                putInt(PreferencesKeys.KEY_STANDBY_NEXT_ALARM_COLOR_PICKER, color.toArgb())
                            }
                        }
                    )
                }
            }
        }


        // ───────────────────── Split clock appearance ─────────────────────
        if (isSplitClock) {

            title("Clock appearance")

            section {
                item {
                    ClockAppearanceSlider(
                        title = "Overall size",
                        value = state.scale,
                        valueRange = 50f..150f,
                        onValueChange = { state.scale = it },
                        onValueChangeFinished = {
                            prefs.edit { putInt(PreferencesKeys.KEY_STANDBY_SCALE_PERCENT, state.scale.toInt()) }
                        }
                    )
                }
                item {
                    ClockAppearanceSlider(
                        title = "Time spacing",
                        value = state.timeSpacing,
                        valueRange = 0f..24f,
                        suffix = " dp",
                        onValueChange = { state.timeSpacing = it },
                        onValueChangeFinished = {
                            prefs.edit {
                                putInt(PreferencesKeys.KEY_STANDBY_TIME_SPACING_DP, state.timeSpacing.toInt())
                            }
                        }
                    )
                }
                item {
                    ClockAppearanceSlider(
                        title = "Stroke weight",
                        value = state.clockWeight,
                        valueRange = 70f..130f,
                        onValueChange = { state.clockWeight = it },
                        onValueChangeFinished = {
                            prefs.edit {
                                putInt(PreferencesKeys.KEY_STANDBY_CLOCK_WEIGHT_PERCENT, state.clockWeight.toInt())
                            }
                        }
                    )
                }
                switchItem(
                    title = "Automatic width",
                    description = "Fit the font width to the available clock space",
                    checked = state.automaticClockWidth,
                    onCheckedChange = { enabled ->
                        state.automaticClockWidth = enabled
                        prefs.edit {
                            putBoolean(PreferencesKeys.KEY_STANDBY_CLOCK_WIDTH_AUTO, enabled)
                        }
                    }
                )
                if (!state.automaticClockWidth) {
                    segmentedPickerItem(
                        options = listOf("Thin", "Normal", "Wide"),
                        icons = listOf(
                            painterResource(R.drawable.ic_text_decrease),
                            painterResource(R.drawable.sample_text_icon),
                            painterResource(R.drawable.ic_text_increase)
                        ),
                        selectedIndex = when {
                            state.clockWidth < 85f -> 0
                            state.clockWidth < 145f -> 1
                            else -> 2
                        },
                        onSelected = { index ->
                            state.clockWidth = listOf(50f, 100f, 190f)[index]
                            prefs.edit {
                                putInt(PreferencesKeys.KEY_STANDBY_CLOCK_WIDTH_PERCENT, state.clockWidth.toInt())
                            }
                        }
                    )
                    item {
                        ClockAppearanceSlider(
                            title = "Advanced width",
                            value = state.clockWidth,
                            valueRange = 25f..151f,
                            suffix = "",
                            onValueChange = { state.clockWidth = it },
                            onValueChangeFinished = {
                                prefs.edit {
                                    putInt(PreferencesKeys.KEY_STANDBY_CLOCK_WIDTH_PERCENT, state.clockWidth.toInt())
                                }
                            }
                        )
                    }
                }
                item {
                    ClockAppearanceSlider(
                        title = "Roundness",
                        value = state.clockRoundness,
                        valueRange = 0f..200f,
                        onValueChange = { state.clockRoundness = it },
                        onValueChangeFinished = {
                            prefs.edit {
                                putInt(PreferencesKeys.KEY_STANDBY_CLOCK_ROUNDNESS_PERCENT, state.clockRoundness.toInt())
                            }
                        }
                    )
                }
            }
        }
        item {
            Spacer(Modifier.height(100.dp))
        }
    }
}

@Composable
private fun ClockAppearanceSlider(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    suffix: String = "%",
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Slider(
            value = value,
            valueRange = valueRange,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished
        )
        Text("${value.toInt()}$suffix")
    }
}

private val STANDBY_DATE_FORMATS = listOf(
    "Fri, Aug 21" to "EEE, MMM d",
    "Friday, August 21" to "EEEE, MMMM d",
    "Fri 21 August" to "EEE d MMMM",
    "Fri • 21/08" to "EEE • dd/MM",
    "Fri • 08/21" to "EEE • MM/dd",
    "2026-08-21" to "yyyy-MM-dd"
)

val TEXT_SWATCHES = listOf(
    "White" to Color.White,
    "Periwinkle" to Color(0xFFB8C4FF),
    "Red" to Color(0xFFFF5252),
    "Orange" to Color(0xFFFF6D00),
    "Yellow" to Color(0xFFFFD600),
    "Green" to Color(0xFF00E676),
    "Mint" to Color(0xFF69F0AE),
    "Teal" to Color(0xFF64FFDA),
    "Cyan" to Color(0xFF00E5FF),
    "Light Blue" to Color(0xFF40C4FF),
    "Blue" to Color(0xFF2962FF),
    "Indigo" to Color(0xFF8C9EFF),
    "Purple" to Color(0xFF7C4DFF),
    "Lavender" to Color(0xFFB388FF),
    "Magenta" to Color(0xFFE040FB),
    "Pink" to Color(0xFFFF80AB),
    "Coral" to Color(0xFFFF8A80),
    "Amber" to Color(0xFFFFAB40)
)

fun weightLabel(w: Int): String = when (w) {
    100 -> "100 — Thin"
    200 -> "200 — ExtraLight"
    300 -> "300 — Light"
    400 -> "400 — Normal"
    500 -> "500 — Medium"
    600 -> "600 — SemiBold"
    700 -> "700 — Bold"
    800 -> "800 — ExtraBold"
    900 -> "900 — Black"
    else -> "$w"
}

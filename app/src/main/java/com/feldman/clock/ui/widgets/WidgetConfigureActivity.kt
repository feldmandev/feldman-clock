package com.feldman.clock.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.AppTheme
import com.feldman.motion.SettingsScaffold

/**
 * Configuration screen for both clock widgets, reached by long-pressing the widget on the home
 * screen. Both providers declare `configuration_optional`, so a widget is placed with sensible
 * defaults and this screen is only ever opened to change them.
 *
 * Edits are written and pushed to the widget as they are made; there is no separate save step.
 */
class WidgetConfigureActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        // Report success up front: the widget already exists, so backing out of this screen should
        // keep it rather than remove it.
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        )

        val providerName = AppWidgetManager.getInstance(this)
            ?.getAppWidgetInfo(appWidgetId)
            ?.provider
            ?.className

        val isAnalog = providerName == AnalogClockWidgetProvider::class.java.name
        val screenTitle = if (isAnalog) "Analog Widget" else "Digital Widget"

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    WidgetConfigureScreen(
                        appWidgetId = appWidgetId,
                        isAnalog = isAnalog,
                        screenTitle = screenTitle,
                        onBack = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetConfigureScreen(
    appWidgetId: Int,
    isAnalog: Boolean,
    screenTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    val defaultDateFormat = if (isAnalog) "EEE d" else "EEE, MMM d"

    var dialShape by remember {
        mutableStateOf(WidgetPrefs.dialShape(context, appWidgetId))
    }
    var backgroundShape by remember {
        mutableStateOf(WidgetPrefs.backgroundShape(context, appWidgetId))
    }
    var showDate by remember {
        mutableStateOf(WidgetPrefs.showDate(context, appWidgetId))
    }
    var dateFormat by remember {
        mutableStateOf(WidgetPrefs.dateFormat(context, appWidgetId, defaultDateFormat))
    }
    var showSecondHand by remember {
        mutableStateOf(WidgetPrefs.showSecondHand(context, appWidgetId))
    }
    var showNextAlarm by remember {
        mutableStateOf(WidgetPrefs.showNextAlarm(context, appWidgetId))
    }
    var wideText by remember {
        mutableStateOf(WidgetPrefs.wideText(context, appWidgetId))
    }
    var automaticWidth by remember {
        mutableStateOf(WidgetPrefs.automaticWidth(context, appWidgetId))
    }
    var clockLayout by remember {
        mutableStateOf(WidgetPrefs.clockLayout(context, appWidgetId))
    }

    fun push() = ClockWidgetRenderer.refresh(context, appWidgetId)

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = screenTitle,
                chromeColor = SettingsCategoryColor.CLOCK.container(isDarkTheme()),
                onBack = onBack
            )
        }
    ) {
        title("Appearance")
        section {
            if (isAnalog) {
                dropdownItem(
                    label = "Dial shape",
                    options = WidgetPrefs.DialShape.labels,
                    selected = dialShape.label,
                    onSelected = { label ->
                        dialShape = WidgetPrefs.DialShape.fromLabel(label)
                        WidgetPrefs.setDialShape(context, appWidgetId, dialShape)
                        push()
                    }
                )
                switchItem(
                    title = "Show second hand",
                    checked = showSecondHand,
                    onCheckedChange = {
                        showSecondHand = it
                        WidgetPrefs.setShowSecondHand(context, appWidgetId, it)
                        push()
                    }
                )
            } else {
                dropdownItem(
                    label = "Background shape",
                    options = WidgetPrefs.BackgroundShape.labels,
                    selected = backgroundShape.label,
                    onSelected = { label ->
                        backgroundShape = WidgetPrefs.BackgroundShape.fromLabel(label)
                        WidgetPrefs.setBackgroundShape(context, appWidgetId, backgroundShape)
                        push()
                    }
                )
                dropdownItem(
                    label = "Clock design",
                    options = WidgetPrefs.ClockLayout.labels,
                    selected = clockLayout.label,
                    onSelected = { label ->
                        clockLayout = WidgetPrefs.ClockLayout.fromLabel(label)
                        WidgetPrefs.setClockLayout(context, appWidgetId, clockLayout)
                        push()
                    }
                )
                val isSplitClock = clockLayout == WidgetPrefs.ClockLayout.SPLIT_12 ||
                    clockLayout == WidgetPrefs.ClockLayout.SPLIT_24
                if (isSplitClock) {
                    switchItem(
                        title = "Automatic width",
                        description = "Fit the font width when the widget is resized",
                        checked = automaticWidth,
                        onCheckedChange = {
                            automaticWidth = it
                            WidgetPrefs.setAutomaticWidth(context, appWidgetId, it)
                            push()
                        }
                    )
                }
                if (!isSplitClock || !automaticWidth) {
                    switchItem(
                        title = "Wide text",
                        checked = wideText,
                        onCheckedChange = {
                            wideText = it
                            WidgetPrefs.setWideText(context, appWidgetId, it)
                            push()
                        }
                    )
                }
            }
        }

        title("Date")
        section {
            switchItem(
                title = "Show date",
                checked = showDate,
                onCheckedChange = {
                    showDate = it
                    WidgetPrefs.setShowDate(context, appWidgetId, it)
                    push()
                }
            )
            if (showDate) {
                dropdownItem(
                    label = "Date format",
                    options = WidgetPrefs.DateFormats.map { it.first },
                    selected = WidgetPrefs.DateFormats
                        .firstOrNull { it.second == dateFormat }
                        ?.first
                        ?: WidgetPrefs.DateFormats.first().first,
                    onSelected = { label ->
                        dateFormat = WidgetPrefs.DateFormats
                            .firstOrNull { it.first == label }
                            ?.second
                            ?: defaultDateFormat
                        WidgetPrefs.setDateFormat(context, appWidgetId, dateFormat)
                        push()
                    }
                )
            }
        }

        if (!isAnalog) {
            title("Next Alarm")
            section {
                switchItem(
                    title = "Show next alarm",
                    checked = showNextAlarm,
                    onCheckedChange = {
                        showNextAlarm = it
                        WidgetPrefs.setShowNextAlarm(context, appWidgetId, it)
                        push()
                    }
                )
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

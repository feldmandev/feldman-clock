package com.feldman.clock.ui.standby.screensavers

import com.feldman.clock.R

import android.app.AlarmManager
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.core.data.DataModel.ClockStyle
import com.feldman.clock.core.data.DataModel.RotationMode
import com.feldman.clock.core.data.DataModel.StandbyPageLayout
import com.feldman.clock.core.data.standby.UiColors
import com.feldman.clock.core.util.AlarmUtils
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.util.color.toComposeColor
import com.feldman.clock.ui.standby.widgets.ClockAppWidgetHost
import com.feldman.clock.ui.standby.widgets.ClockWidgetHostActivity
import com.feldman.clock.ui.standby.widgets.KEY_STANDBY_WIDGETS_CHANGED_AT
import com.feldman.clock.ui.standby.widgets.WidgetData
import com.feldman.clock.ui.standby.widgets.WidgetHostLayoutMode
import com.feldman.clock.ui.standby.widgets.WidgetHostScreen
import com.feldman.clock.ui.standby.widgets.loadWidgetsFromPrefs
import com.feldman.clock.ui.standby.widgets.saveWidgetsToPrefs
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.min

private const val APPWIDGET_HOST_ID = 1024
private const val GRID_UNIT_DP = 36
private const val GRID_PADDING_DP = 12

@Composable
fun ClockStandby(
    onDismiss: () -> Unit,
    isInteractive: Boolean = true,
    allowEdit: Boolean = true,
    appWidgetHost: ClockAppWidgetHost? = null,
    appWidgetManager: AppWidgetManager? = null
) {
    val context = LocalContext.current
    val prefs = remember(context) { ClockApplication.getDefaultSharedPreferences(context) }
    val manager = remember(appWidgetManager) {
        appWidgetManager ?: AppWidgetManager.getInstance(context)
    }
    val host = remember(appWidgetHost) {
        appWidgetHost ?: ClockAppWidgetHost(context, APPWIDGET_HOST_ID)
    }

    DisposableEffect(host, appWidgetHost) {
        if (appWidgetHost == null) host.startListening()
        onDispose {
            if (appWidgetHost == null) host.stopListening()
        }
    }

    val widgets = remember {
        mutableStateListOf<WidgetData>().apply { addAll(loadWidgetsFromPrefs(context)) }
    }
    fun reloadWidgets() {
        widgets.clear()
        widgets.addAll(loadWidgetsFromPrefs(context))
    }
    fun persistWidgets() = saveWidgetsToPrefs(context, widgets)

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_STANDBY_WIDGETS_CHANGED_AT) reloadWidgets()
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val rotationPref = RotationMode.from(SettingsDAO.getStandbyRotationMode(prefs))
    val rotation = remember(context.resources.configuration.orientation, rotationPref) {
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        when {
            rotationPref == RotationMode.Portrait && width > height -> -90f
            rotationPref == RotationMode.Landscape && width < height -> 90f
            else -> 0f
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .then(if (rotation != 0f) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
    ) {
        RotatedStandbyPage(rotation) {
            val baseConfig = LocalConfiguration.current
            val effectiveConfig = remember(baseConfig, rotation) {
                if (rotation == 0f) baseConfig else Configuration(baseConfig).apply {
                    orientation = if (baseConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        Configuration.ORIENTATION_PORTRAIT
                    } else {
                        Configuration.ORIENTATION_LANDSCAPE
                    }
                    screenWidthDp = baseConfig.screenHeightDp
                    screenHeightDp = baseConfig.screenWidthDp
                }
            }

            androidx.compose.runtime.CompositionLocalProvider(LocalConfiguration provides effectiveConfig) {
                val dismiss = if (isInteractive) onDismiss else ({})
                val widgetPage: @Composable (WidgetHostLayoutMode) -> Unit = { layoutMode ->
                    WidgetHostScreen(
                        onAddWidgetClick = {
                            context.startActivity(
                                Intent(context, ClockWidgetHostActivity::class.java)
                                    .putExtra(ClockWidgetHostActivity.EXTRA_START_PICKER, true)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        widgets = widgets,
                        appWidgetHost = host,
                        appWidgetManager = manager,
                        onDeleteWidget = { widget ->
                            widgets.removeAll { it.appWidgetId == widget.appWidgetId }
                            host.deleteAppWidgetId(widget.appWidgetId)
                            persistWidgets()
                        },
                        onResizeWidget = { widget, spanX, spanY ->
                            val widthDp = GRID_UNIT_DP * spanX - GRID_PADDING_DP
                            val heightDp = GRID_UNIT_DP * spanY.coerceAtLeast(2) - GRID_PADDING_DP
                            manager.updateAppWidgetOptions(
                                widget.appWidgetId,
                                appWidgetOptions(widthDp, heightDp)
                            )
                            persistWidgets()
                        },
                        onMoveWidget = { _, _, _ -> persistWidgets() },
                        onConfigureWidget = { widget ->
                            context.startActivity(
                                Intent(context, ClockWidgetHostActivity::class.java)
                                    .putExtra(
                                        ClockWidgetHostActivity.EXTRA_CONFIGURE_WIDGET_ID,
                                        widget.appWidgetId
                                    )
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            )
                        },
                        onWidgetsChanged = ::persistWidgets,
                        showAddButton = allowEdit,
                        onDismiss = dismiss,
                        layoutMode = layoutMode
                    )
                }

                when (SettingsDAO.getStandbyPageLayout(prefs)) {
                    StandbyPageLayout.SPLIT -> Row(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            StandbyClockFace(onDismiss = dismiss, isInteractive = isInteractive)
                        }
                        Box(Modifier.weight(1f).fillMaxSize()) {
                            widgetPage(WidgetHostLayoutMode.SPLIT_VERTICAL_PAGER)
                        }
                    }
                    else -> {
                        val pagerState = rememberPagerState(pageCount = { 2 })
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            if (page == 0) {
                                StandbyClockFace(onDismiss = dismiss, isInteractive = isInteractive)
                            } else {
                                widgetPage(WidgetHostLayoutMode.GRID)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StandbyClockFace(onDismiss: () -> Unit, isInteractive: Boolean) {
    val context = LocalContext.current
    val prefs = remember(context) { ClockApplication.getDefaultSharedPreferences(context) }
    val clockColor = SettingsDAO.getStandbyClockColorPicker(prefs).toComposeColor()
    val scheme = UiColors(
        background = Color.Black,
        primary = clockColor,
        secondary = MaterialTheme.colorScheme.secondary,
        tertiary = MaterialTheme.colorScheme.tertiary,
        surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    )
    val style = SettingsDAO.getStandbyClockStyle(prefs)
    val hourColor = SettingsDAO.getStandbyHourColorPicker(prefs).toComposeColor()
    val minuteColor = SettingsDAO.getStandbyMinuteColorPicker(prefs).toComposeColor()
    val dateColor = SettingsDAO.getStandbyDateColorPicker(prefs).toComposeColor()
    val nextAlarmColor = SettingsDAO.getStandbyNextAlarmColorPicker(prefs).toComposeColor()
    val showDate = SettingsDAO.getStandbyShowDate(prefs)
    val showNextAlarm = SettingsDAO.getStandbyShowNextAlarm(prefs)
    val dateScale = SettingsDAO.getStandbyDateSizePercent(prefs) / 100f
    val dateSpacing = SettingsDAO.getStandbyDateSpacingDp(prefs).toFloat()
    val clockWeight = SettingsDAO.getStandbyClockWeightPercent(prefs) / 100f
    val clockWidth = SettingsDAO.getStandbyClockWidthPercent(prefs) / 100f
    val automaticClockWidth = SettingsDAO.getStandbyAutomaticClockWidth(prefs)
    val clockRoundness = SettingsDAO.getStandbyClockRoundnessPercent(prefs) / 100f
    val timeSpacing = SettingsDAO.getStandbyTimeSpacingDp(prefs).toFloat()
    val dateFormatter = remember(prefs) {
        DateTimeFormatter.ofPattern(SettingsDAO.getStandbyDateFormat(prefs), Locale.getDefault())
    }
    val showSeconds = SettingsDAO.areStandbyClockSecondsDisplayed(prefs)
    val weight = SettingsDAO.getStandbyFontWeight(prefs)
    val scale = SettingsDAO.getStandbyScalePercent(prefs).coerceIn(50, 150) / 100f
    val singleColor = SettingsDAO.getStandbySingleColorMode(prefs)
    var time by remember { mutableStateOf(LocalTime.now()) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var nextAlarm by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(showSeconds) {
        while (true) {
            time = LocalTime.now()
            date = LocalDate.now()
            delay(if (showSeconds) 250L else 1_000L)
        }
    }

    DisposableEffect(context, showNextAlarm) {
        if (!showNextAlarm) {
            nextAlarm = null
            onDispose { }
        } else {
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    nextAlarm = AlarmUtils.getNextAlarmClockText(context)
                }
            }
            val filter = IntentFilter().apply {
                addAction(AlarmUtils.ACTION_NEXT_ALARM_CHANGED_BY_CLOCK)
                addAction(AlarmManager.ACTION_NEXT_ALARM_CLOCK_CHANGED)
            }
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            nextAlarm = AlarmUtils.getNextAlarmClockText(context)
            onDispose { context.unregisterReceiver(receiver) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scheme.background)
            .then(
                if (isInteractive) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
                } else Modifier
            ),
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            ClockStyle.DIGITAL -> DigitalClock(
                time = time,
                scheme = scheme,
                weight = weight,
                showSeconds = showSeconds,
                userScale = scale,
                isSingleColorMode = singleColor
            )
            ClockStyle.DIGITAL_SPLIT_12, ClockStyle.DIGITAL_SPLIT_24 -> SplitDigitalClock(
                time = time,
                hourColor = hourColor,
                minuteColor = minuteColor,
                dateText = date.format(dateFormatter),
                dateColor = dateColor,
                showDate = showDate,
                nextAlarmText = nextAlarm,
                nextAlarmColor = nextAlarmColor,
                dateScale = dateScale,
                dateSpacing = dateSpacing,
                clockWeight = clockWeight,
                clockWidth = clockWidth,
                automaticWidth = automaticClockWidth,
                clockRoundness = clockRoundness,
                timeSpacing = timeSpacing,
                is24Hour = style == ClockStyle.DIGITAL_SPLIT_24,
                userScale = scale
            )
            else -> AnalogClock(
                time = time,
                scheme = scheme,
                showSeconds = showSeconds
            )
        }
    }
}

private fun appWidgetOptions(widthDp: Int, heightDp: Int): Bundle = Bundle().apply {
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
    putInt(
        AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
        AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
    )
}

@Composable
private fun RotatedStandbyPage(rotation: Float, content: @Composable () -> Unit) {
    SubcomposeLayout(Modifier.fillMaxSize()) { constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val rotating = rotation != 0f
        val childConstraints = if (!rotating) constraints else Constraints(
            minWidth = 0,
            minHeight = 0,
            maxWidth = height,
            maxHeight = width
        )
        val placeables = subcompose("standby", content).map { it.measure(childConstraints) }
        val childWidth = placeables.maxOfOrNull { it.width } ?: 0
        val childHeight = placeables.maxOfOrNull { it.height } ?: 0
        val scale = if (!rotating || childWidth == 0 || childHeight == 0) 1f else min(
            width / childHeight.toFloat(),
            height / childWidth.toFloat()
        )

        layout(width, height) {
            val centerX = width / 2
            val centerY = height / 2
            placeables.forEach { placeable ->
                placeable.placeWithLayer(
                    centerX - placeable.width / 2,
                    centerY - placeable.height / 2
                ) {
                    rotationZ = rotation
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin.Center
                }
            }
        }
    }
}

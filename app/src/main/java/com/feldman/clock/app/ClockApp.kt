package com.feldman.clock.app

import com.feldman.clock.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.graphics.ExperimentalAnimationGraphicsApi
import androidx.compose.animation.graphics.res.animatedVectorResource
import androidx.compose.animation.graphics.res.rememberAnimatedVectorPainter
import androidx.compose.animation.graphics.vector.AnimatedImageVector
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.zIndex
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.ui.LocalActivityIntent
import com.feldman.clock.ui.LocalSetFabClick
import com.feldman.clock.ui.LocalShowSnackbar
import com.feldman.clock.ui.ShowSnackbar
import android.content.Intent
import androidx.compose.runtime.LaunchedEffect
import com.feldman.clock.alarm.AlarmConstants
import com.feldman.clock.timer.TimerConstants
import com.feldman.clock.timer.TimerService
import com.feldman.clock.stopwatch.StopwatchService
import android.provider.AlarmClock
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.feldman.clock.core.util.NotificationPermissionHelper
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.ui.settings.settingsChromeColor
import com.feldman.motion.MotionLevel
import com.feldman.motion.MotionThemeRepository
import com.feldman.motion.rememberExpressiveDesign
import com.feldman.motion.MotionBottomBarHost
import com.feldman.motion.FloatingToolbarDefaults
import com.feldman.motion.LocalBottomBarHeight
import com.feldman.motion.MotionDest
import com.feldman.motion.rememberMotionNavigationState
import com.feldman.motion.MotionNavigator
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

// Navigation imports
import com.feldman.clock.app.navigation.Dest
import com.feldman.clock.app.navigation.DestBackStack
import com.feldman.clock.app.navigation.AppNavHost
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.NavigationEventDispatcherOwner
import com.feldman.clock.core.util.isScreenWide
import kotlinx.coroutines.flow.first
import com.feldman.motion.MotionNavigationBar

@Parcelize
@Serializable
private data object BottomBarHostDest : MotionDest {
    @IgnoredOnParcel
    @Transient
    override val label = "Clock"
    @IgnoredOnParcel
    @Transient
    override val filledIcon = R.drawable.ic_tab_clock_static
    @IgnoredOnParcel
    @Transient
    override val outlineIcon = R.drawable.ic_tab_clock_static

    @Composable
    override fun Content(
        onNavigate: MotionNavigator,
        onBack: () -> Unit,
        searchQuery: String,
        onFabAction: ((() -> Unit) -> Unit) -> Unit
    ) = Unit
}

private fun notificationStartDestination(intent: Intent?): Dest? = when (intent?.action) {
    MainActivity.ACTION_SHOW_CLOCK -> Dest.Clock
    TimerService.ACTION_SHOW_TIMER,
    AlarmClock.ACTION_SHOW_TIMERS,
    AlarmClock.ACTION_SET_TIMER -> Dest.Timer
    StopwatchService.ACTION_SHOW_STOPWATCH -> Dest.Stopwatch
    else -> null
}

// Keep Screen for compatibility if needed, but unused in new logic
sealed class Screen(val route: String, val title: String, val icon: Int? = null, val animatedIcon: Int? = null) {
    object Alarm : Screen("alarm", "Alarm", R.drawable.ic_tab_alarm_static, R.drawable.ic_tab_alarm_avd)
    object Clock : Screen("clock", "Clock", R.drawable.ic_tab_clock_static, R.drawable.ic_tab_clock_avd)
    object Timer : Screen("timer", "Timer", R.drawable.ic_tab_timer_static, R.drawable.ic_tab_timer_avd)
    object Stopwatch : Screen("stopwatch", "Stopwatch", R.drawable.ic_tab_stopwatch_static, R.drawable.ic_tab_stopwatch_avd)
    object Settings : Screen("settings", "Settings")
    object ExpiredTimers : Screen("expired_timers", "Expired Timers")
}

data class FabConfig(
    val visible: Boolean = false,
    val icon: ImageVector = Icons.Default.Add,
    val contentDescription: String = "",
    val onClick: (() -> Unit)? = null
)

val LocalFabConfig: ProvidableCompositionLocal<MutableState<FabConfig>> =
    staticCompositionLocalOf { error("LocalFabConfig not provided") }

enum class HourglassPhase {
    Idle,
    Rotating,
    Falling
}
private const val SAND_START_ANGLE = 175f

/**
 * How long snackbars stay on screen. Material's built-in Short (4s) and Long (10s) both linger
 * longer than this app wants, and neither is configurable, so [ShowSnackbar] shows snackbars
 * with [SnackbarDuration.Indefinite] and dismisses them on these timers instead.
 */
private const val SNACKBAR_DURATION_MS = 2_000L

/** Slightly longer when there is an action, so it stays tappable. */
private const val SNACKBAR_ACTION_DURATION_MS = 3_500L

@Composable
fun HourglassIcon(
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp
) {
    // Start the sand a bit before we reach 180°
    val sandStartAngle = 175f

    var wasSelected by remember { mutableStateOf(false) }
    var sandStarted by remember { mutableStateOf(false) }

    val rotation = remember { Animatable(0f) }        // 0 -> 180 (spring)
    val sandProgress = remember { Animatable(0f) }    // 0 -> 1 (linear fill/drain)

    LaunchedEffect(selected) {
        // ✅ Entering Timer: animate
        if (selected && !wasSelected) {
            sandStarted = false
            sandProgress.snapTo(0f)

            // rotate (keep going even after sand starts)
            launch {
                rotation.snapTo(0f)
                rotation.animateTo(
                    targetValue = 180f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                )
            }

            // start sand when close enough to 180°
            launch {
                snapshotFlow { rotation.value }
                    .first { it >= sandStartAngle }

                sandStarted = true

                sandProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 100,
                        easing = LinearEasing
                    )
                )
            }
        }

        // ⛔ Leaving Timer: snap (no animation)
        if (!selected && wasSelected) {
            sandStarted = false
            sandProgress.snapTo(0f)
            rotation.snapTo(0f)
        }

        wasSelected = selected
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        // 🟦 Not selected: frame + bottom sand (NO animation)
        if (!selected) {
            Icon(
                painter = painterResource(R.drawable.ic_frame),
                contentDescription = null
            )
            Icon(
                painter = painterResource(R.drawable.ic_sand_bottom),
                contentDescription = null
            )
            return@Box
        }

        // 🟧 Selected: rotate everything
        Box(
            modifier = Modifier.graphicsLayer {
                rotationZ = rotation.value
                transformOrigin = TransformOrigin.Center
            },
            contentAlignment = Alignment.Center
        ) {
            // During early rotation: use the connected drawable and hide other sand
            if (!sandStarted) {
                Icon(
                    painter = painterResource(R.drawable.ic_frame_sand_bottom),
                    contentDescription = null
                )
            } else {
                // After sand starts: show frame + sand transfer
                Icon(
                    painter = painterResource(R.drawable.ic_frame),
                    contentDescription = null
                )

                // This was originally the bottom sand, but after a 180° flip it becomes the "top" sand.
                Icon(
                    painter = painterResource(R.drawable.ic_sand_bottom),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer {
                        scaleY = 1f - sandProgress.value   // drain
                        transformOrigin = TransformOrigin.Center
                    }
                )

                // This was originally the top sand, but after the flip it becomes the "bottom" sand.
                Icon(
                    painter = painterResource(R.drawable.ic_sand_top),
                    contentDescription = null,
                    modifier = Modifier.graphicsLayer {
                        scaleY = sandProgress.value        // fill
                        transformOrigin = TransformOrigin.Center
                    }
                )
            }
        }
    }
}




@OptIn(ExperimentalAnimationGraphicsApi::class)
@Composable
private fun ClockBottomBarItems(
    destinations: List<Dest>,
    currentDestination: Dest,
    onNavigate: (Dest) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        destinations.forEach { destination ->
            val selected = currentDestination == destination
            Surface(
                onClick = { if (!selected) onNavigate(destination) },
                shape = RoundedCornerShape(24.dp),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                contentColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            ) {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (destination) {
                        Dest.Timer -> HourglassIcon(selected = selected, size = 20.dp)
                        else -> {
                            val icon = if (selected && destination.animatedIcon != null) {
                                val animatedIcon = AnimatedImageVector.animatedVectorResource(destination.animatedIcon!!)
                                rememberAnimatedVectorPainter(animatedIcon, true)
                            } else {
                                painterResource(destination.outlineIcon ?: destination.filledIcon ?: R.drawable.ic_tab_clock_static)
                            }
                            Icon(
                                painter = icon,
                                contentDescription = destination.label,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn() + expandHorizontally(),
                        exit = fadeOut() + shrinkHorizontally()
                    ) {
                        Text(
                            text = destination.label,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationGraphicsApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ClockApp(intent: Intent? = null, startDestination: Dest = Dest.Alarm) {
    val context = LocalContext.current

    val isWide = isScreenWide()
    val expressiveDesign = rememberExpressiveDesign()
    val motionLevel by remember(context) { MotionThemeRepository(context).motionLevel }
        .collectAsState(initial = MotionLevel.MEDIUM)

    val bottomNavItems = remember {
        listOf(Dest.Alarm, Dest.Clock, Dest.Timer, Dest.Stopwatch)
    }
    val backStack = remember { DestBackStack(notificationStartDestination(intent) ?: startDestination) }
    val navigationState = rememberMotionNavigationState(
        backStack = backStack.backStack,
        topLevelDestinations = bottomNavItems,
        motionLevel = motionLevel,
        showsBottomBar = { destination ->
            bottomNavItems.any { it::class == destination::class }
        }
    )
    val navHost = remember(backStack, navigationState) {
        movableContentOf {
            AppNavHost(
                backStack = backStack,
                onNavigate = { destination ->
                    val completesSetup = destination == Dest.Alarm &&
                        backStack.backStack.lastOrNull() == Dest.SetupDone
                    if (completesSetup) {
                        backStack.reset(Dest.Alarm, useFadeTransition = true)
                    } else {
                        backStack.navigate(destination)
                    }
                },
                navigationState = navigationState,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var bottomBarHeight by remember { mutableStateOf(0.dp) }
    val scope = rememberCoroutineScope()

    var onFabClick: (() -> Unit)? by remember { mutableStateOf(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(intent) {
        if (intent != null) {
            when (intent.action) {
                MainActivity.ACTION_SHOW_CLOCK -> {
                    backStack.reset(Dest.Clock)
                }
                TimerConstants.ACTION_SHOW_EXPIRED_TIMERS -> {
                    backStack.navigate(Dest.ExpiredTimers)
                }
                TimerService.ACTION_SHOW_TIMER -> {
                    backStack.navigateTop(Dest.Timer)
                }
                AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_DISMISS_ALARM, AlarmClock.ACTION_SNOOZE_ALARM -> {
                    backStack.navigateTop(Dest.Alarm)
                }
                AlarmClock.ACTION_SHOW_TIMERS, AlarmClock.ACTION_SET_TIMER -> {
                    backStack.navigateTop(Dest.Timer)
                }
                StopwatchService.ACTION_SHOW_STOPWATCH,
                StopwatchService.ACTION_START_STOPWATCH,
                StopwatchService.ACTION_PAUSE_STOPWATCH -> {
                    backStack.navigateTop(Dest.Stopwatch)
                }
                else -> {
                    if (intent.hasExtra(AlarmConstants.ALARM_CREATE_NEW_INTENT_EXTRA) ||
                        intent.hasExtra(AlarmConstants.SCROLL_TO_ALARM_INTENT_EXTRA) ||
                        intent.action == AlarmClock.ACTION_SET_ALARM) { // Handle SET_ALARM specifically
                        backStack.navigateTop(Dest.Alarm)
                    }
                }
            }
        }
    }

    val railItems = listOf(
        Dest.Alarm,
        Dest.Clock,
        Dest.Timer,
        Dest.Stopwatch,
        Dest.Settings
    )

    // Current top dest
    val currentDestination = backStack.currentTop
    val visibleDestination = backStack.backStack.lastOrNull() ?: currentDestination
    val isSettingsFlow = currentDestination == Dest.Settings
    val isTab = if (isWide) {
        railItems.contains(currentDestination)
    } else {
        bottomNavItems.contains(visibleDestination)
    }

    val destinationChromeColor = settingsChromeColor(visibleDestination)
    val chromeColor = destinationChromeColor ?: if (!isTab) {
        MaterialTheme.colorScheme.background
    } else {
        expressiveChromeColor(
            if (isWide) NavigationBarDefaults.containerColor else MaterialTheme.colorScheme.background
        )
    }
    var hasPermission by remember { mutableStateOf(NotificationPermissionHelper.hasNotificationPermission(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasPermission = NotificationPermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    
    LaunchedEffect(currentDestination, hasPermission) {
        showPermissionDialog = false
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { /* Non-dismissable */ },
            title = { Text("Notification Permission Required") },
            text = { 
                Text(
                    "Alarms and timers require notification permission to function properly.\n\n" +
                    "Without this permission:\n" +
                    "• Alarms won't ring when they're due\n" +
                    "• Timers won't notify when they expire\n\n" +
                    "Please grant notification permission in settings."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        NotificationPermissionHelper.openNotificationSettings(context)
                    }
                ) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        backStack.navigateTop(Dest.Clock)
                    }
                ) {
                    Text("Go to Clock")
                }
            }
        )
    }

    val fabEnabled = visibleDestination !in listOf(Dest.Alarm, Dest.Timer) || hasPermission
    val fabShape = RoundedCornerShape(28.dp)
    val fabContainerColor = if (fabEnabled) {
        MaterialTheme.colorScheme.tertiaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val fabContentColor = if (fabEnabled) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    // Try to get the dispatcher owner from context
    val dispatcherOwner = context as? NavigationEventDispatcherOwner
    
    // Helper composable to wrap content
    @Composable
    fun ContentWrapper(content: @Composable () -> Unit) {
        if (dispatcherOwner != null) {
            CompositionLocalProvider(
                LocalNavigationEventDispatcherOwner provides dispatcherOwner,
                content = content
            )
        } else {
            // Provide specific error or fallback?
            // If we can't provide it, NavDisplay will likely crash again.
            // But we hope context is the Activity which implements it.
            content()
        }
    }

    val scrollState = rememberScrollState()

    ContentWrapper {
        CompositionLocalProvider(
            LocalSetFabClick provides { newClick -> onFabClick = newClick },
            LocalShowSnackbar provides remember(snackbarHostState, scope) {
                ShowSnackbar { message, actionLabel, onAction ->
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        // Material's Short/Long durations (4s/10s) are both longer than we
                        // want, and they are not configurable. Show the snackbar
                        // indefinitely and time it out ourselves instead.
                        val timeout = launch {
                            delay(
                                if (actionLabel != null) SNACKBAR_ACTION_DURATION_MS
                                else SNACKBAR_DURATION_MS
                            )
                            snackbarHostState.currentSnackbarData?.dismiss()
                        }
                        val result = snackbarHostState.showSnackbar(
                            message = message,
                            actionLabel = actionLabel,
                            withDismissAction = false,
                            duration = SnackbarDuration.Indefinite
                        )
                        timeout.cancel()
                        if (result == SnackbarResult.ActionPerformed) onAction?.invoke()
                    }
                }
            },
            LocalActivityIntent provides intent,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .background(chromeColor)
            ) {
                var headerHeightPx by remember { mutableStateOf(0) }

                // ⬅️ Rail lives OUTSIDE Scaffold
                if (isWide && isTab && !isSettingsFlow) {
                    val railContainerColor = if (expressiveDesign) Color.Transparent
                        else NavigationBarDefaults.containerColor

                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = railContainerColor,
                        contentColor = androidx.compose.material3.contentColorFor(railContainerColor),
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                    ) {
                        Column(
                            modifier = Modifier
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            bottomNavItems.forEach { screen ->
                                val selected = currentDestination == screen
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { backStack.navigateTop(screen) },
                                    icon = {
                                        when (screen) {
                                            Dest.Timer -> HourglassIcon(selected = selected)
                                            else -> Icon(
                                                painter = painterResource(screen.filledIcon!!),
                                                contentDescription = null
                                            )
                                        }
                                    },
                                    label = { Text(screen.label) },
                                    alwaysShowLabel = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        val settingsSelected = currentDestination == Dest.Settings
                        NavigationRailItem(
                            selected = settingsSelected,
                            onClick = { backStack.navigateTop(Dest.Settings) },
                            icon = {
                                Icon(
                                    painter = painterResource(Dest.Settings.filledIcon),
                                    contentDescription = null
                                )
                            },
                            label = { Text(Dest.Settings.label) },
                            alwaysShowLabel = true,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }


                // ➡️ Scaffold only owns app content
                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets(0),
                    containerColor = if (expressiveDesign) Color.Transparent
                        else if (isWide) NavigationBarDefaults.containerColor
                        else MaterialTheme.colorScheme.background,

                    floatingActionButton = {
                        if (!isTab && visibleDestination.showFab == true) {
                            onFabClick?.let { click ->
                                LargeFloatingActionButton(
                                    onClick = if (fabEnabled) click else ({}),
                                    modifier = Modifier.alpha(if (fabEnabled) 1f else 0.5f),
                                    shape = fabShape,
                                    containerColor = fabContainerColor,
                                    contentColor = fabContentColor
                                ) {
                                    Icon(
                                        painter = painterResource(visibleDestination.fabIcon ?: R.drawable.ic_add),
                                        contentDescription = visibleDestination.fabContentDescription ?: "Add",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = innerPadding.calculateTopPadding())
                                .then(
                                    if (!expressiveDesign && isWide) {
                                        Modifier
                                            .clip(RoundedCornerShape(topStart = 32.dp))
                                            .background(MaterialTheme.colorScheme.background)
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            CompositionLocalProvider(
                                LocalBottomBarHeight provides if (!isWide) bottomBarHeight else 0.dp
                            ) {
                                navHost()

                            }
                        }

                        SnackbarHost(
                            hostState = snackbarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(
                                    bottom = if (!isWide && isTab) {
                                        (bottomBarHeight - 8.dp).coerceAtLeast(0.dp)
                                    } else {
                                        16.dp
                                    }
                                )
                                .zIndex(3f)
                        ) { data ->
                            Snackbar(
                                snackbarData = data,
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Without this the action falls back to inversePrimary, which
                                // is picked for the default (inverseSurface) snackbar and
                                // clashes against this surfaceVariant container.
                                actionColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(50)
                            )
                        }

                        if (isWide && isTab && visibleDestination.showFab == true) {
                            onFabClick?.let { click ->
                                LargeFloatingActionButton(
                                    onClick = if (fabEnabled) click else ({}),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .windowInsetsPadding(
                                            WindowInsets.safeDrawing.only(
                                                WindowInsetsSides.Bottom + WindowInsetsSides.End
                                            )
                                        )
                                        .padding(end = 24.dp, bottom = 16.dp)
                                        .alpha(if (fabEnabled) 1f else 0.5f),
                                    shape = fabShape,
                                    containerColor = fabContainerColor,
                                    contentColor = fabContentColor
                                ) {
                                    Icon(
                                        painter = painterResource(visibleDestination.fabIcon ?: R.drawable.ic_add),
                                        contentDescription = visibleDestination.fabContentDescription ?: "Add",
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }
                        if (!isWide) {
                            MotionBottomBarHost(
                                navigationState = navigationState,
                                motionLevel = motionLevel,
                                bottomBarHeight = bottomBarHeight,
                                fullyDarkened = true,
                                darkeningHeight = 240.dp,
                                modifier = Modifier.align(Alignment.BottomCenter)
                            ) {
                                MotionNavigationBar(
                                    modifier = Modifier.fillMaxWidth(),
                                    visible = true,
                                    currentDest = BottomBarHostDest,
                                    destinations = listOf(BottomBarHostDest),
                                    onNavigate = {},
                                    onHeightChanged = { bottomBarHeight = it },
                                    showContrast = false,
                                    fullyDarkened = true,
                                    darkeningHeight = 180.dp,
                                    toolbarColors = FloatingToolbarDefaults.standardFloatingToolbarColors(
                                        toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                                        toolbarContentColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    floatingActionButton = if (visibleDestination.showFab == true) {
                                        onFabClick?.let { click ->
                                            {
                                                FloatingActionButton(
                                                    onClick = if (fabEnabled) click else ({}),
                                                    modifier = Modifier.alpha(if (fabEnabled) 1f else 0.5f),
                                                    containerColor = fabContainerColor,
                                                    contentColor = fabContentColor
                                                ) {
                                                    Icon(
                                                        painter = painterResource(visibleDestination.fabIcon ?: R.drawable.ic_add),
                                                        contentDescription = visibleDestination.fabContentDescription ?: "Add",
                                                        modifier = Modifier.size(36.dp)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    toolbarContent = {
                                        ClockBottomBarItems(
                                            destinations = bottomNavItems,
                                            currentDestination = currentDestination,
                                            onNavigate = { backStack.navigateTop(it) }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

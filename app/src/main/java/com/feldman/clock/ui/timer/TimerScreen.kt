@file:Suppress("AssignedValueIsNeverRead")

package com.feldman.clock.ui.timer

import com.feldman.clock.R
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.ui.components.ClockAppBarIconButton
import com.feldman.clock.ui.components.ClockPageTitle
import com.feldman.clock.ui.components.ExpressiveContentSurface

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults.buttonColors
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feldman.clock.ui.timer.model.Timer
import com.feldman.clock.ui.timer.model.TimerState
import kotlinx.coroutines.delay
import com.feldman.clock.core.util.isScreenWide
import com.feldman.motion.FontAxes
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionButtonState
import com.feldman.clock.app.navigation.Dest
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalContext
import com.feldman.motion.feldmanFont
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.feldman.clock.core.util.NotificationPermissionHelper
import com.feldman.clock.ui.components.NotificationPermissionRequiredCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    viewModel: TimerViewModel = viewModel(),
    onFabClick: ((() -> Unit)?) -> Unit,
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current
    var hasNotificationPermission by remember {
        mutableStateOf(NotificationPermissionHelper.hasNotificationPermission(context))
    }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationPermission =
                    NotificationPermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val timers by viewModel.timers.collectAsState()

    val displayTimers by remember(timers) {
        derivedStateOf {
            timers.sortedWith(
                compareBy<Timer> { timer ->
                    when (timer.state) {
                        TimerState.RUNNING -> 0 // Running timers first
                        TimerState.PAUSED -> 1 // Paused timers second
                        TimerState.RESET -> 1 // Reset (unstarted) same as paused
                        TimerState.EXPIRED -> 2 // Expired last
                    }
                }.thenBy { it.remainingTimeInMillis } // Then by remaining time ascending
            )
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var restoreScroll by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    fun toggleTimerPreserveScroll(id: Long) {
        // Capture state BEFORE toggle
        val beforeList = displayTimers
        val toggledIndex = beforeList.indexOfFirst { it.id == id }
        val currentFirstVisible = listState.firstVisibleItemIndex
        
        // Perform the toggle
        viewModel.toggleTimer(id)
        
        // After recomposition, LazyColumn will recalculate
        // We need to adjust scroll if item moved above our viewport
        if (toggledIndex < currentFirstVisible) {
            // Item was above viewport, no adjustment needed
            restoreScroll = null
        } else {
            // Item MAY move above viewport after toggle
            // Save current position to restore after recomposition
            restoreScroll = currentFirstVisible to listState.firstVisibleItemScrollOffset
        }
    }

    LaunchedEffect(displayTimers) {
        val snap = restoreScroll ?: return@LaunchedEffect
        
        delay(1)
        
        if (displayTimers.isNotEmpty() && !listState.isScrollInProgress) {
            val targetIndex = snap.first.coerceIn(0, displayTimers.lastIndex)
            listState.scrollToItem(targetIndex, snap.second)
        }
        restoreScroll = null
    }

    LaunchedEffect(hasNotificationPermission) {
        onFabClick {
            if (hasNotificationPermission) showAddDialog = true
        }
    }

    var pendingScrollToId by remember { mutableStateOf<Long?>(null) }
    val isWide = isScreenWide()

    LaunchedEffect(pendingScrollToId) {
        val id = pendingScrollToId ?: return@LaunchedEffect
        val index = displayTimers.indexOfFirst { it.id == id }
        if (index != -1) {
            listState.animateScrollToItem(index)
        }
        pendingScrollToId = null
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { ClockPageTitle("Timers") },
                navigationIcon = {
                    if (!isTab) {
                        ClockAppBarIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    ClockAppBarIconButton(onClick = { onNavigate(Dest.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = expressiveChromeColor(
                        if (isWide) NavigationBarDefaults.containerColor
                        else colorScheme.surfaceContainer
                    ),
                    scrolledContainerColor = Color.Unspecified,
                    navigationIconContentColor = Color.Unspecified,
                    titleContentColor = Color.Unspecified,
                    actionIconContentColor = Color.Unspecified
                )
            )
        }
    ) { innerPadding ->
        ExpressiveContentSurface(modifier = Modifier.padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {

        if (!hasNotificationPermission) {
            NotificationPermissionRequiredCard(
                message = "Timers cannot notify you when they expire until notification permission is granted.",
                onGrantClick = {
                    NotificationPermissionHelper.openNotificationSettings(context)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (timers.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "No Timers", style = typography.bodyLarge)
            }
        } else {

            if (isWide) {
                //Wide screen -> grid
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 180.dp)
                ) {
                    items(
                        items = displayTimers,
                        key = { it.id }
                    ) { timer ->
                        TimerItem(
                            timer = timer,
                            onToggle = { toggleTimerPreserveScroll(timer.id) },
                            onDelete = { viewModel.deleteTimer(timer.id) },
                            onPlayRequest = { id -> pendingScrollToId = id },
                            viewModel = viewModel,
                            modifier = Modifier.animateItem()
                        )
                    }
                }

            } else {
                //Phone / narrow -> single column
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(
                        items = displayTimers,
                        key = { it.id }
                    ) { timer ->
                        TimerItem(
                            timer = timer,
                            onToggle = { toggleTimerPreserveScroll(timer.id) },
                            onDelete = { viewModel.deleteTimer(timer.id) },
                            onPlayRequest = { id -> pendingScrollToId = id },
                            viewModel = viewModel,
                            modifier = Modifier.animateItem()
                        )
                    }
                    item {
                        Spacer(modifier = Modifier.height(180.dp))
                    }
                }
            }
        }

        }
        }
    }

    
    if (showAddDialog) {
        TimerCreateDialog(
            onDismiss = { showAddDialog = false },
            onCreate = { hours, minutes, seconds, label ->
                val totalMillis = (hours * 3600 + minutes * 60 + seconds) * 1000L
                if (totalMillis > 0) {
                    viewModel.addTimer(totalMillis, label)
                }
                showAddDialog = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerCreateDialog(
    onDismiss: () -> Unit,
    onCreate: (hours: Int, minutes: Int, seconds: Int, label: String) -> Unit
) {
    var input by remember { mutableStateOf("") }
    
    // Parse input into hours, minutes, seconds
    val (hours, minutes, seconds) = remember(input) {
        val digits = input.padStart(6, '0').takeLast(6)
        val h = digits.take(2).toIntOrNull() ?: 0
        val m = digits.substring(2, 4).toIntOrNull() ?: 0
        val s = digits.substring(4, 6).toIntOrNull() ?: 0
        Triple(h, m, s)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceVariant,
        title = null,
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 24.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = String.format(LocalLocale.current.platformLocale, "%02d", hours),
                        fontSize = 48.sp,
                        fontFamily = feldmanFont,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "h",
                        fontSize = 20.sp,
                        fontFamily = feldmanFont,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = String.format(LocalLocale.current.platformLocale, "%02d", minutes),
                        fontSize = 48.sp,
                        fontFamily = feldmanFont,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "m",
                        fontSize = 20.sp,
                        fontFamily = feldmanFont,
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                    )

                    Spacer(Modifier.width(10.dp))

                    Text(
                        text = String.format(LocalLocale.current.platformLocale, "%02d", seconds),
                        fontSize = 48.sp,
                        fontFamily = feldmanFont,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = "s",
                        fontSize = 20.sp,
                        fontFamily = feldmanFont(weight = 350),
                        modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
                    )
                }

                
                // Numpad
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Row 1: 1 2 3
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumpadButton("1") { input += "1" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("2") { input += "2" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("3") { input += "3" }
                    }
                    
                    // Row 2: 4 5 6
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumpadButton("4") { input += "4" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("5") { input += "5" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("6") { input += "6" }
                    }
                    
                    // Row 3: 7 8 9
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumpadButton("7") { input += "7" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("8") { input += "8" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("9") { input += "9" }
                    }
                    
                    // Row 4: 00 0 delete
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        NumpadButton("00") { input += "00" }
                        Spacer(Modifier.width(4.dp))
                        NumpadButton("0") { input += "0" }
                        Spacer(Modifier.width(4.dp))
                        BackspaceButton(
                            onClick = {
                                if (input.isNotEmpty()) {
                                    input = input.dropLast(1)
                                }
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            MotionButton(
                onClick = { onCreate(hours, minutes, seconds, "") },
                text = "Create",
                width = 80.dp,
                height = 40.dp,
                defaultState = MotionButtonState(
                    backgroundColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    cornerRadius = 50f
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = colorScheme.primary,
                    contentColor = colorScheme.onPrimary,
                    cornerRadius = 32f
                ),
                fontSize = 14.sp
            )
        },
        dismissButton = {
            MotionButton(
                onClick = onDismiss,
                text = "Cancel",
                width = 80.dp,
                height = 40.dp,
                defaultState = MotionButtonState(
                    backgroundColor = Color.Transparent,
                    contentColor = colorScheme.primary,
                    cornerRadius = 50f,
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = Color.Transparent,
                    contentColor = colorScheme.primary,
                    cornerRadius = 40f,
                ),
                fontSize = 14.sp
            )
        }
    )
}

@Composable
fun NumpadButton(
    text: String,
    onClick: () -> Unit
) {
    MotionButton(
        text = text,
        onClick = onClick,
        modifier = Modifier.size(70.dp),
        // Use defaultState to define the idle look
        defaultState = MotionButtonState(
            backgroundColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
            cornerRadius = 50f, // Circle-ish idle
            fontAxes = FontAxes(weight = 600, width = 100f)
        ),
        // defaultPressedState is automatically calculated by MotionButton
        // if not provided, but you can override it for a specific corner radius (16dp = ~23f)
        defaultPressedState = MotionButtonState(
            backgroundColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            cornerRadius = 32f, // 16dp equivalent
            fontAxes = FontAxes(weight = 500, width = 125f)
        ),
        fontSize = 24.sp
    )
}

@Composable
fun BackspaceButton(
    onClick: () -> Unit
) {
    MotionButton(
        icon = MotionButton.drawable.ic_backspace,
        onClick = onClick,
        modifier = Modifier.size(70.dp),
        defaultState = MotionButtonState(
            backgroundColor = colorScheme.surfaceContainer,
            contentColor = colorScheme.onSurface,
            cornerRadius = 50f, // Circle-ish idle
            fontAxes = FontAxes(weight = 600, width = 100f)
        ),
        // defaultPressedState is automatically calculated by MotionButton
        // if not provided, but you can override it for a specific corner radius (16dp = ~23f)
        defaultPressedState = MotionButtonState(
            backgroundColor = colorScheme.primaryContainer,
            contentColor = colorScheme.onPrimaryContainer,
            cornerRadius = 32f, // 16dp equivalent
            fontAxes = FontAxes(weight = 500, width = 125f)
        ),
        iconSize = 24.dp
    )
}

@Composable
fun TimerLabelDialog(
    initialLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(initialLabel) }

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            OutlinedTextField(
                value = label,
                onValueChange = { label = it },
                label = { Text("Label") },
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(label) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TimerItem(
    timer: Timer,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onPlayRequest: (Long) -> Unit,
    viewModel: TimerViewModel,
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(true) }
    var showLabelDialog by remember { mutableStateOf(false) }

    if (showLabelDialog) {
        TimerLabelDialog(
            initialLabel = timer.label,
            onConfirm = { newLabel ->
                viewModel.setTimerLabel(timer.id, newLabel)
                showLabelDialog = false
            },
            onDismiss = { showLabelDialog = false }
        )
    }

    LaunchedEffect(visible) {
        if (!visible) {
            delay(220)
            onDelete()
        }
    }
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        exit = shrinkVertically(
            shrinkTowards = Alignment.Top,
            animationSpec = tween(220, easing = FastOutSlowInEasing)
        ) + fadeOut(
            animationSpec = tween(150)
        )
    ) {
        Card(
            onClick = { showLabelDialog = true },
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = colorScheme.surfaceContainerHigh
            )
        ) {
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {

                Button(
                    onClick = { visible = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(36.dp),
                    shape = CircleShape,
                    colors = buttonColors(
                        containerColor = colorScheme.lighterSurfaceVariant()
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove timer",
                        modifier = Modifier.size(18.dp),
                        tint = colorScheme.onSurface
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val title = timer.label.ifEmpty { "${formatDuration(timer.totalLengthInMillis)} Timer" }
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontFamily = feldmanFont(weight = 400, width = 100f),
                            color = colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        // Progress ring + remaining time
                        Box(
                            modifier = Modifier.size(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                progress = {
                                    if (timer.totalLengthInMillis > 0)
                                        timer.remainingTimeInMillis.toFloat() / timer.totalLengthInMillis
                                    else 0f
                                },
                                strokeWidth = 8.dp,
                                color = colorScheme.primary,
                                trackColor = colorScheme.surface,
                                modifier = Modifier.size(200.dp)
                            )

                            Box(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                // ⏱ Time — truly centered
                                Text(
                                    text = formatTime(timer.remainingTimeInMillis),
                                    fontSize = 48.sp,
                                    fontFamily = feldmanFont(weight = 300, width = 75f),
                                    color = colorScheme.onSurface,
                                    modifier = Modifier.align(Alignment.Center)
                                )

                                // 🔄 Reset button — visually below center
                                IconButton(
                                    onClick = { viewModel.resetTimer(timer.id) },
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 36.dp)
                                        .size(32.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_restart_alt),
                                        contentDescription = "Reset Timer",
                                        tint = colorScheme.onSurface,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }

                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {

                            MotionButton(
                                text = "+1:00",
                                modifier = Modifier
                                    .height(80.dp)
                                    .width(120.dp),
                                onClick = { viewModel.addOneMinute(timer.id) },
                                fontSize = 24.sp,
                                // Define the idle state using your custom color helper
                                defaultState = MotionButtonState(
                                    backgroundColor = colorScheme.lighterSurfaceVariant(),
                                    contentColor = colorScheme.onSurface,
                                    cornerRadius = 50f,
                                    fontAxes = FontAxes(weight = 600, width = 100f)
                                ),
                                defaultPressedState = MotionButtonState(
                                    backgroundColor = colorScheme.secondaryContainer,
                                    contentColor = colorScheme.onSecondaryContainer,
                                    fontAxes = FontAxes(weight = 500, width = 115f)
                                )
                            )
                            MotionButton(
                                icon = when (timer.state) {
                                    TimerState.RUNNING -> MotionButton.drawable.ic_pause
                                    TimerState.EXPIRED -> MotionButton.drawable.ic_refresh
                                    else -> MotionButton.drawable.ic_play_arrow
                                },
                                modifier = Modifier
                                    .height(80.dp)
                                    .width(120.dp),
                                onClick = {
                                    val wasRunning = timer.state == TimerState.RUNNING
                                    onToggle()

                                    if (!wasRunning) {
                                        onPlayRequest(timer.id)
                                    }
                                },
                                iconSize = 28.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
fun lighterSurfaceVariant(
    liftAlpha: Float = 0.06f
): Color {
    val scheme = colorScheme

    return Color.White.copy(alpha = liftAlpha)
        .compositeOver(scheme.surfaceVariant)
}
@Composable
fun ColorScheme.lighterSurfaceVariant(
    liftAlpha: Float = 0.06f
): Color {
    val scheme = colorScheme

    return Color.White.copy(alpha = liftAlpha)
        .compositeOver(scheme.surfaceVariant)
}

@Composable
fun blendedSurfacePrimary(
    primaryAlpha: Float = 0.04f,
    liftAlpha: Float = 0.04f
): Color {
    val scheme = colorScheme

    val liftedSurface = Color.White.copy(alpha = liftAlpha)
        .compositeOver(scheme.surface)

    return scheme.primary.copy(alpha = primaryAlpha)
        .compositeOver(liftedSurface)
}

fun formatDuration(millis: Long): String {
    val totalSeconds = millis / 1000

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        if (seconds > 0) append("${seconds}s")
    }.trim()
}



fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000

    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return buildString {
        if (hours > 0) {
            append("$hours:")
        }

        if (minutes > 0 || hours > 0) {
            if (hours > 0) {
                append(String.format("%02d:", minutes))
            } else {
                append("$minutes:")
            }
        }

        if (minutes > 0 || hours > 0) {
            append(String.format("%02d", seconds))
        } else {
            append(seconds)
        }
    }
}


@file:Suppress("AssignedValueIsNeverRead")

package com.feldman.clock.ui.alarm

import com.feldman.clock.R
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.ui.components.ClockAppBarIconButton
import com.feldman.clock.ui.components.ClockPageTitle
import com.feldman.clock.ui.components.ExpressiveContentSurface
import com.feldman.clock.ui.components.NotificationPermissionRequiredCard

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feldman.clock.core.data.Weekdays
import com.feldman.clock.core.alarm.AlarmChallenge
import com.feldman.clock.core.alarm.AlarmChallengePrefs
import com.feldman.clock.core.alarm.AlarmSnoozePolicy
import com.feldman.clock.ui.ShowSnackbar
import com.feldman.clock.ui.alarm.model.AlarmUi
import com.feldman.clock.ui.clock.clockTimeText
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

import com.feldman.clock.alarm.AlarmConstants
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.alarm.AlarmDurationOptions.labelFor
import androidx.core.net.toUri
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.feldman.clock.core.storage.provider.ClockContract
import com.feldman.clock.core.util.isScreenWide
import com.feldman.clock.core.util.NotificationPermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.feldman.clock.app.navigation.Dest
import com.feldman.motion.feldmanFont
import com.feldman.motion.MotionLevel
import com.feldman.motion.MotionSwipeRow
import com.feldman.motion.rememberExpressiveDesign
import com.feldman.motion.rememberMotionLevel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen(
    viewModel: AlarmViewModel = viewModel(),
    showSnackbar: ShowSnackbar = ShowSnackbar { _, _, _ -> },
    intent: Intent? = null,
    onFabClick: ((() -> Unit)?) -> Unit,
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean,
    showTopAppBar: Boolean = true,
    embeddedSheet: Boolean = false
) {
    val context = LocalContext.current
    val isWide = isScreenWide()
    val expressiveDesign = rememberExpressiveDesign()
    val motionLevel = rememberMotionLevel()
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

    var showAddTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(hasNotificationPermission) {
        onFabClick {
            if (hasNotificationPermission) showAddTimePicker = true
        }
    }

    LaunchedEffect(intent) {
        if (intent != null && intent.hasExtra(AlarmConstants.ALARM_CREATE_NEW_INTENT_EXTRA)) {
            if (intent.getBooleanExtra(AlarmConstants.ALARM_CREATE_NEW_INTENT_EXTRA, false)) {
                showAddTimePicker = true
            }
        }
    }

    val alarms by viewModel.alarms.collectAsStateWithLifecycle(initialValue = emptyList())

    SideEffect {
        Log.e("AlarmScreen", "alarms.size=${alarms.size} ids=${alarms.map { it.id }}")
    }

    LaunchedEffect(alarms) {
        // runs on every alarms emission
         Log.d("AlarmScreen", "alarms=${alarms}")
    }

    // Bottom sheet state
    var selectedAlarm by remember { mutableStateOf<AlarmUi?>(null) }
    var draftAlarm by remember { mutableStateOf<AlarmUi?>(null) }
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    // Snackbar relay
    LaunchedEffect(Unit) {
        viewModel.snackbarFlow.collectLatest { showSnackbar(it) }
    }

    // Keep selectedAlarm in sync with the latest alarm data
    // This ensures the editor always shows current upcomingInstanceTime
    LaunchedEffect(alarms, selectedAlarm?.id) {
        Log.d("AlarmScreen", "Sync LaunchedEffect triggered. alarms.size=${alarms.size}, selectedAlarm.id=${selectedAlarm?.id}")
        if (selectedAlarm != null) {
            val updated = alarms.find { it.id == selectedAlarm!!.id }
            if (updated != null) {
                Log.d("AlarmScreen", "Found updated alarm. Old upcomingInstanceTime=${selectedAlarm!!.upcomingInstanceTime?.time}, New upcomingInstanceTime=${updated.upcomingInstanceTime?.time}")
                // Always update selectedAlarm to get latest upcomingInstanceTime
                // but never update draftAlarm (preserves user edits)
                selectedAlarm = updated
                Log.d("AlarmScreen", "Updated selectedAlarm")
            } else {
                Log.d("AlarmScreen", "No updated alarm found for id=${selectedAlarm!!.id}")
            }
        }
    }

    // Confirm snooze dismiss dialog
    var showCancelSnoozeDialog by remember { mutableStateOf<AlarmUi?>(null) }

    if (showCancelSnoozeDialog != null) {
        val alarm = showCancelSnoozeDialog!!
        val snoozeTime = alarm.snoozingUntil ?: ""
        
        AlertDialog(
            onDismissRequest = { showCancelSnoozeDialog = null },
            title = { Text("Snoozing until $snoozeTime") },
            text = { Text("Do you want to dismiss this alarm?") },
            confirmButton = {
                Button(
                    onClick = { showCancelSnoozeDialog = null }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        viewModel.dismissSnooze(alarm)
                        showCancelSnoozeDialog = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorScheme.error,
                        contentColor = colorScheme.onError
                    )
                ) {
                    Text("Dismiss")
                }
            }
        )
    }

    // Direct time picker (skipping sheet)
    var showDirectTimePicker by remember { mutableStateOf<AlarmUi?>(null) }
    var handledRequestedAlarmId by remember(intent) { mutableStateOf<Long?>(null) }
    val requestedAlarmId = intent?.getLongExtra(
        AlarmConstants.SCROLL_TO_ALARM_INTENT_EXTRA,
        com.feldman.clock.core.storage.provider.Alarm.INVALID_ID
    ) ?: com.feldman.clock.core.storage.provider.Alarm.INVALID_ID

    LaunchedEffect(requestedAlarmId, alarms) {
        if (
            requestedAlarmId != com.feldman.clock.core.storage.provider.Alarm.INVALID_ID &&
            handledRequestedAlarmId != requestedAlarmId
        ) {
            alarms.firstOrNull { it.id == requestedAlarmId }?.let { requestedAlarm ->
                showDirectTimePicker = requestedAlarm
                handledRequestedAlarmId = requestedAlarmId
            }
        }
    }

    if (showDirectTimePicker != null) {
        val alarm = showDirectTimePicker!!
        KeyboardFirstTimePickerDialog(
            title = "Edit alarm time",
            initialHour = alarm.hour,
            initialMinute = alarm.minutes,
            is24Hour = viewModel.is24HourFormat(),
            onDismissRequest = { showDirectTimePicker = null },
            onConfirm = { hour, minute ->
                // Create a copy with updated time
                val updated = alarm.copy(hour = hour, minutes = minute, enabled = true)
                viewModel.updateAlarm(updated)
                showDirectTimePicker = null
            }
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            if (showTopAppBar) CenterAlignedTopAppBar(
                title = { ClockPageTitle("Alarms") },
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
                    )
                )
            )
        }
    ) { innerPadding ->
        AlarmContentSurface(
            embeddedSheet = embeddedSheet,
            modifier = Modifier.padding(innerPadding)
        ) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {

        // Shared by every alarm card so a row being dragged can pull its neighbours along.
        val swipeState = remember { MagneticSwipeState() }

        // Resolved here so the swipe callbacks stay non-composable.
        val alarmDeletedText = stringResource(R.string.alarm_deleted)
        val undoText = stringResource(R.string.undo)

        if (!hasNotificationPermission) {
            NotificationPermissionRequiredCard(
                message = "Alarms cannot ring or notify you until notification permission is granted.",
                onGrantClick = {
                    NotificationPermissionHelper.openNotificationSettings(context)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else if (isWide) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(items = alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                    AlarmSwipeRow(
                        index = index,
                        rowKey = alarm.id,
                        swipeState = swipeState,
                        motionLevel = motionLevel,
                        onDelete = {
                            viewModel.deleteAlarm(alarm)
                            showSnackbar(alarmDeletedText, undoText) {
                                viewModel.restoreAlarm(alarm)
                            }
                        },
                        modifier = Modifier.animateItem()
                    ) {
                        AlarmRow(
                            alarm = alarm,
                            is24Hour = viewModel.is24HourFormat(),
                            expressiveDesign = expressiveDesign,
                            motionLevel = motionLevel,
                            onRowClick = {
                                selectedAlarm = alarm
                                draftAlarm = alarm
                            },
                            onTimeClick = { showDirectTimePicker = alarm },
                            onToggle = { enabled ->
                                viewModel.toggleAlarm(alarm, enabled)
                            },
                            onSnoozeClick = {
                                showCancelSnoozeDialog = alarm
                            },
                            onDismissUpcomingClick = {
                                viewModel.dismissUpcoming(alarm)
                            }
                        )
                    }
                }

                item(span = { GridItemSpan(3) }) {
                    Spacer(Modifier.height(116.dp))
                }
            }
        } else {
            // 📱 Phone → single column list
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items = alarms, key = { _, alarm -> alarm.id }) { index, alarm ->
                    AlarmSwipeRow(
                        index = index,
                        rowKey = alarm.id,
                        swipeState = swipeState,
                        motionLevel = motionLevel,
                        onDelete = {
                            viewModel.deleteAlarm(alarm)
                            showSnackbar(alarmDeletedText, undoText) {
                                viewModel.restoreAlarm(alarm)
                            }
                        },
                        modifier = Modifier.animateItem()
                    ) {
                        AlarmRow(
                            alarm = alarm,
                            is24Hour = viewModel.is24HourFormat(),
                            expressiveDesign = expressiveDesign,
                            motionLevel = motionLevel,
                            onRowClick = {
                                selectedAlarm = alarm
                                draftAlarm = alarm
                            },
                            onTimeClick = { showDirectTimePicker = alarm },
                            onToggle = { enabled ->
                                viewModel.toggleAlarm(alarm, enabled)
                            },
                            onSnoozeClick = {
                                showCancelSnoozeDialog = alarm
                            },
                            onDismissUpcomingClick = {
                                viewModel.dismissUpcoming(alarm)
                            }
                        )
                    }
                }

                item { Spacer(Modifier.height(180.dp)) }
            }
        }
        }
    }

    if (showAddTimePicker) {
        val now = Calendar.getInstance()

        KeyboardFirstTimePickerDialog(
            title = "New alarm",
            initialHour = now.get(Calendar.HOUR_OF_DAY),
            initialMinute = now.get(Calendar.MINUTE),
            is24Hour = viewModel.is24HourFormat(),
            onDismissRequest = { showAddTimePicker = false },
            onConfirm = { hour, minute ->
                viewModel.addAlarm(hour, minute)
                showAddTimePicker = false
            }
        )
    }


    // Bottom sheet editor
    if (selectedAlarm != null && draftAlarm != null) {
        ModalBottomSheet(
            modifier = Modifier.imePadding(),
            onDismissRequest = {
                selectedAlarm = null
                draftAlarm = null
            },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            AlarmEditorSheet(
                original = selectedAlarm!!,
                draft = draftAlarm!!,
                onDraftChange = { draftAlarm = it },
                onCancel = {
                    selectedAlarm = null
                    draftAlarm = null
                },
                onSave = {
                    viewModel.updateAlarm(draftAlarm!!)
                    selectedAlarm = null
                    draftAlarm = null
                },
                onDelete = {
                    viewModel.deleteAlarm(selectedAlarm!!)
                    selectedAlarm = null
                    draftAlarm = null
                },
                viewModel = viewModel
            )
        }
    }
}
}

@Composable
private fun AlarmContentSurface(
    embeddedSheet: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    if (embeddedSheet) {
        Box(modifier = modifier.fillMaxSize(), content = { content() })
    } else {
        ExpressiveContentSurface(modifier = modifier, content = content)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyboardFirstTimePickerDialog(
    title: String,
    initialHour: Int,
    initialMinute: Int,
    is24Hour: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    var useKeyboard by remember { mutableStateOf(true) }

    val state = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = is24Hour
    )

    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            tonalElevation = 6.dp
        ) {
            Column {

                // Title
                Text(
                    text = title,
                    style = typography.titleLarge,
                    modifier = Modifier.padding(24.dp),
                    fontWeight = FontWeight.Bold
                )

                // Content
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (useKeyboard) {
                        TimeInput(state = state)
                    } else {
                        TimePicker(state = state)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            useKeyboard = !useKeyboard
                            focusManager.clearFocus(force = true)
                        }
                    ) {
                        Icon(
                            painter = if (useKeyboard)
                                painterResource(R.drawable.ic_clock)
                            else
                                painterResource(R.drawable.ic_keyboard),
                            contentDescription = null
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    TextButton(onClick = onDismissRequest) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = { onConfirm(state.hour, state.minute) }
                    ) {
                        Text("OK")
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: AlarmUi,
    is24Hour: Boolean,
    expressiveDesign: Boolean,
    motionLevel: MotionLevel,
    onRowClick: () -> Unit,
    onTimeClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onSnoozeClick: () -> Unit,
    onDismissUpcomingClick: () -> Unit
) {
    val context = LocalContext.current
    val floatAnimationSpec: FiniteAnimationSpec<Float> = when (motionLevel) {
        MotionLevel.NONE -> snap()
        MotionLevel.LOW -> spring(dampingRatio = 0.9f, stiffness = 1_000f)
        MotionLevel.MEDIUM -> spring(dampingRatio = 0.75f, stiffness = 900f)
        MotionLevel.HIGH -> spring(dampingRatio = 0.6f, stiffness = 800f)
    }
    val colorAnimationSpec: FiniteAnimationSpec<Color> = when (motionLevel) {
        MotionLevel.NONE -> snap()
        MotionLevel.LOW -> spring(dampingRatio = 0.9f, stiffness = 1_000f)
        MotionLevel.MEDIUM -> spring(dampingRatio = 0.75f, stiffness = 900f)
        MotionLevel.HIGH -> spring(dampingRatio = 0.6f, stiffness = 800f)
    }
    val cardColor by animateColorAsState(
        targetValue = if (alarm.enabled) colorScheme.secondaryContainer else colorScheme.surfaceContainerHigh,
        animationSpec = colorAnimationSpec,
        label = "AlarmCardBackground"
    )
    val timeFontSize by animateFloatAsState(
        targetValue = if (alarm.enabled) 50f else 46f,
        animationSpec = floatAnimationSpec,
        label = "AlarmTimeFontSize"
    )
    val timeFontWidth by animateFloatAsState(
        targetValue = when {
            !expressiveDesign -> 100f
            !alarm.enabled -> 90f
            else -> 112f
        },
        animationSpec = floatAnimationSpec,
        label = "AlarmTimeFontWidth"
    )
    val timeFontWeight by animateFloatAsState(
        targetValue = if (alarm.enabled) 500f else 300f,
        animationSpec = floatAnimationSpec,
        label = "AlarmTimeFontWeight"
    )
    val calendar = remember(alarm.hour, alarm.minutes) {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, alarm.hour)
            set(Calendar.MINUTE, alarm.minutes)
        }
    }

    // The card must not change height when the alarm is toggled. Two things would otherwise
    // resize it: the time's animated font size, and the status line at the bottom appearing or
    // disappearing. Both rows therefore get a fixed height, derived from the type sizes so the
    // layout still respects the user's font-scale setting.
    val density = LocalDensity.current
    val timeRowHeight = with(density) { 60.sp.toDp() }.coerceAtLeast(48.dp)
    val statusRowHeight = with(density) { typography.bodyMedium.lineHeight.toDp() }

    Card(
        onClick = onRowClick,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 84.dp),
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {

            // ── Schedule + label (same line)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = alarm.scheduleText(context),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.width(16.dp))

                if (!alarm.label.isNullOrBlank()) {
                    Text(
                        text = alarm.label,
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Time + switch (same line)
            // Fixed height so the enabled/disabled font-size animation cannot resize the card.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(timeRowHeight),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    // AM/PM is rendered smaller than the time, matching the clock screen.
                    text = clockTimeText(
                        time = DateFormat.format(
                            if (is24Hour) "kk:mm" else "h:mm",
                            calendar
                        ).toString(),
                        period = if (is24Hour) {
                            null
                        } else {
                            DateFormat.format("aa", calendar).toString()
                        },
                        fontSize = timeFontSize.sp
                    ),
                    fontSize = timeFontSize.sp,
                    lineHeight = 60.sp,
                    fontFamily = feldmanFont(
                        weight = timeFontWeight.roundToInt(),
                        width = timeFontWidth
                    ),
                    color = if (alarm.enabled)
                        colorScheme.onSecondaryContainer
                    else
                        colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onTimeClick() }
                        .padding(4.dp)
                )


                Spacer(modifier = Modifier.weight(1f))

                MaterialSwitch(
                    checked = alarm.enabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.sizeIn(minWidth = 52.dp, minHeight = 48.dp)
                )
            }


            // ── Status line: snoozing, or a dismiss action for an imminent alarm.
            // The slot is always laid out, even when empty, so that toggling the alarm — which
            // makes the dismiss action come and go — never changes the card's height.
            val showDismiss = alarm.snoozingUntil == null &&
                alarm.enabled &&
                alarm.upcomingInstanceTime != null &&
                (alarm.upcomingInstanceTime.timeInMillis - System.currentTimeMillis()) in
                0..(2 * 60 * 60 * 1000)

            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusRowHeight),
                contentAlignment = Alignment.CenterStart
            ) {
                when {
                    alarm.snoozingUntil != null -> Text(
                        text = "Snoozing until ${alarm.snoozingUntil}",
                        style = typography.bodyMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable { onSnoozeClick() }
                    )

                    showDismiss -> Text(
                        text = "Dismiss",
                        style = typography.bodyMedium,
                        color = colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.clickable { onDismissUpcomingClick() }
                    )
                }
            }
        }
    }
}

private val SwipeDeleteContainerColor = Color(0xFFFF7165)
private val SwipeDeleteIconColor = Color(0xFF490002)

/**
 * Shared drag state for one list of connected alarm cards.
 *
 * [MotionSwipeRow] reports its own translation but knows nothing about its siblings, so the
 * list owns this and every row reads it to decide how far to trail the row being dragged.
 */
private class MagneticSwipeState {
    /** Index of the row currently being dragged, or -1 when nothing is. */
    var index by mutableIntStateOf(-1)

    /** Raw drag translation of that row, in pixels. */
    var offset by mutableFloatStateOf(0f)

    /** True once the dragged row has passed the detach threshold and broken away. */
    var detached by mutableStateOf(false)

    fun onSwipeChanged(rowIndex: Int, translationPx: Float, isDetached: Boolean) {
        if (abs(translationPx) > 0.5f) {
            index = rowIndex
            offset = translationPx
            detached = isDetached
        } else if (index == rowIndex) {
            reset()
        }
    }

    fun reset() {
        index = -1
        offset = 0f
        detached = false
    }
}

/** Matches the settle/detach springs inside [MotionSwipeRow] so neighbours move in sympathy. */
private fun magneticSpring(motionLevel: MotionLevel): AnimationSpec<Float> = when (motionLevel) {
    MotionLevel.NONE -> snap()
    MotionLevel.LOW -> spring(dampingRatio = 0.95f, stiffness = 900f)
    MotionLevel.MEDIUM -> spring(dampingRatio = 0.78f, stiffness = 700f)
    MotionLevel.HIGH -> spring(dampingRatio = 0.6f, stiffness = 550f)
}

/**
 * An alarm card that can be swiped away in either direction, with the connected-card motion
 * used by the motion library's settings scaffold: while the row is still magnetically attached
 * it drags its immediate neighbours along, and the moment it detaches they spring back.
 */
@Composable
private fun AlarmSwipeRow(
    index: Int,
    rowKey: Any,
    swipeState: MagneticSwipeState,
    motionLevel: MotionLevel,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    // Immediate neighbours follow at 12%, the next ones out at 4%; everything else stays put.
    // Once the dragged row detaches it is on its own, so the pull drops to zero and the
    // neighbours spring home — that snap is what makes the detachment readable.
    val neighborMultiplier = when (abs(index - swipeState.index)) {
        1 -> 0.12f
        2 -> 0.04f
        else -> 0f
    }
    val neighborTarget = if (swipeState.detached) 0f else swipeState.offset * neighborMultiplier
    val neighborOffset by animateFloatAsState(
        targetValue = neighborTarget,
        animationSpec = magneticSpring(motionLevel),
        label = "AlarmSwipeNeighbor"
    )

    val deleteIcon = rememberVectorPainter(Icons.Default.Delete)

    Box(modifier = modifier.offset { IntOffset(neighborOffset.roundToInt(), 0) }) {
        MotionSwipeRow(
            key = rowKey,
            motionLevel = motionLevel,
            // Either direction deletes, matching the previous HardSwipeRow behaviour.
            onSwipeLeft = onDelete,
            onSwipeRight = onDelete,
            leftContainerColor = SwipeDeleteContainerColor,
            leftIconColor = SwipeDeleteIconColor,
            leftIcon = deleteIcon,
            rightContainerColor = SwipeDeleteContainerColor,
            rightIconColor = SwipeDeleteIconColor,
            rightIcon = deleteIcon,
            onMagneticSwipeChanged = { translationPx, isDetached ->
                swipeState.onSwipeChanged(index, translationPx, isDetached)
            },
            content = content
        )
    }
}

private enum class RevealValue { Closed, OpenStart, OpenEnd }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HardSwipeRow(
    modifier: Modifier = Modifier,
    deleteSwipeFractionOfRow: Float = 0.8f,
    gap: Dp = 8.dp,
    minHeight: Dp = 84.dp,
    radius: Dp = 36.dp,
    shape: Shape = shapes.large,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current

    // Measure content height so the action card matches it (LazyColumn-safe)
    var contentHeightPx by remember { mutableIntStateOf(0) }
    val cardHeight = with(density) {
        if (contentHeightPx == 0) minHeight else contentHeightPx.toDp()
    }

    // Visibility state for “collapse on delete”
    val visibleState = remember { MutableTransitionState(true) }
    var deleteTriggered by remember { mutableStateOf(false) }

    val exitTransition =
        shrinkVertically(
            animationSpec = tween(220, easing = FastOutSlowInEasing),
            shrinkTowards = Alignment.Top
        ) + fadeOut(animationSpec = tween(120))

    // IMPORTANT: keep this OUTSIDE AnimatedVisibility so it doesn't get cancelled
    LaunchedEffect(deleteTriggered) {
        if (deleteTriggered) {
            snapshotFlow {
                visibleState.isIdle && !visibleState.currentState
            }
                .filter { it }
                .first()

            onSwiped()
        }
    }

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier,
        exit = exitTransition
    ) {
        BoxWithConstraints(modifier = Modifier.clipToBounds().clip(shape)) {
            val rowWidthPx = with(density) { maxWidth.toPx() }
            val gapPx = with(density) { gap.toPx() }

            val fraction = deleteSwipeFractionOfRow.coerceIn(0f, 1f)*2
            val deleteSwipePx = (rowWidthPx * fraction).coerceAtLeast(1f)

            val anchors = remember(deleteSwipePx) {
                DraggableAnchors {
                    RevealValue.Closed at 0f
                    RevealValue.OpenStart at deleteSwipePx // swipe right
                    RevealValue.OpenEnd at -deleteSwipePx // swipe left
                }
            }

            val state = remember(anchors) {
                AnchoredDraggableState(
                    initialValue = RevealValue.Closed,
                    anchors = anchors
                )
            }
            
            // Observe state changes and trigger delete animation
            LaunchedEffect(state.currentValue) {
                if (!deleteTriggered && state.currentValue != RevealValue.Closed) {
                    deleteTriggered = true
                    visibleState.targetState = false
                }
            }

            val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
                state = state,
                positionalThreshold = { distance -> distance * 0.5f },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessLow
                )
            )


            val offsetPx by remember { derivedStateOf { state.requireOffset() } }
            val revealedPx = abs(offsetPx).coerceIn(0f, deleteSwipePx)

            val maxActionWidthPx = (rowWidthPx - gapPx).coerceAtLeast(0f)

            val actionWidthPx = (revealedPx - gapPx)
                .coerceIn(0f, maxActionWidthPx)

            val actionWidthDp = with(density) { actionWidthPx.toDp() }

            val isRevealingStart = offsetPx > 0f // swiping right => reveal left side

            val dragMod = if (deleteTriggered) Modifier
            else Modifier.anchoredDraggable(state, Orientation.Horizontal, flingBehavior = flingBehavior)

            Box(modifier = dragMod) {

                if (actionWidthPx > 0.5f) {
                    Card(
                        modifier = Modifier
                            .align(if (isRevealingStart) Alignment.CenterStart else Alignment.CenterEnd)
                            .height(cardHeight)
                            .width(actionWidthDp),
                        shape = RoundedCornerShape(radius),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFF7165)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xff490002)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .offset { IntOffset(offsetPx.roundToInt(), 0) }
                        .onSizeChanged {
                            if (contentHeightPx != it.height) contentHeightPx = it.height
                        }
                ) {
                    content()
                }
            }
        }
    }
}









fun AlarmUi.scheduleText(context: Context): String {
    val hasDate = year != 0 && month != 0 && day != 0
    val isRepeating = daysOfWeek.isRepeating()

    return when {
        // 1️⃣ No schedule at all
        !hasDate && !isRepeating -> {
            "Not Scheduled"
        }

        // 2️⃣ One-time alarm (specific date)
        hasDate -> {
            String.format(
                Locale.getDefault(),
                "%02d/%02d/%04d",
                day,
                month,
                year
            )
        }

        // 3️⃣ Repeating alarm
        else -> {
            daysOfWeek.toString(
                context,
                Weekdays.Order.SUN_TO_SAT
            )
        }
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditorSheet(
    original: AlarmUi,
    draft: AlarmUi,
    onDraftChange: (AlarmUi) -> Unit,
    onCancel: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    viewModel: AlarmViewModel
) {
    val context = LocalContext.current
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }

    // Time picker for editing existing alarm time
    var showTimePicker by remember { mutableStateOf(false) }
    var showSoundPicker by remember { mutableStateOf(false) }
    var showPauseRangePicker by remember { mutableStateOf(false) }
    val ringtoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }

            onDraftChange(
                draft.copy(ringtoneUri = uri?.toString())
            )
        }
    }
    if (showSoundPicker) {
        val defaultRingtoneUri = remember { viewModel.getDefaultRingtoneUri() }
        val intent = remember(draft.ringtoneUri, defaultRingtoneUri) {
            Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM
                )
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, defaultRingtoneUri)

                val existingUri = draft.ringtoneUri?.toUri()

                putExtra(
                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    existingUri
                )
            }
        }

        LaunchedEffect(Unit) {
            ringtoneLauncher.launch(intent)
            showSoundPicker = false
        }
    }

    // Date Picker Logic
    val date = remember(draft.year, draft.month, draft.day) {
        if (draft.year != 0) {
            Calendar.getInstance().apply {
                set(Calendar.YEAR, draft.year)
                set(Calendar.MONTH, draft.month)
                set(Calendar.DAY_OF_MONTH, draft.day)
            }
        } else null
    }

    var showDatePicker by remember { mutableStateOf(false) }

    // Calculate and show next alarm time
    var nextTimeString by remember { mutableStateOf<String?>(null) }
    var isDismissed by remember { mutableStateOf(false) }
    var dismissedDayLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(draft, original) {
        Log.d("AlarmScreen", "Editor recalc triggered. draft.id=${draft.id}, original.upcomingInstanceTime=${original.upcomingInstanceTime?.time}")
        // Check if scheduling fields are identical
        val isUnchanged = draft.hour == original.hour &&
                draft.minutes == original.minutes &&
                draft.daysOfWeek == original.daysOfWeek &&
                draft.year == original.year &&
                draft.month == original.month &&
                draft.day == original.day

        Log.d("AlarmScreen", "isUnchanged=$isUnchanged, has upcomingInstanceTime=${original.upcomingInstanceTime != null}")

        val nextAlarmTime = if (isUnchanged && original.upcomingInstanceTime != null) {
            // Unchanged: Use the database-backed forthcoming instance (respects dismissals)
            original.upcomingInstanceTime
        } else {
            // Changed (or no DB instance): Calculate fresh from NOW
            // We intentionally IGNORE any previous dismissals/skips because modifying the alarm
            // (time, days, etc.) implicitly resets any temporary "skip today" actions.
            val alarm = draft.toProvider(context)
            alarm.getNextAlarmTime(Calendar.getInstance())
        }

        // Check if the NEXT alarm was dismissed
        // Only show as dismissed if we are Unchanged AND there's a matching PREDISMISSED instance
        var dismissedDay: String? = null
        isDismissed = if (isUnchanged) {
            withContext(Dispatchers.IO) {
                val instances = AlarmInstance.getInstancesByAlarmId(
                    context.contentResolver,
                    original.id
                )

                // Find ANY PREDISMISSED instance that is still relevant (future/today valid)
                var validPredismissed: AlarmInstance? = null

                val predismissedInstances = instances.filter {
                    it.mAlarmState == ClockContract.InstancesColumns.PREDISMISSED_STATE
                }

                for (predismissed in predismissedInstances) {
                    val now = Calendar.getInstance()
                    val dismissedTime = predismissed.alarmTime

                    // Check if it's still relevant
                    val isStillFuture = if (dismissedTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                        dismissedTime.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
                        // Today - check if we're still before the alarm time
                        val result = now.timeInMillis < dismissedTime.timeInMillis
                        result
                    } else {
                        // Future date
                        val result = dismissedTime.after(now)
                        result
                    }

                    if (isStillFuture) {
                        validPredismissed = predismissed

                        // Determine the day label
                        val today = now.get(Calendar.DAY_OF_YEAR)
                        val dismissedDayOfYear = dismissedTime.get(Calendar.DAY_OF_YEAR)

                        dismissedDay = when {
                            dismissedTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) && dismissedDayOfYear == today -> "Today"
                            dismissedTime.get(Calendar.YEAR) == now.get(Calendar.YEAR) && dismissedDayOfYear == today + 1 -> "Tomorrow"
                            else -> DateFormat.format("EEE, MMM d", dismissedTime).toString()
                        }
                        break // Found a valid one, stop searching
                    }
                }

                validPredismissed != null
            }
        } else {
            // If changed, we consider it NOT dismissed (reset)
            false
        }

        // Update the dismissed day label
        dismissedDayLabel = dismissedDay

        // Format as Today, Tomorrow, or date
        val now = Calendar.getInstance()
        val today = now.get(Calendar.DAY_OF_YEAR)
        val todayYear = now.get(Calendar.YEAR)
        val alarmDay = nextAlarmTime.get(Calendar.DAY_OF_YEAR)
        val alarmYear = nextAlarmTime.get(Calendar.YEAR)

        nextTimeString = when {
            alarmYear == todayYear && alarmDay == today -> "Today"
            alarmYear == todayYear && alarmDay == today + 1 -> "Tomorrow"
            else -> DateFormat.format("EEE, MMM d", nextAlarmTime).toString()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onDelete,
                contentPadding = PaddingValues(horizontal = 12.dp),
                enabled = true,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.surfaceVariant,
                    contentColor = Color(0xffe86364)
                )
            ) {
                Text("Delete")
            }

            Spacer(Modifier.weight(1f))

            Button(
                onClick = onSave,
                contentPadding = PaddingValues(horizontal = 12.dp),
                enabled = true
            ) {
                Text("Save")
            }
        }

        // Content
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            // Big time row (tap to change time)
            val cal = remember(draft.hour, draft.minutes) {
                Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, draft.hour)
                    set(Calendar.MINUTE, draft.minutes)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(shapes.medium)
                        .clickable {
                            showTimePicker = true
                        },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = DateFormat.format(
                            if (viewModel.is24HourFormat()) "kk:mm" else "h:mm aa",
                            cal
                        ).toString(),
                        fontSize = 40.sp,
                        fontFamily = feldmanFont,
                        color = colorScheme.onSurface
                    )




                }



                Spacer(Modifier.weight(1f))

                Row(
                    modifier = Modifier
                        .clip(shapes.medium)
                        .clickable { showDatePicker = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_calendar_clock),
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(Modifier.width(12.dp))

                    Column(Modifier.padding(vertical = 8.dp)) {

                        if (date != null) {
                            Text(
                                text = DateFormat
                                    .format("MMM d, yyyy", date)
                                    .toString()
                            )
                        }
                        else {
                            Text(
                                text = "Schedule alarm",
                                style = typography.bodyLarge
                            )
                        }
                    }

                    if (date != null) {
                        IconButton(
                            onClick = {
                                onDraftChange(
                                    draft.copy(year = 0, month = 0, day = 0)
                                )
                            }
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear Date"
                            )
                        }
                    }
                }
            }


            if (nextTimeString != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Upcoming alarm\n${nextTimeString!!}",
                        style = typography.bodyMedium,
                        fontFamily = feldmanFont(weight = 450, round = 500f),
                        fontSize = 15.sp,
                        color = colorScheme.onBackground,
                    )

                    if (isDismissed && dismissedDayLabel != null) {
                        // Show "Enable for Today/Tomorrow" chip
                        AssistChip(
                            onClick = {
                                // Optimistic update: immediately hide the chip
                                isDismissed = false
                                dismissedDayLabel = null
                                // Then do the actual undismiss
                                viewModel.undismissAlarm(original)
                            },
                            label = {
                                Text("Enable for $dismissedDayLabel")
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = colorScheme.primaryContainer,
                                labelColor = colorScheme.onPrimaryContainer,
                                leadingIconContentColor = colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }

        // Pause Alarm
        Spacer(Modifier.height(8.dp))
        val isPaused = draft.pauseStartTime > 0L && draft.pauseEndTime > 0L

        if (isPaused) {
            // Show paused range with clear button
            val startStr = DateFormat.getDateFormat(context).format(java.util.Date(draft.pauseStartTime))
            val endStr = DateFormat.getDateFormat(context).format(java.util.Date(draft.pauseEndTime))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Paused: $startStr - $endStr",
                    style = typography.bodyMedium,
                    color = colorScheme.error
                )
                IconButton(
                    onClick = {
                        onDraftChange(draft.copy(pauseStartTime = 0, pauseEndTime = 0))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear pause",
                        tint = colorScheme.error
                    )
                }
            }
        } else {
            TextButton(
                onClick = { showPauseRangePicker = true },
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Pause alarm")
            }
        }


        Spacer(Modifier.height(12.dp))

        RepeatDaysChips(
            daysOfWeek = draft.daysOfWeek,
            onChange = { newDays ->
                if (newDays.mBits != 0) {
                    onDraftChange(draft.copy(daysOfWeek = newDays, year = 0, month = 0, day = 0))
                } else {
                    onDraftChange(draft.copy(daysOfWeek = newDays))
                }
            }
        )

        Spacer(Modifier.height(18.dp))

        AlarmSettingsGroup(
            draft = draft,
            onDraftChange = onDraftChange,
            onPickSound = { showSoundPicker = true }
        )

        Spacer(Modifier.height(18.dp))
    }




    if (showTimePicker) {
        KeyboardFirstTimePickerDialog(
            title = "Edit alarm",
            initialHour = draft.hour,
            initialMinute = draft.minutes,
            is24Hour = viewModel.is24HourFormat(),
            onDismissRequest = { showTimePicker = false },
            onConfirm = { hour, minute ->
                onDraftChange(
                    // IMPORTANT: Preserve year/month/day when updating time
                    draft.copy(
                        hour = hour,
                        minutes = minute,
                        year = draft.year,
                        month = draft.month,
                        day = draft.day,
                        enabled = true
                    )
                )
                showTimePicker = false
            }
        )
    }
    if (showDatePicker) {
        val datePickerState = key(original.id) {
            rememberDatePickerState(
                initialSelectedDateMillis = date?.timeInMillis,
                initialDisplayedMonthMillis = date?.timeInMillis ?: System.currentTimeMillis(),
                yearRange = currentYear..2100
            )
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val newDate = Calendar.getInstance().apply { timeInMillis = millis }
                            onDraftChange(draft.copy(
                                year = newDate.get(Calendar.YEAR),
                                month = newDate.get(Calendar.MONTH),
                                day = newDate.get(Calendar.DAY_OF_MONTH),
                                daysOfWeek = Weekdays.NONE
                            ))
                        }
                        showDatePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showPauseRangePicker) {
        val dateRangePickerState = key(original.id) {
            rememberDateRangePickerState(
                initialSelectedStartDateMillis = if (draft.pauseStartTime > 0L) draft.pauseStartTime else null,
                initialSelectedEndDateMillis = if (draft.pauseEndTime > 0L) draft.pauseEndTime else null,
                initialDisplayedMonthMillis = if (draft.pauseStartTime > 0L) draft.pauseStartTime else System.currentTimeMillis(),
                yearRange = currentYear..2100
            )
        }
        
        DatePickerDialog(
            onDismissRequest = { showPauseRangePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val start = dateRangePickerState.selectedStartDateMillis
                        val end = dateRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            // DateRangePicker returns UTC midnight. We need to convert this to local
                            // and make the range inclusive of the entire end day.
                            
                            val startCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = start
                            }
                            val endCal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
                                timeInMillis = end
                            }

                            val localStart = Calendar.getInstance().apply {
                                set(startCal.get(Calendar.YEAR), startCal.get(Calendar.MONTH), startCal.get(Calendar.DAY_OF_MONTH), 0, 0, 0)
                                set(Calendar.MILLISECOND, 0)
                            }.timeInMillis

                            val localEnd = Calendar.getInstance().apply {
                                set(endCal.get(Calendar.YEAR), endCal.get(Calendar.MONTH), endCal.get(Calendar.DAY_OF_MONTH), 23, 59, 59)
                                set(Calendar.MILLISECOND, 999)
                            }.timeInMillis

                            onDraftChange(draft.copy(pauseStartTime = localStart, pauseEndTime = localEnd))
                        }
                        showPauseRangePicker = false
                    }
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPauseRangePicker = false }) { Text("Cancel") }
            }
        ) {
            DateRangePicker(state = dateRangePickerState)
        }
    }
}
/** Which duration picker dialog the settings group currently has open. */
private enum class AlarmDurationPicker { CRESCENDO, SNOOZE, AUTO_SILENCE }

private val AlarmCardTopShape = RoundedCornerShape(16.dp, 16.dp, 2.dp, 2.dp)
private val AlarmCardMiddleShape = RoundedCornerShape(2.dp)
private val AlarmCardBottomShape = RoundedCornerShape(2.dp, 2.dp, 16.dp, 16.dp)
private val AlarmCardSingleShape = RoundedCornerShape(16.dp)

private fun alarmCardShape(index: Int, count: Int) = when {
    count == 1 -> AlarmCardSingleShape
    index == 0 -> AlarmCardTopShape
    index == count - 1 -> AlarmCardBottomShape
    else -> AlarmCardMiddleShape
}

private val SnoozeDurationOptions = AlarmDurationOptions.Snooze
private val AutoSilenceOptions = AlarmDurationOptions.AutoSilence
private val CrescendoDurationOptions = AlarmDurationOptions.Crescendo

@Composable
fun AlarmSettingsGroup(
    draft: AlarmUi,
    onDraftChange: (AlarmUi) -> Unit,
    onPickSound: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember(context) { ClockApplication.getDefaultSharedPreferences(context) }

    // The global "enable per alarm ..." switches decide which overrides the user may set here.
    // When one is off the alarm just follows the global setting, so we hide the row entirely.
    val perAlarmVolume = remember(prefs) { SettingsDAO.isPerAlarmVolumeEnabled(prefs) }
    val perAlarmCrescendo = remember(prefs) { SettingsDAO.isPerAlarmCrescendoDurationEnabled(prefs) }
    val perAlarmAutoSilence = remember(prefs) { SettingsDAO.isPerAlarmAutoSilenceEnabled(prefs) }
    val flashlightEnabled = remember(prefs) { SettingsDAO.shouldTurnOnBackFlashForTriggeredAlarm(prefs) }

    val maxVolume = remember(context) {
        (context.getSystemService(Context.AUDIO_SERVICE) as AudioManager)
            .getStreamMaxVolume(AudioManager.STREAM_ALARM)
    }

    var openPicker by remember { mutableStateOf<AlarmDurationPicker?>(null) }

    // Challenges live in their own preference store keyed by alarm id, not on the alarm row,
    // so they are read and written here rather than travelling through the draft.
    var challenge by remember(draft.id) {
        mutableStateOf(AlarmChallengePrefs.get(context, draft.id))
    }
    var snoozePolicy by remember(draft.id) {
        mutableStateOf(AlarmChallengePrefs.getSnoozePolicy(context, draft.id))
    }
    var showChallengePicker by remember { mutableStateOf(false) }
    var showSnoozePolicyPicker by remember { mutableStateOf(false) }

    // Collect the rows that are actually visible first, so the rounded top/bottom corners follow
    // the group's real extent instead of whichever rows happen to be hidden.
    val rows = buildList<@Composable (RoundedCornerShape) -> Unit> {
        add { shape ->
            AlarmNameCard(
                label = draft.label.orEmpty(),
                onLabelChange = { onDraftChange(draft.copy(label = it)) },
                shape = shape
            )
        }

        add { shape ->
            SoundRowCard(
                ringtoneUri = draft.ringtoneUri,
                onPickSound = onPickSound,
                shape = shape
            )
        }

        if (perAlarmVolume) {
            add { shape ->
                AlarmVolumeCard(
                    volume = draft.alarmVolume.coerceIn(0, maxVolume),
                    maxVolume = maxVolume,
                    onVolumeChange = { onDraftChange(draft.copy(alarmVolume = it)) },
                    shape = shape
                )
            }
        }

        if (perAlarmCrescendo) {
            add { shape ->
                AlarmValueCard(
                    icon = R.drawable.ic_volume_down,
                    title = "Increase volume gradually",
                    value = CrescendoDurationOptions.labelFor(draft.crescendoDuration),
                    onClick = { openPicker = AlarmDurationPicker.CRESCENDO },
                    shape = shape
                )
            }
        }

        add { shape ->
            VibrateCard(
                vibrate = draft.vibrate,
                onVibrateChange = { onDraftChange(draft.copy(vibrate = it)) },
                shape = shape
            )
        }

        add { shape ->
            AlarmValueCard(
                icon = R.drawable.ic_shield_lock,
                title = "Dismiss challenge",
                value = challenge.label,
                onClick = { showChallengePicker = true },
                shape = shape
            )
        }

        // Only meaningful once there is a challenge to apply.
        if (challenge != AlarmChallenge.NONE) {
            add { shape ->
                AlarmValueCard(
                    icon = R.drawable.ic_snooze,
                    title = "Challenge to snooze",
                    value = snoozePolicy.label,
                    onClick = { showSnoozePolicyPicker = true },
                    shape = shape
                )
            }
        }

        if (flashlightEnabled) {
            add { shape ->
                AlarmSwitchCard(
                    icon = R.drawable.ic_flash,
                    title = "Flashlight",
                    checked = draft.flash,
                    onCheckedChange = { onDraftChange(draft.copy(flash = it)) },
                    shape = shape
                )
            }
        }

        if (perAlarmAutoSilence) {
            add { shape ->
                AlarmValueCard(
                    icon = R.drawable.ic_snooze,
                    title = "Snooze duration",
                    value = SnoozeDurationOptions.labelFor(draft.snoozeDuration),
                    onClick = { openPicker = AlarmDurationPicker.SNOOZE },
                    shape = shape
                )
            }

            add { shape ->
                AlarmValueCard(
                    icon = R.drawable.ic_av_timer,
                    title = "Silence after",
                    value = AutoSilenceOptions.labelFor(draft.autoSilenceDuration),
                    onClick = { openPicker = AlarmDurationPicker.AUTO_SILENCE },
                    shape = shape
                )
            }
        }

        // Only occasional alarms can be deleted after they ring; repeating ones always come back.
        if (!draft.daysOfWeek.isRepeating()) {
            add { shape ->
                AlarmSwitchCard(
                    icon = R.drawable.ic_delete,
                    title = "Delete after it rings",
                    checked = draft.deleteAfterUse,
                    onCheckedChange = { onDraftChange(draft.copy(deleteAfterUse = it)) },
                    shape = shape
                )
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        rows.forEachIndexed { index, row ->
            row(alarmCardShape(index, rows.size))
        }
    }

    if (showChallengePicker) {
        AlarmRadioDialog(
            title = "Dismiss challenge",
            options = AlarmChallenge.entries,
            labelOf = { it.label },
            selected = challenge,
            onSelect = {
                challenge = it
                AlarmChallengePrefs.set(context, draft.id, it)
            },
            onDismiss = { showChallengePicker = false }
        )
    }

    if (showSnoozePolicyPicker) {
        AlarmRadioDialog(
            title = "Challenge to snooze",
            description = "Snoozing is unlimited, so without this the challenge can be " +
                "deferred forever.",
            options = AlarmSnoozePolicy.entries,
            labelOf = { it.label },
            selected = snoozePolicy,
            onSelect = {
                snoozePolicy = it
                AlarmChallengePrefs.setSnoozePolicy(context, draft.id, it)
            },
            onDismiss = { showSnoozePolicyPicker = false }
        )
    }

    when (openPicker) {
        AlarmDurationPicker.CRESCENDO -> AlarmOptionsDialog(
            title = "Increase volume gradually",
            options = CrescendoDurationOptions,
            selected = draft.crescendoDuration,
            onSelect = { onDraftChange(draft.copy(crescendoDuration = it)) },
            onDismiss = { openPicker = null }
        )

        AlarmDurationPicker.SNOOZE -> AlarmOptionsDialog(
            title = "Snooze duration",
            options = SnoozeDurationOptions,
            selected = draft.snoozeDuration,
            onSelect = { onDraftChange(draft.copy(snoozeDuration = it)) },
            onDismiss = { openPicker = null }
        )

        AlarmDurationPicker.AUTO_SILENCE -> AlarmOptionsDialog(
            title = "Silence after",
            options = AutoSilenceOptions,
            selected = draft.autoSilenceDuration,
            onSelect = { onDraftChange(draft.copy(autoSilenceDuration = it)) },
            onDismiss = { openPicker = null }
        )

        null -> Unit
    }
}

@Composable
private fun AlarmOptionsDialog(
    title: String,
    options: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (label, value) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(shapes.medium)
                            .clickable {
                                onSelect(value)
                                onDismiss()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = value == selected,
                            onClick = {
                                onSelect(value)
                                onDismiss()
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(text = label, style = typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Row showing a setting and its current value; tapping it opens a picker. */
/** Single-choice list dialog; selecting an option applies it and closes. */
@Composable
private fun <T> AlarmRadioDialog(
    title: String,
    options: List<T>,
    labelOf: (T) -> String,
    selected: T,
    onSelect: (T) -> Unit,
    onDismiss: () -> Unit,
    description: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                if (description != null) {
                    Text(
                        text = description,
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }
                options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onSelect(option)
                                onDismiss()
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(labelOf(option))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun AlarmValueCard(
    icon: Int,
    title: String,
    value: String,
    onClick: () -> Unit,
    shape: RoundedCornerShape = AlarmCardMiddleShape
) {
    val colors = colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = title,
                style = typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = value,
                style = typography.bodyMedium,
                color = colors.onBackground
            )
        }
    }
}

/** Row with a trailing switch, matching [VibrateCard]. */
@Composable
fun AlarmSwitchCard(
    icon: Int,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    shape: RoundedCornerShape = AlarmCardMiddleShape
) {
    val colors = colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = title,
                style = typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )

            MaterialSwitch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
fun AlarmVolumeCard(
    volume: Int,
    maxVolume: Int,
    onVolumeChange: (Int) -> Unit,
    shape: RoundedCornerShape = AlarmCardMiddleShape
) {
    val colors = colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(R.drawable.ic_volume_up),
                    contentDescription = null,
                    tint = colors.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )

                Spacer(Modifier.width(16.dp))

                Text(
                    text = "Alarm volume",
                    style = typography.bodyLarge,
                    color = colors.onBackground,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "$volume / $maxVolume",
                    style = typography.bodyMedium,
                    color = colors.onBackground
                )
            }

            Slider(
                value = volume.toFloat(),
                onValueChange = { onVolumeChange(it.roundToInt()) },
                valueRange = 0f..maxVolume.toFloat(),
                steps = (maxVolume - 1).coerceAtLeast(0)
            )
        }
    }
}

@Composable
fun SoundRowCard(
    ringtoneUri: String?,
    onPickSound: () -> Unit,
    shape: RoundedCornerShape = AlarmCardMiddleShape
) {
    val context = LocalContext.current
    val colors = colorScheme

    val ringtoneTitle = remember(ringtoneUri) {
        ringtoneUri?.let {
            RingtoneManager.getRingtone(context, it.toUri())
                ?.getTitle(context)
        } ?: "Default"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onPickSound() }
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_notifications_active),
                contentDescription = null,
                tint = colors.onSurfaceVariant
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = "Alarm sound",
                style = typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = ringtoneTitle,
                style = typography.bodyMedium,
                color = colors.onBackground
            )
        }
    }
}
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MaterialSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        thumbContent = {
            AnimatedContent(
                targetState = checked,
                transitionSpec = {
                    (scaleIn(initialScale = 0.6f) + fadeIn()) togetherWith fadeOut()
                },
                label = "SwitchIconAnim"
            ) { state ->
                Icon(
                    imageVector = if (state) Icons.Filled.Check else Icons.Filled.Close,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        },
        colors = SwitchDefaults.colors(
            checkedIconColor = colorScheme.primary,
        ),
        modifier = modifier
    )
}

@Composable
fun AlarmNameCard(
    label: String,
    onLabelChange: (String) -> Unit,
    shape: RoundedCornerShape = AlarmCardTopShape
) {
    val colors = colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_label),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = "Alarm name",
                style = typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )

            InlineAlarmNameField(
                value = label,
                onValueChange = onLabelChange
            )


        }
    }
}
@Composable
fun InlineAlarmNameField(
    value: String,
    onValueChange: (String) -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    val indicatorColor by animateColorAsState(
        if (focused) colorScheme.primary
        else Color.Transparent,
        label = "indicator"
    )

    CompositionLocalProvider(
        LocalTextSelectionColors provides TextSelectionColors(
            handleColor = colorScheme.primary,
            backgroundColor = colorScheme.primary.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 96.dp, max = 160.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = typography.bodyLarge.copy(
                    color = colorScheme.onSurface,
                    textAlign = TextAlign.End
                ),
                cursorBrush = SolidColor(colorScheme.primary),
                modifier = Modifier
                    .height(36.dp)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.CenterEnd
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Alarm",
                                style = typography.bodyLarge,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // underline
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(indicatorColor)
            )
        }
    }
}

@Composable
fun VibrateCard(
    vibrate: Boolean,
    onVibrateChange: (Boolean) -> Unit,
    shape: RoundedCornerShape = AlarmCardBottomShape
) {
    val colors = colorScheme

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colors.surfaceVariant
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVibrateChange(!vibrate) }
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_vibrate),
                contentDescription = null,
                tint = colors.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )

            Spacer(Modifier.width(16.dp))

            Text(
                text = "Vibrate",
                style = typography.bodyLarge,
                color = colors.onBackground,
                modifier = Modifier.weight(1f)
            )

            MaterialSwitch(
                checked = vibrate,
                onCheckedChange = onVibrateChange
            )
        }
    }
}

@Composable
fun RepeatDaysChips(
    daysOfWeek: Weekdays,
    onChange: (Weekdays) -> Unit
) {
    val days = listOf(
        Calendar.SUNDAY to "S",
        Calendar.MONDAY to "M",
        Calendar.TUESDAY to "T",
        Calendar.WEDNESDAY to "W",
        Calendar.THURSDAY to "T",
        Calendar.FRIDAY to "F",
        Calendar.SATURDAY to "S"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        days.forEachIndexed { index, (day, label) ->
            val selected = daysOfWeek.isBitOn(day)
            val backgroundColor by animateColorAsState(
                if (selected) colorScheme.primaryContainer
                else colorScheme.surfaceContainerLowest,
                label = "dayBg"
            )
            val textColor by animateColorAsState(
                if (selected) colorScheme.onPrimaryContainer
                else colorScheme.onSurfaceVariant,
                label = "dayText"
            )

            Surface(
                modifier = Modifier
                    .height(height = 40.dp)
                    .weight(1f)
                    .clip(if (selected) RoundedCornerShape(50) else RoundedCornerShape(12.0.dp))
                    .clickable {
                        val newDays = daysOfWeek.setBit(day, !selected)
                        onChange(newDays)
                    },
                shape = shapes.medium,
                color = backgroundColor,
                tonalElevation = if (selected) 2.dp else 0.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = typography.titleMedium,
                        color = textColor
                    )
                }
            }
            if (index != days.size - 1) {
                Spacer(Modifier.width(4.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    title: (@Composable () -> Unit)? = null,
    onDismissRequest: () -> Unit,
    confirmButton: @Composable (() -> Unit),
    dismissButton: @Composable (() -> Unit)? = null,
    containerColor: Color = colorScheme.surface,
    content: @Composable () -> Unit,
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = true),
        content = {
            Surface(
                shape = RoundedCornerShape(36.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .wrapContentWidth()
                    .wrapContentHeight()
                    .background(
                        shape = RoundedCornerShape(36.dp),
                        color = containerColor
                    )
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (title != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 20.dp)
                        ) {
                            CompositionLocalProvider(LocalTextStyle provides typography.labelMedium) {
                                title()
                            }
                        }
                    }

                    content()
                    Row(
                        modifier = Modifier
                            .height(40.dp)
                            .fillMaxWidth(),
                    ) {
                        Spacer(modifier = Modifier.weight(1f))
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    )
}

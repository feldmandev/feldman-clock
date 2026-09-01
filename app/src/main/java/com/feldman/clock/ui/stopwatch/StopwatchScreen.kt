package com.feldman.clock.ui.stopwatch

import com.feldman.clock.R
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.ui.components.ClockAppBarIconButton
import com.feldman.clock.ui.components.ClockPageTitle
import com.feldman.clock.ui.components.ExpressiveContentSurface

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feldman.clock.core.data.stopwatch.Lap
import com.feldman.clock.core.data.Stopwatch
import com.feldman.clock.core.util.isScreenWide
import com.feldman.motion.FontAxes
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionButtonState
import com.feldman.motion.LocalBottomBarHeight
import com.feldman.clock.app.navigation.Dest
import com.feldman.motion.feldmanFont

@Composable
private fun StopwatchNarrowLayout(
    viewModel: StopwatchViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning = uiState.status == Stopwatch.State.RUNNING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp)
            .padding(bottom = 180.dp)
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        // Timer display
        Text(
            text = formatStopwatchTime(uiState.totalTimeInMillis),
            style = typography.displayLarge.copy(
                fontSize = 80.sp,
                fontWeight = FontWeight.Light,
                fontFamily = feldmanFont,
                fontFeatureSettings = "tnum"
            ),
            color = colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            autoSize = TextAutoSize.StepBased(
                minFontSize = 36.sp,
                maxFontSize = 80.sp,
                stepSize = 1.sp
            ),
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .height(150.dp),
        ) {
            if (uiState.laps.size > 1) {
                itemsIndexed(uiState.laps) { index, lap ->
                    LapItem(
                        lap = lap,
                        index = index,
                        total = uiState.laps.size,
                        isLatest = lap.lapNumber == uiState.laps.size
                    )
                    Spacer(Modifier.height(2.dp))
                }
            }
        }

        Spacer(Modifier.height(32.dp))

        // START / STOP BUTTON
        MotionButton(
            text = if (isRunning) "Stop" else "Start",
            icon = if (isRunning) "pause" else "play_arrow",
            onClick = { viewModel.toggleStartPause() },
            selected = isRunning,
            modifier = Modifier.fillMaxWidth(),
            height = 120.dp,
            iconSize = 36.dp,
            fontSize = 30.sp,
            defaultState = MotionButtonState(
                backgroundColor = colorScheme.tertiaryContainer,
                contentColor = colorScheme.onTertiaryContainer,
                cornerRadius = 60f, // Converted to Float
                fontAxes = FontAxes(weight = 500, width = 100f, round = 300f)
            ),
            selectedState = MotionButtonState(
                backgroundColor = Color(0xFFFF7165),
                contentColor = Color(0xff490002),
                cornerRadius = 36f,
                fontAxes = FontAxes(weight = 500, width = 110f, round = 300f)
            ),
            selectedPressedState = MotionButtonState(
                backgroundColor = Color(0xFFFF7165),
                contentColor = Color(0xff490002),
                cornerRadius = 24f,
                fontAxes = FontAxes(weight = 400, width = 90f, round = 300f)
            )
        )

        if (uiState.status == Stopwatch.State.PAUSED || isRunning) {
            Spacer(Modifier.height(6.dp))
            // RESET BUTTON
            MotionButton(
                text = "Reset",
                icon = "restart_alt",
                onClick = { viewModel.reset() },
                modifier = Modifier.fillMaxWidth(),
                height = 100.dp,
                iconSize = 36.dp,
                fontSize = 30.sp,
                defaultState = MotionButtonState(
                    backgroundColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurfaceVariant,
                    cornerRadius = 50f,
                    fontAxes = FontAxes(weight = 500, width = 100f, round = 300f)
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = colorScheme.surfaceVariant,
                    contentColor = colorScheme.onSurfaceVariant,
                    cornerRadius = 32f,
                    fontAxes = FontAxes(weight = 400, width = 125f, round = 300f)
                )
            )
        }

        if (isRunning) {
            Spacer(Modifier.height(6.dp))
            // LAP BUTTON
            MotionButton(
                text = "Lap",
                icon = painterResource(R.drawable.ic_laps),
                onClick = { viewModel.lap() },
                modifier = Modifier.fillMaxWidth(),
                height = 100.dp,
                iconSize = 36.dp,
                fontSize = 30.sp,
                defaultState = MotionButtonState(
                    backgroundColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    cornerRadius = 50f,
                    fontAxes = FontAxes(weight = 500, width = 100f, round = 300f)
                ),
                defaultPressedState = MotionButtonState(
                    backgroundColor = colorScheme.secondaryContainer,
                    contentColor = colorScheme.onSecondaryContainer,
                    cornerRadius = 32f,
                    fontAxes = FontAxes(weight = 400, width = 125f, round = 300f)
                )
            )
        }
    }
}
@Composable
private fun StopwatchWideLayout(
    viewModel: StopwatchViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // ⬅️ Left side: 60% width for Time (top) and Laps list (bottom)
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Time display
            Text(
                text = formatStopwatchTime(uiState.totalTimeInMillis),
                style = typography.displayLarge.copy(
                    fontSize = 64.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = feldmanFont,
                    fontFeatureSettings = "tnum"
                ),
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Laps list (if any)
            if (uiState.laps.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    itemsIndexed(uiState.laps) { index, lap ->
                        LapItem(
                            lap = lap,
                            index = index,
                            total = uiState.laps.size,
                            isLatest = lap.lapNumber == uiState.laps.size
                        )
                    }
                }
            } else {
                // If no laps, show a placeholder or empty spacer
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No laps",
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
        }

        // ➡️ Right side: 40% width for the three buttons stacked vertically
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            StopwatchButtons(viewModel, spacing = 8.dp)
        }
    }
}

@Composable
private fun ColumnScope.StopwatchButtons(
    viewModel: StopwatchViewModel,
    spacing: androidx.compose.ui.unit.Dp = 12.dp
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRunning = uiState.status == Stopwatch.State.RUNNING

    // We can dynamically scale icon and font size based on space, but using solid values of 24.dp/20.sp is highly readable and fits all sizes.
    val buttonIconSize = 28.dp
    val buttonFontSize = 22.sp

    // 1. START / STOP BUTTON
    MotionButton(
        text = if (isRunning) "Stop" else "Start",
        icon = painterResource(if (isRunning) R.drawable.ic_fab_pause else R.drawable.ic_fab_play),
        onClick = { viewModel.toggleStartPause() },
        selected = isRunning,
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .heightIn(max = 120.dp),
        height = Dp.Unspecified,
        width = Dp.Unspecified,
        iconSize = buttonIconSize,
        fontSize = buttonFontSize,
        // Idle / Unselected (Start)
        defaultState = MotionButtonState(
            backgroundColor = colorScheme.tertiaryContainer,
            contentColor = colorScheme.onTertiaryContainer,
            cornerRadius = 50f,
            fontAxes = FontAxes(weight = 500, width = 100f)
        ),
        // Active / Selected (Stop)
        selectedState = MotionButtonState(
            backgroundColor = Color(0xFFFF7165),
            contentColor = Color(0xFF490002),
            cornerRadius = 32f,
            fontAxes = FontAxes(weight = 500, width = 110f)
        ),
        // Pressed State (Squish effect)
        selectedPressedState = MotionButtonState(
            backgroundColor = Color(0xFFFF7165),
            contentColor = Color(0xFF490002),
            cornerRadius = 24f,
            fontAxes = FontAxes(weight = 400, width = 90f)
        )
    )

    Spacer(Modifier.height(spacing))

    // 2. RESET BUTTON
    if (uiState.status != Stopwatch.State.RESET) {
        MotionButton(
            text = "Reset",
            icon = painterResource(R.drawable.ic_reset),
            onClick = { viewModel.reset() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 100.dp),
            height = Dp.Unspecified,
            width = Dp.Unspecified,
            iconSize = buttonIconSize,
            fontSize = buttonFontSize,
            defaultState = MotionButtonState(
                backgroundColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant,
                cornerRadius = 50f,
                fontAxes = FontAxes(weight = 500, width = 100f)
            ),
            defaultPressedState = MotionButtonState(
                backgroundColor = colorScheme.surfaceVariant,
                contentColor = colorScheme.onSurfaceVariant,
                cornerRadius = 32f,
                fontAxes = FontAxes(weight = 400, width = 125f)
            )
        )
    } else {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 100.dp)
        )
    }

    Spacer(Modifier.height(spacing))

    // 3. LAP BUTTON
    if (isRunning) {
        MotionButton(
            text = "Lap",
            icon = painterResource(R.drawable.ic_laps),
            onClick = { viewModel.lap() },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 100.dp),
            height = Dp.Unspecified,
            width = Dp.Unspecified,
            iconSize = buttonIconSize,
            fontSize = buttonFontSize,
            defaultState = MotionButtonState(
                backgroundColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                cornerRadius = 50f,
                fontAxes = FontAxes(weight = 500, width = 100f)
            ),
            defaultPressedState = MotionButtonState(
                backgroundColor = colorScheme.secondaryContainer,
                contentColor = colorScheme.onSecondaryContainer,
                cornerRadius = 32f,
                fontAxes = FontAxes(weight = 400, width = 125f)
            )
        )
    } else {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .heightIn(max = 100.dp)
        )
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StopwatchScreen(
    viewModel: StopwatchViewModel = viewModel(),
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean
) {
    val isWide = isScreenWide()
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { ClockPageTitle("Stopwatch") },
                navigationIcon = {
                    if (!isTab) {
                        ClockAppBarIconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    // Laps are only worth sharing once at least one full lap has been recorded;
                    // the in-progress lap is always present, hence the size check against 1.
                    if (uiState.laps.size > 1) {
                        ClockAppBarIconButton(
                            onClick = { shareLaps(context, uiState.laps, uiState.totalTimeInMillis) }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = "Share laps")
                        }
                    }
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
        ExpressiveContentSurface(modifier = Modifier.padding(innerPadding)) {
            Box(modifier = Modifier.fillMaxSize().padding(top = 12.dp)) {
            if (isWide) {
                StopwatchWideLayout(viewModel)
            } else {
                StopwatchNarrowLayout(viewModel)
            }
        }
        }
    }
}

/**
 * Hands the recorded laps to the system share sheet as plain text.
 *
 * Laps arrive newest-first, so they are reversed to read the way they were run.
 */
private fun shareLaps(context: Context, laps: List<Lap>, totalTimeInMillis: Long) {
    if (laps.isEmpty()) return

    val body = buildString {
        appendLine("Stopwatch: ${formatStopwatchTime(totalTimeInMillis)}")
        appendLine()
        laps.reversed().forEach { lap ->
            appendLine(
                "Lap ${lap.lapNumber}\t" +
                    "${formatStopwatchTime(lap.lapTime)}\t" +
                    formatStopwatchTime(lap.accumulatedTime)
            )
        }
    }

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Stopwatch laps")
        putExtra(Intent.EXTRA_TEXT, body.trim())
    }
    context.startActivity(Intent.createChooser(intent, "Share laps"))
}

@Composable
fun LapItem(
    lap: Lap,
    index: Int,
    total: Int,
    isLatest: Boolean
) {
    val isFirst = index == 0
    val isLast = index == total - 1

    val shape = RoundedCornerShape(
        topStart = if (isFirst) 16.dp else 4.dp,
        topEnd = if (isFirst) 16.dp else 4.dp,
        bottomStart = if (isLast) 16.dp else 4.dp,
        bottomEnd = if (isLast) 16.dp else 4.dp
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = String.format("#%02d", lap.lapNumber),
                style = typography.bodyMedium.copy(fontFamily = feldmanFont(weight = 350)),
                color = if (isLatest) colorScheme.primary else colorScheme.onSurfaceVariant
            )

            Text(
                text = formatStopwatchTime(lap.lapTime),
                style = typography.bodyLarge.copy(fontFamily = feldmanFont),
                color = if (isLatest) colorScheme.primary else colorScheme.onSurface
            )

            Text(
                text = formatStopwatchTime(lap.accumulatedTime),
                style = typography.bodyMedium.copy(fontFamily = feldmanFont),
                color = colorScheme.onSurfaceVariant
            )
        }
    }

}


fun formatStopwatchTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundreds = (millis % 1000) / 10
    
    // If hours > 0?
    val hours = minutes / 60
    if (hours > 0) {
         val remMinutes = minutes % 60
         return String.format("%d:%02d:%02d.%02d", hours, remMinutes, seconds, hundreds)
    }
    
    return String.format("%02d:%02d.%02d", minutes, seconds, hundreds)
}

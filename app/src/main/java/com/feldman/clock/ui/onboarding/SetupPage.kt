package com.feldman.clock.ui.onboarding

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.feldman.clock.R
import com.feldman.motion.BouncingPhysicsModel
import com.feldman.motion.BouncingShapeSpec
import com.feldman.motion.BouncingShapes
import com.feldman.motion.MotionButton
import com.feldman.motion.MotionButtonState

@Composable
private fun rememberPermissionState(
    checkCurrentState: () -> Boolean,
    onGrantedAutomatically: () -> Unit
): Boolean {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isGranted by remember { mutableStateOf(checkCurrentState()) }
    var hasAutoNavigated by remember { mutableStateOf(false) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val currentlyGranted = checkCurrentState()
                isGranted = currentlyGranted
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(isGranted) {
        if (isGranted && !hasAutoNavigated) {
            hasAutoNavigated = true
            onGrantedAutomatically()
        }
    }
    return isGranted
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupPageSurface(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        BouncingShapes(
            variation = 1,
            areaExtraFraction = 0.500f,
            speedMultiplier = 1.000f,
            physicsModel = BouncingPhysicsModel.Animated,
            gravityMetersPerSecondSquared = 10.000f,
            metersAcrossShortSide = 2.000f,
            shapeSpecs = listOf(
                BouncingShapeSpec(
                    shape = MaterialShapes.Cookie9Sided,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                    xFraction = 0.934f,
                    yFraction = 1.112f,
                    scaleFraction = 2.495f,
                    velocityX = 0.000f,
                    velocityY = 0.000f,
                    rotationDegrees = 47.310f,
                    rotationSpeed = 0.000f,
                    gravity = 0.20000f,
                    horizontalAttraction = 0.00015f,
                    horizontalDamping = 0.99500f,
                    maxHorizontalSpeed = 12.000f,
                    isTrampoline = true,
                    bounces = false,
                    massKg = 0.100f,
                    initialVelocityXMetersPerSecond = 0.000f,
                    initialVelocityYMetersPerSecond = 0.000f,
                    restitution = 0.900f,
                    springConstant = 200.000f,
                    springDamping = 1.000f,
                    morphTargets = emptyList(),
                    filled = true,
                    strokeWidthFraction = 0.00850f
                ),
                BouncingShapeSpec(
                    shape = MaterialShapes.Pill,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    xFraction = 1.105f,
                    yFraction = 0.348f,
                    scaleFraction = 0.750f,
                    velocityX = 4.000f,
                    velocityY = 2.000f,
                    rotationDegrees = 0.000f,
                    rotationSpeed = 0.300f,
                    gravity = 0.20000f,
                    horizontalAttraction = 0.00015f,
                    horizontalDamping = 0.99500f,
                    maxHorizontalSpeed = 12.000f,
                    isTrampoline = false,
                    bounces = true,
                    massKg = 1.000f,
                    initialVelocityXMetersPerSecond = 0.500f,
                    initialVelocityYMetersPerSecond = 0.200f,
                    restitution = 0.900f,
                    springConstant = 200.000f,
                    springDamping = 1.000f,
                    morphTargets = listOf(
                        MaterialShapes.Sunny,
                        MaterialShapes.Pill,
                        MaterialShapes.VerySunny,
                        MaterialShapes.Cookie6Sided,
                        MaterialShapes.Circle,
                        MaterialShapes.Square,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Cookie12Sided,
                    ),
                    filled = true,
                    strokeWidthFraction = 0.00850f
                ),
                BouncingShapeSpec(
                    shape = MaterialShapes.Gem,
                    color = MaterialTheme.colorScheme.tertiary,
                    xFraction = 0.081f,
                    yFraction = 0.357f,
                    scaleFraction = 0.400f,
                    velocityX = 4.000f,
                    velocityY = 2.000f,
                    rotationDegrees = -1.187f,
                    rotationSpeed = 0.300f,
                    gravity = 0.20000f,
                    horizontalAttraction = 0.00015f,
                    horizontalDamping = 0.99500f,
                    maxHorizontalSpeed = 12.000f,
                    isTrampoline = true,
                    bounces = false,
                    massKg = 1.000f,
                    initialVelocityXMetersPerSecond = 0.500f,
                    initialVelocityYMetersPerSecond = 0.200f,
                    restitution = 0.900f,
                    springConstant = 200.000f,
                    springDamping = 1.000f,
                    orbitCenterXFraction = 0.049f,
                    orbitCenterYFraction = 0.346f,
                    orbitSpeed = 1.000f,
                    morphTargets = listOf(
                        MaterialShapes.Sunny,
                        MaterialShapes.Pill,
                        MaterialShapes.VerySunny,
                        MaterialShapes.Cookie6Sided,
                        MaterialShapes.Circle,
                        MaterialShapes.Square,
                        MaterialShapes.Cookie9Sided,
                        MaterialShapes.Cookie12Sided,
                    ),
                    filled = true,
                    strokeWidthFraction = 0.00850f
                )
            )
        )
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            content = content
        )
    }
}

@Composable
fun SetupWelcomePage(onNext: () -> Unit) {
    SetupPageSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.setup_welcome_title),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.setup_welcome_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            MotionButton(
                text = stringResource(R.string.setup_next),
                onClick = onNext,
                fontSize = 22.sp,
                height = 76.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun PermissionStep(
    title: String,
    description: String,
    onGrantClick: () -> Unit,
    onBack: () -> Unit,
    bottomBackText: String = stringResource(R.string.setup_skip),
    bottomNextText: String = stringResource(R.string.setup_grant_permission),
    onBottomBack: () -> Unit = onBack,
    onBottomNext: () -> Unit = onGrantClick
) {
    SetupPageSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.setup_back)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.weight(1f))
            SetupNavigationButtons(
                onBack = onBottomBack,
                onNext = onBottomNext,
                backText = bottomBackText,
                nextText = bottomNextText
            )
        }
    }
}

@Composable
private fun SetupNavigationButtons(
    onBack: () -> Unit,
    onNext: () -> Unit,
    backText: String,
    nextText: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        MotionButton(
            text = backText,
            onClick = onBack,
            fontSize = 16.sp,
            height = 52.dp,
            modifier = Modifier.weight(1f),
            defaultState = MotionButtonState(
                backgroundColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                outlineWidth = 2.dp,
                outlineColor = MaterialTheme.colorScheme.primary
            )
        )
        MotionButton(
            text = nextText,
            onClick = onNext,
            fontSize = 16.sp,
            height = 52.dp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun SetupNotificationPage(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val isGranted = rememberPermissionState(
        checkCurrentState = {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        },
        onGrantedAutomatically = onNext
    )
    val grantPermission = {
        if (isGranted) onNext()
        else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    PermissionStep(
        title = stringResource(R.string.setup_notifications_title),
        description = stringResource(R.string.setup_notifications_desc),
        onGrantClick = grantPermission,
        onBack = onBack,
        onBottomBack = onNext
    )
}

@Composable
fun SetupExactAlarmPage(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    fun canScheduleExactAlarms() =
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    rememberPermissionState(::canScheduleExactAlarms, onNext)
    val requestPermission = {
        if (canScheduleExactAlarms()) {
            onNext()
        } else {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = "package:${context.packageName}".toUri()
            })
        }
    }

    PermissionStep(
        title = stringResource(R.string.setup_exact_alarm_title),
        description = stringResource(R.string.setup_exact_alarm_desc),
        onGrantClick = requestPermission,
        onBack = onBack,
        onBottomBack = onNext
    )
}

@Composable
fun SetupOverlayPage(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    rememberPermissionState(
        checkCurrentState = { Settings.canDrawOverlays(context) },
        onGrantedAutomatically = onNext
    )
    val requestPermission = {
        if (Settings.canDrawOverlays(context)) {
            onNext()
        } else {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                "package:${context.packageName}".toUri()
            ))
        }
    }

    PermissionStep(
        title = stringResource(R.string.setup_overlay_title),
        description = stringResource(R.string.setup_overlay_desc),
        onGrantClick = requestPermission,
        onBack = onBack,
        onBottomBack = onNext
    )
}

@Composable
fun SetupFullScreenAlarmPage(onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    fun canUseFullScreenIntent() =
        context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    rememberPermissionState(::canUseFullScreenIntent, onNext)
    val requestPermission = {
        if (canUseFullScreenIntent()) {
            onNext()
        } else {
            context.startActivity(Intent(
                Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                "package:${context.packageName}".toUri()
            ))
        }
    }

    PermissionStep(
        title = stringResource(R.string.setup_fullscreen_title),
        description = stringResource(R.string.setup_fullscreen_desc),
        onGrantClick = requestPermission,
        onBack = onBack,
        onBottomBack = onNext
    )
}

@Composable
fun SetupDonePage(onDone: () -> Unit, onBack: () -> Unit) {
    SetupPageSurface {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 16.dp)
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.setup_back)
                )
            }
            Text(
                text = stringResource(R.string.setup_done_title),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.setup_done_desc),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            MotionButton(
                text = stringResource(R.string.setup_done),
                onClick = onDone,
                fontSize = 16.sp,
                height = 52.dp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

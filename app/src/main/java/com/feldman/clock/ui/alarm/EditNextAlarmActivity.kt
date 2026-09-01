@file:Suppress("PROPERTY_WONT_BE_SERIALIZED")

package com.feldman.clock.ui.alarm

import android.animation.ValueAnimator
import android.os.Bundle
import android.os.Build
import android.os.Parcelable
import android.provider.AlarmClock
import android.text.format.DateFormat
import android.view.WindowManager
import android.view.animation.PathInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlarm
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import com.feldman.clock.alarm.AlarmStateManager
import com.feldman.clock.app.integration.HandleApiCalls
import com.feldman.clock.core.storage.provider.Alarm
import com.feldman.clock.core.storage.provider.AlarmInstance
import com.feldman.clock.alarmui.ClockAlarmListContent
import com.feldman.motion.MotionTheme
import com.feldman.motion.MotionBottomSheetBackdropScope
import com.feldman.motion.MotionBottomSheetEffects
import com.feldman.motion.MotionBottomSheetSceneStrategy
import com.feldman.motion.MotionDest
import com.feldman.motion.MotionNavigator
import com.feldman.motion.MotionPaneType
import com.feldman.motion.motionPaneMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.util.Calendar
import kotlin.math.roundToInt

/** Seamless alarm-time editor for trusted callers such as Feldman Home. */
class EditNextAlarmActivity : ComponentActivity() {

    private var animateBlurBehind = false
    private var blurAnimationStarted = false
    private var currentBlurRadius = 0
    private var blurAnimator: ValueAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action == ACTION_SHOW_ALARM_LIST) {
            enableBackgroundBlur()
            setContent {
                MotionTheme {
                    MotionAlarmListSheet(
                        onDismissStarted = ::animateBackgroundBlurOut,
                        onFinished = ::finishWithoutAnimation
                    )
                }
            }
            return
        }

        lifecycleScope.launch {
            val alarm = withContext(Dispatchers.IO) {
                val alarmId = AlarmStateManager.getNextFiringAlarm(this@EditNextAlarmActivity)
                    ?.mAlarmId
                    ?: return@withContext null
                Alarm.getAlarm(contentResolver, alarmId)
            }
            if (intent.action == ACTION_DISABLE_NEXT_ALARM) {
                alarm?.let { disableAlarm(it) }
                finish()
                return@launch
            }
            val now = Calendar.getInstance()

            setContent {
                MotionTheme {
                    KeyboardFirstTimePickerDialog(
                        title = if (alarm == null) "New alarm" else "Edit alarm time",
                        initialHour = alarm?.hour ?: now.get(Calendar.HOUR_OF_DAY),
                        initialMinute = alarm?.minutes ?: now.get(Calendar.MINUTE),
                        is24Hour = DateFormat.is24HourFormat(this@EditNextAlarmActivity),
                        onDismissRequest = ::finishWithFade,
                        onConfirm = { hour, minute ->
                            if (alarm == null) createAlarm(hour, minute)
                            else saveAlarmTime(alarm, hour, minute)
                        }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && animateBlurBehind && !blurAnimationStarted) {
            blurAnimationStarted = true
            animateBackgroundBlur(targetBackgroundBlurRadius(), 800L)
        }
    }

    override fun onDestroy() {
        blurAnimator?.cancel()
        super.onDestroy()
    }

    private fun createAlarm(hour: Int, minute: Int) {
        startActivity(
            android.content.Intent(this, HandleApiCalls::class.java)
                .setAction(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
        )
        finishWithFade()
    }

    private fun saveAlarmTime(alarm: Alarm, hour: Int, minute: Int) {
        lifecycleScope.launch {
            val updatedAlarm = alarm.toUi().copy(
                hour = hour,
                minutes = minute,
                enabled = true
            ).toProvider(this@EditNextAlarmActivity)

            withContext(Dispatchers.IO) {
                Alarm.updateAlarm(contentResolver, updatedAlarm)
                AlarmStateManager.deleteAllInstances(this@EditNextAlarmActivity, updatedAlarm.id)
                val instance = updatedAlarm.createInstanceAfter(Calendar.getInstance())
                AlarmInstance.addInstance(contentResolver, instance)
                AlarmStateManager.registerInstance(this@EditNextAlarmActivity, instance, true)
            }
            finishWithFade()
        }
    }

    private suspend fun disableAlarm(alarm: Alarm) {
        withContext(Dispatchers.IO) {
            alarm.enabled = false
            Alarm.updateAlarm(contentResolver, alarm)
            AlarmStateManager.deleteAllInstances(this@EditNextAlarmActivity, alarm.id)
        }
    }

    private fun finishWithFade() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, android.R.anim.fade_out)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, android.R.anim.fade_out)
        }
    }

    private fun finishWithoutAnimation() {
        finish()
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }
    }

    private fun enableBackgroundBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        animateBlurBehind = true
        setBackgroundBlurRadius(0)
    }

    private fun animateBackgroundBlurOut() {
        animateBackgroundBlur(0, 200L)
    }

    private fun animateBackgroundBlur(targetRadius: Int, durationMillis: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        blurAnimator?.cancel()
        blurAnimator = ValueAnimator.ofInt(currentBlurRadius, targetRadius).apply {
            duration = durationMillis
            interpolator = PathInterpolator(0.4f, 0f, 0.2f, 1f)
            addUpdateListener { animator ->
                setBackgroundBlurRadius(animator.animatedValue as Int)
            }
            start()
        }
    }

    private fun targetBackgroundBlurRadius(): Int =
        (18 * resources.displayMetrics.density).roundToInt()

    private fun setBackgroundBlurRadius(radius: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        currentBlurRadius = radius
        val attributes = window.attributes
        attributes.blurBehindRadius = radius
        window.attributes = attributes
    }

    @Composable
    private fun MotionAlarmListSheet(
        onDismissStarted: () -> Unit,
        onFinished: () -> Unit
    ) {
        val backStack = remember {
            mutableStateListOf<NavKey>(AlarmSheetBackdrop, AlarmSheetContent)
        }
        val sheetStrategy = remember { MotionBottomSheetSceneStrategy<NavKey>() }
        val scope = rememberCoroutineScope()
        var closing by remember { mutableStateOf(false) }
        val onDismiss = {
            if (!closing) {
                closing = true
                onDismissStarted()
                backStack.removeLastOrNull()
                scope.launch {
                    delay(240)
                    onFinished()
                }
            }
        }

        NavDisplay(
            backStack = backStack,
            modifier = Modifier.fillMaxSize(),
            onBack = onDismiss,
            sceneStrategies = listOf(sheetStrategy),
            entryProvider = entryProvider {
                entry<AlarmSheetBackdrop>(metadata = AlarmSheetBackdrop.motionPaneMetadata()) {}
                entry<AlarmSheetContent>(metadata = AlarmSheetContent.motionPaneMetadata()) {
                    AlarmSheetContent.Content(
                        onNavigate = NoOpMotionNavigator,
                        onBack = onDismiss
                    )
                }
            }
        )
    }

    companion object {
        const val ACTION_EDIT_NEXT_ALARM = "com.feldman.clock.action.EDIT_NEXT_ALARM"
        const val ACTION_SHOW_ALARM_LIST = "com.feldman.clock.action.SHOW_ALARM_LIST"
        const val ACTION_DISABLE_NEXT_ALARM = "com.feldman.clock.action.DISABLE_NEXT_ALARM"
    }
}

private object NoOpMotionNavigator : MotionNavigator {
    override fun invoke(dest: MotionDest, resetStack: Boolean) = Unit
}

@Parcelize
@Serializable
private data object AlarmSheetBackdrop : MotionDest {
    override val label = "Alarm backdrop"
    override val showNavigation = false

    @Composable
    override fun Content(
        onNavigate: MotionNavigator,
        onBack: () -> Unit,
        searchQuery: String,
        onFabAction: ((() -> Unit) -> Unit) -> Unit
    ) = Unit
}

@Parcelize
@Serializable
private data object AlarmSheetContent : MotionDest {
    override val label = "Alarms"
    override val showNavigation = false
    override val pane = MotionPaneType.BOTTOM_SHEET
    override val parent: MotionDest = AlarmSheetBackdrop
    override val bottomSheetEffects = MotionBottomSheetEffects(
        blurBackground = true,
        darkenBackground = true,
        backdropScope = MotionBottomSheetBackdropScope.FULL_SCREEN
    )

    @Composable
    override fun Content(
        onNavigate: MotionNavigator,
        onBack: () -> Unit,
        searchQuery: String,
        onFabAction: ((() -> Unit) -> Unit) -> Unit
    ) {
        AlarmListSheetContent(onDismiss = onBack)
    }
}

@Composable
private fun AlarmListSheetContent(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val controller = remember(context) { DirectClockAlarmController(context) }
    ClockAlarmListContent(
        controller = controller,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.86f)
    )
}

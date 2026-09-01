package com.feldman.clock.ui.standby

import com.feldman.clock.R

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.service.dreams.DreamService
import android.graphics.Rect
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import android.view.MotionEvent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.core.data.DataModel
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.screensavers.ClockStandby
import com.feldman.clock.ui.standby.util.color.toComposeColor
import android.view.WindowManager
import com.feldman.clock.ui.standby.util.standbyservice.isInPreviewModeSafe
import com.feldman.clock.ui.standby.widgets.ClockAppWidgetHost
import com.feldman.motion.AppTheme
import android.util.Log
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.doOnLayout

private object StandbyRuntime {
    @Volatile var isPreview: Boolean = false
}

private const val APPWIDGET_HOST_ID = 1024
private const val WIDGET_HOST_KEEPALIVE_MS = 30_000L
private const val CLOCK_WIDGET_HOST_TAG = "ClockWidgetHost"
private const val CLOCK_WIDGET_TOUCH_TAG = "ClockWidgetTouch"

private class LoggingFrameLayout(
    context: Context,
    private val onDreamTouchEvent: (MotionEvent) -> Unit
) : FrameLayout(context) {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        onDreamTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "Dream root dispatch ${MotionEvent.actionToString(ev.actionMasked)} " +
                    "x=${ev.x.toInt()} y=${ev.y.toInt()}"
            )
        }
        val handled = super.dispatchTouchEvent(ev)
        val result = true
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "Dream root dispatch ${MotionEvent.actionToString(ev.actionMasked)} " +
                    "handled=$handled return=$result"
            )
        }

        return result
    }
}

class ClockStandbyService :
    DreamService(),
    LifecycleOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry
        get() = savedStateController.savedStateRegistry

    private lateinit var appWidgetHost: ClockAppWidgetHost
    private lateinit var appWidgetManager: AppWidgetManager
    private val widgetHostHandler = Handler(Looper.getMainLooper())
    private var suppressWakeUntilUptime = 0L
    private val widgetHostKeepAlive = object : Runnable {
        override fun run() {
            refreshWidgetHostListening("keepalive")
            widgetHostHandler.postDelayed(this, WIDGET_HOST_KEEPALIVE_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        keepDreamInteractive("onCreate")
    }

    private fun keepDreamInteractive(reason: String) {
        isInteractive = true
        isFullscreen = true
        Log.d(
            CLOCK_WIDGET_HOST_TAG,
            "Dream keepInteractive($reason) interactive=$isInteractive fullscreen=$isFullscreen"
        )
    }

    private fun markDreamTouchEvent(ev: MotionEvent) {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_MOVE ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            suppressWakeUntilUptime = SystemClock.uptimeMillis() + 2_000L
        }
    }

    override fun onWakeUp() {
        val now = SystemClock.uptimeMillis()
        if (now <= suppressWakeUntilUptime) {
            Log.d(
                CLOCK_WIDGET_HOST_TAG,
                "Dream onWakeUp suppressed after touch now=$now until=$suppressWakeUntilUptime"
            )
            return
        }

        Log.d(CLOCK_WIDGET_HOST_TAG, "Dream onWakeUp allowed")
        super.onWakeUp()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        suppressWakeUntilUptime = SystemClock.uptimeMillis() + 2_000L
        lockDreamWindowOrientation("onConfigurationChanged")
        keepDreamInteractive("onConfigurationChanged")
        Log.d(
            CLOCK_WIDGET_HOST_TAG,
            "Dream configuration changed orientation=${orientationName(newConfig.orientation)} " +
                "screen=${newConfig.screenWidthDp}x${newConfig.screenHeightDp}dp"
        )
    }

    override fun onDreamingStarted() {
        keepDreamInteractive("onDreamingStarted")
        super.onDreamingStarted()
    }

    override fun onAttachedToWindow() {
        val inPreview = isInPreviewModeSafe()
        super.onAttachedToWindow()

        keepDreamInteractive("onAttachedToWindow")

        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            win.clearFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
            )

            win.attributes = win.attributes.apply {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                flags = flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv() and
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH.inv()
            }
        }

        val prefs = ClockApplication.getDefaultSharedPreferences(this)
        appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetHost = ClockAppWidgetHost(this, APPWIDGET_HOST_ID)
        startWidgetHostListening()

        Log.d("ClockStandbyService", "onAttachedToWindow: inPreview = $inPreview")
        StandbyRuntime.isPreview = inPreview

        // ───────────────── Rotation mode ─────────────────
        val rotationMode =
            DataModel.RotationMode.from(
                SettingsDAO.getStandbyRotationMode(prefs)
            )

        if (!inPreview) {
            lockDreamWindowOrientation("onAttachedToWindow", rotationMode)
        }

        // ───────────────── Wireless-only check ─────────────────
        val wirelessOnly =
            SettingsDAO.isStandbyWirelessOnly(prefs)

        Log.d("ClockStandbyService", "onAttachedToWindow: inPreview=$inPreview, wirelessOnly=$wirelessOnly, wirelessCharging=${isWirelessCharging()}")

        if (!inPreview && wirelessOnly && !isWirelessCharging()) {
            Log.d("ClockStandbyService", "onAttachedToWindow: TERMINATING because wirelessOnly=true and not wireless charging")
            finish()
            return
        }

        // ───────────────── Lifecycle + Compose ─────────────────
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val root = LoggingFrameLayout(this, ::markDreamTouchEvent).apply {
            setViewTreeLifecycleOwner(this@ClockStandbyService)
            setViewTreeSavedStateRegistryOwner(this@ClockStandbyService)
            doOnLayout {
                systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
                Log.d(
                    CLOCK_WIDGET_HOST_TAG,
                    "Dream systemGestureExclusion set ${width}x${height}"
                )
            }
        }

        val clockColor =
            SettingsDAO.getStandbyClockColorPicker(prefs).toComposeColor()

        val vibrantPalette =
            SettingsDAO.getStandbyVibrantPalette(prefs)

        val dynamicColor =
            SettingsDAO.getStandbyDynamicColor(prefs)

        val composeView = ComposeView(this).apply {
            setContent {
                AppTheme(baseColor = if (!dynamicColor) clockColor else null, useVibrantPalette = if (!dynamicColor) vibrantPalette else null) {
                    ClockStandby(
                        onDismiss = {
                            Log.d(CLOCK_WIDGET_HOST_TAG, "Dream finish requested by ClockStandby onDismiss")
                            finish()
                        },
                        isInteractive = true,
                        // Cards stay interactive, but layout editing lives in the preview.
                        allowEdit = false,
                        appWidgetHost = appWidgetHost,
                        appWidgetManager = appWidgetManager
                    )
                }
            }
        }

        root.addView(
            composeView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        setContentView(root)
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onDetachedFromWindow() {
        Log.d(CLOCK_WIDGET_HOST_TAG, "Dream onDetachedFromWindow")
        stopWidgetHostListening()
        super.onDetachedFromWindow()

        if (lifecycleRegistry.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
    }

    override fun onDestroy() {
        Log.d(CLOCK_WIDGET_HOST_TAG, "Dream onDestroy")
        super.onDestroy()
    }

    private fun startWidgetHostListening() {
        Log.d(CLOCK_WIDGET_HOST_TAG, "Dream startWidgetHostListening host=${System.identityHashCode(appWidgetHost)}")
        refreshWidgetHostListening("start")
        widgetHostHandler.removeCallbacks(widgetHostKeepAlive)
        widgetHostHandler.postDelayed(widgetHostKeepAlive, WIDGET_HOST_KEEPALIVE_MS)
    }

    private fun stopWidgetHostListening() {
        widgetHostHandler.removeCallbacks(widgetHostKeepAlive)
        if (::appWidgetHost.isInitialized) {
            Log.d(CLOCK_WIDGET_HOST_TAG, "Dream stopWidgetHostListening host=${System.identityHashCode(appWidgetHost)}")
            runCatching { appWidgetHost.stopListening() }
                .onFailure {
                    Log.w("ClockStandbyService", "AppWidgetHost stopListening failed", it)
                }
        }
    }

    private fun refreshWidgetHostListening(reason: String) {
        if (!::appWidgetHost.isInitialized) return
        Log.d(
            CLOCK_WIDGET_HOST_TAG,
            "Dream refreshWidgetHostListening($reason) host=${System.identityHashCode(appWidgetHost)}"
        )
        runCatching { appWidgetHost.stopListening() }
            .onFailure {
                Log.w(CLOCK_WIDGET_HOST_TAG, "Dream stopListening before refresh failed ($reason)", it)
            }
        runCatching { appWidgetHost.startListening() }
            .onSuccess {
                Log.d("ClockStandbyService", "AppWidgetHost listening refreshed ($reason)")
                Log.d(
                    CLOCK_WIDGET_HOST_TAG,
                    "Dream startListening success ($reason) host=${System.identityHashCode(appWidgetHost)}"
                )
            }
            .onFailure {
                Log.w("ClockStandbyService", "AppWidgetHost startListening failed ($reason)", it)
            }
    }

    private fun isWirelessCharging(): Boolean {
        val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val plugged = i?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0
        return (plugged and BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0
    }

    private fun lockDreamWindowOrientation(
        reason: String,
        visualRotationMode: DataModel.RotationMode? = null
    ) {
        val lp = window?.attributes ?: return
        if (lp.screenOrientation != ActivityInfo.SCREEN_ORIENTATION_LOCKED) {
            lp.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_LOCKED
            window?.attributes = lp
        }
        Log.d(
            CLOCK_WIDGET_HOST_TAG,
            "Dream screenOrientation=${screenOrientationName(lp.screenOrientation)} " +
                "reason=$reason visualRotationMode=${visualRotationMode ?: "unchanged"}"
        )
    }

    private fun orientationName(orientation: Int): String =
        when (orientation) {
            Configuration.ORIENTATION_PORTRAIT -> "portrait"
            Configuration.ORIENTATION_LANDSCAPE -> "landscape"
            else -> "undefined"
        }

    private fun screenOrientationName(orientation: Int): String =
        when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LOCKED -> "locked"
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "portrait"
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "landscape"
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED -> "unspecified"
            else -> orientation.toString()
        }
}

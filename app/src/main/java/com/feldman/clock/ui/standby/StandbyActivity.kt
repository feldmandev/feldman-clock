package com.feldman.clock.ui.standby

import com.feldman.clock.R

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.feldman.clock.core.analytics.Events
import com.feldman.clock.ui.standby.screensavers.ClockStandby
import com.feldman.motion.AppTheme
import com.feldman.clock.ui.standby.util.color.toComposeColor
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.app.ClockApplication

private const val CLOCK_WIDGET_TOUCH_TAG = "ClockWidgetTouch"

class StandbyActivity : AppCompatActivity() {
    private var isStartingInternalActivity = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "StandbyActivity dispatch ${MotionEvent.actionToString(ev.actionMasked)} " +
                    "x=${ev.x.toInt()} y=${ev.y.toInt()}"
            )
        }
        val handled = super.dispatchTouchEvent(ev)
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "StandbyActivity dispatch ${MotionEvent.actionToString(ev.actionMasked)} handled=$handled"
            )
        }
        return handled
    }

    override fun startActivity(intent: Intent?) {
        if (intent?.component?.packageName == packageName) {
            isStartingInternalActivity = true
        }
        super.startActivity(intent)
    }

    override fun startActivity(intent: Intent?, options: Bundle?) {
        if (intent?.component?.packageName == packageName) {
            isStartingInternalActivity = true
        }
        super.startActivity(intent, options)
    }

    private val mIntentReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.v("StandbyActivity", "StandbyActivity onReceive, action: " + intent.action)
            when (intent.action) {
                Intent.ACTION_POWER_CONNECTED -> updateWakeLock(true)
                Intent.ACTION_POWER_DISCONNECTED -> updateWakeLock(false)
                Intent.ACTION_USER_PRESENT -> finish()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = ClockApplication.getDefaultSharedPreferences(this)

        // Rotate the activity for real per the standby orientation setting (the visual
        // rotation fallback is only for the DreamService, whose window cannot rotate).
        requestedOrientation = when (com.feldman.clock.core.data.DataModel.RotationMode.from(SettingsDAO.getStandbyRotationMode(prefs))) {
            com.feldman.clock.core.data.DataModel.RotationMode.Landscape -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            com.feldman.clock.core.data.DataModel.RotationMode.Portrait -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        val clockColor = SettingsDAO.getStandbyClockColorPicker(prefs).toComposeColor()

        setContent {
            AppTheme(baseColor = clockColor) {
                ClockStandby(
                    onDismiss = { finish() },
                    isInteractive = true
                )
            }
        }

        val intent = intent
        if (intent != null) {
            val eventLabel = intent.getIntExtra(Events.EXTRA_EVENT_LABEL, 0)
            Events.sendStandbyEvent(R.string.action_show, eventLabel)
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(Intent.ACTION_USER_PRESENT)
        }

        registerReceiver(mIntentReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onResume() {
        super.onResume()

        hideSystemBars()

        registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED), Context.RECEIVER_NOT_EXPORTED)

        val pluggedIn = intent != null && intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        updateWakeLock(pluggedIn)
    }

    override fun onPause() {
        super.onPause()
        // If an app opens from a widget click, this activity will be paused.
        // This is the most reliable way to dismiss only when an actual app launches,
        // as background widget interactions (like music controls) don't trigger onPause.
        // ❗ We DON'T finish if we are just opening our own editor/configuration screens.
        if (!isFinishing && !isStartingInternalActivity) {
            finish()
        }
        isStartingInternalActivity = false
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onStop() {
        unregisterReceiver(mIntentReceiver)
        super.onStop()
    }

    override fun onUserInteraction() {
        // Handled by Pager/Widgets now, but if we want to exit on tap:
        // finish() 
    }

    /**
     * @param pluggedIn `true` if the device is currently plugged in to a charger
     */
    private fun updateWakeLock(pluggedIn: Boolean) {
        val win = window
        val winParams = win.attributes

        val insetsController = win.insetsController
        if (insetsController != null) {
            insetsController.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsets.Type.systemBars())
        }
        
        @Suppress("DEPRECATION")
        winParams.flags = winParams.flags or WindowManager.LayoutParams.FLAG_FULLSCREEN
        
        val flags = windowFlags
        if (pluggedIn) {
            winParams.flags = winParams.flags or flags
        } else {
            winParams.flags = winParams.flags and flags.inv()
        }
        win.attributes = winParams
        setShowWhenLocked(pluggedIn)
        setTurnScreenOn(pluggedIn)
    }

    companion object {
        /**
         * Returns the flags to apply for modern versions of Android (API 27 and above).
         */
        private val windowFlags: Int
            get() = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON

    }
}

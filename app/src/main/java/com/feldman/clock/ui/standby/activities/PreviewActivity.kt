package com.feldman.clock.ui.standby.activities

import com.feldman.clock.R

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.screensavers.ClockStandby
import com.feldman.clock.ui.standby.util.color.toComposeColor
import com.feldman.motion.AppTheme

private const val CLOCK_WIDGET_TOUCH_TAG = "ClockWidgetTouch"

class PreviewActivity : ComponentActivity() {
    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN ||
            ev.actionMasked == MotionEvent.ACTION_UP ||
            ev.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "PreviewActivity dispatch ${MotionEvent.actionToString(ev.actionMasked)} " +
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
                "PreviewActivity dispatch ${MotionEvent.actionToString(ev.actionMasked)} handled=$handled"
            )
        }
        return handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = ClockApplication.getDefaultSharedPreferences(this)

        // Rotate the activity for real instead of visually rotating the content: gestures,
        // insets and scrolling then behave naturally (the visual-rotation fallback only
        // remains for the DreamService, whose window cannot rotate).
        requestedOrientation = when (com.feldman.clock.core.data.DataModel.RotationMode.from(SettingsDAO.getStandbyRotationMode(prefs))) {
            com.feldman.clock.core.data.DataModel.RotationMode.Landscape -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            com.feldman.clock.core.data.DataModel.RotationMode.Portrait -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            else -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        val clockColor =
            SettingsDAO.getStandbyClockColorPicker(prefs).toComposeColor()

        val vibrantPalette =
            SettingsDAO.getStandbyVibrantPalette(prefs)

        val dynamicColor =
            SettingsDAO.getStandbyDynamicColor(prefs)

        setContent {
            AppTheme(baseColor = if (!dynamicColor) clockColor else null, useVibrantPalette = if (!dynamicColor) vibrantPalette else null) {
                Box(Modifier.fillMaxSize()) {
                    // The standby host provides its own close (X) control; a second,
                    // unrotated button here just floats in the wrong place.
                    ClockStandby(
                        onDismiss = { finish() },
                        isInteractive = true
                    )
                }
            }
        }
    }
}

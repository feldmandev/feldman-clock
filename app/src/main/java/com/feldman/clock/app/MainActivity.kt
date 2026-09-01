package com.feldman.clock.app

import com.feldman.clock.R

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.feldman.clock.app.ClockApp
import com.feldman.clock.app.navigation.Dest
import com.feldman.motion.AppTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val KEY_IS_FIRST_LAUNCH = "key_is_first_launch"
        const val ACTION_SHOW_CLOCK = "com.feldman.clock.action.SHOW_CLOCK"
    }
    private val currentIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        currentIntent.value = intent

        setContent {
            AppTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    val prefs = remember {
                        ClockApplication.getDefaultSharedPreferences(applicationContext)
                    }

                    val startDestination = if (prefs.getBoolean(KEY_IS_FIRST_LAUNCH, true)) {
                        when (prefs.getInt("setup_step", 0).coerceIn(0, 5)) {
                            1 -> Dest.SetupNotifications
                            2 -> Dest.SetupExactAlarms
                            3 -> Dest.SetupOverlay
                            4 -> Dest.SetupFullScreenAlarms
                            5 -> Dest.SetupDone
                            else -> Dest.SetupWelcome
                        }
                    } else {
                        Dest.Alarm
                    }
                    val intent by currentIntent
                    ClockApp(intent = intent, startDestination = startDestination)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        currentIntent.value = intent
        setIntent(intent)
    }
}

package com.feldman.clock.ui.standby.activities

import com.feldman.clock.R

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.res.painterResource
import com.feldman.clock.app.navigation.Dest
import com.feldman.clock.ui.standby.pages.StandbySettingsPage
import com.feldman.motion.AppTheme

class StandbySettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AppTheme {
                val fabAction = remember { mutableStateOf<(() -> Unit)?>(null) }

                Scaffold(
                    floatingActionButton = {
                        fabAction.value?.let { action ->
                            ExtendedFloatingActionButton(
                                onClick = action,
                                icon = {
                                    Icon(
                                        painterResource(R.drawable.ic_play_arrow),
                                        contentDescription = "Preview"
                                    )
                                },
                                text = { Text("Preview") }
                            )
                        }
                    }
                ) { padding ->
                    StandbySettingsPage(
                        onFabClick = { action ->
                            fabAction.value = action
                        },
                        onNavigate = { /* Dummy for now */ },
                        onBack = { finish() },
                        isTab = true
                    )
                }
            }
        }
    }
}

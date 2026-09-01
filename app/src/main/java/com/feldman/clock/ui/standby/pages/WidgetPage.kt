package com.feldman.clock.ui.standby.pages

import com.feldman.clock.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.view.View
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.feldman.clock.app.ClockApplication
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.widgets.ClockWidgetHostActivity
import com.feldman.clock.ui.standby.widgets.ClockAppWidgetHost
import com.feldman.clock.ui.standby.widgets.StandbyWidgetPickerSheet

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WidgetPage(
    appWidgetHost: ClockAppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember {
        ClockApplication.getDefaultSharedPreferences(context)
    }

    var widgetIds by remember {
        mutableStateOf(SettingsDAO.getStandbyWidgetIds(prefs))
    }

    var widgetToDelete by remember { mutableStateOf<Int?>(null) }
    var showPickerSheet by remember { mutableStateOf(false) }

    // ───────────────── Delete dialog ─────────────────
    widgetToDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { widgetToDelete = null },
            title = { Text("Remove Widget") },
            text = { Text("Are you sure you want to remove this widget?") },
            confirmButton = {
                TextButton(onClick = {
                    appWidgetHost.deleteAppWidgetId(id)
                    SettingsDAO.removeStandbyWidgetId(prefs, id)
                    widgetIds = SettingsDAO.getStandbyWidgetIds(prefs)
                    widgetToDelete = null
                }) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { widgetToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.Center,
                maxItemsInEachRow = 2
            ) {
                widgetIds.forEach { widgetId ->
                    Box(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth(0.45f)
                    ) {
                        val id = widgetId.toIntOrNull()
                        if (id != null) {
                            WidgetHostView(
                                appWidgetId = id,
                                appWidgetHost = appWidgetHost,
                                appWidgetManager = appWidgetManager,
                                onLongClick = { widgetToDelete = id }
                            )
                        }

                    }
                }
            }

            Button(
                onClick = {
                    showPickerSheet = true
                },
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add", modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (showPickerSheet) {
            StandbyWidgetPickerSheet(
                onDismiss = { showPickerSheet = false },
                onWidgetSelected = { providerInfo ->
                    val intent = Intent(context, ClockWidgetHostActivity::class.java).apply {
                        putExtra(ClockWidgetHostActivity.EXTRA_AUTO_ADD_SPECIFIC_WIDGET, providerInfo.provider)
                    }
                    context.startActivity(intent)
                }
            )
        }
    }
}

@Composable
fun WidgetHostView(
    appWidgetId: Int,
    appWidgetHost: ClockAppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onLongClick: () -> Unit
) {
    key(appWidgetId) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { ctx ->
                val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
                if (info == null) return@AndroidView View(ctx)

                val hostView = appWidgetHost.createView(ctx, appWidgetId, info)
                hostView.setAppWidget(appWidgetId, info)

                // ✅ Fallback long-click (ALWAYS WORKS)
                hostView.setOnLongClickListener {
                    onLongClick()
                    true
                }


                hostView
            }
        )
    }
}

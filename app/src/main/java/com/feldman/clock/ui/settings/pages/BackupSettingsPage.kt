package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.core.storage.ClockBackup
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manual backup, as an alternative to Android's account-based one.
 *
 * Restore replaces everything rather than merging — merging would duplicate every alarm on
 * each run — so it sits behind a confirmation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean,
    showSnackbar: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingRestore by remember { mutableStateOf<Uri?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val message = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use {
                        ClockBackup.export(context, it)
                    } ?: error("Could not open the file for writing")
                }.fold(
                    onSuccess = { "Backup saved" },
                    onFailure = { "Backup failed: ${it.message}" }
                )
            }
            showSnackbar(message)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> pendingRestore = uri }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Backup & Restore",
                chromeColor = SettingsCategoryColor.ABOUT.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("Backup")
        section {
            pageItem(
                title = "Export to file",
                description = "Save alarms, timers, cities and settings as a file you keep",
                icon = painterResource(R.drawable.ic_save),
                onClick = { exportLauncher.launch(ClockBackup.suggestedFileName()) }
            )
            pageItem(
                title = "Restore from file",
                description = "Replaces everything currently in Clock",
                icon = painterResource(R.drawable.ic_restore),
                onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }
            )
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }

    pendingRestore?.let { uri ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Text(
                    "Every alarm, timer, city and setting currently in Clock will be replaced " +
                        "by the contents of this file. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        val message = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)?.use {
                                    ClockBackup.import(context, it)
                                } ?: error("Could not open the file")
                            }.fold(
                                onSuccess = { result ->
                                    when (result) {
                                        is ClockBackup.Result.Success ->
                                            "Restored ${result.alarmCount} alarms"
                                        is ClockBackup.Result.Failure -> result.message
                                    }
                                },
                                onFailure = { it.message ?: "Restore failed" }
                            )
                        }
                        showSnackbar(message)
                    }
                }) { Text("Restore", color = colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
    }
}

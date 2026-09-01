package com.feldman.clock.ui.settings.pages

import com.feldman.clock.R

import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.feldman.clock.BuildConfig
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.motion.SettingsScaffold
import java.io.File
import androidx.core.net.toUri

@Composable
fun AboutSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current

    fun openLink(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    @Composable
    fun InfoItem(title: String, description: String) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "About",
                chromeColor = SettingsCategoryColor.ABOUT.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        title("About")
        section {
            item { InfoItem("Version", BuildConfig.VERSION_NAME) }
            pageItem(
                title = "Fork of BlackyHawky/Clock",
                description = "View the original project on GitHub",
                onClick = { openLink("https://github.com/BlackyHawky/Clock") }
            )
            item { InfoItem("UI and code rewrite", "Rewritten with Kotlin and Jetpack Compose") }
            item { InfoItem("Developer", "feldman") }
            pageItem(
                title = "Export Debug Logs",
                description = "Save logs to External Storage",
                onClick = {
                    try {
                        val protectedContext = context.createDeviceProtectedStorageContext()
                        val logFile = File(protectedContext.filesDir, "alarm_debug_log.txt")

                        if (logFile.exists()) {
                            val contentValues = ContentValues().apply {
                                put(MediaStore.MediaColumns.DISPLAY_NAME, "clock_debug_log_${System.currentTimeMillis()}.txt")
                                put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClockDebug")
                            }

                            val resolver = context.contentResolver
                            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

                            if (uri != null) {
                                resolver.openOutputStream(uri)?.use { outputStream ->
                                    logFile.inputStream().use { inputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }
                                val message = "Logs exported to Downloads/ClockDebug"
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                Log.i("AboutSettingsPage", message)
                            } else {
                                Toast.makeText(context, "Failed to create file in Downloads", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(context, "No internal logs found.", Toast.LENGTH_SHORT).show()
                        }
                    } catch (exception: Exception) {
                        Log.e("AboutSettingsPage", "Error exporting logs", exception)
                        Toast.makeText(context, "Export failed: ${exception.message}", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

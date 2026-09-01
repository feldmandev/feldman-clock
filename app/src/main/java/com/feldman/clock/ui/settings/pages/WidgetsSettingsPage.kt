package com.feldman.clock.ui.settings.pages

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.feldman.clock.R
import com.feldman.clock.ui.settings.SettingsCategoryColor
import com.feldman.clock.ui.settings.SettingsTopBar
import com.feldman.clock.ui.theme.isDarkTheme
import com.feldman.clock.ui.widgets.AnalogClockWidgetProvider
import com.feldman.clock.ui.widgets.DigitalClockWidgetProvider
import com.feldman.clock.ui.widgets.WidgetConfigureActivity
import com.feldman.motion.SettingsScaffold

private data class PlacedWidget(
    val appWidgetId: Int,
    val title: String,
    val iconRes: Int
)

/**
 * Lists the clock widgets currently on the home screen and opens each one's configuration.
 *
 * The same screen is reachable by long-pressing a widget on the home screen; this gives it a home
 * inside the app for people who do not know about that gesture.
 */
@Composable
fun WidgetsSettingsPage(
    onBack: () -> Unit,
    isTab: Boolean
) {
    val context = LocalContext.current

    val placed = remember(context) {
        val manager = AppWidgetManager.getInstance(context)
        if (manager == null) {
            emptyList()
        } else {
            val analog = manager
                .getAppWidgetIds(ComponentName(context, AnalogClockWidgetProvider::class.java))
                .map { PlacedWidget(it, "Analog", R.drawable.ic_tab_clock_static) }

            val digital = manager
                .getAppWidgetIds(ComponentName(context, DigitalClockWidgetProvider::class.java))
                .map { PlacedWidget(it, "Digital", R.drawable.ic_clock) }

            analog + digital
        }
    }

    SettingsScaffold(
        topBar = {
            SettingsTopBar(
                title = "Widgets",
                chromeColor = SettingsCategoryColor.CLOCK.container(isDarkTheme()),
                onBack = if (isTab) null else onBack
            )
        }
    ) {
        if (placed.isEmpty()) {
            title("Home Screen Widgets")
            item {
                Text(
                    text = "No clock widgets on your home screen yet.\n\n" +
                        "Long-press an empty spot on the home screen, tap Widgets, then pick a " +
                        "clock widget. Once placed, you can long-press it to change its shape, " +
                        "date format, and what it shows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
            }
        } else {
            title("On Your Home Screen")
            section {
                placed.forEach { widget ->
                    pageItem(
                        title = widget.title,
                        description = "Shape, date and contents",
                        icon = painterResource(widget.iconRes),
                        backgroundColor = SettingsCategoryColor.CLOCK.container(isDarkTheme()),
                        iconColor = SettingsCategoryColor.CLOCK.content(isDarkTheme()),
                        onClick = {
                            context.startActivity(
                                Intent(context, WidgetConfigureActivity::class.java).apply {
                                    putExtra(
                                        AppWidgetManager.EXTRA_APPWIDGET_ID,
                                        widget.appWidgetId
                                    )
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(100.dp)) }
    }
}

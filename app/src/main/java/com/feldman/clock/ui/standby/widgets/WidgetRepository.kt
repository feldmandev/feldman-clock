package com.feldman.clock.ui.standby.widgets

import com.feldman.clock.R

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WidgetUiModel(
    val providerInfo: AppWidgetProviderInfo,
    val label: String
)

data class AppGroup(
    val packageName: String,
    val appName: String,
    val widgets: List<WidgetUiModel>
)
private fun canHostWidget(
    context: Context,
    info: AppWidgetProviderInfo
): Boolean {
    val pm = context.packageManager

    // Provider must be accessible
    try {
        pm.getReceiverInfo(info.provider, 0)
    } catch (e: Exception) {
        return false
    }

    // If widget has a config activity, it must be exported
    info.configure?.let { cn ->
        try {
            pm.getActivityInfo(cn, 0)
        } catch (e: Exception) {
            return false
        }
    }

    return true
}


object WidgetRepository {
    private var cachedAppGroups: List<AppGroup>? = null
    
    suspend fun getAppGroups(context: Context): List<AppGroup> {
        // Return cached data if available
        cachedAppGroups?.let { return it }
        
        // Otherwise, load fresh data
        return loadAppGroups(context).also { cachedAppGroups = it }
    }
    
    suspend fun loadAppGroups(context: Context): List<AppGroup> = withContext(Dispatchers.IO) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val providers = appWidgetManager.getInstalledProviders()
        val pm = context.packageManager
        
        // Group by package name
        val grouped = providers.groupBy { it.provider.packageName }
        
        grouped.map { (packageName, widgets) ->
            val appInfo = try {
                pm.getApplicationInfo(packageName, 0)
            } catch (e: Exception) {
                null
            }
            
            val appName = appInfo?.let {
                pm.getApplicationLabel(it).toString()
            } ?: packageName

            val widgetUiModels = widgets
                .filter { canHostWidget(context, it) }
                .map { info ->
                    WidgetUiModel(
                        providerInfo = info,
                        label = info.loadLabel(pm)
                    )
                }

            
            AppGroup(packageName, appName, widgetUiModels)
        }.sortedBy { it.appName }
    }
    
    fun clearCache() {
        cachedAppGroups = null
    }
}

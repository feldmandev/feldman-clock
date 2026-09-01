package com.feldman.clock.ui.standby.widgets

import com.feldman.clock.R

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.Toast
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.app.ClockApplication.Companion.getDefaultSharedPreferences
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.zIndex

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MediumFloatingActionButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDownIgnoreConsumed
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.lifecycleScope
// import com.feldman.clock.ui.standby.activities.WidgetPickerActivity
import com.feldman.motion.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

const val KEY_STANDBY_WIDGETS_CHANGED_AT = "key_standby_widgets_changed_at"
private const val CLOCK_WIDGET_TOUCH_TAG = "ClockWidgetTouch"
private const val CLOCK_WIDGET_HOST_TAG = "ClockWidgetHost"

object L {
    fun d(message: () -> String) {
        Log.d("AppWidgets", message())
    }

    fun e(e: Throwable) {
        Log.e("AppWidgets", "Error", e)
    }

    fun d(message: String) {
        Log.d("AppWidgets", message)
    }
}

val Int.dpToPx: Int
    get() = (this * Resources.getSystem().displayMetrics.density).toInt()

val Int.pxToDp: Int
    get() = (this / Resources.getSystem().displayMetrics.density).toInt()

// Helper to make the snap outline color
val SnapOutlineColor = Color(0x8000FF00) // Semi-transparent Green

// Grid configuration
private const val GRID_COLUMNS = 10  // Number of columns in the widget grid
private const val GRID_UNIT_DP = 36
private const val GRID_PADDING_DP = 12
private const val MIN_VISUAL_SPAN_Y = 2
private const val GRID_GAP_DP = 4

fun visualSpanY(spanY: Int): Int =
    if (spanY < MIN_VISUAL_SPAN_Y) MIN_VISUAL_SPAN_Y else spanY
fun widgetHeightDp(spanY: Int): Int {
    val visualSpanY = visualSpanY(spanY)
    return GRID_UNIT_DP * visualSpanY - GRID_PADDING_DP
}

data class WidgetData(
    val appWidgetId: Int,
    var col: Int = 0,
    var row: Int = 0,
    var spanX: Int = 2,
    var spanY: Int = 2,
    // Helper to get pixel height for old consumers (or we refactor them)
    // var heightDp is removed in favor of spanY * GridUnit
) {
    // Pixel Launcher uses (70n - 30) formula for widget dimensions
    val widthDp: Int get() = (spanX * 70 - 30).coerceAtLeast(40)
}

enum class WidgetHostLayoutMode {
    GRID,
    SPLIT_VERTICAL_PAGER
}

class ClockAppWidgetHost(
    context: Context,
    hostId: Int
) : AppWidgetHost(context, hostId) {

    override fun onCreateView(
        context: Context,
        appWidgetId: Int,
        appWidget: AppWidgetProviderInfo?
    ): AppWidgetHostView {
        return LauncherAppWidgetHostView(context)
    }
}

private val gson = Gson()

private data class WidgetOptionsKey(
    val appWidgetId: Int,
    val widthDp: Int,
    val heightDp: Int
)

private fun appWidgetOptions(widthDp: Int, heightDp: Int): Bundle =
    Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp)
        putInt(
            AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,
            AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN
        )
    }

private fun notifyStandbyWidgetsChanged(context: Context) {
    getDefaultSharedPreferences(context)
        .edit()
        .putLong(KEY_STANDBY_WIDGETS_CHANGED_AT, System.currentTimeMillis())
        .apply()
}

fun saveWidgetsToPrefs(context: Context, widgets: List<WidgetData>) {
    val prefs = getDefaultSharedPreferences(context)
    val json = gson.toJson(widgets)
    SettingsDAO.setStandbyWidgetsConfig(prefs, json)
}

fun loadWidgetsFromPrefs(context: Context): List<WidgetData> {
    val prefs = getDefaultSharedPreferences(context)
    val json = SettingsDAO.getStandbyWidgetsConfig(prefs)
    if (json.isNullOrEmpty()) return emptyList()
    return try {
        val type = object : TypeToken<List<WidgetData>>() {}.type
        gson.fromJson(json, type)
    } catch (e: Exception) {
        L.e(e)
        emptyList()
    }
}

class ClockWidgetHostActivity : ComponentActivity() {

    private var appWidgetManager: AppWidgetManager? = null
    private var appWidgetHost: AppWidgetHost? = null
    private val APPWIDGET_HOST_ID = 1024
    private var pendingAppWidgetId = -1

    private val bindWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = result.data
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it != -1 }
            ?: pendingAppWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            pendingAppWidgetId = -1
            configureWidget(appWidgetId)
        } else {
            cancelWidgetSetup(appWidgetId)
        }
    }

    private val configureWidgetLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val appWidgetId = result.data
            ?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            ?.takeIf { it != -1 }
            ?: pendingAppWidgetId
        if (result.resultCode == RESULT_OK && appWidgetId != -1) {
            createWidgetAndFinishIfExtra(appWidgetId)
            pendingAppWidgetId = -1
        } else {
            cancelWidgetSetup(appWidgetId)
        }
    }

    // State for the bottom sheet picker
    private var showPickerSheet by mutableStateOf(false)

    // Store added widgets with their size data
    private val widgets = mutableStateListOf<WidgetData>()

    private val isHelperLaunch: Boolean
        get() = intent.getBooleanExtra(EXTRA_START_PICKER, false) ||
                intent.hasExtra(EXTRA_AUTO_ADD_SPECIFIC_WIDGET) ||
                intent.hasExtra(EXTRA_CONFIGURE_WIDGET_ID)

    private fun loadPersistentWidgets() {
        val loaded = loadWidgetsFromPrefs(this)
        widgets.clear()
        widgets.addAll(loaded)
    }

    private fun persistWidgets() {
        saveWidgetsToPrefs(this, widgets)
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        try {
            appWidgetManager = AppWidgetManager.getInstance(this)
            appWidgetHost = LauncherAppWidgetHost(this, APPWIDGET_HOST_ID)
        } catch (e: Exception) {
            L.e(e)
            // Handle initialization failure
        }

        // ❗ Synchronous load to avoid race condition with auto-add
        loadPersistentWidgets()

        // Pre-load widget data for picker display (slow part can be async)
        lifecycleScope.launch {
            WidgetRepository.loadAppGroups(this@ClockWidgetHostActivity)
        }

        // ❗ NEW: Automatic picker launch logic
        val autoAddSpecific = intent.getParcelableExtra(
            EXTRA_AUTO_ADD_SPECIFIC_WIDGET,
            ComponentName::class.java
        )
        if (autoAddSpecific != null) {
            val infos = appWidgetManager?.getInstalledProviders() ?: emptyList()
            val specificInfo = infos.find { it.provider == autoAddSpecific }
            if (specificInfo != null) {
                startWidgetBinding(specificInfo)
            }
        } else if (intent.getBooleanExtra(EXTRA_START_PICKER, false)) {
            showPickerSheet = true
        }

        val configId = intent.getIntExtra(EXTRA_CONFIGURE_WIDGET_ID, -1)
        if (configId != -1) {
            configureWidget(configId)
        }

        setContent {
            AppTheme {
                val context = LocalContext.current
                
                Box(modifier = Modifier.fillMaxSize()) {
                    WidgetHostScreen(
                        onAddWidgetClick = { showPickerSheet = true },
                        widgets = widgets,
                        appWidgetHost = appWidgetHost!!,
                        appWidgetManager = appWidgetManager!!,
                        onDeleteWidget = { widget ->
                            deleteWidget(widget)
                        },
                        onResizeWidget = { widget, newSpanX, newSpanY ->
                            val index = widgets.indexOfFirst { it.appWidgetId == widget.appWidgetId }
                            if (index != -1) {
                                val updatedWidget = widget.copy(spanX = newSpanX, spanY = newSpanY)
                                widgets[index] = updatedWidget
                                updateWidgetSize(updatedWidget)
                            }
                        },
                        onMoveWidget = { widget, newCol, newRow ->
                            val index = widgets.indexOfFirst { it.appWidgetId == widget.appWidgetId }
                            if (index != -1) {
                                widgets[index] = widget.copy(col = newCol, row = newRow)
                            }
                        },
                        onConfigureWidget = { widget ->
                            configureWidget(widget.appWidgetId)
                        },
                        onWidgetsChanged = {
                            persistWidgets()
                        },
                        onEditModeChanged = { /* Not needed in full screen editor for now */ }
                    )

                    if (showPickerSheet) {
                        StandbyWidgetPickerSheet(
                            onDismiss = { showPickerSheet = false },
                            onWidgetSelected = { providerInfo ->
                                startWidgetBinding(providerInfo)
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (!isHelperLaunch) {
            appWidgetHost?.startListening()
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isHelperLaunch) {
            appWidgetHost?.stopListening()
        }
    }

    private fun startWidgetSelection() {
        showPickerSheet = true
    }

    private fun startWidgetBinding(info: AppWidgetProviderInfo) {
        val appWidgetId = appWidgetHost!!.allocateAppWidgetId()
        val success = appWidgetManager!!.bindAppWidgetIdIfAllowed(appWidgetId, info.provider)
        
        if (success) {
            configureWidget(appWidgetId)
        } else {
            pendingAppWidgetId = appWidgetId
            val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            }
            bindWidgetLauncher.launch(intent)
        }
    }

    private fun cancelWidgetSetup(appWidgetId: Int) {
        if (appWidgetId != -1 && widgets.none { it.appWidgetId == appWidgetId }) {
            appWidgetHost?.deleteAppWidgetId(appWidgetId)
            notifyStandbyWidgetsChanged(this)
        }
        pendingAppWidgetId = -1

        if (isHelperLaunch) {
            finish()
        }
    }

    private fun configureWidget(appWidgetId: Int) {
        val appWidgetInfo = appWidgetManager?.getAppWidgetInfo(appWidgetId)

        if (appWidgetInfo?.configure != null) {
            try {
                pendingAppWidgetId = appWidgetId
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                    component = appWidgetInfo.configure
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                }
                configureWidgetLauncher.launch(intent)
            } catch (e: SecurityException) {
                // ❗ Widget claims to be configurable but blocks us (Google Weather, etc.)
                Log.w("WidgetHost", "Widget config not exported: ${appWidgetInfo.provider}", e)
                createWidgetAndFinishIfExtra(appWidgetId)
            }
        } else {
            createWidgetAndFinishIfExtra(appWidgetId)
        }
    }

    private fun createWidgetAndFinishIfExtra(appWidgetId: Int) {
        createWidget(appWidgetId)
        persistWidgets()
        notifyStandbyWidgetsChanged(this)

        val startedFromExtra = intent.getBooleanExtra(EXTRA_START_PICKER, false) ||
                intent.hasExtra(EXTRA_AUTO_ADD_SPECIFIC_WIDGET) ||
                intent.hasExtra(EXTRA_CONFIGURE_WIDGET_ID)

        if (startedFromExtra) {
            finish()
        }
    }


    private fun overlaps(aCol: Int, aRow: Int, aSpanX: Int, aSpanY: Int, b: WidgetData): Boolean {
        val ax1 = aCol
        val ax2 = aCol + aSpanX
        val ay1 = aRow
        val ay2 = aRow + aSpanY

        val bx1 = b.col
        val bx2 = b.col + b.spanX
        val by1 = b.row
        val by2 = b.row + b.spanY

        return ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1
    }

    private fun canPlace(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (col < 0 || row < 0) return false
        if (spanX < 1 || spanY < 1) return false
        if (col + spanX > GRID_COLUMNS) return false

        return widgets.none { existing ->
            overlaps(col, row, spanX, spanY, existing)
        }
    }

    private fun createWidget(appWidgetId: Int) {
        if (widgets.any { it.appWidgetId == appWidgetId }) return

        val info = appWidgetManager?.getAppWidgetInfo(appWidgetId)

        val rawSpanX = when {
            info == null -> 3
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellWidth > 0 ->
                info.targetCellWidth
            else ->
                ((info.minWidth + GRID_PADDING_DP) / GRID_UNIT_DP).coerceAtLeast(1)
        }

        val rawSpanY = when {
            info == null -> 2
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && info.targetCellHeight > 0 ->
                info.targetCellHeight
            else ->
                ((info.minHeight + GRID_PADDING_DP) / GRID_UNIT_DP).coerceAtLeast(1)
        }

        // ✅ CLAMP spans so the search loop can't go empty / negative
        val spanX = rawSpanX.coerceIn(1, GRID_COLUMNS)
        val spanY = rawSpanY.coerceAtLeast(1)

        var targetCol = -1
        var targetRow = -1

        // Search enough rows (and allow it to grow as widgets grow)
        val startMaxRow = (widgets.maxOfOrNull { it.row + it.spanY } ?: 0)
        val searchRows = (startMaxRow + 200).coerceAtLeast(200)

        loop@ for (r in 0 until searchRows) {
            for (c in 0..(GRID_COLUMNS - spanX)) {
                if (canPlace(c, r, spanX, spanY)) {
                    targetCol = c
                    targetRow = r
                    break@loop
                }
            }
        }

        // ✅ DO NOT add if we couldn't find a valid spot
        if (targetCol == -1 || targetRow == -1) {
            Toast.makeText(this, "No space for this widget", Toast.LENGTH_SHORT).show()
            appWidgetHost?.deleteAppWidgetId(appWidgetId) // avoid leaking allocated id
            return
        }

        val newWidget = WidgetData(
            appWidgetId = appWidgetId,
            col = targetCol,
            row = targetRow,
            spanX = spanX,
            spanY = spanY
        )
        widgets.add(newWidget)
        updateWidgetSize(newWidget)
    }


    private fun updateWidgetSize(widget: WidgetData) {
        val widthDp = GRID_UNIT_DP * widget.spanX - GRID_PADDING_DP
        val heightDp = widgetHeightDp(widget.spanY)

        appWidgetManager?.updateAppWidgetOptions(widget.appWidgetId, appWidgetOptions(widthDp, heightDp))
    }


    private fun deleteWidget(widget: WidgetData) {
        widgets.remove(widget)
        appWidgetHost?.deleteAppWidgetId(widget.appWidgetId)
        persistWidgets() // ❗ Persist change
    }

    // Custom AppWidgetHost to return our custom AppWidgetHostView
    class LauncherAppWidgetHost(context: Context, hostId: Int) : AppWidgetHost(context, hostId) {
        override fun onCreateView(
            context: Context,
            appWidgetId: Int,
            appWidget: AppWidgetProviderInfo?
        ): AppWidgetHostView {
            return LauncherAppWidgetHostView(context)
        }
    }

    companion object {
        const val EXTRA_START_PICKER = "extra_start_picker"
        const val EXTRA_AUTO_ADD_SPECIFIC_WIDGET = "extra_auto_add_specific_widget"
        const val EXTRA_CONFIGURE_WIDGET_ID = "extra_configure_widget_id"
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WidgetHostScreen(
    onAddWidgetClick: () -> Unit,
    widgets: MutableList<WidgetData>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    onDeleteWidget: (WidgetData) -> Unit,
    onResizeWidget: (WidgetData, Int, Int) -> Unit,
    onMoveWidget: (WidgetData, Int, Int) -> Unit,
    onConfigureWidget: (WidgetData) -> Unit,
    onWidgetsChanged: () -> Unit = {},
    onEditModeChanged: (Boolean) -> Unit = {},
    showAddButton: Boolean = true,
    onDismiss: () -> Unit = {},
    layoutMode: WidgetHostLayoutMode = WidgetHostLayoutMode.GRID,
    clearEditRequest: Int = 0
) {

    fun isWidgetConfigurable(widgetId: Int): Boolean {
        val info = appWidgetManager.getAppWidgetInfo(widgetId)
        return info?.configure != null
    }

    val editingWidgetId = remember { mutableStateOf<Int?>(null) }
    val showDeleteDialog = remember { mutableStateOf<WidgetData?>(null) }
    val isOverDeleteZone = remember { mutableStateOf(false) }
    val fabBounds = remember { mutableStateOf<Rect?>(null) }
    val context = LocalContext.current
    val isEditing = editingWidgetId.value != null
    LaunchedEffect(isEditing) {
        onEditModeChanged(isEditing)
    }
    LaunchedEffect(clearEditRequest) {
        if (clearEditRequest != 0 && editingWidgetId.value != null) {
            editingWidgetId.value = null
            isOverDeleteZone.value = false
        }
    }

    LaunchedEffect(widgets.size) {
        // This is a bit redundant if we have onWidgetsChanged but good for safety
    }

    if (showDeleteDialog.value != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog.value = null },
            title = { Text("Delete Widget?") },
            text = { Text("Are you sure you want to remove this widget?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val widgetToDelete = showDeleteDialog.value
                        if (widgetToDelete != null) {
                            onDeleteWidget(widgetToDelete)
                            if (editingWidgetId.value == widgetToDelete.appWidgetId) {
                                editingWidgetId.value = null
                            }
                            showDeleteDialog.value = null
                        }
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog.value = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            if (showAddButton) {
                val inEditMode = editingWidgetId.value != null
                val deleteZoneActive = isOverDeleteZone.value
                var expanded by remember { mutableStateOf(false) }

                // Visual states from your original code
                val fabScale by animateFloatAsState(
                    targetValue = if (deleteZoneActive) 1.5f else 1f,
                    label = "fabScale"
                )

                // Ensure menu closes if we enter edit mode
                LaunchedEffect(inEditMode) { expanded = false }
                val surfaceVariant = colorScheme.surfaceVariant
                if (inEditMode) {
                    FloatingActionButtonMenu(
                        expanded = expanded,
                        button = {
                            MediumFloatingActionButton(
                                onClick = { expanded = !expanded },
                                containerColor =
                                if (deleteZoneActive) surfaceVariant.copy(alpha = 0.9f)
                                else surfaceVariant,
                                modifier = Modifier
                                    .scale(fabScale)
                                    .onGloballyPositioned { fabBounds.value = it.boundsInWindow() }
                            ) {
                                val rotation by animateFloatAsState(
                                    if (expanded) 45f else 0f,
                                    label = "fabRotation"
                                )

                                Icon(
                                    Icons.Filled.MoreVert,
                                    contentDescription = "More",
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .size(32.dp)
                                        .rotate(rotation),
                                )
                            }

                        }
                    ) {
                        val currentEditingWidget = widgets.find { it.appWidgetId == editingWidgetId.value }
                        val configurableEditingWidget = currentEditingWidget?.takeIf {
                            appWidgetManager.getAppWidgetInfo(it.appWidgetId)?.configure != null
                        }

                        FloatingActionButtonMenuItem(
                            onClick = {
                                expanded = false
                                currentEditingWidget?.let {
                                    onDeleteWidget(it)
                                    editingWidgetId.value = null
                                }
                            },
                            icon = { Icon(Icons.Default.Delete, "Delete Widget") },
                            containerColor = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                            text = { Text("Delete Widget") }
                        )

                        if (configurableEditingWidget != null) {
                            FloatingActionButtonMenuItem(
                                onClick = {
                                    expanded = false
                                    onConfigureWidget(configurableEditingWidget)
                                },
                                icon = { Icon(Icons.Default.Settings, "Widget Settings") },
                                text = { Text("Widget Settings") }
                            )
                        }

                        FloatingActionButtonMenuItem(
                            onClick = {
                                expanded = false
                                onAddWidgetClick()
                            },
                            icon = { Icon(Icons.Default.Add, "Add Widget") },
                            text = { Text("Add Widget") }
                        )
                    }

                } else {
                    // Expressive Menu for Normal Mode
                    FloatingActionButtonMenu(
                        expanded = expanded,
                        button = {
                            MediumFloatingActionButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.onGloballyPositioned {
                                    fabBounds.value = it.boundsInWindow()
                                }
                            ) {
                                val rotation by animateFloatAsState(
                                    if (expanded) 45f else 0f,
                                    label = "fabRotation"
                                )

                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = "Menu",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .rotate(rotation),

                                    )
                            }

                        }
                    ) {
                        FloatingActionButtonMenuItem(
                            onClick = {
                                expanded = false
                                onAddWidgetClick()
                            },
                            icon = { Icon(Icons.Default.Add, "Add Widget") },
                            text = { Text("Add Widget") }
                        )
                        FloatingActionButtonMenuItem(
                            onClick = {
                                expanded = false
                                val intent = Intent(context, com.feldman.clock.ui.standby.activities.StandbySettingsActivity::class.java)
                                context.startActivity(intent)
                            },
                            icon = { Icon(Icons.Default.Settings, "Standby Settings") },
                            text = { Text("Standby Settings") }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Background tap detector to exit edit mode
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 16.dp, bottom = 24.dp)
        ) {
            if (widgets.isEmpty())
            {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No widgets added. Tap + to add one.")
                }
            }
            else if (layoutMode == WidgetHostLayoutMode.SPLIT_VERTICAL_PAGER) {
                SplitWidgetPagerContent(
                    widgets = widgets,
                    appWidgetHost = appWidgetHost,
                    appWidgetManager = appWidgetManager,
                    editingWidgetId = editingWidgetId.value,
                    showAddButton = showAddButton,
                    fabBounds = fabBounds.value,
                    onEditStart = { widgetId ->
                        if (showAddButton) {
                            Log.d(
                                CLOCK_WIDGET_TOUCH_TAG,
                                "SPLIT edit toggle requested widgetId=$widgetId previousEditing=${editingWidgetId.value}"
                            )
                            editingWidgetId.value =
                                if (editingWidgetId.value == widgetId) null else widgetId
                            isOverDeleteZone.value = false
                        }
                    },
                    onEditEnd = {
                        Log.d(CLOCK_WIDGET_TOUCH_TAG, "SPLIT editEnd currentEditing=${editingWidgetId.value}")
                        editingWidgetId.value = null
                        isOverDeleteZone.value = false
                    },
                    onDeleteZoneChanged = { isOverDeleteZone.value = it },
                    onDeleteWidget = { widget ->
                        onDeleteWidget(widget)
                        onWidgetsChanged()
                        if (editingWidgetId.value == widget.appWidgetId) {
                            editingWidgetId.value = null
                        }
                        isOverDeleteZone.value = false
                    },
                    onResizeWidget = { widget, newSpanX, newSpanY ->
                        val index = widgets.indexOfFirst { it.appWidgetId == widget.appWidgetId }
                        if (index != -1) {
                            val updated = widgets[index].copy(
                                spanX = newSpanX.coerceIn(1, GRID_COLUMNS),
                                spanY = newSpanY.coerceIn(1, 10)
                            )
                            widgets[index] = updated
                            onResizeWidget(updated, updated.spanX, updated.spanY)
                            onWidgetsChanged()
                        }
                    },
                    onConfigureWidget = onConfigureWidget,
                    onDismiss = onDismiss
                )
            }
            else
            {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize()
                ) {

                    val widgetPaddingCompensation = 30.dp

                    val screenWidth = maxWidth

                    val horizontalMargin = 0.dp
                    val gridUnit = maxWidth / GRID_COLUMNS
                    val gridWidth = gridUnit * GRID_COLUMNS 
                    val gridStartX = 0.dp


                    // Use a stable maxRow for centering during edit sessions to prevent the grid 
                    // from jumping up/down while the user is actively dragging a widget.
                    val stableMaxRow = remember(editingWidgetId.value == null) {
                        widgets.maxOfOrNull { it.row + it.spanY } ?: 0
                    }
                    val totalContentHeight = (gridUnit * stableMaxRow)
                    
                    val isLandscape = maxWidth > maxHeight
                    val centeringPadding = if (widgets.isNotEmpty()) 16.dp else 0.dp

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = centeringPadding)
                    ) {
                        if (editingWidgetId.value != null || !showAddButton) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .zIndex(0f)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        if (editingWidgetId.value != null) {
                                            Log.d(
                                                CLOCK_WIDGET_TOUCH_TAG,
                                                "GRID outside click editingWidgetId=${editingWidgetId.value} showAddButton=$showAddButton -> editEnd"
                                            )
                                            editingWidgetId.value = null
                                            isOverDeleteZone.value = false
                                        } else {
                                            Log.d(
                                                CLOCK_WIDGET_TOUCH_TAG,
                                                "GRID outside click editingWidgetId=null showAddButton=$showAddButton -> dismiss"
                                            )
                                            onDismiss()
                                        }
                                    }
                            )
                        }

                        // Use the dynamically calculated gridUnit from outer scope
                        val colPitch = gridUnit

// Pixel padding is total 30dp → 15dp each side
                        val pixelInset = (GRID_PADDING_DP / 2).dp

                        widgets.forEach { widget ->
                            val spanW = colPitch * widget.spanX
                            val spanH = gridUnit * visualSpanY(widget.spanY)

                            val x = gridStartX + colPitch * widget.col
                            val y = gridUnit * widget.row

                            key(widget.appWidgetId) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = x, y = y)
                                        .width(spanW)
                                        .height(spanH)
                                        .zIndex(1f)
                                ) {
                                    // This is the ACTUAL widget area: 70n - 30, centered inside the span
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(pixelInset) // 15dp on all sides
                                    ) {
                                        val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
                                        val isConfigurable = appWidgetInfo?.configure != null

                                        ResizableWidgetContainer(
                                            widgetData = widget,
                                            allWidgets = widgets,
                                            isEditing = editingWidgetId.value == widget.appWidgetId,
                                            showAddButton = showAddButton,
                                            cellWidth = with(LocalDensity.current) { colPitch.toPx() },
                                            cellHeight = with(LocalDensity.current) { gridUnit.toPx() },
                                            maxScreenWidth = with(LocalDensity.current) { this@BoxWithConstraints.maxWidth.toPx() },
                                            maxScreenHeight = with(LocalDensity.current) { this@BoxWithConstraints.maxHeight.toPx() },
                                            fabBounds = fabBounds.value,
                                            onDeleteZoneChanged = { isOverDeleteZone.value = it },
                                            onEditStart = { editingWidgetId.value = widget.appWidgetId },
                                            onEditEnd = {
                                                Log.d(CLOCK_WIDGET_TOUCH_TAG, "GRID editEnd widgetId=${widget.appWidgetId}")
                                                editingWidgetId.value = null
                                                isOverDeleteZone.value = false
                                            },
                                            isConfigurable = isConfigurable,

                                            onResize = { newSpanX, newSpanY, newCol, newRow ->
                                                val index = widgets.indexOfFirst { it.appWidgetId == widget.appWidgetId }
                                                if (index != -1) {
                                                    val old = widgets[index]

                                                    // Clamp so we never go outside the 6-column grid
                                                    val clampedSpanX = newSpanX.coerceIn(1, GRID_COLUMNS)
                                                    val clampedCol = newCol.coerceIn(0, (GRID_COLUMNS - clampedSpanX).coerceAtLeast(0))
                                                    val clampedRow = newRow.coerceAtLeast(0)

                                                    val updated = old.copy(
                                                        spanX = clampedSpanX,
                                                        spanY = newSpanY.coerceAtLeast(1),
                                                        col = clampedCol,
                                                        row = clampedRow
                                                    )

                                                    widgets[index] = updated
                                                    onResizeWidget(updated, updated.spanX, updated.spanY)
                                                    onWidgetsChanged()
                                                }
                                            },

                                            onMove = { dCol, dRow ->
                                                val index = widgets.indexOfFirst { it.appWidgetId == widget.appWidgetId }
                                                if (index != -1) {
                                                    val old = widgets[index]
                                                    val maxCol = GRID_COLUMNS - old.spanX
                                                    val newC = if (maxCol >= 0) (old.col + dCol).coerceIn(0, maxCol) else 0
                                                    val newR = (old.row + dRow).coerceAtLeast(0)

                                                    val updated = old.copy(col = newC, row = newR)
                                                    widgets[index] = updated
                                                    onMoveWidget(updated, newC, newR)
                                                    onWidgetsChanged()
                                                }
                                            },

                                            onDelete = {
                                                onDeleteWidget(widget)
                                                onWidgetsChanged()
                                                if (editingWidgetId.value == widget.appWidgetId) {
                                                    editingWidgetId.value = null
                                                }
                                                isOverDeleteZone.value = false
                                            },

                                            onConfigure = { onConfigureWidget(widget) },

                                            content = {
                                                AndroidWidgetHost(
                                                    widgetData = widget,
                                                    appWidgetHost = appWidgetHost,
                                                    appWidgetManager = appWidgetManager,
                                                    widthDp = (spanW.value - GRID_PADDING_DP).roundToInt().coerceAtLeast(1),
                                                    heightDp = (spanH.value - GRID_PADDING_DP).roundToInt().coerceAtLeast(1),
                                                    onLongClick = {
                                                        Log.d(
                                                            CLOCK_WIDGET_TOUCH_TAG,
                                                            "GRID native longClick widgetId=${widget.appWidgetId} showAddButton=$showAddButton"
                                                        )
                                                        if (showAddButton) editingWidgetId.value = widget.appWidgetId
                                                    },
                                                    onDismiss = onDismiss
                                                )
                                            }
                                        )

                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun SplitWidgetPagerContent(
    widgets: MutableList<WidgetData>,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    editingWidgetId: Int?,
    showAddButton: Boolean,
    fabBounds: Rect?,
    onEditStart: (Int) -> Unit,
    onEditEnd: () -> Unit,
    onDeleteZoneChanged: (Boolean) -> Unit,
    onDeleteWidget: (WidgetData) -> Unit,
    onResizeWidget: (WidgetData, Int, Int) -> Unit,
    onConfigureWidget: (WidgetData) -> Unit,
    onDismiss: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { widgets.size })
    var previousWidgetCount by remember { mutableIntStateOf(widgets.size) }
    var pendingFastSwipeTarget by remember { mutableIntStateOf(-1) }
    var fastSwipeRequestId by remember { mutableIntStateOf(0) }

    LaunchedEffect(widgets.size) {
        if (widgets.isEmpty()) {
            previousWidgetCount = 0
            return@LaunchedEffect
        }

        val lastPage = widgets.lastIndex
        when {
            widgets.size > previousWidgetCount -> pagerState.scrollToPage(lastPage)
            pagerState.currentPage > lastPage -> pagerState.scrollToPage(lastPage)
        }
        previousWidgetCount = widgets.size
    }

    LaunchedEffect(widgets.size, editingWidgetId) {
        if (editingWidgetId != null && widgets.none { it.appWidgetId == editingWidgetId }) {
            onEditEnd()
        }
    }

    LaunchedEffect(fastSwipeRequestId) {
        if (fastSwipeRequestId == 0) return@LaunchedEffect
        val targetPage = pendingFastSwipeTarget
        if (targetPage !in widgets.indices) return@LaunchedEffect

        // Let the AndroidView/RemoteViews touch stream finish before starting the pager animation.
        delay(80L)
        if (targetPage != pagerState.currentPage) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "SPLIT pager fast widget animate " +
                    "from=${pagerState.currentPage} to=$targetPage request=$fastSwipeRequestId"
            )
            pagerState.animateScrollToPage(targetPage)
        }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(showAddButton, editingWidgetId, widgets.size) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val down = event.changes.firstOrNull { it.changedToDownIgnoreConsumed() }
                        if (down != null) {
                            Log.d(
                                CLOCK_WIDGET_TOUCH_TAG,
                                "SPLIT pager pointer DOWN page=${pagerState.currentPage} " +
                                    "showAddButton=$showAddButton editingWidgetId=$editingWidgetId " +
                                    "pos=${down.position.x.toInt()},${down.position.y.toInt()}"
                            )
                        }
                        val up = event.changes.firstOrNull { it.changedToUpIgnoreConsumed() }
                        if (up != null) {
                            Log.d(
                                CLOCK_WIDGET_TOUCH_TAG,
                                "SPLIT pager pointer UP page=${pagerState.currentPage} " +
                                    "showAddButton=$showAddButton editingWidgetId=$editingWidgetId " +
                                    "pos=${up.position.x.toInt()},${up.position.y.toInt()}"
                            )
                        }
                    }
                }
            },
        userScrollEnabled = editingWidgetId == null && widgets.size > 1
    ) { page ->
        val widget = widgets.getOrNull(page) ?: return@VerticalPager

        Box(modifier = Modifier.fillMaxSize()) {
            if (editingWidgetId != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(0f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            Log.d(
                                CLOCK_WIDGET_TOUCH_TAG,
                                "SPLIT outside click page=$page widgetId=${widget.appWidgetId} editingWidgetId=$editingWidgetId showAddButton=$showAddButton -> editEnd"
                            )
                            onEditEnd()
                        }
                )
            }

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .zIndex(1f),
                contentAlignment = Alignment.Center
            ) {
                val visualRows = visualSpanY(widget.spanY).coerceAtLeast(1)
                val aspect = widget.spanX.toFloat() / visualRows.toFloat()
                val heightFromWidth = maxWidth / aspect
                val widgetWidth: androidx.compose.ui.unit.Dp
                val widgetHeight: androidx.compose.ui.unit.Dp

                if (heightFromWidth <= maxHeight) {
                    widgetWidth = maxWidth
                    widgetHeight = heightFromWidth
                } else {
                    widgetWidth = maxHeight * aspect
                    widgetHeight = maxHeight
                }

                val density = LocalDensity.current
                val cellWidth = with(density) {
                    widgetWidth.toPx() / widget.spanX.coerceAtLeast(1)
                }
                val cellHeight = with(density) {
                    widgetHeight.toPx() / visualRows
                }
                val pageMaxWidth = maxWidth
                val pageMaxHeight = maxHeight
                val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widget.appWidgetId)
                val isConfigurable = appWidgetInfo?.configure != null

                key(widget.appWidgetId) {
                    Box(
                        modifier = Modifier
                            .width(widgetWidth)
                            .height(widgetHeight)
                    ) {
                        ResizableWidgetContainer(
                            widgetData = widget,
                            allWidgets = widgets,
                            isEditing = editingWidgetId == widget.appWidgetId,
                            showAddButton = showAddButton,
                            cellWidth = cellWidth,
                            cellHeight = cellHeight,
                            maxScreenWidth = with(density) { pageMaxWidth.toPx() },
                            maxScreenHeight = with(density) { pageMaxHeight.toPx() },
                            fabBounds = fabBounds,
                            onDeleteZoneChanged = onDeleteZoneChanged,
                            onEditStart = { onEditStart(widget.appWidgetId) },
                            onEditEnd = onEditEnd,
                            isConfigurable = isConfigurable,
                            useGridPlacement = false,
                            moveEnabled = false,
                            onResize = { newSpanX, newSpanY, _, _ ->
                                onResizeWidget(widget, newSpanX, newSpanY)
                            },
                            onMove = { _, _ -> },
                            onDelete = { onDeleteWidget(widget) },
                            onConfigure = { onConfigureWidget(widget) },
                            content = {
                                val hostWidthDp =
                                    (widgetWidth.value - GRID_PADDING_DP).roundToInt()
                                        .coerceAtLeast(1)
                                val hostHeightDp =
                                    (widgetHeight.value - GRID_PADDING_DP).roundToInt()
                                        .coerceAtLeast(1)

                                AndroidWidgetHost(
                                    widgetData = widget,
                                    appWidgetHost = appWidgetHost,
                                    appWidgetManager = appWidgetManager,
                                    widthDp = hostWidthDp,
                                    heightDp = hostHeightDp,
                                    onVerticalSwipe = { deltaY ->
                                        if (editingWidgetId == null && widgets.size > 1) {
                                            val targetPage = when {
                                                deltaY < 0f -> pagerState.currentPage + 1
                                                deltaY > 0f -> pagerState.currentPage - 1
                                                else -> pagerState.currentPage
                                            }.coerceIn(0, widgets.lastIndex)

                                            if (targetPage != pagerState.currentPage) {
                                                Log.d(
                                                    CLOCK_WIDGET_TOUCH_TAG,
                                                    "SPLIT pager fast widget swipe " +
                                                        "from=${pagerState.currentPage} to=$targetPage " +
                                                        "deltaY=${deltaY.toInt()}"
                                                )
                                                pendingFastSwipeTarget = targetPage
                                                fastSwipeRequestId += 1
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        Log.d(
                                            CLOCK_WIDGET_TOUCH_TAG,
                                            "SPLIT native longClick page=$page widgetId=${widget.appWidgetId} showAddButton=$showAddButton"
                                        )
                                        if (showAddButton) {
                                            onEditStart(widget.appWidgetId)
                                        }
                                    },
                                    onDismiss = onDismiss
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ResizableWidgetContainer(
    widgetData: WidgetData,
    allWidgets: List<WidgetData>,
    isEditing: Boolean,
    cellWidth: Float,
    cellHeight: Float,
    maxScreenWidth: Float,
    maxScreenHeight: Float,
    fabBounds: Rect?,
    onDeleteZoneChanged: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit,
    onResize: (Int, Int, Int, Int) -> Unit, // spanX, spanY, col, row
    onMove: (Int, Int) -> Unit,
    onDelete: () -> Unit,
    onConfigure: () -> Unit,
    isConfigurable: Boolean,
    showAddButton: Boolean,
    useGridPlacement: Boolean = true,
    moveEnabled: Boolean = true,
    content: @Composable () -> Unit
) {
    fun overlaps(aCol: Int, aRow: Int, aSpanX: Int, aSpanY: Int, b: WidgetData): Boolean {
        val ax1 = aCol
        val ax2 = aCol + aSpanX
        val ay1 = aRow
        val ay2 = aRow + aSpanY

        val bx1 = b.col
        val bx2 = b.col + b.spanX
        val by1 = b.row
        val by2 = b.row + b.spanY

        return ax1 < bx2 && ax2 > bx1 && ay1 < by2 && ay2 > by1
    }

    fun canPlace(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        if (!useGridPlacement) {
            return spanX in 1..GRID_COLUMNS && spanY in 1..10
        }

        // bounds
        if (col < 0) return false
        if (row < 0) return false
        if (spanX < 1 || spanY < 1) return false
        if (col + spanX > GRID_COLUMNS) return false

        // collision (ignore self)
        return allWidgets.none { other ->
            other.appWidgetId != widgetData.appWidgetId &&
                    overlaps(col, row, spanX, spanY, other)
        }
    }

    val density = LocalDensity.current
    val currentOnResize by rememberUpdatedState(onResize)
    val currentOnMove by rememberUpdatedState(onMove)
    val currentOnDelete by rememberUpdatedState(onDelete)
    val currentOnEditEnd by rememberUpdatedState(onEditEnd)

    // Move Accumulators (Visual only)
    val moveAccX = remember { mutableFloatStateOf(0f) }
    val moveAccY = remember { mutableFloatStateOf(0f) }

    val currentWidgetData by rememberUpdatedState(widgetData) // Needed for clamping logic

    // Preview Grid Deltas (Where will it snap?)
    val previewDCol = remember { mutableIntStateOf(0) }
    val previewDRow = remember { mutableIntStateOf(0) }
    val lastValidDCol = remember { mutableIntStateOf(0) }
    val lastValidDRow = remember { mutableIntStateOf(0) }
    val isPlacementValid = remember { mutableStateOf(true) }

    LaunchedEffect(isEditing, moveEnabled) {
        if (isEditing && !moveEnabled) {
            onDeleteZoneChanged(false)
        }
    }

    // Outer Box for the widget + overlay + handles
    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (isEditing) {
                    val xDp = with(density) { moveAccX.floatValue.toDp() }
                    val yDp = with(density) { moveAccY.floatValue.toDp() }

                    Modifier
                        .offset(x = xDp, y = yDp)
                        .border(
                            3.dp,
                            if (isPlacementValid.value) colorScheme.primary
                            else colorScheme.error,
                            RoundedCornerShape(28.dp)
                        )
                } else Modifier
            )
    ) {
        // Main Widget Content
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        if (isEditing) {
            val isOverFabLocal = remember { mutableStateOf(false) }
            val widgetCoordinates = remember { mutableStateOf<LayoutCoordinates?>(null) }

            if (moveEnabled) {
            // Snap Guide
            // The Parent Box is ALREADY offset by (moveAccX, moveAccY).
            // We want the Snap Guide to appear at (previewDCol * cellWidth, previewDRow * cellHeight) relative to the START.
            // So relative to the CURRENT parent position, we must subtract the moveAcc.

            val snapColor = if (isOverFabLocal.value) colorScheme.error else colorScheme.tertiary

            val snapOffsetX = if (isOverFabLocal.value && fabBounds != null) {
                // Calculate offset to center the outline on the FAB
                widgetCoordinates.value?.let { coords: LayoutCoordinates ->
                    val widgetBounds = coords.boundsInWindow()
                    fabBounds.center.x - widgetBounds.center.x
                } ?: 0f
            } else {
                (previewDCol.value.toFloat() * cellWidth) - moveAccX.floatValue
            }

            val snapOffsetY = if (isOverFabLocal.value && fabBounds != null) {
                widgetCoordinates.value?.let { coords: LayoutCoordinates ->
                    val widgetBounds = coords.boundsInWindow()
                    fabBounds.center.y - widgetBounds.center.y
                } ?: 0f
            } else {
                (previewDRow.value.toFloat() * cellHeight) - moveAccY.floatValue
            }

            Box(
                modifier = Modifier
                    // Hide outline if over toggle to avoid visual clutter
                    .alpha(if (isOverFabLocal.value) 0f else 1f)
                    .matchParentSize()
                    .offset(
                        x = (snapOffsetX / density.density).dp,
                        y = (snapOffsetY / density.density).dp
                    )
                    .border(2.dp, snapColor, RoundedCornerShape(28.dp))
                    .background(snapColor.copy(alpha = 0.2f), RoundedCornerShape(28.dp))
            )

            // Drag Capture Overlay - Sit on top of widget content to intercept touches
            // widgetCoordinates is now moved above

            Box(
                modifier = Modifier
                    .matchParentSize()
                    .onGloballyPositioned { coordinates ->
                        widgetCoordinates.value = coordinates
                    }
                    .pointerInput(currentOnEditEnd) {
                        detectTapGestures(
                            onLongPress = {
                                currentOnEditEnd()
                            }
                        )
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = {
                                moveAccX.floatValue = 0f
                                moveAccY.floatValue = 0f
                                previewDCol.value = 0
                                previewDRow.value = 0
                                lastValidDCol.value = 0
                                lastValidDRow.value = 0
                                isPlacementValid.value = true
                                isOverFabLocal.value = false
                                onDeleteZoneChanged(false)
                            },
                            onDragEnd = {
                                if (isOverFabLocal.value) {
                                    currentOnDelete()
                                } else if (isPlacementValid.value) {
                                    if (previewDCol.value != 0 || previewDRow.value != 0) {
                                        currentOnMove(previewDCol.value, previewDRow.value)
                                    }
                                }

                                moveAccX.floatValue = 0f
                                moveAccY.floatValue = 0f
                                previewDCol.value = 0
                                previewDRow.value = 0
                                lastValidDCol.value = 0
                                lastValidDRow.value = 0
                                isPlacementValid.value = true
                                isOverFabLocal.value = false
                                onDeleteZoneChanged(false)
                            },
                            onDragCancel = {
                                moveAccX.floatValue = 0f
                                moveAccY.floatValue = 0f
                                previewDCol.value = 0
                                previewDRow.value = 0
                                lastValidDCol.value = 0
                                lastValidDRow.value = 0
                                isPlacementValid.value = true
                                isOverFabLocal.value = false
                                onDeleteZoneChanged(false)
                            }

                        ) { change, dragAmount ->
                            change.consume()

                            val widgetWidth = currentWidgetData.spanX * cellWidth
                            val widgetHeight = currentWidgetData.spanY * cellHeight

                            val minMoveX = -(currentWidgetData.col * cellWidth)
                            val maxMoveX = maxScreenWidth - (currentWidgetData.col * cellWidth + widgetWidth)

                            val minMoveY = -(currentWidgetData.row * cellHeight)
                            val maxMoveY = maxScreenHeight - (currentWidgetData.row * cellHeight + widgetHeight)

                            moveAccX.floatValue = (moveAccX.floatValue + dragAmount.x).let {
                                if (maxMoveX > minMoveX) it.coerceIn(minMoveX, maxMoveX) else minMoveX
                            }
                            moveAccY.floatValue = (moveAccY.floatValue + dragAmount.y).let {
                                if (maxMoveY > minMoveY) it.coerceIn(minMoveY, maxMoveY) else minMoveY
                            }

                            // Robust Hit Detection using Touch Position in Window
                            val isOverFab = fabBounds?.let { fab ->
                                widgetCoordinates.value?.let { coords ->
                                    // Use the actual touch position in window coordinates
                                    val touchInWindow = coords.positionInWindow() + change.position
                                    fab.contains(touchInWindow)
                                } ?: false
                            } ?: false

                            isOverFabLocal.value = isOverFab
                            onDeleteZoneChanged(isOverFab)


                            val dCol = (moveAccX.floatValue / cellWidth).roundToInt()
                            val dRow = (moveAccY.floatValue / cellHeight).roundToInt()

                            val minDCol = -currentWidgetData.col
                            val maxDCol = (GRID_COLUMNS - currentWidgetData.spanX) - currentWidgetData.col

                            val clampedDCol =
                                if (maxDCol > minDCol) {
                                    dCol.coerceIn(minDCol, maxDCol)
                                } else {
                                    minDCol
                                }

                            val minDRow = -currentWidgetData.row
                            val maxDRow =
                                (maxScreenHeight / cellHeight).toInt() - (currentWidgetData.row + currentWidgetData.spanY)

                            val clampedDRow = if (maxDRow > minDRow) {
                                dRow.coerceIn(minDRow, maxDRow)
                            } else {
                                minDRow
                            }

                            val candidateCol = currentWidgetData.col + clampedDCol
                            val candidateRow = currentWidgetData.row + clampedDRow

                            val ok = canPlace(
                                col = candidateCol,
                                row = candidateRow,
                                spanX = currentWidgetData.spanX,
                                spanY = currentWidgetData.spanY
                            )

                            isPlacementValid.value = ok

                            if (ok) {
                                previewDCol.value = clampedDCol
                                previewDRow.value = clampedDRow
                                lastValidDCol.value = clampedDCol
                                lastValidDRow.value = clampedDRow
                            } else {
                                // ✅ do NOT highlight occupied spots:
                                // keep highlight at last valid (or 0,0 if none)
                                previewDCol.value = lastValidDCol.value
                                previewDRow.value = lastValidDRow.value
                            }


                            }
                        }
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .onGloballyPositioned { coordinates ->
                            widgetCoordinates.value = coordinates
                        }
                        .pointerInput(currentOnEditEnd) {
                            detectTapGestures(
                                onLongPress = {
                                    currentOnEditEnd()
                                }
                            )
                        }
                )
            }

                // --- Top Controls (Delete & Settings) ---
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(12.dp)
                        .zIndex(2f) // Above the drag overlay
                ) {
                    // Delete Button (Top Left)
                    IconButton(
                        onClick = currentOnDelete,
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Surface(
                            color = colorScheme.errorContainer.copy(alpha = 0.9f),
                            shape = CircleShape,
                            tonalElevation = 4.dp,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = colorScheme.onErrorContainer,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }

                    // Settings Button (Top Right)
                    if (isConfigurable) {
                        IconButton(
                            onClick = onConfigure,
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Surface(
                                color = colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                shape = CircleShape,
                                tonalElevation = 4.dp,
                                modifier = Modifier.size(42.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Settings",
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // --- RIGHT HANDLE (Width +) ---
            val dragAccumulatorX = remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .offset(x = 46.5.dp)
                    .size(96.dp)  // Huge touch target
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccumulatorX.floatValue = 0f },
                            onDragEnd = { dragAccumulatorX.floatValue = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulatorX.floatValue += dragAmount.x

                            // Threshold: 0.7 * Cell Width (Easier to trigger)
                            val thresholdPx = cellWidth * 0.7f

                            if (dragAccumulatorX.floatValue > thresholdPx) {
                                val newSpanX = currentWidgetData.spanX + 1
                                if (newSpanX <= GRID_COLUMNS) {
                                    val ok = canPlace(
                                        col = currentWidgetData.col,
                                        row = currentWidgetData.row,
                                        spanX = newSpanX,
                                        spanY = currentWidgetData.spanY
                                    )
                                    if (ok) {
                                        currentOnResize(newSpanX, currentWidgetData.spanY, currentWidgetData.col, currentWidgetData.row)
                                        dragAccumulatorX.floatValue -= thresholdPx
                                    } else {
                                        // refuse: keep accumulator pinned so it "pushes"
                                        dragAccumulatorX.floatValue = thresholdPx
                                    }
                                }
                            } else if (dragAccumulatorX.floatValue < -thresholdPx) {
                                if (currentWidgetData.spanX > 1) {
                                    // Shrink Right: Col stays same
                                    currentOnResize(currentWidgetData.spanX - 1, currentWidgetData.spanY, currentWidgetData.col, currentWidgetData.row)
                                    dragAccumulatorX.floatValue += thresholdPx
                                } else {
                                    dragAccumulatorX.floatValue = -thresholdPx
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(40.dp)
                        .background(colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }

            // --- LEFT HANDLE (Width -, Move Left) ---
            val dragAccumulatorLeft = remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = (-46.5).dp)
                    .size(96.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccumulatorLeft.floatValue = 0f },
                            onDragEnd = { dragAccumulatorLeft.floatValue = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulatorLeft.floatValue += dragAmount.x // Negative = left

                            val thresholdPx = cellWidth * 0.7f

                            if (dragAccumulatorLeft.floatValue < -thresholdPx) {
                                // Drag Left -> EXPAND left (col - 1, spanX + 1)
                                val newCol = if (useGridPlacement) {
                                    currentWidgetData.col - 1
                                } else {
                                    currentWidgetData.col
                                }
                                val newSpanX = currentWidgetData.spanX + 1

                                val ok = canPlace(
                                    col = newCol,
                                    row = currentWidgetData.row,
                                    spanX = newSpanX,
                                    spanY = currentWidgetData.spanY
                                )

                                if (ok) {
                                    currentOnResize(
                                        newSpanX,
                                        currentWidgetData.spanY,
                                        newCol,
                                        currentWidgetData.row
                                    )
                                    dragAccumulatorLeft.floatValue += thresholdPx
                                } else {
                                    // refuse / "push back"
                                    dragAccumulatorLeft.floatValue = -thresholdPx
                                }
                            } else if (dragAccumulatorLeft.floatValue > thresholdPx) {
                                // Drag Right -> SHRINK from left (col + 1, spanX - 1)
                                val newSpanX = currentWidgetData.spanX - 1
                                val newCol = if (useGridPlacement) {
                                    currentWidgetData.col + 1
                                } else {
                                    currentWidgetData.col
                                }
                                if (newSpanX >= 1) {
                                    currentOnResize(
                                        newSpanX,
                                        currentWidgetData.spanY,
                                        newCol,
                                        currentWidgetData.row
                                    )
                                    dragAccumulatorLeft.floatValue -= thresholdPx
                                } else {
                                    dragAccumulatorLeft.floatValue = thresholdPx
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(6.dp)
                        .height(40.dp)
                        .background(colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }



            // --- BOTTOM HANDLE (Height +) ---
            val dragAccumulatorY = remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 46.5.dp)
                    .size(96.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccumulatorY.floatValue = 0f },
                            onDragEnd = { dragAccumulatorY.floatValue = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulatorY.floatValue += dragAmount.y

                            val stepPx = cellHeight

                            if (dragAccumulatorY.floatValue > stepPx) {
                                // Drag Down -> EXPAND down (spanY + 1)
                                val newSpanY = currentWidgetData.spanY + 1
                                if (newSpanY <= 10) {
                                    val ok = canPlace(
                                        col = currentWidgetData.col,
                                        row = currentWidgetData.row,
                                        spanX = currentWidgetData.spanX,
                                        spanY = newSpanY
                                    )
                                    if (ok) {
                                        currentOnResize(
                                            currentWidgetData.spanX,
                                            newSpanY,
                                            currentWidgetData.col,
                                            currentWidgetData.row
                                        )
                                        dragAccumulatorY.floatValue -= stepPx
                                    } else {
                                        dragAccumulatorY.floatValue = stepPx
                                    }
                                }
                            } else if (dragAccumulatorY.floatValue < -stepPx) {
                                // Drag Up -> SHRINK from bottom (spanY - 1)
                                val newSpanY = currentWidgetData.spanY - 1
                                if (newSpanY >= 1) {
                                    currentOnResize(
                                        currentWidgetData.spanX,
                                        newSpanY,
                                        currentWidgetData.col,
                                        currentWidgetData.row
                                    )
                                    dragAccumulatorY.floatValue += stepPx
                                } else {
                                    dragAccumulatorY.floatValue = -stepPx
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(6.dp)
                        .background(colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }


            // --- TOP HANDLE (Height -, Move Up) ---
            val dragAccumulatorTop = remember { mutableFloatStateOf(0f) }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (-46.5).dp)
                    .size(96.dp)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { dragAccumulatorTop.floatValue = 0f },
                            onDragEnd = { dragAccumulatorTop.floatValue = 0f }
                        ) { change, dragAmount ->
                            change.consume()
                            dragAccumulatorTop.floatValue += dragAmount.y // Negative = up

                            val stepPx = cellHeight

                            if (dragAccumulatorTop.floatValue < -stepPx) {
                                // Drag Up -> EXPAND up (row - 1, spanY + 1)
                                val newRow = if (useGridPlacement) {
                                    currentWidgetData.row - 1
                                } else {
                                    currentWidgetData.row
                                }
                                val newSpanY = currentWidgetData.spanY + 1

                                val ok = canPlace(
                                    col = currentWidgetData.col,
                                    row = newRow,
                                    spanX = currentWidgetData.spanX,
                                    spanY = newSpanY
                                )

                                if (ok) {
                                    currentOnResize(
                                        currentWidgetData.spanX,
                                        newSpanY,
                                        currentWidgetData.col,
                                        newRow
                                    )
                                    dragAccumulatorTop.floatValue += stepPx
                                } else {
                                    dragAccumulatorTop.floatValue = -stepPx
                                }
                            } else if (dragAccumulatorTop.floatValue > stepPx) {
                                // Drag Down -> SHRINK from top (row + 1, spanY - 1)
                                val newSpanY = currentWidgetData.spanY - 1
                                val newRow = if (useGridPlacement) {
                                    currentWidgetData.row + 1
                                } else {
                                    currentWidgetData.row
                                }
                                if (newSpanY >= 1) {
                                    currentOnResize(
                                        currentWidgetData.spanX,
                                        newSpanY,
                                        currentWidgetData.col,
                                        newRow
                                    )
                                    dragAccumulatorTop.floatValue -= stepPx
                                } else {
                                    dragAccumulatorTop.floatValue = stepPx
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(6.dp)
                        .background(colorScheme.primary, RoundedCornerShape(3.dp))
                )
            }

        }
    }
}


@Composable
fun AndroidWidgetHost(
    widgetData: WidgetData,
    appWidgetHost: AppWidgetHost,
    appWidgetManager: AppWidgetManager,
    widthDp: Int,
    heightDp: Int,
    onVerticalSwipe: (Float) -> Unit = {},
    onLongClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appWidgetInfo = appWidgetManager.getAppWidgetInfo(widgetData.appWidgetId)
        ?: run {
            Log.w(
                CLOCK_WIDGET_HOST_TAG,
                "AndroidWidgetHost missing provider info appWidgetId=${widgetData.appWidgetId}"
            )
            return
        }
    var lastOptionsKey by remember { mutableStateOf<WidgetOptionsKey?>(null) }
    var hostView by remember { mutableStateOf<LauncherAppWidgetHostView?>(null) }
    val touchSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
    val widgetTapCancelSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop }
    val widgetReleaseSlop = remember(context) { ViewConfiguration.get(context).scaledTouchSlop * 2 }
    val widgetFastSwipeVelocity = remember(context) {
        ViewConfiguration.get(context).scaledMinimumFlingVelocity
            .toFloat()
            .coerceAtLeast(900f)
    }
    val currentOnVerticalSwipe by rememberUpdatedState(onVerticalSwipe)
    val downX = remember { mutableFloatStateOf(0f) }
    val downY = remember { mutableFloatStateOf(0f) }
    var forwardingToPager by remember { mutableStateOf(false) }
    var streamStartedInBridge by remember { mutableStateOf(false) }
    val bridgeMapping = "normalizedFlipY"
    var activeBridgeMapping by remember { mutableStateOf(bridgeMapping) }

    fun normalizeHostCoordinate(coordinate: Float, size: Int): Float {
        if (size <= 0) return coordinate
        val sizeFloat = size.toFloat()
        return when {
            coordinate < 0f && coordinate + sizeFloat >= 0f -> coordinate + sizeFloat
            coordinate > sizeFloat && coordinate - sizeFloat <= sizeFloat -> coordinate - sizeFloat
            else -> coordinate
        }
    }

    fun clickableTargetAt(parent: View, x: Float, y: Float): View? {
        if (x < 0f || y < 0f || x > parent.width || y > parent.height) return null
        if (parent.visibility != View.VISIBLE) return null

        if (parent is ViewGroup) {
            for (i in parent.childCount - 1 downTo 0) {
                val child = parent.getChildAt(i)
                val childX = x - child.left + child.scrollX - child.translationX
                val childY = y - child.top + child.scrollY - child.translationY
                val hit = clickableTargetAt(child, childX, childY)
                if (hit != null) return hit
            }
        }

        return if (parent.isClickable || parent.hasOnClickListeners()) parent else null
    }

    fun View.debugName(): String {
        val idName = if (id != View.NO_ID) {
            runCatching { resources.getResourceEntryName(id) }.getOrNull()
        } else {
            null
        }
        return if (idName != null) {
            "${javaClass.simpleName}#$idName"
        } else {
            javaClass.simpleName
        }
    }

    fun mappedPointFor(
        event: MotionEvent,
        view: LauncherAppWidgetHostView,
        mapping: String
    ): Pair<Float, Float> {
        val w = view.width.toFloat()
        val h = view.height.toFloat()
        val nx = normalizeHostCoordinate(event.x, view.width)
        val ny = normalizeHostCoordinate(event.y, view.height)
        val rawLocation = IntArray(2)
        view.getLocationOnScreen(rawLocation)
        val rawX = event.rawX - rawLocation[0]
        val rawY = event.rawY - rawLocation[1]

        return when (mapping) {
            "rawScreen" -> rawX to rawY
            "original" -> event.x to event.y
            "normalizedFlipX" -> (w - nx) to ny
            "normalizedFlipY" -> nx to (h - ny)
            "normalizedFlipXY" -> (w - nx) to (h - ny)
            "swap" -> ny to nx
            "swapFlipX" -> (w - ny) to nx
            "swapFlipY" -> ny to (h - nx)
            "swapFlipXY" -> (w - ny) to (h - nx)
            else -> nx to ny
        }
    }

    fun MotionEvent.copyForHostView(view: LauncherAppWidgetHostView): MotionEvent {
        val forwarded = MotionEvent.obtain(this)
        val (mappedX, mappedY) = mappedPointFor(this, view, activeBridgeMapping)
        forwarded.offsetLocation(mappedX - x, mappedY - y)
        return forwarded
    }

    fun dispatchComposeBridgeEvent(
        view: LauncherAppWidgetHostView,
        action: Int,
        x: Float,
        y: Float,
        downTime: Long,
        eventTime: Long
    ): Boolean {
        val localX = x.coerceIn(0f, view.width.toFloat())
        val localY = y.coerceIn(0f, view.height.toFloat())
        val forwarded = MotionEvent.obtain(
            downTime,
            eventTime,
            action,
            localX,
            localY,
            0
        )
        val handled = view.dispatchBridgedTouchEvent(forwarded)
        if (action == MotionEvent.ACTION_DOWN ||
            action == MotionEvent.ACTION_UP ||
            action == MotionEvent.ACTION_CANCEL
        ) {
            Log.d(
                CLOCK_WIDGET_TOUCH_TAG,
                "AndroidWidgetHost composeBridge ${MotionEvent.actionToString(action)} " +
                    "appWidgetId=${widgetData.appWidgetId} " +
                    "local=${localX.toInt()},${localY.toInt()} " +
                    "hit=${clickableTargetAt(view, localX, localY)?.debugName() ?: "none"} " +
                    "viewSize=${view.width}x${view.height} handled=$handled"
            )
        }
        forwarded.recycle()
        return handled
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(28.dp))
            .pointerInput(widgetData.appWidgetId, hostView, touchSlop) {
                awaitEachGesture {
                    val view = hostView
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    if (view == null) {
                        Log.d(
                            CLOCK_WIDGET_TOUCH_TAG,
                            "AndroidWidgetHost composeBridge ACTION_DOWN " +
                                "appWidgetId=${widgetData.appWidgetId} ignored: no host view"
                        )
                        return@awaitEachGesture
                    }

                    val downTime = down.uptimeMillis
                    val downPosition = down.position
                    var previousPosition = downPosition
                    var previousTime = down.uptimeMillis
                    dispatchComposeBridgeEvent(
                        view = view,
                        action = MotionEvent.ACTION_DOWN,
                        x = downPosition.x,
                        y = downPosition.y,
                        downTime = downTime,
                        eventTime = down.uptimeMillis
                    )

                    while (true) {
                        val pointerEvent = awaitPointerEvent(PointerEventPass.Initial)
                        val change = pointerEvent.changes.firstOrNull { it.id == down.id }
                            ?: pointerEvent.changes.firstOrNull()
                            ?: continue

                        if (change.pressed) {
                            val dx = change.position.x - downPosition.x
                            val dy = change.position.y - downPosition.y
                            val absDx = abs(dx)
                            val absDy = abs(dy)
                            val deltaSincePrevious = change.position - previousPosition
                            val timeSincePreviousMs =
                                (change.uptimeMillis - previousTime).coerceAtLeast(1L)
                            val velocityY =
                                deltaSincePrevious.y / timeSincePreviousMs.toFloat() * 1000f
                            val verticalSwipeStarted =
                                absDy > widgetTapCancelSlop && absDy > absDx * 1.2f
                            val movedTooFarForTap =
                                absDx > widgetReleaseSlop || absDy > widgetReleaseSlop
                            val fastVerticalSwipe =
                                abs(velocityY) >= widgetFastSwipeVelocity &&
                                    abs(deltaSincePrevious.y) > abs(deltaSincePrevious.x) * 1.2f
                            val manuallyPageFastSwipe = fastVerticalSwipe
                            if (verticalSwipeStarted || movedTooFarForTap || fastVerticalSwipe) {
                                dispatchComposeBridgeEvent(
                                    view = view,
                                    action = MotionEvent.ACTION_CANCEL,
                                    x = change.position.x,
                                    y = change.position.y,
                                    downTime = downTime,
                                    eventTime = change.uptimeMillis
                                )
                                Log.d(
                                    CLOCK_WIDGET_TOUCH_TAG,
                                    "AndroidWidgetHost composeBridge MOVE appWidgetId=${widgetData.appWidgetId} " +
                                        "crossedSlop dx=${dx.toInt()} dy=${dy.toInt()} " +
                                        "tapThreshold=$widgetTapCancelSlop releaseThreshold=$widgetReleaseSlop " +
                                        "velocityY=${velocityY.toInt()} fastVerticalSwipe=$fastVerticalSwipe " +
                                        "manualPage=$manuallyPageFastSwipe " +
                                        "verticalSwipe=$verticalSwipeStarted -> release to pager"
                                )
                                if (manuallyPageFastSwipe) {
                                    change.consume()
                                    currentOnVerticalSwipe(dy)
                                }
                                return@awaitEachGesture
                            }

                            dispatchComposeBridgeEvent(
                                view = view,
                                action = MotionEvent.ACTION_MOVE,
                                x = change.position.x,
                                y = change.position.y,
                                downTime = downTime,
                                eventTime = change.uptimeMillis
                            )
                            previousPosition = change.position
                            previousTime = change.uptimeMillis
                        } else {
                            dispatchComposeBridgeEvent(
                                view = view,
                                action = MotionEvent.ACTION_UP,
                                x = change.position.x,
                                y = change.position.y,
                                downTime = downTime,
                                eventTime = change.uptimeMillis
                            )
                            change.consume()
                            return@awaitEachGesture
                        }
                    }
                }
            }
            .pointerInteropFilter { event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    Log.d(
                        CLOCK_WIDGET_TOUCH_TAG,
                        "AndroidWidgetHost legacy MotionEvent bridge ignored " +
                            "${MotionEvent.actionToString(event.actionMasked)} " +
                            "appWidgetId=${widgetData.appWidgetId} " +
                            "x=${event.x.toInt()} y=${event.y.toInt()}"
                    )
                }
                return@pointerInteropFilter false

                val view = hostView ?: return@pointerInteropFilter false

                val actionName = MotionEvent.actionToString(event.actionMasked)
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        streamStartedInBridge = true
                        forwardingToPager = false
                        downX.floatValue = event.x
                        downY.floatValue = event.y
                        activeBridgeMapping = bridgeMapping
                        Log.d(
                            CLOCK_WIDGET_TOUCH_TAG,
                            "AndroidWidgetHost bridge $actionName appWidgetId=${widgetData.appWidgetId} " +
                                "x=${event.x.toInt()} y=${event.y.toInt()} " +
                                "mapping=$activeBridgeMapping view=${System.identityHashCode(view)}"
                        )
                    }

                    MotionEvent.ACTION_MOVE -> {
                        if (!forwardingToPager &&
                            (abs(event.x - downX.floatValue) > touchSlop ||
                                abs(event.y - downY.floatValue) > touchSlop)
                        ) {
                            val cancelEvent = event.copyForHostView(view).apply {
                                action = MotionEvent.ACTION_CANCEL
                            }
                            val cancelHandled = view.dispatchBridgedTouchEvent(cancelEvent)
                            cancelEvent.recycle()
                            forwardingToPager = true
                            Log.d(
                                CLOCK_WIDGET_TOUCH_TAG,
                                "AndroidWidgetHost bridge $actionName appWidgetId=${widgetData.appWidgetId} " +
                                    "crossedSlop cancelHandled=$cancelHandled -> release to pager"
                            )
                            return@pointerInteropFilter false
                        }
                    }

                    MotionEvent.ACTION_UP,
                    MotionEvent.ACTION_CANCEL -> {
                        Log.d(
                            CLOCK_WIDGET_TOUCH_TAG,
                            "AndroidWidgetHost bridge $actionName appWidgetId=${widgetData.appWidgetId} " +
                                "forwardingToPager=$forwardingToPager"
                        )
                    }
                }

                if (forwardingToPager) {
                    if (event.actionMasked == MotionEvent.ACTION_UP ||
                        event.actionMasked == MotionEvent.ACTION_CANCEL
                    ) {
                        streamStartedInBridge = false
                        forwardingToPager = false
                    }
                    return@pointerInteropFilter false
                }

                val forwarded = event.copyForHostView(view)
                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    Log.d(
                        CLOCK_WIDGET_TOUCH_TAG,
                            "AndroidWidgetHost bridge $actionName appWidgetId=${widgetData.appWidgetId} " +
                                "map ${event.x.toInt()},${event.y.toInt()} -> " +
                                "${forwarded.x.toInt()},${forwarded.y.toInt()} " +
                                "mapping=$activeBridgeMapping " +
                                "hit=${clickableTargetAt(view, forwarded.x, forwarded.y)?.debugName() ?: "none"} " +
                                "viewSize=${view.width}x${view.height}"
                    )
                }
                val handled = view.dispatchBridgedTouchEvent(forwarded)
                forwarded.recycle()
                val result = handled || streamStartedInBridge

                if (event.actionMasked == MotionEvent.ACTION_DOWN ||
                    event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    Log.d(
                        CLOCK_WIDGET_TOUCH_TAG,
                        "AndroidWidgetHost bridge $actionName appWidgetId=${widgetData.appWidgetId} " +
                            "hostHandled=$handled return=$result"
                    )
                }

                if (event.actionMasked == MotionEvent.ACTION_UP ||
                    event.actionMasked == MotionEvent.ACTION_CANCEL
                ) {
                    streamStartedInBridge = false
                    forwardingToPager = false
                }

                result
            },
        factory = { ctx ->
            // Use the host to create the view
            val view = appWidgetHost.createView(ctx, widgetData.appWidgetId, appWidgetInfo) as LauncherAppWidgetHostView
            hostView = view
            Log.d(
                CLOCK_WIDGET_HOST_TAG,
                "AndroidWidgetHost create appWidgetId=${widgetData.appWidgetId} " +
                    "provider=${appWidgetInfo.provider.flattenToShortString()} " +
                    "size=${widthDp}x${heightDp}dp view=${System.identityHashCode(view)}"
            )
            view.setAppWidget(widgetData.appWidgetId, appWidgetInfo)
            view.blockUnbridgedTouchEvents = true
            // ❗ Remove default system padding to allow exact boundary alignment
            view.setPadding(0, 0, 0, 0)
            view
        },
        update = { view ->
            hostView = view
            view.blockUnbridgedTouchEvents = true
            // Update callback for long press from the native view
            view.onLongPress = {
                Log.d(
                    CLOCK_WIDGET_TOUCH_TAG,
                    "AndroidWidgetHost onLongPress callback appWidgetId=${widgetData.appWidgetId} " +
                        "view=${System.identityHashCode(view)}"
                )
                onLongClick()
            }


            // Sync visual dimensions with Android Widget sizing system
            val optionsKey = WidgetOptionsKey(widgetData.appWidgetId, widthDp, heightDp)
            if (lastOptionsKey != optionsKey) {
                Log.d(
                    CLOCK_WIDGET_HOST_TAG,
                    "AndroidWidgetHost updateOptions appWidgetId=${widgetData.appWidgetId} " +
                        "size=${widthDp}x${heightDp}dp previous=$lastOptionsKey"
                )
                appWidgetManager.updateAppWidgetOptions(
                    widgetData.appWidgetId,
                    appWidgetOptions(widthDp, heightDp)
                )
                lastOptionsKey = optionsKey
            }
        }
    )
}

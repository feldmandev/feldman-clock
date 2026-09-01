package com.feldman.clock.ui.standby.widgets

import com.feldman.clock.R

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.LayoutRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.graphics.shapes.RoundedPolygon
import com.feldman.clock.settings.SettingsDAO
import com.feldman.clock.ui.standby.util.color.toComposeColor
import com.feldman.motion.SettingsScaffold
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

private const val TAG = "WidgetPickerSheet"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandbyWidgetPickerSheet(
    onDismiss: () -> Unit,
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden),
        containerColor = colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Box(modifier = Modifier.fillMaxHeight(0.9f)) {
            WidgetPickerScreen(onWidgetSelected = { providerInfo ->
                onWidgetSelected(providerInfo)
                onDismiss()
            })
        }
    }
}

@Composable
fun WidgetPickerScreen(
    onWidgetSelected: (AppWidgetProviderInfo) -> Unit
) {
    var appGroups by remember { mutableStateOf<List<AppGroup>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    val expandedPackages = remember { mutableStateMapOf<String, Boolean>() }
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val t0 = SystemClock.elapsedRealtime()
        
        val handler = CoroutineExceptionHandler { _, e ->
            Log.e(TAG, "WidgetPickerScreen: coroutine crashed", e)
        }

        var done = false
        val watchdog = launch(handler) {
            var secs = 0
            while (!done) {
                delay(1000)
                secs++
                if (secs == 5) Log.w(TAG, "WidgetPickerScreen: loading >5s")
            }
        }

        try {
            val groups = WidgetRepository.getAppGroups(context)
            appGroups = groups
        } catch (e: Throwable) {
            Log.e(TAG, "WidgetPickerScreen: FAILED to load app groups", e)
        } finally {
            isLoading = false
            done = true
            watchdog.cancel()
        }
    }

    val filteredAppGroups = remember(appGroups, searchQuery) {
        if (searchQuery.isBlank()) appGroups
        else {
            appGroups.mapNotNull { group ->
                val appMatches = group.appName.contains(searchQuery, ignoreCase = true)
                val matchingWidgets = group.widgets.filter { it.label.contains(searchQuery, ignoreCase = true) }
                if (appMatches) group else if (matchingWidgets.isNotEmpty()) group.copy(widgets = matchingWidgets) else null
            }
        }
    }

    SettingsScaffold(
        topBar = {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                placeholder = { Text("Search apps...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    { IconButton(onClick = { searchQuery = "" }) { Icon(Icons.Default.Close, null) } }
                } else null,
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )
        }
    ) {

        if (isLoading) {
            item {
                Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        } else {
            section {
                filteredAppGroups.forEach { appGroup ->
                    val isSearching = searchQuery.isNotBlank()
                    val expanded = if (isSearching) true else (expandedPackages[appGroup.packageName] ?: false)

                    item(
                        key = appGroup.packageName,
                        modifier = Modifier.clickable {
                            if (!isSearching) expandedPackages[appGroup.packageName] = !expanded
                        }
                    ) {
                        AppHeaderContent(appGroup, expanded)
                    }

                    appGroup.widgets.forEach { widget ->
                        item(
                            key = widget.providerInfo.provider.flattenToString(),
                            modifier = Modifier.clickable { onWidgetSelected(widget.providerInfo) },
                            visible = expanded
                        ) {
                            WidgetChildContent(widget)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppHeaderContent(appGroup: AppGroup, isExpanded: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val context = LocalContext.current
        var appIcon by remember { mutableStateOf<Drawable?>(null) }
        val surfaceVarient = colorScheme.surfaceVariant
        var backgroundColor by remember {
            mutableStateOf(surfaceVarient)
        }

        LaunchedEffect(appGroup.packageName) {
            withContext(Dispatchers.IO) {
                try {
                    val appInfo =
                        context.packageManager.getApplicationInfo(appGroup.packageName, 0)
                    val icon = context.packageManager.getApplicationIcon(appInfo)
                    val bitmap = icon.toBitmap()

                    val color = extractOutlineColor(bitmap, surfaceVarient)

                    withContext(Dispatchers.Main) {
                        appIcon = icon
                        backgroundColor = color
                    }
                } catch (_: Exception) {}
            }
        }

        val shape = rememberAppIconShape(appGroup.packageName)

        if (appIcon != null) {
            Surface(
                shape = shape.toShape(),
                color = backgroundColor,
                tonalElevation = 2.dp,
                modifier = Modifier.size(48.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(6.dp)
                ) {
                    Image(
                        bitmap = appIcon!!.toBitmap().asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize().scale(1.15f)
                    )
                }
            }
        } else {
            Box(Modifier.size(48.dp))
        }

        Spacer(Modifier.width(16.dp))

        Column(Modifier.weight(1f)) {
            Text(
                appGroup.appName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1
            )
            Text(
                "${appGroup.widgets.size} widgets",
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        val rotation by animateFloatAsState(
            if (isExpanded) 180f else 0f,
            label = "chevron"
        )
        Icon(
            Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier.rotate(rotation)
        )
    }
}

@Composable
fun WidgetChildContent(model: WidgetUiModel) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(150.dp),
            contentAlignment = Alignment.Center
        ) {
            val context = LocalContext.current
            val density = LocalDensity.current
            var previewResult by remember(model.providerInfo) { mutableStateOf<Any?>(null) }


            LaunchedEffect(model.providerInfo) {
                val widthPx = with(density) { 300.dp.roundToPx() }
                val heightPx = with(density) { 150.dp.roundToPx() }
                previewResult = loadWidgetPreview(context, model.providerInfo, widthPx, heightPx)
            }

            when (val result = previewResult) {
                is View -> AndroidView(factory = { result }, modifier = Modifier.wrapContentSize())
                is Drawable -> Image(
                    bitmap = result.toBitmap().asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                else -> CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = model.label,
            style = MaterialTheme.typography.labelLarge,
            color = colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

suspend fun loadWidgetPreview(
    context: Context,
    info: AppWidgetProviderInfo,
    widthPx: Int,
    heightPx: Int
): Any? = withContext(Dispatchers.IO) {
    withTimeoutOrNull(1_500) {
        loadWidgetPreviewInternal(context, info, widthPx, heightPx)
    }
}

suspend fun loadWidgetPreviewInternal(
    context: Context,
    info: AppWidgetProviderInfo,
    widthPx: Int,
    heightPx: Int
): Any? = withContext(Dispatchers.IO) {

    val appWidgetManager = AppWidgetManager.getInstance(context)

    if (Build.VERSION.SDK_INT >= 35) {
        try {
            val remoteViews = appWidgetManager.getWidgetPreview(
                info.provider,
                info.profile,
                info.widgetCategory
            )

            if (remoteViews != null) {
                return@withContext withContext(Dispatchers.Main) {
                    remoteViews.apply(context, null)
                }
            }
        } catch (e: Exception) {}
    }

    try {
        val legacyPreview = info.loadPreviewImage(context, 0)
        if (legacyPreview != null) return@withContext legacyPreview
    } catch (e: Exception) {}

    @LayoutRes
    val layoutId: Int = when {
        info.previewLayout != 0 -> info.previewLayout
        info.initialLayout != 0 -> info.initialLayout
        else -> 0
    }

    if (layoutId != 0) {
        try {
            val view = withContext(Dispatchers.Main) {
                val packageContext = context.createPackageContext(info.provider.packageName, 0)
                val inflater = LayoutInflater.from(packageContext)
                inflater.inflate(layoutId, null)
            }
            return@withContext view
        } catch (e: Exception) {}
    }

    info.loadIcon(context, 0)
}

fun extractOutlineColor(bitmap: Bitmap, fallback: Color): Color {
    val w = bitmap.width
    val h = bitmap.height
    if (w == 0 || h == 0) return fallback

    val border = (minOf(w, h) * 0.12f).roundToInt()
    val colorCount = HashMap<Int, Int>()

    fun record(x: Int, y: Int) {
        val pixel = bitmap.getPixel(x, y)
        val alpha = (pixel shr 24) and 0xFF
        if (alpha < 64) return
        val r = ((pixel shr 16) and 0xFF) / 16 * 16
        val g = ((pixel shr 8) and 0xFF) / 16 * 16
        val b = (pixel and 0xFF) / 16 * 16
        val quantized = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        colorCount[quantized] = (colorCount[quantized] ?: 0) + 1
    }

    for (y in 0 until border) {
        for (x in 0 until w) record(x, y)
        for (x in 0 until w) record(x, h - 1 - y)
    }
    for (x in 0 until border) {
        for (y in border until h - border) record(x, y)
        for (y in border until h - border) record(w - 1 - x, y)
    }

    val best = colorCount.maxByOrNull { it.value }?.key
    return best?.let { Color(it) } ?: fallback
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberAppIconShape(packageName: String): RoundedPolygon {
    return remember(packageName) {
        AppIconShapes[abs(packageName.hashCode()) % AppIconShapes.size]
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
val AppIconShapes = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Pentagon,
    MaterialShapes.SoftBurst,
    MaterialShapes.Square,
    MaterialShapes.Circle,
)

fun Drawable.toBitmap(): Bitmap {
    if (this is BitmapDrawable) return bitmap
    val bitmap = createBitmap(intrinsicWidth.takeIf { it > 0 } ?: 1, intrinsicHeight.takeIf { it > 0 } ?: 1)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap
}

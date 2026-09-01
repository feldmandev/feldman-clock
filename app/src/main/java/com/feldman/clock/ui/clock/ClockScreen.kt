package com.feldman.clock.ui.clock

import com.feldman.clock.R
import com.feldman.clock.ui.theme.expressiveChromeColor
import com.feldman.clock.ui.components.ClockAppBarIconButton
import com.feldman.clock.ui.components.ClockPageTitle
import com.feldman.clock.ui.components.ExpressiveContentSurface

import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import com.feldman.clock.app.navigation.Dest
import com.feldman.clock.ui.alarm.HardSwipeRow
import com.feldman.clock.ui.clock.model.City
import com.feldman.clock.core.util.isScreenWide
import com.feldman.motion.INNER_CORNER_RADIUS
import com.feldman.motion.ITEM_SPACER
import com.feldman.motion.OUTER_CORNER_RADIUS
import java.text.DateFormatSymbols
import java.util.*

private val CONNECTED_INNER_CORNER_RADIUS = 1.dp

@OptIn(ExperimentalTextApi::class)
private val clockTimeFont = FontFamily(
    Font(
        resId = com.feldman.motion.R.font.feldman_font,
        variationSettings = FontVariation.Settings(
            FontVariation.weight(400),
            FontVariation.width(60f),
            FontVariation.Setting("opsz", 144f),
            FontVariation.Setting("ROND", 100f),
            FontVariation.Setting("GRAD", 100f)
        )
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockScreen(
    viewModel: ClockViewModel = viewModel(),
    onFabClick: ((() -> Unit)?) -> Unit,
    onNavigate: (Dest) -> Unit,
    onBack: () -> Unit,
    isTab: Boolean
) {
    val cities by viewModel.cities.collectAsState()
    val currentTime by viewModel.currentTime.collectAsState()
    val localWeather by viewModel.localWeather.collectAsState()
    val cityWeather by viewModel.cityWeather.collectAsState()
    var showCityPicker by remember { mutableStateOf(false) }
    val isWide = isScreenWide()
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel.refreshWeather() }

    LaunchedEffect(Unit) {
        onFabClick { showCityPicker = true }
        if (viewModel.hasLocationPermission()) {
            viewModel.refreshWeather()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0),
        topBar = {
            CenterAlignedTopAppBar(
                title = { ClockPageTitle("Clock") },
                navigationIcon = {
                    if (!isTab) {
                        ClockAppBarIconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    ClockAppBarIconButton(onClick = { onNavigate(Dest.Settings) }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = expressiveChromeColor(
                        if (isWide) NavigationBarDefaults.containerColor
                        else colorScheme.surfaceContainer
                    )
                )
            )
        }
    ) { innerPadding ->
        ExpressiveContentSurface(modifier = Modifier.padding(innerPadding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 12.dp, bottom = 16.dp)
            ) {
                LocalTimeCard(
                    currentTime = currentTime,
                    is24Hour = viewModel.is24HourFormat(),
                    dateFormat = viewModel.getDateFormatPattern(),
                    weather = localWeather,
                    hasLocationPermission = viewModel.hasLocationPermission()
                )

                Spacer(Modifier.height(24.dp))

                if (cities.isNotEmpty()) {
                    if (isWide) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(ITEM_SPACER),
                            verticalArrangement = Arrangement.spacedBy(ITEM_SPACER),
                            contentPadding = PaddingValues(bottom = 116.dp)
                        ) {
                            itemsIndexed(items = cities, key = { _, city -> city.id }) { index, city ->
                                val shape = connectedCardShape(index, cities.size, columns = 3)
                                HardSwipeRow(
                                    shape = shape,
                                    onSwiped = { viewModel.deleteCity(city) }
                                ) {
                                    CityCard(
                                        city = city,
                                        currentTime = currentTime,
                                        weather = cityWeather[city.id],
                                        shape = shape
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(ITEM_SPACER),
                            contentPadding = PaddingValues(bottom = 116.dp)
                        ) {
                            itemsIndexed(items = cities, key = { _, city -> city.id }) { index, city ->
                                val shape = connectedCardShape(index, cities.size, columns = 1)
                                HardSwipeRow(
                                    shape = shape,
                                    onSwiped = { viewModel.deleteCity(city) }
                                ) {
                                    CityCard(
                                        city = city,
                                        currentTime = currentTime,
                                        weather = cityWeather[city.id],
                                        shape = shape
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }

    if (showCityPicker) {
        CityPickerDialog(
            onDismiss = { showCityPicker = false },
            onCitySelected = { city ->
                viewModel.addCity(city)
                showCityPicker = false
            },
            existingCityIds = cities.map { it.id }
        )
    }
}
@Composable
fun LocalTimeCard(
    currentTime: Long,
    is24Hour: Boolean,
    dateFormat: String,
    weather: Weather?,
    hasLocationPermission: Boolean
) {
    val context = LocalContext.current
    val calendar = remember(currentTime) {
        Calendar.getInstance().apply {
            timeInMillis = currentTime
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AutoSizedClockTime(
            time = DateFormat.format(if (is24Hour) "HH:mm:ss" else "h:mm:ss", calendar).toString(),
            period = if (is24Hour) null else DateFormat.format("aa", calendar).toString()
        )

        Spacer(Modifier.height(4.dp))

        // Date
        Text(
            text = DateFormat.format(dateFormat, calendar).toString(),
            style = typography.bodyLarge,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(4.dp))

        Text(
            text = weather?.let { "${it.temperature.roundToInt()}° · ${it.description}" }
                ?: if (hasLocationPermission) "Loading weather…" else "Allow location for weather",
            style = typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AutoSizedClockTime(
    time: String,
    period: String?
) {
    val maxFontSize = 120.sp
    val textMeasurer = rememberTextMeasurer(cacheSize = 16)

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val locale = Locale.getDefault()
        val naturalWidth = remember(textMeasurer, period != null, locale) {
            val measurementStyle = TextStyle(
                fontSize = maxFontSize,
                fontFamily = clockTimeFont
            )
            val widestDigit = ('0'..'9').maxBy { digit ->
                textMeasurer.measure(
                    text = digit.toString(),
                    style = measurementStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width
            }
            val pair = widestDigit.toString().repeat(2)
            val probeTime = "$pair:$pair:$pair"
            val probePeriods: List<String?> = if (period == null) {
                listOf(null)
            } else {
                DateFormatSymbols.getInstance(locale).amPmStrings.toList()
            }

            probePeriods.maxOf { probePeriod ->
                textMeasurer.measure(
                    text = clockTimeText(probeTime, probePeriod, maxFontSize),
                    style = measurementStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width
            }
        }
        val scale = if (naturalWidth > constraints.maxWidth) {
            constraints.maxWidth.toFloat() / naturalWidth * 0.99f
        } else {
            1f
        }
        val fontSize = (maxFontSize.value * scale).sp

        Text(
            text = clockTimeText(time, period, fontSize),
            fontSize = fontSize,
            fontFamily = clockTimeFont,
            color = colorScheme.onBackground,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip
        )
    }
}

/** The AM/PM marker renders at this fraction of the time's font size. */
internal const val CLOCK_PERIOD_FONT_SCALE = 0.42f

/**
 * Builds "10:30 AM" with the period rendered smaller than the time.
 *
 * Shared with the alarm list so both surfaces size their AM/PM marker identically.
 */
internal fun clockTimeText(time: String, period: String?, fontSize: TextUnit): AnnotatedString =
    buildAnnotatedString {
        append(time)
        if (period != null) {
            withStyle(
                SpanStyle(
                    fontSize = (fontSize.value * CLOCK_PERIOD_FONT_SCALE).sp
                )
            ) {
                append(" ")
                append(period)
            }
        }
    }

@Composable
fun CityCard(
    city: City,
    currentTime: Long,
    weather: Weather?,
    shape: Shape = shapes.large
) {
    val calendar = remember(city.timeZone, currentTime) {
        Calendar.getInstance(TimeZone.getTimeZone(city.timeZone)).apply {
            timeInMillis = currentTime
        }
    }
    val timeStr = DateFormat.format("HH:mm", calendar).toString()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // LEFT: name + country (same line), time diff below
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = city.name,
                        style = typography.titleMedium,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = city.country,
                        style = typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = city.getTimeDifferenceStr(),
                    style = typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(2.dp))
                Text(
                    text = weather?.let { "${it.temperature.roundToInt()}° · ${it.description}" }
                        ?: "Loading weather…",
                    style = typography.bodySmall,
                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // RIGHT: time
            Text(
                text = timeStr,
                fontSize = 56.sp,
                fontFamily = clockTimeFont,
                color = colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun connectedCardShape(index: Int, count: Int, columns: Int): Shape {
    if (count == 1) return RoundedCornerShape(OUTER_CORNER_RADIUS)

    val row = index / columns
    val column = index % columns
    val lastRow = (count - 1) / columns
    val lastColumnInRow = minOf(columns - 1, count - row * columns - 1)

    return RoundedCornerShape(
        topStart = if (row == 0 && column == 0) OUTER_CORNER_RADIUS else INNER_CORNER_RADIUS,
        topEnd = if (row == 0 && column == lastColumnInRow) OUTER_CORNER_RADIUS else INNER_CORNER_RADIUS,
        bottomStart = if (row == lastRow && column == 0) OUTER_CORNER_RADIUS else INNER_CORNER_RADIUS,
        bottomEnd = if (index == count - 1) OUTER_CORNER_RADIUS else INNER_CORNER_RADIUS
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HardSwipeRowClock(
    modifier: Modifier = Modifier,
    swipeToTriggerFraction: Float = 0.7f,
    onSwiped: () -> Unit,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * swipeToTriggerFraction }
    )

    var contentHeightPx by remember { mutableIntStateOf(0) }
    val contentHeightDp = with(LocalDensity.current) {
        if (contentHeightPx == 0) 0.dp else contentHeightPx.toDp()
    }

    LaunchedEffect(state.currentValue) {
        if (state.currentValue != SwipeToDismissBoxValue.Settled) {
            onSwiped()
            state.reset()
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val height = if (contentHeightPx == 0) 84.dp else contentHeightDp
            val toward = state.targetValue
            val align = when (toward) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.CenterEnd
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                shape = shapes.large,
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFFFF7165)
                ),
                elevation = CardDefaults.cardElevation(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = align
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color(0xff490002),
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        },
        content = {
            Box(
                modifier = Modifier.onSizeChanged { size ->
                    if (contentHeightPx != size.height) contentHeightPx = size.height
                }
            ) {
                content()
            }
        }
    )
}

@Composable
fun CityPickerDialog(
    onDismiss: () -> Unit,
    onCitySelected: (City) -> Unit,
    existingCityIds: List<String>,
    viewModel: ClockViewModel = viewModel()
) {
    var searchQuery by remember { mutableStateOf("") }
    var cityResults by remember { mutableStateOf<List<City>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    
    // Load local city suggestions initially
    val localCities = remember {
        viewModel.getLocalCitySuggestions()
            .filterNot { it.id in existingCityIds }
    }
    
    // Search cities as user types (with JSON database + fallback)
    LaunchedEffect(searchQuery) {
        if (searchQuery.length >= 2) {
            isSearching = true
            cityResults = viewModel.searchWorldwideCities(searchQuery)
                .filterNot { it.id in existingCityIds }
            isSearching = false
        } else {
            cityResults = emptyList()
        }
    }
    
    // Decide what to show
    val citiesToShow = if (searchQuery.isBlank()) localCities else cityResults

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add City",
                style = typography.titleLarge
            )
        },
        text = {
            Column {
                //Search bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    placeholder = { Text(if (searchQuery.isBlank()) "Popular cities in your region..." else "Search cities...") },
                    singleLine = true,
                    shape = shapes.medium,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colorScheme.primary,
                        unfocusedBorderColor = colorScheme.outline
                    )
                )
                
                // Cities list
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.heightIn(max = 400.dp)
                ) {
                    if (isSearching) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    } else {
                        items(citiesToShow) { city ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCitySelected(city) },
                                shape = shapes.medium,
                                color = colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        // City name - large and prominent
                                        Text(
                                            text = city.name,
                                            style = typography.titleLarge,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                        
                                        Spacer(Modifier.height(4.dp))
                                        
                                        // Country and time difference - smaller text below
                                        Text(
                                            text = "${city.country} • ${city.getTimeDifferenceStr()}",
                                            style = typography.bodyMedium,
                                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    if (!isSearching && citiesToShow.isEmpty() && searchQuery.length >= 2) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "No cities found",
                                    style = typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

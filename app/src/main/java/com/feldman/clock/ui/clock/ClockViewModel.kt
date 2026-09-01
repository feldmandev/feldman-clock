package com.feldman.clock.ui.clock

import com.feldman.clock.R

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.text.format.DateFormat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.feldman.clock.ui.clock.model.City
import com.feldman.clock.core.network.CityDatabaseService
import com.feldman.clock.core.network.WeatherService
import com.feldman.clock.core.network.WeatherGeocodingService
import com.feldman.clock.core.data.DataModel
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.delay
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.TimeZone

class ClockViewModel(application: Application) : AndroidViewModel(application) {
    private val dataModel = DataModel.dataModel

    // Using StateFlow to trigger recomposition on change would be ideal.
    // However, DataModel access is synchronous.
    // For now, we can expose current values, but to be reactive, we might need a listener or shared flow from Settings.
    // But since `SettingsViewModel` persists to SharedPrefs, and we want `ClockScreen` to update when coming bck.
    // SharedPrefs change listener is one way.
    // Or simply, read it in the Composable via LaunchedEffect or similar if not observable here.
    // BUT, let's keep it simple: `ClockScreen` redraws regularly for time.
    // We can just expose a function or property that delegates to DataModel.
    
    fun is24HourFormat(): Boolean = dataModel.is24HourFormat()
    fun getDateFormatPattern(): String = dataModel.dateFormat
    private val prefs = application.getSharedPreferences("world_clock_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val weatherService = WeatherService.create()
    private val weatherGeocodingService = WeatherGeocodingService.create()
    
    private val _cities = MutableStateFlow<List<City>>(emptyList())
    val cities: StateFlow<List<City>> = _cities.asStateFlow()

    private val _currentTime = MutableStateFlow(System.currentTimeMillis())
    val currentTime: StateFlow<Long> = _currentTime.asStateFlow()

    private val _localWeather = MutableStateFlow<Weather?>(null)
    val localWeather: StateFlow<Weather?> = _localWeather.asStateFlow()

    private val _cityWeather = MutableStateFlow<Map<String, Weather>>(emptyMap())
    val cityWeather: StateFlow<Map<String, Weather>> = _cityWeather.asStateFlow()

    init {
        // Load saved cities from SharedPreferences
        loadCities()
        refreshWeather()

        // Update time every second
        viewModelScope.launch {
            while (isActive) {
                _currentTime.value = System.currentTimeMillis()
                delay(1000)
            }
        }
    }
    
    private fun loadCities() {
        val citiesJson = prefs.getString("cities", null)
        if (citiesJson != null) {
            val type = object : TypeToken<List<City>>() {}.type
            _cities.value = gson.fromJson(citiesJson, type) ?: emptyList()
        }
    }
    
    private fun saveCities() {
        val citiesJson = gson.toJson(_cities.value)
        prefs.edit().putString("cities", citiesJson).apply()
    }

    fun getCurrentTimeForCity(city: City): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone(city.timeZone))
        calendar.timeInMillis = _currentTime.value
        // Always return 24-hour format, let UI handle format preference
        return DateFormat.format("HH:mm", calendar).toString()
    }

    fun deleteCity(city: City) {
        _cities.value = _cities.value.filter { it.id != city.id }
        saveCities()
        _cityWeather.value = _cityWeather.value - city.id
    }

    fun addCity(city: City) {
        _cities.value = _cities.value + city
        saveCities()
        refreshWeather()
    }

    // Only coarse location is requested: the forecast is resolved at city granularity, so fine
    // location would be collecting more than the feature needs.
    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(getApplication(), android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun refreshWeather() {
        viewModelScope.launch {
            coroutineScope {
                val localWeatherRequest = if (hasLocationPermission()) {
                    async {
                        lastKnownLocation()?.let { location ->
                            weatherAt(location.latitude, location.longitude)
                        }
                    }
                } else {
                    null
                }
                val cityWeatherRequests = _cities.value.map { city ->
                    async { city.id to weatherForCity(city) }
                }

                localWeatherRequest?.await()?.let { _localWeather.value = it }
                _cityWeather.value = cityWeatherRequests.awaitAll()
                    .mapNotNull { (cityId, weather) -> weather?.let { cityId to it } }
                    .toMap()
            }
        }
    }

    private fun lastKnownLocation(): Location? {
        val locationManager = getApplication<Application>()
            .getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.allProviders
            .mapNotNull { locationManager.getLastKnownLocation(it) }
            .maxByOrNull { it.time }
    }

    private suspend fun weatherForCity(city: City): Weather? = try {
        val queries = listOf(city.name, city.state, city.name.substringBefore('-'))
            .filter { it.isNotBlank() }
            .distinct()
        val places = queries.firstNotNullOfOrNull { query ->
            weatherGeocodingService.search(query, count = 10).results?.takeIf { it.isNotEmpty() }
        } ?: return null
        val place = places.firstOrNull { it.timezone == city.timeZone } ?: places.first()
        weatherAt(place.latitude, place.longitude)
    } catch (_: Exception) {
        null
    }

    private suspend fun weatherAt(latitude: Double, longitude: Double): Weather? = try {
        weatherService.forecast(latitude, longitude).current?.let {
            Weather(it.temperature_2m, it.weather_code)
        }
    } catch (_: Exception) {
        null
    }
    
    /**
     * Get popular cities in the user's region/timezone
     */
    fun getLocalCitySuggestions(): List<City> {
        val localTimeZone = TimeZone.getDefault().id
        val localRegion = localTimeZone.split("/").firstOrNull() ?: "America"
        
        // Return popular cities from the same region
        return getAllWorldwideCities()
            .filter { it.timeZone.startsWith(localRegion) }
            .sortedByDescending { 
                // Prioritize capital cities and major cities
                when {
                    it.name in listOf("New York", "Los Angeles", "London", "Paris", "Tokyo", "Moscow", "Beijing", "Dubai", "Singapore", "Hong Kong") -> 3
                    it.name.length > 3 -> 2
                    else -> 1
                }
            }
            .take(10)
    }

    /**
     * Search for cities worldwide using comprehensive JSON database
     * Falls back to Android timezone database if API fails
     */
    suspend fun searchWorldwideCities(query: String): List<City> {
        if (query.length < 2) return emptyList()
        
        // Try comprehensive JSON database first (kevinroberts/city-timezones)
        val jsonCities = try {
            searchCitiesFromJson(query)
        } catch (e: Exception) {
            // JSON load failed, use Android timezone fallback
            emptyList()
        }
        
        // If JSON database returned results, use them
        if (jsonCities.isNotEmpty()) {
            return jsonCities
        }
        
        // Fallback: Search in Android timezone database
        return getAllWorldwideCities().filter { city ->
            city.name.contains(query, ignoreCase = true) ||
            city.country.contains(query, ignoreCase = true) ||
            city.timeZone.contains(query, ignoreCase = true)
        }
    }

    /**
     * Search cities from comprehensive JSON database
     * Uses kevinroberts/city-timezones database (includes timezone data!)
     */
    private suspend fun searchCitiesFromJson(query: String): List<City> {
        val service = CityDatabaseService.create()
        val cities = service.getCities()
        
        return cities
            .filter { jsonCity ->
                jsonCity.city.contains(query, ignoreCase = true) ||
                jsonCity.country.contains(query, ignoreCase = true) ||
                (jsonCity.province?.contains(query, ignoreCase = true) == true)
            }
            .take(50) // Limit results
            .map { jsonCity ->
                City(
                    id = "${jsonCity.city}_${jsonCity.country}".lowercase().replace(" ", "_"),
                    name = jsonCity.city,
                    timeZone = jsonCity.timezone,
                    country = jsonCity.country,
                    state = jsonCity.province ?: ""
                )
            }
    }

    /**
     * Get all available cities from Android's timezone database
     * Returns 600+ cities from around the world  
     */
    fun getAllWorldwideCities(): List<City> {
        return TimeZone.getAvailableIDs()
            .filter { id ->
                // Filter to only include timezone IDs with a region/city format
                id.contains("/") && !id.startsWith("Etc/") && !id.startsWith("SystemV/")
            }
            .mapNotNull { timezoneId ->
                val parts = timezoneId.split("/")
                if (parts.size < 2) return@mapNotNull null
                
                // Extract city name from timezone ID
                val cityName = parts.last()
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { c -> 
                            if (c.isLowerCase()) c.titlecase() else c.toString()
                        }
                    }
                
                // Skip generic or abbreviated names
                if (cityName.length <= 3 || cityName.all { it.isUpperCase() }) {
                    return@mapNotNull null
                }
                
                // Extract country/region from first part of timezone ID
                val country = parts.first()
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { word ->
                        word.replaceFirstChar { c ->
                            if (c.isLowerCase()) c.titlecase() else c.toString()
                        }
                    }
                
                City(
                    id = timezoneId.replace("/", "_").replace("-", "_").lowercase(),
                    name = cityName,
                    timeZone = timezoneId,
                    country = country,
                    state = "" // Not available from timezone ID
                )
            }
            .distinctBy { it.name } // Remove duplicate city names
            .sortedBy { it.name }
    }
}

data class Weather(val temperature: Double, val weatherCode: Int) {
    val description: String
        get() = when (weatherCode) {
            0 -> "Clear"
            1, 2 -> "Partly cloudy"
            3 -> "Overcast"
            45, 48 -> "Fog"
            51, 53, 55, 56, 57 -> "Drizzle"
            61, 63, 65, 66, 67, 80, 81, 82 -> "Rain"
            71, 73, 75, 77, 85, 86 -> "Snow"
            95, 96, 99 -> "Thunderstorms"
            else -> "Weather"
        }
}

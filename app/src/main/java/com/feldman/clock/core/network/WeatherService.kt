package com.feldman.clock.core.network

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherService {
    @GET("v1/forecast")
    suspend fun forecast(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("current") current: String = "temperature_2m,weather_code"
    ): WeatherResponse

    companion object {
        fun create(): WeatherService = Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherService::class.java)
    }
}

interface WeatherGeocodingService {
    @GET("v1/search")
    suspend fun search(
        @Query("name") name: String,
        @Query("count") count: Int = 1
    ): WeatherSearchResponse

    companion object {
        fun create(): WeatherGeocodingService = Retrofit.Builder()
            .baseUrl("https://geocoding-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherGeocodingService::class.java)
    }
}

data class WeatherResponse(val current: WeatherCurrent?)
data class WeatherCurrent(val temperature_2m: Double, val weather_code: Int)
data class WeatherSearchResponse(val results: List<WeatherSearchResult>?)
data class WeatherSearchResult(
    val latitude: Double,
    val longitude: Double,
    val timezone: String?
)

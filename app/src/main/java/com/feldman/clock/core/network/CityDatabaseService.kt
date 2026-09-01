package com.feldman.clock.core.network

import com.feldman.clock.R

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

/**
 * City Database Service - loads comprehensive city list from JSON
 * NO API KEY REQUIRED - uses static data from GitHub
 * 
 * Database: kevinroberts/city-timezones - comprehensive worldwide city data with timezones
 * Source: https://github.com/kevinroberts/city-timezones
 */
interface CityDatabaseService {
    @GET("master/data/cityMap.json")
    suspend fun getCities(): List<JsonCity>

    companion object {
        // Using city-timezones database from GitHub (includes timezone data!)
        private const val BASE_URL = "https://raw.githubusercontent.com/kevinroberts/city-timezones/"
        
        fun create(): CityDatabaseService {
            val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
            
            return retrofit.create(CityDatabaseService::class.java)
        }
    }
}

data class JsonCity(
    val city: String,
    val country: String,
    val province: String?,
    val timezone: String
)

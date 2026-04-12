package com.example.cliweatherapp

import java.util.*

enum class AppLanguage(val label: String, val locale: Locale) {
    EN("English", Locale.ENGLISH),
    FR("Français", Locale.FRENCH),
    ES("Español", Locale("es")),
    DE("Deutsch", Locale.GERMAN),
    JA("日本語", Locale.JAPANESE)
}

data class Hourly(val time: List<String>, val temperature_2m: List<Double>, val weathercode: List<Int>, val is_day: List<Int>?)
data class Daily(val time: List<String>, val weathercode: List<Int>, val temperature_2m_max: List<Double>, val temperature_2m_min: List<Double>)
data class WeatherResponse(val current_weather: CurrentWeather, val hourly: Hourly?, val daily: Daily?, val timezone: String)
data class CurrentWeather(
    val temperature: Double,
    val weathercode: Int,
    val is_day: Int,
    val time: String
)

interface WeatherApi {
    @retrofit2.http.GET("v1/forecast")
    suspend fun getWeather(
        @retrofit2.http.Query("latitude") lat: Double,
        @retrofit2.http.Query("longitude") lon: Double,
        @retrofit2.http.Query("current_weather") current: Boolean = true,
        @retrofit2.http.Query("hourly") hourly: String = "temperature_2m,weathercode,is_day",
        @retrofit2.http.Query("daily") daily: String = "weathercode,temperature_2m_max,temperature_2m_min",
        @retrofit2.http.Query("timezone") timezone: String = "auto",
        @retrofit2.http.Query("forecast_days") days: Int = 16
    ): WeatherResponse
}

object RetrofitClient {
    private const val BASE_URL = "https://api.open-meteo.com/"
    val api: WeatherApi by lazy {
        retrofit2.Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(WeatherApi::class.java)
    }
}

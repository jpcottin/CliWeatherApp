package com.example.cliweatherapp

import com.google.gson.annotations.SerializedName
import java.util.*

enum class AppLanguage(val label: String, val locale: Locale) {
    EN("English", Locale.ENGLISH),
    FR("Français", Locale.FRENCH),
    ES("Español", Locale("es")),
    DE("Deutsch", Locale.GERMAN),
    JA("日本語", Locale.JAPANESE)
}

data class Hourly(
    val time: List<String>,
    @SerializedName("temperature_2m") val temperature_2m: List<Double>,
    @SerializedName("weather_code") val weather_code: List<Int>,
    @SerializedName("is_day") val is_day: List<Int>?,
    @SerializedName("uv_index") val uv_index: List<Double>?
)

data class Daily(
    val time: List<String>,
    @SerializedName("weather_code") val weather_code: List<Int>,
    @SerializedName("temperature_2m_max") val temperature_2m_max: List<Double>,
    @SerializedName("temperature_2m_min") val temperature_2m_min: List<Double>,
    @SerializedName("sunrise") val sunrise: List<String>,
    @SerializedName("sunset") val sunset: List<String>,
    @SerializedName("uv_index_max") val uv_index_max: List<Double>?
)

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
        @retrofit2.http.Query("hourly") hourly: String = "temperature_2m,weather_code,is_day,uv_index",
        @retrofit2.http.Query("daily") daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max",
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

data class AirQualityHourly(val time: List<String>, val european_aqi: List<Int?>)
data class AirQualityResponse(val hourly: AirQualityHourly, val timezone: String)

interface AirQualityApi {
    @retrofit2.http.GET("v1/air-quality")
    suspend fun getAirQuality(
        @retrofit2.http.Query("latitude") lat: Double,
        @retrofit2.http.Query("longitude") lon: Double,
        @retrofit2.http.Query("hourly") hourly: String = "european_aqi",
        @retrofit2.http.Query("timezone") timezone: String = "auto",
        @retrofit2.http.Query("forecast_days") days: Int = 1
    ): AirQualityResponse
}

object AirQualityRetrofitClient {
    private const val BASE_URL = "https://air-quality-api.open-meteo.com/"
    val api: AirQualityApi by lazy {
        retrofit2.Retrofit.Builder().baseUrl(BASE_URL).addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create()).build().create(AirQualityApi::class.java)
    }
}

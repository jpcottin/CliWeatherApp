package com.jpcottin.weatherglance

import android.annotation.SuppressLint
import com.google.gson.annotations.SerializedName
import com.google.android.gms.tasks.CancellationTokenSource
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

// ── Enums ────────────────────────────────────────────────────────────────────

enum class AppLanguage(val label: String, val locale: Locale) {
    EN("English", Locale.ENGLISH),
    FR("Français", Locale.FRENCH),
    ES("Español", Locale("es")),
    DE("Deutsch", Locale.GERMAN),
    JA("日本語", Locale.JAPANESE)
}

// ── Weather API models ───────────────────────────────────────────────────────

data class Hourly(
    val time: List<String>,
    @SerializedName("temperature_2m")  val temperature: List<Double>,
    @SerializedName("weather_code")    val weatherCode: List<Int>,
    @SerializedName("is_day")          val isDay: List<Int>?,
    @SerializedName("uv_index")        val uvIndex: List<Double>?
)

data class Daily(
    val time: List<String>,
    @SerializedName("weather_code")        val weatherCode: List<Int>,
    @SerializedName("temperature_2m_max")  val maxTemperature: List<Double>,
    @SerializedName("temperature_2m_min")  val minTemperature: List<Double>,
    @SerializedName("sunrise")             val sunrise: List<String>,
    @SerializedName("sunset")             val sunset: List<String>,
    @SerializedName("uv_index_max")        val uvIndexMax: List<Double>?
)

data class WeatherResponse(
    val current_weather: CurrentWeather,
    val hourly: Hourly?,
    val daily: Daily?,
    val timezone: String
)

data class CurrentWeather(
    val temperature: Double,
    @SerializedName("weathercode") val weatherCode: Int,
    @SerializedName("is_day")      val isDay: Int,
    val time: String
)

// ── Air quality models ───────────────────────────────────────────────────────

data class AirQualityHourly(
    val time: List<String>,
    @SerializedName("european_aqi") val europeanAqi: List<Int?>
)

data class AirQualityResponse(val hourly: AirQualityHourly, val timezone: String)

// ── Retrofit APIs ────────────────────────────────────────────────────────────

interface WeatherApi {
    @retrofit2.http.GET("v1/forecast")
    suspend fun getWeather(
        @retrofit2.http.Query("latitude")      lat: Double,
        @retrofit2.http.Query("longitude")     lon: Double,
        @retrofit2.http.Query("current_weather") current: Boolean = true,
        @retrofit2.http.Query("hourly")        hourly: String = "temperature_2m,weather_code,is_day,uv_index",
        @retrofit2.http.Query("daily")         daily: String = "weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,uv_index_max",
        @retrofit2.http.Query("timezone")      timezone: String = "auto",
        @retrofit2.http.Query("forecast_days") days: Int = 16
    ): WeatherResponse
}

interface AirQualityApi {
    @retrofit2.http.GET("v1/air-quality")
    suspend fun getAirQuality(
        @retrofit2.http.Query("latitude")      lat: Double,
        @retrofit2.http.Query("longitude")     lon: Double,
        @retrofit2.http.Query("hourly")        hourly: String = "european_aqi",
        @retrofit2.http.Query("timezone")      timezone: String = "auto",
        @retrofit2.http.Query("forecast_days") days: Int = 1
    ): AirQualityResponse
}

object RetrofitClient {
    val api: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }
}

object AirQualityRetrofitClient {
    val api: AirQualityApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://air-quality-api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AirQualityApi::class.java)
    }
}

// ── Repository ───────────────────────────────────────────────────────────────

interface WeatherRepository {
    suspend fun fetch(
        context: android.content.Context,
        useGps: Boolean,
        lat: Double?,
        lon: Double?,
        lang: AppLanguage,
        is24Hour: Boolean,
        hourlyRange: Int,
        dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit,
        /** Nothing could be fetched: the message to show; location and weather stay as they were. */
        onError: (String) -> Unit = {}
    )
}

class RetrofitWeatherRepository : WeatherRepository {
    override suspend fun fetch(
        context: android.content.Context,
        useGps: Boolean,
        lat: Double?,
        lon: Double?,
        lang: AppLanguage,
        is24Hour: Boolean,
        hourlyRange: Int,
        dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit,
        onError: (String) -> Unit
    ) = fetchLocationAndWeather(context, useGps, lat, lon, lang, is24Hour, hourlyRange, dailyRange, onLoc, onWeather, onError)

    @SuppressLint("MissingPermission")
    private suspend fun fetchLocationAndWeather(
        context: android.content.Context,
        useGps: Boolean,
        lat: Double?,
        lon: Double?,
        lang: AppLanguage,
        is24Hour: Boolean,
        hourlyRange: Int,
        dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            var finalLat = lat ?: 0.0
            var finalLon = lon ?: 0.0

            if (useGps) {
                val fused = com.google.android.gms.location.LocationServices
                    .getFusedLocationProviderClient(context)
                var location: android.location.Location? = null

                try {
                    location = withTimeoutOrNull(5000L) {
                        val cts = CancellationTokenSource()
                        fused.getCurrentLocation(
                            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                            cts.token
                        ).await()
                    }
                } catch (_: Exception) {}

                if (location == null) {
                    try {
                        location = withTimeoutOrNull(2000L) { fused.lastLocation.await() }
                    } catch (_: Exception) {}
                }

                if (location == null) {
                    try {
                        val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE)
                            as android.location.LocationManager
                        location = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                    } catch (_: Exception) {}
                }

                if (location != null) {
                    finalLat = location.latitude
                    finalLon = location.longitude
                } else {
                    // No fix: say so, and leave the last location and weather alone rather
                    // than moving the marker to (0, 0) and showing 0.0° as if it were data.
                    onError(getLocalizedContext(context, lang).getString(R.string.gps_error))
                    return
                }
            }

            val address = withContext(Dispatchers.IO) {
                try {
                    val addrs = withTimeoutOrNull(3000L) {
                        android.location.Geocoder(context, lang.locale)
                            .getFromLocation(finalLat, finalLon, 1)
                    }
                    if (!addrs.isNullOrEmpty()) {
                        val a = addrs!![0]
                        val city = a.locality ?: a.subAdminArea ?: "??"
                        val country = a.countryName ?: "??"
                        Pair(
                            "$city, $country\n(${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)})",
                            "$city, $country"
                        )
                    } else {
                        Pair(
                            "${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}",
                            getLocalizedContext(context, lang).getString(R.string.unknown)
                        )
                    }
                } catch (_: Exception) {
                    Pair(
                        "${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}",
                        getLocalizedContext(context, lang).getString(R.string.unknown)
                    )
                }
            }
            onLoc(address.first, address.second, finalLat, finalLon)

            val neededDays = forecastDaysNeeded(hourlyRange, dailyRange)
            val res = withTimeoutOrNull(5000L) {
                RetrofitClient.api.getWeather(finalLat, finalLon, days = neededDays)
            } ?: run {
                onError("Error: timeout")
                return
            }

            val forecasts = buildForecasts(res, hourlyRange, dailyRange)

            onWeather(
                res.current_weather.weatherCode,
                res.current_weather.temperature,
                res.timezone,
                res.current_weather.isDay,
                forecasts.hourly,
                forecasts.daily,
                forecasts.currentUvIndex
            )
        } catch (e: Exception) {
            onError("Error: ${e.message}")
        }
    }
}

class MockWeatherRepository : WeatherRepository {
    override suspend fun fetch(
        context: android.content.Context,
        useGps: Boolean,
        lat: Double?,
        lon: Double?,
        lang: AppLanguage,
        is24Hour: Boolean,
        hourlyRange: Int,
        dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit,
        onError: (String) -> Unit
    ) {
        onLoc("Mock City, Test\n(45.00, 90.00)", "Mock City", 45.0, 90.0)
        val h = List(hourlyRange) {
            HourlyForecastData(
                "2026-04-12T${String.format("%02d", it % 24)}:00",
                listOf(0, 1, 2, 61, 71, 95)[it % 6],
                1, 20.0 + it,
                uvIndex = (it % 12).toDouble()
            )
        }
        val d = List(dailyRange) {
            DailyForecastData(
                "2026-04-${12 + it}",
                listOf(0, 1, 2, 61, 71, 95)[it % 6],
                15.0, 25.0,
                "2026-04-12T06:00", "2026-04-12T18:00",
                uvIndexMax = 4.0 + it
            )
        }
        onWeather(0, 22.5, "UTC", 1, h, d, 3.0)
    }
}


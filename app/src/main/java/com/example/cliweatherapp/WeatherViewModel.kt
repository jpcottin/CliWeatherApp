@file:OptIn(ExperimentalProjectedApi::class)

package com.example.cliweatherapp

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime

data class WeatherUiState(
    val isRefreshing: Boolean = false,
    val temperature: Double? = null,
    val weatherCode: Int = -1,
    val isDay: Int = 1,
    val timezoneId: String? = null,
    val hourlyForecasts: List<HourlyForecastData> = emptyList(),
    val dailyForecasts: List<DailyForecastData> = emptyList(),
    val currentUvIndex: Double? = null,
    val locationInfo: String = "",
    val rawCity: String = "",
    val currentLat: Double = 37.422,
    val currentLon: Double = -122.084,
    val mapErrorMessage: String? = null,
    val currentAirQuality: Int? = null,
    val aqiHourlyMap: Map<String, Int> = emptyMap(),
    val permissionGranted: Boolean = false,
    val isGlassesConnected: Boolean = false
)

data class WeatherPrefs(
    val isCelsius: Boolean = true,
    val appLanguage: AppLanguage = AppLanguage.EN,
    val useGps: Boolean = false,
    val is24Hour: Boolean = true,
    val hourlyRange: Int = 8,
    val dailyRange: Int = 6,
    val showSunriseSunset: Boolean = true,
    val showUvIndex: Boolean = false,
    val showAirQuality: Boolean = false
)

class WeatherViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        var repository: WeatherRepository = RetrofitWeatherRepository()
    }

    private val prefManager = PreferenceManager(application)

    var uiState by mutableStateOf(
        WeatherUiState(
            currentLat = prefManager.getLatitude(),
            currentLon = prefManager.getLongitude(),
            permissionGranted = hasLocationPermission(application)
        )
    )
        private set

    var prefs by mutableStateOf(
        WeatherPrefs(
            isCelsius = prefManager.getIsCelsius(),
            appLanguage = prefManager.getLanguage(),
            useGps = prefManager.getUseGps(),
            is24Hour = prefManager.getIs24Hour(application),
            hourlyRange = prefManager.getHourlyRange(),
            dailyRange = prefManager.getDailyRange(),
            showSunriseSunset = prefManager.getShowSunriseSunset(),
            showUvIndex = prefManager.getShowUvIndex(),
            showAirQuality = prefManager.getShowAirQuality()
        )
    )
        private set

    init {
        if (Build.VERSION.SDK_INT >= 36) {
            viewModelScope.launch {
                ProjectedContext.isProjectedDeviceConnected(
                    application,
                    viewModelScope.coroutineContext + Dispatchers.Main
                ).collect { connected ->
                    uiState = uiState.copy(isGlassesConnected = connected)
                }
            }
        }
        fetchWeather()
    }

    fun fetchWeather(
        useGps: Boolean = prefs.useGps,
        lat: Double = uiState.currentLat,
        lon: Double = uiState.currentLon
    ) {
        uiState = uiState.copy(isRefreshing = true)
        viewModelScope.launch {
            repository.fetch(
                context = getApplication(),
                useGps = useGps,
                lat = lat,
                lon = lon,
                lang = prefs.appLanguage,
                is24Hour = prefs.is24Hour,
                hourlyRange = prefs.hourlyRange,
                dailyRange = prefs.dailyRange,
                onLoc = { fullInfo, city, newLat, newLon ->
                    uiState = uiState.copy(
                        locationInfo = fullInfo,
                        rawCity = city,
                        currentLat = newLat,
                        currentLon = newLon
                    )
                    prefManager.saveLocation(newLat, newLon)
                    if (prefs.showAirQuality) fetchAirQuality(newLat, newLon)
                },
                onWeather = { code, temp, tz, day, hourly, daily, uvIdx ->
                    uiState = uiState.copy(
                        weatherCode = code,
                        temperature = temp,
                        timezoneId = tz,
                        isDay = day,
                        hourlyForecasts = hourly,
                        dailyForecasts = daily,
                        currentUvIndex = uvIdx,
                        isRefreshing = false
                    )
                }
            )
        }
    }

    fun onMapClick(lat: Double, lon: Double) {
        if (prefs.useGps) {
            val ctx = getLocalizedContext(getApplication(), prefs.appLanguage)
            uiState = uiState.copy(mapErrorMessage = ctx.getString(R.string.gps_error))
            viewModelScope.launch {
                delay(2000)
                uiState = uiState.copy(mapErrorMessage = null)
            }
        } else {
            uiState = uiState.copy(temperature = null)
            fetchWeather(useGps = false, lat = lat, lon = lon)
        }
    }

    fun updatePermission(granted: Boolean) {
        uiState = uiState.copy(permissionGranted = granted)
    }

    fun updateIsCelsius(value: Boolean) {
        prefs = prefs.copy(isCelsius = value)
        viewModelScope.launch { prefManager.saveIsCelsius(value) }
    }

    fun updateLanguage(value: AppLanguage) {
        prefs = prefs.copy(appLanguage = value)
        viewModelScope.launch { prefManager.saveLanguage(value) }
        fetchWeather()
    }

    fun updateUseGps(value: Boolean) {
        prefs = prefs.copy(useGps = value)
        viewModelScope.launch { prefManager.saveUseGps(value) }
    }

    fun updateIs24Hour(value: Boolean) {
        prefs = prefs.copy(is24Hour = value)
        viewModelScope.launch { prefManager.saveIs24Hour(value) }
    }

    fun updateHourlyRange(value: Int) {
        prefs = prefs.copy(hourlyRange = value)
        viewModelScope.launch { prefManager.saveHourlyRange(value) }
        fetchWeather()
    }

    fun updateDailyRange(value: Int) {
        prefs = prefs.copy(dailyRange = value)
        viewModelScope.launch { prefManager.saveDailyRange(value) }
        fetchWeather()
    }

    fun updateShowSunriseSunset(value: Boolean) {
        prefs = prefs.copy(showSunriseSunset = value)
        viewModelScope.launch { prefManager.saveShowSunriseSunset(value) }
    }

    fun updateShowUvIndex(value: Boolean) {
        prefs = prefs.copy(showUvIndex = value)
        viewModelScope.launch { prefManager.saveShowUvIndex(value) }
    }

    fun updateShowAirQuality(value: Boolean) {
        prefs = prefs.copy(showAirQuality = value)
        viewModelScope.launch { prefManager.saveShowAirQuality(value) }
        if (value) fetchAirQuality(uiState.currentLat, uiState.currentLon)
        else uiState = uiState.copy(currentAirQuality = null, aqiHourlyMap = emptyMap())
    }

    private fun fetchAirQuality(lat: Double, lon: Double) {
        viewModelScope.launch {
            runCatching {
                withTimeoutOrNull(5000L) {
                    val res = AirQualityRetrofitClient.api.getAirQuality(lat, lon)
                    val nowStr = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
                    val idx = res.hourly.time.indexOfFirst { it >= nowStr }.takeIf { it != -1 } ?: 0
                    val current = res.hourly.europeanAqi.getOrNull(idx)
                    val hourlyMap = res.hourly.time.zip(res.hourly.europeanAqi)
                        .filter { it.second != null }
                        .associate { it.first to it.second!! }
                    uiState = uiState.copy(currentAirQuality = current, aqiHourlyMap = hourlyMap)
                }
            }
        }
    }

    private fun hasLocationPermission(ctx: android.content.Context) =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
}

@file:OptIn(ExperimentalProjectedApi::class)

package com.example.cliweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.Locale
import kotlin.math.abs

interface WeatherRepository {
    fun fetch(
        scope: CoroutineScope,
        context: Context, useGps: Boolean, lat: Double?, lon: Double?,
        lang: AppLanguage, is24Hour: Boolean, hourlyRange: Int, dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit
    )
}

class RetrofitWeatherRepository : WeatherRepository {
    override fun fetch(
        scope: CoroutineScope,
        context: Context, useGps: Boolean, lat: Double?, lon: Double?,
        lang: AppLanguage, is24Hour: Boolean, hourlyRange: Int, dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit
    ) {
        scope.launch {
            fetchLocationAndWeather(context, useGps, lat, lon, lang, is24Hour, hourlyRange, dailyRange, onLoc, onWeather)
        }
    }
}

class MockWeatherRepository : WeatherRepository {
    override fun fetch(
        scope: CoroutineScope,
        context: Context, useGps: Boolean, lat: Double?, lon: Double?,
        lang: AppLanguage, is24Hour: Boolean, hourlyRange: Int, dailyRange: Int,
        onLoc: (String, String, Double, Double) -> Unit,
        onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit
    ) {
        onLoc("Mock City, Test\n(45.00, 90.00)", "Mock City", 45.0, 90.0)
        val h = List(hourlyRange) { HourlyForecastData("2026-04-12T${String.format("%02d", it % 24)}:00", listOf(0, 1, 2, 61, 71, 95)[it % 6], 1, 20.0 + it, uvIndex = (it % 12).toDouble()) }
        val d = List(dailyRange) { DailyForecastData("2026-04-${12 + it}", listOf(0, 1, 2, 61, 71, 95)[it % 6], 15.0, 25.0, "2026-04-12T06:00", "2026-04-12T18:00", uvIndexMax = 4.0 + it) }
        onWeather(0, 22.5, "UTC", 1, h, d, 3.0)
    }
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    companion object {
        var repository: WeatherRepository = RetrofitWeatherRepository()
    }

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            MaterialTheme {
                WeatherScreen(windowSizeClass, onSpeak = { text, locale ->
                    tts?.language = locale
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                })
            }
        }
    }

    override fun onInit(status: Int) { }
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable
fun WeatherScreen(windowSizeClass: WindowSizeClass, onSpeak: (String, Locale) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val prefManager = remember { PreferenceManager(context) }
    val repo = MainActivity.repository

    val glassesFlow = remember {
        if (Build.VERSION.SDK_INT >= 36) {
            ProjectedContext.isProjectedDeviceConnected(context, scope.coroutineContext + Dispatchers.Main)
        } else {
            MutableStateFlow(false)
        }
    }
    val isGlassesConnected by glassesFlow.collectAsStateWithLifecycle(initialValue = false)

    var showSettings by remember { mutableStateOf(false) }
    var isCelsius by remember { mutableStateOf(prefManager.getIsCelsius()) }
    var appLanguage by remember { mutableStateOf(prefManager.getLanguage()) }
    var useGps by remember { mutableStateOf(prefManager.getUseGps()) }
    var is24Hour by remember { mutableStateOf(prefManager.getIs24Hour(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    var hourlyRange by remember { mutableIntStateOf(prefManager.getHourlyRange()) }
    var dailyRange by remember { mutableIntStateOf(prefManager.getDailyRange()) }
    var showSunriseSunset by remember { mutableStateOf(prefManager.getShowSunriseSunset()) }
    var showUvIndex by remember { mutableStateOf(prefManager.getShowUvIndex()) }
    var showAirQuality by remember { mutableStateOf(prefManager.getShowAirQuality()) }

    var locationInfo by remember { mutableStateOf("") }
    var rawCity by remember { mutableStateOf("") }
    var currentLat by remember { mutableStateOf(prefManager.getLatitude()) }
    var currentLon by remember { mutableStateOf(prefManager.getLongitude()) }
    var weatherCode by remember { mutableIntStateOf(-1) }
    var isDay by remember { mutableIntStateOf(1) }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var mapErrorMessage by remember { mutableStateOf<String?>(null) }
    var hourlyForecasts by remember { mutableStateOf<List<HourlyForecastData>>(emptyList()) }
    var dailyForecasts by remember { mutableStateOf<List<DailyForecastData>>(emptyList()) }
    var currentUvIndex by remember { mutableStateOf<Double?>(null) }
    var currentAirQuality by remember { mutableStateOf<Int?>(null) }
    var aqiHourlyMap by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var apiCallCount by remember { mutableIntStateOf(0) }

    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Optimization: Only recreate context when language changes
    val localizedContext = remember(context, appLanguage) {
        getLocalizedContext(context, appLanguage)
    }

    // Replace the 1-second loop with a Lifecycle observer for better performance
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                if (isGranted != permissionGranted) {
                    permissionGranted = isGranted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        permissionGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(currentLat, currentLon) { prefManager.saveLocation(currentLat, currentLon) }
    LaunchedEffect(isCelsius) { prefManager.saveIsCelsius(isCelsius) }
    LaunchedEffect(appLanguage) { prefManager.saveLanguage(appLanguage) }
    LaunchedEffect(useGps) { prefManager.saveUseGps(useGps) }
    LaunchedEffect(is24Hour) { prefManager.saveIs24Hour(is24Hour) }
    LaunchedEffect(hourlyRange) { prefManager.saveHourlyRange(hourlyRange) }
    LaunchedEffect(dailyRange) { prefManager.saveDailyRange(dailyRange) }
    LaunchedEffect(showSunriseSunset) { prefManager.saveShowSunriseSunset(showSunriseSunset) }
    LaunchedEffect(showUvIndex) { prefManager.saveShowUvIndex(showUvIndex) }
    LaunchedEffect(showAirQuality) { prefManager.saveShowAirQuality(showAirQuality) }
    LaunchedEffect(showAirQuality, currentLat, currentLon) {
        if (showAirQuality) {
            val (current, hourly) = fetchAirQuality(currentLat, currentLon)
            currentAirQuality = current
            aqiHourlyMap = hourly
        } else {
            currentAirQuality = null
            aqiHourlyMap = emptyMap()
        }
    }

    LaunchedEffect(mapErrorMessage) {
        if (mapErrorMessage != null) { delay(2000); mapErrorMessage = null }
    }

    val performFetch: (Boolean, Double?, Double?) -> Unit = { gps, lat, lon ->
        apiCallCount++
        android.util.Log.d("API_COUNT", "Fetching weather data (Total calls: $apiCallCount)")
        isRefreshing = true
        repo.fetch(scope, context, gps, lat, lon, appLanguage, is24Hour, hourlyRange, dailyRange, { full, city, flat, flon ->
            locationInfo = full; rawCity = city; currentLat = flat; currentLon = flon
        }, { code, temp, tz, day, hourly, daily, uvIdx ->
            weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
            currentUvIndex = uvIdx
            isRefreshing = false
        })
    }

    LaunchedEffect(Unit) {
        android.util.Log.d("API_COUNT", "Startup effect triggered. Waiting 2s for UI stability...")
        delay(2000)
        android.util.Log.d("API_COUNT", "Starting initial fetch (GPS: $useGps)...")
        performFetch(useGps, currentLat, currentLon)
        android.util.Log.d("API_COUNT", "Initial fetch call completed.")
    }

    val refreshAction = {
        performFetch(useGps, currentLat, currentLon)
        Unit
    }

    val onMapClick: (Double, Double) -> Unit = { lat, lon ->
        if (useGps) {
            mapErrorMessage = localizedContext.getString(R.string.gps_error)
        } else {
            temperature = null
            performFetch(false, lat, lon)
        }
        Unit
    }

    val shareAction = {
        scope.launch(Dispatchers.IO) {
            try {
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                withContext(Dispatchers.Main) { view.draw(canvas) }
                val cachePath = File(context.cacheDir, "shared_images")
                cachePath.mkdirs()
                val file = File(cachePath, "weather_snapshot.png")
                val stream = FileOutputStream(file)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()
                val contentUri = FileProvider.getUriForFile(context, "com.example.cliweatherapp.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, localizedContext.getString(R.string.share)))
            } catch (e: Exception) { e.printStackTrace() }
        }
        Unit
    }

    val buildSpeechText: () -> String? = {
        if (temperature == null) null
        else {
            val displayTime = formatTime(Date(), timezoneId, appLanguage.locale, is24Hour)
            val displayTemp = convertTemperature(temperature!!, isCelsius)
            val unitName = if (isCelsius) (if (appLanguage == AppLanguage.FR) "degrés Celsius" else "degrees Celsius")
                           else (if (appLanguage == AppLanguage.FR) "degrés Fahrenheit" else "degrees Fahrenheit")
            val isNegative = displayTemp < 0
            val absTempStr = String.format("%.1f", abs(displayTemp))
            val minusPrefix = if (isNegative) "${localizedContext.getString(R.string.minus)} " else ""
            val condition = getConditionString(localizedContext, weatherCode)
            val uvStr = if (showUvIndex && currentUvIndex != null) String.format("%.0f", currentUvIndex) else null
            val uvSuffix = when {
                uvStr == null -> ""
                appLanguage == AppLanguage.FR -> " L'indice UV est $uvStr."
                appLanguage == AppLanguage.JA -> " UV指数は ${uvStr}です。"
                appLanguage == AppLanguage.ES -> " El índice UV es $uvStr."
                appLanguage == AppLanguage.DE -> " Der UV-Index beträgt $uvStr."
                else -> " The UV index is $uvStr."
            }
            when (appLanguage) {
                AppLanguage.FR -> "Il est $displayTime à $rawCity. La météo est $condition avec une température de $minusPrefix$absTempStr $unitName.$uvSuffix"
                AppLanguage.JA -> "現在時刻は $displayTime、場所は $rawCity です。天気は $condition、気温は $minusPrefix$absTempStr 度です。$uvSuffix"
                AppLanguage.ES -> "Son las $displayTime en $rawCity. El clima es $condition con una temperatura de $minusPrefix$absTempStr grados.$uvSuffix"
                AppLanguage.DE -> "Es ist $displayTime in $rawCity. Das Wetter ist $condition bei einer Temperatur von $minusPrefix$absTempStr Grad.$uvSuffix"
                else -> "It is $displayTime in $rawCity. The weather is $condition with a temperature of $minusPrefix$absTempStr $unitName.$uvSuffix"
            }
        }
    }

    val speakAction = {
        buildSpeechText()?.let { text -> onSpeak(text, appLanguage.locale) }
        Unit
    }

    val sendToGlassesAction = {
        buildSpeechText()?.let { text ->
            try {
                val intent = Intent(context, GlassesWeatherActivity::class.java).apply {
                    putExtra(GlassesWeatherActivity.EXTRA_WEATHER_TEXT, text)
                    putExtra(GlassesWeatherActivity.EXTRA_WEATHER_CODE, weatherCode)
                    putExtra(GlassesWeatherActivity.EXTRA_IS_DAY, isDay)
                    putExtra(GlassesWeatherActivity.EXTRA_TEMPERATURE, temperature ?: 0.0)
                    putExtra(GlassesWeatherActivity.EXTRA_IS_CELSIUS, isCelsius)
                    putExtra(GlassesWeatherActivity.EXTRA_CITY, rawCity)
                    putExtra(GlassesWeatherActivity.EXTRA_CONDITION, getConditionString(localizedContext, weatherCode))
                    putExtra(GlassesWeatherActivity.EXTRA_IS_24_HOUR, is24Hour)
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_CODES, hourlyForecasts.map { it.code }.toIntArray())
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_IS_DAY, hourlyForecasts.map { it.isDay }.toIntArray())
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_TEMPS, hourlyForecasts.map { it.temp }.toDoubleArray())
                    putStringArrayListExtra(GlassesWeatherActivity.EXTRA_HOURLY_TIMES, ArrayList(hourlyForecasts.map { it.isoTime }))
                    putExtra(GlassesWeatherActivity.EXTRA_LANGUAGE_TAG, appLanguage.locale.toLanguageTag())
                }
                if (Build.VERSION.SDK_INT >= 36) {
                    val options = ProjectedContext.createProjectedActivityOptions(context)
                    context.startActivity(intent, options.toBundle())
                } else {
                    context.startActivity(intent)
                }
            } catch (_: Exception) { }
        }
        Unit
    }

    val bgColors = remember(weatherCode, isDay) {
        getBackgroundColors(weatherCode, isDay)
    }
    val resolvedColors = bgColors.map { colorResource(it) }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(resolvedColors)).safeDrawingPadding()) {
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
            VerticalLayout(localizedContext, timezoneId, is24Hour, permissionGranted, weatherCode, isDay, temperature, isCelsius, locationInfo, currentLat, currentLon, useGps, mapErrorMessage, appLanguage, hourlyForecasts, dailyForecasts, isRefreshing, refreshAction, speakAction, { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, onMapClick, showSunriseSunset, showUvIndex, currentUvIndex, showAirQuality, currentAirQuality, aqiHourlyMap)
        } else {
            HorizontalLayout(localizedContext, timezoneId, is24Hour, permissionGranted, weatherCode, isDay, temperature, isCelsius, locationInfo, currentLat, currentLon, useGps, mapErrorMessage, appLanguage, hourlyForecasts, dailyForecasts, isRefreshing, refreshAction, speakAction, { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, onMapClick, showSunriseSunset, showUvIndex, currentUvIndex, showAirQuality, currentAirQuality, aqiHourlyMap)
        }
        Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            IconButton(onClick = shareAction) {
                Icon(Icons.Rounded.Share, "Share", tint = Color.White)
            }
            if (isGlassesConnected) {
                IconButton(onClick = sendToGlassesAction, enabled = temperature != null) {
                    Icon(
                        painter = painterResource(R.drawable.ic_glasses),
                        contentDescription = "Send to Glasses",
                        tint = if (temperature != null) Color.White else Color.White.copy(alpha = 0.38f)
                    )
                }
            }
        }
        IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
            Icon(Icons.Rounded.Settings, "Settings", tint = Color.White)
        }

        if (showSettings) {
            SettingsDialog(
                isCelsius = isCelsius,
                onCelsiusChange = { isCelsius = it },
                currentLanguage = appLanguage,
                onLanguageChange = {
                    appLanguage = it
                    refreshAction()
                },
                useGps = useGps,
                onGpsChange = { useGps = it },
                is24Hour = is24Hour,
                on24HourChange = { is24Hour = it },
                hourlyRange = hourlyRange,
                onHourlyRangeChange = {
                    hourlyRange = it
                    refreshAction()
                },
                dailyRange = dailyRange,
                onDailyRangeChange = {
                    dailyRange = it
                    refreshAction()
                },
                showSunriseSunset = showSunriseSunset,
                onShowSunriseSunsetChange = { showSunriseSunset = it },
                showUvIndex = showUvIndex,
                onShowUvIndexChange = { showUvIndex = it },
                showAirQuality = showAirQuality,
                onShowAirQualityChange = { showAirQuality = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

private suspend fun fetchAirQuality(lat: Double, lon: Double): Pair<Int?, Map<String, Int>> {
    return try {
        withTimeoutOrNull(5000L) {
            val res = AirQualityRetrofitClient.api.getAirQuality(lat, lon)
            val nowStr = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:00", java.util.Locale.US).format(java.util.Date())
            val idx = res.hourly.time.indexOfFirst { it >= nowStr }.takeIf { it != -1 } ?: 0
            val current = res.hourly.european_aqi.getOrNull(idx)
            val hourlyMap = res.hourly.time.zip(res.hourly.european_aqi)
                .filter { it.second != null }
                .associate { it.first to it.second!! }
            Pair(current, hourlyMap)
        } ?: Pair(null, emptyMap())
    } catch (e: Exception) { Pair(null, emptyMap()) }
}

@SuppressLint("MissingPermission")
private suspend fun fetchLocationAndWeather(context: android.content.Context, useGps: Boolean, lat: Double?, lon: Double?, lang: AppLanguage, is24Hour: Boolean, hourlyRange: Int, dailyRange: Int, onLoc: (String, String, Double, Double) -> Unit, onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>, Double?) -> Unit) {
    try {
        var finalLat: Double = lat ?: 0.0
        var finalLon: Double = lon ?: 0.0

        if (useGps) {
            val fused = LocationServices.getFusedLocationProviderClient(context)
            var location: android.location.Location? = null

            // Try 1: Proper non-blocking suspend await for current location
            try {
                location = kotlinx.coroutines.withTimeoutOrNull(5000L) {
                    val cts = CancellationTokenSource()
                    fused.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token).await()
                }
            } catch (e: Exception) { }

            // Try 2: Fallback to last known location
            if (location == null) {
                try {
                    location = kotlinx.coroutines.withTimeoutOrNull(2000L) {
                        fused.lastLocation.await()
                    }
                } catch (e: Exception) { }
            }

            // Try 3: Fallback to native LocationManager (respects adb emu geo fix)
            if (location == null) {
                try {
                    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
                    location = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                } catch(e: Exception) {}
            }

            if (location != null) {
                finalLat = location.latitude
                finalLon = location.longitude
            } else {
                val err = getLocalizedContext(context, lang).getString(R.string.gps_error)
                onLoc(err, "Error", 0.0, 0.0)
                onWeather(0, 0.0, "UTC", 1, emptyList(), emptyList(), null)
                return
            }
        }

        // Non-blocking Geocoding
        val address = withContext(Dispatchers.IO) {
            try {
                val addrs = kotlinx.coroutines.withTimeoutOrNull(3000L) {
                    android.location.Geocoder(context, lang.locale).getFromLocation(finalLat, finalLon, 1)
                }
                if (!addrs.isNullOrEmpty()) {
                    val a = addrs!![0]; val city = a.locality ?: a.subAdminArea ?: "??"; val country = a.countryName ?: "??"
                    Pair("$city, $country\n(${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)})", "$city, $country")
                } else Pair("${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}", getLocalizedContext(context, lang).getString(R.string.unknown))
            } catch (e: Exception) {
                Pair("${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}", getLocalizedContext(context, lang).getString(R.string.unknown))
            }
        }
        onLoc(address.first, address.second, finalLat, finalLon)

        // Non-blocking Retrofit
        val neededDays = maxOf(1, (hourlyRange + 23) / 24, dailyRange + 1)
        val res = try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                RetrofitClient.api.getWeather(finalLat, finalLon, days = neededDays)
            } ?: throw java.net.SocketTimeoutException("Network Timeout")
        } catch (e: Exception) {
            android.util.Log.e("API_ERROR", "Fetch failed: ${e.message}", e)
            onWeather(0, 0.0, "UTC", 1, emptyList(), emptyList(), null)
            return
        }

        val hourlyList = mutableListOf<HourlyForecastData>()
        var uvIndex: Double? = null
        res.hourly?.let { h ->
            val nowStr = res.current_weather.time
            val startIndex = h.time.indexOfFirst { it >= nowStr }.takeIf { it != -1 } ?: 0

            uvIndex = h.uv_index?.getOrNull(startIndex)
            for (i in startIndex until minOf(startIndex + hourlyRange, h.time.size)) {
                val itemCode = h.weather_code.getOrNull(i) ?: res.current_weather.weathercode
                val itemIsDay = h.is_day?.getOrNull(i) ?: 1
                hourlyList.add(HourlyForecastData(h.time[i], itemCode, itemIsDay, h.temperature_2m[i], uvIndex = h.uv_index?.getOrNull(i)))
            }
        }

        val dailyList = mutableListOf<DailyForecastData>()
        res.daily?.let { d ->
            for (i in 1 until minOf(1 + dailyRange, d.time.size)) {
                val itemCode = d.weather_code.getOrNull(i) ?: 0
                dailyList.add(DailyForecastData(d.time[i], itemCode, d.temperature_2m_min[i], d.temperature_2m_max[i], d.sunrise[i], d.sunset[i], uvIndexMax = d.uv_index_max?.getOrNull(i)))
            }
        }

        onWeather(res.current_weather.weathercode, res.current_weather.temperature, res.timezone, res.current_weather.is_day, hourlyList, dailyList, uvIndex)
    } catch (e: Exception) {
        onLoc("Error: ${e.message}", "Error", 0.0, 0.0)
        onWeather(0, -999.0, "UTC", 1, emptyList(), emptyList(), null)
    }
}

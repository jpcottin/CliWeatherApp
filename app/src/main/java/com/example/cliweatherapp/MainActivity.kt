package com.example.cliweatherapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.*
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.util.*
import kotlin.math.abs

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
    
    var showSettings by remember { mutableStateOf(false) }
    var isCelsius by remember { mutableStateOf(prefManager.getIsCelsius()) }
    var appLanguage by remember { mutableStateOf(prefManager.getLanguage()) }
    var useGps by remember { mutableStateOf(prefManager.getUseGps()) }
    var is24Hour by remember { mutableStateOf(prefManager.getIs24Hour(context)) }
    var isRefreshing by remember { mutableStateOf(false) }
    
    var locationInfo by remember { mutableStateOf("") }
    var rawCity by remember { mutableStateOf("") }
    var currentLat by remember { mutableStateOf(prefManager.getLatitude()) }
    var currentLon by remember { mutableStateOf(prefManager.getLongitude()) }
    var weatherCode by remember { mutableIntStateOf(-1) }
    var isDay by remember { mutableIntStateOf(1) }
    var temperature by remember { mutableStateOf<Double?>(null) }
    var timezoneId by remember { mutableStateOf<String?>(null) }
    var currentTime by remember { mutableStateOf("") }
    var mapErrorMessage by remember { mutableStateOf<String?>(null) }
    var hourlyForecasts by remember { mutableStateOf<List<HourlyForecastData>>(emptyList()) }
    var dailyForecasts by remember { mutableStateOf<List<DailyForecastData>>(emptyList()) }
    
    val ctx = getLocalizedContext(context, appLanguage)

    var permissionGranted by remember { 
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms -> 
        permissionGranted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true || perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true 
    }

    LaunchedEffect(currentLat, currentLon) { prefManager.saveLocation(currentLat, currentLon) }
    LaunchedEffect(isCelsius) { prefManager.saveIsCelsius(isCelsius) }
    LaunchedEffect(appLanguage) { prefManager.saveLanguage(appLanguage) }
    LaunchedEffect(useGps) { prefManager.saveUseGps(useGps) }
    LaunchedEffect(is24Hour) { prefManager.saveIs24Hour(is24Hour) }

    LaunchedEffect(timezoneId, appLanguage, is24Hour) {
        while(true) {
            currentTime = formatTime(Date(), timezoneId, appLanguage.locale, is24Hour)
            delay(1000)
        }
    }

    LaunchedEffect(mapErrorMessage) {
        if (mapErrorMessage != null) { delay(2000); mapErrorMessage = null }
    }

    DisposableEffect(permissionGranted, useGps) {
        if (useGps && permissionGranted) {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    scope.launch {
                        isRefreshing = true
                        fetchLocationAndWeather(context, false, loc.latitude, loc.longitude, appLanguage, is24Hour, { full, city, lat, lon -> 
                            locationInfo = full; rawCity = city; currentLat = lat; currentLon = lon
                        }, { code, temp, tz, day, hourly, daily -> 
                            weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
                            isRefreshing = false
                        })
                    }
                }
                override fun onStatusChanged(p: String?, s: Int, e: Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            try {
                lm.requestLocationUpdates(android.location.LocationManager.GPS_PROVIDER, 2000L, 1f, listener)
                // Trigger an initial fetch
                val lastLoc = lm.getLastKnownLocation(android.location.LocationManager.GPS_PROVIDER)
                scope.launch {
                    isRefreshing = true
                    fetchLocationAndWeather(context, true, lastLoc?.latitude, lastLoc?.longitude, appLanguage, is24Hour, { full, city, lat, lon -> 
                        locationInfo = full; rawCity = city; currentLat = lat; currentLon = lon
                    }, { code, temp, tz, day, hourly, daily -> 
                        weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
                        isRefreshing = false
                    })
                }
            } catch (e: SecurityException) { }
            
            onDispose { lm.removeUpdates(listener) }
        } else {
            // Initial fetch for manual mode
            scope.launch {
                isRefreshing = true
                fetchLocationAndWeather(context, false, currentLat, currentLon, appLanguage, is24Hour, { full, city, lat, lon -> 
                    locationInfo = full; rawCity = city; currentLat = lat; currentLon = lon
                }, { code, temp, tz, day, hourly, daily -> 
                    weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
                    isRefreshing = false
                })
            }
            onDispose { }
        }
    }
val refreshAction = {
    scope.launch {
        isRefreshing = true
        temperature = null
        fetchLocationAndWeather(context, useGps, currentLat, currentLon, appLanguage, is24Hour, { full, city, lat, lon -> 
            locationInfo = full; rawCity = city; currentLat = lat; currentLon = lon
        }, { code, temp, tz, day, hourly, daily -> 
            weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
            isRefreshing = false
        })
    }
    Unit
}

    val onMapClick: (Double, Double) -> Unit = { lat, lon ->
        if (useGps) {
            mapErrorMessage = ctx.getString(R.string.gps_error)
        } else {
            scope.launch {
                isRefreshing = true
                temperature = null
                fetchLocationAndWeather(context, false, lat, lon, appLanguage, is24Hour, { full, city, flat, flon -> 
                    locationInfo = full; rawCity = city; currentLat = flat; currentLon = flon
                }, { code, temp, tz, day, hourly, daily -> 
                    weatherCode = code; temperature = temp; timezoneId = tz; isDay = day; hourlyForecasts = hourly; dailyForecasts = daily
                    isRefreshing = false
                })
            }
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
                context.startActivity(Intent.createChooser(intent, ctx.getString(R.string.share)))
            } catch (e: Exception) { e.printStackTrace() }
        }
        Unit
    }

    val speakAction = {
        if (temperature != null) {
            val displayTemp = convertTemperature(temperature!!, isCelsius)
            val unitName = if (isCelsius) (if(appLanguage == AppLanguage.FR) "degrés Celsius" else "degrees Celsius") 
                       else (if(appLanguage == AppLanguage.FR) "degrés Fahrenheit" else "degrees Fahrenheit")
            val isNegative = displayTemp < 0
            val absTempStr = String.format("%.1f", abs(displayTemp))
            val minusPrefix = if(isNegative) "${ctx.getString(R.string.minus)} " else ""
            
            val condition = getConditionString(ctx, weatherCode)
            val speechText = when(appLanguage) {
                AppLanguage.FR -> "Il est $currentTime à $rawCity. La météo est $condition avec une température de $minusPrefix$absTempStr $unitName."
                AppLanguage.JA -> "現在時刻は $currentTime、場所は $rawCity です。天気は $condition、気温は $minusPrefix$absTempStr 度です。"
                AppLanguage.ES -> "Son las $currentTime en $rawCity. El clima es $condition con una temperatura de $minusPrefix$absTempStr grados."
                AppLanguage.DE -> "Es ist $currentTime in $rawCity. Das Wetter ist $condition bei einer Temperatur von $minusPrefix$absTempStr Grad."
                else -> "It is $currentTime in $rawCity. The weather is $condition with a temperature of $minusPrefix$absTempStr $unitName."
            }
            onSpeak(speechText, appLanguage.locale)
        }
    }

    val bgColors = getBackgroundColors(weatherCode, isDay).map { colorResource(it) }

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(bgColors)).statusBarsPadding()) {
        if (windowSizeClass.widthSizeClass == WindowWidthSizeClass.Compact) {
            VerticalLayout(currentTime, is24Hour, permissionGranted, weatherCode, isDay, temperature, isCelsius, locationInfo, currentLat, currentLon, useGps, mapErrorMessage, appLanguage, hourlyForecasts, dailyForecasts, isRefreshing, refreshAction, speakAction, { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, onMapClick)
            IconButton(onClick = { showSettings = true }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                Icon(Icons.Rounded.Settings, "Settings", tint = Color.White)
            }
        } else {
            HorizontalLayout(currentTime, is24Hour, permissionGranted, weatherCode, isDay, temperature, isCelsius, locationInfo, currentLat, currentLon, useGps, mapErrorMessage, appLanguage, hourlyForecasts, dailyForecasts, isRefreshing, refreshAction, speakAction, { launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }, onMapClick, onOpenSettings = { showSettings = true })
        }

        IconButton(onClick = shareAction, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
            Icon(Icons.Rounded.Share, "Share", tint = Color.White)
        }

        if (showSettings) {
            SettingsDialog(
                isCelsius = isCelsius,
                onCelsiusChange = { isCelsius = it },
                currentLanguage = appLanguage,
                onLanguageChange = { appLanguage = it },
                useGps = useGps,
                onGpsChange = { useGps = it },
                is24Hour = is24Hour,
                on24HourChange = { is24Hour = it },
                onDismiss = { showSettings = false }
            )
        }
    }
}

@SuppressLint("MissingPermission")
private suspend fun fetchLocationAndWeather(context: android.content.Context, useGps: Boolean, lat: Double?, lon: Double?, lang: AppLanguage, is24Hour: Boolean, onLoc: (String, String, Double, Double) -> Unit, onWeather: (Int, Double, String, Int, List<HourlyForecastData>, List<DailyForecastData>) -> Unit) {
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
                onWeather(0, 0.0, "UTC", 1, emptyList(), emptyList())
                return
            }
        }
        
        // Non-blocking Geocoding
        val addressJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).async {
            try { android.location.Geocoder(context, lang.locale).getFromLocation(finalLat, finalLon, 1) } catch(e: Exception) { null }
        }
        val address = try {
            val addrs = kotlinx.coroutines.withTimeoutOrNull(2000L) { addressJob.await() }
            if (!addrs.isNullOrEmpty()) {
                val a = addrs!![0]; val city = a.locality ?: a.subAdminArea ?: "??"; val country = a.countryName ?: "??"
                Pair("$city, $country\n(${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)})", "$city, $country")
            } else Pair("${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}", getLocalizedContext(context, lang).getString(R.string.unknown))
        } catch (e: Exception) { Pair("${String.format("%.2f", finalLat)}, ${String.format("%.2f", finalLon)}", getLocalizedContext(context, lang).getString(R.string.unknown)) }
        onLoc(address.first, address.second, finalLat, finalLon)
        
        // Non-blocking Retrofit
        val res = try {
            kotlinx.coroutines.withTimeoutOrNull(5000L) {
                RetrofitClient.api.getWeather(finalLat, finalLon)
            } ?: throw java.net.SocketTimeoutException("Network Timeout")
        } catch (e: Exception) {
            onWeather(0, 0.0, "UTC", 1, emptyList(), emptyList())
            return
        }

        val hourlyList = mutableListOf<HourlyForecastData>()
        res.hourly?.let { h ->
            val startIndex = h.time.indexOfFirst { it >= res.current_weather.time }.takeIf { it != -1 } ?: 0
            for (i in startIndex until minOf(startIndex + 6, h.time.size)) {
                hourlyList.add(HourlyForecastData(h.time[i], h.weathercode[i], h.is_day?.getOrNull(i) ?: 1, h.temperature_2m[i]))
            }
        }

        val dailyList = mutableListOf<DailyForecastData>()
        res.daily?.let { d ->
            for (i in 1 until minOf(7, d.time.size)) {
                dailyList.add(DailyForecastData(d.time[i], d.weathercode[i], d.temperature_2m_min[i], d.temperature_2m_max[i]))
            }
        }

        onWeather(res.current_weather.weathercode, res.current_weather.temperature, res.timezone, res.current_weather.is_day, hourlyList, dailyList)
    } catch (e: Exception) { 
        onLoc("Error: ${e.message}", "Error", 0.0, 0.0) 
        onWeather(0, -999.0, "UTC", 1, emptyList(), emptyList())
    }
}

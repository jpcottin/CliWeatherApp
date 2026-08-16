@file:OptIn(ExperimentalProjectedApi::class)

package com.jpcottin.weatherglance

import android.Manifest
import android.content.Intent
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.xr.projected.ProjectedContext
import androidx.xr.projected.experimental.ExperimentalProjectedApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        tts = TextToSpeech(this, this)
        setContent {
            val windowSizeClass = calculateWindowSizeClass(this)
            MaterialTheme {
                WeatherScreen(
                    windowSizeClass = windowSizeClass,
                    onSpeak = { text, locale ->
                        tts?.language = locale
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                    }
                )
            }
        }
    }

    override fun onInit(status: Int) {}
    override fun onDestroy() { tts?.stop(); tts?.shutdown(); super.onDestroy() }
}

@Composable
fun WeatherScreen(
    windowSizeClass: WindowSizeClass,
    onSpeak: (String, Locale) -> Unit,
    viewModel: WeatherViewModel = viewModel()
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val uiState = viewModel.uiState
    val prefs = viewModel.prefs

    val localizedContext = remember(context, prefs.appLanguage) {
        getLocalizedContext(context, prefs.appLanguage)
    }

    // Update permission state on resume
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                viewModel.updatePermission(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        viewModel.updatePermission(
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        )
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
                FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                val contentUri = FileProvider.getUriForFile(
                    context, "com.jpcottin.weatherglance.fileprovider", file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/png"
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, localizedContext.getString(R.string.share)))
            } catch (_: Exception) {}
        }
        Unit
    }

    val speakAction = {
        buildSpeechText(uiState, prefs, localizedContext)?.let { text ->
            onSpeak(text, prefs.appLanguage.locale)
        }
        Unit
    }

    val sendToGlassesAction = {
        buildSpeechText(uiState, prefs, localizedContext)?.let { text ->
            try {
                val intent = Intent(context, GlassesWeatherActivity::class.java).apply {
                    putExtra(GlassesWeatherActivity.EXTRA_WEATHER_TEXT, text)
                    putExtra(GlassesWeatherActivity.EXTRA_WEATHER_CODE, uiState.weatherCode)
                    putExtra(GlassesWeatherActivity.EXTRA_IS_DAY, uiState.isDay)
                    putExtra(GlassesWeatherActivity.EXTRA_TEMPERATURE, uiState.temperature ?: 0.0)
                    putExtra(GlassesWeatherActivity.EXTRA_IS_CELSIUS, prefs.isCelsius)
                    putExtra(GlassesWeatherActivity.EXTRA_CITY, uiState.rawCity)
                    putExtra(GlassesWeatherActivity.EXTRA_CONDITION, getConditionString(localizedContext, uiState.weatherCode))
                    putExtra(GlassesWeatherActivity.EXTRA_IS_24_HOUR, prefs.is24Hour)
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_CODES, uiState.hourlyForecasts.map { it.code }.toIntArray())
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_IS_DAY, uiState.hourlyForecasts.map { it.isDay }.toIntArray())
                    putExtra(GlassesWeatherActivity.EXTRA_HOURLY_TEMPS, uiState.hourlyForecasts.map { it.temp }.toDoubleArray())
                    putStringArrayListExtra(GlassesWeatherActivity.EXTRA_HOURLY_TIMES, ArrayList(uiState.hourlyForecasts.map { it.isoTime }))
                    putExtra(GlassesWeatherActivity.EXTRA_LANGUAGE_TAG, prefs.appLanguage.locale.toLanguageTag())
                }
                if (Build.VERSION.SDK_INT >= 36) {
                    val options = ProjectedContext.createProjectedActivityOptions(context)
                    context.startActivity(intent, options.toBundle())
                } else {
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }
        Unit
    }

    var showSettings by remember { mutableStateOf(false) }

    val bgColors = remember(uiState.weatherCode, uiState.isDay) {
        getBackgroundColors(uiState.weatherCode, uiState.isDay)
    }
    val resolvedColors = bgColors.map { colorResource(it) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(resolvedColors))
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Action bar — replaces floating overlay, eliminates hardcoded 56 dp top padding
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 0.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                IconButton(onClick = shareAction) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share", tint = Color.White)
                }
                if (uiState.isGlassesConnected) {
                    IconButton(
                        onClick = sendToGlassesAction,
                        enabled = uiState.temperature != null
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_glasses),
                            contentDescription = "Send to Glasses",
                            tint = if (uiState.temperature != null) Color.White
                                   else Color.White.copy(alpha = 0.38f)
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showSettings = true }) {
                    Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White)
                }
            }

            // Weather content — Expanded → horizontal, Compact/Medium → vertical
            when (windowSizeClass.widthSizeClass) {
                WindowWidthSizeClass.Expanded -> HorizontalLayout(
                    ctx = localizedContext,
                    state = uiState,
                    prefs = prefs,
                    onRefresh = { viewModel.fetchWeather() },
                    onSpeak = speakAction,
                    onRequestPermission = {
                        launcher.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    onMapClick = viewModel::onMapClick
                )
                else -> VerticalLayout(
                    ctx = localizedContext,
                    state = uiState,
                    prefs = prefs,
                    onRefresh = { viewModel.fetchWeather() },
                    onSpeak = speakAction,
                    onRequestPermission = {
                        launcher.launch(arrayOf(
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        ))
                    },
                    onMapClick = viewModel::onMapClick
                )
            }
        }

        if (showSettings) {
            SettingsDialog(
                prefs = prefs,
                onIsCelsiusChange = viewModel::updateIsCelsius,
                onLanguageChange = viewModel::updateLanguage,
                onUseGpsChange = viewModel::updateUseGps,
                on24HourChange = viewModel::updateIs24Hour,
                onHourlyRangeChange = viewModel::updateHourlyRange,
                onDailyRangeChange = viewModel::updateDailyRange,
                onShowSunriseSunsetChange = viewModel::updateShowSunriseSunset,
                onShowUvIndexChange = viewModel::updateShowUvIndex,
                onShowAirQualityChange = viewModel::updateShowAirQuality,
                onDismiss = { showSettings = false }
            )
        }
    }
}

private fun buildSpeechText(
    uiState: WeatherUiState,
    prefs: WeatherPrefs,
    localizedContext: android.content.Context
): String? {
    val temperature = uiState.temperature ?: return null
    val displayTime = formatTime(java.util.Date(), uiState.timezoneId, prefs.appLanguage.locale, prefs.is24Hour)
    val displayTemp = convertTemperature(temperature, prefs.isCelsius)
    val unitName = if (prefs.isCelsius)
        (if (prefs.appLanguage == AppLanguage.FR) "degrés Celsius" else "degrees Celsius")
    else
        (if (prefs.appLanguage == AppLanguage.FR) "degrés Fahrenheit" else "degrees Fahrenheit")
    val isNegative = displayTemp < 0
    val absTempStr = String.format("%.1f", kotlin.math.abs(displayTemp))
    val minusPrefix = if (isNegative) "${localizedContext.getString(R.string.minus)} " else ""
    val condition = getConditionString(localizedContext, uiState.weatherCode)
    val uvStr = if (prefs.showUvIndex && uiState.currentUvIndex != null)
        String.format("%.0f", uiState.currentUvIndex) else null
    val uvSuffix = when {
        uvStr == null -> ""
        prefs.appLanguage == AppLanguage.FR -> " L'indice UV est $uvStr."
        prefs.appLanguage == AppLanguage.JA -> " UV指数は ${uvStr}です。"
        prefs.appLanguage == AppLanguage.ES -> " El índice UV es $uvStr."
        prefs.appLanguage == AppLanguage.DE -> " Der UV-Index beträgt $uvStr."
        else -> " The UV index is $uvStr."
    }
    return when (prefs.appLanguage) {
        AppLanguage.FR -> "Il est $displayTime à ${uiState.rawCity}. La météo est $condition avec une température de $minusPrefix$absTempStr $unitName.$uvSuffix"
        AppLanguage.JA -> "現在時刻は $displayTime、場所は ${uiState.rawCity} です。天気は $condition、気温は $minusPrefix$absTempStr 度です。$uvSuffix"
        AppLanguage.ES -> "Son las $displayTime en ${uiState.rawCity}. El clima es $condition con una temperatura de $minusPrefix$absTempStr grados.$uvSuffix"
        AppLanguage.DE -> "Es ist $displayTime in ${uiState.rawCity}. Das Wetter ist $condition bei einer Temperatur von $minusPrefix$absTempStr Grad.$uvSuffix"
        else -> "It is $displayTime in ${uiState.rawCity}. The weather is $condition with a temperature of $minusPrefix$absTempStr $unitName.$uvSuffix"
    }
}

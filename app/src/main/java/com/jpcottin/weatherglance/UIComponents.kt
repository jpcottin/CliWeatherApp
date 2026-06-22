package com.jpcottin.weatherglance

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import android.content.Context
import java.util.Date
import kotlinx.coroutines.delay

@Composable
private fun rememberRefreshRotation(isRefreshing: Boolean): Float {
    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    return if (isRefreshing) rotation else 0f
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    prefs: WeatherPrefs,
    onIsCelsiusChange: (Boolean) -> Unit,
    onLanguageChange: (AppLanguage) -> Unit,
    onUseGpsChange: (Boolean) -> Unit,
    on24HourChange: (Boolean) -> Unit,
    onHourlyRangeChange: (Int) -> Unit,
    onDailyRangeChange: (Int) -> Unit,
    onShowSunriseSunsetChange: (Boolean) -> Unit,
    onShowUvIndexChange: (Boolean) -> Unit,
    onShowAirQualityChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = getLocalizedContext(LocalContext.current, prefs.appLanguage)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(ctx.getString(R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.unit), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("°C")
                    Switch(checked = !prefs.isCelsius, onCheckedChange = { onIsCelsiusChange(!it) }, modifier = Modifier.scale(0.8f).testTag("switch_unit"))
                    Text("°F")
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.time_format), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text("12h")
                    Switch(checked = prefs.is24Hour, onCheckedChange = { on24HourChange(it) }, modifier = Modifier.scale(0.8f).testTag("switch_time"))
                    Text("24h")
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                var expanded by remember { mutableStateOf(false) }
                Text(ctx.getString(R.string.language), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = prefs.appLanguage.label, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth().testTag("dropdown_lang"), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AppLanguage.values().forEach { lang -> DropdownMenuItem(text = { Text(lang.label) }, onClick = { onLanguageChange(lang); expanded = false }) }
                    }
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.loc_mode), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(ctx.getString(R.string.gps), fontSize = 12.sp, color = if (prefs.useGps) MaterialTheme.colorScheme.primary else Color.Gray)
                    Switch(checked = !prefs.useGps, onCheckedChange = { onUseGpsChange(!it) }, modifier = Modifier.scale(0.8f).testTag("switch_gps"))
                    Text(ctx.getString(R.string.map_click), fontSize = 12.sp, color = if (!prefs.useGps) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                var localHourlyRange by remember(prefs.hourlyRange) { mutableStateOf(prefs.hourlyRange) }
                Text(String.format(ctx.getString(R.string.hourly_range_label), localHourlyRange), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = localHourlyRange.toFloat(),
                    onValueChange = { localHourlyRange = it.toInt() },
                    onValueChangeFinished = { onHourlyRangeChange(localHourlyRange) },
                    valueRange = 0f..168f,
                    steps = 167,
                    modifier = Modifier.testTag("slider_hourly")
                )
                Spacer(Modifier.height(8.dp))

                var localDailyRange by remember(prefs.dailyRange) { mutableStateOf(prefs.dailyRange) }
                Text(String.format(ctx.getString(R.string.daily_range_label), localDailyRange), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = localDailyRange.toFloat(),
                    onValueChange = { localDailyRange = it.toInt() },
                    onValueChangeFinished = { onDailyRangeChange(localDailyRange) },
                    valueRange = 0f..16f,
                    steps = 15,
                    modifier = Modifier.testTag("slider_daily")
                )
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.show_sunrise_sunset), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = prefs.showSunriseSunset, onCheckedChange = { onShowSunriseSunsetChange(it) }, modifier = Modifier.scale(0.8f).testTag("switch_sunrise_sunset"))
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.show_uv_index), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = prefs.showUvIndex, onCheckedChange = { onShowUvIndexChange(it) }, modifier = Modifier.scale(0.8f).testTag("switch_uv_index"))
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.show_air_quality), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Switch(checked = prefs.showAirQuality, onCheckedChange = { onShowAirQualityChange(it) }, modifier = Modifier.scale(0.8f).testTag("switch_air_quality"))
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
            }
        }
    }
}

data class HourlyForecastData(val isoTime: String, val code: Int, val isDay: Int, val temp: Double, val uvIndex: Double? = null)
data class DailyForecastData(val isoDate: String, val code: Int, val minTemp: Double, val maxTemp: Double, val sunrise: String, val sunset: String, val uvIndexMax: Double? = null)

@Composable
fun HourlyForecastRow(
    forecasts: List<HourlyForecastData>,
    prefs: WeatherPrefs,
    aqiMap: Map<String, Int> = emptyMap()
) {
    LazyRow(modifier = Modifier.testTag("hourly_forecast_list"), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecasts) { item ->
            val aqi = if (prefs.showAirQuality) aqiMap[item.isoTime] else null
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatForecastHour(item.isoTime, prefs.appLanguage.locale, prefs.is24Hour), fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Icon(getWeatherIcon(item.code, item.isDay), null, Modifier.size(32.dp), Color.White)
                Spacer(Modifier.height(4.dp))
                Text("${String.format("%.0f", convertTemperature(item.temp, prefs.isCelsius))}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (prefs.showUvIndex && item.uvIndex != null) {
                    Spacer(Modifier.height(3.dp))
                    SmallBadge("UV", String.format("%.0f", item.uvIndex), uvColor(item.uvIndex))
                }
                if (aqi != null) {
                    Spacer(Modifier.height(3.dp))
                    SmallBadge("AQI", aqi.toString(), aqiColor(aqi))
                }
            }
        }
    }
}

@Composable
fun DailyForecastRow(
    forecasts: List<DailyForecastData>,
    prefs: WeatherPrefs
) {
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecasts) { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatForecastDay(item.isoDate, prefs.appLanguage.locale), fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Icon(getWeatherIcon(item.code, 1), null, Modifier.size(32.dp), Color.White)
                Spacer(Modifier.height(4.dp))
                Text("${String.format("%.0f", convertTemperature(item.minTemp, prefs.isCelsius))}° / ${String.format("%.0f", convertTemperature(item.maxTemp, prefs.isCelsius))}°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (prefs.showUvIndex && item.uvIndexMax != null) {
                    Spacer(Modifier.height(4.dp))
                    SmallBadge("UV", String.format("%.0f", item.uvIndexMax), uvColor(item.uvIndexMax))
                }
                if (prefs.showSunriseSunset) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WbSunny, null, Modifier.size(10.dp), Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(2.dp))
                        Text(formatSunriseSunset(item.sunrise, prefs.appLanguage.locale, prefs.is24Hour), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.NightlightRound, null, Modifier.size(10.dp), Color.White.copy(alpha = 0.8f))
                        Spacer(Modifier.width(2.dp))
                        Text(formatSunriseSunset(item.sunset, prefs.appLanguage.locale, prefs.is24Hour), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
fun VerticalLayout(
    ctx: Context,
    state: WeatherUiState,
    prefs: WeatherPrefs,
    onRefresh: () -> Unit,
    onSpeak: () -> Unit,
    onRequestPermission: () -> Unit,
    onMapClick: (Double, Double) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SectionTimeWeather(ctx, state, prefs, compact = true)
        if (state.hourlyForecasts.isNotEmpty()) HourlyForecastRow(state.hourlyForecasts, prefs, state.aqiHourlyMap)
        if (state.dailyForecasts.isNotEmpty()) DailyForecastRow(state.dailyForecasts, prefs)
        SectionControls(ctx, state, prefs, onRefresh, onSpeak, onRequestPermission)
        MiniWorldMap(ctx, state, onMapClick, Modifier.height(220.dp).fillMaxWidth().padding(horizontal = 16.dp))
    }
}

@Composable
fun HorizontalLayout(
    ctx: Context,
    state: WeatherUiState,
    prefs: WeatherPrefs,
    onRefresh: () -> Unit,
    onSpeak: () -> Unit,
    onRequestPermission: () -> Unit,
    onMapClick: (Double, Double) -> Unit
) {
    val rotation = rememberRefreshRotation(state.isRefreshing)
    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTimeWeather(ctx, state, prefs)
            if (state.hourlyForecasts.isNotEmpty()) HourlyForecastRow(state.hourlyForecasts, prefs, state.aqiHourlyMap)
            if (state.dailyForecasts.isNotEmpty()) DailyForecastRow(state.dailyForecasts, prefs)
        }
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            MiniWorldMap(ctx, state, onMapClick, Modifier.weight(0.7f).fillMaxWidth())
            Column(modifier = Modifier.weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = state.locationInfo.substringBefore("\n"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
                Text(text = state.locationInfo.substringAfter("\n", ""), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.testTag("location_coords"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (prefs.useGps) {
                        Button(onClick = onRefresh, enabled = !state.isRefreshing, modifier = Modifier.height(32.dp).testTag("btn_refresh")) {
                            Icon(Icons.Rounded.Refresh, null, Modifier.size(16.dp).graphicsLayer { rotationZ = rotation }, tint = Color.White)
                            Text(ctx.getString(R.string.refresh), fontSize = 12.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Button(onClick = onSpeak, modifier = Modifier.height(32.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(16.dp))
                        Text(ctx.getString(R.string.speak), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// Fixed instant used only when rendering in Compose preview / screenshot tests
// (2026-04-20T14:30:00Z), so the clock in golden screenshots is reproducible.
private const val PREVIEW_CLOCK_MILLIS = 1_776_695_400_000L

@Composable
fun SectionTimeDisplay(ctx: Context, timezoneId: String?, lang: AppLanguage, is24h: Boolean, compact: Boolean = false) {
    // Under Compose preview / screenshot rendering, freeze the clock to a fixed
    // instant so golden screenshots stay deterministic (a live clock would diff
    // on every run). Has no effect on the real app.
    val inspection = LocalInspectionMode.current
    fun now() = if (inspection) Date(PREVIEW_CLOCK_MILLIS) else Date()
    var rawTime by remember(timezoneId, lang, is24h) { mutableStateOf(formatTime(now(), timezoneId, lang.locale, is24h)) }

    if (!inspection) {
        LaunchedEffect(timezoneId, lang, is24h) {
            while (true) {
                rawTime = formatTime(Date(), timezoneId, lang.locale, is24h)
                delay(1000)
            }
        }
    }

    val digits = remember(rawTime, is24h) { if (!is24h) rawTime.substringBeforeLast(" ") else rawTime }
    val suffix = remember(rawTime, is24h) { if (!is24h) rawTime.substringAfterLast(" ") else "" }

    if (compact) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(digits, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (suffix.isNotEmpty()) {
                Text(" $suffix", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(ctx.getString(R.string.current_time), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(digits, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (suffix.isNotEmpty()) {
                    Text(" $suffix", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 8.dp))
                }
            }
        }
    }
}

@Composable
fun SectionTimeWeather(ctx: Context, state: WeatherUiState, prefs: WeatherPrefs, compact: Boolean = false) {
    val hasBadges = (prefs.showUvIndex && state.currentUvIndex != null) || (prefs.showAirQuality && state.currentAirQuality != null)
    if (compact) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                SectionTimeDisplay(ctx, state.timezoneId, prefs.appLanguage, prefs.is24Hour, compact = true)
                if (hasBadges) {
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (prefs.showUvIndex && state.currentUvIndex != null) InfoBadge("UV", state.currentUvIndex.toInt().toString(), uvColor(state.currentUvIndex))
                        if (prefs.showAirQuality && state.currentAirQuality != null) InfoBadge("AQI", state.currentAirQuality.toString(), aqiColor(state.currentAirQuality))
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 12.dp)) {
                Icon(getWeatherIcon(state.weatherCode, state.isDay), null, Modifier.size(64.dp), Color.White)
                Box(modifier = Modifier.testTag("current_temp"), contentAlignment = Alignment.Center) {
                    if (state.temperature != null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${String.format("%.1f", convertTemperature(state.temperature, prefs.isCelsius))}${if (prefs.isCelsius) "°C" else "°F"}", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(getConditionString(ctx, state.weatherCode), fontSize = 14.sp, color = Color.White.copy(alpha = 0.9f))
                        }
                    } else {
                        Text(ctx.getString(R.string.loading), color = Color.White)
                    }
                }
            }
        }
    } else {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            SectionTimeDisplay(ctx, state.timezoneId, prefs.appLanguage, prefs.is24Hour)
            if (hasBadges) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (prefs.showUvIndex && state.currentUvIndex != null) InfoBadge("UV", state.currentUvIndex.toInt().toString(), uvColor(state.currentUvIndex))
                    if (prefs.showAirQuality && state.currentAirQuality != null) InfoBadge("AQI", state.currentAirQuality.toString(), aqiColor(state.currentAirQuality))
                }
            }
            Spacer(Modifier.height(16.dp))
            Icon(getWeatherIcon(state.weatherCode, state.isDay), null, Modifier.size(100.dp), Color.White)
            Box(modifier = Modifier.testTag("current_temp"), contentAlignment = Alignment.Center) {
                if (state.temperature != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${String.format("%.1f", convertTemperature(state.temperature, prefs.isCelsius))}${if (prefs.isCelsius) "°C" else "°F"}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(getConditionString(ctx, state.weatherCode), fontSize = 20.sp, color = Color.White.copy(alpha = 0.9f))
                    }
                } else {
                    Text(ctx.getString(R.string.loading), color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SectionControls(
    ctx: Context,
    state: WeatherUiState,
    prefs: WeatherPrefs,
    onRefresh: () -> Unit,
    onSpeak: () -> Unit,
    onRequestPermission: () -> Unit
) {
    val rotation = rememberRefreshRotation(state.isRefreshing)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!state.permissionGranted) {
            Button(onClick = onRequestPermission) { Text(ctx.getString(R.string.grant_permission)) }
        } else {
            Text(ctx.getString(R.string.location_label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text(state.locationInfo.substringBefore("\n"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
            Text(state.locationInfo.substringAfter("\n", ""), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.testTag("location_coords"))
            Spacer(Modifier.height(12.dp))
            Row {
                if (prefs.useGps) {
                    Button(onClick = onRefresh, enabled = !state.isRefreshing, modifier = Modifier.testTag("btn_refresh")) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp).graphicsLayer { rotationZ = rotation }, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(ctx.getString(R.string.refresh))
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Button(onClick = onSpeak) {
                    Icon(Icons.AutoMirrored.Rounded.VolumeUp, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(ctx.getString(R.string.speak))
                }
            }
        }
    }
}

@Composable
fun MiniWorldMap(ctx: Context, state: WeatherUiState, onMapClick: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.height(20.dp)) {
            if (state.mapErrorMessage != null) Text(state.mapErrorMessage, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else Text(ctx.getString(R.string.map_label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black).testTag("world_map").pointerInput(Unit) {
            detectTapGestures { offset ->
                val flat = 90 - (offset.y / size.height) * 180
                val flon = (offset.x / size.width) * 360 - 180
                onMapClick(flat.toDouble(), flon.toDouble())
            }
        }) {
            Image(painter = painterResource(id = R.drawable.world_map), contentDescription = "World Map", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun mapX(l: Double) = ((l + 180) / 360f) * size.width
                fun mapY(l: Double) = ((90 - l) / 180f) * size.height
                val x = mapX(state.currentLon); val y = mapY(state.currentLat); val cs = 12f
                drawLine(Color.Red, Offset(x.toFloat()-cs, y.toFloat()-cs), Offset(x.toFloat()+cs, y.toFloat()+cs), 4f)
                drawLine(Color.Red, Offset(x.toFloat()+cs, y.toFloat()-cs), Offset(x.toFloat()-cs, y.toFloat()+cs), 4f)
            }
        }
    }
}

internal fun uvColor(uv: Double) = when {
    uv < 3  -> Color(0xFF66BB6A)
    uv < 6  -> Color(0xFFFDD835)
    uv < 8  -> Color(0xFFFFA726)
    uv < 11 -> Color(0xFFEF5350)
    else    -> Color(0xFFAB47BC)
}

internal fun aqiColor(aqi: Int) = when {
    aqi < 20  -> Color(0xFF66BB6A)
    aqi < 40  -> Color(0xFFD4E157)
    aqi < 60  -> Color(0xFFFDD835)
    aqi < 80  -> Color(0xFFFFA726)
    aqi < 100 -> Color(0xFFEF5350)
    else      -> Color(0xFFAB47BC)
}

@Composable
private fun InfoBadge(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.35f)) {
        Text("$label $value", modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

@Composable
private fun SmallBadge(label: String, value: String, color: Color) {
    Surface(shape = RoundedCornerShape(6.dp), color = color.copy(alpha = 0.45f)) {
        Text("$label $value", modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp), fontSize = 10.sp, fontWeight = FontWeight.Medium, color = Color.White)
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
fun PreviewSettingsDialog() {
    MaterialTheme {
        SettingsDialog(
            prefs = WeatherPrefs(),
            onIsCelsiusChange = {}, onLanguageChange = {}, onUseGpsChange = {},
            on24HourChange = {}, onHourlyRangeChange = {}, onDailyRangeChange = {},
            onShowSunriseSunsetChange = {}, onShowUvIndexChange = {}, onShowAirQualityChange = {},
            onDismiss = {}
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewHourlyForecastRow() {
    val forecasts = List(6) { HourlyForecastData("2026-04-20T${14+it}:00", 0, 1, 20.0 + it, uvIndex = 4.0 + it * 0.5) }
    val aqiMap = forecasts.mapIndexed { i, h -> h.isoTime to (25 + i * 5) }.toMap()
    HourlyForecastRow(forecasts = forecasts, prefs = WeatherPrefs(showUvIndex = true, showAirQuality = true), aqiMap = aqiMap)
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewDailyForecastRow() {
    val forecasts = List(5) { DailyForecastData("2026-04-${21+it}", 0, 15.0, 25.0 + it, "2026-04-20T06:30", "2026-04-20T20:15", uvIndexMax = 5.0 + it) }
    DailyForecastRow(forecasts = forecasts, prefs = WeatherPrefs(showSunriseSunset = true, showUvIndex = true))
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewSectionTimeDisplayCompact() {
    SectionTimeDisplay(LocalContext.current, "UTC", AppLanguage.EN, is24h = false, compact = true)
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewSectionTimeDisplay() {
    SectionTimeDisplay(LocalContext.current, "UTC", AppLanguage.EN, is24h = true, compact = false)
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewSectionControlsGps() {
    SectionControls(
        ctx = LocalContext.current,
        state = WeatherUiState(permissionGranted = true, locationInfo = "San Francisco, US\n(37.77, -122.41)", isRefreshing = false),
        prefs = WeatherPrefs(useGps = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewSectionControlsMapMode() {
    SectionControls(
        ctx = LocalContext.current,
        state = WeatherUiState(permissionGranted = true, locationInfo = "Tokyo, Japan\n(35.69, 139.69)", isRefreshing = false),
        prefs = WeatherPrefs(useGps = false),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3, widthDp = 360, heightDp = 240)
@Composable
fun PreviewMiniWorldMap() {
    MiniWorldMap(
        ctx = LocalContext.current,
        state = WeatherUiState(currentLat = 37.77, currentLon = -122.41),
        onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewWeatherSection() {
    SectionTimeWeather(LocalContext.current, WeatherUiState(weatherCode = 0, isDay = 1, temperature = 25.0), WeatherPrefs(is24Hour = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF263238)
@Composable
fun PreviewWeatherSectionNight() {
    SectionTimeWeather(LocalContext.current, WeatherUiState(weatherCode = 0, isDay = 0, temperature = 18.0), WeatherPrefs(is24Hour = false))
}

@Preview(showBackground = true, backgroundColor = 0xFF546E7A)
@Composable
fun PreviewWeatherSectionRainy() {
    SectionTimeWeather(LocalContext.current, WeatherUiState(weatherCode = 61, isDay = 1, temperature = 12.0), WeatherPrefs())
}

@Preview(showBackground = true, backgroundColor = 0xFFB0BEC5)
@Composable
fun PreviewWeatherSectionSnowy() {
    SectionTimeWeather(LocalContext.current, WeatherUiState(weatherCode = 71, isDay = 1, temperature = -3.0), WeatherPrefs())
}

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewWeatherSectionWithBadgesCompact() {
    SectionTimeWeather(
        LocalContext.current,
        WeatherUiState(weatherCode = 0, isDay = 1, temperature = 25.0, currentUvIndex = 6.5, currentAirQuality = 34),
        WeatherPrefs(is24Hour = false, showUvIndex = true, showAirQuality = true),
        compact = true
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewVerticalLayout() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${14+it}:00", 0, 1, 20.0 + it, uvIndex = (4.0 + it * 0.5)) }
    val mockAqiMap = mockHourly.mapIndexed { i, h -> h.isoTime to (25 + i * 5) }.toMap()
    val mockDaily = List(5) { DailyForecastData("2026-04-${21+it}", 0, 15.0, 25.0 + it, "2026-04-20T06:30", "2026-04-20T20:15", uvIndexMax = 5.0 + it) }
    VerticalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(temperature = 22.0, locationInfo = "Mountain View, US\n(37.42, -122.08)", currentLat = 37.42, currentLon = -122.08, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily, currentUvIndex = 4.2, currentAirQuality = 55, aqiHourlyMap = mockAqiMap),
        prefs = WeatherPrefs(is24Hour = false, showUvIndex = true, showAirQuality = true, useGps = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, backgroundColor = 0xFF263238)
@Composable
fun PreviewVerticalLayoutNight() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${20+it}:00", 0, 0, 15.0 + it) }
    val mockDaily = List(3) { DailyForecastData("2026-04-${21+it}", 0, 10.0, 18.0, "2026-04-20T06:30", "2026-04-20T20:15") }
    VerticalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(weatherCode = 0, isDay = 0, temperature = 15.0, locationInfo = "Paris, France\n(48.85, 2.35)", currentLat = 48.85, currentLon = 2.35, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily),
        prefs = WeatherPrefs(is24Hour = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, backgroundColor = 0xFF546E7A)
@Composable
fun PreviewVerticalLayoutRainy() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${14+it}:00", 61, 1, 12.0 + it) }
    val mockDaily = List(3) { DailyForecastData("2026-04-${21+it}", 61, 8.0, 14.0, "2026-04-20T06:45", "2026-04-20T19:30") }
    VerticalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(weatherCode = 61, isDay = 1, temperature = 12.0, locationInfo = "London, UK\n(51.51, -0.13)", currentLat = 51.51, currentLon = -0.13, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily),
        prefs = WeatherPrefs(showSunriseSunset = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, backgroundColor = 0xFFB0BEC5)
@Composable
fun PreviewVerticalLayoutSnowy() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${10+it}:00", 71, 1, -5.0 + it * 0.5) }
    val mockDaily = List(3) { DailyForecastData("2026-04-${21+it}", 71, -8.0, -2.0, "2026-04-20T07:00", "2026-04-20T17:00") }
    VerticalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(weatherCode = 71, isDay = 1, temperature = -4.0, locationInfo = "Oslo, Norway\n(59.91, 10.75)", currentLat = 59.91, currentLon = 10.75, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily),
        prefs = WeatherPrefs(isCelsius = true, showSunriseSunset = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewHorizontalLayout() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${14+it}:00", 0, 1, 20.0 + it, uvIndex = (4.0 + it * 0.5)) }
    val mockAqiMap = mockHourly.mapIndexed { i, h -> h.isoTime to (25 + i * 5) }.toMap()
    val mockDaily = List(5) { DailyForecastData("2026-04-${21+it}", 0, 15.0, 25.0 + it, "2026-04-20T06:30", "2026-04-20T20:15", uvIndexMax = 5.0 + it) }
    HorizontalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(temperature = 22.0, locationInfo = "Mountain View, US\n(37.42, -122.08)", currentLat = 37.42, currentLon = -122.08, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily, currentUvIndex = 4.2, currentAirQuality = 55, aqiHourlyMap = mockAqiMap),
        prefs = WeatherPrefs(is24Hour = false, showUvIndex = true, showAirQuality = true, useGps = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, backgroundColor = 0xFF546E7A)
@Composable
fun PreviewHorizontalLayoutRainy() {
    val mockHourly = List(6) { HourlyForecastData("2026-04-20T${14+it}:00", 61, 1, 12.0 + it) }
    val mockDaily = List(5) { DailyForecastData("2026-04-${21+it}", 61, 8.0, 14.0, "2026-04-20T06:45", "2026-04-20T19:30") }
    HorizontalLayout(
        ctx = LocalContext.current,
        state = WeatherUiState(weatherCode = 61, isDay = 1, temperature = 12.0, locationInfo = "London, UK\n(51.51, -0.13)", currentLat = 51.51, currentLon = -0.13, permissionGranted = true, hourlyForecasts = mockHourly, dailyForecasts = mockDaily),
        prefs = WeatherPrefs(showSunriseSunset = true),
        onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
    )
}

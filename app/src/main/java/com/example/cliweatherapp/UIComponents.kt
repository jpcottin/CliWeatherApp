package com.example.cliweatherapp

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import java.util.Date
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    isCelsius: Boolean, onCelsiusChange: (Boolean) -> Unit,
    currentLanguage: AppLanguage, onLanguageChange: (AppLanguage) -> Unit,
    useGps: Boolean, onGpsChange: (Boolean) -> Unit,
    is24Hour: Boolean, on24HourChange: (Boolean) -> Unit,
    hourlyRange: Int, onHourlyRangeChange: (Int) -> Unit,
    dailyRange: Int, onDailyRangeChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = getLocalizedContext(LocalContext.current, currentLanguage)
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)) {
            Column(modifier = Modifier.padding(24.dp).verticalScroll(rememberScrollState())) {
                Text(ctx.getString(R.string.settings), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.unit), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium); Text("°C"); Switch(checked = !isCelsius, onCheckedChange = { onCelsiusChange(!it) }, modifier = Modifier.scale(0.8f).testTag("switch_unit")); Text("°F")
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.time_format), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium); Text("12h"); Switch(checked = is24Hour, onCheckedChange = { on24HourChange(it) }, modifier = Modifier.scale(0.8f).testTag("switch_time")); Text("24h")
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                var expanded by remember { mutableStateOf(false) }
                Text(ctx.getString(R.string.language), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                    OutlinedTextField(value = currentLanguage.label, onValueChange = {}, readOnly = true, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth().testTag("dropdown_lang"), textStyle = LocalTextStyle.current.copy(fontSize = 14.sp))
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        AppLanguage.values().forEach { lang -> DropdownMenuItem(text = { Text(lang.label) }, onClick = { onLanguageChange(lang); expanded = false }) }
                    }
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(ctx.getString(R.string.loc_mode), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(ctx.getString(R.string.gps), fontSize = 12.sp, color = if(useGps) MaterialTheme.colorScheme.primary else Color.Gray)
                    Switch(checked = !useGps, onCheckedChange = { onGpsChange(!it) }, modifier = Modifier.scale(0.8f).testTag("switch_gps"))
                    Text(ctx.getString(R.string.map_click), fontSize = 12.sp, color = if(!useGps) MaterialTheme.colorScheme.primary else Color.Gray)
                }
                Spacer(Modifier.height(16.dp)); HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f)); Spacer(Modifier.height(16.dp))

                // Hourly Range Slider
                Text(String.format(ctx.getString(R.string.hourly_range_label), hourlyRange), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = hourlyRange.toFloat(),
                    onValueChange = { onHourlyRangeChange(it.toInt()) },
                    valueRange = 0f..168f,
                    steps = 167,
                    modifier = Modifier.testTag("slider_hourly")
                )
                Spacer(Modifier.height(8.dp))

                // Daily Range Slider
                Text(String.format(ctx.getString(R.string.daily_range_label), dailyRange), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary)
                Slider(
                    value = dailyRange.toFloat(),
                    onValueChange = { onDailyRangeChange(it.toInt()) },
                    valueRange = 0f..16f,
                    steps = 15,
                    modifier = Modifier.testTag("slider_daily")
                )

                Spacer(Modifier.height(24.dp)); Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
            }
        }
    }
}

data class HourlyForecastData(val isoTime: String, val code: Int, val isDay: Int, val temp: Double)
data class DailyForecastData(val isoDate: String, val code: Int, val minTemp: Double, val maxTemp: Double, val sunrise: String, val sunset: String)

@Composable
fun HourlyForecastRow(forecasts: List<HourlyForecastData>, isC: Boolean, lang: AppLanguage, is24h: Boolean) {
    LazyRow(modifier = Modifier.testTag("hourly_forecast_list"), contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecasts) { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatForecastHour(item.isoTime, lang.locale, is24h), fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Icon(getWeatherIcon(item.code, item.isDay), null, Modifier.size(32.dp), Color.White)
                Spacer(Modifier.height(4.dp))
                Text("${String.format("%.0f", convertTemperature(item.temp, isC))}°", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

@Composable
fun DailyForecastRow(forecasts: List<DailyForecastData>, isC: Boolean, lang: AppLanguage, is24h: Boolean) {
    LazyRow(contentPadding = PaddingValues(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(forecasts) { item ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(formatForecastDay(item.isoDate, lang.locale), fontSize = 12.sp, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Icon(getWeatherIcon(item.code, 1), null, Modifier.size(32.dp), Color.White)
                Spacer(Modifier.height(4.dp))
                Text("${String.format("%.0f", convertTemperature(item.minTemp, isC))}° / ${String.format("%.0f", convertTemperature(item.maxTemp, isC))}°", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.WbSunny, null, Modifier.size(10.dp), Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(2.dp))
                    Text(formatSunriseSunset(item.sunrise, lang.locale, is24h), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.NightlightRound, null, Modifier.size(10.dp), Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.width(2.dp))
                    Text(formatSunriseSunset(item.sunset, lang.locale, is24h), fontSize = 10.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }
    }
}

@Composable
fun VerticalLayout(timezoneId: String?, is24h: Boolean, perm: Boolean, code: Int, day: Int, temp: Double?, isC: Boolean, locationInfo: String, lat: Double, lon: Double, useGps: Boolean, error: String?, lang: AppLanguage, hourly: List<HourlyForecastData>, daily: List<DailyForecastData>, isRefreshing: Boolean, onRef: () -> Unit, onSpeak: () -> Unit, onPerm: () -> Unit, onMap: (Double, Double) -> Unit) {
    val ctx = getLocalizedContext(LocalContext.current, lang)
    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        SectionTimeWeather(code, day, temp, isC, lang, timezoneId, is24h)
        if (hourly.isNotEmpty()) HourlyForecastRow(hourly, isC, lang, is24h)
        if (daily.isNotEmpty()) DailyForecastRow(daily, isC, lang, is24h)
        SectionControls(perm, locationInfo, useGps, lang, isRefreshing, onRef, onSpeak, onPerm)
        MiniWorldMap(lat, lon, error, lang, onMap, Modifier.height(220.dp).fillMaxWidth().padding(horizontal = 16.dp))
    }
}

@Composable
fun HorizontalLayout(timezoneId: String?, is24h: Boolean, perm: Boolean, code: Int, day: Int, temp: Double?, isC: Boolean, locationInfo: String, lat: Double, lon: Double, useGps: Boolean, error: String?, lang: AppLanguage, hourly: List<HourlyForecastData>, daily: List<DailyForecastData>, isRefreshing: Boolean, onRef: () -> Unit, onSpeak: () -> Unit, onPerm: () -> Unit, onMap: (Double, Double) -> Unit, onOpenSettings: () -> Unit) {
    val ctx = getLocalizedContext(LocalContext.current, lang)
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

    Row(modifier = Modifier.fillMaxSize().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(0.4f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SectionTimeWeather(code, day, temp, isC, lang, timezoneId, is24h)
            if (hourly.isNotEmpty()) HourlyForecastRow(hourly, isC, lang, is24h)
            if (daily.isNotEmpty()) DailyForecastRow(daily, isC, lang, is24h)
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Settings, "Settings", tint = Color.White) }
        }
        Column(modifier = Modifier.weight(0.6f).fillMaxHeight().padding(start = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            MiniWorldMap(lat, lon, error, lang, onMap, Modifier.weight(0.7f).fillMaxWidth())
            Column(modifier = Modifier.weight(0.3f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(text = locationInfo.substringBefore("\n"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
                Text(text = locationInfo.substringAfter("\n", ""), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.testTag("location_coords"))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (useGps) {
                        Button(onClick = onRef, enabled = !isRefreshing, modifier = Modifier.height(32.dp).testTag("btn_refresh")) {
                            Icon(
                                Icons.Rounded.Refresh,
                                null,
                                Modifier.size(16.dp).graphicsLayer { rotationZ = if (isRefreshing) rotation else 0f },
                                tint = Color.White
                            )
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

@Composable
fun SectionTimeDisplay(timezoneId: String?, lang: AppLanguage, is24h: Boolean) {
    var currentTime by remember(timezoneId, lang, is24h) { mutableStateOf(formatTime(Date(), timezoneId, lang.locale, is24h)) }
    val ctx = getLocalizedContext(LocalContext.current, lang)

    LaunchedEffect(timezoneId, lang, is24h) {
        while (true) {
            currentTime = formatTime(Date(), timezoneId, lang.locale, is24h)
            delay(1000)
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(ctx.getString(R.string.current_time), fontSize = 14.sp, color = Color.White.copy(alpha = 0.8f))
        Row(verticalAlignment = Alignment.Bottom) {
            val digits = if (!is24h) currentTime.substringBeforeLast(" ") else currentTime
            val suffix = if (!is24h) currentTime.substringAfterLast(" ") else ""
            Text(digits, fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (suffix.isNotEmpty()) {
                Text(" $suffix", fontSize = 18.sp, fontWeight = FontWeight.Medium, color = Color.White.copy(alpha = 0.9f), modifier = Modifier.padding(bottom = 8.dp))
            }
        }
    }
}

@Composable
fun SectionTimeWeather(code: Int, day: Int, temp: Double?, isC: Boolean, lang: AppLanguage, timezoneId: String?, is24h: Boolean) {
    val ctx = getLocalizedContext(LocalContext.current, lang)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        SectionTimeDisplay(timezoneId, lang, is24h)
        Spacer(Modifier.height(16.dp))
        Icon(getWeatherIcon(code, day), null, Modifier.size(100.dp), Color.White)
        Box(modifier = Modifier.testTag("current_temp"), contentAlignment = Alignment.Center) {
            if (temp != null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${String.format("%.1f", convertTemperature(temp, isC))}${if(isC) "°C" else "°F"}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text(getConditionString(ctx, code), fontSize = 20.sp, color = Color.White.copy(alpha = 0.9f))
                }
            } else { Text(ctx.getString(R.string.loading), color = Color.White) }
        }
    }
}

@Composable
fun SectionControls(perm: Boolean, loc: String, useGps: Boolean, lang: AppLanguage, isRefreshing: Boolean, onRef: () -> Unit, onSpeak: () -> Unit, onPerm: () -> Unit) {
    val ctx = getLocalizedContext(LocalContext.current, lang)
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

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (!perm) { Button(onClick = onPerm) { Text(ctx.getString(R.string.grant_permission)) } }
        else {
            Text(ctx.getString(R.string.location_label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Text(loc.substringBefore("\n"), fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White, textAlign = TextAlign.Center, maxLines = 1)
            Text(loc.substringAfter("\n", ""), fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.testTag("location_coords"))
            Spacer(Modifier.height(12.dp))
            Row {
                if (useGps) {
                    Button(onClick = onRef, enabled = !isRefreshing, modifier = Modifier.testTag("btn_refresh")) {
                        Icon(
                            Icons.Rounded.Refresh,
                            null,
                            Modifier.size(18.dp).graphicsLayer { rotationZ = if (isRefreshing) rotation else 0f },
                            tint = Color.White
                        )
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
fun MiniWorldMap(lat: Double, lon: Double, error: String?, lang: AppLanguage, onMap: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val ctx = getLocalizedContext(LocalContext.current, lang)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Box(modifier = Modifier.height(20.dp)) {
            if (error != null) Text(error, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            else Text(ctx.getString(R.string.map_label), fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
        }
        Spacer(Modifier.height(4.dp))
        Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color.Black).testTag("world_map").pointerInput(Unit) {
            detectTapGestures { offset ->
                val flat = 90 - (offset.y / size.height) * 180
                val flon = (offset.x / size.width) * 360 - 180
                onMap(flat.toDouble(), flon.toDouble())
            }
        }) {
            Image(painter = painterResource(id = R.drawable.world_map), contentDescription = "World Map", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds)
            Canvas(modifier = Modifier.fillMaxSize()) {
                fun mapX(l: Double) = ((l + 180) / 360f) * size.width
                fun mapY(l: Double) = ((90 - l) / 180f) * size.height
                val x = mapX(lon); val y = mapY(lat); val cs = 12f
                drawLine(Color.Red, Offset(x.toFloat()-cs, y.toFloat()-cs), Offset(x.toFloat()+cs, y.toFloat()+cs), 4f)
                drawLine(Color.Red, Offset(x.toFloat()+cs, y.toFloat()-cs), Offset(x.toFloat()-cs, y.toFloat()+cs), 4f)
            }
        }
    }
}

// --- Previews ---

@Preview(showBackground = true, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewWeatherSection() {
    SectionTimeWeather(code = 0, day = 1, temp = 25.0, isC = true, lang = AppLanguage.EN, timezoneId = "UTC", is24h = false)
}

@Preview(showBackground = true, widthDp = 400, heightDp = 800, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewVerticalLayout() {
    VerticalLayout(
        timezoneId = "UTC", is24h = false, perm = true, code = 0, day = 1, temp = 22.0, isC = true,
        locationInfo = "Mountain View, United States\n(37.42, -122.08)", lat = 37.42, lon = -122.08,
        useGps = true, error = null, lang = AppLanguage.EN, hourly = emptyList(), daily = emptyList(),
        isRefreshing = false, onRef = {}, onSpeak = {}, onPerm = {}, onMap = {_, _ ->}
    )
}

@Preview(showBackground = true, widthDp = 800, heightDp = 400, backgroundColor = 0xFF2196F3)
@Composable
fun PreviewHorizontalLayout() {
    HorizontalLayout(
        timezoneId = "UTC", is24h = false, perm = true, code = 0, day = 1, temp = 22.0, isC = true,
        locationInfo = "Mountain View, United States\n(37.42, -122.08)", lat = 37.42, lon = -122.08,
        useGps = true, error = null, lang = AppLanguage.EN, hourly = emptyList(), daily = emptyList(),
        isRefreshing = false, onRef = {}, onSpeak = {}, onPerm = {}, onMap = {_, _ ->}, onOpenSettings = {}
    )
}

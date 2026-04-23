package com.example.cliweatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.Text
import java.util.Locale

class GlassesWeatherActivity : ComponentActivity() {
    companion object {
        const val EXTRA_WEATHER_TEXT = "weather_text"
        const val EXTRA_WEATHER_CODE = "weather_code"
        const val EXTRA_IS_DAY = "is_day"
        const val EXTRA_TEMPERATURE = "temperature"
        const val EXTRA_IS_CELSIUS = "is_celsius"
        const val EXTRA_CITY = "city"
        const val EXTRA_CONDITION = "condition"
        const val EXTRA_IS_24_HOUR = "is_24_hour"
        const val EXTRA_HOURLY_CODES = "hourly_codes"
        const val EXTRA_HOURLY_IS_DAY = "hourly_is_day"
        const val EXTRA_HOURLY_TEMPS = "hourly_temps"
        const val EXTRA_HOURLY_TIMES = "hourly_times"
        const val EXTRA_LANGUAGE_TAG = "language_tag"
    }

    private lateinit var audioInterface: AudioInterface

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val weatherText = intent.getStringExtra(EXTRA_WEATHER_TEXT) ?: ""
        val weatherCode = intent.getIntExtra(EXTRA_WEATHER_CODE, 0)
        val isDay = intent.getIntExtra(EXTRA_IS_DAY, 1)
        val temperature = intent.getDoubleExtra(EXTRA_TEMPERATURE, 0.0)
        val isCelsius = intent.getBooleanExtra(EXTRA_IS_CELSIUS, true)
        val city = intent.getStringExtra(EXTRA_CITY) ?: ""
        val condition = intent.getStringExtra(EXTRA_CONDITION) ?: ""
        val is24Hour = intent.getBooleanExtra(EXTRA_IS_24_HOUR, true)
        val hourlyCodes = intent.getIntArrayExtra(EXTRA_HOURLY_CODES) ?: IntArray(0)
        val hourlyIsDays = intent.getIntArrayExtra(EXTRA_HOURLY_IS_DAY) ?: IntArray(0)
        val hourlyTemps = intent.getDoubleArrayExtra(EXTRA_HOURLY_TEMPS) ?: DoubleArray(0)
        val hourlyTimes = intent.getStringArrayListExtra(EXTRA_HOURLY_TIMES) ?: emptyList<String>()
        val locale = Locale.forLanguageTag(
            intent.getStringExtra(EXTRA_LANGUAGE_TAG) ?: Locale.ENGLISH.toLanguageTag()
        )
        val localizedCtx = run {
            val config = resources.configuration
            config.setLocale(locale)
            createConfigurationContext(config)
        }
        val hourlyItems = hourlyCodes.indices.map { i ->
            HourlyItem(
                isoTime = hourlyTimes.getOrNull(i) ?: "",
                code = hourlyCodes[i],
                isDay = hourlyIsDays.getOrElse(i) { 1 },
                temp = hourlyTemps.getOrElse(i) { 0.0 }
            )
        }

        val goodbyeText = localizedCtx.getString(R.string.goodbye)
        val closeLabel = localizedCtx.getString(R.string.close)

        audioInterface = AudioInterface(this, weatherText, locale)
        lifecycle.addObserver(audioInterface)

        setContent {
            GlimmerTheme {
                WeatherGlassesScreen(
                    weatherCode = weatherCode,
                    isDay = isDay,
                    temperature = temperature,
                    isCelsius = isCelsius,
                    city = city,
                    condition = condition,
                    hourlyItems = hourlyItems,
                    is24Hour = is24Hour,
                    closeLabel = closeLabel,
                    onClose = {
                        audioInterface.speak(goodbyeText)
                        finish()
                    }
                )
            }
        }
    }
}

data class HourlyItem(val isoTime: String, val code: Int, val isDay: Int, val temp: Double)

@Preview(
    name = "Glasses Weather - sunny day",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true
)
@Composable
fun WeatherGlassesScreenPreview() {
    GlimmerTheme {
        WeatherGlassesScreen(
            weatherCode = 0,
            isDay = 1,
            temperature = 22.5,
            isCelsius = true,
            city = "Paris",
            condition = "Clear sky",
            hourlyItems = List(12) { i ->
                HourlyItem("2026-04-20T${(14 + i).toString().padStart(2, '0')}:00",
                    listOf(0, 1, 2, 61, 71, 0)[i % 6], if (i < 8) 1 else 0, 22.5 - i * 0.5)
            },
            is24Hour = true,
            closeLabel = "Close",
            onClose = {}
        )
    }
}

@Composable
fun WeatherGlassesScreen(
    weatherCode: Int,
    isDay: Int,
    temperature: Double,
    isCelsius: Boolean,
    city: String,
    condition: String,
    hourlyItems: List<HourlyItem>,
    is24Hour: Boolean,
    closeLabel: String,
    onClose: () -> Unit
) {
    val displayTemp = convertTemperature(temperature, isCelsius)
    val unit = if (isCelsius) "C" else "F"
    val tempStr = String.format("%.1f°$unit", displayTemp)

    val displayCount = 5  // hours shown at once
    val swipeStep = 4     // advance by 4 so the last hour of each page is the first of the next
    var hourlyOffset by remember { mutableIntStateOf(0) }
    val maxOffset = (hourlyItems.size - displayCount).coerceAtLeast(0)
    val totalPages = if (maxOffset == 0) 1 else maxOffset / swipeStep + 1
    val currentPage = hourlyOffset / swipeStep
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val draggableState = rememberDraggableState { delta ->
        dragAccum += delta
        when {
            // swipe right (delta > 0) → go back in time
            dragAccum > 80f && hourlyOffset > 0 -> {
                hourlyOffset = (hourlyOffset - swipeStep).coerceAtLeast(0)
                dragAccum = 0f
            }
            // swipe left (delta < 0) → go forward in time
            dragAccum < -80f && hourlyOffset < maxOffset -> {
                hourlyOffset = (hourlyOffset + swipeStep).coerceAtMost(maxOffset)
                dragAccum = 0f
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                onDragStopped = { dragAccum = 0f }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main weather card
            Card(
                leadingIcon = {
                    Icon(
                        imageVector = getWeatherIcon(weatherCode, isDay),
                        contentDescription = condition,
                        modifier = Modifier.size(GlimmerTheme.iconSizes.large),
                        tint = GlimmerTheme.colors.primary
                    )
                },
                title = { Text(tempStr, fontSize = 32.sp, fontWeight = FontWeight.Bold) },
                subtitle = { Text(condition, fontSize = 20.sp) },
                action = {
                    Button(onClick = onClose) { Text(closeLabel, fontSize = 18.sp) }
                }
            ) {
                Text(city, fontSize = 18.sp)
            }

            // Swipeable hourly forecast card (5 hours per page, swipe anywhere on screen)
            if (hourlyItems.isNotEmpty()) {
                Card {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            hourlyItems.drop(hourlyOffset).take(displayCount).forEach { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = formatForecastHour(item.isoTime, Locale.getDefault(), is24Hour),
                                        fontSize = 14.sp
                                    )
                                    Icon(
                                        imageVector = getWeatherIcon(item.code, item.isDay),
                                        contentDescription = null,
                                        modifier = Modifier.size(GlimmerTheme.iconSizes.medium),
                                        tint = GlimmerTheme.colors.primary
                                    )
                                    Text(
                                        text = "${String.format("%.0f", convertTemperature(item.temp, isCelsius))}°$unit",
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                        // Page dots
                        if (totalPages > 1) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                repeat(totalPages) { page ->
                                    Text(
                                        text = if (page == currentPage) "●" else "○",
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

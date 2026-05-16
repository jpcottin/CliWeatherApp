package com.example.cliweatherapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.ComposeUiFlags
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.xr.glimmer.Button
import androidx.xr.glimmer.Card
import androidx.xr.glimmer.CardDefaults
import androidx.xr.glimmer.GlimmerTheme
import androidx.xr.glimmer.Icon
import androidx.xr.glimmer.Text
import androidx.xr.glimmer.TitleChip
import androidx.xr.glimmer.TitleChipDefaults
import androidx.xr.glimmer.googlefonts.createGoogleSansFlexTypography
import androidx.xr.glimmer.stack.VerticalStack
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

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        ComposeUiFlags.isInitialFocusOnFocusableAvailable = true
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
        val forecastLabel = localizedCtx.getString(R.string.forecast)
        val pageFormat = localizedCtx.getString(R.string.page_format)

        audioInterface = AudioInterface(this, weatherText, locale)
        lifecycle.addObserver(audioInterface)

        setContent {
            GlimmerTheme(typography = createGoogleSansFlexTypography()) {
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
                    forecastLabel = forecastLabel,
                    pageFormat = pageFormat,
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
    GlimmerTheme(typography = createGoogleSansFlexTypography()) {
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
            forecastLabel = "Forecast",
            pageFormat = "Page %1\$d/%2\$d",
            onClose = {}
        )
    }
}

@Preview(
    name = "Glasses Weather - night",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true
)
@Composable
fun WeatherGlassesScreenNightPreview() {
    GlimmerTheme(typography = createGoogleSansFlexTypography()) {
        WeatherGlassesScreen(
            weatherCode = 0,
            isDay = 0,
            temperature = 14.0,
            isCelsius = true,
            city = "Tokyo",
            condition = "Clear sky",
            hourlyItems = List(6) { i ->
                HourlyItem("2026-04-20T${(20 + i).toString().padStart(2, '0')}:00", 0, 0, 14.0 - i * 0.3)
            },
            is24Hour = true,
            closeLabel = "Close",
            forecastLabel = "Forecast",
            pageFormat = "Page %1\$d/%2\$d",
            onClose = {}
        )
    }
}

@Preview(
    name = "Glasses Weather - no hourly",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true
)
@Composable
fun WeatherGlassesScreenNoHourlyPreview() {
    GlimmerTheme(typography = createGoogleSansFlexTypography()) {
        WeatherGlassesScreen(
            weatherCode = 2,
            isDay = 1,
            temperature = 19.0,
            isCelsius = true,
            city = "Berlin",
            condition = "Partly cloudy",
            hourlyItems = emptyList(),
            is24Hour = true,
            closeLabel = "Close",
            forecastLabel = "Forecast",
            pageFormat = "Page %1\$d/%2\$d",
            onClose = {}
        )
    }
}

@Preview(
    name = "Glasses Weather - Fahrenheit",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true
)
@Composable
fun WeatherGlassesScreenFahrenheitPreview() {
    GlimmerTheme(typography = createGoogleSansFlexTypography()) {
        WeatherGlassesScreen(
            weatherCode = 0,
            isDay = 1,
            temperature = 25.0,
            isCelsius = false,
            city = "New York",
            condition = "Clear sky",
            hourlyItems = List(6) { i ->
                HourlyItem("2026-04-20T${(14 + i).toString().padStart(2, '0')}:00", 0, 1, 25.0 - i * 0.5)
            },
            is24Hour = false,
            closeLabel = "Close",
            forecastLabel = "Forecast",
            pageFormat = "Page %1\$d/%2\$d",
            onClose = {}
        )
    }
}

@Preview(
    name = "Glasses Weather - multi-page",
    device = "spec:width=1280dp,height=800dp,dpi=240",
    showBackground = true
)
@Composable
fun WeatherGlassesScreenMultiPagePreview() {
    GlimmerTheme(typography = createGoogleSansFlexTypography()) {
        WeatherGlassesScreen(
            weatherCode = 61,
            isDay = 1,
            temperature = 12.0,
            isCelsius = true,
            city = "London",
            condition = "Light rain",
            hourlyItems = List(24) { i ->
                HourlyItem(
                    "2026-04-20T${(i % 24).toString().padStart(2, '0')}:00",
                    listOf(61, 63, 65, 61, 0, 1)[i % 6],
                    if (i in 6..20) 1 else 0,
                    12.0 - i * 0.1
                )
            },
            is24Hour = true,
            closeLabel = "Close",
            forecastLabel = "Forecast",
            pageFormat = "Page %1\$d/%2\$d",
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
    forecastLabel: String,
    pageFormat: String,
    onClose: () -> Unit
) {
    val displayTemp = convertTemperature(temperature, isCelsius)
    val unit = if (isCelsius) "C" else "F"
    val tempStr = String.format("%.1f°$unit", displayTemp)

    val displayCount = 7
    val swipeStep = 6
    val maxForecastOffset = (hourlyItems.size - displayCount).coerceAtLeast(0)
    val calculatedPages = if (maxForecastOffset <= 0) 1 else (maxForecastOffset + swipeStep - 1) / swipeStep + 1
    val totalPages = calculatedPages.coerceAtMost(4)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        VerticalStack(modifier = Modifier.fillMaxSize()) {
            item {
                Card(
                    modifier = Modifier.itemDecoration(CardDefaults.shape),
                    leadingIcon = {
                        Icon(
                            imageVector = getWeatherIcon(weatherCode, isDay),
                            contentDescription = condition,
                            modifier = Modifier.size(GlimmerTheme.iconSizes.large),
                            tint = GlimmerTheme.colors.primary
                        )
                    },
                    title = { Text(tempStr, style = GlimmerTheme.typography.titleLarge) },
                    subtitle = { Text(condition, style = GlimmerTheme.typography.bodySmall) },
                    action = {
                        Button(onClick = onClose) {
                            Text(closeLabel, style = GlimmerTheme.typography.caption)
                        }
                    }
                ) {
                    Text(city, style = GlimmerTheme.typography.caption)
                }
            }

            if (hourlyItems.isNotEmpty()) {
                items(totalPages) { pageIndex ->
                    val offset = (pageIndex * swipeStep).coerceAtMost(maxForecastOffset)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(TitleChipDefaults.associatedContentSpacing)
                    ) {
                        TitleChip {
                            Text(forecastLabel)
                        }

                        Card(
                            modifier = Modifier.itemDecoration(CardDefaults.shape),
                            subtitle = {
                                Text(
                                    String.format(pageFormat, pageIndex + 1, totalPages),
                                    style = GlimmerTheme.typography.caption
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                hourlyItems.drop(offset).take(displayCount).forEach { item ->
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = formatForecastHour(item.isoTime, Locale.getDefault(), is24Hour),
                                            style = GlimmerTheme.typography.caption
                                        )
                                        Icon(
                                            imageVector = getWeatherIcon(item.code, item.isDay),
                                            contentDescription = null,
                                            modifier = Modifier.size(GlimmerTheme.iconSizes.medium),
                                            tint = GlimmerTheme.colors.primary
                                        )
                                        Text(
                                            text = "${String.format("%.0f", convertTemperature(item.temp, isCelsius))}°$unit",
                                            style = GlimmerTheme.typography.caption
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
}

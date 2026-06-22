// NOTE: the Phone/Foldable/Tablet goldens embed the raster world map, whose
// native downscaling is not bit-identical across rendering environments. Their
// reference images are recorded on the CI runner (ubuntu-latest) so they match
// the environment that validates them. If you change these previews, regenerate
// the references on CI rather than locally, or `validateDebugScreenshotTest`
// will fail in the pipeline. The component-only previews are host-portable.
package com.jpcottin.weatherglance

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest

// Shared sample data for the layout previews so phone/foldable/tablet stay
// visually comparable across reference images.
private fun sampleHourly() =
    List(6) { HourlyForecastData("2026-04-20T${14 + it}:00", 0, 1, 20.0 + it, uvIndex = 4.0 + it * 0.5) }

private fun sampleAqi(hourly: List<HourlyForecastData>) =
    hourly.mapIndexed { i, h -> h.isoTime to (25 + i * 5) }.toMap()

private fun sampleDaily() =
    List(5) { DailyForecastData("2026-04-${21 + it}", 0, 15.0, 25.0 + it, "2026-04-20T06:30", "2026-04-20T20:15", uvIndexMax = 5.0 + it) }

private fun sampleState(): WeatherUiState {
    val hourly = sampleHourly()
    return WeatherUiState(
        weatherCode = 0,
        isDay = 1,
        temperature = 22.0,
        locationInfo = "Mountain View, US\n(37.42, -122.08)",
        currentLat = 37.42,
        currentLon = -122.08,
        permissionGranted = true,
        hourlyForecasts = hourly,
        dailyForecasts = sampleDaily(),
        currentUvIndex = 4.2,
        currentAirQuality = 55,
        aqiHourlyMap = sampleAqi(hourly),
    )
}

private val samplePrefs = WeatherPrefs(
    is24Hour = false,
    showUvIndex = true,
    showAirQuality = true,
    showSunriseSunset = true,
    useGps = true,
)

// The portrait layout, rendered at phone and foldable canvases. The app picks
// VerticalLayout for compact widths; the foldable preview confirms it still
// composes cleanly on a larger, near-square window.
@PreviewTest
@Preview(name = "Phone", device = Devices.PHONE, showBackground = true)
@Preview(name = "Foldable", device = Devices.FOLDABLE, showBackground = true)
@Composable
fun WeatherVerticalScreenshot() {
    MaterialTheme {
        VerticalLayout(
            ctx = LocalContext.current,
            state = sampleState(),
            prefs = samplePrefs,
            onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> },
        )
    }
}

// The expanded layout the app uses on tablets / unfolded foldables in landscape.
@PreviewTest
@Preview(name = "Tablet", device = Devices.TABLET, showBackground = true)
@Composable
fun WeatherHorizontalScreenshot() {
    MaterialTheme {
        HorizontalLayout(
            ctx = LocalContext.current,
            state = sampleState(),
            prefs = samplePrefs,
            onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> },
        )
    }
}

@PreviewTest
@Preview(name = "Hourly", widthDp = 400, showBackground = true)
@Composable
fun HourlyForecastRowScreenshot() {
    val hourly = sampleHourly()
    MaterialTheme {
        HourlyForecastRow(
            forecasts = hourly,
            prefs = WeatherPrefs(showUvIndex = true, showAirQuality = true),
            aqiMap = sampleAqi(hourly),
        )
    }
}

@PreviewTest
@Preview(name = "Daily", widthDp = 400, showBackground = true)
@Composable
fun DailyForecastRowScreenshot() {
    MaterialTheme {
        DailyForecastRow(
            forecasts = sampleDaily(),
            prefs = WeatherPrefs(showSunriseSunset = true, showUvIndex = true),
        )
    }
}

@PreviewTest
@Preview(name = "Settings", widthDp = 400, heightDp = 800, showBackground = true)
@Composable
fun SettingsDialogScreenshot() {
    MaterialTheme {
        SettingsDialog(
            prefs = WeatherPrefs(),
            onIsCelsiusChange = {}, onLanguageChange = {}, onUseGpsChange = {},
            on24HourChange = {}, onHourlyRangeChange = {}, onDailyRangeChange = {},
            onShowSunriseSunsetChange = {}, onShowUvIndexChange = {}, onShowAirQualityChange = {},
            onDismiss = {},
        )
    }
}

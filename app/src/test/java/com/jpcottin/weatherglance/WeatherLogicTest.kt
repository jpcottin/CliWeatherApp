package com.jpcottin.weatherglance

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.util.*

class WeatherLogicTest {

    @Test
    fun testDayNightIcons() {
        // Clear Sky Day
        assertEquals(Icons.Rounded.WbSunny, getWeatherIcon(0, 1))
        // Clear Sky Night
        assertEquals(Icons.Rounded.NightsStay, getWeatherIcon(0, 0))
        
        // Cloudy Day
        assertEquals(Icons.Rounded.Cloud, getWeatherIcon(1, 1))
        // Cloudy Night
        assertEquals(Icons.Rounded.CloudQueue, getWeatherIcon(1, 0))
    }

    @Test
    fun testDayNightBackgrounds() {
        // Night should always be blue regardless of code (returning resource IDs now)
        assertEquals(R.color.night_blue, getBackgroundColors(0, 0)[0])
        assertEquals(R.color.night_blue, getBackgroundColors(95, 0)[0])
        
        // Day should change with code
        assertEquals(R.color.sunny_orange, getBackgroundColors(0, 1)[0])
    }

    @Test
    fun testTemperatureConversion() {
        // 0C to 32F
        assertEquals(32.0, convertTemperature(0.0, false), 0.1)
        // 0C to 0C
        assertEquals(0.0, convertTemperature(0.0, true), 0.1)
    }

    @Test
    fun testTimeFormatting() {
        val date = Date(1712745600000L) // Fixed date
        // 24h format
        val result24 = formatTime(date, "UTC", Locale.ENGLISH, true)
        assertEquals("10:40:00", result24)
        
        // 12h format
        val result12 = formatTime(date, "UTC", Locale.ENGLISH, false)
        assertEquals("10:40:00 AM", result12)
    }

    @Test
    fun testFormatForecastHour() {
        val isoTime = "2024-04-11T14:00"
        assertEquals("14:00", formatForecastHour(isoTime, Locale.ENGLISH, true))
        assertEquals("2 PM", formatForecastHour(isoTime, Locale.ENGLISH, false))
    }

    @Test
    fun testFormatForecastDay() {
        val isoDate = "2024-04-11" // Thursday
        assertEquals("Thu", formatForecastDay(isoDate, Locale.ENGLISH))
        assertEquals("Jeu", formatForecastDay(isoDate, Locale.FRENCH).take(3)) // Accommodating small variations in Java locales
    }

    @Test
    fun testUvColorScale() {
        // The colour follows the whole number the badge shows (half-up), not the raw value.
        assertEquals(Color(0xFF66BB6A), uvColor(0.0))   // low (< 3)
        assertEquals(Color(0xFF66BB6A), uvColor(2.4))
        assertEquals(Color(0xFFFDD835), uvColor(2.6))   // shown as 3: moderate (3–5)
        assertEquals(Color(0xFFFDD835), uvColor(3.0))
        assertEquals(Color(0xFFFDD835), uvColor(5.4))
        assertEquals(Color(0xFFFFA726), uvColor(6.0))   // high (6–7)
        assertEquals(Color(0xFFFFA726), uvColor(7.4))
        assertEquals(Color(0xFFEF5350), uvColor(7.6))   // shown as 8: very high (8–10)
        assertEquals(Color(0xFFEF5350), uvColor(8.0))
        assertEquals(Color(0xFFEF5350), uvColor(10.4))
        assertEquals(8, uvLevel(7.6))
        assertEquals(2, uvLevel(2.4))
        assertEquals(Color(0xFFAB47BC), uvColor(11.0))  // extreme (≥ 11)
        assertEquals(Color(0xFFAB47BC), uvColor(15.0))
    }

    @Test
    fun testGetWeatherIconForShowers() {
        assertEquals(Icons.Rounded.WaterDrop, getWeatherIcon(80, 1))
        assertEquals(Icons.Rounded.WaterDrop, getWeatherIcon(81, 1))
        assertEquals(Icons.Rounded.WaterDrop, getWeatherIcon(82, 1))
        assertEquals(Icons.Rounded.WaterDrop, getWeatherIcon(80, 0))
    }

    @Test
    fun testGetWeatherIconFor95() {
        assertEquals(Icons.Rounded.Thunderstorm, getWeatherIcon(95, 1))
        assertEquals(Icons.Rounded.Thunderstorm, getWeatherIcon(95, 0))
    }

    // The WMO codes Open-Meteo's forecast endpoint documents.
    private val openMeteoWeatherCodes = listOf(
        0, 1, 2, 3, 45, 48, 51, 53, 55, 56, 57, 61, 63, 65, 66, 67,
        71, 73, 75, 77, 80, 81, 82, 85, 86, 95, 96, 99,
    )

    @Test
    fun everyCodeOpenMeteoServesHasAName() {
        for (code in openMeteoWeatherCodes) {
            assertNotEquals("code $code reads as unknown", R.string.unknown, conditionStringRes(code))
        }
        assertEquals(R.string.unknown, conditionStringRes(42))
    }

    @Test
    fun theFreezingSnowAndHailCodesReadAsTheirKind() {
        // Freezing drizzle and freezing rain are drizzle and rain; snow grains and snow
        // showers are snow; a thunderstorm with hail is a thunderstorm.
        for (code in listOf(56, 57)) {
            assertEquals(Icons.Rounded.WaterDrop, getWeatherIcon(code, 1))
            assertEquals(R.string.weather_drizzle, conditionStringRes(code))
        }
        for (code in listOf(66, 67)) {
            assertEquals(Icons.Rounded.Umbrella, getWeatherIcon(code, 1))
            assertEquals(R.string.weather_rainy, conditionStringRes(code))
        }
        for (code in listOf(77, 85, 86)) {
            assertEquals(Icons.Rounded.AcUnit, getWeatherIcon(code, 0))
            assertEquals(R.string.weather_snowy, conditionStringRes(code))
        }
        for (code in listOf(96, 99)) {
            assertEquals(Icons.Rounded.Thunderstorm, getWeatherIcon(code, 1))
            assertEquals(R.string.weather_storm, conditionStringRes(code))
        }
        assertEquals(Icons.Rounded.Air, getWeatherIcon(48, 1))
        assertEquals(R.string.weather_foggy, conditionStringRes(48))
    }

    @Test
    fun testGetBackgroundColorsForShowers() {
        assertEquals(R.color.rainy_blue, getBackgroundColors(80, 1)[0])
        assertEquals(R.color.rainy_blue, getBackgroundColors(81, 1)[0])
        assertEquals(R.color.rainy_blue, getBackgroundColors(82, 1)[0])
    }

    @Test
    fun testGetBackgroundColorsForStorm() {
        assertEquals(R.color.rainy_blue, getBackgroundColors(95, 1)[0])
    }

    @Test
    fun testFormatSunriseSunset24h() {
        val result = formatSunriseSunset("2026-04-20T06:30", Locale.ENGLISH, true)
        assertEquals("06:30", result)
    }

    @Test
    fun testFormatSunriseSunset12h() {
        val result = formatSunriseSunset("2026-04-20T18:45", Locale.ENGLISH, false)
        assertEquals("6:45 PM", result)
    }

    @Test
    fun testUvColorNegativeEdge() {
        // Negative UV treated same as < 3 (green)
        assertEquals(Color(0xFF66BB6A), uvColor(-1.0))
        assertEquals(Color(0xFF66BB6A), uvColor(-99.0))
    }

    @Test
    fun testAqiColorScale() {
        assertEquals(Color(0xFF66BB6A), aqiColor(0))    // good (< 20)
        assertEquals(Color(0xFF66BB6A), aqiColor(19))
        assertEquals(Color(0xFFD4E157), aqiColor(20))   // fair (20–39)
        assertEquals(Color(0xFFD4E157), aqiColor(39))
        assertEquals(Color(0xFFFDD835), aqiColor(40))   // moderate (40–59)
        assertEquals(Color(0xFFFDD835), aqiColor(59))
        assertEquals(Color(0xFFFFA726), aqiColor(60))   // poor (60–79)
        assertEquals(Color(0xFFFFA726), aqiColor(79))
        assertEquals(Color(0xFFEF5350), aqiColor(80))   // very poor (80–99)
        assertEquals(Color(0xFFEF5350), aqiColor(99))
        assertEquals(Color(0xFFAB47BC), aqiColor(100))  // extremely poor (≥ 100)
        assertEquals(Color(0xFFAB47BC), aqiColor(150))
    }
}

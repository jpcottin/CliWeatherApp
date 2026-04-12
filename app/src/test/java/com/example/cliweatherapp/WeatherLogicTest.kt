package com.example.cliweatherapp

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudQueue
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material.icons.rounded.WbSunny
import org.junit.Assert.assertEquals
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
}

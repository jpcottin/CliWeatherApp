package com.example.cliweatherapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GlassesWeatherTest {

    // --- Intent extra key constants ---

    @Test
    fun intentExtraKeys_haveExpectedValues() {
        assertEquals("weather_text",  GlassesWeatherActivity.EXTRA_WEATHER_TEXT)
        assertEquals("weather_code",  GlassesWeatherActivity.EXTRA_WEATHER_CODE)
        assertEquals("is_day",        GlassesWeatherActivity.EXTRA_IS_DAY)
        assertEquals("temperature",   GlassesWeatherActivity.EXTRA_TEMPERATURE)
        assertEquals("is_celsius",    GlassesWeatherActivity.EXTRA_IS_CELSIUS)
        assertEquals("city",          GlassesWeatherActivity.EXTRA_CITY)
        assertEquals("condition",     GlassesWeatherActivity.EXTRA_CONDITION)
        assertEquals("is_24_hour",    GlassesWeatherActivity.EXTRA_IS_24_HOUR)
        assertEquals("hourly_codes",  GlassesWeatherActivity.EXTRA_HOURLY_CODES)
        assertEquals("hourly_is_day", GlassesWeatherActivity.EXTRA_HOURLY_IS_DAY)
        assertEquals("hourly_temps",  GlassesWeatherActivity.EXTRA_HOURLY_TEMPS)
        assertEquals("hourly_times",  GlassesWeatherActivity.EXTRA_HOURLY_TIMES)
    }

    // --- HourlyItem construction from parallel arrays (mirrors GlassesWeatherActivity.onCreate) ---

    @Test
    fun hourlyItems_builtCorrectlyFromArrays() {
        val codes   = intArrayOf(0, 1, 61, 71)
        val isDays  = intArrayOf(1, 1, 0, 0)
        val temps   = doubleArrayOf(22.0, 20.5, 15.0, 10.0)
        val times   = listOf("2026-04-20T21:00", "2026-04-20T22:00",
                             "2026-04-20T23:00", "2026-04-21T00:00")

        val items = codes.indices.map { i ->
            HourlyItem(
                isoTime = times.getOrNull(i) ?: "",
                code    = codes[i],
                isDay   = isDays.getOrElse(i) { 1 },
                temp    = temps.getOrElse(i) { 0.0 }
            )
        }

        assertEquals(4, items.size)
        assertEquals(HourlyItem("2026-04-20T21:00", 0,  1, 22.0), items[0])
        assertEquals(HourlyItem("2026-04-20T22:00", 1,  1, 20.5), items[1])
        assertEquals(HourlyItem("2026-04-20T23:00", 61, 0, 15.0), items[2])
        assertEquals(HourlyItem("2026-04-21T00:00", 71, 0, 10.0), items[3])
    }

    @Test
    fun hourlyItems_limitedToFourByTake() {
        val many = List(10) { HourlyItem("2026-04-20T${it.toString().padStart(2, '0')}:00", 0, 1, 20.0) }
        assertEquals(4, many.take(4).size)
    }

    @Test
    fun hourlyItems_emptyWhenNoArrayData() {
        val codes = IntArray(0)
        val items = codes.indices.map { HourlyItem("", 0, 1, 0.0) }
        assertTrue(items.isEmpty())
    }

    @Test
    fun hourlyItems_fallbackWhenTimesListShorterThanCodes() {
        val codes  = intArrayOf(0, 1, 2)
        val isDays = intArrayOf(1, 1, 1)
        val temps  = doubleArrayOf(20.0, 19.0, 18.0)
        val times  = listOf("2026-04-20T21:00") // only one time for three codes

        val items = codes.indices.map { i ->
            HourlyItem(
                isoTime = times.getOrNull(i) ?: "",
                code    = codes[i],
                isDay   = isDays.getOrElse(i) { 1 },
                temp    = temps.getOrElse(i) { 0.0 }
            )
        }

        assertEquals(3, items.size)
        assertEquals("2026-04-20T21:00", items[0].isoTime)
        assertEquals("", items[1].isoTime) // fallback to empty string
        assertEquals("", items[2].isoTime)
    }

    // --- Temperature display format used in WeatherGlassesScreen ---

    @Test
    fun temperatureDisplay_celsius() {
        val display = convertTemperature(14.1, toCelsius = true)
        assertEquals("14.1°C", String.format("%.1f°C", display))
    }

    @Test
    fun temperatureDisplay_fahrenheit_freezing() {
        val display = convertTemperature(0.0, toCelsius = false)
        assertEquals("32.0°F", String.format("%.1f°F", display))
    }

    @Test
    fun temperatureDisplay_fahrenheit_body() {
        val display = convertTemperature(37.0, toCelsius = false)
        assertEquals("98.6°F", String.format("%.1f°F", display))
    }

    @Test
    fun hourlyTemperatureDisplay_roundedNoDecimal() {
        val display = convertTemperature(13.7, toCelsius = true)
        assertEquals("14°C", String.format("%.0f", display) + "°C")
    }

    // --- Hourly time formatting used in the forecast row ---

    @Test
    fun forecastHour_24h() {
        assertEquals("21:00", formatForecastHour("2026-04-20T21:00", Locale.ENGLISH, is24h = true))
    }

    @Test
    fun forecastHour_12h() {
        assertEquals("9 PM", formatForecastHour("2026-04-20T21:00", Locale.ENGLISH, is24h = false))
    }

    @Test
    fun forecastHour_midnight_24h() {
        assertEquals("00:00", formatForecastHour("2026-04-21T00:00", Locale.ENGLISH, is24h = true))
    }

    @Test
    fun forecastHour_midnight_12h() {
        assertEquals("12 AM", formatForecastHour("2026-04-21T00:00", Locale.ENGLISH, is24h = false))
    }
}

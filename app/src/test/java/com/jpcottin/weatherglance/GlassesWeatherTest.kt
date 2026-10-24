package com.jpcottin.weatherglance

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
        assertEquals("language_tag",  GlassesWeatherActivity.EXTRA_LANGUAGE_TAG)
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

    // --- Glasses forecast pager (displayCount=7, swipeStep=6, cap=4 pages) ---
    // glassesForecastPages / glassesForecastOffset are what WeatherGlassesScreen
    // feeds the Glimmer VerticalStack.

    @Test
    fun verticalStack_singleForecastPageWhenItemsFewerThanDisplayCount() {
        // Fewer items than displayCount=7 → maxOffset=0 → 1 forecast page
        assertEquals(1, glassesForecastPages(5))
        assertEquals(1, glassesForecastPages(7))
    }

    @Test
    fun verticalStack_twoForecastPagesFor12Items() {
        // 12 items: maxOffset=5, ceil(5/6)+1 = 1+1 = 2 pages
        assertEquals(2, glassesForecastPages(12))
    }

    @Test
    fun verticalStack_threeForecastPagesFor19Items() {
        // 19 items: maxOffset=12, ceil(12/6)+1 = 2+1 = 3 pages
        assertEquals(3, glassesForecastPages(19))
    }

    @Test
    fun verticalStack_pageCappedAtFour() {
        // Very large list should never exceed 4 forecast pages
        assertEquals(4, glassesForecastPages(100))
    }

    @Test
    fun verticalStack_totalStackItems_includesCurrentWeatherCard() {
        // VerticalStack always has 1 current-weather item + totalForecastPages items
        val forecastPages = glassesForecastPages(12)
        val totalStackItems = 1 + forecastPages
        assertEquals(3, totalStackItems) // 1 weather + 2 forecast
    }

    @Test
    fun verticalStack_forecastOffsetPerPage() {
        // 19 items: page 0 → offset 0, page 1 → offset 6, page 2 → offset 12 (clamped so the page is full)
        assertEquals(0,  glassesForecastOffset(0, 19))
        assertEquals(6,  glassesForecastOffset(1, 19))
        assertEquals(12, glassesForecastOffset(2, 19))
    }

    @Test
    fun verticalStack_lastPageIsPulledBackToStayFull() {
        // 10 items: only 3 beyond the first page, so page 1 starts at 3, not 6
        assertEquals(2, glassesForecastPages(10))
        assertEquals(3, glassesForecastOffset(1, 10))
        // every page shows displayCount items
        assertEquals(7, List(10) { it }.drop(glassesForecastOffset(1, 10)).take(GLASSES_FORECAST_DISPLAY_COUNT).size)
    }

    @Test
    fun verticalStack_fewerItemsThanAPage_offsetIsZero() {
        assertEquals(0, glassesForecastOffset(0, 5))
        assertEquals(0, glassesForecastOffset(3, 5))
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
    fun temperatureDisplay_neverShowsANegativeZero() {
        assertEquals("0.0", formatTemperature(convertTemperature(-17.8, toCelsius = false), 1))   // -0.04 °F
        assertEquals("0", formatTemperature(-0.4, 0))
        assertEquals("-1", formatTemperature(-0.6, 0))
        assertEquals("-17.8", formatTemperature(-17.8, 1))
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

    // --- Locale / language-tag round-trip (mirrors GlassesWeatherActivity.onCreate) ---

    @Test
    fun languageTag_englishRoundTrip() {
        val tag = Locale.ENGLISH.toLanguageTag()
        val locale = Locale.forLanguageTag(tag)
        assertEquals(Locale.ENGLISH.language, locale.language)
    }

    @Test
    fun languageTag_frenchRoundTrip() {
        val tag = Locale.FRENCH.toLanguageTag()
        val locale = Locale.forLanguageTag(tag)
        assertEquals("fr", locale.language)
    }

    @Test
    fun languageTag_emptyFallsBackToEnglish() {
        // Mirrors the ?: Locale.ENGLISH.toLanguageTag() fallback in GlassesWeatherActivity
        val tag = null ?: Locale.ENGLISH.toLanguageTag()
        val locale = Locale.forLanguageTag(tag)
        assertEquals(Locale.ENGLISH.language, locale.language)
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

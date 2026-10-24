package com.jpcottin.weatherglance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How one Open-Meteo response becomes the hourly strip and the daily row. */
class ForecastSlicingTest {

    private fun hours(n: Int, from: Int = 0) = List(n) { i -> "2026-04-20T${(from + i).toString().padStart(2, '0')}:00" }

    private fun response(
        currentTime: String = "2026-04-20T10:00",
        hourly: Hourly? = Hourly(
            time = hours(24),
            temperature = List(24) { it.toDouble() },
            weatherCode = List(24) { 1 },
            isDay = List(24) { if (it in 6..19) 1 else 0 },
            uvIndex = List(24) { it / 10.0 },
        ),
        daily: Daily? = Daily(
            time = listOf("2026-04-20", "2026-04-21", "2026-04-22", "2026-04-23", "2026-04-24"),
            weatherCode = listOf(0, 1, 2, 3, 45),
            maxTemperature = listOf(20.0, 21.0, 22.0, 23.0, 24.0),
            minTemperature = listOf(10.0, 11.0, 12.0, 13.0, 14.0),
            sunrise = List(5) { "2026-04-2${it}T06:00" },
            sunset = List(5) { "2026-04-2${it}T20:00" },
            uvIndexMax = listOf(5.0, 6.0, 7.0, 8.0, 9.0),
        ),
    ) = WeatherResponse(
        current_weather = CurrentWeather(temperature = 18.5, weatherCode = 3, isDay = 1, time = currentTime),
        hourly = hourly,
        daily = daily,
        timezone = "Europe/Paris",
    )

    @Test
    fun hourlyRowsStartAtTheCurrentHourAndStopAtTheRange() {
        val f = buildForecasts(response(), hourlyRange = 6, dailyRange = 3)
        assertEquals(hours(6, from = 10), f.hourly.map { it.isoTime })
        assertEquals(10.0, f.hourly.first().temp, 0.0)
        assertEquals(1.0, f.currentUvIndex!!, 0.0)             // uv of the current hour
        assertEquals(1.5, f.hourly.last().uvIndex!!, 0.0)
    }

    @Test
    fun hourlyRowsAreCappedByWhatTheResponseHolds() {
        val f = buildForecasts(response(currentTime = "2026-04-20T20:00"), hourlyRange = 12, dailyRange = 1)
        assertEquals(4, f.hourly.size)                         // 20:00 .. 23:00
    }

    @Test
    fun aCurrentHourAfterEverySlotStartsFromTheFirstSlot() {
        val f = buildForecasts(response(currentTime = "2026-04-21T03:00"), hourlyRange = 3, dailyRange = 1)
        assertEquals(hours(3), f.hourly.map { it.isoTime })
    }

    @Test
    fun missingHourlyFieldsFallBack() {
        val sparse = Hourly(
            time = hours(4), temperature = listOf(1.0, 2.0, 3.0, 4.0),
            weatherCode = listOf(61),                          // only the first hour has a code
            isDay = null, uvIndex = null,
        )
        val f = buildForecasts(response(currentTime = "2026-04-20T00:00", hourly = sparse), hourlyRange = 4, dailyRange = 1)
        assertEquals(listOf(61, 3, 3, 3), f.hourly.map { it.code })   // 3 = the current weather code
        assertTrue(f.hourly.all { it.isDay == 1 })
        assertTrue(f.hourly.all { it.uvIndex == null })
        assertNull(f.currentUvIndex)
    }

    @Test
    fun dailyRowsSkipTodayAndStopAtTheRange() {
        val f = buildForecasts(response(), hourlyRange = 1, dailyRange = 3)
        assertEquals(listOf("2026-04-21", "2026-04-22", "2026-04-23"), f.daily.map { it.isoDate })
        assertEquals(listOf(1, 2, 3), f.daily.map { it.code })
        assertEquals(11.0, f.daily.first().minTemp, 0.0)
        assertEquals(21.0, f.daily.first().maxTemp, 0.0)
        assertEquals(6.0, f.daily.first().uvIndexMax!!, 0.0)
    }

    @Test
    fun dailyRowsAreCappedByWhatTheResponseHolds() {
        val f = buildForecasts(response(), hourlyRange = 1, dailyRange = 10)
        assertEquals(4, f.daily.size)
    }

    @Test
    fun noHourlyOrDailyBlockGivesEmptyRows() {
        val f = buildForecasts(response(hourly = null, daily = null), hourlyRange = 12, dailyRange = 5)
        assertTrue(f.hourly.isEmpty())
        assertTrue(f.daily.isEmpty())
        assertNull(f.currentUvIndex)
    }

    @Test
    fun daysNeededCoverBothRanges() {
        assertEquals(2, forecastDaysNeeded(hourlyRange = 1, dailyRange = 0))    // the hour may be today's last
        assertEquals(3, forecastDaysNeeded(hourlyRange = 30, dailyRange = 0))   // 30 h from late today spill into a third day
        assertEquals(4, forecastDaysNeeded(hourlyRange = 12, dailyRange = 3))   // today + 3 more days
        assertEquals(4, forecastDaysNeeded(hourlyRange = 72, dailyRange = 1))
        assertEquals(8, forecastDaysNeeded(hourlyRange = 168, dailyRange = 6))  // the full week from any hour
    }

    @Test
    fun aFullHourlyRangeLateInTheDayStillFillsTheStrip() {
        val f = buildForecasts(response(currentTime = "2026-04-20T20:00", hourly = Hourly(
            time = hours(24) + List(24) { i -> "2026-04-21T${i.toString().padStart(2, '0')}:00" },
            temperature = List(48) { it.toDouble() }, weatherCode = List(48) { 1 }, isDay = List(48) { 1 }, uvIndex = null,
        )), hourlyRange = 24, dailyRange = 1)
        assertEquals(24, f.hourly.size)
        assertEquals("2026-04-20T20:00", f.hourly.first().isoTime)
    }

    @Test
    fun aShortOrHoleyTemperatureArrayCostsOnlyThoseRows() {
        val f = buildForecasts(response(currentTime = "2026-04-20T00:00", hourly = Hourly(
            time = hours(4), temperature = listOf(1.0, 2.0), weatherCode = List(4) { 1 }, isDay = null, uvIndex = null,
        ), daily = Daily(
            time = listOf("2026-04-20", "2026-04-21", "2026-04-22"), weatherCode = listOf(0, 1, 2),
            maxTemperature = listOf(20.0, 21.0), minTemperature = listOf(10.0, 11.0, 12.0),
            sunrise = listOf("2026-04-20T06:00"), sunset = listOf("2026-04-20T20:00"), uvIndexMax = null,
        )), hourlyRange = 4, dailyRange = 2)
        assertEquals(2, f.hourly.size)
        assertEquals(1, f.daily.size)                              // the 22nd has no max temperature
        assertEquals("", f.daily.first().sunrise)                  // no sunrise row: blank, not a crash
    }

    @Test
    fun airQualityDaysCoverTheHourlyStrip() {
        assertEquals(1, airQualityDaysNeeded(0))
        assertEquals(2, airQualityDaysNeeded(8))
        assertEquals(7, airQualityDaysNeeded(168))
    }

    @Test
    fun daysNeededNeverExceedWhatTheApiServes() {
        assertEquals(16, forecastDaysNeeded(hourlyRange = 1, dailyRange = MAX_DAILY_RANGE))  // the slider's top
        assertEquals(16, forecastDaysNeeded(hourlyRange = 1, dailyRange = 40))               // a stale larger preference
        assertEquals(16, forecastDaysNeeded(hourlyRange = 24 * 30, dailyRange = 0))
    }

    @Test
    fun aCurrentTimeInsideTheHourStartsAtThatHour() {
        val f = buildForecasts(response(currentTime = "2026-04-20T10:15"), hourlyRange = 3, dailyRange = 1)
        assertEquals(listOf("2026-04-20T10:00", "2026-04-20T11:00", "2026-04-20T12:00"), f.hourly.map { it.isoTime })
        assertEquals(1.0, f.currentUvIndex!!, 1e-9)
    }
}

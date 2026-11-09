package com.jpcottin.weatherglance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** What the screen keeps, and gets back, when a fetch fails. */
class FetchFailureTest {

    private val hourly = listOf(HourlyForecastData("2026-04-20T10:00", 0, 1, 21.0))

    @Test
    fun aReadingHiddenForTheNewSpotComesBack() {
        val waiting = WeatherUiState(temperature = null, weatherCode = 61, hourlyForecasts = hourly, isRefreshing = true)
        val after = waiting.afterFetchFailure("Error: timeout", lastGoodTemperature = 21.5)
        assertEquals(21.5, after.temperature!!, 1e-9)
        assertEquals("Error: timeout", after.locationInfo)
        assertFalse(after.isRefreshing)
        assertEquals(61, after.weatherCode)          // the last good weather stays with it
        assertEquals(hourly, after.hourlyForecasts)
    }

    @Test
    fun aReadingStillShowingIsKept() {
        val refreshing = WeatherUiState(temperature = 18.0, isRefreshing = true)
        assertEquals(18.0, refreshing.afterFetchFailure("Error: timeout", lastGoodTemperature = 21.5).temperature!!, 1e-9)
    }

    @Test
    fun nothingGoodYetStaysLoading() {
        val first = WeatherUiState(temperature = null, isRefreshing = true)
        assertNull(first.afterFetchFailure("Error: timeout", lastGoodTemperature = null).temperature)
    }
}

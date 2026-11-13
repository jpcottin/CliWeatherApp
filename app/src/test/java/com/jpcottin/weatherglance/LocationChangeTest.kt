package com.jpcottin.weatherglance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the screen keeps when a fetch finds its place. */
class LocationChangeTest {

    private val aqiMap = mapOf("2026-04-20T10:00" to 31, "2026-04-20T11:00" to 35)
    private val paris = WeatherUiState(
        locationInfo = "Paris, FR", rawCity = "Paris", currentLat = 48.8566, currentLon = 2.3522,
        temperature = 18.0, currentAirQuality = 31, aqiHourlyMap = aqiMap,
    )

    @Test
    fun aNewSpotDropsThePreviousSpotsAirQuality() {
        val tokyo = paris.atLocation("Tokyo, JP", "Tokyo", 35.6762, 139.6503)
        assertEquals("Tokyo, JP", tokyo.locationInfo)
        assertEquals("Tokyo", tokyo.rawCity)
        assertEquals(35.6762, tokyo.currentLat, 1e-9)
        assertEquals(139.6503, tokyo.currentLon, 1e-9)
        assertNull(tokyo.currentAirQuality)
        assertTrue(tokyo.aqiHourlyMap.isEmpty())
        assertEquals(18.0, tokyo.temperature!!, 1e-9)       // the weather is the fetch's to replace
    }

    @Test
    fun aRefreshOfTheSameSpotKeepsItsAirQuality() {
        val again = paris.atLocation("Paris, FR", "Paris", 48.8566, 2.3522)
        assertEquals(31, again.currentAirQuality)
        assertEquals(aqiMap, again.aqiHourlyMap)
    }

    @Test
    fun aGpsNudgeIsTheSameSpot() {
        val nudged = paris.atLocation("Paris, FR", "Paris", 48.8566 + 0.004, 2.3522 - 0.006)
        assertEquals(31, nudged.currentAirQuality)
        assertEquals(aqiMap, nudged.aqiHourlyMap)
        assertEquals(48.8606, nudged.currentLat, 1e-9)       // the marker still moves
    }
}

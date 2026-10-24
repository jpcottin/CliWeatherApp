package com.jpcottin.weatherglance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** How the Open-Meteo air-quality hours become the current reading and the hourly map. */
class AirQualityTest {

    private fun hours(n: Int, from: Int = 0) = List(n) { i -> "2026-04-20T${(from + i).toString().padStart(2, '0')}:00" }

    private fun hourly(times: List<String> = hours(24), aqi: List<Int?> = List(24) { it * 2 }) =
        AirQualityHourly(time = times, europeanAqi = aqi)

    @Test
    fun theCurrentReadingIsTheHourMatchingNow() {
        val aq = buildAirQuality(hourly(), nowIso = "2026-04-20T10:00")
        assertEquals(20, aq.current)
    }

    @Test
    fun aResponseThatStartsLaterUsesItsFirstHour() {
        val aq = buildAirQuality(hourly(times = hours(6, from = 12), aqi = listOf(30, 31, 32, 33, 34, 35)), nowIso = "2026-04-20T10:00")
        assertEquals(30, aq.current)
    }

    @Test
    fun aResponseEntirelyInThePastFallsBackToItsFirstHour() {
        val aq = buildAirQuality(hourly(times = hours(3), aqi = listOf(7, 8, 9)), nowIso = "2026-04-21T10:00")
        assertEquals(7, aq.current)
    }

    @Test
    fun aMissingValueAtTheCurrentHourLeavesCurrentNullAndTheHourOut() {
        val aqi = List<Int?>(24) { it * 2 }.toMutableList().also { it[10] = null }
        val aq = buildAirQuality(hourly(aqi = aqi), nowIso = "2026-04-20T10:00")
        assertNull(aq.current)
        assertTrue("2026-04-20T10:00" !in aq.hourly)
        assertEquals(23, aq.hourly.size)
        assertEquals(22, aq.hourly["2026-04-20T11:00"])
    }

    @Test
    fun theMapKeepsEveryHourWithAValue() {
        val aq = buildAirQuality(hourly(), nowIso = "2026-04-20T10:00")
        assertEquals(hours(24), aq.hourly.keys.toList())
        assertEquals(0, aq.hourly["2026-04-20T00:00"])
        assertEquals(46, aq.hourly["2026-04-20T23:00"])
    }

    @Test
    fun aValueListShorterThanTheTimesIsNotAnError() {
        val aq = buildAirQuality(hourly(times = hours(24), aqi = listOf(1, 2, 3)), nowIso = "2026-04-20T10:00")
        assertNull(aq.current)                      // no sample for 10:00
        assertEquals(3, aq.hourly.size)             // only the hours that have a value
    }

    @Test
    fun anEmptyResponseGivesNothing() {
        val aq = buildAirQuality(hourly(times = emptyList(), aqi = emptyList()), nowIso = "2026-04-20T10:00")
        assertNull(aq.current)
        assertTrue(aq.hourly.isEmpty())
    }

    // "Now" for the matching is taken in the response's zone, not the phone's.
    private val instant = java.time.Instant.parse("2026-04-20T22:15:00Z")   // 15:15 in California, 07:15 next day in Tokyo

    @Test
    fun theCurrentHourIsTakenInTheLocationZone() {
        val clock = java.time.Clock.fixed(instant, java.time.ZoneId.of("America/Los_Angeles"))
        assertEquals("2026-04-21T07:00", currentHourIso("Asia/Tokyo", clock))
        assertEquals("2026-04-20T15:00", currentHourIso("America/Los_Angeles", clock))
    }

    @Test
    fun anUnknownZoneFallsBackToTheDeviceZone() {
        val clock = java.time.Clock.fixed(instant, java.time.ZoneId.of("America/Los_Angeles"))
        assertEquals("2026-04-20T15:00", currentHourIso("Mars/Olympus", clock))
        assertEquals("2026-04-20T15:00", currentHourIso(null, clock))
    }
}

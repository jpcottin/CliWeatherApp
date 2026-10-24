package com.jpcottin.weatherglance

import java.time.ZoneId
import java.time.Clock
import android.content.Context
import android.content.res.Configuration
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

fun getWeatherIcon(code: Int, isDay: Int) = when (code) {
    0 -> if (isDay == 1) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    1, 2, 3 -> if (isDay == 1) Icons.Rounded.Cloud else Icons.Rounded.CloudQueue
    45, 48 -> Icons.Rounded.Air
    51, 53, 55 -> Icons.Rounded.WaterDrop
    61, 63, 65 -> Icons.Rounded.Umbrella
    71, 73, 75 -> Icons.Rounded.AcUnit
    80, 81, 82 -> Icons.Rounded.WaterDrop
    95 -> Icons.Rounded.Thunderstorm
    else -> Icons.Rounded.Thunderstorm
}

fun getBackgroundColors(code: Int, isDay: Int): List<Int> {
    if (isDay == 0) return listOf(R.color.night_blue, R.color.night_dark_blue)
    return when (code) {
        0 -> listOf(R.color.sunny_orange, R.color.sunny_yellow)
        1, 2, 3 -> listOf(R.color.cloudy_blue, R.color.cloudy_light_blue)
        45, 48 -> listOf(R.color.foggy_grey, R.color.foggy_light_grey)
        else -> listOf(R.color.rainy_blue, R.color.rainy_indigo)
    }
}

fun getLocalizedContext(baseContext: Context, lang: AppLanguage): Context {
    val config = Configuration(baseContext.resources.configuration)
    config.setLocale(lang.locale)
    return baseContext.createConfigurationContext(config)
}

fun getConditionString(context: Context, code: Int): String {
    val resId = when (code) {
        0 -> R.string.weather_clear
        1, 2, 3 -> R.string.weather_cloudy
        45, 48 -> R.string.weather_foggy
        51, 53, 55 -> R.string.weather_drizzle
        61, 63, 65 -> R.string.weather_rainy
        71, 73, 75 -> R.string.weather_snowy
        80, 81, 82 -> R.string.weather_showers
        95 -> R.string.weather_storm
        else -> R.string.unknown
    }
    return context.getString(resId)
}

fun convertTemperature(temp: Double, toCelsius: Boolean) = if (toCelsius) temp else (temp * 9 / 5) + 32

/**
 * [value] rounded half-up to [decimals] places as a number, so that formatting it cannot
 * print a negative zero: Java's formatter keeps the sign of a value that rounds to zero
 * ("-0.0°F" for -17.8 °C, "-0°" for -0.4). BigDecimal has no negative zero.
 */
fun roundTemperature(value: Double, decimals: Int): Double =
    java.math.BigDecimal.valueOf(value).setScale(decimals, java.math.RoundingMode.HALF_UP).toDouble()

/** [value] formatted with [decimals] places in the default locale, never as a negative zero. */
fun formatTemperature(value: Double, decimals: Int): String =
    String.format("%.${decimals}f", roundTemperature(value, decimals))

/** The UV index as the whole number the badges show; the colour bands are applied to it. */
fun uvLevel(uv: Double): Int = roundTemperature(uv, 0).toInt()

fun formatTime(d: Date, tz: String?, l: Locale, is24h: Boolean): String {
    val pattern = if (is24h) "HH:mm:ss" else "hh:mm:ss a"
    val sdf = java.text.SimpleDateFormat(pattern, l)
    tz?.let { sdf.timeZone = TimeZone.getTimeZone(it) }
    return sdf.format(d)
}

fun formatForecastHour(isoTime: String, l: Locale, is24h: Boolean): String =
    runCatching {
        val ldt = LocalDateTime.parse(isoTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val pattern = if (is24h) "HH:mm" else "h a"
        ldt.format(DateTimeFormatter.ofPattern(pattern, l))
    }.getOrDefault(isoTime)

fun formatSunriseSunset(isoTime: String, l: Locale, is24h: Boolean): String =
    runCatching {
        val ldt = LocalDateTime.parse(isoTime, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
        val pattern = if (is24h) "HH:mm" else "h:mm a"
        ldt.format(DateTimeFormatter.ofPattern(pattern, l))
    }.getOrDefault(isoTime)

fun formatForecastDay(isoDate: String, l: Locale): String =
    runCatching {
        val ld = LocalDate.parse(isoDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        ld.format(DateTimeFormatter.ofPattern("EEE", l))
            .replaceFirstChar { it.uppercase() }
    }.getOrDefault(isoDate)

// ── Glasses forecast pager ──────────────────────────────────────────────────
// The lens shows the hourly forecast as a vertical stack of pages of
// GLASSES_FORECAST_DISPLAY_COUNT hours, each page starting
// GLASSES_FORECAST_SWIPE_STEP hours after the previous one (one hour of
// overlap), and never more than GLASSES_FORECAST_MAX_PAGES pages.

const val GLASSES_FORECAST_DISPLAY_COUNT = 7
const val GLASSES_FORECAST_SWIPE_STEP = 6
const val GLASSES_FORECAST_MAX_PAGES = 4

/** Number of forecast pages for [itemCount] hourly items: one when they fit, otherwise enough to reach the last item, capped at [maxPages]. */
fun glassesForecastPages(
    itemCount: Int,
    displayCount: Int = GLASSES_FORECAST_DISPLAY_COUNT,
    swipeStep: Int = GLASSES_FORECAST_SWIPE_STEP,
    maxPages: Int = GLASSES_FORECAST_MAX_PAGES,
): Int {
    val maxOffset = (itemCount - displayCount).coerceAtLeast(0)
    val pages = if (maxOffset <= 0) 1 else (maxOffset + swipeStep - 1) / swipeStep + 1
    return pages.coerceAtMost(maxPages)
}

/** Index of the first hourly item on [pageIndex]; the last page is pulled back so it is always full. */
fun glassesForecastOffset(
    pageIndex: Int,
    itemCount: Int,
    displayCount: Int = GLASSES_FORECAST_DISPLAY_COUNT,
    swipeStep: Int = GLASSES_FORECAST_SWIPE_STEP,
): Int = (pageIndex * swipeStep).coerceAtMost((itemCount - displayCount).coerceAtLeast(0))

// ── Forecast slicing ────────────────────────────────────────────────────────
// What the screens show out of one Open-Meteo response.

/** Hourly and daily forecast rows plus the UV index of the current hour. */
data class Forecasts(
    val hourly: List<HourlyForecastData>,
    val daily: List<DailyForecastData>,
    val currentUvIndex: Double?,
)

/** The most forecast days Open-Meteo serves in one request, today included. */
const val MAX_FORECAST_DAYS = 16

/** The most daily rows the settings slider offers: every day after today within [MAX_FORECAST_DAYS]. */
const val MAX_DAILY_RANGE = MAX_FORECAST_DAYS - 1

/**
 * Days to request so that [hourlyRange] hours from now and [dailyRange] days after today are
 * covered, capped at [MAX_FORECAST_DAYS]: the API rejects anything above it outright.
 */
fun forecastDaysNeeded(hourlyRange: Int, dailyRange: Int): Int =
    // The hourly rows start at the current hour, not at midnight: the part of today already
    // gone has to be covered by one more day (the local hour is unknown before the response).
    maxOf(1, (hourlyRange + 23) / 24 + 1, dailyRange + 1).coerceAtMost(MAX_FORECAST_DAYS)

/** The most days the air-quality endpoint serves in one request. */
const val MAX_AIR_QUALITY_DAYS = 7

/** Days of air quality so that every hour of the hourly strip has a reading, within what is served. */
fun airQualityDaysNeeded(hourlyRange: Int): Int = ((hourlyRange + 23) / 24 + 1).coerceIn(1, MAX_AIR_QUALITY_DAYS)

/**
 * Slices [res] into [hourlyRange] hourly rows starting at the current hour (the first hourly
 * slot whose hour is at or after `current_weather.time`'s, or the first slot when none matches) and
 * [dailyRange] daily rows starting tomorrow. Missing per-hour day flags default to day, a
 * missing per-hour weather code falls back to the current one, and a missing daily code to 0.
 */
fun buildForecasts(res: WeatherResponse, hourlyRange: Int, dailyRange: Int): Forecasts {
    val hourlyList = mutableListOf<HourlyForecastData>()
    var uvIndex: Double? = null
    res.hourly?.let { h ->
        // current_weather.time carries minutes ("…T10:15"); match on the hour so the strip starts
        // with the hour we are in rather than the next one.
        val nowHour = res.current_weather.time.take(13)
        val startIndex = h.time.indexOfFirst { it.take(13) >= nowHour }.takeIf { it != -1 } ?: 0
        uvIndex = h.uvIndex?.getOrNull(startIndex)
        for (i in startIndex until minOf(startIndex + hourlyRange, h.time.size)) {
            // A temperature array shorter than the times, or a null inside it, costs the
            // affected hour, not the whole response.
            val temp = h.temperature.getOrNull(i) ?: continue
            hourlyList.add(
                HourlyForecastData(
                    h.time[i],
                    h.weatherCode.getOrNull(i) ?: res.current_weather.weatherCode,
                    h.isDay?.getOrNull(i) ?: 1,
                    temp,
                    uvIndex = h.uvIndex?.getOrNull(i)
                )
            )
        }
    }

    val dailyList = mutableListOf<DailyForecastData>()
    res.daily?.let { d ->
        for (i in 1 until minOf(1 + dailyRange, d.time.size)) {
            val lo = d.minTemperature.getOrNull(i) ?: continue
            val hi = d.maxTemperature.getOrNull(i) ?: continue
            dailyList.add(
                DailyForecastData(
                    d.time[i],
                    d.weatherCode.getOrNull(i) ?: 0,
                    lo,
                    hi,
                    d.sunrise.getOrNull(i) ?: "",
                    d.sunset.getOrNull(i) ?: "",
                    uvIndexMax = d.uvIndexMax?.getOrNull(i)
                )
            )
        }
    }
    return Forecasts(hourlyList, dailyList, uvIndex)
}

// ── Air quality ──────────────────────────────────────────────────────────────

data class AirQualityNow(val current: Int?, val hourly: Map<String, Int>)

/**
 * The air-quality reading for the hour at [nowIso] (the "yyyy-MM-dd'T'HH:00"
 * form the hourly times use) and the hour -> AQI map behind it. The current
 * hour is the first sample at or after now, falling back to the first sample
 * of a response that is entirely in the past; hours without a value are left
 * out of the map, and a missing value at the current hour leaves current null.
 */
/**
 * The current hour, on the hour, in the zone the air-quality response is expressed in
 * (`timezone=auto` makes it the location's zone, not the phone's). An unknown zone name falls
 * back to the device zone rather than failing the whole reading.
 */
fun currentHourIso(zoneName: String?, clock: Clock = Clock.systemDefaultZone()): String {
    val zone = zoneName?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: clock.zone
    return LocalDateTime.now(clock.withZone(zone)).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00"))
}

fun buildAirQuality(hourly: AirQualityHourly, nowIso: String): AirQualityNow {
    val idx = hourly.time.indexOfFirst { it >= nowIso }.takeIf { it != -1 } ?: 0
    val current = hourly.europeanAqi.getOrNull(idx)
    val byHour = hourly.time.zip(hourly.europeanAqi)
        .filter { it.second != null }
        .associate { it.first to it.second!! }
    return AirQualityNow(current, byHour)
}

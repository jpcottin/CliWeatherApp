package com.example.cliweatherapp

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import java.text.SimpleDateFormat
import java.util.*

fun getWeatherIcon(code: Int, isDay: Int) = when (code) {
    0 -> if(isDay == 1) Icons.Rounded.WbSunny else Icons.Rounded.NightsStay
    1, 2, 3 -> if(isDay == 1) Icons.Rounded.Cloud else Icons.Rounded.CloudQueue
    45, 48 -> Icons.Rounded.Air; 51, 53, 55 -> Icons.Rounded.WaterDrop; 61, 63, 65 -> Icons.Rounded.Umbrella; 71, 73, 75 -> Icons.Rounded.AcUnit; else -> Icons.Rounded.Thunderstorm
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

// To maintain manual language toggle, we helper to get strings from specific locale
fun getLocalizedContext(baseContext: Context, lang: AppLanguage): Context {
    val config = baseContext.resources.configuration
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

fun formatTime(d: Date, tz: String?, l: Locale, is24h: Boolean): String {
    val pattern = if (is24h) "HH:mm:ss" else "hh:mm:ss a"
    val sdf = SimpleDateFormat(pattern, l)
    tz?.let { sdf.timeZone = TimeZone.getTimeZone(it) }
    return sdf.format(d)
}

fun formatForecastHour(isoTime: String, l: Locale, is24h: Boolean): String {
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val date = parser.parse(isoTime) ?: return isoTime
        val pattern = if (is24h) "HH:mm" else "h a"
        return SimpleDateFormat(pattern, l).format(date)
    } catch (e: Exception) { return isoTime }
}

fun formatSunriseSunset(isoTime: String, l: Locale, is24h: Boolean): String {
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
        val date = parser.parse(isoTime) ?: return isoTime
        val pattern = if (is24h) "HH:mm" else "h:mm a"
        return SimpleDateFormat(pattern, l).format(date)
    } catch (e: Exception) { return isoTime }
}

fun formatForecastDay(isoDate: String, l: Locale): String {
    try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = parser.parse(isoDate) ?: return isoDate
        val formatter = SimpleDateFormat("EEE", l)
        return formatter.format(date).replaceFirstChar { it.uppercase() }
    } catch (e: Exception) { return isoDate }
}

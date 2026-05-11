package com.example.cliweatherapp

import android.content.Context
import android.content.SharedPreferences
import android.text.format.DateFormat

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE)

    fun saveIsCelsius(value: Boolean) = prefs.edit().putBoolean("is_celsius", value).apply()
    fun getIsCelsius() = prefs.getBoolean("is_celsius", true)

    fun saveLanguage(value: AppLanguage) = prefs.edit().putString("language", value.name).apply()
    fun getLanguage(): AppLanguage {
        val name = prefs.getString("language", AppLanguage.EN.name)
        return try { AppLanguage.valueOf(name!!) } catch (e: Exception) { AppLanguage.EN }
    }

    fun saveUseGps(value: Boolean) = prefs.edit().putBoolean("use_gps", value).apply()
    fun getUseGps() = prefs.getBoolean("use_gps", false)

    fun saveLocation(lat: Double, lon: Double) = prefs.edit()
        .putString("last_lat", lat.toString())
        .putString("last_lon", lon.toString())
        .apply()

    fun getLatitude() = try { prefs.getString("last_lat", "37.422")!!.toDouble() } catch (_: ClassCastException) { 37.422 }
    fun getLongitude() = try { prefs.getString("last_lon", "-122.084")!!.toDouble() } catch (_: ClassCastException) { -122.084 }

    fun saveIs24Hour(value: Boolean) = prefs.edit().putBoolean("is_24h", value).apply()
    fun getIs24Hour(context: Context) = prefs.getBoolean("is_24h", DateFormat.is24HourFormat(context))

    fun saveHourlyRange(value: Int) = prefs.edit().putInt("hourly_range", value).apply()
    fun getHourlyRange() = prefs.getInt("hourly_range", 8)

    fun saveDailyRange(value: Int) = prefs.edit().putInt("daily_range", value).apply()
    fun getDailyRange() = prefs.getInt("daily_range", 6)

    fun saveShowSunriseSunset(value: Boolean) = prefs.edit().putBoolean("show_sunrise_sunset", value).apply()
    fun getShowSunriseSunset() = prefs.getBoolean("show_sunrise_sunset", true)

    fun saveShowUvIndex(value: Boolean) = prefs.edit().putBoolean("show_uv_index", value).apply()
    fun getShowUvIndex() = prefs.getBoolean("show_uv_index", false)

    fun saveShowAirQuality(value: Boolean) = prefs.edit().putBoolean("show_air_quality", value).apply()
    fun getShowAirQuality() = prefs.getBoolean("show_air_quality", false)
}

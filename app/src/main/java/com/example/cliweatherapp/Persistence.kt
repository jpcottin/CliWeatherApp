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
        .putFloat("last_lat", lat.toFloat())
        .putFloat("last_lon", lon.toFloat())
        .apply()
    
    fun getLatitude() = prefs.getFloat("last_lat", 37.422f).toDouble()
    fun getLongitude() = prefs.getFloat("last_lon", -122.084f).toDouble()

    fun saveIs24Hour(value: Boolean) = prefs.edit().putBoolean("is_24h", value).apply()
    fun getIs24Hour(context: Context) = prefs.getBoolean("is_24h", DateFormat.is24HourFormat(context))
}

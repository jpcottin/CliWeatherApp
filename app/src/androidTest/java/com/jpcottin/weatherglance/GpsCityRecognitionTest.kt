package com.jpcottin.weatherglance

import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

/**
 * End-to-end GPS pipeline check: a mock fused location placed on a well-known
 * big city must flow through the real repository (Geocoder + live Open-Meteo
 * fetch) so that the city name appears in the UI.
 *
 * Uses UiAutomator only (no Espresso/Compose input), so it runs on API 37+ too.
 * Requires network access on the device/emulator.
 */
@RunWith(AndroidJUnit4::class)
class GpsCityRecognitionTest {

    companion object {
        private const val CITY_NAME = "Paris"
        private const val CITY_LAT = 48.8566
        private const val CITY_LON = 2.3522
    }

    private lateinit var device: UiDevice
    private lateinit var context: Context
    private lateinit var prefManager: PreferenceManager
    private lateinit var fused: FusedLocationProviderClient
    private var scenario: ActivityScenario<MainActivity>? = null

    private var origUseGps = false
    private var origLang: AppLanguage = AppLanguage.EN
    private lateinit var origRepository: WeatherRepository

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefManager = PreferenceManager(context)

        val pkg = context.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")
        device.executeShellCommand("appops set $pkg android:mock_location allow")

        // Other UI tests statically inject MockWeatherRepository; this test
        // exercises the real pipeline, so swap it in and restore afterwards.
        origRepository = WeatherViewModel.repository
        WeatherViewModel.repository = RetrofitWeatherRepository()

        origUseGps = prefManager.getUseGps()
        origLang = prefManager.getLanguage()
        prefManager.saveUseGps(true)
        prefManager.saveLanguage(AppLanguage.EN)

        fused = LocationServices.getFusedLocationProviderClient(context)
        Tasks.await(fused.setMockMode(true), 10, TimeUnit.SECONDS)
        pushMockLocation()

        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @After
    fun tearDown() {
        scenario?.close()
        try {
            Tasks.await(fused.setMockMode(false), 10, TimeUnit.SECONDS)
        } catch (_: Exception) {
        }
        prefManager.saveUseGps(origUseGps)
        prefManager.saveLanguage(origLang)
        WeatherViewModel.repository = origRepository
        device.executeShellCommand("appops set ${context.packageName} android:mock_location default")
    }

    private fun pushMockLocation() {
        val loc = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = CITY_LAT
            longitude = CITY_LON
            accuracy = 5f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        Tasks.await(fused.setMockLocation(loc), 10, TimeUnit.SECONDS)
    }

    @Test
    fun gpsLocationAtKnownCityShowsCityNameInUi() {
        // The fetch runs at activity launch; keep the mock location fresh and
        // retry via the Refresh button in case geocoding or the API is slow.
        var cityFound = false
        for (attempt in 1..3) {
            pushMockLocation()
            if (device.wait(Until.hasObject(By.textContains(CITY_NAME)), 25000)) {
                cityFound = true
                break
            }
            device.findObject(By.textContains("Refresh"))?.click()
        }
        assert(cityFound) { "$CITY_NAME not shown for mock GPS ($CITY_LAT, $CITY_LON)" }

        // The weather itself must have been fetched for those coordinates too.
        val weatherLoaded = device.wait(Until.hasObject(By.textContains("°")), 15000)
        assert(weatherLoaded) { "No temperature shown after $CITY_NAME was recognized" }
    }
}

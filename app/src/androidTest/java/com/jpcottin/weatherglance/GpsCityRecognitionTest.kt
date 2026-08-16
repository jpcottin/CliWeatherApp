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
import kotlin.concurrent.thread

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

    // getCurrentLocation wants a *fresh* fix; a single mock push can be
    // considered stale by the time the app requests. Keep pushing with new
    // timestamps for the whole test.
    @Volatile
    private var keepPushing = false
    private var pusher: Thread? = null

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
        keepPushing = true
        pusher = thread(name = "mock-location-pusher") {
            while (keepPushing) {
                try {
                    pushMockLocation()
                } catch (_: Exception) {
                }
                Thread.sleep(2000)
            }
        }

        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java))
    }

    @After
    fun tearDown() {
        // Every step independent: a failure in one must not skip the rest,
        // or state leaks into the tests that run after this class.
        keepPushing = false
        runCatching { pusher?.join(3000) }
        runCatching { scenario?.close() }
        runCatching { Tasks.await(fused.setMockMode(false), 10, TimeUnit.SECONDS) }
        runCatching { prefManager.saveUseGps(origUseGps) }
        runCatching { prefManager.saveLanguage(origLang) }
        runCatching { WeatherViewModel.repository = origRepository }
        runCatching {
            device.executeShellCommand("appops set ${context.packageName} android:mock_location default")
        }
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
        // The fetch runs at activity launch; retry once via the Refresh button
        // in case the first fetch ran before the mock location was accepted.
        var cityFound = device.wait(Until.hasObject(By.textContains(CITY_NAME)), 20000)
        if (!cityFound) {
            device.findObject(By.textContains("Refresh"))?.click()
            cityFound = device.wait(Until.hasObject(By.textContains(CITY_NAME)), 20000)
        }
        assert(cityFound) { "$CITY_NAME not shown for mock GPS ($CITY_LAT, $CITY_LON)" }

        // The weather itself must have been fetched for those coordinates too.
        val weatherLoaded = device.wait(Until.hasObject(By.textContains("°")), 15000)
        assert(weatherLoaded) { "No temperature shown after $CITY_NAME was recognized" }
    }
}

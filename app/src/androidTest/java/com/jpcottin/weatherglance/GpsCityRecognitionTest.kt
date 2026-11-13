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
import com.google.gson.Gson
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * End-to-end GPS pipeline check: a mock fused location placed on a well-known
 * big city must flow through the real repository (Geocoder, then the forecast
 * request for those coordinates) so that the city name and its temperature
 * appear in the UI.
 *
 * The forecast and air-quality requests go to a local HTTPS server on the
 * device that answers with Open-Meteo-shaped responses, so the test is about
 * the app's own pipeline and does not fail when the live service does.
 *
 * Uses UiAutomator only (no Espresso/Compose input), so it runs on API 37+ too.
 */
@RunWith(AndroidJUnit4::class)
class GpsCityRecognitionTest {

    companion object {
        private const val CITY_NAME = "Paris"
        private const val CITY_LAT = 48.8566
        private const val CITY_LON = 2.3522
        private const val CITY_ZONE = "Europe/Paris"
        /** What the local server reports as the current temperature, in °C. */
        private const val CITY_TEMPERATURE = 12.3
        private const val FORECAST_DAYS = 16
    }

    private lateinit var device: UiDevice
    private lateinit var context: Context
    private lateinit var prefManager: PreferenceManager
    private lateinit var fused: FusedLocationProviderClient
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var server: MockWebServer

    private var origUseGps = false
    private var origIsCelsius = true
    private var origLang: AppLanguage = AppLanguage.EN
    private lateinit var origRepository: WeatherRepository
    private lateinit var origAirQualityApi: AirQualityApi

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
        // Its forecast comes from the local server, not from the live service.
        val certificate = HeldCertificate.Builder()
            .addSubjectAlternativeName("localhost")
            .addSubjectAlternativeName("127.0.0.1")
            .build()
        server = startForecastServer(certificate)
        val retrofit = retrofitFor(server, certificate)
        origRepository = WeatherViewModel.repository
        origAirQualityApi = WeatherViewModel.airQualityApi
        WeatherViewModel.repository = RetrofitWeatherRepository(retrofit.create(WeatherApi::class.java))
        WeatherViewModel.airQualityApi = retrofit.create(AirQualityApi::class.java)

        origUseGps = prefManager.getUseGps()
        origIsCelsius = prefManager.getIsCelsius()
        origLang = prefManager.getLanguage()
        prefManager.saveUseGps(true)
        prefManager.saveIsCelsius(true)
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
        runCatching { prefManager.saveIsCelsius(origIsCelsius) }
        runCatching { prefManager.saveLanguage(origLang) }
        runCatching { WeatherViewModel.repository = origRepository }
        runCatching { WeatherViewModel.airQualityApi = origAirQualityApi }
        runCatching { server.shutdown() }
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

        // The weather itself must have been fetched for those coordinates too:
        // the temperature on screen is the one served for them.
        val shown = "${formatTemperature(CITY_TEMPERATURE, 1)}°C"
        val weatherLoaded = device.wait(Until.hasObject(By.textContains(shown)), 15000)
        assert(weatherLoaded) { "$shown not shown after $CITY_NAME was recognized" }
    }

    // ── A local Open-Meteo ──────────────────────────────────────────────────

    /**
     * An HTTPS server on the device answering the forecast and air-quality
     * paths the app requests. HTTPS because the app's network security policy
     * allows no cleartext, loopback included; the client trusts its certificate.
     */
    private fun startForecastServer(certificate: HeldCertificate): MockWebServer {
        val serverCertificates = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        return MockWebServer().apply {
            useHttps(serverCertificates.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    val body = when {
                        path.startsWith("/v1/forecast") -> forecastJson()
                        path.startsWith("/v1/air-quality") -> airQualityJson()
                        else -> return MockResponse().setResponseCode(404)
                    }
                    return MockResponse().setHeader("Content-Type", "application/json").setBody(body)
                }
            }
            start()
        }
    }

    /** Retrofit for the app's APIs pointed at [server], trusting its [certificate]. */
    private fun retrofitFor(server: MockWebServer, certificate: HeldCertificate): Retrofit {
        val clientCertificates = HandshakeCertificates.Builder()
            .addTrustedCertificate(certificate.certificate)
            .build()
        val client = OkHttpClient.Builder()
            .sslSocketFactory(clientCertificates.sslSocketFactory(), clientCertificates.trustManager)
            .build()
        return Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val hourFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:00")

    /** A forecast for the city shaped like Open-Meteo's, starting today in its zone. */
    private fun forecastJson(): String {
        val now = ZonedDateTime.now(ZoneId.of(CITY_ZONE))
        val midnight = now.toLocalDate().atStartOfDay()
        val hours = (0 until FORECAST_DAYS * 24).map { midnight.plusHours(it.toLong()) }
        val days = (0 until FORECAST_DAYS).map { now.toLocalDate().plusDays(it.toLong()) }
        val body = mapOf(
            "timezone" to CITY_ZONE,
            "current_weather" to mapOf(
                "temperature" to CITY_TEMPERATURE,
                "weathercode" to 2,
                "is_day" to 1,
                "time" to now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"))
            ),
            "hourly" to mapOf(
                "time" to hours.map { it.format(hourFormat) },
                "temperature_2m" to hours.indices.map { 8.0 + it % 10 },
                "weather_code" to hours.indices.map { it % 4 },
                "is_day" to hours.map { if (it.hour in 7..18) 1 else 0 },
                "uv_index" to hours.indices.map { (it % 6).toDouble() }
            ),
            "daily" to mapOf(
                "time" to days.map { it.toString() },
                "weather_code" to days.indices.map { it % 4 },
                "temperature_2m_max" to days.indices.map { 16.0 + it % 5 },
                "temperature_2m_min" to days.indices.map { 6.0 + it % 5 },
                "sunrise" to days.map { "${it}T07:30" },
                "sunset" to days.map { "${it}T17:30" },
                "uv_index_max" to days.indices.map { 3.0 }
            )
        )
        return Gson().toJson(body)
    }

    /** A week of hourly air quality for the city, shaped like Open-Meteo's. */
    private fun airQualityJson(): String {
        val midnight = ZonedDateTime.now(ZoneId.of(CITY_ZONE)).toLocalDate().atStartOfDay()
        val hours = (0 until MAX_AIR_QUALITY_DAYS * 24).map { midnight.plusHours(it.toLong()) }
        val body = mapOf(
            "timezone" to CITY_ZONE,
            "hourly" to mapOf(
                "time" to hours.map { it.format(hourFormat) },
                "european_aqi" to hours.indices.map { 20 + it % 30 }
            )
        )
        return Gson().toJson(body)
    }
}

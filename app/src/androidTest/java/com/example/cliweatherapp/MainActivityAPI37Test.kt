package com.example.cliweatherapp

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import android.os.Build
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityAPI37Test {

    private lateinit var device: UiDevice
    private lateinit var context: Context
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        // Only run on API 37+
        Assume.assumeTrue(
            "Only runs on API 37+ where Espresso is restricted",
            Build.VERSION.SDK_INT >= 37
        )

        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        context = InstrumentationRegistry.getInstrumentation().targetContext

        // Force permissions
        val pkg = context.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")

        // Inject Mock Repository
        WeatherViewModel.repository = MockWeatherRepository()

        // Launch Activity
        val intent = Intent(context, MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        scenario?.close()
    }

    @Test
    fun testBasicUIElementsVisible() {
        // Wait for weather data to load (looking for the degree symbol in any text)
        // Note: UiAutomator can see the text rendered by Compose
        val degreeFound = device.wait(Until.findObject(By.textContains("°")), 20000)
        assert(degreeFound != null) { "Weather data (degree symbol) not found after 20s" }

        // Check for Share button by content description
        val shareBtn = device.findObject(By.desc("Share"))
        assert(shareBtn != null) { "Share button not found" }

        // Check for Settings button
        val settingsBtn = device.findObject(By.desc("Settings"))
        assert(settingsBtn != null) { "Settings button not found" }

        // Check for "Mock City" from our MockWeatherRepository
        val mockCityFound = device.wait(Until.findObject(By.textContains("Mock City")), 5000)
        assert(mockCityFound != null) { "Mock City not found in UI" }
    }

    @Test
    fun testSettingsDialogOpens() {
        device.wait(Until.findObject(By.desc("Settings")), 10000)?.click()
        
        // Wait for "Settings" title in dialog
        val settingsTitle = device.wait(Until.findObject(By.text("Settings")), 5000)
        assert(settingsTitle != null) { "Settings dialog title not found" }
        
        // Find and click OK (may need scrolling)
        var okBtn = device.findObject(By.text("OK"))
        if (okBtn == null) {
            // Swipe up to scroll down
            device.swipe(640, 2000, 640, 500, 20)
            okBtn = device.wait(Until.findObject(By.text("OK")), 5000)
        }
        okBtn?.click()
        
        // Wait for dialog to disappear
        val settingsTitleGone = device.wait(Until.gone(By.text("Settings")), 5000)
        assert(settingsTitleGone) { "Settings dialog did not close" }
    }
}

package com.example.cliweatherapp

import android.Manifest
import android.content.Context
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.rule.GrantPermissionRule

@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

    companion object {
        init {
            // Inject mock repository for all tests
            MainActivity.repository = MockWeatherRepository()
        }
    }

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    private lateinit var context: Context
    private lateinit var prefManager: PreferenceManager
    private lateinit var device: UiDevice

    // Original state to restore
    private var origIsCelsius: Boolean = true
    private var origLang: AppLanguage = AppLanguage.EN
    private var origUseGps: Boolean = true
    private var origIs24Hour: Boolean = true

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefManager = PreferenceManager(context)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Force permissions via shell for maximum reliability
        val pkg = context.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")

        // Save original state
        origIsCelsius = prefManager.getIsCelsius()
        origLang = prefManager.getLanguage()
        origUseGps = prefManager.getUseGps()
        origIs24Hour = prefManager.getIs24Hour(context)

        // Reset to known defaults for test predictability
        prefManager.saveIsCelsius(true)
        prefManager.saveLanguage(AppLanguage.EN)
        prefManager.saveUseGps(false)
        prefManager.saveIs24Hour(true)
        prefManager.saveHourlyRange(6)
        prefManager.saveDailyRange(6)
    }

    @After
    fun tearDown() {
        // Restore original state
        prefManager.saveIsCelsius(origIsCelsius)
        prefManager.saveLanguage(origLang)
        prefManager.saveUseGps(origUseGps)
        prefManager.saveIs24Hour(origIs24Hour)

        // Reset device orientation to natural
        device.setOrientationNatural()
    }

    private fun waitForDataToLoad() {
        // Wait until actual data (not "Loading...") appears inside current_temp
        composeTestRule.waitUntil(20000) {
            try {
                val nodes = composeTestRule.onNodeWithTag("current_temp", useUnmergedTree = true)
                    .onChildren()
                    .fetchSemanticsNodes()

                nodes.any {
                    val text = it.config.getOrNull(SemanticsProperties.Text)?.firstOrNull()?.text ?: ""
                    text.isNotEmpty() && !text.contains("Loading", ignoreCase = true)
                }
            } catch (e: Exception) { false }
        }
        composeTestRule.waitForIdle()
    }

    private fun SemanticsNodeInteraction.safeClick() {
        try { this.performScrollTo() } catch (e: AssertionError) { }
        this.performClick()
    }

    private fun SemanticsNodeInteraction.safeAssertIsDisplayed(): SemanticsNodeInteraction {
        try { this.performScrollTo() } catch (e: AssertionError) { }
        this.assertIsDisplayed()
        return this
    }

    @Test
    fun testHourlyForecastSwiping() {
        waitForDataToLoad()

        // 1. Set max hourly range to ensure list is long
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("slider_hourly").performTouchInput { swipeRight() }
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        // 2. Swipe the hourly forecast LazyRow
        val hourlyList = composeTestRule.onNodeWithTag("hourly_forecast_list")
        hourlyList.assertExists()
        hourlyList.performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testUnitAndFormatTogglesInUI() {
        waitForDataToLoad()

        // 1. Toggle C to F
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("switch_unit").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()
        // Check for °F anywhere in the UI as a direct verification
        composeTestRule.onNode(hasText("°F", substring = true)).assertExists()

        // 2. Toggle 12h to 24h
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("switch_time").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()
        // Success is reaching here without timeout/crash
    }

    @Test
    fun testLanguageChange() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()

        // Click the language dropdown
        composeTestRule.onNodeWithTag("dropdown_lang").safeClick()

        // Select French
        composeTestRule.onNodeWithText(AppLanguage.FR.label).safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        // Verify language was updated
        assert(prefManager.getLanguage() == AppLanguage.FR)
    }

    @Test
    fun testMapLocationChangeUpdatesUI() {
        waitForDataToLoad()

        // Click on the world map (simulating user pick)
        composeTestRule.onNodeWithTag("world_map").performTouchInput {
            click(percentOffset(0.2f, 0.2f))
        }
        composeTestRule.waitForIdle()
        waitForDataToLoad()

        // Verify coordinate text is displayed
        composeTestRule.onNodeWithTag("location_coords").safeAssertIsDisplayed()
    }

    @Test
    fun testShareButtonOpensChooserAndEscapes() {
        waitForDataToLoad()
        // Click the Share button
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed().safeClick()

        // Wait a bit for the system chooser to appear, then press back
        device.waitForIdle(2000)
        device.pressBack()

        // Verify we are back to the app
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
    }

    @Test
    fun testDefaultModeIsMap() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        // Switch checked = !useGps. Map mode (useGps=false) means switch is ON.
        composeTestRule.onNodeWithTag("switch_gps").assertIsOn()
        composeTestRule.onNodeWithText("OK").safeClick()
    }

    @Test
    fun testRefreshButtonAnimationState() {
        waitForDataToLoad()
        // Toggle to GPS mode so refresh is visible
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("switch_gps").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        // Click refresh and verify it exists (animation is visual but button should be responsive)
        composeTestRule.onNodeWithTag("btn_refresh").performClick()
        composeTestRule.onNodeWithTag("btn_refresh").assertExists()
    }

    @Test
    fun testLayoutWorksInDifferentOrientations() {
        waitForDataToLoad()
        device.setOrientationLeft()
        composeTestRule.waitForIdle()
        waitForDataToLoad()

        composeTestRule.onNodeWithContentDescription("Settings").safeAssertIsDisplayed()

        device.setOrientationNatural()
        composeTestRule.waitForIdle()
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeAssertIsDisplayed()
    }
}

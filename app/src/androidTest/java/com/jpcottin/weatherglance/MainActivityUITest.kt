package com.jpcottin.weatherglance

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import android.os.Build
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class MainActivityUITest {

    companion object {
        init {
            // Inject mock repository for all tests
            WeatherViewModel.repository = MockWeatherRepository()
        }
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private lateinit var context: Context
    private lateinit var prefManager: PreferenceManager
    private lateinit var device: UiDevice
    private var scenario: ActivityScenario<MainActivity>? = null

    // Original state to restore
    private var origIsCelsius: Boolean = true
    private var origLang: AppLanguage = AppLanguage.EN
    private var origUseGps: Boolean = true
    private var origIs24Hour: Boolean = true
    private var origHourlyRange: Int = 6
    private var origDailyRange: Int = 6
    private var origShowUvIndex: Boolean = false
    private var origShowAirQuality: Boolean = false

    @Before
    fun setUp() {
        // TODO: Espresso 3.6.1 uses InputManager.getInstance() via reflection, which is fully
        //  blocked on Android API 37 (Android 16 Preview). Re-enable once Espresso ships a fix.
        //  CI runs on API 35 where these tests pass normally.
        Assume.assumeTrue(
            "Skipped on API 37+: Espresso incompatible with InputManager restrictions",
            Build.VERSION.SDK_INT < 37
        )

        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefManager = PreferenceManager(context)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // 1. Force permissions via shell
        val pkg = context.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")

        // 2. Save current user settings
        origIsCelsius = prefManager.getIsCelsius()
        origLang = prefManager.getLanguage()
        origUseGps = prefManager.getUseGps()
        origIs24Hour = prefManager.getIs24Hour(context)
        origHourlyRange = prefManager.getHourlyRange()
        origDailyRange = prefManager.getDailyRange()
        origShowUvIndex = prefManager.getShowUvIndex()
        origShowAirQuality = prefManager.getShowAirQuality()

        // 3. Reset to known defaults BEFORE activity launch
        prefManager.saveIsCelsius(true)
        prefManager.saveLanguage(AppLanguage.EN)
        prefManager.saveUseGps(false)
        prefManager.saveIs24Hour(true)
        prefManager.saveHourlyRange(6)
        prefManager.saveDailyRange(6)
        prefManager.saveShowUvIndex(false)
        prefManager.saveShowAirQuality(false)

        // 4. Launch Activity manually
        val intent = Intent(context, MainActivity::class.java)
        scenario = ActivityScenario.launch(intent)
    }

    @After
    fun tearDown() {
        // 5. Restore original user settings
        prefManager.saveIsCelsius(origIsCelsius)
        prefManager.saveLanguage(origLang)
        prefManager.saveUseGps(origUseGps)
        prefManager.saveIs24Hour(origIs24Hour)
        prefManager.saveHourlyRange(origHourlyRange)
        prefManager.saveDailyRange(origDailyRange)
        prefManager.saveShowUvIndex(origShowUvIndex)
        prefManager.saveShowAirQuality(origShowAirQuality)

        scenario?.close()
        device.setOrientationNatural()
    }

    private fun waitForDataToLoad() {
        composeTestRule.waitUntil(30000) {
            try {
                composeTestRule.onAllNodesWithText("°", substring = true).fetchSemanticsNodes().isNotEmpty()
            } catch (_: IllegalStateException) {
                // Compose hierarchy not yet attached (e.g. immediately after back-press); keep polling
                false
            }
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
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("slider_hourly").performTouchInput { swipeRight() }
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        val hourlyList = composeTestRule.onNodeWithTag("hourly_forecast_list")
        hourlyList.assertExists()
        hourlyList.performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
    }

    @Test
    fun testUnitAndFormatTogglesInUI() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("switch_unit").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()
        composeTestRule.onNode(hasText("°F", substring = true)).assertExists()

        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("switch_time").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()
    }

    @Test
    fun testLanguageChange() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithTag("dropdown_lang").safeClick()

        val targetLang = AppLanguage.FR
        // Select from the dropdown menu (resolving ambiguity with the text field)
        composeTestRule.onAllNodesWithText(targetLang.label).onLast().safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        assert(prefManager.getLanguage() == AppLanguage.FR)
    }

    @Test
    fun testMapLocationChangeUpdatesUI() {
        waitForDataToLoad()
        composeTestRule.onNodeWithTag("world_map").performTouchInput {
            click(percentOffset(0.2f, 0.2f))
        }
        composeTestRule.waitForIdle()
        waitForDataToLoad()
        composeTestRule.onNodeWithTag("location_coords").safeAssertIsDisplayed()
    }

    @Test
    fun testShareButtonOpensChooserAndEscapes() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed().safeClick()
        device.waitForIdle(2000)
        device.pressBack()
        // Wait for MainActivity's compose hierarchy to be re-established before asserting
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
    }

    @Test
    fun testMapModeSelectedByDefault() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_gps").assertIsOn()
        composeTestRule.onNodeWithText("OK").safeClick()
    }

    @Test
    fun testRefreshButtonAnimationState() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("switch_gps").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        composeTestRule.onNodeWithTag("btn_refresh").performClick()
        composeTestRule.onNodeWithTag("btn_refresh").assertExists()
    }

    @Test
    fun testUvIndexAndAirQualityTogglesInSettings() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.waitForIdle()

        // Both are off by default
        composeTestRule.onNodeWithTag("switch_uv_index").assertIsOff()
        composeTestRule.onNodeWithTag("switch_air_quality").assertIsOff()

        // Toggle both on
        composeTestRule.onNodeWithTag("switch_uv_index").safeClick()
        composeTestRule.onNodeWithTag("switch_uv_index").assertIsOn()
        composeTestRule.onNodeWithTag("switch_air_quality").safeClick()
        composeTestRule.onNodeWithTag("switch_air_quality").assertIsOn()

        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

        // Preferences persisted
        assert(prefManager.getShowUvIndex())
        assert(prefManager.getShowAirQuality())

        // Restore defaults
        prefManager.saveShowUvIndex(false)
        prefManager.saveShowAirQuality(false)
    }

    @Test
    fun testShareButtonIsVisibleAfterLoad() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
    }

    @Test
    fun testGlassesButtonAbsentWhenNotConnected() {
        // On the emulator (API < 36 or no XR projected device), the glasses button must not appear.
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Send to Glasses").assertDoesNotExist()
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

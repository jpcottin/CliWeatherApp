package com.example.cliweatherapp

import android.Manifest
import android.content.Context
import android.content.Intent
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
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

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        prefManager = PreferenceManager(context)
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // 1. Force permissions via shell
        val pkg = context.packageName
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_FINE_LOCATION")
        device.executeShellCommand("pm grant $pkg android.permission.ACCESS_COARSE_LOCATION")

        // 2. Save current user settings
        origIsCelsius = prefManager.getIsCelsius()
        origLang = prefManager.getLanguage()
        origUseGps = prefManager.getUseGps()
        origIs24Hour = prefManager.getIs24Hour(context)
        origHourlyRange = prefManager.getHourlyRange()
        origDailyRange = prefManager.getDailyRange()

        // 3. Reset to known defaults BEFORE activity launch
        prefManager.saveIsCelsius(true)
        prefManager.saveLanguage(AppLanguage.EN)
        prefManager.saveUseGps(false)
        prefManager.saveIs24Hour(true)
        prefManager.saveHourlyRange(6)
        prefManager.saveDailyRange(6)

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

        scenario?.close()
        device.setOrientationNatural()
    }

    private fun waitForDataToLoad() {
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
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
    }

    @Test
    fun testDefaultModeIsMap() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.onNodeWithTag("switch_gps").assertIsOn()
        composeTestRule.onNodeWithText("OK").safeClick()
    }

    @Test
    fun testRefreshButtonAnimationState() {
        waitForDataToLoad()
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        composeTestRule.onNodeWithTag("switch_gps").safeClick()
        composeTestRule.onNodeWithText("OK").safeClick()
        waitForDataToLoad()

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

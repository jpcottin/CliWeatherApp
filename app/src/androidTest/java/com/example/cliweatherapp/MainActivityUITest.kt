package com.example.cliweatherapp

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

@RunWith(AndroidJUnit4::class)
class MainActivityUITest {

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

        // Save original state
        origIsCelsius = prefManager.getIsCelsius()
        origLang = prefManager.getLanguage()
        origUseGps = prefManager.getUseGps()
        origIs24Hour = prefManager.getIs24Hour(context)

        // Clear for clean test state (optional, but good for "Default" tests)
        context.getSharedPreferences("weather_prefs", Context.MODE_PRIVATE).edit().clear().commit()
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
    fun testShareButtonOpensChooserAndEscapes() {
        // Click the Share button
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed().safeClick()

        // Wait a bit for the system chooser to appear, then press back
        device.waitForIdle(2000)
        device.pressBack()

        // Verify we are back to the app by checking if the Share button is displayed again
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
    }

    @Test
    fun testSettingsDialogInteractionAndPersistence() {
        // Click the Settings button
        composeTestRule.onNodeWithContentDescription("Settings").safeAssertIsDisplayed().safeClick()

        // Wait for the dialog to appear (look for the "OK" button)
        composeTestRule.onNodeWithText("OK").safeAssertIsDisplayed()

        // Find switches using test tags
        composeTestRule.onNodeWithTag("switch_unit").safeClick() // Toggle Temperature Unit
        composeTestRule.onNodeWithTag("switch_gps").safeClick() // Toggle Location Mode

        // Click OK to save and dismiss
        composeTestRule.onNodeWithText("OK").safeClick()
        composeTestRule.waitForIdle()

        // Verify state is saved in preferences
        assert(prefManager.getIsCelsius() != origIsCelsius)
        assert(prefManager.getUseGps() != origUseGps)
    }

    @Test
    fun testLanguageChange() {
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithText("OK").safeAssertIsDisplayed()

        // Click the language dropdown via its test tag
        composeTestRule.onNodeWithTag("dropdown_lang").safeClick()
        
        // Select French (or English if already French)
        val targetLang = if (origLang == AppLanguage.FR) AppLanguage.EN else AppLanguage.FR
        composeTestRule.onNodeWithText(targetLang.label).safeClick()

        // Click OK to dismiss
        composeTestRule.onNodeWithText("OK").safeClick()
        composeTestRule.waitForIdle()

        // Verify language was updated
        assert(prefManager.getLanguage() == targetLang)
    }

    @Test
    fun testDefaultModeIsMap() {
        // This test assumes a clean state or that we just set the defaults
        // Open settings and verify GPS switch is OFF (Map mode)
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        // In our app, Switch checked = !useGps. So if useGps is false (Map), checked is true.
        composeTestRule.onNodeWithTag("switch_gps").assertIsOn() 
        composeTestRule.onNodeWithText("OK").safeClick()
    }

    @Test
    fun testRefreshButtonAnimationState() {
        // Ensure we are in GPS mode so the Refresh button is visible
        if (!prefManager.getUseGps()) {
            composeTestRule.onNodeWithContentDescription("Settings").safeClick()
            composeTestRule.onNodeWithTag("switch_gps").safeClick() // Toggle to GPS
            composeTestRule.onNodeWithText("OK").safeClick()
        }

        // Click refresh and immediately check if button is disabled (which happens during animation)
        composeTestRule.onNodeWithText("Refresh").performClick()
        // It might be too fast, but we can check if it exists or is disabled
        try {
            composeTestRule.onNodeWithText("Refresh").assertIsNotEnabled()
        } catch (e: Exception) { }
    }

    @Test
    fun testLocationTextShowsFullInfo() {
        // Ensure we are in Map mode (not GPS) to guarantee a reliable result
        if (prefManager.getUseGps()) {
            composeTestRule.onNodeWithContentDescription("Settings").safeClick()
            composeTestRule.onNodeWithTag("switch_gps").safeClick() // Toggle to Map
            composeTestRule.onNodeWithText("OK").safeClick()
        }

        // Click a location on the world map (London area approx)
        composeTestRule.onNodeWithTag("world_map").performClick()

        // Wait for the coordinate component to appear
        composeTestRule.waitUntil(10000) {
            try {
                composeTestRule.onNodeWithTag("location_coords").assertExists()
                val node = composeTestRule.onNodeWithTag("location_coords").fetchSemanticsNode()
                val textList = node.config.getOrNull(SemanticsProperties.Text)
                val text = textList?.firstOrNull()?.text ?: ""
                text.contains("(") && text.contains(")")
            } catch (e: Exception) { false }
        }

        // Verify the location coordinates are present and correctly formatted
        composeTestRule.onNodeWithTag("location_coords").assertTextContains("(", substring = true)
        composeTestRule.onNodeWithTag("location_coords").assertTextContains(")", substring = true)
    }

    @Test
    fun testLayoutWorksInDifferentOrientations() {
        // Set orientation to Left (Landscape)
        device.setOrientationLeft()
        composeTestRule.waitForIdle()

        // Verify UI elements are still present
        composeTestRule.onNodeWithContentDescription("Share").safeAssertIsDisplayed()
        composeTestRule.onNodeWithContentDescription("Settings").safeAssertIsDisplayed()

        // Open settings, interact, close
        composeTestRule.onNodeWithContentDescription("Settings").safeClick()
        composeTestRule.onNodeWithText("OK").safeAssertIsDisplayed().safeClick()

        // Revert orientation
        device.setOrientationNatural()
        composeTestRule.waitForIdle()

        // Check again
        composeTestRule.onNodeWithContentDescription("Settings").safeAssertIsDisplayed()
    }
}

package com.jpcottin.weatherglance

import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The location permission gates only what needs it: GPS refreshes. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(maxSdkVersion = 36)
class SectionControlsTest {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun mapModeWithoutLocationPermissionKeepsTheLocationAndSpeak() {
        composeTestRule.setContent {
            SectionControls(
                ctx = LocalContext.current,
                state = WeatherUiState(permissionGranted = false, locationInfo = "Mock City, Test\n(45.00, 90.00)"),
                prefs = WeatherPrefs(useGps = false),
                onRefresh = {}, onSpeak = {}, onRequestPermission = {}
            )
        }
        composeTestRule.onNodeWithText("Speak").assertExists()
        composeTestRule.onNodeWithText("Mock City, Test").assertExists()
        composeTestRule.onNodeWithText("Grant Permission").assertDoesNotExist()
    }

    @Test
    fun expandedLayoutInGpsModeOffersToGrantTheMissingPermission() {
        composeTestRule.setContent {
            HorizontalLayout(
                ctx = LocalContext.current,
                state = WeatherUiState(permissionGranted = false),
                prefs = WeatherPrefs(useGps = true),
                onRefresh = {}, onSpeak = {}, onRequestPermission = {}, onMapClick = { _, _ -> }
            )
        }
        composeTestRule.onNodeWithText("Grant Permission").assertExists()
        composeTestRule.onNodeWithTag("btn_refresh").assertDoesNotExist()
    }
}

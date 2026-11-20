package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h915dp")
class MainActivityRobolectricTest {

  @get:Rule
  val composeTestRule = createAndroidComposeRule<MainActivity>()

  @Test
  fun `launches home screen switches modes and navigates across all tabs`() {
    composeTestRule.waitForIdle()

    // Home screen displays core cards and CTA button
    composeTestRule.onNodeWithTag("daily_progress_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("start_focus_button").performScrollTo().assertIsDisplayed()

    // Switch to Deep Focus on Home and wait for DataStore update
    composeTestRule.onNodeWithTag("mode_pill_deep_focus").performScrollTo().performClick()
    composeTestRule.waitUntil(timeoutMillis = 5_000) {
      composeTestRule.onAllNodesWithText("45 min preset").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.onNodeWithText("45 min preset").performScrollTo().assertIsDisplayed()

    // Navigate to Focus Timer tab
    composeTestRule.onNodeWithTag("nav_focus_timer").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("timer_dial").assertIsDisplayed()
    composeTestRule.onNodeWithTag("timer_start_button").performScrollTo().assertIsDisplayed()

    // Navigate to Notification Rules tab
    composeTestRule.onNodeWithTag("nav_notification_rules").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("notification_access_card").assertIsDisplayed()
    composeTestRule.onNodeWithTag("dnd_access_card").assertIsDisplayed()

    // Navigate to Analytics tab
    composeTestRule.onNodeWithTag("nav_analytics").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("analytics_period_week").assertIsDisplayed()

    // Navigate to Settings tab
    composeTestRule.onNodeWithTag("nav_settings").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("settings_theme_dark").assertIsDisplayed()
    composeTestRule.onNodeWithTag("settings_reset_rules_button").performScrollTo().assertIsDisplayed()
  }
}

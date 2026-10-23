package com.princeyadav.grayout.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.testutil.SecureSettingsPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** An ADB grant arrives without an activity resume; Settings must still notice it. */
@RunWith(AndroidJUnit4::class)
class SettingsPermissionGrantTest {
    @get:Rule(order = 0) val permissionRule = SecureSettingsPermissionRule(granted = false)
    @get:Rule(order = 1) val composeRule = createEmptyComposeRule()

    @Test
    fun adbGrantRefreshesTheSettingsSheetAndRowInPlace() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Settings").performClick()
            composeRule.onNodeWithText("Required for grayscale toggle").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("ADB permission").performScrollTo().performClick()
            composeRule.onNodeWithText("Grant via ADB").assertIsDisplayed()

            permissionRule.setGranted(true)
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Permission granted")).fetchSemanticsNodes().isNotEmpty()
            }
            pressBack()
            // The ADB row's subtitle shows only while the permission is missing.
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Required for grayscale toggle")).fetchSemanticsNodes().isEmpty()
            }
        }
    }
}

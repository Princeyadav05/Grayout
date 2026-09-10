package com.princeyadav.grayout.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.princeyadav.grayout.MainActivity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemBarsLayoutTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun tabLabelsStayAboveSystemNavigationAndSystemIconsUseDarkAppContrast() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.waitForIdle()
            var safeBottom = 0f
            var safeLeft = 0f
            var safeRight = 0f
            scenario.onActivity { activity ->
                val decor = activity.window.decorView
                val navigation = checkNotNull(ViewCompat.getRootWindowInsets(decor))
                    .getInsets(WindowInsetsCompat.Type.navigationBars())
                safeBottom = (decor.height - navigation.bottom).toFloat()
                safeLeft = navigation.left.toFloat()
                safeRight = (decor.width - navigation.right).toFloat()
                val controller = WindowCompat.getInsetsController(activity.window, decor)
                assertFalse(controller.isAppearanceLightStatusBars)
                assertFalse(controller.isAppearanceLightNavigationBars)
            }

            for (label in listOf("Home", "Schedules", "Settings")) {
                val node = composeRule.onNodeWithText(label, useUnmergedTree = true)
                    .assertIsDisplayed().fetchSemanticsNode()
                val bounds = node.boundsInWindow
                assertTrue("$label must have visible bounds", bounds.width > 0 && bounds.height > 0)
                assertTrue("$label must be above the system navigation bar", bounds.bottom <= safeBottom)
                assertTrue("$label must clear side navigation insets", bounds.left >= safeLeft && bounds.right <= safeRight)
            }
            composeRule.onNodeWithText("Settings").performClick()
            composeRule.onNodeWithText("SERVICE").assertIsDisplayed()
        }
    }
}

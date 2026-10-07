package com.princeyadav.grayout.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.data.GrayoutDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream

/** Run separately with WRITE_SECURE_SETTINGS revoked. This test grants it through real ADB. */
@RunWith(AndroidJUnit4::class)
class SchedulePermissionGrantTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    @Test
    fun adbGrantRefreshesInPlaceAndRetriesThePreservedDraft() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val dao = GrayoutDatabase.getInstance(context).scheduleDao()
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS))
        runBlocking { dao.getAll().forEach { dao.delete(it) } }
        try {
            ActivityScenario.launch(MainActivity::class.java).use {
                composeRule.onNodeWithText("Schedules").performClick()
                composeRule.onNodeWithText("+ Add").performClick()
                composeRule.onNode(hasSetTextAction()).performTextReplacement("Preserved after setup")
                composeRule.onNodeWithText("Every day").performScrollTo().performClick()
                composeRule.onNodeWithText("Save").performScrollTo().performClick()
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(hasText("Grant grayscale permission to activate this schedule, or save it as off."))
                        .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("Set up permission").performScrollTo().performClick()
                composeRule.onNodeWithText("Grant via ADB").assertIsDisplayed()
                instrumentation.uiAutomation.executeShellCommand(
                    "pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS",
                ).use { descriptor -> FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(hasText("Permission granted")).fetchSemanticsNodes().isNotEmpty()
                }
                pressBack()
                composeRule.onNodeWithText("Preserved after setup").performScrollTo().assertIsDisplayed()
                composeRule.onNodeWithText("Save").performScrollTo().performClick()
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(hasText("+ Add")).fetchSemanticsNodes().isNotEmpty()
                }
                runBlocking {
                    val saved = dao.getAll().single()
                    assertTrue(saved.isEnabled)
                    assertEquals("Preserved after setup", saved.name)
                    assertEquals("MON,TUE,WED,THU,FRI,SAT,SUN", saved.daysOfWeek)
                }
            }
        } finally {
            runBlocking { dao.getAll().forEach { dao.delete(it) } }
        }
    }
}

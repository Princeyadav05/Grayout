package com.princeyadav.grayout.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.MainActivity
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime

/** Run with WRITE_SECURE_SETTINGS revoked before starting instrumentation. */
@RunWith(AndroidJUnit4::class)
class SchedulePermissionTest {
    @get:Rule val composeRule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()

    @Before
    fun requireMissingPermission() = runBlocking {
        assertEquals(PackageManager.PERMISSION_DENIED, context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS))
        dao.getAll().forEach { dao.delete(it) }
    }

    @After
    fun cleanSchedules() = runBlocking {
        dao.getAll().forEach { dao.delete(it) }
    }

    @Test
    fun savingEnabledScheduleWithoutPermissionKeepsDraftAndExplainsSetup() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Schedules").performClick()
            composeRule.onNodeWithText("+ Add").performClick()
            composeRule.onNode(hasSetTextAction()).performTextReplacement("My first schedule")
            composeRule.onNodeWithText("Every day").performScrollTo().performClick()
            composeRule.onNodeWithText("Save").performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Grant grayscale permission to activate this schedule, or save it as off."))
                    .fetchSemanticsNodes().isNotEmpty()
            }

            composeRule.onNodeWithText("New schedule").performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("My first schedule").assertIsDisplayed()
            composeRule.onNodeWithText("Set up permission").performScrollTo().assertIsDisplayed()
            runBlocking { assertTrue("Save must not activate a schedule that cannot run", dao.getEnabledSchedules().isEmpty()) }
        }
    }

    @Test
    fun enabledScheduleWithoutPermissionShowsSetupNeededInsteadOfNow() {
        seed(isEnabled = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSchedules()
            composeRule.onNodeWithText("Setup needed").assertIsDisplayed()
            composeRule.onNodeWithText("Now").assertDoesNotExist()
            composeRule.onNodeWithText("On").assertDoesNotExist()
        }
    }

    @Test
    fun missingPermissionPreventsEnabling() {
        val id = seed(isEnabled = false)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSchedules()
            composeRule.onNodeWithContentDescription("Schedule Permission test").performClick()
            composeRule.waitForIdle()
            runBlocking { assertFalse("Enabling requires permission", dao.getById(id)!!.isEnabled) }
        }
    }

    @Test
    fun saveAsOffPreservesDraftWithoutPermission() {
        ActivityScenario.launch(MainActivity::class.java).use {
            composeRule.onNodeWithText("Schedules").performClick()
            composeRule.onNodeWithText("+ Add").performClick()
            composeRule.onNode(hasSetTextAction()).performTextReplacement("My off schedule")
            composeRule.onNodeWithText("Every day").performScrollTo().performClick()
            composeRule.onNodeWithText("Save as off").performScrollTo().performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Off")).fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking {
                val saved = dao.getAll().single()
                assertFalse(saved.isEnabled)
                assertEquals("My off schedule", saved.name)
            }
        }
    }

    @Test
    fun enabledScheduleCanBeTurnedOffWithoutPermission() {
        val id = seed(isEnabled = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            openSchedules()
            composeRule.onNodeWithContentDescription("Schedule Permission test").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Off")).fetchSemanticsNodes().isNotEmpty()
            }
            runBlocking { assertFalse(dao.getById(id)!!.isEnabled) }
        }
    }

    private fun openSchedules() {
        composeRule.onNodeWithText("Schedules").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasText("Permission test")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun seed(isEnabled: Boolean): Long = runBlocking {
        val now = LocalTime.now()
        val start = now.minusHours(1)
        val end = now.plusHours(1)
        dao.insert(Schedule(
            name = "Permission test", daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
            startTimeHour = start.hour, startTimeMinute = start.minute,
            endTimeHour = end.hour, endTimeMinute = end.minute, isEnabled = isEnabled,
        ))
    }
}

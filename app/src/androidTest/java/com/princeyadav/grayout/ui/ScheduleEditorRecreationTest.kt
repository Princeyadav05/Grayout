package com.princeyadav.grayout.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class ScheduleEditorRecreationTest {
    @get:Rule val composeRule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao = GrayoutDatabase.getInstance(context).scheduleDao()
    private var scheduleId = 0L

    @Before
    fun seedSchedule() = runBlocking {
        scheduleId = dao.insert(
            Schedule(
                name = "Recreation test schedule",
                daysOfWeek = "MON",
                startTimeHour = 9,
                startTimeMinute = 0,
                endTimeHour = 17,
                endTimeMinute = 0,
                isEnabled = false,
            ),
        )
    }

    @After
    fun removeSchedule() = runBlocking {
        dao.getById(scheduleId)?.let { dao.delete(it) }
        Unit
    }

    @Test
    fun unsavedNameAndDaysSurviveActivityRecreation() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            composeRule.onNodeWithText("Schedules").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasText("Recreation test schedule"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNodeWithText("Recreation test schedule").performClick()
            composeRule.waitUntil(5_000) {
                composeRule.onAllNodes(hasSetTextAction() and hasText("Recreation test schedule"))
                    .fetchSemanticsNodes().isNotEmpty()
            }
            composeRule.onNode(hasSetTextAction()).performTextReplacement("Unsaved name")
            val tuesday = DayOfWeek.TUESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
            composeRule.onNodeWithContentDescription(tuesday).performClick()

            scenario.recreate()

            composeRule.onNodeWithText("Unsaved name").assertIsDisplayed()
            composeRule.onNodeWithContentDescription(tuesday).assertIsOn()
            runBlocking { assertEquals("Recreation test schedule", dao.getById(scheduleId)!!.name) }
        }
    }
}

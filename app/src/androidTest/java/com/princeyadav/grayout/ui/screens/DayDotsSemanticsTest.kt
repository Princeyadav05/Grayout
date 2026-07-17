package com.princeyadav.grayout.ui.screens

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class DayDotsSemanticsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun editorDayDotsExposeFullNameAndToggleState() {
        composeRule.setContent {
            GrayoutTheme {
                ScheduleEditorScreen(
                    name = "Focus",
                    selectedDays = setOf(DayOfWeek.MONDAY),
                    startHour = 22,
                    startMinute = 0,
                    endHour = 2,
                    endMinute = 0,
                    overlapError = null,
                    isEditMode = false,
                    onNameChange = {},
                    onToggleDay = {},
                    onSetStartTime = { _, _ -> },
                    onSetEndTime = { _, _ -> },
                    onSelectPreset = {},
                    onSave = {},
                    onDelete = {},
                    onBack = {},
                )
            }
        }

        // Selected day announces its full name and "on" toggle state, not the ambiguous "M".
        // Expected strings are derived the same way production does (Locale.getDefault()),
        // so the test isn't brittle on non-English default locales.
        val monday = DayOfWeek.MONDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val tuesday = DayOfWeek.TUESDAY.getDisplayName(TextStyle.FULL, Locale.getDefault())

        composeRule.onNodeWithContentDescription(monday)
            .assert(hasToggleableState(ToggleableState.On))

        // An unselected same-initial day is disambiguated too, and reads "off".
        composeRule.onNodeWithContentDescription(tuesday)
            .assert(hasToggleableState(ToggleableState.Off))
    }

    @Test
    fun readOnlyDayRowAnnouncesSelectedDaysAsOneNode() {
        composeRule.setContent {
            GrayoutTheme {
                ScheduleListScreen(
                    schedules = listOf(
                        Schedule(
                            id = 1,
                            name = "Focus",
                            daysOfWeek = "MON,WED,FRI",
                            startTimeHour = 22,
                            startTimeMinute = 0,
                            endTimeHour = 2,
                            endTimeMinute = 0,
                            isEnabled = true,
                        ),
                    ),
                    onAddSchedule = {},
                    onEditSchedule = {},
                    onToggleEnabled = {},
                )
            }
        }

        // The read-only dots collapse into a single focusable node listing the days,
        // in Mon..Sun order, using the same SHORT names production renders.
        val expected = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
            .joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

        composeRule.onNode(hasContentDescription(expected))
            .assertIsDisplayed()
    }

    private fun hasToggleableState(state: ToggleableState): SemanticsMatcher =
        SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, state)
}

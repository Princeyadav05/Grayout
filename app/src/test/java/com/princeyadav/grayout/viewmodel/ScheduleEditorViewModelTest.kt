package com.princeyadav.grayout.viewmodel

import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.fakes.FakeScheduleAlarmManager
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleEditorViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var dao: FakeScheduleDao
    private lateinit var repository: ScheduleRepository
    private lateinit var alarm: FakeScheduleAlarmManager
    private lateinit var vm: ScheduleEditorViewModel

    @Before
    fun setUp() {
        dao = FakeScheduleDao()
        repository = ScheduleRepository(dao)
        alarm = FakeScheduleAlarmManager()
        vm = ScheduleEditorViewModel(repository, alarm)
    }

    @Test
    fun `loadSchedule populates all fields from repository`() = runTest {
        val id = dao.insert(
            Schedule(
                id = 0L,
                name = "Focus",
                daysOfWeek = "MON,WED,FRI",
                startTimeHour = 8,
                startTimeMinute = 30,
                endTimeHour = 16,
                endTimeMinute = 45,
                isEnabled = true,
            )
        )

        vm.loadSchedule(id)
        advanceUntilIdle()

        assertEquals("Focus", vm.name.value)
        assertEquals(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            vm.selectedDays.value,
        )
        assertEquals(8, vm.startHour.value)
        assertEquals(30, vm.startMinute.value)
        assertEquals(16, vm.endHour.value)
        assertEquals(45, vm.endMinute.value)
    }

    @Test
    fun `save with empty days sets overlapError to Select at least one day`() = runTest {
        vm.setName("test")
        // leave selectedDays empty
        vm.save()
        advanceUntilIdle()

        assertEquals("Select at least one day", vm.overlapError.value)
        assertFalse("Expected isSaved to stay false on validation failure", vm.isSaved.value)
    }

    @Test
    fun `save with overlap sets overlapError with conflicting schedule name`() = runTest {
        dao.insert(
            Schedule(
                id = 0L,
                name = "Existing",
                daysOfWeek = "MON",
                startTimeHour = 9,
                startTimeMinute = 0,
                endTimeHour = 11,
                endTimeMinute = 0,
                isEnabled = true,
            )
        )

        vm.setName("new")
        vm.toggleDay(DayOfWeek.MONDAY)
        vm.setStartTime(10, 0)
        vm.setEndTime(12, 0)
        vm.save()
        advanceUntilIdle()

        assertEquals("Conflicts with \"Existing\"", vm.overlapError.value)
        assertFalse("Expected isSaved to stay false when overlap is detected", vm.isSaved.value)
    }

    @Test
    fun `save with no overlap persists schedule triggers reschedule and emits isSaved true`() = runTest {
        vm.setName("Work")
        vm.toggleDay(DayOfWeek.MONDAY)
        vm.setStartTime(9, 0)
        vm.setEndTime(17, 0)

        vm.save()
        advanceUntilIdle()

        assertTrue("Expected isSaved to be true after successful save", vm.isSaved.value)
        assertNull("Expected no overlapError after successful save", vm.overlapError.value)
        assertTrue(
            "Expected at least one reschedule after save, had ${alarm.rescheduleCallCount}",
            alarm.rescheduleCallCount >= 1,
        )

        val enabled = dao.getEnabledSchedules()
        assertEquals("Expected exactly one enabled schedule in dao", 1, enabled.size)
        assertEquals("Work", enabled.first().name)
        assertEquals("MON", enabled.first().daysOfWeek)
    }

    @Test
    fun `deleteSchedule removes schedule triggers reschedule and emits isSaved true`() = runTest {
        val id = dao.insert(
            Schedule(
                id = 0L,
                name = "ToDelete",
                daysOfWeek = "MON,TUE",
                startTimeHour = 9,
                startTimeMinute = 0,
                endTimeHour = 17,
                endTimeMinute = 0,
                isEnabled = true,
            )
        )

        vm.loadSchedule(id)
        advanceUntilIdle()

        val baselineRescheduleCalls = alarm.rescheduleCallCount

        vm.deleteSchedule()
        advanceUntilIdle()

        assertTrue("Expected isSaved to be true after delete", vm.isSaved.value)
        assertTrue(
            "Expected at least one reschedule after delete, had ${alarm.rescheduleCallCount}",
            alarm.rescheduleCallCount > baselineRescheduleCalls,
        )
        assertNull("Expected schedule to be gone from dao after delete", dao.getById(id))
    }
}

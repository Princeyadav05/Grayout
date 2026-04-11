package com.princeyadav.grayout.viewmodel

import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.fakes.FakeScheduleAlarmManager
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.testutil.MainDispatcherRule
import com.princeyadav.grayout.testutil.fixedDateTime
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
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleViewModelTest {

    @get:Rule val dispatcherRule = MainDispatcherRule()

    private lateinit var dao: FakeScheduleDao
    private lateinit var repository: ScheduleRepository
    private lateinit var alarm: FakeScheduleAlarmManager

    @Before
    fun setUp() {
        dao = FakeScheduleDao()
        repository = ScheduleRepository(dao)
        alarm = FakeScheduleAlarmManager()
    }

    private fun vm(
        clock: () -> LocalDateTime = { fixedDateTime(DayOfWeek.MONDAY, 12, 0) },
    ): ScheduleViewModel = ScheduleViewModel(repository, alarm, clock)

    private fun makeSchedule(
        id: Long = 0L,
        name: String = "Test",
        daysOfWeek: String = "MON,TUE,WED,THU,FRI",
        startHour: Int = 9,
        startMinute: Int = 0,
        endHour: Int = 17,
        endMinute: Int = 0,
        isEnabled: Boolean = true,
    ): Schedule = Schedule(
        id = id,
        name = name,
        daysOfWeek = daysOfWeek,
        startTimeHour = startHour,
        startTimeMinute = startMinute,
        endTimeHour = endHour,
        endTimeMinute = endMinute,
        isEnabled = isEnabled,
    )

    @Test
    fun `refreshFiringState marks schedule firing when current time within window`() = runTest {
        val id = dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE,WED,THU,FRI",
                startHour = 9,
                endHour = 17,
                isEnabled = true,
            )
        )

        val viewModel = vm(clock = { fixedDateTime(DayOfWeek.MONDAY, 12, 0) })
        advanceUntilIdle()

        assertTrue(
            "Expected firingScheduleIds to contain id=$id, was ${viewModel.firingScheduleIds.value}",
            viewModel.firingScheduleIds.value.contains(id),
        )
    }

    @Test
    fun `refreshFiringState does not mark disabled schedule as firing`() = runTest {
        dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE,WED,THU,FRI",
                startHour = 9,
                endHour = 17,
                isEnabled = false,
            )
        )

        val viewModel = vm(clock = { fixedDateTime(DayOfWeek.MONDAY, 12, 0) })
        advanceUntilIdle()

        assertTrue(
            "Expected firingScheduleIds to be empty, was ${viewModel.firingScheduleIds.value}",
            viewModel.firingScheduleIds.value.isEmpty(),
        )
    }

    @Test
    fun `refreshFiringState does not mark schedule firing when current day not in schedule days`() = runTest {
        dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE",
                startHour = 9,
                endHour = 17,
                isEnabled = true,
            )
        )

        val viewModel = vm(clock = { fixedDateTime(DayOfWeek.SATURDAY, 12, 0) })
        advanceUntilIdle()

        assertTrue(
            "Expected firingScheduleIds to be empty on Saturday, was ${viewModel.firingScheduleIds.value}",
            viewModel.firingScheduleIds.value.isEmpty(),
        )
    }

    @Test
    fun `refreshFiringState handles midnight-crossing window at 23-30`() = runTest {
        val id = dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE,WED,THU,FRI,SAT,SUN",
                startHour = 22,
                startMinute = 0,
                endHour = 2,
                endMinute = 0,
                isEnabled = true,
            )
        )

        val viewModel = vm(clock = { fixedDateTime(DayOfWeek.MONDAY, 23, 30) })
        advanceUntilIdle()

        assertTrue(
            "Expected id=$id to be firing at Mon 23:30 inside 22-02 window, was ${viewModel.firingScheduleIds.value}",
            viewModel.firingScheduleIds.value.contains(id),
        )
    }

    @Test
    fun `toggleEnabled flips state and triggers alarm manager reschedule`() = runTest {
        val id = dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE,WED,THU,FRI",
                startHour = 9,
                endHour = 17,
                isEnabled = false,
            )
        )

        val viewModel = vm()
        advanceUntilIdle()

        val inserted = dao.getById(id)!!
        assertFalse("Precondition: schedule should start disabled", inserted.isEnabled)

        val baselineRescheduleCalls = alarm.rescheduleCallCount

        viewModel.toggleEnabled(inserted)
        advanceUntilIdle()

        val afterToggle = dao.getById(id)!!
        assertTrue("Expected schedule to be enabled after toggle", afterToggle.isEnabled)
        assertTrue(
            "Expected at least one reschedule after toggleEnabled, had ${alarm.rescheduleCallCount} total",
            alarm.rescheduleCallCount > baselineRescheduleCalls,
        )
    }

    @Test
    fun `deleteSchedule removes from repository and triggers reschedule`() = runTest {
        val id = dao.insert(
            makeSchedule(
                daysOfWeek = "MON,TUE,WED,THU,FRI",
                startHour = 9,
                endHour = 17,
                isEnabled = true,
            )
        )

        val viewModel = vm()
        advanceUntilIdle()

        val inserted = dao.getById(id)!!
        val baselineRescheduleCalls = alarm.rescheduleCallCount

        viewModel.deleteSchedule(inserted)
        advanceUntilIdle()

        assertNull("Expected schedule to be deleted from dao", dao.getById(id))
        assertEquals(
            "Expected no enabled schedules in dao after delete",
            0,
            dao.getEnabledSchedules().size,
        )
        assertTrue(
            "Expected at least one reschedule after deleteSchedule, had ${alarm.rescheduleCallCount} total",
            alarm.rescheduleCallCount > baselineRescheduleCalls,
        )
    }
}

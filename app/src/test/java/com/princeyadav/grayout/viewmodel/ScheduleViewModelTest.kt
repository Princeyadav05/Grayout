package com.princeyadav.grayout.viewmodel

import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.fakes.FakeScheduleAlarmManager
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.testutil.MainDispatcherRule
import com.princeyadav.grayout.testutil.fixedDateTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

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

    private fun TestScope.vm(
        clock: () -> LocalDateTime = { fixedDateTime(DayOfWeek.MONDAY, 12, 0) },
    ): ScheduleViewModel = ScheduleViewModel(repository, alarm) {
        Clock.fixed(clock().toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    }.also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.observeFiringState()
        }
    }

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
    fun `observer marks schedule firing when current time within window`() = runTest {
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
    fun `observer does not mark disabled schedule as firing`() = runTest {
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
    fun `observer does not mark schedule firing when current day not in schedule days`() = runTest {
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
    fun `observer handles midnight-crossing window at 23-30`() = runTest {
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
    fun `toggleEnabled refuses to enable a schedule overlapping an enabled one`() = runTest {
        dao.insert(makeSchedule(name = "Live", daysOfWeek = "MON", startHour = 9, endHour = 17))
        val id = dao.insert(
            makeSchedule(name = "Dupe", daysOfWeek = "MON", startHour = 10, endHour = 12, isEnabled = false),
        )

        val viewModel = vm()
        advanceUntilIdle()
        val conflicts = mutableListOf<String>()
        val job = launch { viewModel.enableConflict.collect { conflicts.add(it) } }
        val baselineRescheduleCalls = alarm.rescheduleCallCount

        viewModel.toggleEnabled(dao.getById(id)!!)
        advanceUntilIdle()

        assertFalse("must not enable into an overlap", dao.getById(id)!!.isEnabled)
        assertEquals("no reschedule when the enable is refused", baselineRescheduleCalls, alarm.rescheduleCallCount)
        assertEquals(listOf("Conflicts with \"Live\""), conflicts)
        job.cancel()
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

    @Test
    fun `visible badges update at start and end without querying the database again`() = runTest {
        val id = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 9, endHour = 10))
        val start = fixedDateTime(DayOfWeek.MONDAY, 8, 59)
        var queryCount = 0
        val countingDao = object : com.princeyadav.grayout.data.ScheduleDao by dao {
            override fun getAllSchedules() = kotlinx.coroutines.flow.flow {
                queryCount++
                dao.getAllSchedules().collect { emit(it) }
            }
        }
        val viewModel = ScheduleViewModel(ScheduleRepository(countingDao), alarm) {
            Clock.fixed(start.toInstant(ZoneOffset.UTC).plusMillis(testScheduler.currentTime), ZoneOffset.UTC)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.observeFiringState()
        }
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(setOf(id), viewModel.firingScheduleIds.value)

        advanceTimeBy(3_600_000)
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())
        assertEquals(1, queryCount)
    }

    @Test
    fun `interior starts update individual badges in overlapping legacy schedules`() = runTest {
        val first = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 9, endHour = 12))
        val second = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 10, endHour = 11))
        val start = fixedDateTime(DayOfWeek.MONDAY, 9, 59)
        val viewModel = vm { start.plusNanos(testScheduler.currentTime * 1_000_000) }
        runCurrent()
        assertEquals(setOf(first), viewModel.firingScheduleIds.value)

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(setOf(first, second), viewModel.firingScheduleIds.value)

        advanceTimeBy(3_600_000)
        runCurrent()
        assertEquals(setOf(first), viewModel.firingScheduleIds.value)
    }

    @Test
    fun `database changes replace the pending boundary`() = runTest {
        val id = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 9, endHour = 10))
        val start = fixedDateTime(DayOfWeek.MONDAY, 8, 59)
        val viewModel = vm { start.plusNanos(testScheduler.currentTime * 1_000_000) }
        runCurrent()
        dao.setEnabled(id, false)
        runCurrent()

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())

        dao.setEnabled(id, true)
        runCurrent()
        assertEquals(setOf(id), viewModel.firingScheduleIds.value)

        dao.delete(dao.getById(id)!!)
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())
    }

    @Test
    fun `cancelling observation stops the timer and resume refreshes immediately`() = runTest {
        val id = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 9, endHour = 10))
        val start = fixedDateTime(DayOfWeek.MONDAY, 8, 59).toInstant(ZoneOffset.UTC)
        var clockReads = 0
        val viewModel = ScheduleViewModel(repository, alarm) {
            clockReads++
            Clock.fixed(start.plusMillis(testScheduler.currentTime), ZoneOffset.UTC)
        }
        val observer = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.observeFiringState()
        }
        runCurrent()
        observer.cancel()
        runCurrent()
        val beforePause = clockReads

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(beforePause, clockReads)

        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.observeFiringState()
        }
        runCurrent()
        assertEquals(setOf(id), viewModel.firingScheduleIds.value)
    }

    @Test
    fun `midnight crossing badge ends on the following day`() = runTest {
        val id = dao.insert(makeSchedule(daysOfWeek = "MON", startHour = 22, endHour = 2))
        val start = fixedDateTime(DayOfWeek.TUESDAY, 1, 59)
        val viewModel = vm { start.plusNanos(testScheduler.currentTime * 1_000_000) }
        runCurrent()
        assertEquals(setOf(id), viewModel.firingScheduleIds.value)

        advanceTimeBy(60_000)
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())
    }

    @Test
    fun `daylight saving gap uses the same resolved boundary as alarms`() = runTest {
        val id = dao.insert(makeSchedule(daysOfWeek = "SUN", startHour = 2, startMinute = 30, endHour = 4))
        val zone = ZoneId.of("America/New_York")
        val start = Instant.parse("2026-03-08T07:29:00Z")
        val viewModel = ScheduleViewModel(repository, alarm) {
            Clock.fixed(start.plusMillis(testScheduler.currentTime), zone)
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.observeFiringState()
        }
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())

        advanceTimeBy(60_000)
        runCurrent()
        assertEquals(setOf(id), viewModel.firingScheduleIds.value)

        advanceTimeBy(30 * 60_000)
        runCurrent()
        assertTrue(viewModel.firingScheduleIds.value.isEmpty())
    }
}

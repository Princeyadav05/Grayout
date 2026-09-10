package com.princeyadav.grayout.viewmodel

import com.princeyadav.grayout.data.ScheduleDao
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.fakes.FakeScheduleAlarmManager
import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.testutil.MainDispatcherRule
import com.princeyadav.grayout.scheduling.AlarmScheduler
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
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
    fun `save with equal start and end sets overlapError and does not persist`() = runTest {
        vm.setName("bad")
        vm.toggleDay(DayOfWeek.MONDAY)
        vm.setStartTime(9, 0)
        vm.setEndTime(9, 0)

        vm.save()
        advanceUntilIdle()

        assertEquals("Start and end time can't be the same", vm.overlapError.value)
        assertFalse(vm.isSaved.value)
        assertTrue("nothing should be persisted", dao.getAll().isEmpty())
    }

    @Test
    fun `editing a disabled schedule preserves its disabled state`() = runTest {
        val id = dao.insert(
            Schedule(
                id = 0L,
                name = "Off",
                daysOfWeek = "MON",
                startTimeHour = 9,
                startTimeMinute = 0,
                endTimeHour = 17,
                endTimeMinute = 0,
                isEnabled = false,
            )
        )

        vm.loadSchedule(id)
        advanceUntilIdle()
        vm.setName("Off renamed")
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.isSaved.value)
        val saved = checkNotNull(dao.getById(id))
        assertFalse("editing must not silently re-enable a disabled schedule", saved.isEnabled)
        assertEquals("Off renamed", saved.name)
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

    @Test
    fun `repeated load of retained schedule preserves unsaved edits`() = runTest {
        val id = dao.insert(testSchedule())
        vm.loadSchedule(id)
        advanceUntilIdle()
        vm.setName("Unsaved name")
        vm.setStartTime(7, 30)
        vm.selectPreset("Weekends")

        vm.loadSchedule(id)
        advanceUntilIdle()

        assertEquals("Unsaved name", vm.name.value)
        assertEquals(7, vm.startHour.value)
        assertEquals(30, vm.startMinute.value)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), vm.selectedDays.value)
    }

    @Test
    fun `existing editor is not editable before initial load starts`() = runTest {
        val id = dao.insert(testSchedule())
        vm = ScheduleEditorViewModel(repository, alarm, scheduleId = id)

        assertFalse(vm.isReady.value)
        vm.setName("Placeholder edit")
        vm.selectPreset("Every day")
        vm.save()
        vm.deleteSchedule()
        advanceUntilIdle()

        assertEquals("", vm.name.value)
        assertEquals("Focus", dao.getById(id)?.name)
        assertEquals(0, alarm.rescheduleCallCount)
    }

    @Test
    fun `suspended initial load ignores repeated load and editing actions`() = runTest {
        val id = dao.insert(testSchedule())
        val gate = CompletableDeferred<Unit>()
        var readCount = 0
        val delayedDao = object : ScheduleDao by dao {
            override suspend fun getById(id: Long): Schedule? {
                readCount++
                gate.await()
                return dao.getById(id)
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(delayedDao), alarm)

        vm.loadSchedule(id)
        assertTrue(vm.isLoading.value)
        assertFalse(vm.isReady.value)
        vm.loadSchedule(id)
        runCurrent()
        vm.setName("Do not overwrite the loaded name")
        vm.selectPreset("Every day")
        vm.save()
        vm.deleteSchedule()
        assertEquals(1, readCount)

        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("Focus", vm.name.value)
        assertFalse(vm.isLoading.value)
        assertTrue(vm.isReady.value)
        assertEquals(1, dao.getAll().size)
        assertEquals(0, alarm.rescheduleCallCount)
    }

    @Test
    fun `rapid saves during suspended overlap check only persist once`() = runTest {
        val gate = CompletableDeferred<Unit>()
        var overlapChecks = 0
        val delayedDao = object : ScheduleDao by dao {
            override suspend fun getAll(): List<Schedule> {
                overlapChecks++
                val snapshot = dao.getAll()
                gate.await()
                return snapshot
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(delayedDao), alarm)
        vm.setName("Focus")
        vm.selectPreset("Weekdays")

        vm.save()
        assertTrue(vm.isSaving.value)
        vm.save()
        runCurrent()
        vm.save()
        assertEquals(1, overlapChecks)

        gate.complete(Unit)
        advanceUntilIdle()
        vm.save()
        advanceUntilIdle()

        assertTrue(vm.isSaved.value)
        assertFalse(vm.isSaving.value)
        assertEquals(1, dao.getAll().size)
        assertEquals(1, alarm.rescheduleCallCount)
    }

    @Test
    fun `suspended save blocks deletion loading and changed fields`() = runTest {
        val id = dao.insert(testSchedule())
        val otherId = dao.insert(testSchedule().copy(name = "Other", daysOfWeek = "SAT"))
        val gate = CompletableDeferred<Unit>()
        val delayedDao = object : ScheduleDao by dao {
            override suspend fun update(schedule: Schedule) {
                gate.await()
                dao.update(schedule)
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(delayedDao), alarm)
        vm.loadSchedule(id)
        advanceUntilIdle()
        vm.setName("Saved name")

        vm.save()
        runCurrent()
        vm.deleteSchedule()
        vm.loadSchedule(otherId)
        vm.setName("Changed while saving")
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals("Saved name", dao.getById(id)?.name)
        assertEquals("Saved name", vm.name.value)
        assertEquals("Other", dao.getById(otherId)?.name)
        assertEquals(1, alarm.rescheduleCallCount)
    }

    @Test
    fun `suspended delete ignores repeated delete and save`() = runTest {
        val id = dao.insert(testSchedule())
        val gate = CompletableDeferred<Unit>()
        var deletes = 0
        val delayedDao = object : ScheduleDao by dao {
            override suspend fun delete(schedule: Schedule) {
                deletes++
                gate.await()
                dao.delete(schedule)
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(delayedDao), alarm)
        vm.loadSchedule(id)
        advanceUntilIdle()

        vm.deleteSchedule()
        assertTrue(vm.isSaving.value)
        vm.deleteSchedule()
        runCurrent()
        vm.save()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(1, deletes)
        assertNull(dao.getById(id))
        assertTrue(vm.isSaved.value)
        assertFalse(vm.isSaving.value)
        assertEquals(1, alarm.rescheduleCallCount)
    }

    @Test
    fun `failed load allows retry and prevents placeholder writes`() = runTest {
        val id = dao.insert(testSchedule())
        var shouldFail = true
        val failingDao = object : ScheduleDao by dao {
            override suspend fun getById(id: Long): Schedule? {
                if (shouldFail) error("Read failed")
                return dao.getById(id)
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(failingDao), alarm)
        vm.loadSchedule(id)
        advanceUntilIdle()

        assertFalse(vm.isLoading.value)
        assertFalse(vm.isReady.value)
        assertEquals("Couldn't load this schedule. Go back and try again.", vm.overlapError.value)
        vm.save()
        vm.deleteSchedule()
        advanceUntilIdle()
        assertEquals(1, dao.getAll().size)

        shouldFail = false
        vm.loadSchedule(id)
        advanceUntilIdle()
        assertTrue(vm.isReady.value)
        assertEquals("Focus", vm.name.value)
        assertNull(vm.overlapError.value)
    }

    @Test
    fun `failed insert clears saving state and allows retry`() = runTest {
        var shouldFail = true
        val failingDao = object : ScheduleDao by dao {
            override suspend fun insert(schedule: Schedule): Long {
                if (shouldFail) error("Insert failed")
                return dao.insert(schedule)
            }
        }
        vm = ScheduleEditorViewModel(ScheduleRepository(failingDao), alarm)
        vm.selectPreset("Every day")
        vm.save()
        advanceUntilIdle()

        assertFalse(vm.isSaving.value)
        assertFalse(vm.isSaved.value)
        assertEquals("Couldn't save this schedule. Try again.", vm.overlapError.value)

        shouldFail = false
        vm.save()
        advanceUntilIdle()
        assertEquals(1, dao.getAll().size)
        assertTrue(vm.isSaved.value)
        assertNull(vm.overlapError.value)
    }

    @Test
    fun `retry after failed alarm update keeps the inserted schedule id`() = runTest {
        var shouldFail = true
        val failingAlarm = object : AlarmScheduler {
            override suspend fun reschedule(repository: ScheduleRepository) {
                if (shouldFail) error("Alarm update failed")
            }
        }
        vm = ScheduleEditorViewModel(repository, failingAlarm)
        vm.selectPreset("Every day")
        vm.save()
        advanceUntilIdle()
        val id = dao.getAll().single().id
        assertFalse(vm.isSaved.value)
        assertFalse(vm.isSaving.value)

        shouldFail = false
        vm.save()
        advanceUntilIdle()
        assertEquals(id, dao.getAll().single().id)
        assertTrue(vm.isSaved.value)
    }

    @Test
    fun `delete retry can update alarms after the row was already removed`() = runTest {
        val id = dao.insert(testSchedule())
        var shouldFail = true
        val failingAlarm = object : AlarmScheduler {
            override suspend fun reschedule(repository: ScheduleRepository) {
                if (shouldFail) error("Alarm update failed")
            }
        }
        vm = ScheduleEditorViewModel(repository, failingAlarm)
        vm.loadSchedule(id)
        advanceUntilIdle()
        vm.deleteSchedule()
        advanceUntilIdle()
        assertNull(dao.getById(id))
        assertFalse(vm.isSaved.value)
        assertFalse(vm.isSaving.value)

        assertTrue(vm.isDeleted.value)
        vm.setName("Do not save after deletion")
        vm.setStartTime(6, 0)
        vm.selectPreset("Every day")
        vm.save()
        advanceUntilIdle()
        assertEquals("Focus", vm.name.value)
        assertEquals(9, vm.startHour.value)
        assertEquals(setOf(DayOfWeek.MONDAY), vm.selectedDays.value)
        assertFalse(vm.isSaved.value)
        assertNull(dao.getById(id))

        shouldFail = false
        vm.deleteSchedule()
        advanceUntilIdle()
        assertTrue(vm.isSaved.value)
    }

    private fun testSchedule() = Schedule(
        name = "Focus",
        daysOfWeek = "MON",
        startTimeHour = 9,
        startTimeMinute = 0,
        endTimeHour = 17,
        endTimeMinute = 0,
    )

}

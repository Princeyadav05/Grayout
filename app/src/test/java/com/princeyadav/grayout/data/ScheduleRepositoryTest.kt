package com.princeyadav.grayout.data

import com.princeyadav.grayout.fakes.FakeScheduleDao
import com.princeyadav.grayout.model.Schedule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ScheduleRepositoryTest {

    private lateinit var dao: FakeScheduleDao
    private lateinit var repository: ScheduleRepository

    @Before
    fun setUp() {
        dao = FakeScheduleDao()
        repository = ScheduleRepository(dao)
    }

    private fun schedule(
        id: Long = 0L,
        name: String = "Test",
        daysOfWeek: String = "MON",
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
    fun `findOverlap returns null when no existing schedules`() = runTest {
        assertNull(repository.findOverlap("MON", 9, 0, 17, 0))
    }

    @Test
    fun `findOverlap returns null when days don't intersect Mon vs Tue`() = runTest {
        dao.insert(schedule(daysOfWeek = "MON", startHour = 9, endHour = 17))

        val result = repository.findOverlap("TUE", 9, 0, 17, 0)

        assertNull(result)
    }

    @Test
    fun `findOverlap returns conflicting schedule when same day and times overlap`() = runTest {
        dao.insert(
            schedule(
                name = "Existing",
                daysOfWeek = "MON",
                startHour = 9,
                startMinute = 0,
                endHour = 12,
                endMinute = 0,
            )
        )

        val result = repository.findOverlap("MON", 11, 0, 13, 0)

        assertNotNull("Expected overlap to be detected", result)
        assertEquals("Existing", result!!.name)
    }

    @Test
    fun `findOverlap returns null when times are adjacent but non-overlapping`() = runTest {
        dao.insert(
            schedule(
                name = "Morning",
                daysOfWeek = "MON",
                startHour = 9,
                startMinute = 0,
                endHour = 10,
                endMinute = 0,
            )
        )

        val result = repository.findOverlap("MON", 10, 0, 11, 0)

        assertNull("Adjacent ranges 9-10 and 10-11 should not overlap (strict <)", result)
    }

    @Test
    fun `findOverlap with excludeId matching the existing schedule returns null`() = runTest {
        val id = dao.insert(
            schedule(
                name = "Self",
                daysOfWeek = "MON",
                startHour = 9,
                startMinute = 0,
                endHour = 17,
                endMinute = 0,
            )
        )

        val result = repository.findOverlap("MON", 9, 0, 17, 0, excludeId = id)

        assertNull("excludeId should exclude the schedule being edited", result)
    }

    @Test
    fun `findOverlap handles midnight-crossing window overlap correctly`() = runTest {
        dao.insert(
            schedule(
                name = "Night",
                daysOfWeek = "MON",
                startHour = 22,
                startMinute = 0,
                endHour = 2,
                endMinute = 0,
            )
        )

        val result = repository.findOverlap("MON", 23, 0, 1, 0)

        assertNotNull(
            "Expected overlap within midnight-crossing window 22-02 vs 23-01",
            result,
        )
        assertEquals("Night", result!!.name)
    }
}

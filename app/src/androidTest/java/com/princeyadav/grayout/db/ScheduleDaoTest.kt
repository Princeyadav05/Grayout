package com.princeyadav.grayout.db

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleDao
import com.princeyadav.grayout.model.Schedule
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DAO round-trip tests against a Room in-memory database.
 *
 * Runs on a real device/emulator because Room's code-gen and SQLite live in
 * the Android runtime. No WRITE_SECURE_SETTINGS or other system permissions
 * are required here.
 */
@RunWith(AndroidJUnit4::class)
class ScheduleDaoTest {

    private lateinit var db: GrayoutDatabase
    private lateinit var dao: ScheduleDao

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, GrayoutDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.scheduleDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insert_and_query_by_id_roundtrips() = runBlocking {
        val inserted = dao.insert(
            Schedule(
                id = 0L,
                name = "Test",
                daysOfWeek = "MON,TUE",
                startTimeHour = 9,
                startTimeMinute = 0,
                endTimeHour = 17,
                endTimeMinute = 0,
                isEnabled = true,
            )
        )
        assertTrue(inserted > 0)

        val fetched = dao.getById(inserted)
        assertNotNull(fetched)
        assertEquals("Test", fetched!!.name)
        assertEquals("MON,TUE", fetched.daysOfWeek)
        assertEquals(9, fetched.startTimeHour)
        assertEquals(17, fetched.endTimeHour)
        assertTrue(fetched.isEnabled)
    }

    @Test
    fun delete_removes_schedule() = runBlocking {
        val id = dao.insert(
            Schedule(
                id = 0L,
                name = "X",
                daysOfWeek = "MON",
                startTimeHour = 8,
                startTimeMinute = 0,
                endTimeHour = 10,
                endTimeMinute = 0,
                isEnabled = true,
            )
        )
        val schedule = dao.getById(id)
        assertNotNull(schedule)

        dao.delete(schedule!!)
        assertNull(dao.getById(id))
    }

    @Test
    fun setEnabled_updates_flag_and_leaves_other_fields_untouched() = runBlocking {
        val id = dao.insert(
            Schedule(
                id = 0L,
                name = "Y",
                daysOfWeek = "WED",
                startTimeHour = 14,
                startTimeMinute = 30,
                endTimeHour = 16,
                endTimeMinute = 0,
                isEnabled = true,
            )
        )

        dao.setEnabled(id, false)

        val after = dao.getById(id)
        assertNotNull(after)
        assertFalse(after!!.isEnabled)
        assertEquals("Y", after.name)
        assertEquals("WED", after.daysOfWeek)
        assertEquals(14, after.startTimeHour)
        assertEquals(30, after.startTimeMinute)
        assertEquals(16, after.endTimeHour)
        assertEquals(0, after.endTimeMinute)
    }
}

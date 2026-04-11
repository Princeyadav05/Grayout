package com.princeyadav.grayout.fakes

import com.princeyadav.grayout.data.ScheduleDao
import com.princeyadav.grayout.model.Schedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-memory fake [ScheduleDao] for JVM tests.
 *
 * Wrap the real [com.princeyadav.grayout.data.ScheduleRepository] with this
 * fake DAO instead of maintaining a separate FakeScheduleRepository — this
 * keeps the repository's overlap/save logic under test coverage.
 *
 * Notes:
 * - All suspend signatures match the real DAO. Tests must use `runTest { }`.
 * - [getAllSchedules] returns a [MutableStateFlow] so collectors are updated
 *   immediately on insert/update/delete — no manual re-emission required.
 * - Auto-generated IDs start at 1 and increment on each insert.
 */
class FakeScheduleDao : ScheduleDao {
    private val store = MutableStateFlow<List<Schedule>>(emptyList())
    private var nextId = 1L

    override fun getAllSchedules(): Flow<List<Schedule>> = store

    override suspend fun getEnabledSchedules(): List<Schedule> =
        store.value.filter { it.isEnabled }

    override suspend fun getById(id: Long): Schedule? =
        store.value.firstOrNull { it.id == id }

    override suspend fun insert(schedule: Schedule): Long {
        val id = nextId++
        store.value = store.value + schedule.copy(id = id)
        return id
    }

    override suspend fun update(schedule: Schedule) {
        store.value = store.value.map { if (it.id == schedule.id) schedule else it }
    }

    override suspend fun delete(schedule: Schedule) {
        store.value = store.value.filterNot { it.id == schedule.id }
    }

    override suspend fun setEnabled(id: Long, enabled: Boolean) {
        store.value = store.value.map {
            if (it.id == id) it.copy(isEnabled = enabled) else it
        }
    }
}

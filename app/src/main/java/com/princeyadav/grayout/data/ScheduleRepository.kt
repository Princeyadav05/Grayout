package com.princeyadav.grayout.data

import com.princeyadav.grayout.logic.schedulesOverlap
import com.princeyadav.grayout.model.Schedule
import kotlinx.coroutines.flow.Flow

class ScheduleRepository(private val dao: ScheduleDao) {

    fun getAllSchedules(): Flow<List<Schedule>> = dao.getAllSchedules()

    suspend fun getEnabledSchedules(): List<Schedule> = dao.getEnabledSchedules()

    suspend fun getById(id: Long): Schedule? = dao.getById(id)

    suspend fun save(schedule: Schedule): Long {
        return if (schedule.id == 0L) {
            dao.insert(schedule)
        } else {
            dao.update(schedule)
            schedule.id
        }
    }

    suspend fun delete(schedule: Schedule) = dao.delete(schedule)

    suspend fun setEnabled(id: Long, enabled: Boolean) = dao.setEnabled(id, enabled)

    /**
     * The first stored schedule whose window overlaps the candidate window in
     * real time, or null. Checks *all* schedules (enabled or not): the editor is
     * the only creation path, so refusing any geometric overlap here keeps the DB
     * free of overlapping pairs, which in turn makes re-enabling a schedule safe
     * without a separate toggle-time check.
     */
    suspend fun findOverlap(
        daysOfWeek: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        excludeId: Long = 0L,
    ): Schedule? {
        val candidate = Schedule(
            name = "",
            daysOfWeek = daysOfWeek,
            startTimeHour = startHour,
            startTimeMinute = startMinute,
            endTimeHour = endHour,
            endTimeMinute = endMinute,
        )
        return dao.getAll().firstOrNull { existing ->
            existing.id != excludeId && schedulesOverlap(candidate, existing)
        }
    }
}

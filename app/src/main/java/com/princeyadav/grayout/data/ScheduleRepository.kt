package com.princeyadav.grayout.data

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

    suspend fun findOverlap(
        daysOfWeek: String,
        startHour: Int,
        startMinute: Int,
        endHour: Int,
        endMinute: Int,
        excludeId: Long = 0L,
    ): Schedule? {
        val newDays = daysOfWeek.split(",").toSet()
        val newStart = startHour * 60 + startMinute
        val newEnd = endHour * 60 + endMinute

        val existing = dao.getEnabledSchedules()
        return existing.firstOrNull { schedule ->
            if (schedule.id == excludeId) return@firstOrNull false

            val existDays = schedule.daysOfWeek.split(",").toSet()
            if (newDays.intersect(existDays).isEmpty()) return@firstOrNull false

            val existStart = schedule.startTimeHour * 60 + schedule.startTimeMinute
            val existEnd = schedule.endTimeHour * 60 + schedule.endTimeMinute

            timeRangesOverlap(newStart, newEnd, existStart, existEnd)
        }
    }

    private fun timeRangesOverlap(
        aStart: Int, aEnd: Int,
        bStart: Int, bEnd: Int,
    ): Boolean {
        val aRanges = if (aStart < aEnd) listOf(aStart to aEnd)
                      else listOf(aStart to 1440, 0 to aEnd)
        val bRanges = if (bStart < bEnd) listOf(bStart to bEnd)
                      else listOf(bStart to 1440, 0 to bEnd)

        return aRanges.any { (as1, ae1) ->
            bRanges.any { (bs1, be1) ->
                as1 < be1 && bs1 < ae1
            }
        }
    }
}

package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModel() {

    val schedules = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _firingScheduleIds = MutableStateFlow<Set<Long>>(emptySet())
    val firingScheduleIds: StateFlow<Set<Long>> = _firingScheduleIds.asStateFlow()

    init {
        refreshFiringState()
    }

    fun refreshFiringState() {
        viewModelScope.launch {
            val allEnabled = repository.getEnabledSchedules()
            val now = LocalDateTime.now()
            _firingScheduleIds.value = allEnabled
                .filter { isCurrentlyFiring(it, now) }
                .map { it.id }
                .toSet()
        }
    }

    fun toggleEnabled(schedule: Schedule) {
        viewModelScope.launch {
            repository.setEnabled(schedule.id, !schedule.isEnabled)
            alarmManager.reschedule(repository)
            refreshFiringState()
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.delete(schedule)
            alarmManager.reschedule(repository)
            refreshFiringState()
        }
    }
}

private fun isCurrentlyFiring(schedule: Schedule, now: LocalDateTime): Boolean {
    if (!schedule.isEnabled) return false
    val days = schedule.daysOfWeekList
    if (now.dayOfWeek !in days) return false
    val currentTime = now.toLocalTime()
    val schedStart = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)
    val schedEnd = LocalTime.of(schedule.endTimeHour, schedule.endTimeMinute)
    return if (schedStart.isBefore(schedEnd)) {
        !currentTime.isBefore(schedStart) && currentTime.isBefore(schedEnd)
    } else {
        // Window crosses midnight
        !currentTime.isBefore(schedStart) || currentTime.isBefore(schedEnd)
    }
}

class ScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleViewModel(repository, alarmManager) as T
    }
}

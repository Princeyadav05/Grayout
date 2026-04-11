package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.logic.isCurrentlyFiring
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.AlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
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
            val now = clock()
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

class ScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleViewModel(repository, alarmManager, clock) as T
    }
}

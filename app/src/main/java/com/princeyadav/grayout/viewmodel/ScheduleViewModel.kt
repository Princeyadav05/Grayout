package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModel() {

    val schedules = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleEnabled(schedule: Schedule) {
        viewModelScope.launch {
            repository.setEnabled(schedule.id, !schedule.isEnabled)
            alarmManager.reschedule(repository)
        }
    }

    fun deleteSchedule(schedule: Schedule) {
        viewModelScope.launch {
            repository.delete(schedule)
            alarmManager.reschedule(repository)
        }
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

package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModel() {

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    private val _selectedDays = MutableStateFlow<Set<DayOfWeek>>(emptySet())
    val selectedDays: StateFlow<Set<DayOfWeek>> = _selectedDays

    private val _startHour = MutableStateFlow(9)
    val startHour: StateFlow<Int> = _startHour

    private val _startMinute = MutableStateFlow(0)
    val startMinute: StateFlow<Int> = _startMinute

    private val _endHour = MutableStateFlow(17)
    val endHour: StateFlow<Int> = _endHour

    private val _endMinute = MutableStateFlow(0)
    val endMinute: StateFlow<Int> = _endMinute

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _overlapError = MutableStateFlow<String?>(null)
    val overlapError: StateFlow<String?> = _overlapError

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private var editingScheduleId: Long = 0L

    fun loadSchedule(id: Long) {
        viewModelScope.launch {
            val schedule = repository.getById(id) ?: return@launch
            editingScheduleId = id
            _name.value = schedule.name
            _selectedDays.value = schedule.daysOfWeek.split(",")
                .map { DayOfWeek.valueOf(it.trim()) }
                .toSet()
            _startHour.value = schedule.startTimeHour
            _startMinute.value = schedule.startTimeMinute
            _endHour.value = schedule.endTimeHour
            _endMinute.value = schedule.endTimeMinute
        }
    }

    fun setName(name: String) {
        _name.value = name
        _overlapError.value = null
    }

    fun toggleDay(day: DayOfWeek) {
        _selectedDays.value = _selectedDays.value.let {
            if (day in it) it - day else it + day
        }
        _overlapError.value = null
    }

    fun setStartTime(hour: Int, minute: Int) {
        _startHour.value = hour
        _startMinute.value = minute
        _overlapError.value = null
    }

    fun setEndTime(hour: Int, minute: Int) {
        _endHour.value = hour
        _endMinute.value = minute
        _overlapError.value = null
    }

    fun selectPreset(preset: String) {
        _selectedDays.value = when (preset) {
            "Weekdays" -> setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            )
            "Weekends" -> setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            "Every day" -> DayOfWeek.entries.toSet()
            else -> _selectedDays.value
        }
        _overlapError.value = null
    }

    fun save() {
        viewModelScope.launch {
            val name = _name.value.ifBlank { "Schedule" }
            val days = _selectedDays.value
            if (days.isEmpty()) {
                _overlapError.value = "Select at least one day"
                return@launch
            }

            val daysOfWeek = days.sorted().joinToString(",") {
                it.name.take(3)
            }

            val overlap = repository.findOverlap(
                daysOfWeek,
                _startHour.value, _startMinute.value,
                _endHour.value, _endMinute.value,
                excludeId = editingScheduleId,
            )
            if (overlap != null) {
                _overlapError.value = "Conflicts with \"${overlap.name}\""
                return@launch
            }

            val schedule = Schedule(
                id = editingScheduleId,
                name = name,
                daysOfWeek = daysOfWeek,
                startTimeHour = _startHour.value,
                startTimeMinute = _startMinute.value,
                endTimeHour = _endHour.value,
                endTimeMinute = _endMinute.value,
            )
            repository.save(schedule)
            alarmManager.reschedule(repository)
            _isSaved.value = true
        }
    }

    fun deleteSchedule() {
        viewModelScope.launch {
            val schedule = repository.getById(editingScheduleId) ?: return@launch
            repository.delete(schedule)
            alarmManager.reschedule(repository)
            _isSaved.value = true
        }
    }
}

class ScheduleEditorViewModelFactory(
    private val repository: ScheduleRepository,
    private val alarmManager: ScheduleAlarmManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleEditorViewModel(repository, alarmManager) as T
    }
}

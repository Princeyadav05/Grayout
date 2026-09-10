package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.scheduling.AlarmScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    scheduleId: Long = 0L,
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

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving

    private val _isDeleted = MutableStateFlow(false)
    val isDeleted: StateFlow<Boolean> = _isDeleted

    private val _isReady = MutableStateFlow(scheduleId == 0L)
    val isReady: StateFlow<Boolean> = _isReady

    private val _overlapError = MutableStateFlow<String?>(null)
    val overlapError: StateFlow<String?> = _overlapError

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private var editingScheduleId: Long = scheduleId
    private var editingIsEnabled: Boolean = true

    private val isBusy: Boolean
        get() = _isLoading.value || _isSaving.value || _isSaved.value

    fun loadSchedule(id: Long) {
        if (id <= 0L || isBusy || _isDeleted.value || (editingScheduleId == id && _isReady.value)) return
        editingScheduleId = id
        _isReady.value = false
        _isLoading.value = true
        _overlapError.value = null
        viewModelScope.launch {
            try {
                val schedule = repository.getById(id)
                if (schedule == null) {
                    _overlapError.value = "This schedule is no longer available. Go back to schedules."
                    return@launch
                }
                editingIsEnabled = schedule.isEnabled
                _name.value = schedule.name
                _selectedDays.value = schedule.daysOfWeekList.toSet()
                _startHour.value = schedule.startTimeHour
                _startMinute.value = schedule.startTimeMinute
                _endHour.value = schedule.endTimeHour
                _endMinute.value = schedule.endTimeMinute
                _isReady.value = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _overlapError.value = "Couldn't load this schedule. Go back and try again."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setName(name: String) {
        if (isBusy || !_isReady.value || _isDeleted.value) return
        _name.value = name
        _overlapError.value = null
    }

    fun toggleDay(day: DayOfWeek) {
        if (isBusy || !_isReady.value || _isDeleted.value) return
        _selectedDays.value = _selectedDays.value.let {
            if (day in it) it - day else it + day
        }
        _overlapError.value = null
    }

    fun setStartTime(hour: Int, minute: Int) {
        if (isBusy || !_isReady.value || _isDeleted.value) return
        _startHour.value = hour
        _startMinute.value = minute
        _overlapError.value = null
    }

    fun setEndTime(hour: Int, minute: Int) {
        if (isBusy || !_isReady.value || _isDeleted.value) return
        _endHour.value = hour
        _endMinute.value = minute
        _overlapError.value = null
    }

    fun selectPreset(preset: String) {
        if (isBusy || !_isReady.value || _isDeleted.value) return
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
        if (isBusy || !_isReady.value || _isDeleted.value) return
        // Snapshot the edited fields so validation and persistence use the same values.
        val name = _name.value.ifBlank { "Schedule" }
        val days = _selectedDays.value
        val startHour = _startHour.value
        val startMinute = _startMinute.value
        val endHour = _endHour.value
        val endMinute = _endMinute.value
        if (days.isEmpty()) {
            _overlapError.value = "Select at least one day"
            return
        }
        if (startHour == endHour && startMinute == endMinute) {
            _overlapError.value = "Start and end time can't be the same"
            return
        }

        // Set before launching: a second tap can arrive before the coroutine starts.
        _isSaving.value = true
        _overlapError.value = null
        viewModelScope.launch {
            var didPersist = false
            try {
                val daysOfWeek = days.sorted().joinToString(",") { it.name.take(3) }
                val overlap = repository.findOverlap(
                    daysOfWeek,
                    startHour, startMinute,
                    endHour, endMinute,
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
                    startTimeHour = startHour,
                    startTimeMinute = startMinute,
                    endTimeHour = endHour,
                    endTimeMinute = endMinute,
                    isEnabled = editingIsEnabled,
                )
                // Retain the inserted ID so retrying a failed alarm update cannot
                // insert a second schedule.
                editingScheduleId = repository.save(schedule)
                didPersist = true
                alarmManager.reschedule(repository)
                _isSaved.value = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _overlapError.value = if (didPersist) {
                    "Schedule saved, but alarms couldn't be updated. Tap Save to retry."
                } else {
                    "Couldn't save this schedule. Try again."
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

    fun deleteSchedule() {
        if (isBusy || !_isReady.value || editingScheduleId == 0L) return
        _isSaving.value = true
        _overlapError.value = null
        viewModelScope.launch {
            var didDelete = false
            try {
                if (!_isDeleted.value) {
                    repository.getById(editingScheduleId)?.let { repository.delete(it) }
                    _isDeleted.value = true
                }
                didDelete = true
                alarmManager.reschedule(repository)
                _isSaved.value = true
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _overlapError.value = if (didDelete) {
                    "Schedule deleted, but alarms couldn't be updated. Tap Delete schedule to retry."
                } else {
                    "Couldn't delete this schedule. Try again."
                }
            } finally {
                _isSaving.value = false
            }
        }
    }

}

class ScheduleEditorViewModelFactory(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val scheduleId: Long = 0L,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleEditorViewModel(repository, alarmManager, scheduleId) as T
    }
}

package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.logic.isCurrentlyFiring
import com.princeyadav.grayout.logic.nextScheduleEvent
import com.princeyadav.grayout.logic.schedulesOverlap
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.AlarmScheduler
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : ViewModel() {

    val schedules = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _firingScheduleIds = MutableStateFlow<Set<Long>>(emptySet())
    val firingScheduleIds: StateFlow<Set<Long>> = _firingScheduleIds.asStateFlow()

    private val _enableConflict = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val enableConflict: SharedFlow<String> = _enableConflict.asSharedFlow()

    /** Collect only while the screen is resumed; cancellation stops the boundary timer. */
    suspend fun observeFiringState() {
        schedules.collectLatest { currentSchedules ->
            while (true) {
                val reading = clock()
                val now = reading.instant()
                val zone = reading.zone
                _firingScheduleIds.value = currentSchedules
                    .filter { isCurrentlyFiring(it, now, zone) }
                    .mapTo(mutableSetOf()) { it.id }

                // Each row needs its own boundary: aggregate alarm scheduling can
                // skip an interior start when another legacy window is already active.
                val nextBoundary = currentSchedules.mapNotNull { schedule ->
                    nextScheduleEvent(listOf(schedule), now, zone)
                        ?.dateTime?.atZone(zone)?.toInstant()
                }.minOrNull() ?: break
                delay(Duration.between(now, nextBoundary).toMillis().coerceAtLeast(1L))
            }
        }
    }

    fun toggleEnabled(schedule: Schedule) {
        viewModelScope.launch {
            // Re-enabling must not create two overlapping live windows. The editor
            // blocks creating overlaps, but a schedule disabled while another was
            // edited around it, or legacy data, can still collide on enable.
            if (!schedule.isEnabled) {
                val conflict = repository.getEnabledSchedules()
                    .firstOrNull { it.id != schedule.id && schedulesOverlap(schedule, it) }
                if (conflict != null) {
                    _enableConflict.tryEmit("Conflicts with \"${conflict.name}\"")
                    return@launch
                }
            }
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
    private val alarmManager: AlarmScheduler,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleViewModel(repository, alarmManager, clock) as T
    }
}

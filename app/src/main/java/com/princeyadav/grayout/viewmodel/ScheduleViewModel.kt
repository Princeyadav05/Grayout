package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.logic.isCurrentlyFiring
import com.princeyadav.grayout.logic.TodayState
import com.princeyadav.grayout.logic.nextScheduleLineChange
import com.princeyadav.grayout.logic.schedulesOverlap
import com.princeyadav.grayout.logic.todayState
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.scheduling.AlarmScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.temporal.ChronoUnit

class ScheduleViewModel(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val canWriteSecureSettings: () -> Boolean,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : ViewModel() {

    val schedules = repository.getAllSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _firingScheduleIds = MutableStateFlow<Set<Long>>(emptySet())
    val firingScheduleIds: StateFlow<Set<Long>> = _firingScheduleIds.asStateFlow()

    /** Why the last enable was refused, shown inline under that schedule's row. Cleared on the next toggle. */
    private val _enableError = MutableStateFlow<Pair<Long, String>?>(null)
    val enableError: StateFlow<Pair<Long, String>?> = _enableError.asStateFlow()

    private val _today = MutableStateFlow<TodayState?>(null)
    internal val today: StateFlow<TodayState?> = _today.asStateFlow()

    /** Collect only while the screen is resumed; cancellation stops the boundary timer. */
    suspend fun observeFiringState(timeChanges: Flow<Unit> = emptyFlow()) {
        combine(schedules, timeChanges.onStart { emit(Unit) }) { currentSchedules, _ ->
            currentSchedules
        }.collectLatest { currentSchedules ->
            while (true) {
                val reading = clock()
                val now = reading.instant()
                val zone = reading.zone
                _firingScheduleIds.value = currentSchedules
                    .filter { isCurrentlyFiring(it, now, zone) }
                    .mapTo(mutableSetOf()) { it.id }
                _today.value = todayState(currentSchedules, now, zone)

                // Each row needs its own boundary (per-schedule, instant-based), and the
                // Today strip's now marker and date move every minute while visible.
                val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
                val nextBoundary = nextScheduleLineChange(currentSchedules, now, zone)
                val wake = if (nextBoundary != null && nextBoundary.isBefore(nextMinute)) nextBoundary else nextMinute
                delay(Duration.between(now, wake).toMillis().coerceAtLeast(1L))
            }
        }
    }

    fun toggleEnabled(schedule: Schedule) {
        _enableError.value = null
        viewModelScope.launch {
            // Re-enabling must not create two overlapping live windows. The editor
            // blocks creating overlaps, but a schedule disabled while another was
            // edited around it, or legacy data, can still collide on enable.
            if (!schedule.isEnabled) {
                if (!canWriteSecureSettings()) {
                    _enableError.value = schedule.id to "Turn on the grayscale permission first."
                    return@launch
                }
                val conflict = repository.getEnabledSchedules()
                    .firstOrNull { it.id != schedule.id && schedulesOverlap(schedule, it) }
                if (conflict != null) {
                    _enableError.value = schedule.id to "Overlaps with \"${conflict.name}\""
                    return@launch
                }
            }
            // Leaving the screen must not cancel between the commit and its alarms.
            withContext(NonCancellable) {
                repository.setEnabled(schedule.id, !schedule.isEnabled)
                alarmManager.reschedule(repository, keepManualChoice = true)
            }
        }
    }
}

class ScheduleViewModelFactory(
    private val repository: ScheduleRepository,
    private val alarmManager: AlarmScheduler,
    private val canWriteSecureSettings: () -> Boolean,
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScheduleViewModel(repository, alarmManager, canWriteSecureSettings, clock) as T
    }
}

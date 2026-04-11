package com.princeyadav.grayout.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.model.formatTime12Hour
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class HomeViewModel(
    private val grayscaleManager: GrayscaleController,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val isBatteryOptimized: () -> Boolean,
    private val loadExcludedIcons: (List<String>) -> Pair<List<Bitmap>, Int>,
    private val ioDispatcher: CoroutineDispatcher,
    private val ownPackageName: String,
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

    private val _nextScheduleText = MutableStateFlow("No active schedule")
    val nextScheduleText: StateFlow<String> = _nextScheduleText.asStateFlow()

    private val _excludedAppIcons = MutableStateFlow<List<Bitmap>>(emptyList())
    val excludedAppIcons: StateFlow<List<Bitmap>> = _excludedAppIcons.asStateFlow()

    private val _excludedOverflowCount = MutableStateFlow(0)
    val excludedOverflowCount: StateFlow<Int> = _excludedOverflowCount.asStateFlow()

    private val _needsAttentionCount = MutableStateFlow(0)
    val needsAttentionCount: StateFlow<Int> = _needsAttentionCount.asStateFlow()

    private val _navigateToSetup = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToSetup: SharedFlow<Unit> = _navigateToSetup.asSharedFlow()

    init {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        _enforcementInterval.value = enforcementPrefs.getInterval()
        refreshExcludedAppIcons()
        refreshAttentionCount()
    }

    fun refreshGrayscaleStateFromSystem() {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
    }

    fun toggleGrayscale() {
        val newValue = !_isGrayscaleOn.value
        viewModelScope.launch(ioDispatcher) {
            val success = grayscaleManager.setGrayscale(newValue)
            if (!success) {
                _navigateToSetup.tryEmit(Unit)
                return@launch
            }
            _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        }
    }

    fun setEnforcementInterval(minutes: Int) {
        if (minutes == 0) {
            enforcementPrefs.setInterval(0)
            _enforcementInterval.value = 0
            return
        }
        viewModelScope.launch(ioDispatcher) {
            if (!grayscaleManager.canWriteSecureSettings()) {
                _navigateToSetup.tryEmit(Unit)
                return@launch
            }
            enforcementPrefs.setInterval(minutes)
            _enforcementInterval.value = minutes
        }
    }

    fun refreshExcludedAppIcons() {
        viewModelScope.launch(ioDispatcher) {
            val (icons, overflow) = loadExcludedIcons(exclusionPrefs.getExcludedPackages().toList())
            _excludedAppIcons.value = icons
            _excludedOverflowCount.value = overflow
        }
    }

    fun refreshAttentionCount() {
        viewModelScope.launch(ioDispatcher) {
            var count = 0
            if (!grayscaleManager.canWriteSecureSettings()) count++
            if (!grayscaleManager.isAccessibilityServiceEnabled(ownPackageName)) count++
            if (!isBatteryOptimized()) count++
            _needsAttentionCount.value = count
        }
    }

    fun refreshNextSchedule(repository: ScheduleRepository) {
        viewModelScope.launch {
            val enabledSchedules = repository.getEnabledSchedules()
            if (enabledSchedules.isEmpty()) {
                _nextScheduleText.value = "No active schedule"
                return@launch
            }

            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            var nextStartTime: LocalDateTime? = null

            for (schedule in enabledSchedules) {
                val days = schedule.daysOfWeekList
                val schedStart = LocalTime.of(schedule.startTimeHour, schedule.startTimeMinute)

                for (dayOffset in 0L..7L) {
                    val checkDate = today.plusDays(dayOffset)
                    if (checkDate.dayOfWeek !in days) continue

                    val startDt = LocalDateTime.of(checkDate, schedStart)
                    if (startDt.isAfter(now)) {
                        if (nextStartTime == null || startDt.isBefore(nextStartTime)) {
                            nextStartTime = startDt
                        }
                        break
                    }
                }
            }

            _nextScheduleText.value = if (nextStartTime != null) {
                formatTime12Hour(nextStartTime.hour, nextStartTime.minute)
            } else {
                "No active schedule"
            }
        }
    }
}

class HomeViewModelFactory(
    private val grayscaleManager: GrayscaleController,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val isBatteryOptimized: () -> Boolean,
    private val loadExcludedIcons: (List<String>) -> Pair<List<Bitmap>, Int>,
    private val ioDispatcher: CoroutineDispatcher,
    private val ownPackageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            grayscaleManager,
            enforcementPrefs,
            exclusionPrefs,
            isBatteryOptimized,
            loadExcludedIcons,
            ioDispatcher,
            ownPackageName,
        ) as T
    }
}

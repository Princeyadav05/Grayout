package com.princeyadav.grayout.viewmodel

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.LocalTime

class HomeViewModel(
    private val contentResolver: ContentResolver,
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

    private val _nextScheduleText = MutableStateFlow("—")
    val nextScheduleText: StateFlow<String> = _nextScheduleText.asStateFlow()

    private val grayscaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        }
    }

    init {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        _enforcementInterval.value = enforcementPrefs.getInterval()
        contentResolver.registerContentObserver(
            Settings.Secure.getUriFor("accessibility_display_daltonizer_enabled"),
            false,
            grayscaleObserver,
        )
    }

    fun toggleGrayscale() {
        val newValue = !_isGrayscaleOn.value
        grayscaleManager.setGrayscale(newValue)
        _isGrayscaleOn.value = newValue
    }

    fun setEnforcementInterval(minutes: Int) {
        enforcementPrefs.setInterval(minutes)
        _enforcementInterval.value = minutes
    }

    fun refreshNextSchedule(repository: ScheduleRepository) {
        viewModelScope.launch {
            val enabledSchedules = repository.getEnabledSchedules()
            if (enabledSchedules.isEmpty()) {
                _nextScheduleText.value = "—"
                return@launch
            }

            val now = LocalDateTime.now()
            val today = now.toLocalDate()
            var nextStartTime: LocalDateTime? = null

            for (schedule in enabledSchedules) {
                val days = schedule.daysOfWeek.split(",").map { DayOfWeek.entries.first { d -> d.name.startsWith(it.trim()) } }
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
                "%02d:%02d".format(nextStartTime.hour, nextStartTime.minute)
            } else {
                "—"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentResolver.unregisterContentObserver(grayscaleObserver)
    }
}

class HomeViewModelFactory(
    private val contentResolver: ContentResolver,
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(contentResolver, grayscaleManager, enforcementPrefs) as T
    }
}

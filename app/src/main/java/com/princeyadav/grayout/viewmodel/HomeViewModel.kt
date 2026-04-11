package com.princeyadav.grayout.viewmodel

import android.content.ContentResolver
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.model.formatTime12Hour
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.LocalTime

class HomeViewModel(
    private val contentResolver: ContentResolver,
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val packageManager: PackageManager,
    private val powerManager: PowerManager,
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

    private val _toggleError = MutableStateFlow(false)
    val toggleError: StateFlow<Boolean> = _toggleError.asStateFlow()

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
        refreshExcludedAppIcons()
        refreshAttentionCount()
    }

    fun toggleGrayscale() {
        val newValue = !_isGrayscaleOn.value
        viewModelScope.launch(Dispatchers.IO) {
            grayscaleManager.setGrayscale(newValue)
            val actualState = grayscaleManager.isGrayscaleEnabled()
            _isGrayscaleOn.value = actualState
            if (actualState != newValue) {
                _toggleError.value = true
            } else {
                _toggleError.value = false
            }
        }
    }

    fun dismissToggleError() {
        _toggleError.value = false
    }

    fun setEnforcementInterval(minutes: Int) {
        enforcementPrefs.setInterval(minutes)
        _enforcementInterval.value = minutes
    }

    fun refreshExcludedAppIcons() {
        viewModelScope.launch(Dispatchers.IO) {
            val packages = exclusionPrefs.getExcludedPackages().toList()
            val icons = mutableListOf<Bitmap>()
            var loaded = 0
            var found = 0
            for (pkg in packages) {
                val bitmap = try {
                    packageManager.getApplicationIcon(pkg).toBitmap(width = 64, height = 64)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                if (bitmap != null) {
                    found++
                    if (loaded < 3) {
                        icons.add(bitmap)
                        loaded++
                    }
                }
            }
            _excludedAppIcons.value = icons
            _excludedOverflowCount.value = (found - loaded).coerceAtLeast(0)
        }
    }

    fun refreshAttentionCount() {
        viewModelScope.launch(Dispatchers.IO) {
            var count = 0
            if (!grayscaleManager.canWriteSecureSettings()) count++
            if (!grayscaleManager.isAccessibilityServiceEnabled(ownPackageName)) count++
            if (!powerManager.isIgnoringBatteryOptimizations(ownPackageName)) count++
            _needsAttentionCount.value = count
            if (grayscaleManager.canWriteSecureSettings() && _toggleError.value) {
                _toggleError.value = false
            }
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

    override fun onCleared() {
        super.onCleared()
        contentResolver.unregisterContentObserver(grayscaleObserver)
    }
}

class HomeViewModelFactory(
    private val contentResolver: ContentResolver,
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val packageManager: PackageManager,
    private val powerManager: PowerManager,
    private val ownPackageName: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            contentResolver,
            grayscaleManager,
            enforcementPrefs,
            exclusionPrefs,
            packageManager,
            powerManager,
            ownPackageName,
        ) as T
    }
}

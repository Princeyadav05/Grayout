package com.princeyadav.grayout.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.logic.nextScheduleStart
import com.princeyadav.grayout.model.formatTime12Hour
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Duration

class HomeViewModel(
    private val grayscaleManager: GrayscaleController,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val isBatteryOptimized: () -> Boolean,
    private val loadExcludedIcons: (List<String>) -> Pair<List<Bitmap>, Int>,
    private val ioDispatcher: CoroutineDispatcher,
    private val usageAccessProbe: () -> Boolean,
    serviceRunning: StateFlow<Boolean>,
    private val onEnforcementIntervalChanged: (Int) -> Unit = {},
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
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

    /** Live service-running status for the Settings diagnostics row. */
    val isServiceRunning: StateFlow<Boolean> = serviceRunning

    private val _navigateToSetup = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToSetup: SharedFlow<Unit> = _navigateToSetup.asSharedFlow()

    init {
        refreshSystemState()
    }

    /** Reconcile changes made through Quick Settings while the activity was stopped. */
    fun refreshSystemState() {
        refreshGrayscaleStateFromSystem()
        _enforcementInterval.value = enforcementPrefs.getInterval()
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
        if (minutes != 0 && !grayscaleManager.canWriteSecureSettings()) {
            _navigateToSetup.tryEmit(Unit)
            return
        }
        enforcementPrefs.setInterval(minutes)
        _enforcementInterval.value = minutes
        // Only explicit user changes command the service. Replaying UI state on
        // resume must not overwrite a newer interval set by the Quick Settings tile.
        onEnforcementIntervalChanged(minutes)
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
            if (!usageAccessProbe()) count++
            if (!isBatteryOptimized()) count++
            _needsAttentionCount.value = count
        }
    }

    /** Collect while Home is resumed; cancellation releases the query and boundary timer. */
    suspend fun observeNextSchedule(
        repository: ScheduleRepository,
        timeChanges: Flow<Unit> = emptyFlow(),
    ) {
        combine(
            repository.getAllSchedules(),
            timeChanges.onStart { emit(Unit) },
        ) { schedules, _ -> schedules }.collectLatest { schedules ->
            while (true) {
                val reading = clock()
                val now = reading.instant()
                val zone = reading.zone
                val next = nextScheduleStart(schedules, now, zone)
                _nextScheduleText.value = if (next == null) {
                    "No active schedule"
                } else {
                    formatTime12Hour(next.hour, next.minute)
                }
                if (next == null) break
                val nextInstant = next.atZone(zone).toInstant()
                delay(Duration.between(now, nextInstant).toMillis().coerceAtLeast(1L))
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
    private val usageAccessProbe: () -> Boolean,
    private val serviceRunning: StateFlow<Boolean>,
    private val onEnforcementIntervalChanged: (Int) -> Unit,
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
            usageAccessProbe,
            serviceRunning,
            onEnforcementIntervalChanged,
        ) as T
    }
}

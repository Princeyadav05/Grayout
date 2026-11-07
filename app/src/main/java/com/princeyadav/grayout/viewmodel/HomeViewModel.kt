package com.princeyadav.grayout.viewmodel

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.logic.activeScheduleLines
import com.princeyadav.grayout.logic.nextScheduleLineChange
import com.princeyadav.grayout.logic.scheduleColorIndex
import com.princeyadav.grayout.logic.scheduleLine
import com.princeyadav.grayout.logic.TodayState
import com.princeyadav.grayout.logic.todayState
import com.princeyadav.grayout.model.formatTime
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
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

class HomeViewModel(
    private val grayscaleManager: GrayscaleController,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val loadExcludedIcons: (List<String>) -> Pair<List<Bitmap>, Int>,
    private val ioDispatcher: CoroutineDispatcher,
    serviceRunning: StateFlow<Boolean>,
    private val onEnforcementIntervalChanged: (Int) -> Unit = {},
    private val clock: () -> Clock = { Clock.systemDefaultZone() },
    private val is24HourFormat: () -> Boolean = { false },
    private val notificationsAllowed: () -> Boolean = { true },
    private val onRestoreSettingsChanged: () -> Unit = {},
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

    /** The schedule line on Home's status card; null when nothing is running or upcoming. */
    private val _scheduleLine = MutableStateFlow<HomeScheduleLine?>(null)
    val scheduleLine: StateFlow<HomeScheduleLine?> = _scheduleLine.asStateFlow()

    /** Today's windows for the Status card strip; refreshed every minute while Home is resumed. */
    private val _today = MutableStateFlow<TodayState?>(null)
    internal val today: StateFlow<TodayState?> = _today.asStateFlow()

    private val _excludedAppIcons = MutableStateFlow<List<Bitmap>>(emptyList())
    val excludedAppIcons: StateFlow<List<Bitmap>> = _excludedAppIcons.asStateFlow()

    private val _excludedOverflowCount = MutableStateFlow(0)
    val excludedOverflowCount: StateFlow<Int> = _excludedOverflowCount.asStateFlow()

    /** Live service-running status for the Settings diagnostics row. */
    val isServiceRunning: StateFlow<Boolean> = serviceRunning

    /** Opens the setup guide on Home: the grayscale permission is missing. */
    private val _setupRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val setupRequests: SharedFlow<Unit> = _setupRequests.asSharedFlow()

    /** The grayscale state the last toggle failed to reach while the permission was granted, or null. */
    private val _toggleFailedTarget = MutableStateFlow<Boolean?>(null)
    val toggleFailedTarget: StateFlow<Boolean?> = _toggleFailedTarget.asStateFlow()

    private val _onlyDuringSchedules = MutableStateFlow(false)
    val onlyDuringSchedules: StateFlow<Boolean> = _onlyDuringSchedules.asStateFlow()

    /** In-app ask for notifications, raised the first time automatic restore is turned on. */
    private val _showNotificationPrompt = MutableStateFlow(false)
    val showNotificationPrompt: StateFlow<Boolean> = _showNotificationPrompt.asStateFlow()

    init {
        refreshSystemState()
    }

    /** Reconcile changes made through Quick Settings while the activity was stopped. */
    fun refreshSystemState() {
        refreshGrayscaleStateFromSystem()
        _enforcementInterval.value = enforcementPrefs.getInterval()
        _onlyDuringSchedules.value = enforcementPrefs.isOnlyDuringSchedules()
    }

    fun refreshGrayscaleStateFromSystem() {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        // Something else (a schedule, a restore tick, the tile) reached the failed target.
        if (_toggleFailedTarget.value == _isGrayscaleOn.value) _toggleFailedTarget.value = null
    }

    fun toggleGrayscale() = setGrayscaleTo(!_isGrayscaleOn.value)

    /** "Try again" aims at the state that failed, never the opposite of whatever is showing now. */
    fun retryFailedToggle() {
        _toggleFailedTarget.value?.let(::setGrayscaleTo)
    }

    /** The setup guide's "Try grayscale": turn it on, even if a toggle already did. */
    fun turnGrayscaleOn() = setGrayscaleTo(true)

    private fun setGrayscaleTo(newValue: Boolean) {
        viewModelScope.launch(ioDispatcher) {
            val success = grayscaleManager.setGrayscale(newValue)
            if (!success) {
                if (grayscaleManager.canWriteSecureSettings()) {
                    _toggleFailedTarget.value = newValue
                } else {
                    _toggleFailedTarget.value = null
                    _setupRequests.tryEmit(Unit)
                }
                _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
                return@launch
            }
            _toggleFailedTarget.value = null
            _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        }
    }

    fun setEnforcementInterval(minutes: Int) {
        if (minutes != 0 && !grayscaleManager.canWriteSecureSettings()) {
            _setupRequests.tryEmit(Unit)
            return
        }
        val wasOff = _enforcementInterval.value == 0
        enforcementPrefs.setInterval(minutes)
        _enforcementInterval.value = minutes
        if (wasOff && minutes > 0 && !notificationsAllowed() && !enforcementPrefs.isNotificationPromptDone()) {
            _showNotificationPrompt.value = true
        }
        // Only explicit user changes command the service. Replaying UI state on
        // resume must not overwrite a newer interval set by the Quick Settings tile.
        onEnforcementIntervalChanged(minutes)
    }

    /** "Only during schedules". The service re-checks any pending countdown against it. */
    fun setOnlyDuringSchedules(enabled: Boolean) {
        enforcementPrefs.setOnlyDuringSchedules(enabled)
        _onlyDuringSchedules.value = enabled
        onRestoreSettingsChanged()
    }

    /** Either prompt button. The answer is remembered so the card never comes back. */
    fun dismissNotificationPrompt() {
        enforcementPrefs.setNotificationPromptDone()
        _showNotificationPrompt.value = false
    }

    fun refreshExcludedAppIcons() {
        viewModelScope.launch(ioDispatcher) {
            val (icons, overflow) = loadExcludedIcons(exclusionPrefs.getExcludedPackages().toList())
            _excludedAppIcons.value = icons
            _excludedOverflowCount.value = overflow
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
                val is24Hour = is24HourFormat()
                _scheduleLine.value = scheduleLine(schedules, now, zone)?.let { line ->
                    val today = now.atZone(zone).toLocalDate()
                    HomeScheduleLine(
                        name = line.name,
                        colorIndex = scheduleColorIndex(line.scheduleId),
                        time = formatTime(line.at.hour, line.at.minute, is24Hour),
                        isActive = line.isActive,
                        dayPrefix = if (!line.isActive && line.at.toLocalDate() != today) {
                            line.at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                        } else {
                            null
                        },
                        active = activeScheduleLines(schedules, now, zone).map {
                            HomeActiveSchedule(
                                name = it.name,
                                colorIndex = scheduleColorIndex(it.scheduleId),
                                end = formatTime(it.at.hour, it.at.minute, is24Hour),
                            )
                        },
                    )
                }
                _today.value = todayState(schedules, now, zone)
                // The strip's now marker and date move every minute; schedule boundaries can come sooner.
                val nextMinute = now.truncatedTo(ChronoUnit.MINUTES).plus(1, ChronoUnit.MINUTES)
                val nextBoundary = nextScheduleLineChange(schedules, now, zone)
                val wake = if (nextBoundary != null && nextBoundary.isBefore(nextMinute)) nextBoundary else nextMinute
                delay(Duration.between(now, wake).toMillis().coerceAtLeast(1L))
            }
        }
    }

}

/**
 * [time] is the end when [isActive], else the next start; [dayPrefix] ("Tue") when not today.
 * [active] is every running schedule; more than one when windows overlap.
 */
data class HomeScheduleLine(
    val name: String,
    val colorIndex: Int,
    val time: String,
    val isActive: Boolean,
    val dayPrefix: String?,
    val active: List<HomeActiveSchedule> = emptyList(),
)

/** One running schedule on Home: its name, palette index and formatted configured end. */
data class HomeActiveSchedule(val name: String, val colorIndex: Int, val end: String)

class HomeViewModelFactory(
    private val grayscaleManager: GrayscaleController,
    private val enforcementPrefs: EnforcementPrefs,
    private val exclusionPrefs: ExclusionPrefs,
    private val loadExcludedIcons: (List<String>) -> Pair<List<Bitmap>, Int>,
    private val ioDispatcher: CoroutineDispatcher,
    private val serviceRunning: StateFlow<Boolean>,
    private val onEnforcementIntervalChanged: (Int) -> Unit,
    private val is24HourFormat: () -> Boolean,
    private val notificationsAllowed: () -> Boolean,
    private val onRestoreSettingsChanged: () -> Unit,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(
            grayscaleManager,
            enforcementPrefs,
            exclusionPrefs,
            loadExcludedIcons,
            ioDispatcher,
            serviceRunning,
            onEnforcementIntervalChanged,
            is24HourFormat = is24HourFormat,
            notificationsAllowed = notificationsAllowed,
            onRestoreSettingsChanged = onRestoreSettingsChanged,
        ) as T
    }
}

package com.princeyadav.grayout.viewmodel

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val contentResolver: ContentResolver,
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

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

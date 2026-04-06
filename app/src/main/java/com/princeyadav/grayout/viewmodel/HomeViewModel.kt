package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    private val _enforcementInterval = MutableStateFlow(0)
    val enforcementInterval: StateFlow<Int> = _enforcementInterval.asStateFlow()

    init {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
        _enforcementInterval.value = enforcementPrefs.getInterval()
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
}

class HomeViewModelFactory(
    private val grayscaleManager: GrayscaleManager,
    private val enforcementPrefs: EnforcementPrefs,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(grayscaleManager, enforcementPrefs) as T
    }
}

package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class HomeViewModel(private val grayscaleManager: GrayscaleManager) : ViewModel() {

    private val _isGrayscaleOn = MutableStateFlow(false)
    val isGrayscaleOn: StateFlow<Boolean> = _isGrayscaleOn.asStateFlow()

    init {
        _isGrayscaleOn.value = grayscaleManager.isGrayscaleEnabled()
    }

    fun toggleGrayscale() {
        val newValue = !_isGrayscaleOn.value
        grayscaleManager.setGrayscale(newValue)
        _isGrayscaleOn.value = newValue
    }
}

class HomeViewModelFactory(
    private val grayscaleManager: GrayscaleManager,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HomeViewModel(grayscaleManager) as T
    }
}

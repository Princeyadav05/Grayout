package com.princeyadav.grayout.viewmodel

import android.content.pm.PackageManager
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ExclusionViewModel(
    private val packageManager: PackageManager,
    private val exclusionPrefs: ExclusionPrefs,
    private val grayscaleManager: GrayscaleManager,
    private val ownPackage: String,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isAccessibilityEnabled = MutableStateFlow(true)
    val isAccessibilityEnabled: StateFlow<Boolean> = _isAccessibilityEnabled.asStateFlow()

    val filteredApps: StateFlow<List<AppInfo>> = combine(_apps, _searchQuery) { apps, query ->
        if (query.isBlank()) apps
        else apps.filter { it.appName.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val excludedApps: StateFlow<List<AppInfo>> = filteredApps
        .map { apps -> apps.filter { it.isExcluded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOtherApps: StateFlow<List<AppInfo>> = filteredApps
        .map { apps -> apps.filterNot { it.isExcluded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch(Dispatchers.IO) { loadApps() }
    }

    fun refreshApps() {
        viewModelScope.launch(Dispatchers.IO) { loadApps() }
    }

    private fun loadApps() {
        val installed = packageManager.getInstalledApplications(0)
            .filter { info ->
                packageManager.getLaunchIntentForPackage(info.packageName) != null &&
                    info.packageName != "com.princeyadav.grayout"
            }
            .map { info ->
                AppInfo(
                    packageName = info.packageName,
                    appName = info.loadLabel(packageManager).toString(),
                    icon = info.loadIcon(packageManager).toBitmap(width = 80, height = 80).asImageBitmap(),
                    isExcluded = exclusionPrefs.isExcluded(info.packageName),
                )
            }
            .sortedBy { it.appName.lowercase() }

        _apps.value = installed
    }

    fun toggleExclusion(packageName: String) {
        if (exclusionPrefs.isExcluded(packageName)) {
            exclusionPrefs.removeExcludedPackage(packageName)
        } else {
            exclusionPrefs.addExcludedPackage(packageName)
        }
        _apps.value = _apps.value.map { app ->
            if (app.packageName == packageName) app.copy(isExcluded = !app.isExcluded)
            else app
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkAccessibilityService() {
        _isAccessibilityEnabled.value = grayscaleManager.isAccessibilityServiceEnabled(ownPackage)
    }
}

class ExclusionViewModelFactory(
    private val packageManager: PackageManager,
    private val exclusionPrefs: ExclusionPrefs,
    private val grayscaleManager: GrayscaleManager,
    private val ownPackage: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExclusionViewModel(packageManager, exclusionPrefs, grayscaleManager, ownPackage) as T
    }
}

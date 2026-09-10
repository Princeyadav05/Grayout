package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.service.ExclusionPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class ExclusionViewModel(
    private val exclusionPrefs: ExclusionPrefs,
    private val usageAccessProbe: () -> Boolean,
    private val onExclusionListChanged: () -> Unit,
    private val loadApps: () -> List<AppInfo>,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isUsageAccessGranted = MutableStateFlow(true)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

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

    private var refreshJob: Job? = null

    fun refreshApps() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val apps = withContext(ioDispatcher) { loadApps() }
            // Loading icons can outlive a toggle. Publish on Main using the
            // current preferences so an old snapshot cannot undo the UI change.
            val excludedPackages = exclusionPrefs.getExcludedPackages()
            _apps.value = apps.map { app ->
                app.copy(isExcluded = app.packageName in excludedPackages)
            }
        }
    }

    fun toggleExclusion(packageName: String) {
        val isExcluded = !exclusionPrefs.isExcluded(packageName)
        if (isExcluded) {
            exclusionPrefs.addExcludedPackage(packageName)
        } else {
            exclusionPrefs.removeExcludedPackage(packageName)
        }
        _apps.value = _apps.value.map { app ->
            if (app.packageName == packageName) app.copy(isExcluded = isExcluded)
            else app
        }
        onExclusionListChanged()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkUsageAccess() {
        _isUsageAccessGranted.value = usageAccessProbe()
    }
}

class ExclusionViewModelFactory(
    private val exclusionPrefs: ExclusionPrefs,
    private val usageAccessProbe: () -> Boolean,
    private val onExclusionListChanged: () -> Unit,
    private val loadApps: () -> List<AppInfo>,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExclusionViewModel(
            exclusionPrefs,
            usageAccessProbe,
            onExclusionListChanged,
            loadApps,
            ioDispatcher,
        ) as T
    }
}

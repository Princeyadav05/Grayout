package com.princeyadav.grayout.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.princeyadav.grayout.model.AppCategory
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
    /** The last list [loadApps] returned in this process, shown until the refresh lands. */
    initialApps: List<AppInfo> = emptyList(),
) : ViewModel() {

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isUsageAccessGranted = MutableStateFlow(true)
    val isUsageAccessGranted: StateFlow<Boolean> = _isUsageAccessGranted.asStateFlow()

    private val _selectedCategory = MutableStateFlow<AppCategory?>(null)

    /** The selected chip, or null for All (also when the category no longer has any app). */
    val selectedCategory: StateFlow<AppCategory?> = combine(_apps, _selectedCategory, ::effectiveCategory)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /** Categories with at least one installed app, in chip order, with their app count. */
    val categoryCounts: StateFlow<Map<AppCategory, Int>> = _apps
        .map { apps ->
            AppCategory.entries
                .associateWith { category -> apps.count { category in it.categories } }
                .filterValues { it > 0 }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val filteredApps: StateFlow<List<AppInfo>> = combine(_apps, _searchQuery, _selectedCategory) { apps, query, selected ->
        visibleApps(apps, query, effectiveCategory(apps, selected))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _pendingUndo = MutableStateFlow<BulkExclusion?>(null)

    /** Apps added by the last Exclude all, while its Undo is still offered. */
    val pendingUndo: StateFlow<BulkExclusion?> = _pendingUndo.asStateFlow()

    val excludedApps: StateFlow<List<AppInfo>> = filteredApps
        .map { apps -> apps.filter { it.isExcluded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allOtherApps: StateFlow<List<AppInfo>> = filteredApps
        .map { apps -> apps.filterNot { it.isExcluded } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var refreshJob: Job? = null

    // init already loads, so the screen's first resume would only repeat it.
    private var skipNextResumeRefresh = true

    init {
        // Start loading during the enter transition rather than after it, and show
        // the last known list meanwhile.
        if (initialApps.isNotEmpty()) publish(initialApps)
        refreshApps()
    }

    /** Called on every screen resume, to pick up permission changes and app installs. */
    fun onScreenResumed() {
        checkUsageAccess()
        if (skipNextResumeRefresh) skipNextResumeRefresh = false else refreshApps()
    }

    fun refreshApps() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            val apps = withContext(ioDispatcher) { loadApps() }
            // Loading icons can outlive a toggle. Publish on Main using the
            // current preferences so an old snapshot cannot undo the UI change.
            publish(apps)
        }
    }

    private fun publish(apps: List<AppInfo>) {
        val excludedPackages = exclusionPrefs.getExcludedPackages()
        _apps.value = apps.map { app ->
            app.copy(isExcluded = app.packageName in excludedPackages)
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

    fun selectCategory(category: AppCategory?) {
        _selectedCategory.value = category
    }

    /**
     * Excludes every app the selected chip and search currently show, in one write.
     * Reads the source state rather than [filteredApps], which is stale without a collector.
     */
    fun excludeAllVisible() {
        val apps = _apps.value
        val category = effectiveCategory(apps, _selectedCategory.value) ?: return
        val toAdd = visibleApps(apps, _searchQuery.value, category)
            .filterNot { it.isExcluded }
            .mapTo(HashSet()) { it.packageName }
        if (toAdd.isEmpty()) return
        writeExcluded(exclusionPrefs.getExcludedPackages() + toAdd)
        _pendingUndo.value = BulkExclusion(toAdd)
    }

    /** Reverts [op] only if it is still the latest bulk action, so a stale snackbar is a no-op. */
    fun undoBulkExclusion(op: BulkExclusion) {
        if (_pendingUndo.value !== op) return
        _pendingUndo.value = null
        writeExcluded(exclusionPrefs.getExcludedPackages() - op.packages)
    }

    fun clearPendingUndo(op: BulkExclusion) {
        if (_pendingUndo.value === op) _pendingUndo.value = null
    }

    private fun writeExcluded(packages: Set<String>) {
        exclusionPrefs.setExcludedPackages(packages)
        _apps.value = _apps.value.map { it.copy(isExcluded = it.packageName in packages) }
        onExclusionListChanged()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun checkUsageAccess() {
        _isUsageAccessGranted.value = usageAccessProbe()
    }
}

/**
 * Packages added by one Exclude all. Identity equality on purpose: each tap is a new
 * operation, so the screen's snackbar effect restarts even for an identical set.
 */
class BulkExclusion(val packages: Set<String>)

/** [selected] when at least one app is in it, otherwise null (All). */
internal fun effectiveCategory(apps: List<AppInfo>, selected: AppCategory?): AppCategory? =
    selected?.takeIf { category -> apps.any { category in it.categories } }

/** Apps matching the search [query] and, when set, [category]. */
internal fun visibleApps(apps: List<AppInfo>, query: String, category: AppCategory?): List<AppInfo> =
    apps.filter { app ->
        (query.isBlank() || app.appName.contains(query, ignoreCase = true)) &&
            (category == null || category in app.categories)
    }

class ExclusionViewModelFactory(
    private val exclusionPrefs: ExclusionPrefs,
    private val usageAccessProbe: () -> Boolean,
    private val onExclusionListChanged: () -> Unit,
    private val loadApps: () -> List<AppInfo>,
    private val ioDispatcher: CoroutineDispatcher,
    private val initialApps: List<AppInfo>,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ExclusionViewModel(
            exclusionPrefs,
            usageAccessProbe,
            onExclusionListChanged,
            loadApps,
            ioDispatcher,
            initialApps,
        ) as T
    }
}

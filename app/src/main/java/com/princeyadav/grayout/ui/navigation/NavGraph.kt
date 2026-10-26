package com.princeyadav.grayout.ui.navigation

import android.app.AlarmManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.data.loadInstalledApps
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import com.princeyadav.grayout.scheduling.needsExactAlarmAccess
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.UsageAccess
import com.princeyadav.grayout.ui.components.AdbCommandSheet
import com.princeyadav.grayout.ui.rememberSecureSettingsPermission
import com.princeyadav.grayout.ui.systemTimeChanges
import com.princeyadav.grayout.ui.screens.ExclusionListScreen
import com.princeyadav.grayout.ui.screens.HomeScreen
import com.princeyadav.grayout.ui.screens.ScheduleEditorScreen
import com.princeyadav.grayout.ui.screens.ScheduleListScreen
import com.princeyadav.grayout.ui.screens.SettingsScreen
import com.princeyadav.grayout.viewmodel.ExclusionViewModel
import com.princeyadav.grayout.viewmodel.ExclusionViewModelFactory
import com.princeyadav.grayout.viewmodel.HomeViewModel
import com.princeyadav.grayout.viewmodel.ScheduleEditorViewModel
import com.princeyadav.grayout.viewmodel.ScheduleEditorViewModelFactory
import com.princeyadav.grayout.viewmodel.ScheduleViewModel
import com.princeyadav.grayout.viewmodel.ScheduleViewModelFactory

/**
 * The installed APK's versionName (git-tag-derived at release, "1.0.0-dev" locally),
 * or "unknown" if unavailable. Reads the real artifact instead of a hardcoded string.
 */
private fun installedVersionName(context: Context): String = try {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(context.packageName, 0)
    }
    info.versionName?.takeIf { it.isNotBlank() } ?: "unknown"
} catch (_: PackageManager.NameNotFoundException) {
    "unknown"
}

private fun exactAlarmAccessNeeded(context: Context) = needsExactAlarmAccess(
    Build.VERSION.SDK_INT,
    Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms(),
)

/** Granting re-arms schedules through SystemScheduleReceiver. */
private fun openExactAlarmSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    try {
        context.startActivity(
            Intent(
                AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Uri.fromParts("package", context.packageName, null),
            )
        )
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}

private fun openNotificationSettings(context: Context) {
    try {
        context.startActivity(
            Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName),
        )
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}

private fun openUsageAccessSettings(context: Context) {
    try {
        context.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
    }
}

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SETTINGS = "settings"
    const val EXCLUSION_LIST = "exclusion_list"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    isBatteryUnrestricted: Boolean,
    onBatteryOptimizationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enforcementInterval by homeViewModel.enforcementInterval.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            val context = LocalContext.current
            val lifecycleOwnerHome = LocalLifecycleOwner.current
            val isGrayscaleOn by homeViewModel.isGrayscaleOn.collectAsStateWithLifecycle()

            val exclusionPrefsHome = remember {
                ExclusionPrefs(
                    context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                )
            }
            var excludedAppCount by remember { mutableIntStateOf(exclusionPrefsHome.getExcludedCount()) }
            var isUsageAccessGrantedHome by remember {
                mutableStateOf(UsageAccess.isGranted(context))
            }

            val db = remember { GrayoutDatabase.getInstance(context) }
            val scheduleRepository = remember { ScheduleRepository(db.scheduleDao()) }
            val scheduleLine by homeViewModel.scheduleLine.collectAsStateWithLifecycle()
            val excludedAppIcons by homeViewModel.excludedAppIcons.collectAsStateWithLifecycle()
            val excludedOverflowCount by homeViewModel.excludedOverflowCount.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwnerHome) {
                lifecycleOwnerHome.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    excludedAppCount = exclusionPrefsHome.getExcludedCount()
                    isUsageAccessGrantedHome = UsageAccess.isGranted(context)
                    homeViewModel.refreshExcludedAppIcons()
                    homeViewModel.observeNextSchedule(
                        scheduleRepository, context.applicationContext.systemTimeChanges(),
                    )
                }
            }

            val isPermissionGrantedHome by rememberSecureSettingsPermission()
            val toggleFailedTarget by homeViewModel.toggleFailedTarget.collectAsStateWithLifecycle()
            var showSetup by remember { mutableStateOf(false) }
            val showNotificationPrompt by homeViewModel.showNotificationPrompt.collectAsStateWithLifecycle()
            val notificationPermission = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { homeViewModel.dismissNotificationPrompt() }
            LaunchedEffect(homeViewModel) {
                homeViewModel.setupRequests.collect { showSetup = true }
            }
            if (showSetup) {
                AdbCommandSheet(
                    isGranted = isPermissionGrantedHome,
                    onDismiss = { showSetup = false },
                    onTryGrayscale = homeViewModel::toggleGrayscale,
                )
            }

            HomeScreen(
                isGrayscaleOn = isGrayscaleOn,
                enforcementInterval = enforcementInterval,
                excludedAppCount = excludedAppCount,
                isUsageAccessGranted = isUsageAccessGrantedHome,
                onToggle = homeViewModel::toggleGrayscale,
                onEnforcementIntervalChange = homeViewModel::setEnforcementInterval,
                onNavigateToExclusions = { navController.navigate(Routes.EXCLUSION_LIST) },
                onNavigateToSchedules = {
                    navController.navigate(Routes.SCHEDULES) {
                        popUpTo(Routes.HOME) { saveState = true }
                        restoreState = true
                        launchSingleTop = true
                    }
                },
                scheduleLine = scheduleLine,
                excludedAppIcons = excludedAppIcons,
                excludedOverflowCount = excludedOverflowCount,
                isPermissionGranted = isPermissionGrantedHome,
                toggleFailedTarget = toggleFailedTarget,
                onShowSetup = { showSetup = true },
                onFixUsageAccess = { openUsageAccessSettings(context) },
                showNotificationPrompt = showNotificationPrompt,
                onAllowNotifications = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Granted but switched off in system settings: send the user there.
                        openNotificationSettings(context)
                        homeViewModel.dismissNotificationPrompt()
                    }
                },
                onDismissNotificationPrompt = homeViewModel::dismissNotificationPrompt,
            )
        }
        composable(Routes.SCHEDULES) {
            val context = LocalContext.current
            val db = remember { GrayoutDatabase.getInstance(context) }
            val repository = remember { ScheduleRepository(db.scheduleDao()) }
            val scheduleAlarmManager = remember { ScheduleAlarmManager(context) }
            val grayscaleManager = remember { GrayscaleManager(context.applicationContext) }
            val isPermissionGranted by rememberSecureSettingsPermission()
            var showPermissionSetup by remember { mutableStateOf(false) }
            if (showPermissionSetup) {
                AdbCommandSheet(
                    isGranted = isPermissionGranted,
                    onDismiss = { showPermissionSetup = false },
                )
            }
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(
                    repository, scheduleAlarmManager, grayscaleManager::canWriteSecureSettings,
                ),
            )
            val schedules by viewModel.schedules.collectAsStateWithLifecycle()
            val firingScheduleIds by viewModel.firingScheduleIds.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.observeFiringState(context.applicationContext.systemTimeChanges())
                }
            }

            val today by viewModel.today.collectAsStateWithLifecycle()
            val enableError by viewModel.enableError.collectAsStateWithLifecycle()
            var needsExactAlarms by remember { mutableStateOf(exactAlarmAccessNeeded(context)) }
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    needsExactAlarms = exactAlarmAccessNeeded(context)
                }
            }

            ScheduleListScreen(
                schedules = schedules,
                isPermissionGranted = isPermissionGranted,
                onSetupPermission = { showPermissionSetup = true },
                firingScheduleIds = firingScheduleIds,
                onAddSchedule = { navController.navigate("schedule_editor?id=0") },
                onEditSchedule = { id -> navController.navigate("schedule_editor?id=$id") },
                onToggleEnabled = viewModel::toggleEnabled,
                today = today,
                enableError = enableError,
                needsExactAlarms = needsExactAlarms,
                onAllowExactAlarms = { openExactAlarmSettings(context) },
            )
        }
        composable(
            route = "schedule_editor?id={scheduleId}",
            arguments = listOf(navArgument("scheduleId") { type = NavType.LongType; defaultValue = 0L }),
        ) { backStackEntry ->
            val context = LocalContext.current
            val scheduleId = backStackEntry.arguments?.getLong("scheduleId") ?: 0L
            val db = remember { GrayoutDatabase.getInstance(context) }
            val repository = remember { ScheduleRepository(db.scheduleDao()) }
            val scheduleAlarmManager = remember { ScheduleAlarmManager(context) }
            val grayscaleManager = remember { GrayscaleManager(context.applicationContext) }
            val isPermissionGranted by rememberSecureSettingsPermission()
            var showPermissionSetup by remember { mutableStateOf(false) }
            if (showPermissionSetup) {
                AdbCommandSheet(
                    isGranted = isPermissionGranted,
                    onDismiss = { showPermissionSetup = false },
                )
            }
            val viewModel: ScheduleEditorViewModel = viewModel(
                factory = ScheduleEditorViewModelFactory(
                    repository, scheduleAlarmManager, grayscaleManager::canWriteSecureSettings, scheduleId,
                ),
            )

            if (scheduleId > 0L) {
                LaunchedEffect(scheduleId) { viewModel.loadSchedule(scheduleId) }
            }

            val name by viewModel.name.collectAsStateWithLifecycle()
            val selectedDays by viewModel.selectedDays.collectAsStateWithLifecycle()
            val startHour by viewModel.startHour.collectAsStateWithLifecycle()
            val startMinute by viewModel.startMinute.collectAsStateWithLifecycle()
            val endHour by viewModel.endHour.collectAsStateWithLifecycle()
            val endMinute by viewModel.endMinute.collectAsStateWithLifecycle()
            val overlapError by viewModel.overlapError.collectAsStateWithLifecycle()
            val isSaved by viewModel.isSaved.collectAsStateWithLifecycle()
            val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
            val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
            val isReady by viewModel.isReady.collectAsStateWithLifecycle()
            val isDeleted by viewModel.isDeleted.collectAsStateWithLifecycle()

            LaunchedEffect(isSaved) {
                if (isSaved) navController.popBackStack()
            }

            ScheduleEditorScreen(
                name = name,
                isPermissionGranted = isPermissionGranted,
                onSetupPermission = { showPermissionSetup = true },
                onSaveAsDisabled = viewModel::saveAsDisabled,
                selectedDays = selectedDays,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                overlapError = overlapError,
                isEditMode = scheduleId > 0L,
                isLoading = isLoading,
                isSaving = isSaving,
                isReady = isReady,
                isDeleted = isDeleted,
                onNameChange = viewModel::setName,
                onToggleDay = viewModel::toggleDay,
                onSetStartTime = viewModel::setStartTime,
                onSetEndTime = viewModel::setEndTime,
                onSelectPreset = viewModel::selectPreset,
                onSave = viewModel::save,
                onDelete = viewModel::deleteSchedule,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            var isUsageAccessGranted by remember {
                mutableStateOf(UsageAccess.isGranted(context))
            }
            var needsExactAlarms by remember { mutableStateOf(exactAlarmAccessNeeded(context)) }
            var notificationsOn by remember {
                mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
            }
            val isServiceRunning by homeViewModel.isServiceRunning.collectAsStateWithLifecycle()
            val versionName = remember(context) { installedVersionName(context) }
            val isAdbPermissionGranted by rememberSecureSettingsPermission()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    isUsageAccessGranted = UsageAccess.isGranted(context)
                    needsExactAlarms = exactAlarmAccessNeeded(context)
                    notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
                }
            }

            SettingsScreen(
                isServiceRunning = isServiceRunning,
                versionName = versionName,
                isAdbPermissionGranted = isAdbPermissionGranted,
                isUsageAccessGranted = isUsageAccessGranted,
                isBatteryUnrestricted = isBatteryUnrestricted,
                needsExactAlarmAccess = needsExactAlarms,
                notificationsOn = notificationsOn,
                onNotificationsClick = { openNotificationSettings(context) },
                onBatteryOptimizationClick = onBatteryOptimizationClick,
                onUsageAccessClick = { openUsageAccessSettings(context) },
                onExactAlarmAccessClick = { openExactAlarmSettings(context) },
            )
        }
        composable(Routes.EXCLUSION_LIST) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val prefs = remember {
                context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            }
            val exclusionPrefs = remember { ExclusionPrefs(prefs) }
            val applicationContext = context.applicationContext
            val viewModel: ExclusionViewModel = viewModel(
                factory = ExclusionViewModelFactory(
                    exclusionPrefs = exclusionPrefs,
                    usageAccessProbe = { UsageAccess.isGranted(applicationContext) },
                    onExclusionListChanged = {
                        applicationContext.startForegroundService(
                            Intent(applicationContext, GrayoutService::class.java)
                        )
                    },
                    loadApps = { loadInstalledApps(applicationContext) },
                    ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                )
            )

            val excludedApps by viewModel.excludedApps.collectAsStateWithLifecycle(emptyList())
            val allOtherApps by viewModel.allOtherApps.collectAsStateWithLifecycle(emptyList())
            val categoryApps by viewModel.filteredApps.collectAsStateWithLifecycle()
            val categoryCounts by viewModel.categoryCounts.collectAsStateWithLifecycle()
            val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
            val pendingUndo by viewModel.pendingUndo.collectAsStateWithLifecycle()
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.checkUsageAccess()
                    // Also performs the initial load, keeping one refresh per resume.
                    viewModel.refreshApps()
                }
            }

            ExclusionListScreen(
                excludedApps = excludedApps,
                allOtherApps = allOtherApps,
                categoryApps = categoryApps,
                categoryCounts = categoryCounts,
                selectedCategory = selectedCategory,
                pendingUndo = pendingUndo,
                searchQuery = searchQuery,
                isUsageAccessGranted = isUsageAccessGranted,
                onToggle = viewModel::toggleExclusion,
                onSelectCategory = viewModel::selectCategory,
                onExcludeAll = viewModel::excludeAllVisible,
                onUndo = viewModel::undoBulkExclusion,
                onUndoDismissed = viewModel::clearPendingUndo,
                onSearchQueryChange = viewModel::setSearchQuery,
                onOpenUsageAccessSettings = { openUsageAccessSettings(context) },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

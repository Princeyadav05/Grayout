package com.princeyadav.grayout.ui.navigation

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.princeyadav.grayout.data.GrayoutDatabase
import com.princeyadav.grayout.data.ScheduleRepository
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.scheduling.ScheduleAlarmManager
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.UsageAccess
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

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedule_editor"
    const val SETTINGS = "settings"
    const val EXCLUSION_LIST = "exclusion_list"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    isAdbPermissionGranted: Boolean,
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
            val nextScheduleText by homeViewModel.nextScheduleText.collectAsStateWithLifecycle()
            val excludedAppIcons by homeViewModel.excludedAppIcons.collectAsStateWithLifecycle()
            val excludedOverflowCount by homeViewModel.excludedOverflowCount.collectAsStateWithLifecycle()
            val needsAttentionCount by homeViewModel.needsAttentionCount.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwnerHome) {
                lifecycleOwnerHome.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    excludedAppCount = exclusionPrefsHome.getExcludedCount()
                    isUsageAccessGrantedHome = UsageAccess.isGranted(context)
                    homeViewModel.refreshNextSchedule(scheduleRepository)
                    homeViewModel.refreshExcludedAppIcons()
                    homeViewModel.refreshAttentionCount()
                }
            }

            LaunchedEffect(homeViewModel) {
                homeViewModel.navigateToSetup.collect {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.HOME) { saveState = true }
                        restoreState = true
                        launchSingleTop = true
                    }
                }
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
                nextScheduleText = nextScheduleText,
                excludedAppIcons = excludedAppIcons,
                excludedOverflowCount = excludedOverflowCount,
                needsAttentionCount = needsAttentionCount,
                onAttentionClick = {
                    navController.navigate(Routes.SETTINGS) {
                        popUpTo(Routes.HOME) { saveState = true }
                        restoreState = true
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(Routes.SCHEDULES) {
            val context = LocalContext.current
            val db = remember { GrayoutDatabase.getInstance(context) }
            val repository = remember { ScheduleRepository(db.scheduleDao()) }
            val scheduleAlarmManager = remember { ScheduleAlarmManager(context) }
            val viewModel: ScheduleViewModel = viewModel(
                factory = ScheduleViewModelFactory(repository, scheduleAlarmManager)
            )
            val schedules by viewModel.schedules.collectAsStateWithLifecycle()
            val firingScheduleIds by viewModel.firingScheduleIds.collectAsStateWithLifecycle()

            val lifecycleOwner = LocalLifecycleOwner.current
            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.refreshFiringState()
                }
            }

            LaunchedEffect(viewModel) {
                viewModel.enableConflict.collect { message ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }

            ScheduleListScreen(
                schedules = schedules,
                firingScheduleIds = firingScheduleIds,
                onAddSchedule = { navController.navigate("schedule_editor?id=0") },
                onEditSchedule = { id -> navController.navigate("schedule_editor?id=$id") },
                onToggleEnabled = viewModel::toggleEnabled,
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
            val viewModel: ScheduleEditorViewModel = viewModel(
                factory = ScheduleEditorViewModelFactory(repository, scheduleAlarmManager)
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

            LaunchedEffect(isSaved) {
                if (isSaved) navController.popBackStack()
            }

            ScheduleEditorScreen(
                name = name,
                selectedDays = selectedDays,
                startHour = startHour,
                startMinute = startMinute,
                endHour = endHour,
                endMinute = endMinute,
                overlapError = overlapError,
                isEditMode = scheduleId > 0L,
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
            val isServiceRunning by homeViewModel.isServiceRunning.collectAsStateWithLifecycle()
            val versionName = remember(context) { installedVersionName(context) }

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    isUsageAccessGranted = UsageAccess.isGranted(context)
                }
            }

            SettingsScreen(
                enforcementInterval = enforcementInterval,
                isServiceRunning = isServiceRunning,
                versionName = versionName,
                isAdbPermissionGranted = isAdbPermissionGranted,
                isUsageAccessGranted = isUsageAccessGranted,
                isBatteryUnrestricted = isBatteryUnrestricted,
                onBatteryOptimizationClick = onBatteryOptimizationClick,
                onUsageAccessClick = {
                    try {
                        context.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
                    }
                },
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
                    loadApps = {
                        applicationContext.packageManager.getInstalledApplications(0)
                            .filter { info ->
                                applicationContext.packageManager
                                    .getLaunchIntentForPackage(info.packageName) != null &&
                                    info.packageName != "com.princeyadav.grayout"
                            }
                            .map { info ->
                                AppInfo(
                                    packageName = info.packageName,
                                    appName = info.loadLabel(applicationContext.packageManager).toString(),
                                    icon = info.loadIcon(applicationContext.packageManager)
                                        .toBitmap(width = 80, height = 80)
                                        .asImageBitmap(),
                                    isExcluded = exclusionPrefs.isExcluded(info.packageName),
                                )
                            }
                            .sortedBy { it.appName.lowercase() }
                    },
                    ioDispatcher = kotlinx.coroutines.Dispatchers.IO,
                )
            )

            val excludedApps by viewModel.excludedApps.collectAsStateWithLifecycle(emptyList())
            val allOtherApps by viewModel.allOtherApps.collectAsStateWithLifecycle(emptyList())
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val isUsageAccessGranted by viewModel.isUsageAccessGranted.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.checkUsageAccess()
                }
            }

            ExclusionListScreen(
                excludedApps = excludedApps,
                allOtherApps = allOtherApps,
                searchQuery = searchQuery,
                isUsageAccessGranted = isUsageAccessGranted,
                onToggle = viewModel::toggleExclusion,
                onSearchQueryChange = viewModel::setSearchQuery,
                onOpenUsageAccessSettings = {
                    try {
                        context.startActivity(Intent(AndroidSettings.ACTION_USAGE_ACCESS_SETTINGS))
                    } catch (_: ActivityNotFoundException) {
                        context.startActivity(Intent(AndroidSettings.ACTION_SETTINGS))
                    }
                },
                onBack = { navController.popBackStack() },
                onRefresh = viewModel::refreshApps,
            )
        }
    }
}

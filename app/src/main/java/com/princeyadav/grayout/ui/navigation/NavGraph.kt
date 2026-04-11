package com.princeyadav.grayout.ui.navigation

import android.content.Intent
import android.provider.Settings as AndroidSettings
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
import com.princeyadav.grayout.service.GrayscaleManager
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
            var isAccessibilityEnabledHome by remember {
                val services = AndroidSettings.Secure.getString(
                    context.contentResolver,
                    AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: ""
                mutableStateOf(services.contains("com.princeyadav.grayout"))
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
                    val services = AndroidSettings.Secure.getString(
                        context.contentResolver,
                        AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ) ?: ""
                    isAccessibilityEnabledHome = services.contains("com.princeyadav.grayout")
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
                isAccessibilityEnabled = isAccessibilityEnabledHome,
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
            var isAccessibilityEnabled by remember {
                val services = AndroidSettings.Secure.getString(
                    context.contentResolver,
                    AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: ""
                mutableStateOf(services.contains("com.princeyadav.grayout"))
            }

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    val services = AndroidSettings.Secure.getString(
                        context.contentResolver,
                        AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                    ) ?: ""
                    isAccessibilityEnabled = services.contains("com.princeyadav.grayout")
                }
            }

            SettingsScreen(
                enforcementInterval = enforcementInterval,
                isAdbPermissionGranted = isAdbPermissionGranted,
                isAccessibilityEnabled = isAccessibilityEnabled,
                isBatteryUnrestricted = isBatteryUnrestricted,
                onBatteryOptimizationClick = onBatteryOptimizationClick,
                onAccessibilityClick = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
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
                    grayscaleManager = GrayscaleManager(applicationContext.contentResolver),
                    ownPackage = context.packageName,
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
            val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.checkAccessibilityService()
                }
            }

            ExclusionListScreen(
                excludedApps = excludedApps,
                allOtherApps = allOtherApps,
                searchQuery = searchQuery,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onToggle = viewModel::toggleExclusion,
                onSearchQueryChange = viewModel::setSearchQuery,
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onBack = { navController.popBackStack() },
                onRefresh = viewModel::refreshApps,
            )
        }
    }
}

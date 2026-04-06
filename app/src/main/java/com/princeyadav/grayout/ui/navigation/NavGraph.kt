package com.princeyadav.grayout.ui.navigation

import android.content.Intent
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.ui.screens.ExclusionListScreen
import com.princeyadav.grayout.ui.screens.HomeScreen
import com.princeyadav.grayout.ui.screens.SettingsScreen
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.ExclusionViewModel
import com.princeyadav.grayout.viewmodel.ExclusionViewModelFactory
import com.princeyadav.grayout.viewmodel.HomeViewModel

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
            val isGrayscaleOn by homeViewModel.isGrayscaleOn.collectAsStateWithLifecycle()

            val exclusionPrefsHome = remember {
                ExclusionPrefs(
                    context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
                )
            }
            val excludedAppCount = remember(Unit) { exclusionPrefsHome.getExcludedCount() }

            val isAccessibilityEnabledHome = remember {
                val services = AndroidSettings.Secure.getString(
                    context.contentResolver,
                    AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                ) ?: ""
                services.contains("com.princeyadav.grayout")
            }

            HomeScreen(
                isGrayscaleOn = isGrayscaleOn,
                enforcementInterval = enforcementInterval,
                excludedAppCount = excludedAppCount,
                isAccessibilityEnabled = isAccessibilityEnabledHome,
                onToggle = homeViewModel::toggleGrayscale,
                onEnforcementIntervalChange = homeViewModel::setEnforcementInterval,
                onNavigateToExclusions = { navController.navigate(Routes.EXCLUSION_LIST) },
            )
        }
        composable(Routes.SCHEDULES) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GrayoutTheme.colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Schedules",
                    style = GrayoutTheme.typography.headingMedium,
                    color = GrayoutTheme.colors.text,
                )
            }
        }
        composable(Routes.SCHEDULE_EDITOR) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(GrayoutTheme.colors.bg),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Schedule Editor",
                    style = GrayoutTheme.typography.headingMedium,
                    color = GrayoutTheme.colors.text,
                )
            }
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                enforcementInterval = enforcementInterval,
                isAdbPermissionGranted = isAdbPermissionGranted,
                isBatteryUnrestricted = isBatteryUnrestricted,
                onBatteryOptimizationClick = onBatteryOptimizationClick,
            )
        }
        composable(Routes.EXCLUSION_LIST) {
            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current
            val prefs = remember {
                context.getSharedPreferences(EnforcementPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
            }
            val exclusionPrefs = remember { ExclusionPrefs(prefs) }
            val viewModel: ExclusionViewModel = viewModel(
                factory = ExclusionViewModelFactory(
                    context.packageManager,
                    exclusionPrefs,
                )
            )

            val filteredApps by viewModel.filteredApps.collectAsStateWithLifecycle(emptyList())
            val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
            val isAccessibilityEnabled by viewModel.isAccessibilityEnabled.collectAsStateWithLifecycle()

            LaunchedEffect(lifecycleOwner) {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                    viewModel.checkAccessibilityService(context.contentResolver)
                }
            }

            ExclusionListScreen(
                apps = filteredApps,
                searchQuery = searchQuery,
                isAccessibilityEnabled = isAccessibilityEnabled,
                onToggle = viewModel::toggleExclusion,
                onSearchQueryChange = viewModel::setSearchQuery,
                onOpenAccessibilitySettings = {
                    context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}

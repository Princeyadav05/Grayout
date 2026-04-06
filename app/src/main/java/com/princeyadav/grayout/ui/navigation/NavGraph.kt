package com.princeyadav.grayout.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.princeyadav.grayout.ui.screens.HomeScreen
import com.princeyadav.grayout.ui.screens.SettingsScreen
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedule_editor"
    const val SETTINGS = "settings"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    homeViewModel: HomeViewModel,
    isAdbPermissionGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    modifier: Modifier = Modifier,
) {
    val isGrayscaleOn by homeViewModel.isGrayscaleOn.collectAsStateWithLifecycle()
    val enforcementInterval by homeViewModel.enforcementInterval.collectAsStateWithLifecycle()

    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                isGrayscaleOn = isGrayscaleOn,
                enforcementInterval = enforcementInterval,
                onToggle = homeViewModel::toggleGrayscale,
                onEnforcementIntervalChange = homeViewModel::setEnforcementInterval,
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
            )
        }
    }
}

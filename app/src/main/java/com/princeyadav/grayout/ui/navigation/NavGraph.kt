package com.princeyadav.grayout.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.princeyadav.grayout.ui.theme.GrayoutTheme

object Routes {
    const val HOME = "home"
    const val SCHEDULES = "schedules"
    const val SCHEDULE_EDITOR = "schedule_editor"
    const val SETTINGS = "settings"
}

@Composable
fun GrayoutNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = Routes.HOME,
        modifier = modifier,
    ) {
        composable(Routes.HOME) { PlaceholderScreen("Home") }
        composable(Routes.SCHEDULES) { PlaceholderScreen("Schedules") }
        composable(Routes.SCHEDULE_EDITOR) { PlaceholderScreen("Schedule Editor") }
        composable(Routes.SETTINGS) { PlaceholderScreen("Settings") }
    }
}

@Composable
private fun PlaceholderScreen(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GrayoutTheme.colors.bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name,
            style = GrayoutTheme.typography.headingLarge,
            color = GrayoutTheme.colors.text,
        )
    }
}

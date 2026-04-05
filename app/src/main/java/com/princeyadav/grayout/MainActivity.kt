package com.princeyadav.grayout

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.ui.navigation.GrayoutNavGraph
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel
import com.princeyadav.grayout.viewmodel.HomeViewModelFactory
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val enforcementPrefs by lazy {
        EnforcementPrefs(getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE))
    }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            GrayscaleManager(applicationContext.contentResolver),
            enforcementPrefs,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        startForegroundService(Intent(this, GrayoutService::class.java))

        lifecycleScope.launch {
            homeViewModel.enforcementInterval
                .drop(1)
                .collect { interval ->
                    val intent = Intent(this@MainActivity, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                    startForegroundService(intent)
                }
        }

        setContent {
            GrayoutTheme {
                val navController = rememberNavController()
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = GrayoutTheme.colors.bg,
                ) { innerPadding ->
                    GrayoutNavGraph(
                        navController = navController,
                        homeViewModel = homeViewModel,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }
}

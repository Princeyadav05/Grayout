package com.princeyadav.grayout

import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import androidx.core.graphics.drawable.toBitmap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.service.UsageAccess
import com.princeyadav.grayout.ui.components.BottomNavBar
import com.princeyadav.grayout.ui.navigation.GrayoutNavGraph
import com.princeyadav.grayout.ui.navigation.Routes
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel
import com.princeyadav.grayout.viewmodel.HomeViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op — if denied, service still works, just no visible notification */ }

    private val enforcementPrefs by lazy {
        EnforcementPrefs(getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE))
    }

    private val exclusionPrefs by lazy {
        ExclusionPrefs(getSharedPreferences(EnforcementPrefs.PREFS_NAME, MODE_PRIVATE))
    }

    private val grayscaleManager by lazy { GrayscaleManager(applicationContext) }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            grayscaleManager = GrayscaleManager(applicationContext),
            enforcementPrefs = enforcementPrefs,
            exclusionPrefs = exclusionPrefs,
            isBatteryOptimized = {
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            },
            loadExcludedIcons = { packages ->
                val pm = applicationContext.packageManager
                val icons = mutableListOf<Bitmap>()
                var installedCount = 0
                for (pkg in packages) {
                    // Cheap ApplicationInfo lookup (no bitmap) counts every installed
                    // excluded app for the "+N" badge; only the first 3 are decoded.
                    val info = try {
                        pm.getApplicationInfo(pkg, 0)
                    } catch (_: PackageManager.NameNotFoundException) {
                        continue
                    }
                    installedCount++
                    if (icons.size < 3) {
                        icons.add(pm.getApplicationIcon(info).toBitmap(width = 64, height = 64))
                    }
                }
                icons to (installedCount - icons.size).coerceAtLeast(0)
            },
            ioDispatcher = Dispatchers.IO,
            usageAccessProbe = { UsageAccess.isGranted(applicationContext) },
            serviceRunning = GrayoutService.isRunning,
            onEnforcementIntervalChanged = { interval ->
                applicationContext.startForegroundService(
                    Intent(applicationContext, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                        .putExtra(GrayoutService.EXTRA_USER_INTERVAL_CHANGE, true)
                )
            },
        )
    }

    private val grayscaleObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            homeViewModel.refreshGrayscaleStateFromSystem()
        }
    }

    private var isAdbPermissionGranted by mutableStateOf(false)
    private var isBatteryUnrestricted by mutableStateOf(false)

    private fun refreshSystemChecks() {
        homeViewModel.refreshSystemState()
        lifecycleScope.launch {
            val canWrite = withContext(Dispatchers.IO) { grayscaleManager.canWriteSecureSettings() }
            isAdbPermissionGranted = canWrite

            val powerManager = getSystemService(PowerManager::class.java)
            isBatteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        startForegroundService(Intent(this, GrayoutService::class.java))

        setContent {
            GrayoutTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = GrayoutTheme.colors.bg,
                    bottomBar = {
                        if (currentRoute?.startsWith("schedule_editor") != true && currentRoute != Routes.EXCLUSION_LIST) {
                            BottomNavBar(
                                currentRoute = currentRoute ?: Routes.HOME,
                                onNavigate = { route ->
                                    navController.navigate(route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        restoreState = true
                                        launchSingleTop = true
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    GrayoutNavGraph(
                        navController = navController,
                        homeViewModel = homeViewModel,
                        isAdbPermissionGranted = isAdbPermissionGranted,
                        isBatteryUnrestricted = isBatteryUnrestricted,
                        onBatteryOptimizationClick = {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                            startActivity(intent)
                        },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        contentResolver.registerContentObserver(
            GrayscaleManager.DALTONIZER_ENABLED_URI,
            false,
            grayscaleObserver,
        )
        contentResolver.registerContentObserver(
            GrayscaleManager.DALTONIZER_MODE_URI,
            false,
            grayscaleObserver,
        )
    }

    override fun onResume() {
        super.onResume()
        refreshSystemChecks()
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(grayscaleObserver)
    }
}

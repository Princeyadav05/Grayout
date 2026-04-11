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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.service.ExclusionPrefs
import com.princeyadav.grayout.service.GrayscaleManager
import com.princeyadav.grayout.service.GrayoutService
import com.princeyadav.grayout.ui.components.BottomNavBar
import com.princeyadav.grayout.ui.navigation.GrayoutNavGraph
import com.princeyadav.grayout.ui.navigation.Routes
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeViewModel
import com.princeyadav.grayout.viewmodel.HomeViewModelFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.drop
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

    private val grayscaleManager by lazy { GrayscaleManager(applicationContext.contentResolver) }

    private val homeViewModel: HomeViewModel by viewModels {
        HomeViewModelFactory(
            grayscaleManager = GrayscaleManager(applicationContext.contentResolver),
            enforcementPrefs = enforcementPrefs,
            exclusionPrefs = exclusionPrefs,
            isBatteryOptimized = {
                getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
            },
            loadExcludedIcons = { packages ->
                val icons = mutableListOf<Bitmap>()
                var loaded = 0
                var found = 0
                for (pkg in packages) {
                    val bitmap = try {
                        applicationContext.packageManager
                            .getApplicationIcon(pkg)
                            .toBitmap(width = 64, height = 64)
                    } catch (_: PackageManager.NameNotFoundException) {
                        null
                    }
                    if (bitmap != null) {
                        found++
                        if (loaded < 3) {
                            icons.add(bitmap)
                            loaded++
                        }
                    }
                }
                icons to (found - loaded).coerceAtLeast(0)
            },
            ioDispatcher = Dispatchers.IO,
            ownPackageName = packageName,
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
        lifecycleScope.launch {
            val canWrite = withContext(Dispatchers.IO) { grayscaleManager.canWriteSecureSettings() }
            isAdbPermissionGranted = canWrite

            val powerManager = getSystemService(PowerManager::class.java)
            isBatteryUnrestricted = powerManager.isIgnoringBatteryOptimizations(packageName)

            homeViewModel.refreshAttentionCount()
            homeViewModel.refreshExcludedAppIcons()
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

        lifecycleScope.launch {
            homeViewModel.enforcementInterval
                .drop(1)
                .collect { interval ->
                    val intent = Intent(this@MainActivity, GrayoutService::class.java)
                        .putExtra(GrayoutService.EXTRA_INTERVAL, interval)
                    startForegroundService(intent)
                }
        }

        refreshSystemChecks()

        setContent {
            GrayoutTheme {
                val navController = rememberNavController()
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            refreshSystemChecks()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

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
            Settings.Secure.getUriFor("accessibility_display_daltonizer_enabled"),
            false,
            grayscaleObserver,
        )
    }

    override fun onStop() {
        super.onStop()
        contentResolver.unregisterContentObserver(grayscaleObserver)
    }
}

package com.princeyadav.grayout.ui

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.princeyadav.grayout.service.GrayscaleManager
import kotlinx.coroutines.delay

/** ADB grants can arrive without an activity resume. Only poll while this screen is visible. */
@Composable
fun rememberSecureSettingsPermission(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { GrayscaleManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    // produceState only uses its initial value once; don't re-probe on every recomposition.
    val initial = remember(lifecycleOwner, manager) { manager.canWriteSecureSettings() }
    return produceState(initial, lifecycleOwner, manager) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = manager.canWriteSecureSettings()
                delay(1_000)
            }
        }
    }
}

/** Developer options and USB debugging switches, for the setup guide. Reads only; polled while visible. */
data class AdbToggles(val developerOptionsOn: Boolean, val usbDebuggingOn: Boolean)

@Composable
fun rememberAdbToggles(): State<AdbToggles> {
    val context = LocalContext.current.applicationContext
    val lifecycleOwner = LocalLifecycleOwner.current
    val initial = remember(lifecycleOwner, context) { readAdbToggles(context) }
    return produceState(initial, lifecycleOwner, context) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = readAdbToggles(context)
                delay(1_000)
            }
        }
    }
}

private fun readAdbToggles(context: Context): AdbToggles {
    val resolver = context.contentResolver
    return AdbToggles(
        developerOptionsOn = Settings.Global.getInt(resolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1,
        usbDebuggingOn = Settings.Global.getInt(resolver, Settings.Global.ADB_ENABLED, 0) == 1,
    )
}

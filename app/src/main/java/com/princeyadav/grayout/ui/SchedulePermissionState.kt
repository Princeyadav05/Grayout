package com.princeyadav.grayout.ui

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
fun rememberSchedulePermission(): State<Boolean> {
    val context = LocalContext.current.applicationContext
    val manager = remember(context) { GrayscaleManager(context) }
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(manager.canWriteSecureSettings(), lifecycleOwner, manager) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = manager.canWriteSecureSettings()
                delay(1_000)
            }
        }
    }
}

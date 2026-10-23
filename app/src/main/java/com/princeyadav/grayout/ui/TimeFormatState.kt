package com.princeyadav.grayout.ui

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

/** The device 12/24-hour setting, re-read on resume so a change in system Settings shows up. */
@Composable
fun rememberIs24HourFormat(): Boolean {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var is24Hour by remember(context) { mutableStateOf(DateFormat.is24HourFormat(context)) }
    LaunchedEffect(lifecycleOwner, context) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            is24Hour = DateFormat.is24HourFormat(context)
        }
    }
    return is24Hour
}

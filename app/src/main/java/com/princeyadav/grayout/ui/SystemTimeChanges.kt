package com.princeyadav.grayout.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/** A resumed screen owns its receiver; stopping collection unregisters it. */
internal fun Context.systemTimeChanges(): Flow<Unit> = callbackFlow {
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
                trySend(Unit)
            }
        }
    }
    ContextCompat.registerReceiver(
        this@systemTimeChanges,
        receiver,
        IntentFilter().apply {
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        },
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
    // Also recompute after registration, closing the race with a clock change
    // between the initial screen calculation and receiver registration.
    trySend(Unit)
    awaitClose { unregisterReceiver(receiver) }
}.buffer(Channel.CONFLATED)

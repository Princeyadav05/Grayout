package com.princeyadav.grayout.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View

enum class HapticAction { Toggle, Commit, Destructive }

fun View.performHaptic(action: HapticAction) {
    val constant = when (action) {
        HapticAction.Toggle -> when {
            Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.TOGGLE_ON
            else -> HapticFeedbackConstants.CLOCK_TICK
        }
        HapticAction.Commit -> when {
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.CONFIRM
            else -> HapticFeedbackConstants.CONTEXT_CLICK
        }
        HapticAction.Destructive -> when {
            Build.VERSION.SDK_INT >= 30 -> HapticFeedbackConstants.REJECT
            else -> HapticFeedbackConstants.LONG_PRESS
        }
    }
    performHapticFeedback(constant)
}

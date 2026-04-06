package com.princeyadav.grayout.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.ImageBitmap

@Immutable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap,
    val isExcluded: Boolean,
)

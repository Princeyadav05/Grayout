package com.princeyadav.grayout.model

import androidx.compose.ui.graphics.ImageBitmap

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: ImageBitmap,
    val isExcluded: Boolean,
)

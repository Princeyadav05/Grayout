package com.princeyadav.grayout.model

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val appName: String,
    val icon: Drawable,
    val isExcluded: Boolean,
)

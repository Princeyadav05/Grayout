package com.princeyadav.grayout.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import com.princeyadav.grayout.model.AppCategory
import com.princeyadav.grayout.model.AppInfo

/**
 * Launchable apps other than Grayout, sorted by name and tagged with [AppCategory].
 * [AppInfo.isExcluded] is left false: the exclusion screen applies the current
 * preferences when it publishes the list.
 */
fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    // Not ACTION_IMAGE_CAPTURE or ACTION_VIEW image/* / video/*: those also match
    // capture helpers, browsers, file managers and messengers.
    val camera = pm.handlers(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) +
        pm.handlers(Intent(MediaStore.INTENT_ACTION_VIDEO_CAMERA))
    val gallery = pm.handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY))
    val maps = pm.handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MAPS)) +
        pm.handlers(Intent(Intent.ACTION_VIEW, "geo:0,0".toUri()))

    return pm.getInstalledApplications(0)
        .filter { info ->
            pm.getLaunchIntentForPackage(info.packageName) != null &&
                info.packageName != context.packageName
        }
        .map { info ->
            AppInfo(
                packageName = info.packageName,
                appName = info.loadLabel(pm).toString(),
                icon = info.loadIcon(pm)
                    .toBitmap(width = 80, height = 80)
                    .asImageBitmap(),
                isExcluded = false,
                categories = buildSet {
                    if (info.packageName in camera) add(AppCategory.CAMERA)
                    if (info.packageName in gallery || info.category == ApplicationInfo.CATEGORY_IMAGE) {
                        add(AppCategory.PHOTOS)
                    }
                    if (info.packageName in maps || info.category == ApplicationInfo.CATEGORY_MAPS) {
                        add(AppCategory.MAPS)
                    }
                },
            )
        }
        .sortedBy { it.appName.lowercase() }
}

private fun PackageManager.handlers(intent: Intent): Set<String> {
    val matches = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        queryIntentActivities(intent, 0)
    }
    return matches.mapTo(HashSet()) { it.activityInfo.packageName }
}

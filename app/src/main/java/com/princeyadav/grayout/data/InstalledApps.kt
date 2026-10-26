package com.princeyadav.grayout.data

import android.content.Context
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import com.princeyadav.grayout.model.AppInfo

/**
 * Launchable apps other than Grayout, sorted by name. [AppInfo.isExcluded] is left false:
 * the exclusion screen applies the current preferences when it publishes the list.
 */
fun loadInstalledApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
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
            )
        }
        .sortedBy { it.appName.lowercase() }
}

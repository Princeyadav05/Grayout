package com.princeyadav.grayout.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.components.AdbCommandSheet
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.SettingsRow
import com.princeyadav.grayout.ui.components.StatusValue
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun SettingsScreen(
    isServiceRunning: Boolean,
    versionName: String,
    isAdbPermissionGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    needsExactAlarmAccess: Boolean,
    notificationsOn: Boolean,
    onNotificationsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onUsageAccessClick: () -> Unit,
    onExactAlarmAccessClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPad),
    ) {
        Spacer(modifier = Modifier.height(dimens.sectionGap))

        Text(
            text = "Settings",
            style = typography.headingMedium,
            color = colors.text,
        )

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        PermissionsCard(
            isAdbPermissionGranted = isAdbPermissionGranted,
            isUsageAccessGranted = isUsageAccessGranted,
            isBatteryUnrestricted = isBatteryUnrestricted,
            needsExactAlarmAccess = needsExactAlarmAccess,
            notificationsOn = notificationsOn,
            onNotificationsClick = onNotificationsClick,
            onBatteryOptimizationClick = onBatteryOptimizationClick,
            onUsageAccessClick = onUsageAccessClick,
            onExactAlarmAccessClick = onExactAlarmAccessClick,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        AboutCard(versionName = versionName, isServiceRunning = isServiceRunning)

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        Text(
            text = "Reduce screen addiction, one shade at a time",
            style = typography.labelSmall,
            color = colors.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.height(dimens.sectionGap))
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = GrayoutTheme.typography.labelSmall,
        color = BrandAccent.copy(alpha = 0.75f),
    )
}

@Composable
private fun PermissionsCard(
    isAdbPermissionGranted: Boolean,
    isUsageAccessGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    needsExactAlarmAccess: Boolean,
    notificationsOn: Boolean,
    onNotificationsClick: () -> Unit,
    onBatteryOptimizationClick: () -> Unit,
    onUsageAccessClick: () -> Unit,
    onExactAlarmAccessClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens

    var showAdbSheet by remember { mutableStateOf(false) }

    GrayoutCard {
        Column(modifier = Modifier.padding(dimens.cardPad)) {
            SectionHeader("PERMISSIONS")

            SettingsRow(
                label = "Grayscale permission",
                subtitle = if (isAdbPermissionGranted) null else "Needed to turn grayscale on and off",
                onClick = { showAdbSheet = true },
                trailing = {
                    StatusValue(
                        text = if (isAdbPermissionGranted) "Granted" else "Not granted",
                        isProblem = !isAdbPermissionGranted,
                    )
                },
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            SettingsRow(
                label = "Usage access",
                subtitle = if (isUsageAccessGranted) null else "Needed to keep apps in color",
                onClick = if (!isUsageAccessGranted) onUsageAccessClick else null,
                trailing = {
                    StatusValue(
                        text = if (isUsageAccessGranted) "Granted" else "Not granted",
                        isProblem = !isUsageAccessGranted,
                    )
                },
            )

            if (needsExactAlarmAccess) {
                HorizontalDivider(thickness = 1.dp, color = colors.border)

                SettingsRow(
                    label = "Alarms & reminders",
                    subtitle = "Needed for on-time schedules",
                    onClick = onExactAlarmAccessClick,
                    trailing = { StatusValue(text = "Not granted", isProblem = true) },
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            // Optional, so "Off" is neutral rather than a problem.
            SettingsRow(
                label = "Notifications",
                subtitle = if (notificationsOn) null else "Turn on to see when grayscale comes back",
                onClick = onNotificationsClick,
                trailing = {
                    if (notificationsOn) {
                        StatusValue(text = "On", isProblem = false)
                    } else {
                        Text(text = "Off", style = GrayoutTheme.typography.bodyMedium, color = colors.textMuted)
                    }
                },
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            SettingsRow(
                label = "Battery",
                subtitle = if (isBatteryUnrestricted) null else "Keeps automatic restore on time",
                onClick = if (!isBatteryUnrestricted) onBatteryOptimizationClick else null,
                trailing = {
                    StatusValue(
                        text = if (isBatteryUnrestricted) "Unrestricted" else "Restricted",
                        isProblem = !isBatteryUnrestricted,
                    )
                },
            )
        }
    }

    if (showAdbSheet) {
        AdbCommandSheet(
            isGranted = isAdbPermissionGranted,
            onDismiss = { showAdbSheet = false },
        )
    }
}

@Composable
private fun AboutCard(versionName: String, isServiceRunning: Boolean) {
    val dimens = GrayoutTheme.dimens

    GrayoutCard {
        Column(modifier = Modifier.padding(dimens.cardPad)) {
            SectionHeader("ABOUT")

            SettingsRow(
                label = "Grayout $versionName",
                subtitle = if (isServiceRunning) "Running in the background" else "Not running",
            )
        }
    }
}

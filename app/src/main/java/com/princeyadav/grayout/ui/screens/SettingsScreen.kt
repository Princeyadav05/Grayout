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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.SettingsRow
import com.princeyadav.grayout.ui.components.StatusDot
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun SettingsScreen(
    enforcementInterval: Int,
    isAdbPermissionGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    onBatteryOptimizationClick: () -> Unit,
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

        ServiceCard(enforcementInterval = enforcementInterval)

        Spacer(modifier = Modifier.height(dimens.cardGap))

        SetupCard(
            isAdbPermissionGranted = isAdbPermissionGranted,
            isBatteryUnrestricted = isBatteryUnrestricted,
            onBatteryOptimizationClick = onBatteryOptimizationClick,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        AboutCard()

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        Text(
            text = "Reduce screen addiction, one shade at a time",
            style = typography.labelSmall,
            color = colors.textDim,
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
        color = GrayoutTheme.colors.accent,
    )
}

@Composable
private fun ServiceCard(enforcementInterval: Int) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens

    GrayoutCard {
        Column(modifier = Modifier.padding(dimens.cardPad)) {
            SectionHeader("SERVICE")

            SettingsRow(
                label = "Enforcement status",
                subtitle = if (enforcementInterval > 0) "Every ${enforcementInterval}m" else "Off",
                trailing = { StatusDot(isActive = enforcementInterval > 0) },
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            SettingsRow(
                label = "Service status",
                subtitle = "Running",
                trailing = { StatusDot(isActive = true) },
            )
        }
    }
}

@Composable
private fun SetupCard(
    isAdbPermissionGranted: Boolean,
    isBatteryUnrestricted: Boolean,
    onBatteryOptimizationClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens

    GrayoutCard {
        Column(modifier = Modifier.padding(dimens.cardPad)) {
            SectionHeader("SETUP")

            SettingsRow(
                label = "ADB permission",
                subtitle = "adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS",
                trailing = {
                    Text(
                        text = if (isAdbPermissionGranted) "Granted" else "Not granted",
                        style = GrayoutTheme.typography.bodySmall,
                        color = if (isAdbPermissionGranted) colors.success else colors.danger,
                    )
                },
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            SettingsRow(
                label = "Battery optimization",
                subtitle = "Disable battery optimization for reliable enforcement",
                onClick = if (!isBatteryUnrestricted) onBatteryOptimizationClick else null,
                trailing = {
                    Text(
                        text = if (isBatteryUnrestricted) "Unrestricted" else "Restricted",
                        style = GrayoutTheme.typography.bodySmall,
                        color = if (isBatteryUnrestricted) colors.success else colors.danger,
                    )
                },
            )
        }
    }
}

@Composable
private fun AboutCard() {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens

    GrayoutCard {
        Column(modifier = Modifier.padding(dimens.cardPad)) {
            SectionHeader("ABOUT")

            SettingsRow(
                label = "Version",
                trailing = {
                    Text(
                        text = "1.0",
                        style = GrayoutTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                },
            )

            HorizontalDivider(thickness = 1.dp, color = colors.border)

            SettingsRow(
                label = "Package",
                trailing = {
                    Text(
                        text = "com.princeyadav.grayout",
                        style = GrayoutTheme.typography.bodySmall,
                        color = colors.textMuted,
                    )
                },
            )
        }
    }
}

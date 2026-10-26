package com.princeyadav.grayout.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.R
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeScheduleLine

@Composable
fun HomeScreen(
    isGrayscaleOn: Boolean,
    enforcementInterval: Int,
    excludedAppCount: Int,
    isUsageAccessGranted: Boolean,
    onToggle: () -> Unit,
    onEnforcementIntervalChange: (Int) -> Unit,
    onNavigateToExclusions: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    scheduleLine: HomeScheduleLine?,
    excludedAppIcons: List<Bitmap>,
    excludedOverflowCount: Int,
    isPermissionGranted: Boolean,
    toggleFailedTarget: Boolean?,
    onShowSetup: () -> Unit,
    onFixUsageAccess: () -> Unit,
    showNotificationPrompt: Boolean,
    onAllowNotifications: () -> Unit,
    onDismissNotificationPrompt: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dimens = GrayoutTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPad),
    ) {
        Spacer(modifier = Modifier.height(dimens.sectionGap))

        AppHeader()

        if (isPermissionGranted) {
            StatusCard(
                isGrayscaleOn = isGrayscaleOn,
                scheduleLine = scheduleLine,
                toggleFailedTarget = toggleFailedTarget,
                onToggle = onToggle,
                onScheduleClick = onNavigateToSchedules,
            )
        } else {
            SetupCard(onShowSetup = onShowSetup)
        }

        Spacer(modifier = Modifier.height(dimens.cardGap))

        RestoreCard(
            enforcementInterval = enforcementInterval,
            onIntervalChange = onEnforcementIntervalChange,
        )

        if (showNotificationPrompt) {
            Spacer(modifier = Modifier.height(dimens.cardGap))
            NotificationPromptCard(onAllow = onAllowNotifications, onDismiss = onDismissNotificationPrompt)
        }

        Spacer(modifier = Modifier.height(dimens.cardGap))

        KeptInColorCard(
            count = excludedAppCount,
            isUsageAccessGranted = isUsageAccessGranted,
            excludedAppIcons = excludedAppIcons,
            excludedOverflowCount = excludedOverflowCount,
            onClick = onNavigateToExclusions,
            onFixUsageAccess = onFixUsageAccess,
        )

        Spacer(modifier = Modifier.height(dimens.sectionGap))
    }
}

@Composable
private fun AppHeader() {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
        modifier = Modifier.padding(vertical = 12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_grayout_foreground),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            colorFilter = ColorFilter.tint(BrandAccent),
        )

        Text(
            text = "Grayout",
            style = typography.headingSmall,
            color = colors.text,
        )
    }
}

@Composable
private fun Eyebrow(text: String) {
    Text(
        text = text,
        style = GrayoutTheme.typography.labelSmall,
        color = BrandAccent.copy(alpha = 0.75f),
    )
}

/** Replaces the status card until the grayscale permission is granted, so the switch never fails silently. */
@Composable
private fun SetupCard(onShowSetup: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    GrayoutCard(isActive = true, wash = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Eyebrow("SETUP")
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Text(
                text = "One-time setup",
                style = typography.headingLarge,
                color = colors.text,
            )
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Text(
                text = "Grayout needs one permission that only a computer can grant. It takes about 2 minutes, once.",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(modifier = Modifier.height(dimens.sectionGap))
            PillButton(label = "Show the command", primary = true, onClick = onShowSetup)
        }
    }
}

@Composable
private fun StatusCard(
    isGrayscaleOn: Boolean,
    scheduleLine: HomeScheduleLine?,
    toggleFailedTarget: Boolean?,
    onToggle: () -> Unit,
    onScheduleClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    GrayoutCard(isActive = isGrayscaleOn, wash = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Eyebrow("STATUS")

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (isGrayscaleOn) "Grayscale on" else "Grayscale off",
                    style = typography.headingLarge,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                GrayoutToggle(
                    checked = isGrayscaleOn,
                    onCheckedChange = {
                        view.performHaptic(HapticAction.Toggle)
                        onToggle()
                    },
                    contentDescription = "Grayscale",
                )
            }

            if (toggleFailedTarget != null) {
                ToggleFailureRow(target = toggleFailedTarget, onRetry = onToggle)
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = colors.border,
                modifier = Modifier.padding(vertical = dimens.cardGap),
            )

            ScheduleRow(line = scheduleLine, onClick = onScheduleClick)
        }
    }
}

@Composable
private fun ScheduleRow(line: HomeScheduleLine?, onClick: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val text = when {
        line == null -> "No upcoming schedules"
        line.isActive -> "${line.name}, until ${line.time}"
        else -> "Next: ${line.name}, ${line.dayPrefix?.let { "$it " }.orEmpty()}${line.time}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouch)
            .clickable(onClickLabel = "Open schedules", role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
    ) {
        if (line != null) {
            Box(
                modifier = Modifier
                    .size(dimens.dotSize)
                    .background(colors.schedulePalette[line.colorIndex], CircleShape),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = "Schedule", style = typography.labelSmall, color = colors.textMuted)
            Text(text = text, style = typography.bodyMedium, color = colors.text)
        }
        if (line?.isActive == true) {
            NowPill()
        } else {
            Chevron()
        }
    }
}

/** Lavender "Now" marker for a schedule that is running. Shared with the schedule list. */
@Composable
internal fun NowPill() {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    Text(
        text = "Now",
        style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
        color = colors.bg,
        modifier = Modifier
            .background(colors.accent, RoundedCornerShape(dimens.radiusFull))
            .padding(horizontal = dimens.tightGap, vertical = 2.dp),
    )
}

@Composable
private fun Chevron() {
    Text(
        text = "›",
        style = GrayoutTheme.typography.headingMedium,
        color = GrayoutTheme.colors.textDim,
        modifier = Modifier.clearAndSetSemantics {},
    )
}

/** Inline failure under the switch. Shown only when the permission is granted but the write still failed. */
@Composable
private fun ToggleFailureRow(target: Boolean, onRetry: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.tightGap)
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = colors.danger,
            modifier = Modifier.size(dimens.iconSm),
        )
        Text(
            text = if (target) "Couldn't turn on grayscale" else "Couldn't turn off grayscale",
            style = typography.bodyMedium,
            color = colors.danger,
            modifier = Modifier.weight(1f),
        )
        TextAction(label = "Try again", onClick = onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestoreCard(
    enforcementInterval: Int,
    onIntervalChange: (Int) -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    var showSheet by rememberSaveable { mutableStateOf(false) }

    GrayoutCard {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Eyebrow("AUTOMATIC RESTORE")
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = intervalLabel(enforcementInterval),
                    style = typography.mono,
                    color = colors.text,
                )
                PillButton(label = "Change", onClick = { showSheet = true })
            }
            Text(
                text = if (enforcementInterval > 0) {
                    "Turns grayscale back on if you turn it off."
                } else {
                    "Grayscale stays however you leave it."
                },
                style = typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }

    if (showSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = colors.surface,
            contentColor = colors.text,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.cardPadLarge)
                    .padding(bottom = dimens.sectionGap),
            ) {
                Text(text = "Automatic restore", style = typography.headingSmall, color = colors.text)
                Spacer(modifier = Modifier.height(dimens.tightGap))
                Text(
                    text = "If grayscale gets turned off, turn it back on after:",
                    style = typography.bodyMedium,
                    color = colors.textMuted,
                )
                Spacer(modifier = Modifier.height(dimens.tightGap))
                Column(modifier = Modifier.selectableGroup()) {
                    EnforcementPrefs.INTERVAL_OPTIONS.forEach { value ->
                        val selected = value == enforcementInterval
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = dimens.minTouch)
                                .selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = {
                                        onIntervalChange(value)
                                        showSheet = false
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = selected,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = colors.accent,
                                    unselectedColor = colors.textMuted,
                                ),
                            )
                            Spacer(modifier = Modifier.size(dimens.cardGap))
                            Text(
                                text = intervalLabel(value),
                                style = typography.bodyMedium,
                                color = colors.text,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Asked once, right after automatic restore is first turned on, instead of at app launch. */
@Composable
private fun NotificationPromptCard(onAllow: () -> Unit, onDismiss: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    GrayoutCard(isActive = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Text(text = "See when grayscale comes back?", style = typography.titleMedium, color = colors.text)
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Text(
                text = "Grayout can show a countdown and tell you if a permission stops working.",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )
            Spacer(modifier = Modifier.height(dimens.cardGap))
            Row(horizontalArrangement = Arrangement.spacedBy(dimens.tightGap)) {
                PillButton(label = "Allow notifications", primary = true, onClick = onAllow)
                PillButton(label = "Not now", onClick = onDismiss)
            }
        }
    }
}

private fun intervalLabel(minutes: Int): String = if (minutes == 0) "Off" else "$minutes min"

@Composable
private fun KeptInColorCard(
    count: Int,
    isUsageAccessGranted: Boolean,
    excludedAppIcons: List<Bitmap>,
    excludedOverflowCount: Int,
    onClick: () -> Unit,
    onFixUsageAccess: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    GrayoutCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose apps", onClick = onClick),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Eyebrow("KEPT IN COLOR")
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouch),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
            ) {
                Text(
                    text = when (count) {
                        0 -> "No apps yet"
                        1 -> "1 app"
                        else -> "$count apps"
                    },
                    style = typography.bodyMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                excludedAppIcons.forEach { icon ->
                    Image(
                        bitmap = remember(icon) { icon.asImageBitmap() },
                        contentDescription = null,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
                if (excludedOverflowCount > 0) {
                    Text(
                        text = "+$excludedOverflowCount",
                        style = typography.monoSmall,
                        color = colors.textMuted,
                    )
                }
                Chevron()
            }

            if (count > 0 && !isUsageAccessGranted) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Warning,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier.size(dimens.iconSm),
                    )
                    Text(
                        text = "Paused: usage access is off",
                        style = typography.bodyMedium,
                        color = colors.warning,
                        modifier = Modifier.weight(1f),
                    )
                    TextAction(label = "Fix", onClick = onFixUsageAccess)
                }
            }
        }
    }
}

@Composable
private fun TextAction(label: String, onClick: () -> Unit) {
    val dimens = GrayoutTheme.dimens
    Box(
        modifier = Modifier
            .heightIn(min = dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GrayoutTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = GrayoutTheme.colors.text,
            textDecoration = TextDecoration.Underline,
        )
    }
}

@Composable
private fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusFull)
    Box(
        modifier = Modifier
            .heightIn(min = dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = GrayoutTheme.typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
            color = if (primary) colors.bg else colors.text,
            modifier = Modifier
                .then(
                    if (primary) Modifier.background(colors.text, shape)
                    else Modifier.border(1.dp, colors.borderActive, shape),
                )
                .padding(horizontal = dimens.sectionGap, vertical = dimens.tightGap),
        )
    }
}

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
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.R
import com.princeyadav.grayout.logic.TodayState
import com.princeyadav.grayout.service.EnforcementPrefs
import com.princeyadav.grayout.ui.components.Chevron
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.SectionLabel
import com.princeyadav.grayout.ui.components.TodayStrip
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.rememberIs24HourFormat
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.HomeActiveSchedule
import com.princeyadav.grayout.viewmodel.HomeScheduleLine

@Composable
internal fun HomeScreen(
    isGrayscaleOn: Boolean,
    enforcementInterval: Int,
    excludedAppCount: Int,
    isUsageAccessGranted: Boolean,
    onToggle: () -> Unit,
    onEnforcementIntervalChange: (Int) -> Unit,
    onNavigateToExclusions: () -> Unit,
    onNavigateToSchedules: () -> Unit,
    scheduleLine: HomeScheduleLine?,
    today: TodayState?,
    excludedAppIcons: List<Bitmap>,
    excludedOverflowCount: Int,
    isPermissionGranted: Boolean,
    toggleFailedTarget: Boolean?,
    onShowSetup: () -> Unit,
    onRetryToggle: () -> Unit,
    onFixUsageAccess: () -> Unit,
    onlyDuringSchedules: Boolean,
    onOnlyDuringSchedulesChange: (Boolean) -> Unit,
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
                today = today,
                toggleFailedTarget = toggleFailedTarget,
                onToggle = onToggle,
                onRetryToggle = onRetryToggle,
                onScheduleClick = onNavigateToSchedules,
            )
        } else {
            SetupCard(onShowSetup = onShowSetup)
        }

        Spacer(modifier = Modifier.height(dimens.cardGap))

        RestoreCard(
            enforcementInterval = enforcementInterval,
            onIntervalChange = onEnforcementIntervalChange,
            onlyDuringSchedules = onlyDuringSchedules,
            onOnlyDuringSchedulesChange = onOnlyDuringSchedulesChange,
            isPermissionGranted = isPermissionGranted,
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
            isPermissionGranted = isPermissionGranted,
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
        modifier = Modifier.padding(vertical = dimens.cardGap),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_grayout_foreground),
            contentDescription = null,
            modifier = Modifier.size(dimens.iconLg),
            colorFilter = ColorFilter.tint(BrandAccent),
        )

        Text(
            text = "Grayout",
            style = typography.headingSmall,
            color = colors.text,
        )
    }
}

/** Replaces the status card until the grayscale permission is granted, so the switch never fails silently. */
@Composable
private fun SetupCard(onShowSetup: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    GrayoutCard(isActive = true, wash = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            SectionLabel("SETUP")
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Text(
                text = "One-time setup",
                style = typography.headingLarge,
                color = colors.text,
            )
            Spacer(modifier = Modifier.height(dimens.tightGap))
            // Grayscale may already be on through system settings, so the last sentence names what setup
            // unlocks, never the current state.
            Text(
                text = "Grayout needs one permission that only a computer can grant. It takes about 2 minutes. " +
                    "Until then, Grayout can't switch grayscale.",
                style = typography.bodyRegular,
                color = colors.textMuted,
            )
            Spacer(modifier = Modifier.height(dimens.sectionGap))
            PillButton(label = "Start setup", primary = true, onClick = onShowSetup)
        }
    }
}

@Composable
private fun StatusCard(
    isGrayscaleOn: Boolean,
    scheduleLine: HomeScheduleLine?,
    today: TodayState?,
    toggleFailedTarget: Boolean?,
    onToggle: () -> Unit,
    onRetryToggle: () -> Unit,
    onScheduleClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    GrayoutCard(isActive = isGrayscaleOn, wash = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            SectionLabel("STATUS")

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
                ToggleFailureRow(target = toggleFailedTarget, onRetry = onRetryToggle)
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = colors.border,
                modifier = Modifier.padding(vertical = dimens.cardGap),
            )

            ScheduleRow(line = scheduleLine, today = today, onClick = onScheduleClick)
        }
    }
}

/**
 * Opens Schedules. One line per running schedule, each with its own color dot and end, so overlapping
 * schedules stay apart; the chevron always shows. Under it, today's strip when a schedule runs today.
 */
@Composable
private fun ScheduleRow(line: HomeScheduleLine?, today: TodayState?, onClick: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val running = if (line?.isActive == true) {
        line.active.ifEmpty { listOf(HomeActiveSchedule(line.name, line.colorIndex, line.time)) }
    } else {
        emptyList()
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouch)
            .clickable(onClickLabel = "Open schedules", role = Role.Button, onClick = onClick),
    ) {
        // Caption, dots and lines share one left edge. "until <end>" already says the schedule runs now,
        // so the caption has no Now pill; the chevron ends the caption row.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = when {
                    running.size > 1 -> "Active schedules"
                    running.size == 1 -> "Active schedule"
                    else -> "Schedule"
                },
                style = typography.caption,
                color = colors.textMuted,
                modifier = Modifier.weight(1f),
            )
            Chevron()
        }
        when {
            running.isNotEmpty() -> running.forEach { schedule ->
                RunningScheduleLine(schedule.name, schedule.end, schedule.colorIndex, typography.bodyMedium)
            }
            line != null -> ScheduleLineRow(colorIndex = line.colorIndex) {
                Text(
                    text = "Next: ${line.name}, ${line.dayPrefix?.let { "$it " }.orEmpty()}${line.time}",
                    style = typography.bodyMedium,
                    color = colors.text,
                )
            }
            else -> Text(text = "No upcoming schedules", style = typography.bodyMedium, color = colors.text)
        }
        // Only when a schedule runs today; the strip shows windows, so no sentence repeats them.
        if (today != null && today.segments.isNotEmpty()) {
            Spacer(modifier = Modifier.height(dimens.cardGap))
            TodayStrip(
                today = today,
                is24Hour = rememberIs24HourFormat(),
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

/** A schedule color dot followed by one line of text. */
@Composable
private fun ScheduleLineRow(colorIndex: Int, content: @Composable RowScope.() -> Unit) {
    val dimens = GrayoutTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
    ) {
        Box(
            modifier = Modifier
                .size(dimens.dotSize)
                .background(GrayoutTheme.colors.schedulePalette[colorIndex], CircleShape),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.microGap), content = content)
    }
}

/**
 * One running schedule: color dot, name, "until <end>". Shared by Home's Status card and the Schedules
 * Today card. The name may ellipsize; the end time never wraps or hides.
 */
@Composable
internal fun RunningScheduleLine(name: String, end: String, colorIndex: Int, style: TextStyle) {
    val colors = GrayoutTheme.colors
    ScheduleLineRow(colorIndex = colorIndex) {
        Text(
            text = name,
            style = style,
            color = colors.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(text = "until $end", style = style, color = colors.text, softWrap = false)
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
            .padding(horizontal = dimens.tightGap, vertical = dimens.microGap),
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
    onlyDuringSchedules: Boolean,
    onOnlyDuringSchedulesChange: (Boolean) -> Unit,
    isPermissionGranted: Boolean,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    var showSheet by rememberSaveable { mutableStateOf(false) }

    GrayoutCard {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            SectionLabel("AUTOMATIC RESTORE")
            // Value and helper sit together, like the Kept in color card; the pill centers on both.
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = intervalLabel(enforcementInterval),
                        // Mono only for a number ("5 min"); the word "Off" uses the title font.
                        style = if (enforcementInterval <= 0) typography.titleMedium else typography.mono,
                        color = colors.text,
                    )
                    Text(
                        text = when {
                            // Before setup nothing can turn grayscale on yet, so no line may imply it already works.
                            !isPermissionGranted -> "Takes effect after setup."
                            enforcementInterval <= 0 -> "Schedules still apply."
                            onlyDuringSchedules -> "Only during schedules."
                            else -> "Turns grayscale back on."
                        },
                        style = typography.bodyRegular,
                        color = colors.textMuted,
                    )
                }
                PillButton(label = "Change", onClick = { showSheet = true })
            }
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
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colors.border,
                    modifier = Modifier.padding(vertical = dimens.tightGap),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = dimens.minTouch),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Only during schedules", style = typography.bodyMedium, color = colors.text)
                        Text(
                            text = "Outside a schedule, off stays off.",
                            style = typography.bodyRegular,
                            color = colors.textMuted,
                        )
                    }
                    GrayoutToggle(
                        checked = onlyDuringSchedules,
                        onCheckedChange = onOnlyDuringSchedulesChange,
                        contentDescription = "Only during schedules",
                    )
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
    isPermissionGranted: Boolean,
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
            SectionLabel("KEPT IN COLOR")
            // No minTouch row here: the whole card is the tap target, so the padding stays even top and bottom.
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
            ) {
                Text(
                    text = when (count) {
                        0 -> "None yet"
                        1 -> "1 app"
                        else -> "$count apps"
                    },
                    // The same value style as the restore card's "Off".
                    style = typography.titleMedium,
                    color = colors.text,
                    modifier = Modifier.weight(1f),
                )
                excludedAppIcons.forEach { icon ->
                    Image(
                        bitmap = remember(icon) { icon.asImageBitmap() },
                        contentDescription = null,
                        modifier = Modifier
                            .size(dimens.iconMd)
                            .clip(RoundedCornerShape(dimens.radiusXs)),
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

            if (count == 0) {
                Text(
                    text = if (isPermissionGranted) "Grayscale applies to every app." else "Takes effect after setup.",
                    style = typography.bodyRegular,
                    color = colors.textMuted,
                )
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
internal fun TextAction(label: String, onClick: () -> Unit) {
    val dimens = GrayoutTheme.dimens
    Box(
        modifier = Modifier
            .sizeIn(minWidth = dimens.minTouch, minHeight = dimens.minTouch)
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

/** Outlined (or filled when [primary]) pill action, like Home's "Change". Shared with the exclusions banner. */
@Composable
internal fun PillButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
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
                    else Modifier.border(1.dp, colors.controlOutline, shape),
                )
                .padding(horizontal = dimens.sectionGap, vertical = dimens.tightGap),
        )
    }
}

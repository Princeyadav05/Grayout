package com.princeyadav.grayout.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.logic.TodayState
import com.princeyadav.grayout.logic.daysInWords
import com.princeyadav.grayout.logic.scheduleColorIndex
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.daysOfWeekList
import com.princeyadav.grayout.model.formatTime
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.rememberIs24HourFormat
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
internal fun ScheduleListScreen(
    schedules: List<Schedule>,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    onToggleEnabled: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    firingScheduleIds: Set<Long> = emptySet(),
    isPermissionGranted: Boolean = true,
    onSetupPermission: () -> Unit = {},
    today: TodayState? = null,
    enableError: Pair<Long, String>? = null,
    needsExactAlarms: Boolean = false,
    onAllowExactAlarms: () -> Unit = {},
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val is24Hour = rememberIs24HourFormat()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(horizontal = dimens.screenPad),
    ) {
        item {
            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Schedules",
                    style = typography.headingMedium,
                    color = colors.text,
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .heightIn(min = dimens.minTouch)
                        .clip(RoundedCornerShape(dimens.radiusFull))
                        .clickable(role = Role.Button, onClick = onAddSchedule),
                ) {
                    Text(
                        text = "+ Add",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.bg,
                        modifier = Modifier
                            .background(colors.text, RoundedCornerShape(dimens.radiusFull))
                            .padding(horizontal = dimens.sectionGap, vertical = dimens.tightGap),
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))
        }

        if (schedules.isEmpty()) {
            item {
                GrayoutCard(wash = true) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimens.cardPad),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Spacer(modifier = Modifier.height(dimens.sectionGap))
                        Text(
                            text = "No schedules yet",
                            style = typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(dimens.tightGap))
                        Text(
                            text = "Tap + Add to turn grayscale on at set times",
                            style = typography.bodyMedium,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(dimens.sectionGap))
                    }
                }
            }
        } else {
            if (today != null) {
                item {
                    TodayCard(today = today, schedules = schedules, is24Hour = is24Hour, isPermissionGranted = isPermissionGranted)
                    Spacer(modifier = Modifier.height(dimens.cardGap))
                }
            }

            item {
                GrayoutCard {
                    Column(modifier = Modifier.padding(horizontal = dimens.cardPad)) {
                        schedules.forEachIndexed { index, schedule ->
                            if (index > 0) HorizontalDivider(thickness = 1.dp, color = colors.border)
                            ScheduleRow(
                                schedule = schedule,
                                is24Hour = is24Hour,
                                isFiringNow = isPermissionGranted && schedule.id in firingScheduleIds,
                                needsPermission = !isPermissionGranted && schedule.isEnabled,
                                mayRunLate = isPermissionGranted && needsExactAlarms && schedule.isEnabled,
                                error = enableError?.takeIf { it.first == schedule.id }?.second,
                                onEdit = { onEditSchedule(schedule.id) },
                                onToggle = { onToggleEnabled(schedule) },
                                onSetupPermission = onSetupPermission,
                                onAllowExactAlarms = onAllowExactAlarms,
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(dimens.sectionGap))
        }
    }
}

/** A 24-hour strip of today's windows in schedule colors, a now marker, and one sentence. */
@Composable
private fun TodayCard(today: TodayState, schedules: List<Schedule>, is24Hour: Boolean, isPermissionGranted: Boolean) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val line = today.line
    val sentence = when {
        !isPermissionGranted -> "Schedules need the grayscale permission to run"
        line == null -> "Nothing scheduled"
        line.isActive -> "${line.name} is on until ${formatTime(line.at.hour, line.at.minute, is24Hour)}"
        line.at.toLocalDate() == today.date -> "Next: ${line.name} at ${formatTime(line.at.hour, line.at.minute, is24Hour)}"
        else -> "Nothing else scheduled today"
    }
    val idToColor = schedules.associate { it.id to colors.schedulePalette[scheduleColorIndex(it.id)] }

    GrayoutCard(
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Today. $sentence" },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Text(text = "Today", style = typography.labelSmall, color = colors.textMuted)
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimens.stripHeight)
                    .clip(RoundedCornerShape(dimens.radiusSm))
                    .background(colors.bg)
                    .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusSm)),
            ) {
                today.segments.forEach { segment ->
                    val color = idToColor[segment.scheduleId] ?: return@forEach
                    drawRect(
                        color = color,
                        topLeft = Offset(segment.start * size.width, 0f),
                        size = Size((segment.end - segment.start) * size.width, size.height),
                    )
                }
                val markerWidth = 2.dp.toPx()
                drawRect(
                    color = colors.text,
                    topLeft = Offset(today.now * size.width - markerWidth / 2, 0f),
                    size = Size(markerWidth, size.height),
                )
            }
            Spacer(modifier = Modifier.height(dimens.microGap))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val marks = if (is24Hour) listOf("00", "06", "12", "18", "24") else listOf("12a", "6a", "12p", "6p", "12a")
                marks.forEach { Text(text = it, style = typography.labelXSmall, color = colors.textMuted) }
            }
            Spacer(modifier = Modifier.height(dimens.tightGap))
            Text(text = sentence, style = typography.bodyMedium, color = colors.text)
        }
    }
}

@Composable
private fun ScheduleRow(
    schedule: Schedule,
    is24Hour: Boolean,
    isFiringNow: Boolean,
    needsPermission: Boolean,
    mayRunLate: Boolean,
    error: String?,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
    onSetupPermission: () -> Unit,
    onAllowExactAlarms: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current
    val times = "${formatTime(schedule.startTimeHour, schedule.startTimeMinute, is24Hour)} → " +
        formatTime(schedule.endTimeHour, schedule.endTimeMinute, is24Hour)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit ${schedule.name}", onClick = onEdit)
            .padding(vertical = dimens.cardGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
    ) {
        Box(
            modifier = Modifier
                .size(dimens.dotSize)
                .background(colors.schedulePalette[scheduleColorIndex(schedule.id)], CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
            ) {
                Text(
                    text = schedule.name,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = colors.text,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isFiringNow) NowPill()
            }
            Text(
                text = "$times · ${daysInWords(schedule.daysOfWeekList)}",
                style = typography.monoSmall,
                color = colors.textMuted,
            )
            when {
                error != null -> ProblemLine(Icons.Filled.Warning, error, colors.warning, live = true)
                needsPermission -> ProblemLine(
                    Icons.Filled.Warning, "Needs the grayscale permission", colors.warning,
                    action = "Fix", onAction = onSetupPermission,
                )
                mayRunLate -> ProblemLine(
                    Icons.Filled.Info, "Might start a few minutes late", colors.textMuted,
                    action = "Allow", onAction = onAllowExactAlarms,
                )
            }
        }
        GrayoutToggle(
            checked = schedule.isEnabled,
            onCheckedChange = {
                view.performHaptic(HapticAction.Toggle)
                onToggle()
            },
            contentDescription = "Schedule ${schedule.name}",
        )
    }
}

@Composable
private fun ProblemLine(
    icon: ImageVector,
    text: String,
    color: androidx.compose.ui.graphics.Color,
    live: Boolean = false,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val dimens = GrayoutTheme.dimens
    Row(
        modifier = if (live) Modifier.semantics { liveRegion = LiveRegionMode.Polite } else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(dimens.iconSm))
        Text(
            text = text,
            style = GrayoutTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (action != null) TextAction(label = action, onClick = onAction)
    }
}

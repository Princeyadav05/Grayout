package com.princeyadav.grayout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.model.Schedule
import com.princeyadav.grayout.model.formatTime12Hour
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import java.time.DayOfWeek

@Composable
fun ScheduleListScreen(
    schedules: List<Schedule>,
    onAddSchedule: () -> Unit,
    onEditSchedule: (Long) -> Unit,
    onToggleEnabled: (Schedule) -> Unit,
    modifier: Modifier = Modifier,
    firingScheduleIds: Set<Long> = emptySet(),
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

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
                        .heightIn(min = 48.dp)
                        .background(colors.text, RoundedCornerShape(dimens.radiusFull))
                        .clip(RoundedCornerShape(dimens.radiusFull))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onAddSchedule() }
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "+ Add",
                        style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.bg,
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Text(
                text = "Automatic grayscale windows",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

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
                            text = "Tap + Add to create your first schedule",
                            style = typography.bodyMedium,
                            color = colors.textDim,
                            textAlign = TextAlign.Center,
                        )

                        Spacer(modifier = Modifier.height(dimens.sectionGap))
                    }
                }
            }
        }

        items(schedules, key = { it.id }) { schedule ->
            ScheduleCard(
                schedule = schedule,
                isFiringNow = schedule.id in firingScheduleIds,
                onEdit = { onEditSchedule(schedule.id) },
                onToggle = { onToggleEnabled(schedule) },
            )

            Spacer(modifier = Modifier.height(dimens.cardGap))
        }

        item {
            Spacer(modifier = Modifier.height(dimens.sectionGap))
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: Schedule,
    isFiringNow: Boolean,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val days = schedule.daysOfWeek.split(",").map { it.trim() }.toSet()
    val view = LocalView.current

    val badgeText = when {
        isFiringNow -> "Now"
        schedule.isEnabled -> "On"
        else -> "Off"
    }
    val badgeBg = when {
        isFiringNow -> BrandAccent
        schedule.isEnabled -> colors.text
        else -> Color.Transparent
    }
    val badgeTextColor = if (schedule.isEnabled) colors.bg else colors.offText
    val badgeBorderColor = if (schedule.isEnabled) Color.Transparent else colors.border
    val badgeFontWeight = if (schedule.isEnabled) FontWeight.ExtraBold else FontWeight.SemiBold

    GrayoutCard(
        isActive = isFiringNow,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onEdit() },
    ) {
        val accentBarModifier = if (isFiringNow) {
            Modifier.drawBehind {
                val barWidthPx = 2.dp.toPx()
                val topInsetPx = 12.dp.toPx()
                val bottomInsetPx = 12.dp.toPx()
                drawRect(
                    color = BrandAccent,
                    topLeft = Offset(0f, topInsetPx),
                    size = Size(
                        width = barWidthPx,
                        height = size.height - topInsetPx - bottomInsetPx,
                    ),
                )
            }
        } else {
            Modifier
        }

        Column(
            modifier = Modifier
                .then(accentBarModifier)
                .padding(dimens.cardPad),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = badgeText,
                    style = typography.labelXSmall.copy(fontWeight = badgeFontWeight),
                    color = badgeTextColor,
                    modifier = Modifier
                        .background(badgeBg, RoundedCornerShape(dimens.radiusFull))
                        .border(
                            1.dp,
                            badgeBorderColor,
                            RoundedCornerShape(dimens.radiusFull),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

                GrayoutToggle(
                    checked = schedule.isEnabled,
                    onCheckedChange = {
                        view.performHaptic(HapticAction.Toggle)
                        onToggle()
                    },
                )
            }

            val contentAlpha = if (schedule.isEnabled) 1f else 0.5f

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Column(modifier = Modifier.alpha(contentAlpha)) {
                Text(
                    text = schedule.name,
                    style = typography.titleMedium,
                    color = if (schedule.isEnabled) colors.text else colors.textMuted,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "${formatTime12Hour(schedule.startTimeHour, schedule.startTimeMinute)} \u2192 ${formatTime12Hour(schedule.endTimeHour, schedule.endTimeMinute)}",
                    style = typography.monoSmall,
                    color = if (schedule.isEnabled) colors.text else colors.textMuted,
                )

                Spacer(modifier = Modifier.height(dimens.tightGap))

                DayDotsRow(
                    selectedDays = days,
                    dotSize = 32.dp,
                    isEnabled = schedule.isEnabled,
                )
            }
        }
    }
}

@Composable
private fun DayDotsRow(
    selectedDays: Set<String>,
    dotSize: androidx.compose.ui.unit.Dp,
    isEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val dayLabels = listOf(
        DayOfWeek.MONDAY to "M",
        DayOfWeek.TUESDAY to "T",
        DayOfWeek.WEDNESDAY to "W",
        DayOfWeek.THURSDAY to "T",
        DayOfWeek.FRIDAY to "F",
        DayOfWeek.SATURDAY to "S",
        DayOfWeek.SUNDAY to "S",
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
        modifier = modifier,
    ) {
        dayLabels.forEach { (day, label) ->
            val dayKey = day.name.take(3)
            val isSelected = dayKey in selectedDays

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(dotSize)
                    .background(
                        if (isSelected) colors.text else Color.Transparent,
                        CircleShape,
                    )
                    .border(
                        width = if (isSelected) 0.dp else 1.dp,
                        color = if (isSelected) Color.Transparent else colors.border,
                        shape = CircleShape,
                    ),
            ) {
                Text(
                    text = label,
                    style = typography.labelXSmall.copy(
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                    color = if (isSelected) colors.bg else colors.textDim,
                )
            }
        }
    }
}

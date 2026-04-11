package com.princeyadav.grayout.ui.screens

import android.app.TimePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.model.formatTime12Hour
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import java.time.DayOfWeek

@Composable
fun ScheduleEditorScreen(
    name: String,
    selectedDays: Set<DayOfWeek>,
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    overlapError: String?,
    isEditMode: Boolean,
    onNameChange: (String) -> Unit,
    onToggleDay: (DayOfWeek) -> Unit,
    onSetStartTime: (Int, Int) -> Unit,
    onSetEndTime: (Int, Int) -> Unit,
    onSelectPreset: (String) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPad),
    ) {
        Spacer(modifier = Modifier.height(dimens.sectionGap))

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .background(colors.surface, RoundedCornerShape(dimens.radiusFull))
                    .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusFull))
                    .clip(RoundedCornerShape(dimens.radiusFull))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onBack() },
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colors.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }

            Spacer(modifier = Modifier.width(dimens.cardGap))

            Text(
                text = if (isEditMode) "Edit Schedule" else "New Schedule",
                style = typography.headingSmall,
                color = colors.text,
            )
        }

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        // Name card
        NameCard(
            name = name,
            onNameChange = onNameChange,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Time card
        TimeCard(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            onStartTimeClick = {
                TimePickerDialog(
                    context,
                    android.R.style.Theme_Material_Dialog_Alert,
                    { _, h, m -> onSetStartTime(h, m) },
                    startHour,
                    startMinute,
                    false,
                ).show()
            },
            onEndTimeClick = {
                TimePickerDialog(
                    context,
                    android.R.style.Theme_Material_Dialog_Alert,
                    { _, h, m -> onSetEndTime(h, m) },
                    endHour,
                    endMinute,
                    false,
                ).show()
            },
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Days card
        DaysCard(
            selectedDays = selectedDays,
            onToggleDay = onToggleDay,
            onSelectPreset = onSelectPreset,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Overlap error
        if (overlapError != null) {
            Text(
                text = overlapError,
                style = typography.bodySmall,
                color = colors.danger,
            )

            Spacer(modifier = Modifier.height(dimens.cardGap))
        }

        // Save button
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.accent, RoundedCornerShape(dimens.radius))
                .clip(RoundedCornerShape(dimens.radius))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onSave() }
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = "Save Schedule",
                style = typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.bg,
            )
        }

        // Delete button (only in edit mode)
        if (isEditMode) {
            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Text(
                text = "Delete Schedule",
                style = typography.bodySmall,
                color = colors.danger,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDelete() },
            )
        }

        Spacer(modifier = Modifier.height(dimens.sectionGap))
    }
}

@Composable
private fun NameCard(
    name: String,
    onNameChange: (String) -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    var isFocused by remember { mutableStateOf(false) }

    GrayoutCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPad),
        ) {
            Text(
                text = "NAME",
                style = typography.labelSmall,
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(dimens.itemGap))

            BasicTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.accent),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                colors.bg,
                                RoundedCornerShape(dimens.radiusSm),
                            )
                            .border(
                                1.dp,
                                if (isFocused) colors.accent else colors.border,
                                RoundedCornerShape(dimens.radiusSm),
                            )
                            .padding(dimens.cardPad),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (name.isEmpty()) {
                            Text(
                                text = "Schedule name",
                                style = typography.bodyLarge,
                                color = colors.textMuted,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }
    }
}

@Composable
private fun TimeCard(
    startHour: Int,
    startMinute: Int,
    endHour: Int,
    endMinute: Int,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    GrayoutCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPad),
        ) {
            Text(
                text = "TIME RANGE",
                style = typography.labelSmall,
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "START",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatTime12Hour(startHour, startMinute),
                        style = typography.mono,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onStartTimeClick() },
                    )
                }

                Text(
                    text = "\u2192",
                    style = typography.headingMedium,
                    color = colors.textDim,
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "END",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = formatTime12Hour(endHour, endMinute),
                        style = typography.mono,
                        color = colors.accent,
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onEndTimeClick() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DaysCard(
    selectedDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    onSelectPreset: (String) -> Unit,
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

    val weekdays = setOf(
        DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
    )
    val weekends = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    val everyDay = DayOfWeek.entries.toSet()

    GrayoutCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPad),
        ) {
            Text(
                text = "REPEAT ON",
                style = typography.labelSmall,
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.dayDotGap),
            ) {
                dayLabels.forEach { (day, label) ->
                    val isSelected = day in selectedDays

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (isSelected) colors.accentDim else Color.Transparent,
                                CircleShape,
                            )
                            .border(
                                1.dp,
                                if (isSelected) colors.accent.copy(alpha = 0.33f) else colors.border,
                                CircleShape,
                            )
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { onToggleDay(day) },
                    ) {
                        Text(
                            text = label,
                            style = typography.labelXSmall,
                            color = if (isSelected) colors.accent else colors.textDim,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
            ) {
                val presets = listOf(
                    "Every day" to everyDay,
                    "Weekdays" to weekdays,
                    "Weekends" to weekends,
                )
                presets.forEach { (label, preset) ->
                    PresetChip(
                        label = label,
                        isActive = selectedDays == preset,
                        onClick = { onSelectPreset(label) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PresetChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val bg by animateColorAsState(
        targetValue = if (isActive) colors.accentDim else Color.Transparent,
        animationSpec = tween(300),
        label = "chipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) colors.accent else colors.textMuted,
        animationSpec = tween(300),
        label = "chipText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) colors.accent.copy(alpha = 0.33f) else colors.border,
        animationSpec = tween(300),
        label = "chipBorder",
    )

    Text(
        text = label,
        style = typography.bodySmall,
        color = textColor,
        modifier = Modifier
            .background(bg, RoundedCornerShape(dimens.radiusFull))
            .border(1.dp, borderColor, RoundedCornerShape(dimens.radiusFull))
            .clip(RoundedCornerShape(dimens.radiusFull))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() }
            .padding(horizontal = 14.dp, vertical = 6.dp),
    )
}

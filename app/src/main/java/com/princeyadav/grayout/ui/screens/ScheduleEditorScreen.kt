package com.princeyadav.grayout.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.logic.endsNextDay
import com.princeyadav.grayout.model.formatTime
import com.princeyadav.grayout.ui.rememberIs24HourFormat
import com.princeyadav.grayout.ui.components.DayLabels
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.SectionLabel
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.SchedulePermissionNotice
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

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
    onSelectPreset: (Set<DayOfWeek>) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    isSaving: Boolean = false,
    isReady: Boolean = true,
    isDeleted: Boolean = false,
    isPermissionGranted: Boolean = true,
    onSetupPermission: () -> Unit = {},
    onSaveAsDisabled: () -> Unit = {},
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val is24Hour = rememberIs24HourFormat()
    var showDeleteConfirm by remember { mutableStateOf(false) }
    // null = no picker open; true = start time; false = end time.
    var editingStart by rememberSaveable { mutableStateOf<Boolean?>(null) }
    val canDelete = isReady && !isLoading && !isSaving
    val isEditable = canDelete && !isDeleted

    // Keep the editor alive until persistence and alarm reconciliation finish.
    BackHandler(enabled = isSaving) {}

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
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .size(48.dp)
                    .background(colors.surface, RoundedCornerShape(dimens.radiusFull))
                    .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusFull))
                    .clip(RoundedCornerShape(dimens.radiusFull))
                    .clickable(
                        enabled = !isSaving,
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
                text = if (isEditMode) "Edit schedule" else "New schedule",
                style = typography.headingSmall,
                color = colors.text,
            )
        }

        Spacer(modifier = Modifier.height(dimens.sectionGap))

        if (!isPermissionGranted) {
            SchedulePermissionNotice(onSetup = onSetupPermission)
            Spacer(modifier = Modifier.height(dimens.cardGap))
        }

        // Name card
        NameCard(
            name = name,
            onNameChange = onNameChange,
            enabled = isEditable,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Time card
        TimeCard(
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            is24Hour = is24Hour,
            enabled = isEditable,
            onStartTimeClick = { editingStart = true },
            onEndTimeClick = { editingStart = false },
        )

        editingStart?.let { isStart ->
            TimePickerSheet(
                title = if (isStart) "Start time" else "End time",
                hour = if (isStart) startHour else endHour,
                minute = if (isStart) startMinute else endMinute,
                is24Hour = is24Hour,
                onDismiss = { editingStart = null },
                onSet = { h, m ->
                    if (isStart) onSetStartTime(h, m) else onSetEndTime(h, m)
                    editingStart = null
                },
            )
        }

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Days card
        DaysCard(
            selectedDays = selectedDays,
            onToggleDay = onToggleDay,
            onSelectPreset = onSelectPreset,
            enabled = isEditable,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        // Overlap error
        if (overlapError != null) {
            Text(
                text = overlapError,
                style = typography.bodyMedium,
                color = colors.danger,
            )

            Spacer(modifier = Modifier.height(dimens.cardGap))
        }

        // Save button
        val view = LocalView.current
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .sizeIn(minHeight = 48.dp)
                .background(if (isEditable) colors.text else colors.off, RoundedCornerShape(dimens.radius))
                .clip(RoundedCornerShape(dimens.radius))
                .clickable(
                    enabled = isEditable,
                    role = Role.Button,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    view.performHaptic(HapticAction.Commit)
                    onSave()
                }
                .padding(vertical = 16.dp),
        ) {
            Text(
                text = when {
                    isLoading -> "Loading..."
                    isSaving -> "Please wait..."
                    else -> "Save"
                },
                style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = if (isEditable) colors.bg else colors.offText,
            )
        }

        if (!isPermissionGranted) {
            TextButton(
                onClick = onSaveAsDisabled,
                enabled = isEditable,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Save as off", color = if (isEditable) colors.text else colors.offText)
            }
        }

        // Delete button (only in edit mode)
        if (isEditMode) {
            Spacer(modifier = Modifier.height(dimens.sectionGap))

            val deleteColor = if (canDelete) colors.danger else colors.offText
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .clickable(
                        enabled = canDelete,
                        role = Role.Button,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        view.performHaptic(HapticAction.Destructive)
                        showDeleteConfirm = true
                    }
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = deleteColor,
                    modifier = Modifier.size(dimens.iconSm),
                )
                Text(text = "Delete schedule", style = typography.bodyMedium, color = deleteColor)
            }
        }

        Spacer(modifier = Modifier.height(dimens.sectionGap))
    }

    if (showDeleteConfirm && canDelete) {
        DeleteConfirmSheet(
            scheduleName = name,
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}

@Composable
private fun NameCard(
    name: String,
    onNameChange: (String) -> Unit,
    enabled: Boolean,
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
            SectionLabel("NAME")

            Spacer(modifier = Modifier.height(dimens.tightGap))

            BasicTextField(
                value = name,
                enabled = enabled,
                onValueChange = onNameChange,
                singleLine = true,
                textStyle = typography.bodyLarge.copy(color = colors.text),
                cursorBrush = SolidColor(colors.text),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                colors.bg,
                                RoundedCornerShape(dimens.radiusSm),
                            )
                            .border(
                                width = if (isFocused) 2.dp else 1.dp,
                                color = if (isFocused) colors.text else colors.border,
                                shape = RoundedCornerShape(dimens.radiusSm),
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
    is24Hour: Boolean,
    onStartTimeClick: () -> Unit,
    onEndTimeClick: () -> Unit,
    enabled: Boolean,
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
            SectionLabel("TIME RANGE")

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            val start = formatTime(startHour, startMinute, is24Hour)
            val end = formatTime(endHour, endMinute, is24Hour)
            val overnight = endsNextDay(startHour, startMinute, endHour, endMinute)

            // Three rows sharing one grid: two equal weight(1f) columns around a minTouch-wide arrow
            // column, so START, the boxes and "Ends next day" line up and both boxes are the same width.
            Row(modifier = Modifier.fillMaxWidth()) {
                TimeLabel("START", Modifier.weight(1f))
                Spacer(modifier = Modifier.width(dimens.minTouch))
                TimeLabel("END", Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(dimens.microGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeBox(
                    time = start,
                    description = "Start time, $start",
                    enabled = enabled,
                    onClick = onStartTimeClick,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "\u2192",
                    style = typography.headingMedium,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(dimens.minTouch).clearAndSetSemantics {},
                )
                TimeBox(
                    time = end,
                    description = if (overnight) "End time, $end, next day" else "End time, $end",
                    enabled = enabled,
                    onClick = onEndTimeClick,
                    modifier = Modifier.weight(1f),
                )
            }

            if (overnight) {
                Spacer(modifier = Modifier.height(dimens.microGap))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(modifier = Modifier.weight(1f))
                    Spacer(modifier = Modifier.width(dimens.minTouch))
                    Text(
                        text = "Ends next day",
                        style = typography.bodyRegular,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clearAndSetSemantics {},
                    )
                }
            }
        }
    }
}

@Composable
private fun TimeLabel(text: String, modifier: Modifier) {
    Text(
        text = text,
        style = GrayoutTheme.typography.labelSmall,
        color = GrayoutTheme.colors.textMuted,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}

/** An outlined, tappable time. Fills its column, so the start and end boxes are always the same width. */
@Composable
private fun TimeBox(time: String, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens
    Text(
        text = time,
        style = GrayoutTheme.typography.mono,
        color = colors.text,
        textAlign = TextAlign.Center,
        modifier = modifier
            .semantics { contentDescription = description }
            .sizeIn(minHeight = dimens.minTouch)
            .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusSm))
            .clip(RoundedCornerShape(dimens.radiusSm))
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = dimens.cardGap, vertical = dimens.tightGap),
    )
}

@Composable
private fun DaysCard(
    selectedDays: Set<DayOfWeek>,
    onToggleDay: (DayOfWeek) -> Unit,
    onSelectPreset: (Set<DayOfWeek>) -> Unit,
    enabled: Boolean,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

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
            SectionLabel("REPEAT ON")

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            // One row of seven equal cells, so the week never wraps. Each cell is 1/7 of the row wide
            // (about 43dp on a 392dp phone) and minTouch tall; the cells tile the row, so every tap
            // lands on a day. The circle shrinks below DayCircleSize only if a cell gets narrower.
            Row(modifier = Modifier.fillMaxWidth()) {
                val view = LocalView.current
                DayLabels.forEach { (day, label) ->
                    val isSelected = day in selectedDays
                    val dayName = day.getDisplayName(TextStyle.FULL, Locale.getDefault())

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(dimens.minTouch)
                            .toggleable(
                                value = isSelected,
                                enabled = enabled,
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Checkbox,
                            ) {
                                view.performHaptic(HapticAction.Toggle)
                                onToggleDay(day)
                            }
                            .semantics { contentDescription = dayName },
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .widthIn(max = DayCircleSize)
                                .fillMaxWidth()
                                .aspectRatio(1f)
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
                                color = if (isSelected) colors.bg else colors.textMuted,
                                modifier = Modifier.clearAndSetSemantics {},
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
                verticalArrangement = Arrangement.spacedBy(dimens.tightGap),
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
                        onClick = { onSelectPreset(preset) },
                        enabled = enabled,
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
    enabled: Boolean,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    val bg by animateColorAsState(
        targetValue = if (isActive) colors.text else Color.Transparent,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "chipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) colors.bg else colors.textMuted,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "chipText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Color.Transparent else colors.border,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "chipBorder",
    )

    Box(
        modifier = Modifier
            .heightIn(min = 48.dp)
            .background(bg, RoundedCornerShape(dimens.radiusFull))
            .border(1.dp, borderColor, RoundedCornerShape(dimens.radiusFull))
            .clip(RoundedCornerShape(dimens.radiusFull))
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) {
                view.performHaptic(HapticAction.Toggle)
                onClick()
            }
            .semantics {
                role = Role.RadioButton
                selected = isActive
            }
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.bodyMedium.copy(
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            ),
            color = textColor,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteConfirmSheet(
    scheduleName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val label = scheduleName.ifBlank { "this schedule" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .size(width = 32.dp, height = 4.dp)
                    .background(colors.borderActive, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.cardPadLarge)
                .padding(bottom = dimens.sectionGap),
        ) {
            Text(
                text = "Delete schedule?",
                style = typography.headingSmall,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Text(
                text = "“$label” will be removed. This can't be undone.",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .background(colors.danger, RoundedCornerShape(dimens.radius))
                    .clip(RoundedCornerShape(dimens.radius))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onConfirm() }
                    .semantics { role = Role.Button }
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "Delete",
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.bg,
                )
            }

            Spacer(modifier = Modifier.height(dimens.cardGap))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .sizeIn(minHeight = 48.dp)
                    .border(1.dp, colors.border, RoundedCornerShape(dimens.radius))
                    .clip(RoundedCornerShape(dimens.radius))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onDismiss() }
                    .semantics { role = Role.Button }
                    .padding(vertical = 16.dp),
            ) {
                Text(
                    text = "Cancel",
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.text,
                )
            }
        }
    }
}

/** Themed Material 3 time picker in a bottom sheet, replacing the platform dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    title: String,
    hour: Int,
    minute: Int,
    is24Hour: Boolean,
    onDismiss: () -> Unit,
    onSet: (Int, Int) -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val state = rememberTimePickerState(initialHour = hour, initialMinute = minute, is24Hour = is24Hour)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surface,
        contentColor = colors.text,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.cardPadLarge)
                .padding(bottom = dimens.sectionGap),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = typography.headingSmall,
                color = colors.text,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(dimens.sectionGap))
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = colors.bg,
                    clockDialSelectedContentColor = colors.bg,
                    clockDialUnselectedContentColor = colors.text,
                    selectorColor = colors.accent,
                    containerColor = colors.surface,
                    periodSelectorBorderColor = colors.border,
                    periodSelectorSelectedContainerColor = colors.accent,
                    periodSelectorUnselectedContainerColor = colors.surface,
                    periodSelectorSelectedContentColor = colors.bg,
                    periodSelectorUnselectedContentColor = colors.textMuted,
                    timeSelectorSelectedContainerColor = colors.accent,
                    timeSelectorUnselectedContainerColor = colors.bg,
                    timeSelectorSelectedContentColor = colors.bg,
                    timeSelectorUnselectedContentColor = colors.text,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimens.tightGap, Alignment.End),
            ) {
                SheetButton(label = "Cancel", onClick = onDismiss)
                SheetButton(label = "Set", primary = true, onClick = { onSet(state.hour, state.minute) })
            }
        }
    }
}

@Composable
private fun SheetButton(label: String, primary: Boolean = false, onClick: () -> Unit) {
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

/** Largest size of the visible day circle inside its day cell. */
private val DayCircleSize = 40.dp

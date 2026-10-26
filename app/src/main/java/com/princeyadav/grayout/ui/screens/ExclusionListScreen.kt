package com.princeyadav.grayout.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.model.AppCategory
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.performHaptic
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import com.princeyadav.grayout.viewmodel.BulkExclusion

@Composable
fun ExclusionListScreen(
    excludedApps: List<AppInfo>,
    allOtherApps: List<AppInfo>,
    categoryApps: List<AppInfo>,
    categoryCounts: Map<AppCategory, Int>,
    selectedCategory: AppCategory?,
    pendingUndo: BulkExclusion?,
    searchQuery: String,
    isUsageAccessGranted: Boolean,
    onToggle: (String) -> Unit,
    onSelectCategory: (AppCategory?) -> Unit,
    onExcludeAll: () -> Unit,
    onUndo: (BulkExclusion) -> Unit,
    onUndoDismissed: (BulkExclusion) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(pendingUndo) {
        val op = pendingUndo ?: return@LaunchedEffect
        val count = op.packages.size
        val result = snackbarHostState.showSnackbar(
            message = if (count == 1) "1 app excluded" else "$count apps excluded",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short,
        )
        // Pass the captured op: the view model ignores it if a newer Exclude all replaced it.
        if (result == SnackbarResult.ActionPerformed) onUndo(op) else onUndoDismissed(op)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = dimens.screenPad),
        ) {
            item {
                Spacer(modifier = Modifier.height(dimens.sectionGap))

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
                        text = "Excluded Apps",
                        style = typography.headingMedium,
                        color = colors.text,
                    )
                }

                Spacer(modifier = Modifier.height(dimens.sectionGap))
            }

            if (!isUsageAccessGranted) {
                item {
                    GrayoutCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    colors.danger.copy(alpha = 0.1f),
                                    RoundedCornerShape(dimens.radius),
                                )
                                .border(
                                    1.dp,
                                    colors.danger.copy(alpha = 0.33f),
                                    RoundedCornerShape(dimens.radius),
                                )
                                .padding(dimens.cardPad),
                        ) {
                            Text(
                                text = "Usage access required",
                                style = typography.bodyMedium,
                                color = colors.danger,
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Grant Usage access so Grayout can tell which app is open and pause grayscale for excluded apps",
                                style = typography.bodyMedium,
                                color = colors.textMuted,
                            )

                            Spacer(modifier = Modifier.height(dimens.tightGap))

                            Text(
                                text = "Open Settings",
                                style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                                color = colors.text,
                                modifier = Modifier
                                    .sizeIn(minHeight = 48.dp)
                                    .clickable { onOpenUsageAccessSettings() }
                                    .padding(vertical = 12.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(dimens.sectionGap))
                }
            }

            item {
                var isFocused by remember { mutableStateOf(false) }

                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
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
                                .padding(horizontal = dimens.cardPad),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.heightIn(min = 48.dp),
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search apps...",
                                            style = typography.bodyLarge,
                                            color = colors.textMuted,
                                        )
                                    }
                                    innerTextField()
                                }

                                if (searchQuery.isNotEmpty()) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null,
                                            ) { onSearchQueryChange("") },
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear search",
                                            tint = colors.textMuted,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isFocused = it.isFocused },
                )

                Spacer(modifier = Modifier.height(dimens.sectionGap))
            }

            if (categoryCounts.isNotEmpty()) {
                item(key = "category_chips") {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .selectableGroup(),
                    ) {
                        CategoryChip(
                            label = "All",
                            count = null,
                            isActive = selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                        )
                        categoryCounts.forEach { (category, count) ->
                            CategoryChip(
                                label = category.label,
                                count = count,
                                isActive = selectedCategory == category,
                                onClick = { onSelectCategory(category) },
                            )
                        }
                    }
                }
            }

            val noMatches = if (selectedCategory != null) {
                categoryApps.isEmpty()
            } else {
                excludedApps.isEmpty() && allOtherApps.isEmpty()
            }
            if (noMatches && searchQuery.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(dimens.sectionGap))
                    Text(
                        text = "No apps match \"$searchQuery\"",
                        style = typography.bodyMedium,
                        color = colors.textMuted,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = dimens.cardPad, vertical = dimens.cardPad),
                    )
                }
            } else if (selectedCategory != null) {
                item(key = "header_category") {
                    SectionHeader(
                        text = "${selectedCategory.label.uppercase()} · ${categoryApps.size}",
                        dimens = dimens,
                        typography = typography,
                    ) {
                        ExcludeAllPill(
                            categoryLabel = selectedCategory.label,
                            allExcluded = categoryApps.all { it.isExcluded },
                            onClick = {
                                // Close the keyboard so the Undo snackbar is not hidden behind it.
                                focusManager.clearFocus()
                                onExcludeAll()
                            },
                        )
                    }
                }

                items(categoryApps, key = { "cat_${it.packageName}" }) { app ->
                    AppRow(app = app, onToggle = onToggle, dimens = dimens, typography = typography, textColor = colors.text)
                }

                item(key = "category_hint") {
                    // Search is ANDed with the chip, so an untagged app is only reachable from All.
                    Text(
                        text = "Missing an app? Find it under All.",
                        style = typography.labelSmall,
                        color = colors.textMuted,
                        modifier = Modifier.padding(top = dimens.tightGap),
                    )
                }
            } else {
                if (excludedApps.isNotEmpty()) {
                    item(key = "header_excluded") {
                        SectionHeader(
                            text = "EXCLUDED · ${excludedApps.size}",
                            dimens = dimens,
                            typography = typography,
                        )
                    }

                    items(excludedApps, key = { "ex_${it.packageName}" }) { app ->
                        AppRow(app = app, onToggle = onToggle, dimens = dimens, typography = typography, textColor = colors.text)
                    }
                }

                if (allOtherApps.isNotEmpty()) {
                    item(key = "header_all") {
                        SectionHeader(
                            text = "ALL APPS · ${allOtherApps.size}",
                            dimens = dimens,
                            typography = typography,
                        )
                    }

                    items(allOtherApps, key = { "all_${it.packageName}" }) { app ->
                        AppRow(app = app, onToggle = onToggle, dimens = dimens, typography = typography, textColor = colors.text)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(dimens.sectionGap))
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = dimens.screenPad, vertical = dimens.sectionGap),
        ) { data -> UndoSnackbar(data) }
    }
}

@Composable
private fun SectionHeader(
    text: String,
    dimens: com.princeyadav.grayout.ui.theme.GrayoutDimens,
    typography: com.princeyadav.grayout.ui.theme.GrayoutTypography,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.sectionGap, bottom = dimens.tightGap),
    ) {
        Text(
            text = text,
            style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = BrandAccent.copy(alpha = 0.75f),
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

/**
 * Wrap-content filter chip. Unlike the home screen's EnforcementChip, the painted pill is
 * smaller than the 48dp touch target, matching the design's compact chip row.
 */
@Composable
private fun CategoryChip(
    label: String,
    count: Int?,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current
    val animation = tween<Color>(durationMillis = GrayoutMotion.Fast, easing = GrayoutMotion.Easing)

    val bg by animateColorAsState(
        targetValue = if (isActive) colors.text else Color.Transparent,
        animationSpec = animation,
        label = "categoryChipBg",
    )
    val textColor by animateColorAsState(
        targetValue = if (isActive) colors.bg else colors.textMuted,
        animationSpec = animation,
        label = "categoryChipText",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) Color.Transparent else colors.border,
        animationSpec = animation,
        label = "categoryChipBorder",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = isActive,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.RadioButton,
            ) {
                view.performHaptic(HapticAction.Toggle)
                onClick()
            },
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
            modifier = Modifier
                .background(bg, RoundedCornerShape(dimens.radiusFull))
                .border(1.dp, borderColor, RoundedCornerShape(dimens.radiusFull))
                .padding(horizontal = dimens.cardGap, vertical = dimens.tightGap),
        ) {
            Text(
                text = label,
                style = typography.bodyMedium.copy(
                    fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                ),
                color = textColor,
            )
            if (count != null) {
                Text(
                    text = count.toString(),
                    style = typography.labelXSmall,
                    color = if (isActive) colors.bg else colors.textMuted,
                )
            }
        }
    }
}

@Composable
private fun ExcludeAllPill(
    categoryLabel: String,
    allExcluded: Boolean,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !allExcluded,
                onClickLabel = "Exclude all $categoryLabel apps",
                role = Role.Button,
            ) {
                view.performHaptic(HapticAction.Commit)
                onClick()
            },
    ) {
        Text(
            text = if (allExcluded) "All excluded" else "Exclude all",
            style = typography.labelSmall.copy(
                fontWeight = if (allExcluded) FontWeight.SemiBold else FontWeight.ExtraBold,
            ),
            color = if (allExcluded) colors.textMuted else colors.text,
            modifier = Modifier
                .border(
                    1.dp,
                    if (allExcluded) colors.border else colors.borderActive,
                    RoundedCornerShape(dimens.radiusFull),
                )
                .padding(horizontal = dimens.cardGap, vertical = dimens.tightGap),
        )
    }
}

@Composable
private fun UndoSnackbar(data: SnackbarData) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, RoundedCornerShape(dimens.radiusSm))
            .border(1.dp, colors.borderActive, RoundedCornerShape(dimens.radiusSm))
            .semantics { liveRegion = LiveRegionMode.Polite }
            .padding(start = dimens.cardPad),
    ) {
        Text(
            text = data.visuals.message,
            style = typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
        data.visuals.actionLabel?.let { action ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    .clickable(role = Role.Button) { data.performAction() }
                    .padding(horizontal = dimens.cardPad),
            ) {
                Text(
                    text = action,
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.text,
                )
            }
        }
    }
}

@Composable
private fun AppRow(
    app: AppInfo,
    onToggle: (String) -> Unit,
    dimens: com.princeyadav.grayout.ui.theme.GrayoutDimens,
    typography: com.princeyadav.grayout.ui.theme.GrayoutTypography,
    textColor: androidx.compose.ui.graphics.Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            bitmap = app.icon,
            contentDescription = app.appName,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(dimens.radiusSm)),
        )

        Spacer(modifier = Modifier.width(dimens.cardGap))

        Text(
            text = app.appName,
            style = typography.bodyMedium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )

        Spacer(modifier = Modifier.width(dimens.cardGap))

        GrayoutToggle(
            checked = app.isExcluded,
            onCheckedChange = { onToggle(app.packageName) },
            contentDescription = "Exclude ${app.appName}",
        )
    }
}

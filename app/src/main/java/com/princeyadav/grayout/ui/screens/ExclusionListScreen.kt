package com.princeyadav.grayout.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun ExclusionListScreen(
    excludedApps: List<AppInfo>,
    allOtherApps: List<AppInfo>,
    searchQuery: String,
    isAccessibilityEnabled: Boolean,
    onToggle: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    onRefresh: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnRefresh by rememberUpdatedState(onRefresh)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentOnRefresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bg)
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

        if (!isAccessibilityEnabled) {
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
                            text = "Accessibility Service Required",
                            style = typography.bodyMedium,
                            color = colors.danger,
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "Enable the Grayout accessibility service for app exclusions to work",
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
                                .clickable { onOpenAccessibilitySettings() }
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

        if (excludedApps.isEmpty() && allOtherApps.isEmpty() && searchQuery.isNotEmpty()) {
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
}

@Composable
private fun SectionHeader(
    text: String,
    dimens: com.princeyadav.grayout.ui.theme.GrayoutDimens,
    typography: com.princeyadav.grayout.ui.theme.GrayoutTypography,
) {
    Text(
        text = text,
        style = typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
        color = BrandAccent.copy(alpha = 0.75f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimens.sectionGap, bottom = dimens.tightGap),
    )
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
        )
    }
}

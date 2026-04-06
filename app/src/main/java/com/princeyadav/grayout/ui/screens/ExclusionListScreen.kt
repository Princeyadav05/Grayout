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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.model.AppInfo
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun ExclusionListScreen(
    apps: List<AppInfo>,
    searchQuery: String,
    isAccessibilityEnabled: Boolean,
    onToggle: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
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
                    text = "Excluded Apps",
                    style = typography.headingMedium,
                    color = colors.text,
                )
            }

            Spacer(modifier = Modifier.height(dimens.itemGap))

            Text(
                text = "These apps will bypass grayscale",
                style = typography.bodySmall,
                color = colors.textMuted,
            )

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
                            style = typography.bodySmall,
                            color = colors.textMuted,
                        )

                        Spacer(modifier = Modifier.height(dimens.itemGap))

                        Text(
                            text = "Open Settings",
                            style = typography.bodySmall,
                            color = colors.accent,
                            modifier = Modifier.clickable { onOpenAccessibilitySettings() },
                        )
                    }
                }

                Spacer(modifier = Modifier.height(dimens.sectionGap))
            }
        }

        item {
            var isFocused by remember { mutableStateOf(false) }

            GrayoutCard {
                BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
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
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search apps...",
                                    style = typography.bodyLarge,
                                    color = colors.textMuted,
                                )
                            }
                            innerTextField()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(dimens.cardPad)
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))
        }

        items(apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = dimens.itemGap),
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = typography.bodyMedium,
                        color = colors.text,
                    )
                    Text(
                        text = app.packageName,
                        style = typography.labelSmall,
                        color = colors.textMuted,
                    )
                }

                Spacer(modifier = Modifier.width(dimens.cardGap))

                GrayoutToggle(
                    checked = app.isExcluded,
                    onCheckedChange = { onToggle(app.packageName) },
                )
            }

            HorizontalDivider(color = colors.border)
        }

        item {
            Spacer(modifier = Modifier.height(dimens.sectionGap))
        }
    }
}

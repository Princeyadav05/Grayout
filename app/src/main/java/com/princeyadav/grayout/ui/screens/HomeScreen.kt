package com.princeyadav.grayout.ui.screens

import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
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
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme

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
    nextScheduleText: String,
    excludedAppIcons: List<Bitmap>,
    excludedOverflowCount: Int,
    needsAttentionCount: Int,
    onAttentionClick: () -> Unit,
    isPermissionGranted: Boolean,
    toggleFailedTarget: Boolean?,
    onShowSetup: () -> Unit,
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

        if (needsAttentionCount > 0) {
            NeedsAttentionStrip(
                count = needsAttentionCount,
                onClick = onAttentionClick,
            )
            Spacer(modifier = Modifier.height(dimens.cardGap))
        }

        if (isPermissionGranted) {
            StatusHeroCard(
                isGrayscaleOn = isGrayscaleOn,
                enforcementInterval = enforcementInterval,
                nextScheduleText = nextScheduleText,
                toggleFailedTarget = toggleFailedTarget,
                onToggle = onToggle,
                onNextScheduleClick = onNavigateToSchedules,
            )
        } else {
            SetupCard(onShowSetup = onShowSetup)
        }

        Spacer(modifier = Modifier.height(dimens.cardGap))

        EnforcementCard(
            enforcementInterval = enforcementInterval,
            onIntervalChange = onEnforcementIntervalChange,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        ExcludedAppsCard(
            count = excludedAppCount,
            isUsageAccessGranted = isUsageAccessGranted,
            excludedAppIcons = excludedAppIcons,
            excludedOverflowCount = excludedOverflowCount,
            onClick = onNavigateToExclusions,
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
private fun NeedsAttentionStrip(
    count: Int,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val message = buildString {
        append(count)
        append(if (count == 1) " thing needs attention" else " things need attention")
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(dimens.radius))
            .background(colors.danger.copy(alpha = 0.08f), RoundedCornerShape(dimens.radius))
            .border(1.dp, colors.danger.copy(alpha = 0.33f), RoundedCornerShape(dimens.radius))
            .clickable { onClick() }
            .padding(horizontal = dimens.cardPad, vertical = dimens.cardPad),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(colors.danger, CircleShape),
            )
            Spacer(modifier = Modifier.size(dimens.tightGap))
            Text(
                text = message,
                style = typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "›",
                style = typography.headingMedium,
                color = colors.textMuted,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
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
            Text(
                text = "SETUP",
                style = typography.labelSmall,
                color = BrandAccent.copy(alpha = 0.75f),
            )
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
            Box(
                modifier = Modifier
                    .heightIn(min = dimens.minTouch)
                    .clip(RoundedCornerShape(dimens.radiusFull))
                    .background(colors.text, RoundedCornerShape(dimens.radiusFull))
                    .clickable(role = Role.Button, onClick = onShowSetup)
                    .padding(horizontal = dimens.sectionGap),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Show the command",
                    style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                    color = colors.bg,
                )
            }
        }
    }
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
        Box(
            modifier = Modifier
                .heightIn(min = dimens.minTouch)
                .clickable(role = Role.Button, onClick = onRetry),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Try again",
                style = typography.bodyMedium.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.text,
                textDecoration = TextDecoration.Underline,
            )
        }
    }
}

@Composable
private fun StatusHeroCard(
    isGrayscaleOn: Boolean,
    enforcementInterval: Int,
    nextScheduleText: String,
    toggleFailedTarget: Boolean?,
    onToggle: () -> Unit,
    onNextScheduleClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    GrayoutCard(isActive = isGrayscaleOn, wash = true) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(
                            if (isGrayscaleOn) colors.text else BrandAccent,
                            CircleShape,
                        ),
                )
                Text(
                    text = "STATUS",
                    style = typography.labelSmall,
                    color = BrandAccent.copy(alpha = 0.75f),
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isGrayscaleOn) "Grayscale on" else "Grayscale off",
                style = typography.headingLarge,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Text(
                text = if (isGrayscaleOn) "Your screen is muted" else "Tap to mute your screen",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
            ) {
                if (enforcementInterval > 0) {
                    Text(
                        text = "${enforcementInterval}m",
                        style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = colors.bg,
                        modifier = Modifier
                            .background(
                                colors.text,
                                RoundedCornerShape(dimens.radiusFull),
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                Text(
                    text = nextScheduleText,
                    style = typography.monoSmall,
                    color = colors.text,
                    modifier = Modifier
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onNextScheduleClick() },
                )
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            HorizontalDivider(
                thickness = 1.dp,
                color = colors.border,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "Grayscale",
                    style = typography.bodyMedium,
                    color = colors.text,
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
        }
    }
}

@Composable
private fun EnforcementCard(
    enforcementInterval: Int,
    onIntervalChange: (Int) -> Unit,
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
                text = "ENFORCEMENT",
                style = typography.labelSmall,
                color = BrandAccent.copy(alpha = 0.75f),
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Re-applies grayscale on a timer, even if you turn it off",
                style = typography.labelSmall,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                EnforcementPrefs.INTERVAL_OPTIONS.forEach { value ->
                    EnforcementChip(
                        label = if (value == 0) "Off" else "${value}m",
                        isActive = enforcementInterval == value,
                        onClick = { onIntervalChange(value) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun EnforcementChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
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
        modifier = modifier
            .heightIn(min = 48.dp)
            .background(bg, RoundedCornerShape(dimens.radiusFull))
            .border(1.dp, borderColor, RoundedCornerShape(dimens.radiusFull))
            .clip(RoundedCornerShape(dimens.radiusFull))
            .clickable(
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
            .padding(horizontal = 2.dp),
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

@Composable
private fun ExcludedAppsCard(
    count: Int,
    isUsageAccessGranted: Boolean,
    excludedAppIcons: List<Bitmap>,
    excludedOverflowCount: Int,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val subtitleText: String
    val subtitleColor: androidx.compose.ui.graphics.Color

    if (!isUsageAccessGranted && count > 0) {
        subtitleText = "Setup required"
        subtitleColor = colors.danger
    } else if (count > 0) {
        subtitleText = "$count app${if (count != 1) "s" else ""} excluded"
        subtitleColor = colors.text
    } else {
        subtitleText = "Every app is muted"
        subtitleColor = colors.textMuted
    }

    GrayoutCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "EXCLUSIONS",
                    style = typography.labelSmall,
                    color = BrandAccent.copy(alpha = 0.75f),
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitleText,
                    style = typography.bodyMedium,
                    color = subtitleColor,
                )

                if (excludedAppIcons.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(dimens.tightGap))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimens.tightGap),
                    ) {
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
                    }
                }
            }

            Text(
                text = "›",
                style = typography.headingMedium,
                color = colors.textDim,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}

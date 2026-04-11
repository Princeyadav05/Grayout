package com.princeyadav.grayout.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.R
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
    isAccessibilityEnabled: Boolean,
    onToggle: () -> Unit,
    onEnforcementIntervalChange: (Int) -> Unit,
    onNavigateToExclusions: () -> Unit,
    onNavigateToSchedules: () -> Unit = {},
    nextScheduleText: String = "Nothing scheduled",
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

        StatusHeroCard(
            isGrayscaleOn = isGrayscaleOn,
            enforcementInterval = enforcementInterval,
            nextScheduleText = nextScheduleText,
            onToggle = onToggle,
            onNextScheduleClick = onNavigateToSchedules,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        EnforcementCard(
            enforcementInterval = enforcementInterval,
            onIntervalChange = onEnforcementIntervalChange,
        )

        Spacer(modifier = Modifier.height(dimens.cardGap))

        ExcludedAppsCard(
            count = excludedAppCount,
            isAccessibilityEnabled = isAccessibilityEnabled,
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
        modifier = Modifier.padding(bottom = dimens.sectionGap),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(dimens.radiusSm))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            BrandAccent.copy(alpha = 0.15f),
                            BrandAccent.copy(alpha = 0.4f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_grayout_foreground),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                colorFilter = ColorFilter.tint(colors.text),
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = "Grayout",
            style = typography.headingSmall,
            color = colors.text,
        )
    }
}

@Composable
private fun StatusHeroCard(
    isGrayscaleOn: Boolean,
    enforcementInterval: Int,
    nextScheduleText: String,
    onToggle: () -> Unit,
    onNextScheduleClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    GrayoutCard(isActive = isGrayscaleOn) {
        Column(modifier = Modifier.fillMaxWidth().padding(dimens.cardPad)) {
            Text(
                text = "STATUS",
                style = typography.labelSmall,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = if (isGrayscaleOn) "Grayscale on" else "Grayscale off",
                style = typography.headingLarge,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.itemGap))

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
                val pillLabel = if (enforcementInterval > 0) "${enforcementInterval}m" else "Off"
                Text(
                    text = pillLabel,
                    style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (enforcementInterval > 0) colors.bg else colors.offText,
                    modifier = Modifier
                        .background(
                            if (enforcementInterval > 0) colors.text else Color.Transparent,
                            RoundedCornerShape(dimens.radiusFull),
                        )
                        .border(
                            1.dp,
                            if (enforcementInterval > 0) Color.Transparent else colors.border,
                            RoundedCornerShape(dimens.radiusFull),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )

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
                )
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
                color = colors.textMuted,
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
                val options = listOf(
                    0 to "Off",
                    1 to "1m",
                    5 to "5m",
                    10 to "10m",
                    15 to "15m",
                    30 to "30m",
                )
                options.forEach { (value, label) ->
                    EnforcementChip(
                        label = label,
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
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.bodySmall.copy(
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            ),
            color = textColor,
        )
    }
}

@Composable
private fun ExcludedAppsCard(
    count: Int,
    isAccessibilityEnabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    val subtitleText: String
    val subtitleColor: androidx.compose.ui.graphics.Color

    if (!isAccessibilityEnabled && count > 0) {
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
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitleText,
                    style = typography.bodyMedium,
                    color = subtitleColor,
                )
            }

            Text(
                text = "›",
                style = typography.headingMedium,
                color = colors.textDim,
            )
        }
    }
}

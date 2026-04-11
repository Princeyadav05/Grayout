package com.princeyadav.grayout.ui.screens

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.R
import com.princeyadav.grayout.ui.components.GrayoutCard
import com.princeyadav.grayout.ui.components.GrayoutToggle
import com.princeyadav.grayout.ui.components.HapticAction
import com.princeyadav.grayout.ui.components.StatusDot
import com.princeyadav.grayout.ui.components.performHaptic
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
    nextScheduleText: String = "—",
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = dimens.screenPad),
    ) {
        Spacer(modifier = Modifier.height(dimens.sectionGap))

        AppHeader()

        MainToggleCard(isGrayscaleOn = isGrayscaleOn, onToggle = onToggle)

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

        Spacer(modifier = Modifier.height(dimens.cardGap))

        StatCardsRow(isGrayscaleOn = isGrayscaleOn, nextScheduleText = nextScheduleText, onNextScheduleClick = onNavigateToSchedules)

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
                            colors.accentDim,
                            colors.accent.copy(alpha = 0.4f),
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
private fun MainToggleCard(
    isGrayscaleOn: Boolean,
    onToggle: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val view = LocalView.current

    val reduceMotion = rememberReduceMotion()

    val strokeColor by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.borderActive else colors.border,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Slow,
            easing = GrayoutMotion.Easing,
        ),
        label = "circleStroke",
    )
    val strokeWidth by animateDpAsState(
        targetValue = if (isGrayscaleOn) 2.dp else 1.dp,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Slow,
            easing = GrayoutMotion.Easing,
        ),
        label = "circleStrokeWidth",
    )
    val iconColor by animateColorAsState(
        targetValue = if (isGrayscaleOn) colors.text else colors.textDim,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Slow,
            easing = GrayoutMotion.Easing,
        ),
        label = "iconColor",
    )

    // Breathing pulse — runs only when active AND reduce-motion is off.
    val pulseAlpha: Float
    val pulseOffsetDp: Float
    if (isGrayscaleOn && !reduceMotion) {
        val transition = rememberInfiniteTransition(label = "breathPulse")
        val alpha by transition.animateFloat(
            initialValue = 0.18f,
            targetValue = 0.32f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = GrayoutMotion.BreathPeriodMs / 2,
                    easing = GrayoutMotion.Easing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )
        val offset by transition.animateFloat(
            initialValue = 8f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(
                    durationMillis = GrayoutMotion.BreathPeriodMs / 2,
                    easing = GrayoutMotion.Easing,
                ),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseOffset",
        )
        pulseAlpha = alpha
        pulseOffsetDp = offset
    } else if (isGrayscaleOn) {
        // Reduce-motion on: render mid-value static glow.
        pulseAlpha = 0.25f
        pulseOffsetDp = 11f
    } else {
        pulseAlpha = 0f
        pulseOffsetDp = 0f
    }

    GrayoutCard(isActive = isGrayscaleOn) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.cardPadLarge),
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .drawBehind {
                        if (pulseAlpha > 0f) {
                            val extraPx = pulseOffsetDp.dp.toPx()
                            val r = size.minDimension / 2f + extraPx
                            drawCircle(
                                color = colors.text.copy(alpha = pulseAlpha),
                                radius = r,
                                center = center,
                                style = Stroke(width = 2.dp.toPx()),
                            )
                        }
                    }
                    .background(colors.surface, CircleShape)
                    .border(strokeWidth, strokeColor, CircleShape)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        view.performHaptic(HapticAction.Toggle)
                        onToggle()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_grayout_foreground),
                    contentDescription = "Toggle grayscale",
                    modifier = Modifier.size(64.dp),
                    colorFilter = ColorFilter.tint(iconColor),
                )
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Text(
                text = if (isGrayscaleOn) "On" else "Off",
                style = typography.headingLarge,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.itemGap))

            Text(
                text = if (isGrayscaleOn) "Your screen is muted"
                    else "Tap to mute your screen",
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RoundedCornerShape(dimens.radiusSm))
                    .padding(dimens.cardPad),
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
private fun rememberReduceMotion(): Boolean {
    val resolver = LocalContext.current.contentResolver
    return remember {
        Settings.Global.getFloat(
            resolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

@Composable
private fun StatCardsRow(isGrayscaleOn: Boolean, nextScheduleText: String, onNextScheduleClick: () -> Unit) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimens.cardGap),
        modifier = Modifier.fillMaxWidth(),
    ) {
        GrayoutCard(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.padding(dimens.cardPad)) {
                Text(
                    text = "STATUS",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(dimens.itemGap))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusDot(isActive = isGrayscaleOn)

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = if (isGrayscaleOn) "Active" else "Inactive",
                        style = typography.bodyMedium,
                        color = if (isGrayscaleOn) colors.success else colors.offText,
                    )
                }
            }
        }

        GrayoutCard(modifier = Modifier.weight(1f).clickable { onNextScheduleClick() }) {
            Column(modifier = Modifier.padding(dimens.cardPad)) {
                Text(
                    text = "NEXT SCHEDULE",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )

                Spacer(modifier = Modifier.height(dimens.itemGap))

                Text(
                    text = nextScheduleText,
                    style = typography.monoSmall,
                    color = colors.textDim,
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
                color = colors.accent,
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Turns grayscale back on periodically, even if you switch it off",
                style = typography.labelSmall,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(dimens.chipGap),
                verticalArrangement = Arrangement.spacedBy(dimens.chipGap),
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
        subtitleText = "No apps excluded"
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
                    text = "EXCLUDED APPS",
                    style = typography.labelSmall,
                    color = colors.accent,
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

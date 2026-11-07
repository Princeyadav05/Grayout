package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun GrayoutToggle(
    checked: Boolean,
    /** Null when a parent row is the toggleable control: the switch then only draws, so TalkBack reads one node. */
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens

    val trackColor by animateColorAsState(
        targetValue = if (checked) colors.accent else colors.off,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "trackColor",
    )
    val thumbColor by animateColorAsState(
        targetValue = if (checked) colors.bg else colors.textMuted,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "thumbColor",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) dimens.toggleWidth - dimens.toggleInset * 2 - dimens.toggleThumb else 0.dp,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "thumbOffset",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(width = dimens.toggleWidth, height = dimens.minTouch)
            .then(
                if (onCheckedChange == null) {
                    Modifier
                } else {
                    Modifier
                        .toggleable(
                            value = checked,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Switch,
                            onValueChange = onCheckedChange,
                        )
                        .semantics {
                            contentDescription?.let { this.contentDescription = it }
                            stateDescription = if (checked) "On" else "Off"
                        }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = dimens.toggleWidth, height = dimens.toggleHeight)
                .background(trackColor, RoundedCornerShape(percent = 50))
                .padding(dimens.toggleInset),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(dimens.toggleThumb)
                    .background(thumbColor, CircleShape),
            )
        }
    }
}

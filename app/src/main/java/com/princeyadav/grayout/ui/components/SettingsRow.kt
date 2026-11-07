package com.princeyadav.grayout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun SettingsRow(
    label: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens

    // The label and status share the top line; the subtitle sits under both and may use the full width.
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = dimens.minTouch)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = dimens.cardGap),
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = typography.bodyMedium,
                color = colors.text,
                modifier = Modifier.weight(1f),
            )
            if (trailing != null) {
                Spacer(modifier = Modifier.width(dimens.cardGap))
                trailing()
            }
            // Every row you can tap shows the chevron; rows you cannot tap show none.
            if (onClick != null) {
                Spacer(modifier = Modifier.width(dimens.microGap))
                Chevron()
            }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = typography.bodyRegular,
                color = colors.textMuted,
            )
        }
    }
}

/** Trailing "opens something" chevron. Decorative: the row's click label carries the meaning. */
@Composable
internal fun Chevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = GrayoutTheme.colors.textMuted,
    )
}

/**
 * A row's status word. Healthy is a plain word in `success`; a problem adds the warning icon and
 * uses `warning`. Never a tick: color backs up the word, it never replaces it.
 */
@Composable
fun StatusValue(text: String, isProblem: Boolean) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimens.microGap),
    ) {
        if (isProblem) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = colors.warning,
                modifier = Modifier.size(dimens.iconSm),
            )
        }
        Text(
            text = text,
            style = GrayoutTheme.typography.bodyMedium,
            color = if (isProblem) colors.warning else colors.success,
        )
    }
}

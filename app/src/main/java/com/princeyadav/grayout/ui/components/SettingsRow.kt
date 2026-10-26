package com.princeyadav.grayout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
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

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = dimens.tightGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = label,
                style = typography.bodyMedium,
                color = colors.text,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(dimens.cardGap))
            trailing()
        }
        if (onClick != null && subtitle != null) {
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "›",
                style = typography.bodyMedium,
                color = colors.textMuted,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
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
        horizontalArrangement = Arrangement.spacedBy(4.dp),
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

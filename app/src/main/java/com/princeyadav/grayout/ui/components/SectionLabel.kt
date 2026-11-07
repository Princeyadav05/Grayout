package com.princeyadav.grayout.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.princeyadav.grayout.ui.theme.GrayoutTheme

/** The one section label on every screen: uppercase `labelSmall` in opaque `accent` (7.7:1 on surface, 8.4:1 on bg). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = GrayoutTheme.typography.labelSmall,
        color = GrayoutTheme.colors.accent,
        modifier = modifier,
    )
}

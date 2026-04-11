package com.princeyadav.grayout.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.navigation.Routes
import com.princeyadav.grayout.ui.theme.GrayoutMotion
import com.princeyadav.grayout.ui.theme.GrayoutTheme

private data class NavTab(
    val route: String,
    val label: String,
    val iconFilled: ImageVector,
    val iconOutlined: ImageVector,
)

private val tabs = listOf(
    NavTab(Routes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab(Routes.SCHEDULES, "Schedules", Icons.Filled.DateRange, Icons.Outlined.DateRange),
    NavTab(Routes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings),
)

@Composable
fun BottomNavBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors

    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = colors.border)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bg)
                .padding(top = 12.dp, bottom = 28.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            tabs.forEach { tab ->
                NavTabItem(
                    tab = tab,
                    isActive = currentRoute == tab.route,
                    onClick = { onNavigate(tab.route) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavTabItem(
    tab: NavTab,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography

    val pillColor by animateColorAsState(
        targetValue = if (isActive) colors.text else Color.Transparent,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "pillColor",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isActive) colors.bg else colors.offText,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "iconTint",
    )
    val labelColor by animateColorAsState(
        targetValue = if (isActive) colors.text else colors.offText,
        animationSpec = tween(
            durationMillis = GrayoutMotion.Fast,
            easing = GrayoutMotion.Easing,
        ),
        label = "labelColor",
    )

    Column(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(30.dp)
                .background(pillColor, RoundedCornerShape(percent = 50)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isActive) tab.iconFilled else tab.iconOutlined,
                contentDescription = tab.label,
                tint = iconTint,
                modifier = Modifier.size(22.dp),
            )
        }

        Text(
            text = tab.label,
            style = typography.labelXSmall.copy(
                fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
            ),
            color = labelColor,
        )
    }
}

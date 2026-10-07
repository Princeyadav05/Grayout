package com.princeyadav.grayout.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.princeyadav.grayout.ui.theme.GrayoutTheme

@Composable
fun SchedulePermissionNotice(onSetup: () -> Unit) {
    val colors = GrayoutTheme.colors
    GrayoutCard(wash = true) {
        Column(Modifier.fillMaxWidth().padding(GrayoutTheme.dimens.cardPad)) {
            Text(
                text = "Schedules need grayscale permission to run. You can still edit or save them as off.",
                style = GrayoutTheme.typography.bodyMedium,
                color = colors.text,
            )
            TextButton(onClick = onSetup) {
                Text("Set up permission", color = colors.text)
            }
        }
    }
}

package com.princeyadav.grayout.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.ui.theme.BrandAccent
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import kotlinx.coroutines.delay

private const val ADB_COMMAND =
    "adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbCommandSheet(
    isGranted: Boolean,
    onDismiss: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) {
            delay(1200)
            copied = false
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surface,
        contentColor = colors.text,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp)
                    .width(32.dp)
                    .height(4.dp)
                    .background(colors.borderActive, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.cardPadLarge)
                .padding(bottom = dimens.sectionGap),
        ) {
            Text(
                text = "Grant via ADB",
                style = typography.headingSmall,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Text(
                text = if (isGranted) {
                    "Already granted. Re-run if it stops working."
                } else {
                    "One Android permission that can only be granted from a computer."
                },
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            StepRow(number = "1", text = "Enable USB debugging on your phone (Settings → Developer options).")
            Spacer(modifier = Modifier.height(dimens.tightGap))
            StepRow(number = "2", text = "Connect your phone to a computer with ADB installed.")
            Spacer(modifier = Modifier.height(dimens.tightGap))
            StepRow(number = "3", text = "Run the command below.")

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bg, RoundedCornerShape(dimens.radiusSm))
                    .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusSm))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", ADB_COMMAND))
                        view.performHaptic(HapticAction.Commit)
                        copied = true
                    }
                    .padding(dimens.cardPad),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        text = ADB_COMMAND,
                        style = typography.monoSmall,
                        color = colors.text,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp),
                    )
                    Text(
                        text = if (copied) "COPIED" else "COPY",
                        style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
                        color = if (copied) BrandAccent else colors.text,
                    )
                }
            }

            Spacer(modifier = Modifier.height(dimens.cardPad))

            Text(
                text = if (isGranted) {
                    "Permission granted"
                } else {
                    "Waiting for grant. Re-open this screen after running."
                },
                style = typography.labelSmall,
                color = if (isGranted) colors.text else colors.textMuted,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun StepRow(number: String, text: String) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .sizeIn(minWidth = 20.dp, minHeight = 20.dp)
                .background(colors.text, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = colors.bg,
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = typography.bodyMedium,
            color = colors.text,
            modifier = Modifier.weight(1f),
        )
    }
}

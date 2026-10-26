package com.princeyadav.grayout.ui.components

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.logic.SetupStepState
import com.princeyadav.grayout.logic.setupStepState
import com.princeyadav.grayout.logic.setupStepsDone
import com.princeyadav.grayout.ui.rememberAdbToggles
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import kotlinx.coroutines.delay

private const val ADB_COMMAND =
    "adb shell pm grant com.princeyadav.grayout android.permission.WRITE_SECURE_SETTINGS"

/**
 * One-time setup guide for the WRITE_SECURE_SETTINGS grant. Steps light up as the user turns on
 * developer options and USB debugging, and the sheet confirms the grant on its own.
 * [onTryGrayscale] adds a "Try grayscale" button once granted (Home passes it; other callers don't).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbCommandSheet(
    isGranted: Boolean,
    onDismiss: () -> Unit,
    onTryGrayscale: (() -> Unit)? = null,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val context = LocalContext.current
    val view = LocalView.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val toggles by rememberAdbToggles()
    val stepsDone = setupStepsDone(toggles.developerOptionsOn, toggles.usbDebuggingOn, isGranted)

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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = dimens.cardPadLarge)
                .padding(bottom = dimens.sectionGap),
        ) {
            Text(
                text = "One-time setup",
                style = typography.headingSmall,
                color = colors.text,
            )

            Spacer(modifier = Modifier.height(dimens.tightGap))

            Text(
                text = if (isGranted) {
                    "Already granted. Re-run the command if grayscale stops working."
                } else {
                    "Grayout needs one permission that only a computer can grant. It takes about 2 minutes, once."
                },
                style = typography.bodyMedium,
                color = colors.textMuted,
            )

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            SetupStep(
                number = 1,
                state = setupStepState(0, stepsDone),
                title = "Turn on Developer options",
                detail = "Settings › About phone › tap Build number 7 times.",
            ) {
                SheetAction("Open About phone") {
                    openSettings(context, Settings.ACTION_DEVICE_INFO_SETTINGS)
                }
            }
            SetupStep(
                number = 2,
                state = setupStepState(1, stepsDone),
                title = "Turn on USB debugging",
                detail = "In Developer options. Then plug into a computer with ADB and tap Allow.",
            ) {
                SheetAction("Open Developer options") {
                    openSettings(context, Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
                }
            }
            SetupStep(
                number = 3,
                state = setupStepState(2, stepsDone),
                title = "Run this on your computer",
                detail = null,
            ) {
                Text(
                    text = ADB_COMMAND,
                    style = typography.monoSmall,
                    color = colors.text,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bg, RoundedCornerShape(dimens.radiusSm))
                        .border(1.dp, colors.border, RoundedCornerShape(dimens.radiusSm))
                        .padding(dimens.cardPad),
                )
                Spacer(modifier = Modifier.height(dimens.tightGap))
                Row(horizontalArrangement = Arrangement.spacedBy(dimens.tightGap)) {
                    SheetAction(if (copied) "Copied" else "Copy", highlighted = copied) {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB command", ADB_COMMAND))
                        view.performHaptic(HapticAction.Commit)
                        copied = true
                    }
                    SheetAction("Share") {
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, ADB_COMMAND)
                        context.startActivity(Intent.createChooser(send, "Send the command to your computer"))
                    }
                }
            }

            Spacer(modifier = Modifier.height(dimens.sectionGap))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { liveRegion = LiveRegionMode.Polite },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isGranted) "Permission granted" else "Waiting for the grant. This updates on its own.",
                    style = typography.bodyMedium,
                    color = if (isGranted) colors.success else colors.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (isGranted && onTryGrayscale != null) {
                    SheetAction("Try grayscale", primary = true) {
                        onTryGrayscale()
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStep(
    number: Int,
    state: SetupStepState,
    title: String,
    detail: String?,
    content: @Composable () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = dimens.tightGap),
        verticalAlignment = Alignment.Top,
    ) {
        // Finished steps are a filled number, never a tick.
        val circle = when (state) {
            SetupStepState.Done -> Modifier.background(colors.text, CircleShape)
            SetupStepState.Current -> Modifier.border(1.dp, colors.accent, CircleShape)
            SetupStepState.Upcoming -> Modifier.border(1.dp, colors.border, CircleShape)
        }
        Box(
            modifier = Modifier.size(dimens.stepSize).then(circle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number.toString(),
                style = typography.labelXSmall.copy(fontWeight = FontWeight.ExtraBold),
                color = when (state) {
                    SetupStepState.Done -> colors.bg
                    SetupStepState.Current -> colors.accent
                    SetupStepState.Upcoming -> colors.textMuted
                },
            )
        }
        Spacer(modifier = Modifier.width(dimens.cardGap))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = typography.bodyMedium,
                color = if (state == SetupStepState.Upcoming) colors.textMuted else colors.text,
            )
            if (detail != null) {
                Text(text = detail, style = typography.labelSmall, color = colors.textMuted)
            }
            Spacer(modifier = Modifier.height(dimens.tightGap))
            content()
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    primary: Boolean = false,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = GrayoutTheme.colors
    val typography = GrayoutTheme.typography
    val dimens = GrayoutTheme.dimens
    val shape = RoundedCornerShape(dimens.radiusFull)
    Box(
        modifier = Modifier
            .heightIn(min = dimens.minTouch)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = typography.labelSmall.copy(fontWeight = FontWeight.ExtraBold),
            color = when {
                primary -> colors.bg
                highlighted -> colors.accent
                else -> colors.text
            },
            modifier = Modifier
                .then(
                    if (primary) Modifier.background(colors.text, shape)
                    else Modifier.border(1.dp, colors.borderActive, shape),
                )
                .padding(horizontal = dimens.cardGap, vertical = dimens.tightGap),
        )
    }
}

private fun openSettings(context: Context, action: String) {
    try {
        context.startActivity(Intent(action))
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

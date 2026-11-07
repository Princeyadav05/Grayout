package com.princeyadav.grayout.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.dp
import com.princeyadav.grayout.logic.TodayState
import com.princeyadav.grayout.logic.scheduleColorIndex
import com.princeyadav.grayout.logic.stripLanes
import com.princeyadav.grayout.ui.theme.GrayoutTheme
import kotlin.math.roundToInt

/**
 * A 24-hour strip of today's windows in schedule colors, a now marker, and axis labels. Shared by
 * Schedules and Home. Segments are inset by `stripGap`, so neighbours stay apart in grayscale, and
 * overlapping windows split the height into lanes ([stripLanes]) instead of covering each other.
 */
@Composable
internal fun TodayStrip(today: TodayState, is24Hour: Boolean, modifier: Modifier = Modifier) {
    val colors = GrayoutTheme.colors
    val dimens = GrayoutTheme.dimens
    val laned = remember(today.segments) { stripLanes(today.segments) }
    val shape = RoundedCornerShape(dimens.radiusSm)

    Column(modifier = modifier.fillMaxWidth()) {
        // "Now" sits above the marker, centered on it but clamped inside the strip near either edge.
        Layout(
            content = { Text(text = "Now", style = GrayoutTheme.typography.labelXSmall, color = colors.text) },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val label = measurables.first().measure(constraints.copy(minWidth = 0))
            val width = constraints.maxWidth
            layout(width, label.height) {
                val centered = (today.now * width - label.width / 2f).roundToInt()
                label.place(centered.coerceIn(0, (width - label.width).coerceAtLeast(0)), 0)
            }
        }
        Spacer(modifier = Modifier.height(dimens.microGap))
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(dimens.stripHeight)
                .clip(shape)
                .background(colors.bg)
                .border(1.dp, colors.border, shape),
        ) {
            val half = dimens.stripGap.toPx() / 2
            laned.forEach { (segment, lane, lanes) ->
                val laneHeight = size.height / lanes
                val left = segment.start * size.width + half
                val right = segment.end * size.width - half
                val top = lane * laneHeight + if (lane > 0) half else 0f
                val bottom = (lane + 1) * laneHeight - if (lane < lanes - 1) half else 0f
                drawRect(
                    color = colors.schedulePalette[scheduleColorIndex(segment.scheduleId)],
                    topLeft = Offset(left, top),
                    // A window shorter than the gap still shows as a hairline.
                    size = Size((right - left).coerceAtLeast(1f), bottom - top),
                )
            }
            val markerWidth = dimens.stripMarker.toPx()
            drawRect(
                color = colors.text,
                topLeft = Offset(today.now * size.width - markerWidth / 2, 0f),
                size = Size(markerWidth, size.height),
            )
        }
        Spacer(modifier = Modifier.height(dimens.microGap))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val marks = if (is24Hour) listOf("00", "06", "12", "18", "24") else listOf("12a", "6a", "12p", "6p", "12a")
            marks.forEach { Text(text = it, style = GrayoutTheme.typography.labelXSmall, color = colors.textMuted) }
        }
    }
}

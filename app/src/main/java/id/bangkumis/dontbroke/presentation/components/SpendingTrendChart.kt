package id.bangkumis.dontbroke.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.DailyTrend

/**
 * Which bar a tap landed on, or null if it fell outside the plot. Bars divide the
 * width evenly, so the slot index is the tap's share of the total width.
 */
internal fun barAt(tapX: Float, width: Float, bars: Int): Int? {
    if (bars <= 0 || width <= 0f) return null
    if (tapX < 0f || tapX >= width) return null
    return ((tapX / width) * bars).toInt().coerceIn(0, bars - 1)
}

/**
 * Axis ticks as (label, span) pairs. A frame leaves most ticks blank once the bars
 * outnumber the room for text — each blank run is folded into the label before it,
 * so "1" sits over the week it opens instead of being squeezed into one bar's width.
 */
internal fun axisTicks(frame: AnalyticsTimeFrame, from: Long, bars: Int): List<Pair<String, Int>> {
    val ticks = mutableListOf<Pair<String, Int>>()
    for (i in 0 until bars) {
        val label = frame.bucketLabel(i, from, bars)
        // a blank before any label at all still needs its width reserved
        if (label.isEmpty() && ticks.isNotEmpty()) {
            val (text, span) = ticks.removeAt(ticks.lastIndex)
            ticks.add(text to span + 1)
        } else {
            ticks.add(label to 1)
        }
    }
    return ticks
}

/**
 * Spend per bucket, scaled against [maxSpent]. Draws only — the selected bucket is
 * hoisted so the readout and the bar cannot disagree.
 *
 * A zero-spend bucket still gets a flat stub so it reads as a period with nothing
 * in it rather than as a gap in the axis.
 */
@Composable
fun SpendingTrendChart(
    buckets: List<DailyTrend>,
    maxSpent: Double,
    frame: AnalyticsTimeFrame,
    windowStart: Long,
    selected: Int,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 120.dp
) {
    val bars = buckets.size.coerceAtLeast(1)
    val amounts = List(bars) { buckets.getOrNull(it)?.totalSpent ?: 0.0 }
    val empty = maxSpent <= 0.0
    // guard the divide; an empty window draws stubs and never reaches it anyway
    val peak = maxSpent.coerceAtLeast(1.0)

    val current = selected.coerceIn(0, bars - 1)
    val today = currentIndex.coerceIn(-1, bars - 1)

    val selectedColor = MaterialTheme.colorScheme.primary
    val barColor = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    // animateFloatAsState starts *at* its target, so the flip has to happen after the
    // first composition or the bars appear full height instead of growing into it.
    // Keyed on the frame so switching timeframe replays the growth.
    var laidOut by remember(frame) { mutableStateOf(false) }
    LaunchedEffect(frame) { laidOut = true }
    val growth by animateFloatAsState(
        targetValue = if (laidOut && !empty) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "bars"
    )

    // a peak plus a total, not all N buckets read aloud one by one
    val spoken = if (empty) "No spending recorded ${frame.title.lowercase()}" else {
        val top = amounts.indices.maxByOrNull { amounts[it] } ?: 0
        "Spending ${frame.title.lowercase()}, total ${idr.format(amounts.sum())}, " +
            "highest ${frame.bucketName(top, windowStart)} at ${idr.format(amounts[top])}"
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (empty) "No spending recorded ${frame.title.lowercase()}"
            else "${frame.bucketName(current, windowStart)}: ${idr.format(amounts[current])}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (empty) muted else selectedColor
        )

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .semantics { contentDescription = spoken }
                .pointerInput(bars) {
                    detectTapGestures { tap ->
                        barAt(tap.x, size.width.toFloat(), bars)?.let(onSelect)
                    }
                }
        ) {
            val slot = size.width / bars
            // thin bars need most of their slot; a week's seven can afford the gap
            val width = slot * if (bars > 14) 0.8f else 0.55f
            val radius = CornerRadius(width / 4)

            amounts.forEachIndexed { i, amount ->
                // a flat stub keeps a spendless bucket visible as a period, not a gap
                val full =
                    if (amount > 0.0) (size.height * amount / peak).toFloat().coerceAtLeast(2f)
                    else 2f
                val barHeight = if (amount > 0.0) (full * growth).coerceAtLeast(2f) else 2f

                drawRoundRect(
                    color = when {
                        amount <= 0.0 -> track
                        i == current -> selectedColor
                        else -> barColor
                    },
                    topLeft = Offset(i * slot + (slot - width) / 2, size.height - barHeight),
                    size = Size(width, barHeight),
                    cornerRadius = radius
                )
            }
        }

        // the current period stays bold even after tapping elsewhere, so "now" is never lost
        Row(Modifier.fillMaxWidth()) {
            var index = 0
            axisTicks(frame, windowStart, bars).forEach { (label, span) ->
                val start = index
                val coversToday = today in start until start + span
                val coversSelection = current in start until start + span
                index += span
                Text(
                    label,
                    Modifier.weight(span.toFloat()),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = if (coversToday) FontWeight.Bold else FontWeight.Normal,
                    color = if (coversSelection) selectedColor else muted,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

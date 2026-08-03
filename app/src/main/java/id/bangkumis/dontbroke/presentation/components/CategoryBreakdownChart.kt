package id.bangkumis.dontbroke.presentation.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.domain.model.CategoryExpense
import java.util.Locale
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Which slice a tap landed on, or null if it missed the ring. [sweeps] are the
 * drawn sweeps in draw order, degrees, first one starting at 12 o'clock and
 * running clockwise — the same convention `drawArc` uses.
 */
internal fun sliceAt(
    tapX: Float,
    tapY: Float,
    centerX: Float,
    centerY: Float,
    innerRadius: Float,
    outerRadius: Float,
    sweeps: List<Float>
): Int? {
    if (sweeps.isEmpty()) return null
    val dx = tapX - centerX
    val dy = tapY - centerY
    val distance = hypot(dx, dy)
    if (distance < innerRadius || distance > outerRadius) return null

    // atan2 is 0 at 3 o'clock and grows clockwise on a y-down canvas; +90 puts 0 at 12.
    var degrees = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat() + 90f
    if (degrees < 0f) degrees += 360f

    var end = 0f
    sweeps.forEachIndexed { i, sweep ->
        end += sweep
        if (degrees < end) return i
    }
    // rounding can leave the last hair of the circle unclaimed
    return sweeps.lastIndex
}

/**
 * Spending as a ring, one slice per category. Tap a slice to read it in the
 * middle. Selection is hoisted — this draws what it is told.
 *
 * An empty [categories] draws the placeholder ring with [emptyMessage], so
 * `Empty` needs no separate composable.
 */
@Composable
fun CategoryBreakdownChart(
    categories: List<CategoryExpense>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No expenses recorded this month",
    diameter: Dp = 200.dp,
    ringWidth: Dp = 30.dp
) {
    val ringPx = with(LocalDensity.current) { ringWidth.toPx() }
    val popPx = with(LocalDensity.current) { 10.dp.toPx() }

    val colors = categoryColors(categories.size)
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = categories.sumOf { it.totalAmount }
    val sweeps =
        if (total <= 0.0) emptyList()
        else categories.map { (it.totalAmount / total * 360.0).toFloat() }

    val current = selected.coerceIn(0, (categories.size - 1).coerceAtLeast(0))
    // one animated cursor instead of one animation per slice: the leaving slice
    // sinks as the arriving one rises, and slice count can change freely
    val cursor by animateFloatAsState(
        targetValue = current.toFloat(),
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "slice"
    )

    val spoken =
        if (sweeps.isEmpty()) emptyMessage
        else categories.joinToString(", ") { "${it.categoryName} ${idr.format(it.totalAmount)} ${percent(it.percentage)}" }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Spending by category: $spoken" }
                .pointerInput(sweeps) {
                    detectTapGestures { tap ->
                        val outer = size.width.coerceAtMost(size.height) / 2f
                        sliceAt(
                            tapX = tap.x,
                            tapY = tap.y,
                            centerX = size.width / 2f,
                            centerY = size.height / 2f,
                            innerRadius = outer - popPx - ringPx,
                            outerRadius = outer,
                            sweeps = sweeps
                        )?.let(onSelect)
                    }
                }
        ) {
            val outer = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            if (sweeps.isEmpty()) {
                ring(center, outer - popPx - ringPx / 2f, ringPx, track, -90f, 360f)
                return@Canvas
            }

            // a hairline gap reads as separate slices without a second colour
            val gap = if (sweeps.size > 1) 1.5f else 0f
            var start = -90f
            sweeps.forEachIndexed { i, sweep ->
                val pop = (1f - abs(i - cursor)).coerceIn(0f, 1f)
                ring(
                    center = center,
                    radius = outer - ringPx / 2f - popPx * (1f - pop),
                    width = ringPx,
                    color = colors[i],
                    startAngle = start,
                    sweep = (sweep - gap).coerceAtLeast(0.5f)
                )
                start += sweep
            }
        }

        val pick = categories.getOrNull(current)
        Column(
            Modifier.padding(ringWidth + 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (pick == null) {
                Text(
                    emptyMessage,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    pick.categoryName,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    idr.format(pick.totalAmount),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    textAlign = TextAlign.Center
                )
                Text(
                    percent(pick.percentage),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun DrawScope.ring(
    center: Offset,
    radius: Float,
    width: Float,
    color: Color,
    startAngle: Float,
    sweep: Float
) = drawArc(
    color = color,
    startAngle = startAngle,
    sweepAngle = sweep,
    useCenter = false,
    topLeft = Offset(center.x - radius, center.y - radius),
    size = Size(radius * 2f, radius * 2f),
    style = Stroke(width = width)
)

/**
 * 18.9% rather than 19%, so a small slice never reads as nothing. Formatted id-ID
 * like every other number here — the default locale would disagree with the
 * currency beside it on the separator.
 */
internal fun percent(value: Float): String = String.format(Locale("id", "ID"), "%.1f%%", value)

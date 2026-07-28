package id.bangkumis.dontbroke.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class DonutSlice(val value: Float, val color: Color, val label: String)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    strokeWidth: Dp = 28.dp
) {
    val total = slices.sumOf { it.value.toDouble() }.toFloat().coerceAtLeast(1f)
    val stroke = Stroke(width = strokeWidth.value * 3)
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(modifier = modifier.size(size)) {
        val diameter = this.size.minDimension - stroke.width
        val topLeft = Offset(stroke.width / 2, stroke.width / 2)
        val arcSize = Size(diameter, diameter)

        if (slices.isEmpty() || total == 0f) {
            drawArc(emptyColor, 0f, 360f, false, topLeft, arcSize, style = stroke)
            return@Canvas
        }

        var startAngle = -90f
        slices.forEach { slice ->
            val sweep = (slice.value / total) * 360f
            drawArc(slice.color, startAngle, sweep, false, topLeft, arcSize, style = stroke)
            startAngle += sweep
        }
    }
}

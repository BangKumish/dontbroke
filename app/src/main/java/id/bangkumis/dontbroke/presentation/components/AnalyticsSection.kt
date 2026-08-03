package id.bangkumis.dontbroke.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.domain.model.AnalyticsRange
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.DailyTrend
import id.bangkumis.dontbroke.domain.model.dayStart
import id.bangkumis.dontbroke.domain.model.weekWindow
import id.bangkumis.dontbroke.presentation.addtransaction.fromPickerMillis
import id.bangkumis.dontbroke.presentation.home.CategorySpendingUiState
import id.bangkumis.dontbroke.presentation.home.SpendingTrendUiState
import java.text.NumberFormat
import java.util.Locale

private const val DAY_MS = 86_400_000L

// Rupiah is not written with cents. Shared with the chart files in this package.
internal val idr = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    .apply { maximumFractionDigits = 0 }

/**
 * The three home charts: where the money went, when it went, and whether more
 * came in than went out. Every figure arrives already aggregated — this file
 * only draws.
 *
 * One timeframe drives all three, so the cards can never disagree about which
 * period they are describing.
 */
@Composable
fun AnalyticsSection(
    range: AnalyticsRange,
    canStepForward: Boolean,
    onTimeFrameChange: (AnalyticsTimeFrame) -> Unit,
    onStep: (Int) -> Unit,
    onCustomRange: (Long, Long) -> Unit,
    categorySpending: CategorySpendingUiState,
    spendingTrend: SpendingTrendUiState,
    income: Long,
    expense: Long,
    modifier: Modifier = Modifier
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val timeFrame = range.frame

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TimeFrameChips(timeFrame) { frame ->
            if (frame == AnalyticsTimeFrame.CUSTOM) picking = true else onTimeFrameChange(frame)
        }
        RangeStepper(
            label = range.label,
            canStepBack = range.canStepBack,
            canStepForward = canStepForward,
            onStep = onStep,
            onPick = { picking = true }
        )
        CategoryBreakdown(categorySpending, timeFrame)
        SpendingTrend(spendingTrend)
        IncomeVsExpense(income, expense, timeFrame)
    }

    if (picking) {
        DateRangePickerDialog(
            onDismiss = { picking = false },
            onConfirm = { start, end -> picking = false; onCustomRange(start, end) }
        )
    }
}

/** One row, five frames — the whole analytics section's scope in a glance. */
@Composable
internal fun TimeFrameChips(
    selected: AnalyticsTimeFrame,
    onSelect: (AnalyticsTimeFrame) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            // five chips no longer divide a phone width without truncating "Custom",
            // so they keep their natural size and the row scrolls instead
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        AnalyticsTimeFrame.entries.forEach { frame ->
            FilterChip(
                selected = frame == selected,
                onClick = { onSelect(frame) },
                label = {
                    Text(
                        frame.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            )
        }
    }
}

/**
 * `<` label `>`. Back always works on a fixed frame; forward stops at the window
 * that contains now, because there is no spending in the future to show. A custom
 * range has no neighbours, so its arrows are off and the label re-opens the picker.
 */
@Composable
private fun RangeStepper(
    label: String,
    canStepBack: Boolean,
    canStepForward: Boolean,
    onStep: (Int) -> Unit,
    onPick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onStep(-1) }, enabled = canStepBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous period")
        }
        Text(
            label,
            Modifier
                .weight(1f)
                .clickable(onClick = onPick),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        IconButton(onClick = { onStep(1) }, enabled = canStepForward) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next period")
        }
    }
}

/**
 * Picks one date or a span of them. The picker speaks UTC-midnight millis while
 * the ledger stores local wall-clock, so both bounds cross back through
 * [fromPickerMillis] — the same conversion the add-transaction date field uses.
 *
 * Selecting only a start date is a valid answer: start == end inspects a single day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DateRangePickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val pickerState = rememberDateRangePickerState()
    val start = pickerState.selectedStartDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val s = pickerState.selectedStartDateMillis ?: return@TextButton
                    val e = pickerState.selectedEndDateMillis ?: s
                    onConfirm(s.fromPickerMillis(), e.fromPickerMillis())
                },
                enabled = start != null
            ) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    ) {
        RangePresets { s, e -> onConfirm(s, e) }
        DateRangePicker(state = pickerState, title = null)
    }
}

/**
 * The three ranges people actually ask for, applied straight away rather than
 * fed back into the calendar — one tap beats two.
 *
 * "Work Days" is this week's Monday through Friday, so it reads the same on a
 * Saturday as it does on a Tuesday.
 */
@Composable
private fun RangePresets(onPick: (Long, Long) -> Unit) {
    val now = System.currentTimeMillis()
    val monday = weekWindow(now).first
    val presets = listOf(
        "Work Days" to (monday to monday + 4 * DAY_MS),
        "Last 7 Days" to (dayStart(now) - 6 * DAY_MS to dayStart(now)),
        "Last 30 Days" to (dayStart(now) - 29 * DAY_MS to dayStart(now))
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        presets.forEach { (label, span) ->
            AssistChip(
                onClick = { onPick(span.first, span.second) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) }
            )
        }
    }
}

@Composable
private fun ChartCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/**
 * Shades one hue instead of introducing new ones, so the palette stays
 * monochromatic however many categories show up. The legend repeats every
 * label and percentage as text — colour is never the only channel.
 */
@Composable
internal fun categoryColors(count: Int): List<Color> {
    val from = MaterialTheme.colorScheme.primary
    val to = MaterialTheme.colorScheme.tertiary
    return List(count) { i ->
        lerp(from, to, if (count <= 1) 0f else i.toFloat() / (count - 1))
    }
}

/**
 * Selection lives here, not in the chart: the ring and the legend highlight the
 * same slice. 0 is the biggest spender — the SQL already sorts total DESC, and
 * the selection resets with the frame because the slices change under it.
 */
@Composable
private fun CategoryBreakdown(state: CategorySpendingUiState, frame: AnalyticsTimeFrame) {
    ChartCard("Spending by Category — ${frame.title}") {
        val categories = (state as? CategorySpendingUiState.Success)?.categories ?: emptyList()
        var selected by rememberSaveable(frame) { mutableIntStateOf(0) }

        if (state is CategorySpendingUiState.Loading) {
            EmptyNote("Adding it up…")
            return@ChartCard
        }
        CategoryBreakdownChart(
            categories = categories,
            selected = selected,
            onSelect = { selected = it },
            emptyMessage = "No expenses recorded ${frame.title.lowercase()}",
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        val colors = categoryColors(categories.size)
        categories.forEachIndexed { i, slice ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { selected = i },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("●", color = colors[i], style = MaterialTheme.typography.labelSmall)
                Text(
                    slice.categoryName,
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(percent(slice.percentage), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Text(
                    idr.format(slice.totalAmount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Selection lives here so the readout and the bars agree, and starts on the
 * bucket containing now. An empty window still draws its stubs, so only Loading
 * gets an early return.
 */
@Composable
private fun SpendingTrend(state: SpendingTrendUiState) {
    if (state is SpendingTrendUiState.Loading) {
        ChartCard("Spending Trend") { EmptyNote("Counting the days…") }
        return
    }

    val success = state as? SpendingTrendUiState.Success
    val empty = state as? SpendingTrendUiState.Empty
    val frame = success?.frame ?: empty?.frame ?: AnalyticsTimeFrame.THIS_MONTH
    val windowStart = success?.windowStart ?: empty?.windowStart ?: 0L
    val currentIndex = success?.currentIndex ?: empty?.currentIndex ?: -1
    val buckets = success?.dailyTrends
        ?: List(empty?.bucketCount ?: 1) { DailyTrend(frame.bucketName(it, windowStart), 0.0) }

    ChartCard("Daily Burn Rate — ${frame.title}") {
        // the frame keys the default: a bucket index means nothing across frames
        var selected by rememberSaveable(frame) {
            mutableIntStateOf(currentIndex.coerceAtLeast(0))
        }

        SpendingTrendChart(
            buckets = buckets,
            maxSpent = success?.maxDaySpent ?: 0.0,
            frame = frame,
            windowStart = windowStart,
            selected = selected,
            currentIndex = currentIndex,
            onSelect = { selected = it },
            modifier = Modifier.fillMaxWidth()
        )
        success?.let {
            Text(
                "${frame.title} total ${idr.format(it.totalWeekSpent)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IncomeVsExpense(income: Long, expense: Long, frame: AnalyticsTimeFrame) {
    ChartCard("Income vs Expense — ${frame.title}") {
        val scale = maxOf(income, expense, 1L)
        val incomeColor = MaterialTheme.colorScheme.primary
        val expenseColor = MaterialTheme.colorScheme.error
        val track = MaterialTheme.colorScheme.surfaceVariant

        GaugeRow("Income", income, income / scale.toFloat(), incomeColor, track)
        GaugeRow("Expense", expense, expense / scale.toFloat(), expenseColor, track)

        val net = income - expense
        Text(
            if (net >= 0) "Net +${idr.format(net)}" else "Overspent ${idr.format(-net)}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (net >= 0) incomeColor else expenseColor
        )
    }
}

@Composable
private fun GaugeRow(label: String, amount: Long, fraction: Float, fill: Color, track: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(idr.format(amount), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(14.dp)
                .semantics { contentDescription = "$label ${idr.format(amount)}" }
        ) {
            val radius = CornerRadius(size.height / 2)
            drawRoundRect(track, size = size, cornerRadius = radius)
            if (fraction > 0f) {
                drawRoundRect(
                    color = fill,
                    size = Size((size.width * fraction).coerceAtLeast(size.height), size.height),
                    cornerRadius = radius
                )
            }
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

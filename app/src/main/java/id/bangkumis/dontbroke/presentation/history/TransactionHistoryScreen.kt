package id.bangkumis.dontbroke.presentation.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.presentation.components.DayHeader
import id.bangkumis.dontbroke.presentation.components.LabeledDropdown
import id.bangkumis.dontbroke.presentation.components.TransactionItem
import java.text.NumberFormat
import java.util.Locale

private val idr = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    .apply { maximumFractionDigits = 0 }

/** Shown in place of a blank filter — the dropdowns need a word for "no filter". */
private const val ALL_LABEL = "All"

/**
 * The full ledger, filtered. The bar sits outside the LazyColumn so it stays put
 * while the list scrolls — no sticky-header experiment needed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionHistoryScreen(
    onBack: () -> Unit,
    onEditTransaction: (Long) -> Unit = {},
    vm: TransactionHistoryViewModel = hiltViewModel()
) {
    val filters by vm.filters.collectAsState()
    val days by vm.days.collectAsState()
    val options by vm.options.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction History", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            FilterBar(
                filters = filters,
                options = options,
                onFrameChange = vm::onFrameChange,
                onCategoryChange = vm::onCategoryChange,
                onAccountChange = vm::onAccountChange,
                onLocationChange = vm::onLocationChange,
                onClear = vm::clearFilters
            )
            HorizontalDivider()

            if (days.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No transactions match these filters.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    days.forEach { day ->
                        item(key = "h${day.dayStart}") { DayHeader(day) }
                        items(day.transactions, key = { it.id }) { txn ->
                            TransactionItem(
                                transaction = txn,
                                onEdit = { onEditTransaction(it.id) },
                                onDelete = vm::delete,
                                showDate = false // the header already says the date
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Timeframe, then the three multi-filters this screen exists for. */
@Composable
private fun FilterBar(
    filters: HistoryFilters,
    options: FilterOptions,
    onFrameChange: (AnalyticsTimeFrame) -> Unit,
    onCategoryChange: (String) -> Unit,
    onAccountChange: (String) -> Unit,
    onLocationChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AnalyticsTimeFrame.entries.forEach { frame ->
                FilterChip(
                    selected = frame == filters.frame,
                    onClick = { onFrameChange(frame) },
                    label = { Text(frame.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown("Category", filters.category, options.categories, onCategoryChange, Modifier.weight(1f))
            FilterDropdown("Account", filters.account, options.accounts, onAccountChange, Modifier.weight(1f))
        }
        FilterDropdown("Location", filters.location, options.locations, onLocationChange)

        // only offered once something is actually filtered
        val active = listOf(filters.category, filters.account, filters.location).count { it != ALL }
        if (active > 0) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "$active filter${if (active > 1) "s" else ""} active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onClear) { Text("Clear") }
            }
        }
    }
}

/** LabeledDropdown with an explicit "All" row standing in for the blank value. */
@Composable
private fun FilterDropdown(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LabeledDropdown(
        label = label,
        value = if (value == ALL) ALL_LABEL else value,
        options = listOf(ALL_LABEL) + options,
        onSelect = { onSelect(if (it == ALL_LABEL) ALL else it) },
        modifier = modifier
    )
}

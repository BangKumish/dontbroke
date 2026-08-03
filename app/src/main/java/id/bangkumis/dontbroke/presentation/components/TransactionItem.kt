package id.bangkumis.dontbroke.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.domain.model.Transaction
import id.bangkumis.dontbroke.domain.model.TransactionDay
import id.bangkumis.dontbroke.domain.model.dayLabel
import id.bangkumis.dontbroke.domain.model.dayStart
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val idrFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
private val dateFormat = SimpleDateFormat("d MMM", Locale("id", "ID"))

/**
 * How far a row must travel before a swipe counts. The default third of the width
 * fires on a careless flick; two thirds has to be meant.
 */
private const val SWIPE_THRESHOLD = 0.65f

/**
 * Tap or swipe right to edit, swipe left to delete.
 *
 * Neither gesture completes the dismissal: [confirmValueChange] refuses every
 * direction and acts on the intent instead, so the row always springs back. That
 * is deliberate for delete — a stray swipe must not be able to destroy a ledger
 * entry, and the dialog is the only thing that can.
 */
@Composable
fun TransactionItem(
    transaction: Transaction,
    onEdit: (Transaction) -> Unit = {},
    onDelete: (Transaction) -> Unit = {},
    showDate: Boolean = true
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val swipeState = rememberSwipeToDismissBoxState(
        positionalThreshold = { distance -> distance * SWIPE_THRESHOLD },
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> confirmDelete = true
                SwipeToDismissBoxValue.StartToEnd -> onEdit(transaction)
                SwipeToDismissBoxValue.Settled -> Unit
            }
            false
        }
    )

    SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            when (swipeState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> SwipeBackdrop(
                    fill = MaterialTheme.colorScheme.primaryContainer,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = Icons.Default.Edit,
                    label = "Edit transaction",
                    alignment = Alignment.CenterStart
                )
                SwipeToDismissBoxValue.EndToStart -> SwipeBackdrop(
                    fill = MaterialTheme.colorScheme.errorContainer,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    icon = Icons.Default.Delete,
                    label = "Delete transaction",
                    alignment = Alignment.CenterEnd
                )
                // nothing to reveal until the drag commits to a direction
                SwipeToDismissBoxValue.Settled -> Unit
            }
        }
    ) {
        TransactionRow(transaction, showDate, Modifier.clickable { onEdit(transaction) })
    }
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete transaction?") },
            text = {
                Text("${transaction.category} — ${idrFormat.format(transaction.amount)} will be removed.")
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(transaction) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }
}

/** What sits behind a swiping row: one color, one icon, anchored to the swipe's side. */
@Composable
private fun SwipeBackdrop(
    fill: Color,
    tint: Color,
    icon: ImageVector,
    label: String,
    alignment: Alignment
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(fill)
            .padding(horizontal = 24.dp),
        contentAlignment = alignment
    ) {
        Icon(icon, contentDescription = label, tint = tint)
    }
}

/**
 * Date plus that day's net, so a long list reads as days rather than rows. Shared
 * by the dashboard feed and the history screen so both name a day the same way.
 */
@Composable
fun DayHeader(day: TransactionDay, modifier: Modifier = Modifier) {
    val net = day.transactions.sumOf {
        if (it.type == TransactionType.INCOME) it.amount else -it.amount
    }
    // pinned per composition: "Hari Ini" must not shift under a scrolling list
    val today = remember { dayStart(System.currentTimeMillis()) }

    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            dayLabel(day.dayStart, today),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            if (net >= 0) "+${idrFormat.format(net)}" else idrFormat.format(net),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (net >= 0) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error
        )
    }
}

/** The row itself, without any gesture opinions — shared by list and swipe box. */
@Composable
private fun TransactionRow(
    transaction: Transaction,
    showDate: Boolean,
    modifier: Modifier = Modifier
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error
    val sign = if (isIncome) "+" else "-"

    ListItem(
        modifier = modifier,
        headlineContent = {
            Text(transaction.category.ifBlank { "Uncategorized" }, fontWeight = FontWeight.Medium)
        },
        supportingContent = {
            val details = listOfNotNull(
                if (showDate) dateFormat.format(Date(transaction.timestamp)) else null,
                transaction.sourceOrAccount.ifBlank { null },
                transaction.location?.ifBlank { null },
                transaction.note.ifBlank { null }
            )
            if (details.isNotEmpty()) Text(details.joinToString(" · "))
        },
        trailingContent = {
            Text(
                "$sign${idrFormat.format(transaction.amount)}",
                color = amountColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    )
}

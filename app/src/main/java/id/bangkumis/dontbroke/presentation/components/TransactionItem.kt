package id.bangkumis.dontbroke.presentation.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.domain.model.Transaction
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val idrFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
private val dateFormat = SimpleDateFormat("d MMM", Locale("id", "ID"))

@Composable
fun TransactionItem(
    transaction: Transaction,
    onEdit: (Transaction) -> Unit = {},
    onDelete: (Transaction) -> Unit = {}
) {
    val isIncome = transaction.type == TransactionType.INCOME
    val amountColor = if (isIncome) MaterialTheme.colorScheme.primary
                      else MaterialTheme.colorScheme.error
    val sign = if (isIncome) "+" else "-"

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.clickable { menuOpen = true },
            headlineContent = {
                Text(transaction.category.ifBlank { "Uncategorized" }, fontWeight = FontWeight.Medium)
            },
            supportingContent = {
                val details = listOfNotNull(
                    dateFormat.format(Date(transaction.timestamp)),
                    transaction.sourceOrAccount.ifBlank { null },
                    transaction.location?.ifBlank { null },
                    transaction.note.ifBlank { null }
                )
                Text(details.joinToString(" · "))
            },
            trailingContent = {
                Text(
                    "$sign${idrFormat.format(transaction.amount)}",
                    color = amountColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        )
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text("Edit") },
                onClick = { menuOpen = false; onEdit(transaction) }
            )
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                onClick = { menuOpen = false; confirmDelete = true }
            )
        }
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

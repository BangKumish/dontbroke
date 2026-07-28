package id.bangkumis.dontbroke.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.domain.model.Account

private fun AccountType.label() = when (this) {
    AccountType.BANK -> "Bank"
    AccountType.E_WALLET -> "E-Wallet"
    AccountType.E_MONEY -> "E-Money"
    AccountType.CASH -> "Cash"
    AccountType.OTHER -> "Other"
}

private val typeLabels = AccountType.entries.map { it.label() }

/** Create a wallet/account, optionally with money already in it. */
@Composable
fun AddAccountDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: AccountType, initialBalance: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(AccountType.BANK) }
    var balance by remember { mutableStateOf("") }

    // The insert ignores conflicts, so without this the starting balance would
    // be dropped without a word.
    val taken = existingNames.any { it.equals(name.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Bank Jago, Jenius…") },
                    singleLine = true,
                    isError = taken,
                    supportingText = if (taken) {
                        { Text("You already have an account called that.") }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                LabeledDropdown(
                    label = "Type",
                    value = type.label(),
                    options = typeLabels,
                    onSelect = { picked -> type = AccountType.entries.first { it.label() == picked } }
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter(Char::isDigit) },
                    label = { Text("Starting balance (Rp)") },
                    placeholder = { Text("Money in it right now — 0 if empty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && !taken,
                onClick = { onConfirm(name.trim(), type, balance.toDoubleOrNull() ?: 0.0) }
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** Adjust an existing account's starting balance; transaction history is untouched. */
@Composable
fun EditInitialBalanceDialog(
    account: Account,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit
) {
    var balance by remember {
        mutableStateOf(account.initialBalance.toLong().takeIf { it != 0L }?.toString() ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(account.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Starting balance only — recorded transactions stay as they are.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = balance,
                    onValueChange = { balance = it.filter(Char::isDigit) },
                    label = { Text("Starting balance (Rp)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(balance.toDoubleOrNull() ?: 0.0) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

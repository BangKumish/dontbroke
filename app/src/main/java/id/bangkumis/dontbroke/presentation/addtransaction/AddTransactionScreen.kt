package id.bangkumis.dontbroke.presentation.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.presentation.components.AddAccountDialog
import id.bangkumis.dontbroke.presentation.components.LabeledDropdown as Dropdown
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateLabel = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))
private const val ADD_ACCOUNT_OPTION = "＋  Add new account…"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(
    onBack: () -> Unit,
    vm: AddTransactionViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val accounts by vm.accounts.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showAddAccount by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit Transaction" else "Add Transaction") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Type toggle
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TransactionType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { vm.onTypeChange(type) },
                        label = { Text(type.name) }
                    )
                }
            }

            OutlinedTextField(
                value = state.amount,
                onValueChange = vm::onAmountChange,
                label = { Text("Amount (Rp)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            // Date — read-only field with a click overlay opening the M3 picker
            Box {
                OutlinedTextField(
                    value = dateLabel.format(Date(state.date)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Date") },
                    trailingIcon = { Icon(Icons.Default.DateRange, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.matchParentSize().clickable { showDatePicker = true })
            }

            Dropdown("Category", state.category, categoriesFor(state.type), vm::onCategoryChange)

            // Accounts come from Room; last entry creates one inline.
            Dropdown(
                label = "Source / Account",
                value = state.sourceOrAccount.ifBlank { "Choose an account" },
                options = accounts.map { it.name } + ADD_ACCOUNT_OPTION,
                onSelect = { picked ->
                    if (picked == ADD_ACCOUNT_OPTION) showAddAccount = true else vm.onSourceChange(picked)
                }
            )

            if (accounts.isEmpty()) {
                Text(
                    "No accounts yet — add your cash or wallet first, with the money you have in it now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.location,
                onValueChange = vm::onLocationChange,
                label = { Text("Location (optional)") },
                placeholder = { Text("Indomaret, KRL Stasiun Palmerah…") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = vm::onNoteChange,
                label = { Text("Keterangan (optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave
            ) { Text(if (state.isEditing) "Update" else "Save") }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.date.toPickerMillis()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { vm.onDateChange(it.fromPickerMillis()) }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
            }
        ) { DatePicker(state = pickerState) }
    }

    if (showAddAccount) {
        AddAccountDialog(
            existingNames = accounts.map { it.name },
            onDismiss = { showAddAccount = false },
            onConfirm = { name, type, initialBalance ->
                vm.createAccount(name, type, initialBalance)
                showAddAccount = false
            }
        )
    }
}

package id.bangkumis.dontbroke.presentation.addtransaction

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.presentation.components.AddAccountDialog
import id.bangkumis.dontbroke.presentation.scan.ReceiptCameraScreen
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
    var showCamera by remember { mutableStateOf(false) }
    val snackbars = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    state.scanMessage?.let { message ->
        LaunchedEffect(message) {
            snackbars.showSnackbar(message)
            vm.dismissScanMessage()
        }
    }

    // Returns early: the viewfinder owns the whole window, and composing the form
    // behind it would keep the camera bound to a screen nobody can see.
    if (showCamera) {
        ReceiptCameraScreen(
            onCaptured = { bitmap ->
                showCamera = false
                vm.scanReceipt(bitmap)
            },
            onPicked = { uri ->
                showCamera = false
                vm.scanReceipt(uri)
            },
            onCancel = { showCamera = false },
            onFailure = { message ->
                showCamera = false
                vm.reportScanError(message)
            }
        )
        return
    }

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
        },
        snackbarHost = { SnackbarHost(snackbars) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Scan first: it fills the fields below, so it reads top-down.
            OutlinedButton(
                onClick = { showCamera = true },
                enabled = !state.isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Star, not AutoAwesome: the sparkles icon lives in
                // material-icons-extended, a dependency this app does not carry
                // and not worth adding for one glyph.
                Icon(Icons.Default.Star, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Pindai Struk / QRIS")
            }

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

    // Modal by construction: Dialog takes the back gesture and the scrim blocks
    // the form, so nothing can be edited into a field the scan is about to fill.
    if (state.isScanning) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    Modifier.padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    Text(
                        "Menganalisis struk dengan AI…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

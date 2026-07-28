package id.bangkumis.dontbroke.presentation.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.domain.model.Account
import id.bangkumis.dontbroke.presentation.components.AddAccountDialog
import id.bangkumis.dontbroke.presentation.components.DonutChart
import id.bangkumis.dontbroke.presentation.components.DonutSlice
import id.bangkumis.dontbroke.presentation.components.EditInitialBalanceDialog
import id.bangkumis.dontbroke.presentation.components.TransactionItem
import java.text.NumberFormat
import java.util.Locale

// Rupiah is not written with cents, and the fraction digits crowd the cards out.
private val idr = NumberFormat.getCurrencyInstance(Locale("id", "ID"))
    .apply { maximumFractionDigits = 0 }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onAddTransaction: () -> Unit,
    onEditTransaction: (Long) -> Unit = {},
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    var showAddAccount by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    LaunchedEffect(Unit) { vm.fetchInsight() }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Dont Broke", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddTransaction) {
                Icon(Icons.Default.Add, contentDescription = "Add transaction")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Balance card
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Balance Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            BalanceStat("Income", idr.format(state.totalIncome), colors.primary)
                            BalanceStat("Spent", idr.format(state.totalExpense), MaterialTheme.colorScheme.error)
                            BalanceStat("Remaining", idr.format(state.totalIncome - state.totalExpense), colors.secondary)
                        }
                    }
                }
            }

            // Spend summary
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Spend Summary", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SpendCard("Today", state.spentToday, Modifier.weight(1f))
                        SpendCard("This Week", state.spentThisWeek, Modifier.weight(1f))
                        SpendCard("This Month", state.spentThisMonth, Modifier.weight(1f))
                    }
                }
            }

            // Accounts & wallets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Accounts & Wallets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            idr.format(state.totalLiquidity),
                            style = MaterialTheme.typography.labelLarge,
                            color = colors.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(state.accounts, key = { it.id }) { account ->
                            AccountCard(account) { editingAccount = account }
                        }
                        item { AddAccountCard { showAddAccount = true } }
                    }
                }
            }

            // Donut chart
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Budget Allocation", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        val alloc = state.allocation
                        DonutChart(
                            slices = listOf(
                                DonutSlice(alloc.essential.toFloat(), colors.primary, "Essential"),
                                DonutSlice(alloc.savings.toFloat(), colors.secondary, "Savings"),
                                DonutSlice(alloc.daily.toFloat(), colors.tertiary, "Daily"),
                            ).filter { it.value > 0 }
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            LegendItem("Essential", idr.format(alloc.essential), colors.primary)
                            LegendItem("Savings", idr.format(alloc.savings), colors.secondary)
                            LegendItem("Daily", idr.format(alloc.daily), colors.tertiary)
                        }
                    }
                }
            }

            // AI insight
            item {
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = colors.primaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("AI Insight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = colors.onPrimaryContainer)
                        if (state.isLoadingInsight) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Text(state.aiInsight.ifBlank { "Tap refresh to load insight." }, color = colors.onPrimaryContainer)
                        }
                    }
                }
            }

            // Recent transactions header
            item {
                Text("Recent Transactions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }

            if (state.recentTransactions.isEmpty()) {
                item { Text("No transactions yet.", color = colors.onSurfaceVariant) }
            } else {
                items(state.recentTransactions, key = { it.id }) { txn ->
                    TransactionItem(
                        transaction = txn,
                        onEdit = { onEditTransaction(it.id) },
                        onDelete = vm::delete
                    )
                }
            }
        }
    }

    if (showAddAccount) {
        AddAccountDialog(
            existingNames = state.accounts.map { it.name },
            onDismiss = { showAddAccount = false },
            onConfirm = { name, type, initialBalance ->
                vm.createAccount(name, type, initialBalance)
                showAddAccount = false
            }
        )
    }

    editingAccount?.let { account ->
        EditInitialBalanceDialog(
            account = account,
            onDismiss = { editingAccount = null },
            onConfirm = { value ->
                vm.setInitialBalance(account.id, value)
                editingAccount = null
            }
        )
    }
}

@Composable
private fun AccountCard(account: Account, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(140.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                account.type.label(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                account.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                idr.format(account.currentBalance),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (account.currentBalance < 0) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun AddAccountCard(onClick: () -> Unit) {
    OutlinedCard(onClick = onClick, modifier = Modifier.width(140.dp)) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add account")
            Text("New account", style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun AccountType.label() = when (this) {
    AccountType.BANK -> "Bank"
    AccountType.E_WALLET -> "E-Wallet"
    AccountType.E_MONEY -> "E-Money"
    AccountType.CASH -> "Cash"
    AccountType.OTHER -> "Other"
}

@Composable
private fun SpendCard(label: String, amount: Long, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            Modifier.padding(vertical = 12.dp, horizontal = 10.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                idr.format(amount),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                // nothing spent reads as calm, not as an alert
                color = if (amount > 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun BalanceStat(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = color, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun LegendItem(label: String, value: String, color: androidx.compose.ui.graphics.Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("●  $label", color = color, style = MaterialTheme.typography.labelSmall)
        Text(value, style = MaterialTheme.typography.labelSmall)
    }
}

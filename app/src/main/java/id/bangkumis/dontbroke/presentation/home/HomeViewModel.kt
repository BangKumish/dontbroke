package id.bangkumis.dontbroke.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.data.repository.AccountRepository
import id.bangkumis.dontbroke.data.repository.TransactionRepository
import id.bangkumis.dontbroke.domain.model.Account
import id.bangkumis.dontbroke.domain.model.BudgetAllocation
import id.bangkumis.dontbroke.domain.model.Transaction
import id.bangkumis.dontbroke.network.api.GeminiApi
import id.bangkumis.dontbroke.network.model.GeminiRequest
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

private fun midnight(now: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/** Local calendar day containing [now], as a half-open [start, end) range. */
fun todayWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    return start to cal.timeInMillis
}

/** Monday 00:00 through Sunday, ending at the next Monday 00:00. */
fun weekWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    // walk back explicitly — Calendar.firstDayOfWeek varies by locale
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) cal.add(Calendar.DAY_OF_MONTH, -1)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 7)
    return start to cal.timeInMillis
}

/** 1st of this month 00:00 up to the 1st of next month. */
fun monthWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}

data class HomeUiState(
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val spentToday: Long = 0,
    val spentThisWeek: Long = 0,
    val spentThisMonth: Long = 0,
    val recentTransactions: List<Transaction> = emptyList(),
    val accounts: List<Account> = emptyList(),
    val allocation: BudgetAllocation = BudgetAllocation(0, 0, 0),
    val aiInsight: String = "",
    val isLoadingInsight: Boolean = false
) {
    val totalLiquidity: Double get() = accounts.sumOf { it.currentBalance }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val gemini: GeminiApi
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        accountRepo.getAll()
            .onEach { accounts -> _state.update { it.copy(accounts = accounts) } }
            .launchIn(viewModelScope)

        // ponytail: windows are pinned when the ViewModel is created; a session
        // left open past midnight keeps yesterday's "Today" until it is recreated.
        val now = System.currentTimeMillis()
        combine(
            repo.expenseIn(todayWindow(now)),
            repo.expenseIn(weekWindow(now)),
            repo.expenseIn(monthWindow(now))
        ) { today, week, month -> Triple(today, week, month) }
            .onEach { (today, week, month) ->
                _state.update {
                    it.copy(spentToday = today, spentThisWeek = week, spentThisMonth = month)
                }
            }
            .launchIn(viewModelScope)

        combine(
            repo.totalIncome(),
            repo.totalExpense(),
            repo.getAll()
        ) { income, expense, txns ->
            val remaining = income - expense
            // ponytail: naive 50/30/20 split on remaining; replace with user-defined rules if needed
            HomeUiState(
                totalIncome = income,
                totalExpense = expense,
                recentTransactions = txns.take(10),
                allocation = BudgetAllocation(
                    essential = (remaining * 0.50).toLong(),
                    savings   = (remaining * 0.30).toLong(),
                    daily     = (remaining * 0.20).toLong()
                )
            )
        }.onEach { snap ->
            // copy, not replace — otherwise every DB change wipes the loaded AI insight
            _state.update {
                it.copy(
                    totalIncome = snap.totalIncome,
                    totalExpense = snap.totalExpense,
                    recentTransactions = snap.recentTransactions,
                    allocation = snap.allocation
                )
            }
        }.launchIn(viewModelScope)
    }

    fun delete(transaction: Transaction) {
        viewModelScope.launch { repo.delete(transaction) }
    }

    fun createAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch { accountRepo.create(name, type, initialBalance) }
    }

    fun setInitialBalance(accountId: Long, value: Double) {
        viewModelScope.launch { accountRepo.setInitialBalance(accountId, value) }
    }

    fun fetchInsight() {
        val s = _state.value
        if (s.isLoadingInsight) return
        _state.update { it.copy(isLoadingInsight = true) }
        viewModelScope.launch {
            val prompt = """
                Monthly income: Rp ${s.totalIncome}
                Total spent: Rp ${s.totalExpense}
                Remaining: Rp ${s.totalIncome - s.totalExpense}
                Budget split — Essential: Rp ${s.allocation.essential}, Savings: Rp ${s.allocation.savings}, Daily: Rp ${s.allocation.daily}
                Give one concise financial insight in 2 sentences for an Indonesian user.
            """.trimIndent()
            val insight = runCatching {
                gemini.generateContent(request = GeminiRequest(
                    listOf(GeminiRequest.Content(listOf(GeminiRequest.Part(prompt))))
                )).text()
            }.getOrElse { "Could not load insight. Check your API key." }
            _state.update { it.copy(aiInsight = insight, isLoadingInsight = false) }
        }
    }
}

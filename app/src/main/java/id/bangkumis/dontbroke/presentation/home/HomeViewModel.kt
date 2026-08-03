package id.bangkumis.dontbroke.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.bangkumis.dontbroke.BuildConfig
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.data.repository.AccountRepository
import id.bangkumis.dontbroke.data.repository.TransactionRepository
import id.bangkumis.dontbroke.domain.model.Account
import id.bangkumis.dontbroke.domain.model.AnalyticsRange
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.CategoryExpense
import id.bangkumis.dontbroke.domain.model.DailyTrend
import id.bangkumis.dontbroke.domain.model.MonthComparison
import id.bangkumis.dontbroke.domain.model.Transaction
import id.bangkumis.dontbroke.domain.model.TransactionDay
import id.bangkumis.dontbroke.domain.model.groupByDay
import id.bangkumis.dontbroke.domain.model.monthWindow
import id.bangkumis.dontbroke.domain.model.todayWindow
import id.bangkumis.dontbroke.domain.model.weekWindow
import id.bangkumis.dontbroke.domain.usecase.GetCategorySpendingUseCase
import id.bangkumis.dontbroke.domain.usecase.GetIncomeVsExpenseUseCase
import id.bangkumis.dontbroke.domain.usecase.GetSpendingTrendUseCase
import id.bangkumis.dontbroke.network.api.GeminiApi
import id.bangkumis.dontbroke.network.model.GeminiRequest
import id.bangkumis.dontbroke.presentation.history.ALL
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the category ring shows. [Empty] is a real answer, not a failure. */
sealed interface CategorySpendingUiState {
    data object Loading : CategorySpendingUiState
    data object Empty : CategorySpendingUiState
    data class Success(
        val categories: List<CategoryExpense>,
        val totalSpent: Double
    ) : CategorySpendingUiState
}

/**
 * What the trend chart shows. [Empty] still knows its shape — a month with no
 * spending draws thirty flat stubs, not nothing.
 */
sealed interface SpendingTrendUiState {
    data object Loading : SpendingTrendUiState
    data class Empty(
        val frame: AnalyticsTimeFrame,
        val windowStart: Long,
        val bucketCount: Int,
        val currentIndex: Int
    ) : SpendingTrendUiState
    data class Success(
        val dailyTrends: List<DailyTrend>,
        val maxDaySpent: Double,
        val totalWeekSpent: Double,
        val frame: AnalyticsTimeFrame,
        val windowStart: Long,
        val currentIndex: Int
    ) : SpendingTrendUiState
}

/**
 * A window with nothing spent is [Empty], not a Success full of zeroes —
 * otherwise the chart would scale its bars against a peak of zero.
 */
fun spendingTrendState(
    daily: List<DailyTrend>,
    frame: AnalyticsTimeFrame,
    windowStart: Long,
    currentIndex: Int
): SpendingTrendUiState {
    val max = daily.maxOfOrNull { it.totalSpent } ?: 0.0
    if (max <= 0.0) {
        return SpendingTrendUiState.Empty(
            frame = frame,
            windowStart = windowStart,
            bucketCount = daily.size.coerceAtLeast(1),
            currentIndex = currentIndex
        )
    }
    return SpendingTrendUiState.Success(
        dailyTrends = daily,
        maxDaySpent = max,
        totalWeekSpent = daily.sumOf { it.totalSpent },
        frame = frame,
        windowStart = windowStart,
        currentIndex = currentIndex
    )
}

data class HomeUiState(
    val totalIncome: Long = 0,
    val totalExpense: Long = 0,
    val spentToday: Long = 0,
    val spentThisWeek: Long = 0,
    val spentThisMonth: Long = 0,
    val accounts: List<Account> = emptyList(),
    val aiInsight: String = "",
    val isLoadingInsight: Boolean = false
) {
    val totalLiquidity: Double get() = accounts.sumOf { it.currentBalance }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val accountRepo: AccountRepository,
    private val getCategorySpending: GetCategorySpendingUseCase,
    private val getSpendingTrend: GetSpendingTrendUseCase,
    private val getIncomeVsExpense: GetIncomeVsExpenseUseCase,
    private val gemini: GeminiApi
) : ViewModel() {

    // ponytail: windows are pinned when the ViewModel is created; a session
    // left open past midnight keeps yesterday's "Today" until it is recreated.
    private val now = System.currentTimeMillis()

    /**
     * The one control every chart below reacts to: which frame, and which window
     * of it. Changing the frame re-anchors on now — stepping three months back and
     * then switching to Day should show today, not a day in April.
     */
    private val _range = MutableStateFlow(AnalyticsRange(anchor = now))
    val range: StateFlow<AnalyticsRange> = _range.asStateFlow()

    /** Whether the `>` arrow can move: the window must still be in the past. */
    val canStepForward: StateFlow<Boolean> = _range
        .map { it.canStepForward(now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun onTimeFrameChange(frame: AnalyticsTimeFrame) {
        _range.update { AnalyticsRange(frame = frame, anchor = now, customStart = it.customStart, customEnd = it.customEnd) }
    }

    /** `<` and `>`; a no-op on CUSTOM, whose window comes from the picker. */
    fun onStep(delta: Int) {
        _range.update { if (it.canStepBack) it.stepped(delta) else it }
    }

    /** Both bounds are inclusive local day starts — a single date passes the same value twice. */
    fun onCustomRange(startMillis: Long, endMillis: Long) {
        _range.update {
            it.copy(
                frame = AnalyticsTimeFrame.CUSTOM,
                customStart = minOf(startMillis, endMillis),
                customEnd = maxOf(startMillis, endMillis)
            )
        }
    }

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    /**
     * The Recent Transactions feed has its own timeframe, deliberately separate
     * from [_range]: the charts above answer "how did this month go", the feed
     * answers "what did I just spend". Tying them would make one control fight
     * two jobs.
     */
    private val _feedRange = MutableStateFlow(
        AnalyticsRange(frame = AnalyticsTimeFrame.THIS_WEEK, anchor = now)
    )
    val feedRange: StateFlow<AnalyticsRange> = _feedRange.asStateFlow()

    /** Grouped by day, so the list reads as days rather than an undifferentiated run of rows. */
    val feedDays: StateFlow<List<TransactionDay>> =
        _feedRange
            .flatMapLatest { range ->
                val (from, until) = range.window
                // no category/account/location filter here — that is what the
                // history screen's "View All" is for
                repo.filtered(from, until, ALL, ALL, ALL).map { groupByDay(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onFeedFrameChange(frame: AnalyticsTimeFrame) {
        _feedRange.update {
            AnalyticsRange(frame = frame, anchor = now, customStart = it.customStart, customEnd = it.customEnd)
        }
    }

    /** Both bounds are inclusive local day starts — a single date passes the same value twice. */
    fun onFeedCustomRange(startMillis: Long, endMillis: Long) {
        _feedRange.update {
            it.copy(
                frame = AnalyticsTimeFrame.CUSTOM,
                customStart = minOf(startMillis, endMillis),
                customEnd = maxOf(startMillis, endMillis)
            )
        }
    }

    /** Loading until the first DB emission for the selected window lands. */
    val categorySpending: StateFlow<CategorySpendingUiState> =
        _range
            .flatMapLatest { range ->
                val (from, until) = range.window
                getCategorySpending(from, until).map { categories ->
                    if (categories.isEmpty()) CategorySpendingUiState.Empty
                    else CategorySpendingUiState.Success(
                        categories = categories,
                        totalSpent = categories.sumOf { it.totalAmount }
                    )
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CategorySpendingUiState.Loading)

    val spendingTrend: StateFlow<SpendingTrendUiState> =
        _range
            .flatMapLatest { range ->
                val frame = range.frame
                val (from, until) = range.window
                val count = frame.bucketCount(from, until)
                val current = frame.currentBucket(now, from, count)
                getSpendingTrend(frame, from, until)
                    .map { daily -> spendingTrendState(daily, frame, from, current) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpendingTrendUiState.Loading)

    val comparison: StateFlow<MonthComparison> =
        _range
            .flatMapLatest { range ->
                val (from, until) = range.window
                getIncomeVsExpense(from, until)
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MonthComparison(0.0, 0.0))

    init {
        accountRepo.getAll()
            .onEach { accounts -> _state.update { it.copy(accounts = accounts) } }
            .launchIn(viewModelScope)

        combine(
            repo.expenseIn(todayWindow(now)),
            repo.expenseIn(weekWindow(now)),
            repo.expenseIn(monthWindow(now))
        ) { today, wk, mon -> Triple(today, wk, mon) }
            .onEach { (today, wk, mon) ->
                _state.update {
                    it.copy(spentToday = today, spentThisWeek = wk, spentThisMonth = mon)
                }
            }
            .launchIn(viewModelScope)

        combine(repo.totalIncome(), repo.totalExpense()) { income, expense -> income to expense }
            .onEach { (income, expense) ->
                // copy, not replace — otherwise every DB change wipes the loaded AI insight
                _state.update { it.copy(totalIncome = income, totalExpense = expense) }
            }
            .launchIn(viewModelScope)
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
        // a keyless request can only come back 403 — say why instead of firing it
        if (BuildConfig.GEMINI_API_KEY.isBlank()) {
            _state.update { it.copy(aiInsight = "No API key configured — set GEMINI_API_KEY in local.properties.") }
            return
        }
        _state.update { it.copy(isLoadingInsight = true) }
        viewModelScope.launch {
            val prompt = """
                Monthly income: Rp ${s.totalIncome}
                Total spent: Rp ${s.totalExpense}
                Remaining: Rp ${s.totalIncome - s.totalExpense}
                Spent today: Rp ${s.spentToday}, this week: Rp ${s.spentThisWeek}, this month: Rp ${s.spentThisMonth}
                Give one concise financial insight in 2 sentences for an Indonesian user.
            """.trimIndent()
            val insight = runCatching {
                gemini.generateContent(request = GeminiRequest(
                    listOf(GeminiRequest.Content(listOf(GeminiRequest.Part(prompt))))
                )).text()
            }.getOrElse { e ->
                android.util.Log.e("HomeViewModel", "Gemini API error", e)
                when (e) {
                    is retrofit2.HttpException -> "API Error ${e.code()}: ${
                        if (e.code() == 403) "Invalid API key — check BuildConfig.GEMINI_API_KEY"
                        else if (e.code() == 429) "Rate limit exceeded"
                        else e.message()
                    }"
                    is java.net.UnknownHostException -> "No internet connection"
                    is java.net.SocketTimeoutException -> "Request timed out"
                    else -> "Could not load insight: ${e.message ?: e::class.simpleName}"
                }
            }
            _state.update { it.copy(aiInsight = insight, isLoadingInsight = false) }
        }
    }
}

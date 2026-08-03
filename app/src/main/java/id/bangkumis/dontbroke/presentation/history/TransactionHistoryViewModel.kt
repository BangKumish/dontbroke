package id.bangkumis.dontbroke.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.bangkumis.dontbroke.data.repository.AccountRepository
import id.bangkumis.dontbroke.data.repository.TransactionRepository
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.Transaction
import id.bangkumis.dontbroke.domain.model.TransactionDay
import id.bangkumis.dontbroke.domain.model.groupByDay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Blank means "no filter" — see TransactionDao.getFiltered. */
const val ALL = ""

data class HistoryFilters(
    val frame: AnalyticsTimeFrame = AnalyticsTimeFrame.THIS_MONTH,
    val category: String = ALL,
    val account: String = ALL,
    val location: String = ALL
)

/** The options each filter offers, gathered from the data rather than hardcoded. */
data class FilterOptions(
    val categories: List<String> = emptyList(),
    val accounts: List<String> = emptyList(),
    val locations: List<String> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TransactionHistoryViewModel @Inject constructor(
    private val repo: TransactionRepository,
    accountRepo: AccountRepository
) : ViewModel() {

    // ponytail: pinned at construction like the dashboard's windows; a screen left
    // open past midnight keeps yesterday's "Day" until it is recreated.
    private val now = System.currentTimeMillis()

    private val _filters = MutableStateFlow(HistoryFilters())
    val filters: StateFlow<HistoryFilters> = _filters.asStateFlow()

    /** Every filter re-queries; SQL does the work, not the list in memory. */
    val days: StateFlow<List<TransactionDay>> =
        _filters
            .flatMapLatest { f ->
                val (from, until) = f.frame.window(now)
                repo.filtered(from, until, f.category, f.account, f.location)
                    .map { groupByDay(it) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val options: StateFlow<FilterOptions> =
        combine(
            repo.categories(),
            accountRepo.getAll().map { list -> list.map { it.name } },
            repo.locations()
        ) { categories, accounts, locations ->
            FilterOptions(categories, accounts, locations)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FilterOptions())

    fun onFrameChange(frame: AnalyticsTimeFrame) = _filters.update { it.copy(frame = frame) }
    fun onCategoryChange(value: String) = _filters.update { it.copy(category = value) }
    fun onAccountChange(value: String) = _filters.update { it.copy(account = value) }
    fun onLocationChange(value: String) = _filters.update { it.copy(location = value) }
    fun clearFilters() = _filters.update { HistoryFilters(frame = it.frame) }

    /** The DAO rebalances the affected account in the same DB transaction. */
    fun delete(transaction: Transaction) {
        viewModelScope.launch { repo.delete(transaction) }
    }
}

package id.bangkumis.dontbroke.presentation.addtransaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.data.repository.AccountRepository
import id.bangkumis.dontbroke.data.repository.TransactionRepository
import id.bangkumis.dontbroke.domain.model.Account
import id.bangkumis.dontbroke.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject

val INCOME_CATEGORIES = listOf("Salary", "Side Job", "Investment Return", "Gift", "Other")

val EXPENSE_CATEGORIES = listOf(
    "Food & Beverage", "Transportation", "Rent & Housing", "Bills & Utilities",
    "Shopping", "Entertainment", "Healthcare", "Education", "Other"
)

fun categoriesFor(type: TransactionType) = when (type) {
    TransactionType.INCOME -> INCOME_CATEGORIES
    TransactionType.EXPENSE -> EXPENSE_CATEGORIES
}

/** M3 DatePicker speaks UTC midnight; the DB stores local wall-clock millis. */
fun Long.toPickerMillis(): Long = this + TimeZone.getDefault().getOffset(this)
fun Long.fromPickerMillis(): Long = this - TimeZone.getDefault().getOffset(this)

data class AddTransactionUiState(
    val id: Long = 0,
    val amount: String = "",
    val type: TransactionType = TransactionType.EXPENSE,
    val category: String = EXPENSE_CATEGORIES.first(),
    /** Blank until picked — accounts are user-created, so nothing is safe to assume. */
    val sourceOrAccount: String = "",
    val location: String = "",
    val note: String = "",
    val date: Long = System.currentTimeMillis(),
    val saved: Boolean = false
) {
    val isEditing: Boolean get() = id != 0L

    /**
     * An amount with no account would be stored against a nonexistent account
     * name and silently counted in no balance at all.
     */
    val canSave: Boolean
        get() = (amount.toLongOrNull() ?: 0) > 0 && sourceOrAccount.isNotBlank()
}

@HiltViewModel
class AddTransactionViewModel @Inject constructor(
    private val repo: TransactionRepository,
    private val accountRepo: AccountRepository,
    handle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(AddTransactionUiState())
    val state = _state.asStateFlow()

    val accounts: StateFlow<List<Account>> = accountRepo.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Creates the account and selects it straight away. */
    fun createAccount(name: String, type: AccountType, initialBalance: Double) {
        viewModelScope.launch {
            accountRepo.create(name, type, initialBalance)
            onSourceChange(name.trim())
        }
    }

    init {
        val editId = handle.get<Long>("id") ?: 0L
        if (editId > 0) viewModelScope.launch {
            repo.getById(editId)?.let { t ->
                _state.value = AddTransactionUiState(
                    id = t.id,
                    amount = t.amount.toString(),
                    type = t.type,
                    category = t.category,
                    sourceOrAccount = t.sourceOrAccount,
                    location = t.location.orEmpty(),
                    note = t.note,
                    date = t.timestamp
                )
            }
        }
    }

    fun onAmountChange(v: String) = _state.update { it.copy(amount = v.filter(Char::isDigit)) }

    fun onTypeChange(v: TransactionType) = _state.update {
        val cats = categoriesFor(v)
        it.copy(type = v, category = if (it.category in cats) it.category else cats.first())
    }

    fun onCategoryChange(v: String) = _state.update { it.copy(category = v) }
    fun onSourceChange(v: String) = _state.update { it.copy(sourceOrAccount = v) }
    fun onLocationChange(v: String) = _state.update { it.copy(location = v) }
    fun onNoteChange(v: String) = _state.update { it.copy(note = v) }
    fun onDateChange(v: Long) = _state.update { it.copy(date = v) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        val amount = s.amount.toLong()
        viewModelScope.launch {
            repo.save(
                Transaction(
                    id = s.id,
                    amount = amount,
                    type = s.type,
                    category = s.category,
                    note = s.note.trim(),
                    sourceOrAccount = s.sourceOrAccount,
                    location = s.location.trim().ifBlank { null },
                    timestamp = s.date
                )
            )
            _state.update { it.copy(saved = true) }
        }
    }
}

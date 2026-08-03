package id.bangkumis.dontbroke.data.repository

import id.bangkumis.dontbroke.data.local.dao.TransactionDao
import id.bangkumis.dontbroke.data.local.entity.TransactionEntity
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransactionRepository @Inject constructor(private val dao: TransactionDao) {

    fun getAll(): Flow<List<Transaction>> = dao.getAll().map { list ->
        list.map { it.toDomain() }
    }

    /** Newest [limit] rows, bounded in SQL so the dashboard never loads the ledger. */
    fun recent(limit: Int): Flow<List<Transaction>> =
        dao.getRecent(limit).map { list -> list.map { it.toDomain() } }

    /** History feed. A blank filter means "all" — see [TransactionDao.getFiltered]. */
    fun filtered(
        from: Long,
        until: Long,
        category: String,
        account: String,
        location: String
    ): Flow<List<Transaction>> =
        dao.getFiltered(from, until, category, account, location)
            .map { list -> list.map { it.toDomain() } }

    fun categories(): Flow<List<String>> = dao.distinctCategories()
    fun locations(): Flow<List<String>> = dao.distinctLocations()

    fun totalIncome(): Flow<Long> = dao.totalIncome()
    fun totalExpense(): Flow<Long> = dao.totalExpense()

    /** Spend in [window), as produced by the period helpers in HomeViewModel. */
    fun expenseIn(window: Pair<Long, Long>): Flow<Long> =
        dao.expenseBetween(window.first, window.second)

    suspend fun getById(id: Long): Transaction? = dao.getById(id)?.toDomain()

    /**
     * Insert, or overwrite when [Transaction.id] already exists (edit).
     * The DAO rebalances the affected account(s) in the same DB transaction.
     */
    suspend fun save(t: Transaction) = dao.save(t.toEntity())
    suspend fun delete(t: Transaction) = dao.deleteAndRecalc(t.toEntity())

    private fun TransactionEntity.toDomain() =
        Transaction(id, amount, type, category, note, sourceOrAccount, location, timestamp)

    private fun Transaction.toEntity() =
        TransactionEntity(id, amount, type, category, note, sourceOrAccount, location, timestamp)
}

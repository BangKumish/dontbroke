package id.bangkumis.dontbroke.data.local.dao

import androidx.room.*
import id.bangkumis.dontbroke.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Balance = the account's own starting balance plus the signed sum of every
 * transaction pointing at it by name. Append a WHERE clause to scope it.
 * Shared with the v2→v3 migration so both can never drift apart.
 */
internal const val RECALC_ALL_BALANCES =
    "UPDATE accounts SET currentBalance = accounts.initialBalance + COALESCE((" +
        "SELECT SUM(CASE WHEN transactions.type = 'INCOME' THEN transactions.amount " +
        "ELSE -transactions.amount END) FROM transactions " +
        "WHERE transactions.sourceOrAccount = accounts.name), 0)"

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type = 'INCOME'")
    fun totalIncome(): Flow<Long>

    @Query("SELECT COALESCE(SUM(amount),0) FROM transactions WHERE type = 'EXPENSE'")
    fun totalExpense(): Flow<Long>

    /**
     * Spend inside a half-open window [from, until) — the boundary instant
     * belongs to the next period, so day/week/month totals can never
     * double-count a transaction dated exactly at midnight.
     */
    @Query(
        "SELECT COALESCE(SUM(amount),0) FROM transactions " +
            "WHERE type = 'EXPENSE' AND timestamp >= :from AND timestamp < :until"
    )
    fun expenseBetween(from: Long, until: Long): Flow<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    /**
     * Recomputes one account's balance from scratch instead of applying deltas —
     * no revert bookkeeping to get wrong, and drift can never accumulate.
     */
    @Query("$RECALC_ALL_BALANCES WHERE accounts.name = :accountName")
    suspend fun recalcAccountBalance(accountName: String)

    /** Insert or overwrite, then rebalance the accounts the row moved between. */
    @Transaction
    suspend fun save(entity: TransactionEntity) {
        val previousAccount = if (entity.id != 0L) getById(entity.id)?.sourceOrAccount else null
        insert(entity)
        if (previousAccount != null && previousAccount != entity.sourceOrAccount) {
            recalcAccountBalance(previousAccount)
        }
        recalcAccountBalance(entity.sourceOrAccount)
    }

    @Transaction
    suspend fun deleteAndRecalc(entity: TransactionEntity) {
        delete(entity)
        recalcAccountBalance(entity.sourceOrAccount)
    }
}

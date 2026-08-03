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

/**
 * Expenses per category inside a half-open window, biggest first.
 * Kept as a constant so a JVM test can run the real statement.
 */
internal const val EXPENSE_BY_CATEGORY =
    "SELECT category, COALESCE(SUM(amount),0) AS total FROM transactions " +
        "WHERE type = 'EXPENSE' AND timestamp >= :from AND timestamp < :until " +
        "GROUP BY category ORDER BY total DESC"

/**
 * Trend buckets: expenses grouped by how many whole [:bucketMs] slices they sit
 * past [:from], so one statement serves an hourly, daily or weekly axis. A slice
 * with no spend produces no row at all; the caller pads the gaps.
 *
 * ponytail: fixed-width millisecond slices, exact in Indonesia (WIB/WITA/WIT have
 * no DST). Bucket in Kotlin off Calendar if this ever ships to a DST zone.
 */
internal const val EXPENSE_BY_BUCKET =
    "SELECT (timestamp - :from) / :bucketMs AS bucketIndex, COALESCE(SUM(amount),0) AS total " +
        "FROM transactions WHERE type = 'EXPENSE' " +
        "AND timestamp >= :from AND timestamp < :until " +
        "GROUP BY bucketIndex ORDER BY bucketIndex"

/**
 * Income and expense side by side for one window. No GROUP BY, so SQLite
 * always returns exactly one row — zeroed rather than absent on an empty table.
 */
internal const val MONTH_TOTALS =
    "SELECT COALESCE(SUM(CASE WHEN type = 'INCOME' THEN amount ELSE 0 END),0) AS income, " +
        "COALESCE(SUM(CASE WHEN type = 'EXPENSE' THEN amount ELSE 0 END),0) AS expense " +
        "FROM transactions WHERE timestamp >= :from AND timestamp < :until"

/** Row shapes for the aggregate queries above. */
data class CategoryTotal(val category: String, val total: Long)
data class BucketTotal(val bucketIndex: Int, val total: Long)
data class MonthTotals(val income: Long, val expense: Long)

@Dao
interface TransactionDao {

    @Query(EXPENSE_BY_CATEGORY)
    fun expenseByCategory(from: Long, until: Long): Flow<List<CategoryTotal>>

    @Query(EXPENSE_BY_BUCKET)
    fun expenseByBucket(from: Long, until: Long, bucketMs: Long): Flow<List<BucketTotal>>

    @Query(MONTH_TOTALS)
    fun monthTotals(from: Long, until: Long): Flow<MonthTotals>

    /** Dashboard feed. Bounded in SQL so the dashboard never loads the whole ledger. */
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<TransactionEntity>>

    /**
     * History feed. An empty filter string means "all", so one statement covers
     * every combination and SQLite keeps using the timestamp ordering.
     *
     * A chosen location matches only rows carrying it — rows with no location at
     * all are a different thing from rows at that merchant.
     */
    @Query(
        "SELECT * FROM transactions WHERE " +
            "timestamp >= :from AND timestamp < :until " +
            "AND (:category = '' OR category = :category) " +
            "AND (:account = '' OR sourceOrAccount = :account) " +
            "AND (:location = '' OR location = :location) " +
            "ORDER BY timestamp DESC, id DESC"
    )
    fun getFiltered(
        from: Long,
        until: Long,
        category: String,
        account: String,
        location: String
    ): Flow<List<TransactionEntity>>

    /** Filter options, straight from the data rather than a hardcoded list. */
    @Query("SELECT DISTINCT category FROM transactions WHERE category != '' ORDER BY category")
    fun distinctCategories(): Flow<List<String>>

    @Query(
        "SELECT DISTINCT location FROM transactions " +
            "WHERE location IS NOT NULL AND location != '' ORDER BY location"
    )
    fun distinctLocations(): Flow<List<String>>

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

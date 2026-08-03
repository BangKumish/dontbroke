package id.bangkumis.dontbroke.data.repository

import id.bangkumis.dontbroke.data.local.dao.TransactionDao
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.CategoryExpense
import id.bangkumis.dontbroke.domain.model.DailyTrend
import id.bangkumis.dontbroke.domain.model.MonthComparison
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns the DAO's raw aggregate rows into domain models. Amounts are whole
 * rupiah in the database (Rupiah has no cents), so the widening to Double is
 * plain — no scaling.
 *
 * Every method is empty-safe: SQLite simply returns no rows for an empty table,
 * which maps to an empty list, a run of zeroed buckets, or a zeroed comparison.
 */
@Singleton
class AnalyticsRepository @Inject constructor(private val dao: TransactionDao) {

    /** Expenses per category in [from, until), biggest first. Nothing spent → empty list. */
    fun categorySpending(from: Long, until: Long): Flow<List<CategoryExpense>> =
        dao.expenseByCategory(from, until).map { rows ->
            val total = rows.sumOf { it.total }
            // guard the divide, not the list: no rows means no slices to build anyway
            if (total <= 0L) return@map emptyList()
            rows.map {
                CategoryExpense(
                    categoryName = it.category,
                    totalAmount = it.total.toDouble(),
                    percentage = it.total * 100f / total
                )
            }
        }

    /**
     * One bucket per slice of [frame] covering [from, until) — 24 hours, 7 days,
     * ~30 days or ~52 weeks. Slices with no spend produce no row in SQL, so they
     * are padded here rather than left missing.
     */
    fun spendingTrend(frame: AnalyticsTimeFrame, from: Long, until: Long): Flow<List<DailyTrend>> {
        val count = frame.bucketCount(from, until)
        return dao.expenseByBucket(from, until, frame.bucketMs).map { rows ->
            val buckets = LongArray(count)
            rows.forEach { row ->
                // a clamp, not a trust: a stale window must not throw out of bounds
                buckets[row.bucketIndex.coerceIn(0, count - 1)] += row.total
            }
            List(count) { i -> DailyTrend(frame.bucketName(i, from), buckets[i].toDouble()) }
        }
    }

    /** Income vs expense for one window. Never null — the query has no GROUP BY. */
    fun comparison(from: Long, until: Long): Flow<MonthComparison> =
        dao.monthTotals(from, until).map {
            MonthComparison(income = it.income.toDouble(), expense = it.expense.toDouble())
        }
}

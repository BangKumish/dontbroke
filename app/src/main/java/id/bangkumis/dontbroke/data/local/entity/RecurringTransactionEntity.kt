package id.bangkumis.dontbroke.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Calendar

enum class BillingCycle { DAILY, WEEKLY, MONTHLY, YEARLY }

/**
 * A bill that repeats. Fields mirror [TransactionEntity] so a due bill converts
 * into a real transaction with no lookup: [category] is the same plain string
 * used there (this app has no category table) and [sourceOrAccount] is the
 * account *name*, the join key balances are computed on.
 */
@Entity(tableName = "recurring_transactions")
data class RecurringTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val amount: Long,
    val category: String,
    val sourceOrAccount: String,
    val billingCycle: BillingCycle,
    /** Epoch millis of the next charge; advanced by [BillingCycle.next] when paid. */
    val nextDueDate: Long,
    val isActive: Boolean = true
)

/** Same instant, one cycle later. Calendar handles month lengths and leap years. */
fun BillingCycle.next(from: Long): Long = Calendar.getInstance().apply {
    timeInMillis = from
    when (this@next) {
        BillingCycle.DAILY -> add(Calendar.DAY_OF_MONTH, 1)
        BillingCycle.WEEKLY -> add(Calendar.WEEK_OF_YEAR, 1)
        BillingCycle.MONTHLY -> add(Calendar.MONTH, 1)
        BillingCycle.YEARLY -> add(Calendar.YEAR, 1)
    }
}.timeInMillis

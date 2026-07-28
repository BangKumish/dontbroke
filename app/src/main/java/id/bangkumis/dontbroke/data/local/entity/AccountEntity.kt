package id.bangkumis.dontbroke.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class AccountType { BANK, E_WALLET, E_MONEY, CASH, OTHER }

/**
 * A wallet / account money moves through. [name] is the join key used by
 * [TransactionEntity.sourceOrAccount], so it is unique.
 */
@Entity(tableName = "accounts", indices = [Index(value = ["name"], unique = true)])
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.OTHER,
    val initialBalance: Double = 0.0,
    /** Derived: initialBalance + signed sum of this account's transactions. */
    val currentBalance: Double = 0.0,
    val iconResOrName: String? = null
)

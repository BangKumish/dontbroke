package id.bangkumis.dontbroke.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class TransactionType { INCOME, EXPENSE }

// Every list and aggregate filters or orders by timestamp, so it carries an index.
@Entity(tableName = "transactions", indices = [Index("timestamp")])
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    /** Keterangan — optional. */
    val note: String = "",
    @ColumnInfo(defaultValue = "'Cash'") val sourceOrAccount: String = "Cash",
    val location: String? = null,
    /** Transaction date (epoch millis) and list sort key. */
    val timestamp: Long = System.currentTimeMillis()
)

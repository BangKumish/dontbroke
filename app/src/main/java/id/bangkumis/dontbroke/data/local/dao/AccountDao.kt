package id.bangkumis.dontbroke.data.local.dao

import androidx.room.*
import id.bangkumis.dontbroke.data.local.entity.AccountEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    /** Funded accounts first, so the home row leads with what actually has money. */
    @Query("SELECT * FROM accounts ORDER BY currentBalance DESC, name ASC")
    fun getAll(): Flow<List<AccountEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: AccountEntity): Long

    /** Shifts currentBalance by the same delta, so transaction history stays intact. */
    @Query(
        "UPDATE accounts SET currentBalance = currentBalance - initialBalance + :value, " +
            "initialBalance = :value WHERE id = :id"
    )
    suspend fun setInitialBalance(id: Long, value: Double)
}

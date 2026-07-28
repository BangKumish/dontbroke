package id.bangkumis.dontbroke.data.repository

import id.bangkumis.dontbroke.data.local.dao.AccountDao
import id.bangkumis.dontbroke.data.local.entity.AccountEntity
import id.bangkumis.dontbroke.data.local.entity.AccountType
import id.bangkumis.dontbroke.domain.model.Account
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AccountRepository @Inject constructor(private val dao: AccountDao) {

    fun getAll(): Flow<List<Account>> = dao.getAll().map { list -> list.map { it.toDomain() } }

    /** New accounts start at their initial balance; ignored if the name already exists. */
    suspend fun create(name: String, type: AccountType, initialBalance: Double = 0.0) =
        dao.insert(
            AccountEntity(
                name = name.trim(),
                type = type,
                initialBalance = initialBalance,
                currentBalance = initialBalance
            )
        )

    suspend fun setInitialBalance(id: Long, value: Double) = dao.setInitialBalance(id, value)

    private fun AccountEntity.toDomain() =
        Account(id, name, type, initialBalance, currentBalance, iconResOrName)
}

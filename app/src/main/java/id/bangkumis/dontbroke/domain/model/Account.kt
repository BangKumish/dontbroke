package id.bangkumis.dontbroke.domain.model

import id.bangkumis.dontbroke.data.local.entity.AccountType

data class Account(
    val id: Long = 0,
    val name: String,
    val type: AccountType = AccountType.OTHER,
    val initialBalance: Double = 0.0,
    val currentBalance: Double = 0.0,
    val iconResOrName: String? = null
)

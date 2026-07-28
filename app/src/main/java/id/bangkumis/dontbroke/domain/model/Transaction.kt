package id.bangkumis.dontbroke.domain.model

import id.bangkumis.dontbroke.data.local.entity.TransactionType

data class Transaction(
    val id: Long = 0,
    val amount: Long,
    val type: TransactionType,
    val category: String,
    val note: String = "",
    val sourceOrAccount: String = "Cash",
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

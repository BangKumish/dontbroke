package id.bangkumis.dontbroke.domain.usecase

import id.bangkumis.dontbroke.data.repository.AnalyticsRepository
import id.bangkumis.dontbroke.domain.model.CategoryExpense
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Expenses per category in the half-open window [startDate, endDate). */
class GetCategorySpendingUseCase @Inject constructor(
    private val analytics: AnalyticsRepository
) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<List<CategoryExpense>> =
        analytics.categorySpending(startDate, endDate)
}

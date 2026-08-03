package id.bangkumis.dontbroke.domain.usecase

import id.bangkumis.dontbroke.data.repository.AnalyticsRepository
import id.bangkumis.dontbroke.domain.model.MonthComparison
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Income against expense across [startDate, endDate). */
class GetIncomeVsExpenseUseCase @Inject constructor(
    private val analytics: AnalyticsRepository
) {
    operator fun invoke(startDate: Long, endDate: Long): Flow<MonthComparison> =
        analytics.comparison(startDate, endDate)
}

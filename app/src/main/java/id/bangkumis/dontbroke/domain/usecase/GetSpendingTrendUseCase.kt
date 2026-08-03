package id.bangkumis.dontbroke.domain.usecase

import id.bangkumis.dontbroke.data.repository.AnalyticsRepository
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.DailyTrend
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/**
 * Spend per bucket across [startDate, endDate) — hourly for a day, daily for a
 * week or month, weekly for a year. Was GetWeeklyTrendUseCase, before the charts
 * gained a timeframe selector.
 */
class GetSpendingTrendUseCase @Inject constructor(
    private val analytics: AnalyticsRepository
) {
    operator fun invoke(
        frame: AnalyticsTimeFrame,
        startDate: Long,
        endDate: Long
    ): Flow<List<DailyTrend>> = analytics.spendingTrend(frame, startDate, endDate)
}

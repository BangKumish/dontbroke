package id.bangkumis.dontbroke.domain.model

/** One slice of the month's spending. [percentage] is 0..100 of that month's total. */
data class CategoryExpense(
    val categoryName: String,
    val totalAmount: Double,
    val percentage: Float
)

/** One bar of the weekly trend. Always seven of these, Monday first. */
data class DailyTrend(
    val dayName: String,
    val totalSpent: Double
)

/** Income against expense for one month. Zeroed, never absent, on an empty table. */
data class MonthComparison(
    val income: Double,
    val expense: Double
) {
    val net: Double get() = income - expense
}

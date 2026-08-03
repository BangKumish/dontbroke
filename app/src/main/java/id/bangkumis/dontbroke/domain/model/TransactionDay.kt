package id.bangkumis.dontbroke.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val dayStamp = SimpleDateFormat("d MMM yyyy", Locale("id", "ID"))

/** One date's transactions, in the order the query returned them. */
data class TransactionDay(val dayStart: Long, val transactions: List<Transaction>)

/**
 * Groups an already-sorted (newest first) list into days. Keeps the incoming
 * order within each day, so the SQL ordering stays the single source of truth.
 */
fun groupByDay(transactions: List<Transaction>): List<TransactionDay> =
    transactions
        .groupBy { dayStart(it.timestamp) }
        .map { (day, rows) -> TransactionDay(day, rows) }
        .sortedByDescending { it.dayStart }

/**
 * What a day header says. The two most recent days get names, because that is how
 * people refer to them; anything older gets a date.
 *
 * [today] is passed in rather than read from the clock, so the label is a pure
 * function of its inputs and a list cannot relabel itself mid-scroll. Yesterday
 * is walked back through Calendar rather than by subtracting 24h — that is exact
 * even where a day is not 24 hours long.
 */
fun dayLabel(day: Long, today: Long): String {
    val yesterday = Calendar.getInstance().apply {
        timeInMillis = today
        add(Calendar.DAY_OF_MONTH, -1)
    }.timeInMillis
    return when (day) {
        today -> "Hari Ini"
        yesterday -> "Kemarin"
        else -> dayStamp.format(Date(day))
    }
}

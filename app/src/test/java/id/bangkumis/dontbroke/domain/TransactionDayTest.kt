package id.bangkumis.dontbroke.domain

import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.domain.model.Transaction
import id.bangkumis.dontbroke.domain.model.dayLabel
import id.bangkumis.dontbroke.domain.model.dayStart
import id.bangkumis.dontbroke.domain.model.groupByDay
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The dashboard feed and the history screen both read their days from here, so a
 * grouping or naming slip shows up on two screens at once. Asia/Jakarta is fixed
 * because "which day is this" is a local-time question.
 */
class TransactionDayTest {

    private lateinit var original: TimeZone

    @Before
    fun pinZone() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Jakarta"))
    }

    @After
    fun restoreZone() {
        TimeZone.setDefault(original)
    }

    private val parser get() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("Asia/Jakarta") }

    private fun at(text: String): Long = parser.parse(text)!!.time

    private var nextId = 1L
    private fun txn(timestamp: Long, amount: Long = 1_000, type: TransactionType = TransactionType.EXPENSE) =
        Transaction(
            id = nextId++,
            amount = amount,
            type = type,
            category = "Food",
            note = "",
            sourceOrAccount = "Cash",
            location = null,
            timestamp = timestamp
        )

    @Test
    fun `transactions on the same date land in one group`() {
        val rows = listOf(
            txn(at("2026-08-01 23:59")),
            txn(at("2026-08-01 00:00")),
            txn(at("2026-07-31 12:00"))
        )
        val days = groupByDay(rows)
        assertEquals(2, days.size)
        assertEquals(2, days[0].transactions.size)
        assertEquals(1, days[1].transactions.size)
    }

    @Test
    fun `days come back newest first whatever order they arrived in`() {
        val rows = listOf(
            txn(at("2026-07-29 10:00")),
            txn(at("2026-08-01 10:00")),
            txn(at("2026-07-31 10:00"))
        )
        val days = groupByDay(rows)
        assertEquals(
            listOf(at("2026-08-01 00:00"), at("2026-07-31 00:00"), at("2026-07-29 00:00")),
            days.map { it.dayStart }
        )
    }

    /** The SQL ordering is the single source of truth — grouping must not re-sort within a day. */
    @Test
    fun `a day keeps the order its rows arrived in`() {
        val rows = listOf(
            txn(at("2026-08-01 08:00")),
            txn(at("2026-08-01 20:00")),
            txn(at("2026-08-01 13:00"))
        )
        assertEquals(rows.map { it.id }, groupByDay(rows).single().transactions.map { it.id })
    }

    @Test
    fun `an empty feed groups into no days`() {
        assertEquals(emptyList<Long>(), groupByDay(emptyList()).map { it.dayStart })
    }

    @Test
    fun `the two most recent days are named and the rest are dated`() {
        val today = dayStart(at("2026-08-01 09:00"))
        assertEquals("Hari Ini", dayLabel(today, today))
        assertEquals("Kemarin", dayLabel(dayStart(at("2026-07-31 09:00")), today))
        // id-ID abbreviates July to "Jul"; August would be "Agu", not "Aug"
        assertEquals("28 Jul 2026", dayLabel(dayStart(at("2026-07-28 09:00")), today))
    }

    /** Yesterday walks back through Calendar, so a month boundary is not a special case. */
    @Test
    fun `yesterday is still yesterday across a month boundary`() {
        val today = dayStart(at("2026-08-01 09:00"))
        assertEquals("Kemarin", dayLabel(dayStart(at("2026-07-31 23:00")), today))
    }

    @Test
    fun `a future day is dated rather than named`() {
        val today = dayStart(at("2026-08-01 09:00"))
        assertEquals("2 Agu 2026", dayLabel(dayStart(at("2026-08-02 09:00")), today))
    }
}

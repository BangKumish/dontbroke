package id.bangkumis.dontbroke.presentation.addtransaction

import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The M3 DatePicker hands back UTC-midnight millis while the DB stores local
 * wall-clock millis. Getting this backwards shifts dates by a day — check both
 * a positive (WIB) and a negative (New York) offset.
 */
class DatePickerMillisTest {

    private fun utcMidnight(year: Int, month: Int, day: Int): Long =
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month, day)
        }.timeInMillis

    private fun localDay(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(millis))

    private fun withZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        try { block() } finally { TimeZone.setDefault(original) }
    }

    @Test
    fun `picked date keeps its calendar day in local time`() {
        listOf("Asia/Jakarta", "America/New_York").forEach { zone ->
            withZone(zone) {
                val stored = utcMidnight(2026, Calendar.JULY, 28).fromPickerMillis()
                assertEquals("wrong day in $zone", "2026-07-28", localDay(stored))
            }
        }
    }

    @Test
    fun `stored date round-trips through the picker`() {
        listOf("Asia/Jakarta", "America/New_York").forEach { zone ->
            withZone(zone) {
                val stored = utcMidnight(2026, Calendar.JULY, 28).fromPickerMillis()
                assertEquals(stored, stored.toPickerMillis().fromPickerMillis())
            }
        }
    }
}

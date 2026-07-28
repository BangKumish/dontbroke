package id.bangkumis.dontbroke.presentation.home

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The spend counters are only as honest as their boundaries — an off-by-one day
 * silently moves someone's spending into the wrong bucket. Asia/Jakarta is fixed
 * here so the test does not depend on the machine's zone.
 */
class SpendWindowTest {

    private val jakarta = TimeZone.getTimeZone("Asia/Jakarta")
    private val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    private lateinit var original: TimeZone

    @Before
    fun setUp() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(jakarta)
        fmt.timeZone = jakarta
    }

    @After
    fun tearDown() = TimeZone.setDefault(original)

    private fun at(text: String): Long = fmt.parse(text)!!.time
    private fun Pair<Long, Long>.render() = fmt.format(Date(first)) to fmt.format(Date(second))

    private fun dayOfWeek(millis: Long) =
        Calendar.getInstance(jakarta).apply { timeInMillis = millis }.get(Calendar.DAY_OF_WEEK)

    @Test
    fun `today runs from local midnight to the next midnight`() {
        assertEquals(
            "2026-07-15 00:00" to "2026-07-16 00:00",
            todayWindow(at("2026-07-15 14:30")).render()
        )
    }

    @Test
    fun `a late-evening transaction still counts as today`() {
        val (start, end) = todayWindow(at("2026-07-15 23:59"))
        assertEquals("2026-07-15 00:00", fmt.format(Date(start)))
        assertEquals("2026-07-16 00:00", fmt.format(Date(end)))
    }

    @Test
    fun `midweek resolves to Monday through the following Monday`() {
        // 2026-07-15 is a Wednesday
        assertEquals(
            "2026-07-13 00:00" to "2026-07-20 00:00",
            weekWindow(at("2026-07-15 09:00")).render()
        )
    }

    @Test
    fun `Sunday belongs to the week that started six days earlier`() {
        // Sunday closes the week, it does not open one
        assertEquals(
            "2026-07-13 00:00" to "2026-07-20 00:00",
            weekWindow(at("2026-07-19 21:00")).render()
        )
    }

    @Test
    fun `Monday itself is the start of its own week`() {
        assertEquals(
            "2026-07-13 00:00" to "2026-07-20 00:00",
            weekWindow(at("2026-07-13 00:00")).render()
        )
    }

    @Test
    fun `every week starts on a Monday whatever day is asked`() {
        repeat(14) { offset ->
            val now = at("2026-07-13 12:00") + offset * 86_400_000L
            assertEquals(
                "day offset $offset",
                Calendar.MONDAY,
                dayOfWeek(weekWindow(now).first)
            )
        }
    }

    @Test
    fun `the month runs from the first to the first of the next`() {
        assertEquals(
            "2026-07-01 00:00" to "2026-08-01 00:00",
            monthWindow(at("2026-07-15 09:00")).render()
        )
    }

    @Test
    fun `December rolls over into the next year`() {
        assertEquals(
            "2026-12-01 00:00" to "2027-01-01 00:00",
            monthWindow(at("2026-12-20 09:00")).render()
        )
    }

    @Test
    fun `windows are half-open so a midnight transaction lands in one bucket only`() {
        val boundary = at("2026-07-16 00:00")
        val (_, endOfThe15th) = todayWindow(at("2026-07-15 10:00"))
        val (startOfThe16th, _) = todayWindow(boundary)
        assertEquals("the boundary must be shared, not overlapped", endOfThe15th, startOfThe16th)
        assertEquals(boundary, startOfThe16th)
    }
}

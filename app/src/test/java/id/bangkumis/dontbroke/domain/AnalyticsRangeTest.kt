package id.bangkumis.dontbroke.domain

import id.bangkumis.dontbroke.domain.model.AnalyticsRange
import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The stepper moves money windows around; an off-by-one period shows someone the
 * wrong month's spending and looks entirely plausible. Asia/Jakarta is fixed so
 * these assertions do not depend on the machine's zone.
 */
class AnalyticsRangeTest {

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
    private fun Long.render() = fmt.format(Date(this))
    private fun Pair<Long, Long>.render() = first.render() to second.render()

    private fun range(frame: AnalyticsTimeFrame, at: String) =
        AnalyticsRange(frame = frame, anchor = at(at))

    // --- stepping -------------------------------------------------------

    @Test
    fun `stepping back a day moves the window to yesterday`() {
        val today = range(AnalyticsTimeFrame.TODAY, "2026-07-15 14:30")
        assertEquals(
            "2026-07-14 00:00" to "2026-07-15 00:00",
            today.stepped(-1).window.render()
        )
    }

    @Test
    fun `stepping back twice reaches two days ago`() {
        val today = range(AnalyticsTimeFrame.TODAY, "2026-07-15 14:30")
        assertEquals("2026-07-13 00:00", today.stepped(-1).stepped(-1).window.first.render())
    }

    @Test
    fun `stepping back a week lands on the previous Monday`() {
        val week = range(AnalyticsTimeFrame.THIS_WEEK, "2026-07-15 09:00") // a Wednesday
        assertEquals(
            "2026-07-06 00:00" to "2026-07-13 00:00",
            week.stepped(-1).window.render()
        )
    }

    @Test
    fun `stepping back a month lands on the whole previous month`() {
        val month = range(AnalyticsTimeFrame.THIS_MONTH, "2026-07-15 09:00")
        assertEquals(
            "2026-06-01 00:00" to "2026-07-01 00:00",
            month.stepped(-1).window.render()
        )
    }

    @Test
    fun `stepping back a year lands on the whole previous year`() {
        val year = range(AnalyticsTimeFrame.THIS_YEAR, "2026-07-15 09:00")
        assertEquals(
            "2025-01-01 00:00" to "2026-01-01 00:00",
            year.stepped(-1).window.render()
        )
    }

    /**
     * Calendar clamps 31 March minus a month to 28 February. Normalising the
     * anchor to the window start is what stops that from leaking a day.
     */
    @Test
    fun `a month step from the 31st does not drift when stepped back`() {
        val march = range(AnalyticsTimeFrame.THIS_MONTH, "2026-03-31 12:00")
        val back = march.stepped(-1).stepped(-1)
        assertEquals("2026-01-01 00:00", back.window.first.render())
        assertEquals("back then forward returns where it began", "2026-03-01 00:00",
            back.stepped(1).stepped(1).window.first.render())
    }

    @Test
    fun `stepping is a no-op on a custom range`() {
        val custom = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = at("2026-08-10 00:00"),
            customEnd = at("2026-08-14 00:00")
        )
        assertEquals(custom.window, custom.stepped(-1).window)
    }

    // --- the forward stop -----------------------------------------------

    @Test
    fun `the next arrow is off once the window already holds now`() {
        val now = at("2026-07-15 14:30")
        assertFalse(AnalyticsRange(AnalyticsTimeFrame.TODAY, now).canStepForward(now))
        assertFalse(AnalyticsRange(AnalyticsTimeFrame.THIS_WEEK, now).canStepForward(now))
        assertFalse(AnalyticsRange(AnalyticsTimeFrame.THIS_MONTH, now).canStepForward(now))
        assertFalse(AnalyticsRange(AnalyticsTimeFrame.THIS_YEAR, now).canStepForward(now))
    }

    @Test
    fun `the next arrow comes back on as soon as the window is in the past`() {
        val now = at("2026-07-15 14:30")
        assertTrue(AnalyticsRange(AnalyticsTimeFrame.TODAY, now).stepped(-1).canStepForward(now))
        assertTrue(AnalyticsRange(AnalyticsTimeFrame.THIS_MONTH, now).stepped(-1).canStepForward(now))
    }

    @Test
    fun `a custom range has no arrows at all`() {
        val custom = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = at("2026-01-01 00:00"),
            customEnd = at("2026-01-05 00:00")
        )
        assertFalse(custom.canStepBack)
        assertFalse(custom.canStepForward(at("2026-07-15 14:30")))
    }

    // --- custom bounds --------------------------------------------------

    /** The picked end day must be included whole — half-open, so it closes at the next midnight. */
    @Test
    fun `a custom range covers both picked days end to end`() {
        val custom = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = at("2026-08-10 09:15"),
            customEnd = at("2026-08-14 21:40")
        )
        assertEquals(
            "2026-08-10 00:00" to "2026-08-15 00:00",
            custom.window.render()
        )
    }

    @Test
    fun `a single picked date is one whole day`() {
        val day = at("2026-08-10 13:00")
        val custom = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = day,
            customEnd = day
        )
        assertEquals("2026-08-10 00:00" to "2026-08-11 00:00", custom.window.render())
        assertEquals(1, AnalyticsTimeFrame.CUSTOM.bucketCount(custom.window.first, custom.window.second))
    }

    /** Nothing picked yet must still draw something rather than a zero-width window. */
    @Test
    fun `a custom frame with no dates falls back to today`() {
        val custom = AnalyticsRange(frame = AnalyticsTimeFrame.CUSTOM, anchor = at("2026-07-15 14:30"))
        assertEquals("2026-07-15 00:00" to "2026-07-16 00:00", custom.window.render())
    }

    // --- labels ---------------------------------------------------------

    @Test
    fun `each frame names its window the way the stepper reads it`() {
        val now = at("2026-07-15 09:00")
        assertEquals("15 Jul 2026", AnalyticsRange(AnalyticsTimeFrame.TODAY, now).label)
        assertEquals("13 Jul - 19 Jul 2026", AnalyticsRange(AnalyticsTimeFrame.THIS_WEEK, now).label)
        assertEquals("2026", AnalyticsRange(AnalyticsTimeFrame.THIS_YEAR, now).label)
    }

    /** The week label must end on Sunday, not on the exclusive Monday bound. */
    @Test
    fun `a week label ends on its last included day`() {
        val week = AnalyticsRange(AnalyticsTimeFrame.THIS_WEEK, at("2026-07-15 09:00"))
        assertTrue("ends on Sunday the 19th, not Monday the 20th", week.label.endsWith("19 Jul 2026"))
    }

    /** Labels are formatted in id-ID, where August abbreviates to "Agu", not "Aug". */
    @Test
    fun `a custom span reads as a range and a single day reads as a date`() {
        val span = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = at("2026-08-10 00:00"),
            customEnd = at("2026-08-14 00:00")
        )
        assertEquals("10 Agu - 14 Agu 2026", span.label)

        val one = AnalyticsRange(
            frame = AnalyticsTimeFrame.CUSTOM,
            customStart = at("2026-08-10 00:00"),
            customEnd = at("2026-08-10 00:00")
        )
        assertEquals("10 Agu 2026", one.label)
    }
}

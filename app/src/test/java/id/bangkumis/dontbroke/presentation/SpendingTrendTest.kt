package id.bangkumis.dontbroke.presentation

import id.bangkumis.dontbroke.domain.model.AnalyticsTimeFrame
import id.bangkumis.dontbroke.domain.model.DailyTrend
import id.bangkumis.dontbroke.presentation.components.axisTicks
import id.bangkumis.dontbroke.presentation.components.barAt
import id.bangkumis.dontbroke.presentation.home.SpendingTrendUiState
import id.bangkumis.dontbroke.presentation.home.spendingTrendState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

class SpendingTrendTest {

    // --- tap math -------------------------------------------------------

    /** 700px over 7 bars puts each bar in its own 100px slot. */
    @Test
    fun `a tap picks the bar whose slot it fell in`() {
        assertEquals(0, barAt(0f, 700f, 7))
        assertEquals(0, barAt(99f, 700f, 7))
        assertEquals(1, barAt(100f, 700f, 7))
        assertEquals(3, barAt(350f, 700f, 7))
        assertEquals(6, barAt(699f, 700f, 7))
    }

    /** The same width now has to divide into 24 hourly slots instead of 7 days. */
    @Test
    fun `the bar count, not a constant seven, decides the slot width`() {
        assertEquals(0, barAt(10f, 720f, 24))
        assertEquals(1, barAt(30f, 720f, 24))
        assertEquals(12, barAt(360f, 720f, 24))
        assertEquals(23, barAt(719f, 720f, 24))
    }

    @Test
    fun `taps outside the plot select nothing`() {
        assertNull(barAt(-1f, 700f, 7))
        assertNull(barAt(700f, 700f, 7))
        assertNull(barAt(900f, 700f, 7))
    }

    @Test
    fun `a zero-width or barless plot cannot be hit`() {
        assertNull(barAt(0f, 0f, 7))
        assertNull(barAt(10f, 700f, 0))
    }

    // --- axis ticks -----------------------------------------------------

    /** Every bar is accounted for, whatever the frame — spans must sum to the bar count. */
    @Test
    fun `ticks always span exactly the bars they label`() {
        AnalyticsTimeFrame.entries.forEach { frame ->
            val bars = frame.bucketCount(0, 30 * DAY)
            val ticks = axisTicks(frame, 0, bars)
            assertEquals("$frame", bars, ticks.sumOf { it.second })
        }
    }

    @Test
    fun `a week labels every bar with its own initial`() {
        val ticks = axisTicks(AnalyticsTimeFrame.THIS_WEEK, 0, 7)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), ticks.map { it.first })
        assertTrue("no merging when every bar has a label", ticks.all { it.second == 1 })
    }

    /** 31 days would crush 31 labels together, so blanks are folded into the week before. */
    @Test
    fun `a month labels every seventh day and swallows the blanks between`() {
        val ticks = axisTicks(AnalyticsTimeFrame.THIS_MONTH, 0, 31)
        assertEquals(listOf("1", "8", "15", "22", "29"), ticks.map { it.first })
        assertEquals(listOf(7, 7, 7, 7, 3), ticks.map { it.second })
    }

    @Test
    fun `a day labels every sixth hour`() {
        val ticks = axisTicks(AnalyticsTimeFrame.TODAY, 0, 24)
        assertEquals(listOf("00", "06", "12", "18"), ticks.map { it.first })
        assertEquals(listOf(6, 6, 6, 6), ticks.map { it.second })
    }

    // --- window shape ---------------------------------------------------

    @Test
    fun `each frame slices its own window into the bars it expects`() {
        assertEquals(24, AnalyticsTimeFrame.TODAY.bucketCount(0, 24 * HOUR))
        assertEquals(7, AnalyticsTimeFrame.THIS_WEEK.bucketCount(0, 7 * DAY))
        assertEquals(31, AnalyticsTimeFrame.THIS_MONTH.bucketCount(0, 31 * DAY))
        assertEquals(53, AnalyticsTimeFrame.THIS_YEAR.bucketCount(0, 366 * DAY))
    }

    @Test
    fun `a partial trailing bar still counts as a bar`() {
        assertEquals("28 days plus an hour is 29 bars", 29, AnalyticsTimeFrame.THIS_MONTH.bucketCount(0, 28 * DAY + HOUR))
        assertEquals("a window smaller than one bar is still one bar", 1, AnalyticsTimeFrame.THIS_WEEK.bucketCount(0, 1))
    }

    @Test
    fun `now maps to the bar containing it`() {
        val frame = AnalyticsTimeFrame.THIS_MONTH
        assertEquals(0, frame.currentBucket(0, 0, 31))
        assertEquals(0, frame.currentBucket(DAY - 1, 0, 31))
        assertEquals(1, frame.currentBucket(DAY, 0, 31))
        assertEquals(14, frame.currentBucket(14 * DAY + HOUR, 0, 31))
    }

    @Test
    fun `an instant outside the window has no current bar`() {
        val frame = AnalyticsTimeFrame.THIS_MONTH
        assertEquals("before the window", -1, frame.currentBucket(-1, 0, 31))
        assertEquals("past the last bar", -1, frame.currentBucket(31 * DAY, 0, 31))
    }

    // --- Empty vs Success ----------------------------------------------

    private fun week(vararg spend: Double) =
        spend.mapIndexed { i, amount -> DailyTrend("D$i", amount) }

    private fun state(daily: List<DailyTrend>) =
        spendingTrendState(daily, AnalyticsTimeFrame.THIS_WEEK, windowStart = 0, currentIndex = 3)

    /** Empty keeps its shape, so a spendless week still draws seven stubs and an axis. */
    @Test
    fun `a week with nothing spent is Empty but still knows it has seven bars`() {
        val result = state(week(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0))
        assertTrue(result is SpendingTrendUiState.Empty)
        result as SpendingTrendUiState.Empty
        assertEquals(7, result.bucketCount)
        assertEquals(3, result.currentIndex)
        assertEquals(AnalyticsTimeFrame.THIS_WEEK, result.frame)
    }

    @Test
    fun `no bars at all is Empty with one stub rather than a divide by zero`() {
        val result = state(emptyList())
        assertTrue(result is SpendingTrendUiState.Empty)
        assertEquals(1, (result as SpendingTrendUiState.Empty).bucketCount)
    }

    @Test
    fun `one spending day carries the peak and the total`() {
        val result = state(week(0.0, 15_000.0, 0.0, 45_000.0, 0.0, 0.0, 5_000.0))
        assertTrue(result is SpendingTrendUiState.Success)
        result as SpendingTrendUiState.Success
        assertEquals(45_000.0, result.maxDaySpent, 0.0)
        assertEquals(65_000.0, result.totalWeekSpent, 0.0)
        assertEquals(7, result.dailyTrends.size)
        assertEquals(3, result.currentIndex)
    }

}

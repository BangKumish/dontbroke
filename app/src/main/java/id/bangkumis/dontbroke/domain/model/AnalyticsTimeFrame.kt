package id.bangkumis.dontbroke.domain.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val HOUR = 3_600_000L
private const val DAY = 86_400_000L

/** Monday first — every window this file produces for a week starts on a Monday. */
private val WEEK_INITIALS = listOf("M", "T", "W", "T", "F", "S", "S")
private val WEEK_NAMES =
    listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

private fun midnight(now: Long): Calendar = Calendar.getInstance().apply {
    timeInMillis = now
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

/** Local midnight opening the day that contains [millis] — the day-grouping key. */
fun dayStart(millis: Long): Long = midnight(millis).timeInMillis

/** Local calendar day containing [now], as a half-open [start, end) range. */
fun todayWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 1)
    return start to cal.timeInMillis
}

/** Monday 00:00 through Sunday, ending at the next Monday 00:00. */
fun weekWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    // walk back explicitly — Calendar.firstDayOfWeek varies by locale
    while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) cal.add(Calendar.DAY_OF_MONTH, -1)
    val start = cal.timeInMillis
    cal.add(Calendar.DAY_OF_MONTH, 7)
    return start to cal.timeInMillis
}

/** 1st of this month 00:00 up to the 1st of next month. */
fun monthWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    cal.set(Calendar.DAY_OF_MONTH, 1)
    val start = cal.timeInMillis
    cal.add(Calendar.MONTH, 1)
    return start to cal.timeInMillis
}

/** 1 January 00:00 up to the 1st of next January. */
fun yearWindow(now: Long): Pair<Long, Long> {
    val cal = midnight(now)
    cal.set(Calendar.DAY_OF_YEAR, 1)
    val start = cal.timeInMillis
    cal.add(Calendar.YEAR, 1)
    return start to cal.timeInMillis
}

private fun stamp(pattern: String, millis: Long) =
    SimpleDateFormat(pattern, Locale("id", "ID")).format(Date(millis))

/**
 * The period every analytics chart is scoped to. Each frame owns both its window
 * and the width of one trend bar, so a chart never has to guess how to slice the
 * range it was handed.
 *
 * ponytail: bars are fixed-width milliseconds, which is exact in Indonesia
 * (WIB/WITA/WIT have no DST) and keeps the SQL to one integer division. A year is
 * therefore 52–53 weekly bars rather than 12 calendar months; switch the query to
 * strftime('%m') if month bars are ever wanted.
 */
enum class AnalyticsTimeFrame(
    /** Chip text. */
    val label: String,
    /** Card-header text. */
    val title: String,
    val bucketMs: Long
) {
    TODAY("Day", "Today", HOUR),
    THIS_WEEK("Week", "This Week", DAY),
    THIS_MONTH("Month", "This Month", DAY),
    THIS_YEAR("Year", "This Year", 7 * DAY),

    /**
     * A hand-picked range. Its window comes from [AnalyticsRange], not from an
     * anchor — [window] only answers with today so a CUSTOM frame with no dates
     * chosen yet still draws something.
     *
     * ponytail: custom ranges always bucket by day, so a single-date pick is one
     * bar rather than 24 hourly ones. Stepping the Day frame back already covers
     * hour-by-hour inspection; make bucketMs range-dependent if that changes.
     */
    CUSTOM("Custom", "Custom Range", DAY);

    /**
     * The window containing [at] — pass the anchor, not the clock, and the same
     * four rules give you any day/week/month/year in history.
     */
    fun window(at: Long): Pair<Long, Long> = when (this) {
        TODAY, CUSTOM -> todayWindow(at)
        THIS_WEEK -> weekWindow(at)
        THIS_MONTH -> monthWindow(at)
        THIS_YEAR -> yearWindow(at)
    }

    /**
     * Anchor moved [delta] whole periods, normalised back to its window start.
     * Normalising matters: Calendar clamps 31 Mar minus a month to 28 Feb, and
     * without it a few steps back and forward would not return where they began.
     */
    fun step(anchor: Long, delta: Int): Long {
        if (this == CUSTOM) return anchor
        val cal = Calendar.getInstance().apply { timeInMillis = anchor }
        when (this) {
            TODAY -> cal.add(Calendar.DAY_OF_MONTH, delta)
            THIS_WEEK -> cal.add(Calendar.WEEK_OF_YEAR, delta)
            THIS_MONTH -> cal.add(Calendar.MONTH, delta)
            THIS_YEAR -> cal.add(Calendar.YEAR, delta)
            CUSTOM -> Unit
        }
        return window(cal.timeInMillis).first
    }

    /** What sits between the `<` and `>` arrows. [until] is exclusive. */
    fun rangeLabel(from: Long, until: Long): String = when (this) {
        TODAY -> stamp("d MMM yyyy", from)
        THIS_MONTH -> stamp("MMMM yyyy", from)
        THIS_YEAR -> stamp("yyyy", from)
        // until is exclusive, so the last *included* millisecond names the end
        THIS_WEEK -> "${stamp("d MMM", from)} - ${stamp("d MMM yyyy", until - 1)}"
        CUSTOM ->
            if (until - from <= DAY) stamp("d MMM yyyy", from)
            else "${stamp("d MMM", from)} - ${stamp("d MMM yyyy", until - 1)}"
    }

    /** How many bars [from, until) divides into. A trailing partial bar still counts. */
    fun bucketCount(from: Long, until: Long): Int =
        (((until - from) + bucketMs - 1) / bucketMs).toInt().coerceAtLeast(1)

    fun bucketStart(index: Int, from: Long): Long = from + index * bucketMs

    /** Which bar holds [now], or -1 when [now] falls outside the window entirely. */
    fun currentBucket(now: Long, from: Long, count: Int): Int {
        if (now < from) return -1
        val index = ((now - from) / bucketMs).toInt()
        return if (index >= count) -1 else index
    }

    /** Readout name for one bar — what the chart header says when it is selected. */
    fun bucketName(index: Int, from: Long): String = when (this) {
        TODAY -> "%02d:00".format(Locale.US, index)
        THIS_WEEK -> WEEK_NAMES[index % 7]
        THIS_MONTH, CUSTOM -> stamp("d MMM", bucketStart(index, from))
        THIS_YEAR -> "Week of ${stamp("d MMM", bucketStart(index, from))}"
    }

    /**
     * Short axis tick, blank where a label would collide with its neighbours. The
     * blanks are meaningful: the chart merges each run of them into the preceding
     * label's segment so the text has room to render.
     *
     * [bars] is only consulted by CUSTOM, whose length is not known in advance —
     * a five-day range labels every bar, a thirty-day one labels every week.
     */
    fun bucketLabel(index: Int, from: Long, bars: Int = 0): String = when (this) {
        TODAY -> if (index % 6 == 0) "%02d".format(Locale.US, index) else ""
        THIS_WEEK -> WEEK_INITIALS[index % 7]
        THIS_MONTH -> if (index % 7 == 0) "${index + 1}" else ""
        CUSTOM -> {
            val every = if (bars <= 10) 1 else 7
            if (index % every == 0) stamp("d", bucketStart(index, from)) else ""
        }
        // only the first bar of each month is labelled, so a year reads as months
        THIS_YEAR -> {
            val month = stamp("MMM", bucketStart(index, from))
            if (index > 0 && month == stamp("MMM", bucketStart(index - 1, from))) "" else month
        }
    }
}

/**
 * Which slice of history the analytics cards are looking at: a frame plus the
 * anchor it is measured from, so `<` and `>` are nothing more than moving the
 * anchor. CUSTOM ignores the anchor and uses the picked dates instead.
 *
 * [customEnd] is the last day the user picked, inclusive — the window closes at
 * the following midnight, which keeps every range in this app half-open. (A
 * literal 23:59:59 bound would drop the final second of the day.)
 */
data class AnalyticsRange(
    val frame: AnalyticsTimeFrame = AnalyticsTimeFrame.THIS_MONTH,
    val anchor: Long = 0L,
    val customStart: Long = 0L,
    val customEnd: Long = 0L
) {
    val window: Pair<Long, Long>
        get() =
            if (frame == AnalyticsTimeFrame.CUSTOM && customEnd > 0L)
                dayStart(customStart) to dayStart(customEnd) + DAY
            else frame.window(anchor)

    val label: String get() = frame.rangeLabel(window.first, window.second)

    /** A picked range has no natural next or previous — re-pick instead. */
    val canStepBack: Boolean get() = frame != AnalyticsTimeFrame.CUSTOM

    /** Off once the window already contains [now]: there is no future to show. */
    fun canStepForward(now: Long): Boolean =
        frame != AnalyticsTimeFrame.CUSTOM && window.second <= now

    fun stepped(delta: Int): AnalyticsRange = copy(anchor = frame.step(anchor, delta))
}

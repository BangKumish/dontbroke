package id.bangkumis.dontbroke.presentation.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The insight card used to refetch on every home-screen load. These pin the
 * boundary of the one-hour window that replaced that.
 */
class InsightCacheTest {

    private val now = 1_700_000_000_000L

    @Test fun justFetchedIsFresh() {
        assertTrue(isInsightFresh(now, now))
    }

    @Test fun underAnHourIsFresh() {
        assertTrue(isInsightFresh(now - INSIGHT_TTL_MS + 1, now))
    }

    /** Half-open: exactly one hour old is stale, matching every other window here. */
    @Test fun exactlyAnHourIsStale() {
        assertFalse(isInsightFresh(now - INSIGHT_TTL_MS, now))
    }

    @Test fun olderThanAnHourIsStale() {
        assertFalse(isInsightFresh(now - INSIGHT_TTL_MS * 3, now))
    }

    /** A cache stamped in the future means the clock moved back — refetch, don't trust it. */
    @Test fun futureTimestampIsStale() {
        assertFalse(isInsightFresh(now + 60_000, now))
    }

    @Test fun ttlIsOneHour() {
        assertTrue(INSIGHT_TTL_MS == 3_600_000L)
    }
}

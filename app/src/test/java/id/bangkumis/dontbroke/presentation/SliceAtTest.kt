package id.bangkumis.dontbroke.presentation

import id.bangkumis.dontbroke.presentation.components.sliceAt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Ring hit-testing. A wrong angle offset still selects *a* slice, so the chart
 * would look alive while reporting the wrong category — assert the mapping.
 * Centre (100,100), ring from r=40 to r=100, slices 90° / 90° / 180°.
 */
class SliceAtTest {

    private val sweeps = listOf(90f, 90f, 180f)

    private fun at(x: Float, y: Float) =
        sliceAt(x, y, 100f, 100f, innerRadius = 40f, outerRadius = 100f, sweeps = sweeps)

    @Test
    fun `slices are walked clockwise from twelve o'clock`() {
        assertEquals(0, at(100f, 30f))   // straight up, first slice starts here
        assertEquals(0, at(170f, 90f))   // just before 3 o'clock
        assertEquals(1, at(170f, 110f))  // just after 3 o'clock
        assertEquals(2, at(100f, 170f))  // straight down
    }

    @Test
    fun `the wrap back to twelve o'clock lands in the last slice`() {
        assertEquals(2, at(95f, 31f))
    }

    @Test
    fun `taps in the hole or outside the ring select nothing`() {
        assertNull(at(100f, 100f))
        assertNull(at(110f, 110f))
        assertNull(at(100f, 250f))
    }

    @Test
    fun `an empty ring has nothing to hit`() {
        assertNull(sliceAt(100f, 30f, 100f, 100f, 40f, 100f, emptyList()))
    }
}

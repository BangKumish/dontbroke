package id.bangkumis.dontbroke.presentation.scan

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the reason `ReceiptCameraScreen` and the two API call sites all rethrow
 * cancellation explicitly: it is an ordinary [Exception] as far as `catch` and
 * `runCatching` are concerned, so a generic handler reports the normal exit as a
 * failure. That is what put "Gagal membuka kamera: The coroutine scope left the
 * composition" on screen after a scan that had just succeeded.
 */
class CancellationIsNotFailureTest {

    @Test
    fun `cancellation is an ordinary Exception on the JVM`() {
        val e: Throwable = CancellationException("The coroutine scope left the composition")
        // If this ever fails, the language changed and the explicit rethrows can go.
        assertTrue("kotlinx CancellationException must still be an Exception", e is Exception)
        assertTrue("…and specifically an IllegalStateException", e is IllegalStateException)
    }

    @Test
    fun `a bare catch reports cancellation as a camera failure`() {
        var reported: String? = null
        try {
            throw CancellationException("The coroutine scope left the composition")
        } catch (e: Exception) {
            reported = "Gagal membuka kamera: ${e.message}"
        }
        // The bug, pinned: the old code took this branch on every successful scan.
        assertEquals(
            "Gagal membuka kamera: The coroutine scope left the composition",
            reported
        )
    }

    @Test
    fun `rethrowing cancellation first leaves the failure branch untaken`() {
        var reported: String? = null
        val thrown = runCatching {
            try {
                throw CancellationException("The coroutine scope left the composition")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reported = "Gagal membuka kamera: ${e.message}"
            }
        }.exceptionOrNull()

        assertEquals("no failure may be reported for a normal exit", null, reported)
        assertTrue("cancellation must propagate", thrown is CancellationException)
    }

    @Test
    fun `runCatching swallows cancellation unless it is rethrown`() {
        // The ScanReceiptUseCase / HomeViewModel shape: getOrElse sees cancellation
        // like any other throwable, so both now check before building a message.
        val swallowed = runCatching { throw CancellationException("cancelled") }
            .getOrElse { "Gagal memindai: ${it.message}" }
        assertEquals("Gagal memindai: cancelled", swallowed)

        val rethrown = runCatching {
            runCatching { throw CancellationException("cancelled") }
                .getOrElse { e ->
                    if (e is CancellationException) throw e
                    "Gagal memindai: ${e.message}"
                }
        }.exceptionOrNull()
        assertTrue(rethrown is CancellationException)
    }
}

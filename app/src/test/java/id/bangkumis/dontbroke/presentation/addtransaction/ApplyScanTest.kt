package id.bangkumis.dontbroke.presentation.addtransaction

import id.bangkumis.dontbroke.domain.usecase.ParsedReceiptResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A scan fills the form; it must never destroy what the user already typed. */
class ApplyScanTest {

    private val accounts = listOf("BCA", "ShopeePay")

    @Test
    fun `fills every field the scan found`() {
        val state = AddTransactionUiState(isScanning = true).applyScan(
            ParsedReceiptResult(15000.0, "Food & Beverage", "Bakmi GM", "ShopeePay"),
            accounts
        )
        assertEquals("15000", state.amount)
        assertEquals("Food & Beverage", state.category)
        assertEquals("Bakmi GM", state.location)
        assertEquals("ShopeePay", state.sourceOrAccount)
        assertFalse(state.isScanning)
        assertNull(state.scanMessage)
        assertTrue(state.canSave)
    }

    @Test
    fun `null fields keep what the user already entered`() {
        val typed = AddTransactionUiState(
            amount = "50000",
            category = "Shopping",
            location = "Indomaret",
            sourceOrAccount = "BCA"
        )
        val state = typed.applyScan(ParsedReceiptResult(), accounts)
        assertEquals("50000", state.amount)
        assertEquals("Shopping", state.category)
        assertEquals("Indomaret", state.location)
        assertEquals("BCA", state.sourceOrAccount)
    }

    /** Names join to accounts, so an unknown one must not be written in. */
    @Test
    fun `unknown account is reported, not filled`() {
        val state = AddTransactionUiState().applyScan(
            ParsedReceiptResult(9000.0, null, null, "GoPay"),
            accounts
        )
        assertEquals("", state.sourceOrAccount)
        assertTrue(state.scanMessage!!.contains("GoPay"))
        assertFalse(state.canSave) // amount alone is not saveable
    }

    @Test
    fun `amount is truncated to whole rupiah`() {
        assertEquals("15000", AddTransactionUiState().applyScan(
            ParsedReceiptResult(amount = 15000.75), accounts
        ).amount)
    }
}

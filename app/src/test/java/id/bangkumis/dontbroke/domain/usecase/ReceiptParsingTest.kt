package id.bangkumis.dontbroke.domain.usecase

import id.bangkumis.dontbroke.data.local.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The model's reply is untrusted text, so these pin the shapes it actually
 * returns: fenced JSON, prose around it, Rupiah formatting, Indonesian category
 * names, and account guesses that do not exist.
 */
class ReceiptParsingTest {

    @Test
    fun `parses a clean json object`() {
        val parsed = parseReceiptJson(
            """{"amount": 15000.0, "category": "Makanan & Minuman", "location": "Bakmi GM", "suggestedAccount": "ShopeePay"}"""
        )!!
        assertEquals(15000.0, parsed.amount!!, 0.001)
        assertEquals("Food & Beverage", parsed.category)
        assertEquals("Bakmi GM", parsed.location)
        assertEquals("ShopeePay", parsed.suggestedAccount)
    }

    @Test
    fun `strips code fences and surrounding prose`() {
        val reply = """
            Sure! Here is the extracted data:
            ```json
            {"amount": 42000, "category": "Transportasi", "location": null, "suggestedAccount": null}
            ```
            Let me know if you need anything else.
        """.trimIndent()
        val parsed = parseReceiptJson(reply)!!
        assertEquals(42000.0, parsed.amount!!, 0.001)
        assertEquals("Transportation", parsed.category)
        assertNull(parsed.location)
        assertNull(parsed.suggestedAccount)
    }

    @Test
    fun `nested braces do not truncate the object`() {
        val json = extractJsonObject("""noise {"a": {"b": 1}, "c": 2} tail""")
        assertEquals("""{"a": {"b": 1}, "c": 2}""", json)
    }

    @Test
    fun `brace inside a string does not end the object`() {
        val json = extractJsonObject("""{"location": "Warung }{ Mas", "amount": 1}""")
        assertEquals("""{"location": "Warung }{ Mas", "amount": 1}""", json)
    }

    @Test
    fun `no json at all is null, not an empty result`() {
        assertNull(parseReceiptJson("I cannot read this image."))
        assertNull(extractJsonObject("""{"unterminated": 1"""))
    }

    @Test
    fun `rupiah formatting survives`() {
        assertEquals(15000.0, parseAmount("Rp 15.000")!!, 0.001)
        assertEquals(1500000.0, parseAmount("1.500.000")!!, 0.001)
        assertEquals(15000.5, parseAmount("15.000,50")!!, 0.001)
        assertEquals(15000.0, parseAmount("15000.0")!!, 0.001)
    }

    @Test
    fun `unusable amounts are null`() {
        assertNull(parseAmount(null))
        assertNull(parseAmount("null"))
        assertNull(parseAmount("tidak terbaca"))
        assertNull(parseAmount("0"))
    }

    @Test
    fun `unknown category falls back to Other rather than leaking through`() {
        assertEquals("Other", canonicalCategory("Kopi Susu Kekinian"))
        assertNull(canonicalCategory(null))
        assertNull(canonicalCategory("  "))
    }

    @Test
    fun `english category passes through and income uses the income list`() {
        assertEquals("Food & Beverage", canonicalCategory("food & beverage"))
        assertEquals("Other", canonicalCategory("Makanan & Minuman", TransactionType.INCOME))
    }

    @Test
    fun `account matches only an existing name`() {
        val existing = listOf("BCA", "ShopeePay", "Cash")
        assertEquals("ShopeePay", matchAccount("shopeepay", existing))
        assertEquals("ShopeePay", matchAccount("Shopee Pay", existing))
        assertNull(matchAccount("GoPay", existing))
        assertNull(matchAccount(null, existing))
    }

    /**
     * Subsampling must never land *below* the target — the scaler takes it the
     * rest of the way. 4000/4 would be 1000px, under 1024, so 2 is the answer.
     */
    @Test
    fun `sample size keeps the long edge at or above the target`() {
        assertEquals(1, sampleSizeFor(800, 600, 1024))
        assertEquals(2, sampleSizeFor(4000, 3000, 1024))
        assertEquals(1, sampleSizeFor(0, 0, 1024))
        listOf(4032 to 3024, 8000 to 6000, 1024 to 768).forEach { (w, h) ->
            assertTrue("$w x $h", w / sampleSizeFor(w, h, 1024) >= 1024)
        }
    }
}

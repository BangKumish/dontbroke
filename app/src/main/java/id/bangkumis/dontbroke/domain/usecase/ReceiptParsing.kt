package id.bangkumis.dontbroke.domain.usecase

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import id.bangkumis.dontbroke.data.local.entity.TransactionType
import id.bangkumis.dontbroke.presentation.addtransaction.categoriesFor

/** What the vision model managed to read off a receipt. Every field is optional. */
data class ParsedReceiptResult(
    val amount: Double? = null,
    val category: String? = null,
    val location: String? = null,
    val suggestedAccount: String? = null,
) {
    val isEmpty: Boolean
        get() = amount == null && category == null && location == null && suggestedAccount == null
}

/**
 * The prompt asks for Indonesian category names, but the DB does `GROUP BY
 * category` over the English ones the form offers. Writing "Makanan & Minuman"
 * straight through would split the analytics ring into two rows that never
 * merge, so every answer is mapped back to a canonical category here.
 */
private val CATEGORY_ALIASES = mapOf(
    "makanan & minuman" to "Food & Beverage",
    "makanan dan minuman" to "Food & Beverage",
    "makanan" to "Food & Beverage",
    "transportasi" to "Transportation",
    "belanja" to "Shopping",
    "tagihan" to "Bills & Utilities",
    "hiburan" to "Entertainment",
    "kesehatan" to "Healthcare",
    "pendidikan" to "Education",
    "sewa" to "Rent & Housing",
    "lainnya" to "Other",
)

/**
 * Maps a model-supplied category onto one the form actually offers. Falls back to
 * "Other" rather than null: a receipt that scanned fine shouldn't leave the
 * category empty just because the model invented a label.
 */
fun canonicalCategory(raw: String?, type: TransactionType = TransactionType.EXPENSE): String? {
    val name = raw?.trim().orEmpty()
    if (name.isEmpty()) return null
    val options = categoriesFor(type)
    options.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }
    CATEGORY_ALIASES[name.lowercase()]?.let { mapped -> if (mapped in options) return mapped }
    return options.last() // "Other" in both lists
}

/**
 * Models wrap JSON in prose or ```json fences more often than not, so take the
 * outermost brace-balanced span rather than trusting the whole reply. Braces
 * inside strings are skipped; an unterminated object yields null.
 */
fun extractJsonObject(raw: String): String? {
    val start = raw.indexOf('{')
    if (start < 0) return null
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until raw.length) {
        val c = raw[i]
        when {
            escaped -> escaped = false
            c == '\\' && inString -> escaped = true
            c == '"' -> inString = !inString
            inString -> Unit
            c == '{' -> depth++
            c == '}' -> {
                depth--
                if (depth == 0) return raw.substring(start, i + 1)
            }
        }
    }
    return null
}

/**
 * "15000.0", 15000, "Rp 15.000", "15.000,50" — the model returns whichever it
 * feels like, so digits are pulled out by hand instead of trusting Gson to coerce.
 * Indonesian formatting means `.` groups thousands and `,` is the decimal mark.
 */
fun parseAmount(raw: String?): Double? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
    val cleaned = text.filter { it.isDigit() || it == '.' || it == ',' }
    if (cleaned.none(Char::isDigit)) return null
    // a lone trailing group of 1-2 digits after ',' is a decimal; '.' groups thousands
    val normalised = when {
        cleaned.contains(',') -> cleaned.replace(".", "").replace(',', '.')
        cleaned.count { it == '.' } == 1 && cleaned.substringAfterLast('.').length != 3 -> cleaned
        else -> cleaned.replace(".", "")
    }
    return normalised.toDoubleOrNull()?.takeIf { it > 0 }
}

private fun JsonObject.stringOrNull(key: String): String? =
    get(key)?.takeIf { !it.isJsonNull }?.asString?.trim()
        ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

/**
 * Parses the model's reply. Returns null when there is no usable JSON at all —
 * the caller reports that differently from a scan that simply found no fields.
 */
fun parseReceiptJson(raw: String, type: TransactionType = TransactionType.EXPENSE): ParsedReceiptResult? {
    val json = extractJsonObject(raw) ?: return null
    val obj = runCatching { JsonParser.parseString(json).asJsonObject }.getOrNull() ?: return null
    return ParsedReceiptResult(
        amount = parseAmount(obj.stringOrNull("amount")),
        category = canonicalCategory(obj.stringOrNull("category"), type),
        location = obj.stringOrNull("location"),
        suggestedAccount = obj.stringOrNull("suggestedAccount"),
    )
}

/**
 * Picks the account whose name the model's guess actually matches — it suggests
 * free text ("ShopeePay"), but a transaction's `sourceOrAccount` has to equal an
 * existing account name exactly or the balance recalc silently ignores the row.
 */
fun matchAccount(suggested: String?, existing: List<String>): String? {
    val name = suggested?.trim().orEmpty()
    if (name.isEmpty()) return null
    existing.firstOrNull { it.equals(name, ignoreCase = true) }?.let { return it }
    val squashed = name.filter(Char::isLetterOrDigit).lowercase()
    if (squashed.isEmpty()) return null
    return existing.firstOrNull { it.filter(Char::isLetterOrDigit).lowercase() == squashed }
}

/**
 * Largest power-of-two subsample that keeps the longest edge at or above [maxPx],
 * so a 12MP photo never lands in memory whole on the way to being scaled down.
 */
fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    if (width <= 0 || height <= 0 || maxPx <= 0) return 1
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxPx) sample *= 2
    return sample
}

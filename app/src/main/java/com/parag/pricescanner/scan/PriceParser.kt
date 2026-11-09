package com.parag.pricescanner.scan

/**
 * Extracts likely retail prices from OCR text printed on a pack, e.g.
 * "MRP ₹14.00 (incl. of all taxes)", "M.R.P. Rs. 1,299/-", "PRICE: ₹ 20".
 *
 * Pure Kotlin so it can be unit tested on the JVM.
 */
object PriceParser {

    data class Candidate(val value: Double, val score: Int)

    private const val NUM = """(\d{1,3}(?:,\d{2,3})+|\d{1,6})(?:\s?[.,]\s?(\d{1,2}))?"""

    // Price introduced by MRP / M.R.P / Price keywords (strongest signal).
    private val mrpRegex = Regex(
        """(?:M\s?\.?\s?R\s?\.?\s?P\s?\.?|PRICE|RETAIL\s+PRICE)\s*[:\-]?\s*(?:₹|RS\.?|INR|R5\.?|\?|=)?\s*[:\-]?\s*$NUM""",
        RegexOption.IGNORE_CASE
    )

    // Price introduced by a currency sign or code.
    private val currencyRegex = Regex(
        """(?:₹|\bRS\.?|\bINR|\bR5\.?)\s*[:\-]?\s*$NUM""",
        RegexOption.IGNORE_CASE
    )

    // "14/-" style.
    private val slashDashRegex = Regex("""$NUM\s*/\s*-""")

    // Unit prices such as "₹ 200/kg" or "per 100 g" are not the pack price.
    private val unitSuffix = Regex("""^\s*(?:/|per\b)\s*(?:\d+\s*)?(?:kg|g|gm|gms|ml|l|ltr|pc|pcs|unit)\b""", RegexOption.IGNORE_CASE)

    /** All price candidates, best first, de-duplicated by value. */
    fun candidates(text: String): List<Candidate> {
        if (text.isBlank()) return emptyList()
        val normalized = text.replace('\n', ' ').replace(Regex("""\s+"""), " ")
        val found = mutableListOf<Candidate>()

        fun collect(regex: Regex, base: Int) {
            for (m in regex.findAll(normalized)) {
                val value = toNumber(m.groupValues[1], m.groupValues[2]) ?: continue
                if (value <= 0.0 || value > 500_000.0) continue
                var score = base
                val after = normalized.substring(m.range.last + 1)
                if (unitSuffix.containsMatchIn(after.take(12))) score -= 60
                if (m.groupValues[2].length == 2) score += 5 // "14.00" looks like a price
                found += Candidate(value, score)
            }
        }
        collect(mrpRegex, 100)
        collect(currencyRegex, 70)
        collect(slashDashRegex, 50)

        return found
            .groupBy { it.value }
            .map { (_, list) -> list.maxBy { it.score }.copy(score = list.maxOf { it.score } + (list.size - 1) * 3) }
            .filter { it.score > 0 }
            .sortedByDescending { it.score }
    }

    fun bestPrice(text: String): Double? = candidates(text).firstOrNull()?.value

    private fun toNumber(intPart: String, decimals: String): Double? {
        val whole = intPart.replace(",", "")
        val str = if (decimals.isNotEmpty()) "$whole.$decimals" else whole
        return str.toDoubleOrNull()
    }
}

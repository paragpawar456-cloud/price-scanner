package com.parag.pricescanner.scan

/** Picks the most "brand-like" line of OCR text to use as a product name. */
object NameGuesser {

    data class Line(val text: String, val height: Int)

    private val stopWords = listOf(
        "mrp", "m.r.p", "price", "rs", "inr", "₹", "net", "wt", "weight", "mfg", "exp", "best before",
        "batch", "lot", "incl", "taxes", "date", "pkd", "packed", "use by", "fssai", "lic", "customer",
        "care", "www", "http", "ingredients", "nutrition", "energy", "kcal", "per", "veg", "made in",
        "manufactured", "marketed", "address", "email", "contact", "barcode", "usp"
    )

    fun guess(lines: List<Line>): String? = lines
        .asSequence()
        .map { it.copy(text = it.text.trim()) }
        .filter { isNameLike(it.text) }
        .sortedByDescending { it.height }
        .firstOrNull()
        ?.text
        ?.let(::tidy)

    fun isNameLike(text: String): Boolean {
        if (text.length !in 3..32) return false
        val letters = text.count { it.isLetter() }
        if (letters < 3 || letters.toDouble() / text.length < 0.6) return false
        val lower = text.lowercase()
        return stopWords.none { lower.contains(it) }
    }

    private fun tidy(text: String): String =
        text.split(' ').filter { it.isNotBlank() }.joinToString(" ") { word ->
            if (word.length <= 1) word.uppercase()
            else word.first().uppercase() + word.substring(1).lowercase()
        }
}

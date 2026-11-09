package com.parag.pricescanner.scan

import java.time.LocalDate

/** What the camera saw in a single frame. */
data class FrameResult(
    val prices: List<PriceParser.Candidate>,
    val nameGuess: String?,
    val barcode: String?,
    val mfgDate: LocalDate? = null,
    val expiryDate: LocalDate? = null,
    val nutrition: Nutrition? = null,
    val details: ProductDetails? = null,
    val categoryScores: Map<Category, Int> = emptyMap(),
) {
    val bestPrice: Double? get() = prices.firstOrNull()?.value
    val isEmpty: Boolean get() = prices.isEmpty() && barcode == null
}

package com.parag.pricescanner.scan

import java.time.LocalDate

/**
 * Groups camera frames into "sessions": one session is the time one pack is in
 * front of the camera. Everything read during a session (price, barcode, name,
 * dates, nutrition) belongs to the same item, and the item is counted exactly
 * once per session. A new session starts only after the camera has seen no
 * label content for [clearMillis] — i.e. the pack was taken away.
 *
 * This replaces per-key counting, which split one pack into several items when
 * the reading changed mid-scan (price first, then the barcode came into view).
 */
class SessionTracker(
    private val clearMillis: Long = 900L,
    private val stablePriceVotes: Int = 3,
    private val stableBarcodeVotes: Int = 2,
) {
    data class Snapshot(
        val sessionId: Long,
        val price: Double?,
        val alternatives: List<Double>,
        val barcode: String?,
        val name: String?,
        val mfgDate: LocalDate?,
        val expiryDate: LocalDate?,
        val nutrition: Nutrition?,
        val details: ProductDetails?,
        val category: Category?,
        /** True on exactly one frame per session: when the item first becomes identifiable. */
        val countNow: Boolean,
    ) {
        val identified get() = price != null || barcode != null
    }

    private var sessionId = 0L
    private var active = false
    private var counted = false
    private var lastContentAt = Long.MIN_VALUE / 2

    private val prices = mutableMapOf<Double, Int>()
    private val barcodes = mutableMapOf<String, Int>()
    private val names = mutableMapOf<String, Int>()
    private val mfg = mutableMapOf<LocalDate, Int>()
    private val exp = mutableMapOf<LocalDate, Int>()
    private val nutritionVotes = mutableMapOf<String, MutableMap<Double, Int>>()
    private val categoryScores = mutableMapOf<Category, Int>()
    private var details: ProductDetails? = null

    /** @return the current session snapshot, or null when no pack is in view. */
    fun onFrame(f: FrameResult, now: Long): Snapshot? {
        val hasContent = !f.isEmpty || f.mfgDate != null || f.expiryDate != null || f.nutrition?.isEmpty == false ||
            f.details?.isEmpty == false || f.categoryScores.values.sum() >= 3
        if (!hasContent) {
            if (active && now - lastContentAt >= clearMillis) reset()
            return if (active) snapshot(countNow = false) else null
        }
        if (!active) {
            active = true
            counted = false
            sessionId++
        }
        lastContentAt = now

        f.bestPrice?.let { prices.inc(it) }
        f.barcode?.let { barcodes.inc(it) }
        f.nameGuess?.let { names.inc(it) }
        f.mfgDate?.let { mfg.inc(it) }
        f.expiryDate?.let { exp.inc(it) }
        f.categoryScores.forEach { (c, v) -> categoryScores[c] = (categoryScores[c] ?: 0) + v }
        f.details?.let { d -> details = (details ?: ProductDetails()).merge(d) }
        f.nutrition?.toMap()?.forEach { (k, v) -> nutritionVotes.getOrPut(k) { mutableMapOf() }.inc(v) }

        val snap = snapshot(countNow = false)
        if (!counted && snap.identified) {
            counted = true
            return snap.copy(countNow = true)
        }
        return snap
    }

    private fun snapshot(countNow: Boolean): Snapshot {
        val price = prices.best(stablePriceVotes)
        val nut = nutritionVotes.mapValues { (_, m) -> m.best(1) }.filterValues { it != null }
            .entries.joinToString(";") { "${it.key}=${it.value}" }
        return Snapshot(
            sessionId = sessionId,
            price = price,
            alternatives = prices.entries.sortedByDescending { it.value }.map { it.key }.filter { it != price }.take(3),
            barcode = barcodes.best(stableBarcodeVotes),
            name = names.best(1),
            mfgDate = mfg.best(2),
            expiryDate = exp.best(2),
            nutrition = Nutrition.decode(nut),
            details = details,
            category = DetailsParser.decide(categoryScores),
            countNow = countNow,
        )
    }

    fun reset() {
        active = false
        counted = false
        prices.clear(); barcodes.clear(); names.clear(); mfg.clear(); exp.clear(); nutritionVotes.clear()
        categoryScores.clear(); details = null
    }

    private fun <K> MutableMap<K, Int>.inc(k: K) { this[k] = (this[k] ?: 0) + 1 }
    private fun <K> Map<K, Int>.best(min: Int): K? = entries.filter { it.value >= min }.maxByOrNull { it.value }?.key
}

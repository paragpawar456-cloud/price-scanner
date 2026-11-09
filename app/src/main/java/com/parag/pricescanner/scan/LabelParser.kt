package com.parag.pricescanner.scan

import java.time.LocalDate
import java.time.YearMonth

/**
 * Layout-aware reading of a pack label. Works on OCR words with their positions,
 * so it handles labels where the value is printed under a heading, e.g.
 *
 *   MRP RS. ₹↓        USP ₹/g↓      BATCH NO.↓
 *   140.00            0.28          DW D1212616
 *   12 SEP. 26                      11 JUN. 27
 *   DATE OF PACKAGING↑              USE BY↑
 *
 * Pure Kotlin (no Android types) so it can be unit tested.
 */
object LabelParser {

    data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val cx get() = (left + right) / 2f
        val cy get() = (top + bottom) / 2f
        val height get() = (bottom - top).coerceAtLeast(1)
        val width get() = (right - left).coerceAtLeast(1)
    }

    data class Word(val text: String, val box: Box)
    data class Line(val text: String, val box: Box, val words: List<Word>)

    data class Result(
        val prices: List<PriceParser.Candidate>,
        val mfgDate: LocalDate?,
        val expiryDate: LocalDate?,
    )

    // ---------- headings ----------
    private enum class Head { MRP, UNIT_PRICE, BATCH, WEIGHT, MFG, EXPIRY, OTHER }

    private val mrpHead = Regex("""\bM\s?\.?\s?R\s?\.?\s?P\b|\bRETAIL\s+PRICE\b|^PRICE\b""", RegexOption.IGNORE_CASE)
    private val unitHead = Regex("""\bUSP\b|UNIT\s+SALE|₹\s*/\s*(g|kg|ml|l)\b|PER\s+(g|kg|100)""", RegexOption.IGNORE_CASE)
    private val batchHead = Regex("""\bBATCH\b|\bB\.?\s?NO\b|\bLOT\b""", RegexOption.IGNORE_CASE)
    private val weightHead = Regex("""\bNET\s*(WT|WEIGHT|QTY|QUANTITY|VOL)\b|\bNET\s*WEIGHT""", RegexOption.IGNORE_CASE)
    private val mfgHead = Regex("""\bMFG\b|\bMFD\b|\bMANUFACTUR|\bPKD\b|\bPACK(ED|AGING|AGED|ING)\b|\bD\.?\s?O\.?\s?M\b|\bDATE\s+OF\s+(MFG|MANUF|PACK)""", RegexOption.IGNORE_CASE)
    private val expHead = Regex("""\bEXP\b|\bEXPIRY\b|\bEXPIRES?\b|\bUSE\s*BY\b|\bBEST\s*BEFORE\b|\bBB\b|\bUSE\s+BEFORE\b""", RegexOption.IGNORE_CASE)

    private fun headOf(text: String): Head? = when {
        mrpHead.containsMatchIn(text) -> Head.MRP
        unitHead.containsMatchIn(text) -> Head.UNIT_PRICE
        expHead.containsMatchIn(text) -> Head.EXPIRY
        mfgHead.containsMatchIn(text) -> Head.MFG
        batchHead.containsMatchIn(text) -> Head.BATCH
        weightHead.containsMatchIn(text) -> Head.WEIGHT
        else -> null
    }

    // ---------- values ----------
    private val priceToken = Regex("""^(?:₹|RS\.?)?\s*(\d{1,3}(?:,\d{2,3})+|\d{1,6})(?:[.,](\d{1,2}))?(?:/-)?$""", RegexOption.IGNORE_CASE)

    private val months = mapOf(
        "JAN" to 1, "FEB" to 2, "MAR" to 3, "APR" to 4, "MAY" to 5, "JUN" to 6,
        "JUL" to 7, "AUG" to 8, "SEP" to 9, "SEPT" to 9, "OCT" to 10, "NOV" to 11, "DEC" to 12,
    )
    private const val MON = """(JAN|FEB|MAR|APR|MAY|JUNE?|JULY?|AUG|SEPT?|OCT|NOV|DEC)[A-Z]*"""
    private const val SEP = """\s*[.\-/'’,]?\s*"""
    private val dMonY = Regex("""\b(\d{1,2})$SEP$MON\.?$SEP(\d{4}|\d{2})\b""", RegexOption.IGNORE_CASE)
    private val monY = Regex("""\b$MON\.?$SEP(\d{4}|\d{2})\b""", RegexOption.IGNORE_CASE)
    private val dmy = Regex("""\b(\d{1,2})\s*[./\-]\s*(\d{1,2})\s*[./\-]\s*(\d{4}|\d{2})\b""")
    private val my = Regex("""\b(\d{1,2})\s*[./\-]\s*(\d{4})\b|\b(\d{2})\s*/\s*(\d{2})\b""")
    private val bestBeforeMonths = Regex("""BEST\s*BEFORE\s*(\d{1,2})\s*MONTHS?|(?:USE|CONSUME)\s*WITHIN\s*(\d{1,2})\s*MONTHS?|EXPIRES?\s*(?:IN|WITHIN)\s*(\d{1,2})\s*MONTHS?""", RegexOption.IGNORE_CASE)

    private data class DateHit(val date: LocalDate, val start: Int, val end: Int, val monthOnly: Boolean = false)

    /** Finds dates in one string, from most to least specific, without overlaps. */
    fun findDates(text: String): List<LocalDate> = findDateHits(text).map { it.date }

    private fun findDateHits(text: String): List<DateHit> {
        val hits = mutableListOf<DateHit>()
        fun free(r: IntRange) = hits.none { r.first < it.end && it.start <= r.last }

        for (m in dMonY.findAll(text)) {
            val d = m.groupValues[1].toInt()
            val mo = monthOf(m.groupValues[2]) ?: continue
            val y = year(m.groupValues[3]) ?: continue
            safeDate(y, mo, d)?.let { hits += DateHit(it, m.range.first, m.range.last + 1) }
        }
        for (m in dmy.findAll(text)) {
            if (!free(m.range)) continue
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt()
            val y = year(m.groupValues[3]) ?: continue
            safeDate(y, mo, d)?.let { hits += DateHit(it, m.range.first, m.range.last + 1) }
        }
        for (m in monY.findAll(text)) {
            if (!free(m.range)) continue
            val mo = monthOf(m.groupValues[1]) ?: continue
            val y = year(m.groupValues[2]) ?: continue
            hits += DateHit(LocalDate.of(y, mo, 1), m.range.first, m.range.last + 1, monthOnly = true)
        }
        for (m in my.findAll(text)) {
            if (!free(m.range)) continue
            val (moS, yS) = if (m.groupValues[1].isNotEmpty()) m.groupValues[1] to m.groupValues[2] else m.groupValues[3] to m.groupValues[4]
            val mo = moS.toInt(); val y = year(yS) ?: continue
            if (mo in 1..12) hits += DateHit(LocalDate.of(y, mo, 1), m.range.first, m.range.last + 1, monthOnly = true)
        }
        return hits.sortedBy { it.start }
    }

    private fun monthOf(s: String): Int? = months[s.uppercase().take(4)] ?: months[s.uppercase().take(3)]
    private fun year(s: String): Int? {
        val y = s.toIntOrNull() ?: return null
        val full = if (s.length == 2) 2000 + y else y
        return full.takeIf { it in 2000..2099 }
    }
    private fun safeDate(y: Int, m: Int, d: Int): LocalDate? =
        if (m !in 1..12 || d !in 1..31) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()

    // ---------- main ----------
    fun parse(lines: List<Line>, today: LocalDate = LocalDate.now()): Result {
        val fullText = lines.joinToString("\n") { it.text }
        val textPrices = PriceParser.candidates(fullText)

        val words = lines.flatMap { it.words }
        // Headings are detected per line (a heading like "MRP RS. ₹" spans several words).
        val heads = lines.mapNotNull { l -> headOf(l.text)?.let { it to l.box } } +
            words.mapNotNull { w -> headOf(w.text)?.let { it to w.box } }

        val spatialPrices = spatialPrices(words, heads)
        val prices = merge(textPrices, spatialPrices, words)

        val (mfg, exp) = dates(lines, heads, today)
        return Result(prices, mfg, exp)
    }

    /** Numbers printed under (or right of) an MRP heading, and closest to it rather than to other headings. */
    private fun spatialPrices(words: List<Word>, heads: List<Pair<Head, Box>>): List<PriceParser.Candidate> {
        val mrpBoxes = heads.filter { it.first == Head.MRP }.map { it.second }
        if (mrpBoxes.isEmpty()) return emptyList()
        val out = mutableListOf<PriceParser.Candidate>()
        for (w in words) {
            val m = priceToken.find(w.text.trim()) ?: continue
            val value = number(m.groupValues[1], m.groupValues[2]) ?: continue
            if (value <= 0 || value > 500_000) continue
            if (headOf(w.text) != null) continue
            val owner = owningHead(w.box, heads) ?: continue
            if (owner.first != Head.MRP) continue
            var score = 105
            if (m.groupValues[2].length == 2) score += 5
            val d = distance(owner.second, w.box) / owner.second.height
            score -= d.toInt().coerceAtMost(30)
            out += PriceParser.Candidate(value, score)
        }
        return out
    }

    /** The heading a value belongs to: below it (or right of it on the same row) and the nearest such heading. */
    private fun owningHead(v: Box, heads: List<Pair<Head, Box>>): Pair<Head, Box>? =
        heads.filter { (_, h) ->
            val below = v.cy > h.bottom - h.height * 0.2f && v.top - h.bottom < h.height * 5
            val rightSameRow = kotlin.math.abs(v.cy - h.cy) < h.height * 0.8f && v.left >= h.right - 2 && v.left - h.right < h.height * 8
            below || rightSameRow
        }.minByOrNull { (_, h) -> distance(h, v) }

    /** Distance weighting horizontal offset more, since values sit in columns under headings. */
    private fun distance(h: Box, v: Box): Float {
        val dx = when {
            v.right < h.left -> (h.left - v.right).toFloat()
            v.left > h.right -> (v.left - h.right).toFloat()
            else -> 0f
        }
        val dy = (v.cy - h.cy).coerceAtLeast(0f)
        return dx * 2.5f + dy
    }

    private fun merge(
        text: List<PriceParser.Candidate>,
        spatial: List<PriceParser.Candidate>,
        words: List<Word>,
    ): List<PriceParser.Candidate> {
        val all = (text + spatial).toMutableList()
        if (all.isEmpty()) {
            // Last resort: a lone amount with paise, e.g. "140.00", that is not a date or weight.
            words.mapNotNull { priceToken.find(it.text.trim()) }
                .filter { it.groupValues[2].length == 2 && !it.value.contains('/') }
                .mapNotNull { number(it.groupValues[1], it.groupValues[2]) }
                .filter { it >= 1.0 }
                .forEach { all += PriceParser.Candidate(it, 25) }
        }
        return all.groupBy { it.value }
            .map { (v, l) -> PriceParser.Candidate(v, l.maxOf { it.score } + (l.size - 1) * 3) }
            .sortedByDescending { it.score }
    }

    private fun dates(lines: List<Line>, heads: List<Pair<Head, Box>>, today: LocalDate): Pair<LocalDate?, LocalDate?> {
        var mfg: LocalDate? = null
        var exp: LocalDate? = null
        val unlabeled = mutableListOf<LocalDate>()
        val monthOnly = mutableSetOf<LocalDate>()

        for (line in lines) {
            val hits = findDateHits(line.text)
            for (hit in hits) {
                if (hit.monthOnly) monthOnly += hit.date
                // 1) keyword just before the date on the same line: "EXP 11/27", "Best before 06.2026"
                val before = line.text.substring(0, hit.start).takeLast(28)
                val after = line.text.substring(hit.end).take(20)
                val inline = lastHead(before) ?: firstHead(after)
                // 2) otherwise the heading printed above or below it (labels with ↓ / ↑ arrows)
                val kind = inline ?: nearestDateHead(line.box, heads)
                when (kind) {
                    Head.MFG -> if (mfg == null) mfg = hit.date
                    Head.EXPIRY -> if (exp == null) exp = hit.date
                    else -> unlabeled += hit.date
                }
            }
        }

        // Unlabelled dates: earliest is manufacture/packing, latest is expiry.
        val pool = (unlabeled + listOfNotNull(mfg, exp)).distinct().sorted()
        if (mfg == null && exp == null && pool.size >= 2) {
            mfg = pool.first(); exp = pool.last()
        } else if (unlabeled.isNotEmpty()) {
            if (mfg == null) mfg = unlabeled.filter { exp == null || it < exp!! }.minOrNull()?.takeIf { !it.isAfter(today) }
            if (exp == null) exp = unlabeled.filter { mfg == null || it > mfg!! }.maxOrNull()?.takeIf { it.isAfter(today) || mfg != null }
        }

        // "Best before 9 months from manufacture"
        if (exp == null && mfg != null) {
            bestBeforeMonths.find(lines.joinToString(" ") { it.text })?.let { m ->
                val n = m.groupValues.drop(1).firstOrNull { it.isNotEmpty() }?.toLongOrNull()
                if (n != null) exp = mfg!!.plusMonths(n)
            }
        }
        // Month-only expiry means the end of that month.
        exp = exp?.let { if (it in monthOnly) YearMonth.from(it).atEndOfMonth() else it }

        if (mfg != null && exp != null && !exp!!.isAfter(mfg)) { val t = mfg; mfg = exp; exp = t }
        return mfg to exp
    }

    private fun lastHead(s: String): Head? {
        val e = expHead.findAll(s).lastOrNull()?.range?.first ?: -1
        val m = mfgHead.findAll(s).lastOrNull()?.range?.first ?: -1
        return when { e < 0 && m < 0 -> null; e > m -> Head.EXPIRY; else -> Head.MFG }
    }

    private fun firstHead(s: String): Head? {
        val e = expHead.find(s)?.range?.first ?: Int.MAX_VALUE
        val m = mfgHead.find(s)?.range?.first ?: Int.MAX_VALUE
        return when { e == Int.MAX_VALUE && m == Int.MAX_VALUE -> null; e < m -> Head.EXPIRY; else -> Head.MFG }
    }

    /** The MFG/EXP heading in the same column, above or below the date, within a few line heights. */
    private fun nearestDateHead(v: Box, heads: List<Pair<Head, Box>>): Head? =
        heads.filter { it.first == Head.MFG || it.first == Head.EXPIRY }
            .map { (k, h) ->
                val overlap = minOf(v.right, h.right) - maxOf(v.left, h.left)
                val dx = if (overlap > 0) 0f else -overlap.toFloat()
                val dy = kotlin.math.abs(v.cy - h.cy)
                Triple(k, dx, dy / v.height)
            }
            .filter { it.third < 6f && it.second < v.width * 1.5f }
            .minByOrNull { it.second * 2 + it.third * v.height }
            ?.first

    private fun number(intPart: String, decimals: String): Double? {
        val whole = intPart.replace(",", "")
        return (if (decimals.isNotEmpty()) "$whole.$decimals" else whole).toDoubleOrNull()
    }
}

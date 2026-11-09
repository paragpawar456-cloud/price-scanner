package com.parag.pricescanner.scan

import kotlin.math.abs

/** Nutrition per 100 g / 100 ml. Any field may be unknown. Pure Kotlin. */
data class Nutrition(
    val energyKcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val sugar: Double? = null,
    val addedSugar: Double? = null,
    val fat: Double? = null,
    val saturatedFat: Double? = null,
    val transFat: Double? = null,
    val sodiumMg: Double? = null,
    val fiber: Double? = null,
) {
    val isEmpty get() = toMap().isEmpty()
    val salt: Double? get() = sodiumMg?.let { it * 2.5 / 1000 }

    fun merge(other: Nutrition?): Nutrition = if (other == null) this else Nutrition(
        energyKcal ?: other.energyKcal, protein ?: other.protein, carbs ?: other.carbs,
        sugar ?: other.sugar, addedSugar ?: other.addedSugar, fat ?: other.fat,
        saturatedFat ?: other.saturatedFat, transFat ?: other.transFat,
        sodiumMg ?: other.sodiumMg, fiber ?: other.fiber,
    )

    fun toMap(): Map<String, Double> = buildMap {
        energyKcal?.let { put("kcal", it) }; protein?.let { put("protein", it) }; carbs?.let { put("carbs", it) }
        sugar?.let { put("sugar", it) }; addedSugar?.let { put("added_sugar", it) }; fat?.let { put("fat", it) }
        saturatedFat?.let { put("sat_fat", it) }; transFat?.let { put("trans_fat", it) }
        sodiumMg?.let { put("sodium_mg", it) }; fiber?.let { put("fiber", it) }
    }

    /** Compact storage form, e.g. "kcal=427;sugar=2.1". */
    fun encode(): String? = toMap().takeIf { it.isNotEmpty() }?.entries?.joinToString(";") { "${it.key}=${it.value}" }

    enum class Level { LOW, MEDIUM, HIGH }

    data class Row(val label: String, val value: String, val level: Level?)

    /** Display rows with traffic-light levels (UK FSA / WHO guidance for foods per 100 g). */
    fun rows(): List<Row> = buildList {
        energyKcal?.let { add(Row("Energy", "${fmt(it)} kcal", null)) }
        fat?.let { add(Row("Total fat", "${fmt(it)} g", level(it, 3.0, 17.5))) }
        saturatedFat?.let { add(Row("Saturated fat", "${fmt(it)} g", level(it, 1.5, 5.0))) }
        transFat?.let { add(Row("Trans fat", "${fmt(it)} g", if (it <= 0.0) Level.LOW else if (it <= 0.2) Level.MEDIUM else Level.HIGH)) }
        carbs?.let { add(Row("Carbohydrate", "${fmt(it)} g", null)) }
        sugar?.let { add(Row("Sugar", "${fmt(it)} g", level(it, 5.0, 22.5))) }
        addedSugar?.let { add(Row("Added sugar", "${fmt(it)} g", level(it, 2.5, 10.0))) }
        protein?.let { add(Row("Protein", "${fmt(it)} g", null)) }
        fiber?.let { add(Row("Fibre", "${fmt(it)} g", null)) }
        sodiumMg?.let { add(Row("Sodium", "${fmt(it)} mg  (salt ${fmt(it * 2.5 / 1000)} g)", level(it * 2.5 / 1000, 0.3, 1.5))) }
    }

    /** Short warnings like "High sugar", for badges. */
    fun warnings(): List<String> = rows().filter { it.level == Level.HIGH }.map { "High " + it.label.lowercase().removePrefix("total ") }

    companion object {
        fun decode(s: String?): Nutrition? {
            if (s.isNullOrBlank()) return null
            val m = s.split(';').mapNotNull { p -> p.split('=').takeIf { it.size == 2 }?.let { it[0] to it[1].toDoubleOrNull() } }
                .filter { it.second != null }.associate { it.first to it.second!! }
            return Nutrition(
                m["kcal"], m["protein"], m["carbs"], m["sugar"], m["added_sugar"], m["fat"],
                m["sat_fat"], m["trans_fat"], m["sodium_mg"], m["fiber"],
            ).takeIf { !it.isEmpty }
        }

        private fun level(v: Double, low: Double, high: Double) = when {
            v <= low -> Level.LOW
            v > high -> Level.HIGH
            else -> Level.MEDIUM
        }

        fun fmt(v: Double): String = if (abs(v - Math.round(v)) < 0.05) Math.round(v).toString() else "%.1f".format(v)
    }
}

/**
 * Reads a nutrition table from OCR lines. Handles values in the same line
 * ("Total Sugars 2.1 g") and tables where values sit in a column to the right
 * of the labels (the first value column, normally "per 100 g", is used).
 */
object NutritionParser {
    private enum class N { ENERGY, PROTEIN, CARBS, ADDED_SUGAR, SUGAR, SAT_FAT, TRANS_FAT, FAT, SODIUM, SALT, FIBER }

    // Checked in this order so "saturated fat" is not taken as "fat".
    private val labels = listOf(
        N.ENERGY to Regex("""\bENERGY\b|\bCALORI""", RegexOption.IGNORE_CASE),
        N.PROTEIN to Regex("""\bPROTEINS?\b""", RegexOption.IGNORE_CASE),
        N.ADDED_SUGAR to Regex("""\bADDED\s+SUGARS?\b""", RegexOption.IGNORE_CASE),
        N.SUGAR to Regex("""\b(TOTAL\s+)?SUGARS?\b""", RegexOption.IGNORE_CASE),
        N.CARBS to Regex("""\bCARBOHYDRATES?\b|\bCARBS\b""", RegexOption.IGNORE_CASE),
        N.SAT_FAT to Regex("""\bSATURATED\b|\bSAT\.?\s*FAT""", RegexOption.IGNORE_CASE),
        N.TRANS_FAT to Regex("""\bTRANS\b""", RegexOption.IGNORE_CASE),
        N.FAT to Regex("""^(?!.*\b(MONO|POLY|UNSAT|SATURATED|TRANS)).*\b(TOTAL\s+)?FATS?\b""", RegexOption.IGNORE_CASE),
        N.SODIUM to Regex("""\bSODIUM\b""", RegexOption.IGNORE_CASE),
        N.SALT to Regex("""\bSALT\b""", RegexOption.IGNORE_CASE),
        N.FIBER to Regex("""\b(DIETARY\s+)?FIB(RE|ER)\b""", RegexOption.IGNORE_CASE),
    )

    private val valueRegex = Regex("""(?<![A-Z0-9])(\d{1,4}(?:[.,]\d{1,2})?)\s*(KCAL|KJ|MG|G|GM)?\b""", RegexOption.IGNORE_CASE)

    fun parse(lines: List<LabelParser.Line>): Nutrition {
        val found = mutableMapOf<N, Pair<Double, String>>()
        val words = lines.flatMap { it.words }

        for (line in lines) {
            val kind = labels.firstOrNull { it.second.containsMatchIn(line.text) }?.first ?: continue
            if (kind in found && kind != N.ENERGY) continue
            val labelEnd = labels.first { it.first == kind }.second.find(line.text)!!.range.last + 1
            val unitHint = Regex("""\((KCAL|KJ|MG|G|GM)\)""", RegexOption.IGNORE_CASE).find(line.text)?.groupValues?.get(1).orEmpty()

            // 1) value in the same text line, after the label
            var vals = valueRegex.findAll(line.text.substring(labelEnd))
                .filterNot { it.value.trim().let { v -> v == "100" && line.text.contains("100", true) && line.text.contains("per", true) } }
                .map { it.groupValues[1] to it.groupValues[2].ifEmpty { unitHint } }.toList()

            // 2) otherwise numbers to the right on the same row
            if (vals.isEmpty()) {
                vals = words.filter { w ->
                    w.box.left >= line.box.right - 4 &&
                        kotlin.math.abs(w.box.cy - line.box.cy) < line.box.height * 0.6f
                }.sortedBy { it.box.left }
                    .mapNotNull { w -> valueRegex.matchEntire(w.text.trim())?.let { it.groupValues[1] to it.groupValues[2].ifEmpty { unitHint } } }
            }
            if (vals.isEmpty()) continue

            if (kind == N.ENERGY) {
                // Prefer a kcal value; convert kJ if that is all there is.
                val kcal = vals.firstOrNull { it.second.equals("kcal", true) }?.first?.num()
                    ?: vals.firstOrNull { it.second.isEmpty() }?.first?.num()?.takeIf { !line.text.contains("kj", true) || line.text.contains("kcal", true) }
                    ?: vals.firstOrNull { it.second.equals("kj", true) }?.first?.num()?.div(4.184)
                if (kcal != null && N.ENERGY !in found) found[N.ENERGY] = kcal to "kcal"
                continue
            }
            val (v, unit) = vals.first()
            val value = v.num() ?: continue
            found[kind] = value to unit
        }

        fun g(n: N): Double? = found[n]?.let { (v, u) -> if (u.equals("mg", true) && n != N.SODIUM) v / 1000 else v }
            ?.takeIf { it in 0.0..100.0 || n == N.SODIUM }
        val sodium = found[N.SODIUM]?.let { (v, u) -> if (u.equals("g", true) || u.equals("gm", true)) v * 1000 else v }
            ?: found[N.SALT]?.let { (v, u) -> (if (u.equals("mg", true)) v / 1000 else v) * 400 }

        return Nutrition(
            energyKcal = found[N.ENERGY]?.first?.takeIf { it in 0.0..950.0 },
            protein = g(N.PROTEIN), carbs = g(N.CARBS), sugar = g(N.SUGAR), addedSugar = g(N.ADDED_SUGAR),
            fat = g(N.FAT), saturatedFat = g(N.SAT_FAT), transFat = g(N.TRANS_FAT),
            sodiumMg = sodium?.takeIf { it in 0.0..40_000.0 }, fiber = g(N.FIBER),
        )
    }

    private fun String.num() = replace(',', '.').toDoubleOrNull()
}

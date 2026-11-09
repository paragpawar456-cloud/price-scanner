package com.parag.pricescanner.scan

enum class Category(val label: String) {
    FOOD("Food"),
    GROCERY("Groceries"),
    MEDICINE("Medicine"),
    CLOTHING("Clothes"),
    PERSONAL_CARE("Personal care"),
    HOUSEHOLD("Household"),
    ELECTRONICS("Electronics"),
    OTHER("Other");

    val hasNutrition get() = this == FOOD || this == GROCERY

    companion object {
        fun of(name: String?): Category? = entries.firstOrNull { it.name == name }
    }
}

/** Information read from a pack (or the online database). Every field is optional. Pure Kotlin. */
data class ProductDetails(
    val brand: String? = null,
    val netQuantity: String? = null,
    val ingredients: String? = null,
    val allergens: String? = null,
    val diet: String? = null,
    val nutriScore: String? = null,
    val processing: String? = null,
    val composition: String? = null,
    val dosage: String? = null,
    val prescription: String? = null,
    val fabric: String? = null,
    val size: String? = null,
    val care: String? = null,
    val manufacturer: String? = null,
    val storage: String? = null,
    val origin: String? = null,
    val warnings: String? = null,
) {
    private fun fields(): List<Pair<String, String?>> = listOf(
        "brand" to brand, "net" to netQuantity, "ingredients" to ingredients, "allergens" to allergens,
        "diet" to diet, "nutriscore" to nutriScore, "processing" to processing, "composition" to composition,
        "dosage" to dosage, "rx" to prescription, "fabric" to fabric, "size" to size, "care" to care,
        "mfr" to manufacturer, "storage" to storage, "origin" to origin, "warnings" to warnings,
    )

    val isEmpty get() = fields().all { it.second.isNullOrBlank() }

    /** Keeps this object's values and fills gaps from [other]; for free text, the longer reading wins. */
    fun merge(other: ProductDetails?): ProductDetails {
        if (other == null) return this
        fun pick(a: String?, b: String?) = if (a.isNullOrBlank()) b else a
        fun longer(a: String?, b: String?) = listOfNotNull(a, b).maxByOrNull { it.length }
        return ProductDetails(
            pick(brand, other.brand), pick(netQuantity, other.netQuantity),
            longer(ingredients, other.ingredients), pick(allergens, other.allergens), pick(diet, other.diet),
            pick(nutriScore, other.nutriScore), pick(processing, other.processing),
            longer(composition, other.composition), pick(dosage, other.dosage), pick(prescription, other.prescription),
            pick(fabric, other.fabric), pick(size, other.size), pick(care, other.care),
            pick(manufacturer, other.manufacturer), pick(storage, other.storage), pick(origin, other.origin),
            pick(warnings, other.warnings),
        )
    }

    fun encode(): String? = fields().filter { !it.second.isNullOrBlank() }
        .takeIf { it.isNotEmpty() }
        ?.joinToString("\n") { (k, v) -> k + "\t" + v!!.replace('\n', ' ').replace('\t', ' ') }

    /** Rows to display, ordered for the category. */
    fun rows(category: Category?): List<Pair<String, String>> {
        val all = linkedMapOf(
            "Prescription" to prescription, "Composition" to composition, "Dosage" to dosage,
            "Fabric" to fabric, "Size" to size, "Wash care" to care,
            "Brand" to brand, "Net quantity" to netQuantity,
            "Diet" to diet, "Nutri-Score" to nutriScore, "Processing" to processing,
            "Ingredients" to ingredients, "Allergens" to allergens,
            "Warnings" to warnings, "Storage" to storage, "Manufacturer" to manufacturer, "Origin" to origin,
        )
        val first = when (category) {
            Category.MEDICINE -> listOf("Prescription", "Composition", "Dosage", "Net quantity", "Warnings", "Storage")
            Category.CLOTHING -> listOf("Brand", "Fabric", "Size", "Wash care")
            Category.FOOD, Category.GROCERY -> listOf("Brand", "Net quantity", "Diet", "Nutri-Score", "Processing", "Ingredients", "Allergens")
            else -> listOf("Brand", "Net quantity", "Ingredients", "Warnings")
        }
        val order = first + all.keys.filter { it !in first }
        return order.mapNotNull { k -> all[k]?.takeIf { it.isNotBlank() }?.let { k to it } }
    }

    companion object {
        fun decode(s: String?): ProductDetails? {
            if (s.isNullOrBlank()) return null
            val m = s.split('\n').mapNotNull { l -> l.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            return ProductDetails(
                m["brand"], m["net"], m["ingredients"], m["allergens"], m["diet"], m["nutriscore"], m["processing"],
                m["composition"], m["dosage"], m["rx"], m["fabric"], m["size"], m["care"], m["mfr"], m["storage"],
                m["origin"], m["warnings"],
            ).takeIf { !it.isEmpty }
        }
    }
}

/** Reads product details and category clues from the OCR text of a frame. */
object DetailsParser {

    data class Result(val details: ProductDetails, val categoryScores: Map<Category, Int>)

    private fun rx(p: String) = Regex(p, setOf(RegexOption.IGNORE_CASE))

    private val net = rx("""NET\s*(?:WT|WEIGHT|QTY|QUANTITY|VOL(?:UME)?|CONTENTS?)\.?\s*[:\-]?\s*(\d+(?:[.,]\d+)?\s*(?:KG|G|GM|GMS|MG|ML|L|LTR|LITRES?|PCS?|N|UNITS?|TABLETS?|CAPSULES?)\b)""")
    private val countPack = rx("""\b(\d{1,3})\s*(TABLETS|CAPSULES|SACHETS|STRIPS)\b""")
    private val ingredients = rx("""INGREDIENTS?\s*[:\-]?\s*(.{8,400}?)(?=\b(?:NUTRITION|ALLERGEN|CONTAINS\s+(?:ADDED|PERMITTED)?|MAY\s+CONTAIN|MFD|MKTD|MANUFACTURED|STORE|BEST\s+BEFORE|NET\s+(?:WT|QTY)|MRP|M\.R\.P|FSSAI|CUSTOMER)\b|$)""")
    private val allergens = rx("""(?:ALLERGEN(?:\s+INFORMATION|S)?\s*[:\-]?\s*|CONTAINS\s*[:\-]?\s*(?=(?:WHEAT|MILK|NUTS?|SOY|GLUTEN|PEANUT|EGG|SESAME|MUSTARD|FISH|CASHEW|ALMOND))|MAY\s+CONTAIN\s*(?:TRACES\s+OF)?\s*)([A-Z ,&/]{3,80})""")
    private val composition = rx("""(?:COMPOSITION\s*[:\-]?|EACH\s+(?:FILM[\s-]*COATED\s+|UNCOATED\s+|HARD\s+GELATIN\s+|SOFT\s+GELATIN\s+|CHEWABLE\s+)?(?:TABLET|CAPSULE|ML|5\s*ML|10\s*ML|GRAM|G)\s+CONTAINS?\s*[:\-]?)\s*(.{4,240}?)(?=\b(?:DOSAGE|DIRECTIONS?|STORE|STORAGE|MFD|MFG|MANUFACTURED|MKTD|WARNING|KEEP\s+OUT|SCHEDULE|BATCH|EXP)\b|$)""")
    private val dosage = rx("""(?:DOSAGE|DOSE|DIRECTIONS?\s+FOR\s+USE)\s*[:\-]?\s*(.{4,120}?)(?=\b(?:STORE|STORAGE|MFD|MFG|WARNING|KEEP|SCHEDULE|BATCH)\b|$)""")
    private val schedule = rx("""SCHEDULE\s+(H1|H|X|G)\b|\bRx\b|PRESCRIPTION\s+(?:ONLY|DRUG)|SOLD\s+BY\s+RETAIL\s+ON\s+THE\s+PRESCRIPTION""")
    private val fabric = rx("""(\d{1,3}\s*%\s*(?:COTTON|POLYESTER|VISCOSE|RAYON|NYLON|POLYAMIDE|LINEN|WOOL|SILK|SPANDEX|ELASTANE|LYCRA|ACRYLIC|MODAL|LYOCELL)(?:\s*[,/&+]?\s*\d{1,3}\s*%\s*[A-Z]+)*)""")
    private val size = rx("""\bSIZE\s*[:\-]?\s*(XXS|XS|S|M|L|XL|XXL|XXXL|[2-6]XL|\d{2,3}(?:\s*CM)?)\b""")
    private val care = rx("""((?:MACHINE|HAND|GENTLE|COLD)\s+WASH[^.]{0,60}|DO\s+NOT\s+(?:BLEACH|TUMBLE\s+DRY|IRON|WRING)[^.]{0,40}|DRY\s+CLEAN(?:\s+ONLY)?|WASH\s+(?:SEPARATELY|INSIDE\s+OUT)[^.]{0,40})""")
    private val mfr = rx("""(?:MFD|MKTD|MFG|MANUFACTURED|MARKETED|PACKED)\.?\s*(?:&\s*(?:MKTD|MFD|PKD)\.?\s*)?(?:BY|FOR)\s*[:\-]?\s*([A-Z][A-Z0-9 .&()'-]{3,60}?(?:LTD|LIMITED|PVT|INC|LLP|CO|COMPANY|INDUSTRIES|FOODS|PHARMA[A-Z]*)\.?)""")
    private val storage = rx("""(STORE\s+(?:IN|AT|BELOW|UNDER|AWAY)[^.]{3,90})""")
    private val origin = rx("""(?:COUNTRY\s+OF\s+ORIGIN\s*[:\-]?\s*|MADE\s+IN\s+)([A-Z]{3,20})""")
    private val warning = rx("""(KEEP\s+OUT\s+OF\s+(?:THE\s+)?REACH\s+OF\s+CHILDREN|FOR\s+EXTERNAL\s+USE\s+ONLY|NOT\s+FOR\s+(?:MEDICINAL|CHILDREN)[^.]{0,30}|FLAMMABLE|HARMFUL\s+IF\s+SWALLOWED)""")

    // Category clues: word -> weight.
    private val clues: Map<Category, List<Pair<Regex, Int>>> = mapOf(
        Category.MEDICINE to listOf(
            rx("""\bTABLETS?\b|\bCAPSULES?\b|\bSYRUP\b|\bOINTMENT\b|\bINJECTION\b|\bSUSPENSION\b""") to 3,
            rx("""SCHEDULE\s+H|\bRx\b|PHYSICIAN|PRESCRIPTION|\bDOSAGE\b|\bCOMPOSITION\b""") to 4,
            rx("""\bI\.?P\.?\b\s*\d|\d\s*MG\s+(?:I\.?P|B\.?P|U\.?S\.?P)|MFG\.?\s*LIC|\bM\.L\.\s*\d|DRUG|PHARMA""") to 3,
        ),
        Category.CLOTHING to listOf(
            rx("""\d{1,3}\s*%\s*(?:COTTON|POLYESTER|VISCOSE|RAYON|NYLON|LINEN|WOOL|SPANDEX|ELASTANE|LYCRA)""") to 5,
            rx("""\bFABRIC\b|\bGARMENT\b|\bAPPAREL\b|MACHINE\s+WASH|DRY\s+CLEAN|\bCHEST\b|\bWAIST\b|\bSHIRT\b|\bT-SHIRT\b|\bJEANS\b|\bKURTA\b|\bTROUSERS?\b""") to 3,
            rx("""\bSIZE\s*[:\-]?\s*(?:XS|S|M|L|XL|XXL|\d{2})\b""") to 2,
        ),
        Category.PERSONAL_CARE to listOf(
            rx("""\bSHAMPOO\b|\bSOAP\b|TOOTH\s*PASTE|\bLOTION\b|\bDEODORANT\b|FACE\s*WASH|HAIR\s+OIL|\bSUNSCREEN\b|\bCONDITIONER\b|BODY\s+WASH|\bMOISTURI[SZ]""") to 4,
            rx("""FOR\s+EXTERNAL\s+USE|\bCOSMETIC\b|\bSKIN\b|\bHAIR\b|\bCREAM\b""") to 2,
        ),
        Category.HOUSEHOLD to listOf(
            rx("""\bDETERGENT\b|DISH\s*WASH|\bCLEANER\b|FLOOR|TOILET|DISINFECT|\bMOSQUITO\b|\bINSECT|FABRIC\s+CONDITIONER|\bREPELLENT\b|AIR\s+FRESHENER""") to 4,
        ),
        Category.ELECTRONICS to listOf(
            rx("""\bVOLTAGE\b|\d\s*MAH\b|\d\s*W\b(?!\w)|\bBATTERY\b|\bCHARGER\b|\bUSB\b|\bWARRANTY\b|\bELECTRIC|\bLED\b|\d\s*V\s*~""") to 3,
        ),
        Category.GROCERY to listOf(
            rx("""\bATTA\b|\bFLOUR\b|\bRICE\b|\bDAL\b|\bPULSES?\b|\bMASALA\b|\bSPICES?\b|\bGHEE\b|\bBESAN\b|\bPOHA\b|\bSOOJI\b|\bRAVA\b|\bJAGGERY\b|IODI[SZ]ED|\bPICKLE\b|ACHAR|\bTEA\b|\bCOFFEE\b|COOKING\s+OIL|\bREFINED\b|\bSALT\b|\bSUGAR\b(?!S)""") to 3,
        ),
        Category.FOOD to listOf(
            rx("""\bCHOCOLATE\b|\bBISCUITS?\b|\bNOODLES\b|\bCHIPS\b|\bSNACKS?\b|ICE\s+CREAM|FROZEN\s+DESSERT|\bBEVERAGE\b|\bJUICE\b|READY\s+TO\s+(?:EAT|COOK)|\bNAMKEEN\b|\bCAKE\b|\bCOOKIES?\b|\bSWEETS?\b|\bDRINK\b""") to 3,
            rx("""\bINGREDIENTS\b|NUTRITION(?:AL)?\s+INFORMATION|\bKCAL\b|\bFSSAI\b|BEST\s+BEFORE|\bVEG\b""") to 1,
        ),
    )

    fun parse(lines: List<LabelParser.Line>): Result {
        val text = lines.joinToString(" ") { it.text }.replace(Regex("""\s+"""), " ")
        fun first(r: Regex, group: Int = 1) = r.find(text)?.groupValues?.get(group)?.trim()?.trimEnd(',', ';', ':', '-')?.takeIf { it.length >= 2 }

        val details = ProductDetails(
            netQuantity = first(net) ?: countPack.find(text)?.value?.trim(),
            ingredients = first(ingredients)?.let(::tidySentence),
            allergens = first(allergens)?.let(::tidySentence),
            composition = first(composition)?.let(::tidySentence),
            dosage = first(dosage)?.let(::tidySentence),
            prescription = schedule.find(text)?.let { m ->
                val sched = m.groupValues[1]
                if (sched.isNotEmpty()) "Prescription only (Schedule ${sched.uppercase()})" else "Prescription only"
            },
            fabric = first(fabric)?.uppercase()?.let(::tidyWords),
            size = first(size)?.uppercase(),
            care = care.findAll(text).map { tidySentence(it.value) }.distinct().take(3).joinToString(" · ").ifEmpty { null },
            manufacturer = first(mfr)?.let(::tidyWords),
            storage = first(storage)?.let(::tidySentence),
            origin = first(origin)?.let(::tidyWords),
            warnings = warning.findAll(text).map { tidySentence(it.value) }.distinct().take(2).joinToString(" · ").ifEmpty { null },
        )

        // Words inside the ingredient list ("sugar", "salt") say nothing about the kind of product.
        val clueText = ingredients.find(text)?.let { m -> text.removeRange(m.groups[1]!!.range) } ?: text
        val scores = clues.mapValues { (_, list) -> list.sumOf { (r, w) -> r.findAll(clueText).count().coerceAtMost(3) * w } }
            .filterValues { it > 0 }
        return Result(details, scores)
    }

    /** Picks the category from accumulated scores; null when there is too little evidence. */
    fun decide(scores: Map<Category, Int>): Category? {
        val best = scores.maxByOrNull { it.value } ?: return null
        if (best.value < 3) return null
        // Staples outrank generic food clues (FSSAI, ingredients) printed on every food pack.
        val grocery = scores[Category.GROCERY] ?: 0
        if (best.key == Category.FOOD && grocery >= 3 && grocery >= best.value - 3) return Category.GROCERY
        return best.key
    }

    /** Category from Open Food Facts category tags (everything there is food or grocery). */
    fun fromFoodTags(tags: List<String>): Category {
        val staples = listOf(
            "flour", "rice", "legume", "pulse", "lentil", "vegetable-oil", "cooking-oil", "spice", "condiment", "salt",
            "sugars", "tea", "coffee", "pickle", "ghee", "cereal-grain", "grains", "semolina", "jaggery", "honeys",
        )
        val t = tags.joinToString(" ").lowercase()
        return if (staples.any { t.contains(it) }) Category.GROCERY else Category.FOOD
    }

    private fun tidySentence(s: String): String {
        val t = s.trim().trimEnd('.', ',', ';', ':').replace(Regex("""\s+"""), " ")
        val lower = t.lowercase()
        return lower.replaceFirstChar { it.uppercase() }.replace(Regex("""\b(ip|bp|usp|mg|ml)\b""")) { it.value.uppercase() }
    }

    private fun tidyWords(s: String): String = s.trim().split(' ').filter { it.isNotBlank() }.joinToString(" ") { w ->
        if (w.length <= 3 && w.all { it.isUpperCase() || it == '.' }) w else w.lowercase().replaceFirstChar { it.uppercase() }
    }
}

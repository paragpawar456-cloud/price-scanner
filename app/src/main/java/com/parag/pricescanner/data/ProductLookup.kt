package com.parag.pricescanner.data

import com.parag.pricescanner.scan.Nutrition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Looks up a barcode on Open Food Facts (free, open product database with good
 * coverage of Indian packaged foods). Returns null when offline or not found.
 */
object ProductLookup {

    data class Info(val name: String, val nutrition: Nutrition?, val imageUrl: String?)

    suspend fun fetch(barcode: String): Info? = withContext(Dispatchers.IO) {
        if (!barcode.all { it.isDigit() } || barcode.length !in 8..14) return@withContext null
        runCatching {
            val url = URL(
                "https://world.openfoodfacts.org/api/v2/product/$barcode.json" +
                    "?fields=product_name,product_name_en,brands,quantity,nutriments,image_front_small_url,image_front_url,image_url"
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                setRequestProperty("User-Agent", "PriceScan-Android/1.2")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val root = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                if (root.optInt("status") != 1) return@runCatching null
                parse(root.getJSONObject("product"))
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun imageOf(p: JSONObject): String? =
        listOf("image_front_url", "image_front_small_url", "image_url")
            .map { p.optString(it) }.firstOrNull { it.startsWith("https://") }

    /**
     * Finds a photo by product name, for packs without a barcode.
     * Returns null when nothing close enough is found.
     */
    suspend fun searchImage(name: String): String? = withContext(Dispatchers.IO) {
        val query = name.trim()
        if (query.count { it.isLetter() } < 4 || query.startsWith("Item ₹") || query == "New item") return@withContext null
        runCatching {
            val url = URL(
                "https://world.openfoodfacts.org/cgi/search.pl?action=process&json=1&search_simple=1&page_size=8" +
                    "&fields=product_name,brands,image_front_url,image_front_small_url,image_url" +
                    "&search_terms=" + java.net.URLEncoder.encode(query, "UTF-8")
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 10000
                setRequestProperty("User-Agent", "PriceScan-Android/1.3")
            }
            try {
                if (conn.responseCode != 200) return@runCatching null
                val products = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("products")
                    ?: return@runCatching null
                val words = query.lowercase().split(Regex("""\s+""")).filter { it.length >= 3 }
                // Prefer a result whose name/brand shares the most words with ours.
                (0 until products.length()).map { products.getJSONObject(it) }
                    .mapNotNull { p ->
                        val img = imageOf(p) ?: return@mapNotNull null
                        val text = (p.optString("product_name") + " " + p.optString("brands")).lowercase()
                        val hits = words.count { text.contains(it) }
                        if (hits == 0) null else hits to img
                    }
                    .maxByOrNull { it.first }?.second
            } finally {
                conn.disconnect()
            }
        }.getOrNull()
    }

    private fun parse(p: JSONObject): Info? {
        val product = p.optString("product_name_en").ifBlank { p.optString("product_name") }.trim()
        val brand = p.optString("brands").split(',').firstOrNull()?.trim().orEmpty()
        val qty = p.optString("quantity").trim()
        val name = listOf(
            if (brand.isNotEmpty() && !product.contains(brand, ignoreCase = true)) brand else "",
            product,
            qty,
        ).filter { it.isNotEmpty() }.joinToString(" ").trim()
        if (name.isEmpty()) return null

        val n = p.optJSONObject("nutriments")
        fun d(key: String): Double? = n?.takeIf { it.has(key) }?.optDouble(key)?.takeIf { !it.isNaN() }
        val sodiumMg = d("sodium_100g")?.times(1000) ?: d("salt_100g")?.times(400)
        val nutrition = Nutrition(
            energyKcal = d("energy-kcal_100g") ?: d("energy_100g")?.div(4.184),
            protein = d("proteins_100g"),
            carbs = d("carbohydrates_100g"),
            sugar = d("sugars_100g"),
            addedSugar = d("added-sugars_100g"),
            fat = d("fat_100g"),
            saturatedFat = d("saturated-fat_100g"),
            transFat = d("trans-fat_100g"),
            sodiumMg = sodiumMg,
            fiber = d("fiber_100g"),
        ).takeIf { !it.isEmpty }
        return Info(name, nutrition, imageOf(p))
    }
}

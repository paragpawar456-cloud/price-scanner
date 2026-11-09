package com.parag.pricescanner.scan

import com.parag.pricescanner.scan.LabelParser.Box
import com.parag.pricescanner.scan.LabelParser.Line
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailsParserTest {
    private fun lines(vararg t: String) = t.mapIndexed { i, s -> Line(s, Box(0, i * 30, 400, i * 30 + 20), emptyList()) }
    private fun parse(vararg t: String) = DetailsParser.parse(lines(*t))

    @Test fun medicine() {
        val r = parse(
            "PARACIP-500", "Paracetamol Tablets IP 500 mg",
            "COMPOSITION: Each uncoated tablet contains: Paracetamol IP 500 mg",
            "Dosage: As directed by the physician.", "Store in a cool dry place, protected from light.",
            "SCHEDULE H PRESCRIPTION DRUG - CAUTION", "Keep out of reach of children", "10 Tablets",
        )
        assertEquals(Category.MEDICINE, DetailsParser.decide(r.categoryScores))
        assertEquals("Prescription only (Schedule H)", r.details.prescription)
        assertTrue(r.details.composition!!.contains("Paracetamol IP 500 MG", ignoreCase = true))
        assertEquals("10 Tablets", r.details.netQuantity)
        assertTrue(r.details.warnings!!.contains("reach of children"))
    }

    @Test fun clothing() {
        val r = parse("SIZE: XL", "60% COTTON 40% POLYESTER", "MACHINE WASH COLD WITH LIKE COLOURS", "DO NOT BLEACH", "MADE IN INDIA")
        assertEquals(Category.CLOTHING, DetailsParser.decide(r.categoryScores))
        assertEquals("60% Cotton 40% Polyester", r.details.fabric)
        assertEquals("XL", r.details.size)
        assertEquals("India", r.details.origin)
    }

    @Test fun foodPack() {
        val r = parse(
            "INGREDIENTS: Milk solids, sugar, cocoa butter, almonds (8%), emulsifier.",
            "CONTAINS MILK, ALMOND.", "Frozen dessert", "Nutritional Information per 100 g", "FSSAI Lic. No. 10012022000217",
            "Net Qty: 80 ml", "Mktd. by: Hindustan Unilever Ltd.",
        )
        assertEquals(Category.FOOD, DetailsParser.decide(r.categoryScores))
        assertTrue(r.details.ingredients!!.startsWith("Milk solids"))
        assertEquals("80 ml", r.details.netQuantity)
        assertEquals("Hindustan Unilever Ltd.", r.details.manufacturer)
    }

    @Test fun groceryAtta() {
        val r = parse("WHOLE WHEAT ATTA", "Ingredients: 100% whole wheat", "FSSAI", "Net Wt 5 kg", "Best before 3 months")
        assertEquals(Category.GROCERY, DetailsParser.decide(r.categoryScores))
        assertEquals("5 kg", r.details.netQuantity)
    }

    @Test fun detailsRoundTrip() {
        val d = ProductDetails(brand = "Amul", ingredients = "Milk, sugar", prescription = null, size = "M")
        assertEquals(d, ProductDetails.decode(d.encode()))
    }
}

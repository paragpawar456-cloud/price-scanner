package com.parag.pricescanner.scan

import com.parag.pricescanner.scan.LabelParser.Box
import com.parag.pricescanner.scan.LabelParser.Line
import com.parag.pricescanner.scan.LabelParser.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SessionTrackerTest {
    private val empty = FrameResult(emptyList(), null, null)
    private fun price(v: Double, name: String? = null, barcode: String? = null) =
        FrameResult(listOf(PriceParser.Candidate(v, 100)), name, barcode)

    @Test fun priceThenBarcodeIsOneItem() {
        val t = SessionTracker()
        var counts = 0
        var now = 0L
        // price visible first, then the pack is turned and the barcode shows, then the nutrition table
        repeat(4) { if (t.onFrame(price(140.0, "Mango Pickle"), now)?.countNow == true) counts++; now += 100 }
        repeat(4) { if (t.onFrame(FrameResult(emptyList(), null, "8903553601491"), now)?.countNow == true) counts++; now += 100 }
        repeat(3) { if (t.onFrame(FrameResult(emptyList(), "Nutrition", null, nutrition = Nutrition(sugar = 2.0)), now)?.countNow == true) counts++; now += 100 }
        val snap = t.onFrame(price(140.0), now)!!
        assertEquals(1, counts)
        assertEquals("8903553601491", snap.barcode)
        assertEquals(140.0, snap.price!!, 0.0)
        assertEquals(2.0, snap.nutrition!!.sugar!!, 0.0)
    }

    @Test fun ocrJitterDoesNotSplit() {
        val t = SessionTracker()
        var counts = 0
        var now = 0L
        listOf(14.0, 140.0, 140.0, 1.4, 140.0, 14.0, 140.0).forEach {
            if (t.onFrame(price(it), now)?.countNow == true) counts++
            now += 100
        }
        assertEquals(1, counts)
        assertEquals(140.0, t.onFrame(price(140.0), now)!!.price!!, 0.0)
    }

    @Test fun fivePacketsWithGaps() {
        val t = SessionTracker(clearMillis = 900)
        var counts = 0
        var now = 0L
        repeat(5) {
            repeat(6) { if (t.onFrame(price(14.0, "Maggi"), now)?.countNow == true) counts++; now += 100 }
            repeat(10) { t.onFrame(empty, now); now += 100 }
        }
        assertEquals(5, counts)
    }

    @Test fun briefDropoutStaysSameItem() {
        val t = SessionTracker(clearMillis = 900)
        var counts = 0
        var now = 0L
        repeat(5) { if (t.onFrame(price(20.0), now)?.countNow == true) counts++; now += 100 }
        repeat(4) { t.onFrame(empty, now); now += 100 }
        repeat(5) { if (t.onFrame(price(20.0), now)?.countNow == true) counts++; now += 100 }
        assertEquals(1, counts)
    }
}

class NutritionParserTest {
    private fun line(x: Int, y: Int, text: String, h: Int = 20): Line {
        val words = mutableListOf<Word>()
        var cx = x
        text.split(" ").forEach { w -> words += Word(w, Box(cx, y, cx + w.length * 10, y + h)); cx += w.length * 10 + 10 }
        return Line(text, Box(x, y, cx - 10, y + h), words)
    }

    @Test fun inlineValues() {
        val n = NutritionParser.parse(
            listOf(
                line(0, 0, "Energy 1786 kJ / 427 kcal"),
                line(0, 30, "Protein 8.9 g"),
                line(0, 60, "Carbohydrate 61.2 g"),
                line(0, 90, "Total Sugars 2.1 g"),
                line(0, 120, "Added Sugars 0.6 g"),
                line(0, 150, "Total Fat 16.4 g"),
                line(0, 180, "Saturated Fat 7.6 g"),
                line(0, 210, "Trans Fat 0.1 g"),
                line(0, 240, "Sodium 1025 mg"),
            )
        )
        assertEquals(427.0, n.energyKcal!!, 0.01)
        assertEquals(8.9, n.protein!!, 0.0)
        assertEquals(2.1, n.sugar!!, 0.0)
        assertEquals(0.6, n.addedSugar!!, 0.0)
        assertEquals(16.4, n.fat!!, 0.0)
        assertEquals(7.6, n.saturatedFat!!, 0.0)
        assertEquals(0.1, n.transFat!!, 0.0)
        assertEquals(1025.0, n.sodiumMg!!, 0.0)
        assertEquals(listOf("High saturated fat", "High sodium"), n.warnings())
    }

    @Test fun columnTable() {
        // labels in one column, per-100g values in the next, per-serving values after that
        val n = NutritionParser.parse(
            listOf(
                line(0, 0, "Saturated Fat (g)"), line(300, 2, "7.6"), line(400, 2, "5.3"),
                line(0, 40, "Trans Fat (g)"), line(300, 42, "0.1"), line(400, 42, "0.07"),
                line(0, 80, "Total Sugars (g)"), line(300, 82, "2.1"), line(400, 82, "1.5"),
                line(0, 120, "Sodium (mg)"), line(300, 122, "1025"), line(400, 122, "717"),
            )
        )
        assertEquals(7.6, n.saturatedFat!!, 0.0)
        assertEquals(0.1, n.transFat!!, 0.0)
        assertEquals(2.1, n.sugar!!, 0.0)
        assertEquals(1025.0, n.sodiumMg!!, 0.0)
    }

    @Test fun encodeRoundTrip() {
        val n = Nutrition(energyKcal = 427.0, sugar = 2.1, transFat = 0.0)
        assertNotNull(Nutrition.decode(n.encode()))
        assertEquals(n, Nutrition.decode(n.encode()))
    }
}

package com.parag.pricescanner.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PriceParserTest {
    @Test fun mrpWithRupee() = assertEquals(14.0, PriceParser.bestPrice("MRP ₹14.00 (incl. of all taxes)"))
    @Test fun mrpDotted() = assertEquals(1299.0, PriceParser.bestPrice("M.R.P. Rs. 1,299/-"))
    @Test fun rsOnly() = assertEquals(20.0, PriceParser.bestPrice("Net Wt 70g\nRs 20"))
    @Test fun slashDash() = assertEquals(55.0, PriceParser.bestPrice("Only 55/-"))
    @Test fun ocrSpacedDecimal() = assertEquals(12.5, PriceParser.bestPrice("MRP: ₹ 12. 50"))
    @Test fun prefersMrpOverUnitPrice() =
        assertEquals(96.0, PriceParser.bestPrice("Unit sale price ₹ 342.86/kg  MRP ₹ 96.00"))
    @Test fun ignoresPlainNumbers() = assertNull(PriceParser.bestPrice("Net Wt 280 g  Batch 2245 Pkd 09/24"))
    @Test fun empty() = assertNull(PriceParser.bestPrice(""))
}

class NameGuesserTest {
    @Test fun picksTallestBrandLine() {
        val name = NameGuesser.guess(
            listOf(
                NameGuesser.Line("MRP ₹14.00", 40),
                NameGuesser.Line("MAGGI", 90),
                NameGuesser.Line("2-Minute Noodles", 50),
            )
        )
        assertEquals("Maggi", name)
    }
}

class AutoCounterTest {
    @Test fun countsFivePacketsOfSameItem() {
        val c = AutoCounter(stableFrames = 3, clearMillis = 700)
        var t = 0L
        var count = 0
        repeat(5) {
            repeat(6) { if (c.onFrame("maggi", t) != null) count++; t += 100 }
            repeat(8) { c.onFrame(null, t); t += 100 } // packet moved away
        }
        assertEquals(5, count)
    }

    @Test fun holdingStillCountsOnce() {
        val c = AutoCounter()
        var count = 0
        for (i in 0 until 100) if (c.onFrame("maggi", i * 100L) != null) count++
        assertEquals(1, count)
    }

    @Test fun briefDropoutDoesNotRecount() {
        val c = AutoCounter(stableFrames = 3, clearMillis = 700)
        var count = 0
        var t = 0L
        repeat(5) { if (c.onFrame("a", t) != null) count++; t += 100 }
        repeat(2) { c.onFrame(null, t); t += 100 }
        repeat(5) { if (c.onFrame("a", t) != null) count++; t += 100 }
        assertEquals(1, count)
    }

    @Test fun differentItemCountsImmediately() {
        val c = AutoCounter(stableFrames = 2)
        var count = 0
        var t = 0L
        repeat(3) { if (c.onFrame("a", t) != null) count++; t += 100 }
        repeat(3) { if (c.onFrame("b", t) != null) count++; t += 100 }
        assertEquals(2, count)
    }
}

package com.parag.pricescanner.scan

import com.parag.pricescanner.scan.LabelParser.Box
import com.parag.pricescanner.scan.LabelParser.Line
import com.parag.pricescanner.scan.LabelParser.Word
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class LabelParserTest {
    private val today = LocalDate.of(2026, 11, 9)

    /** Builds a line whose words are laid out left to right at a given row. */
    private fun line(x: Int, y: Int, vararg words: String, h: Int = 20, charW: Int = 10): Line {
        var cx = x
        val ws = words.map { w ->
            val box = Box(cx, y, cx + w.length * charW, y + h)
            cx = box.right + charW
            Word(w, box)
        }
        return Line(words.joinToString(" "), Box(x, y, cx - charW, y + h), ws)
    }

    // The pickle-jar label: headings with arrows, values underneath.
    private val jar = listOf(
        line(190, 860, "NET", "WEIGHT:", "500g"),
        line(190, 895, "MRP", "RS.", "₹↓", h = 16, charW = 8),
        line(190, 915, "(INCLUSIVE", "OF", "ALL", "TAXES)", h = 16, charW = 8),
        line(400, 900, "USP", "₹/g↓", h = 16, charW = 8),
        line(670, 905, "BATCH", "NO.↓", h = 16, charW = 8),
        line(190, 975, "140.00", h = 40, charW = 16),
        line(325, 990, "0.28", h = 40, charW = 16),
        line(510, 985, "DW", "D1212616", h = 40, charW = 16),
        line(190, 1040, "12", "SEP.", "26", h = 40, charW = 16),
        line(470, 1055, "11JUN.27", h = 40, charW = 16),
        line(180, 1150, "DATE", "OF", "PACKAGING↑", h = 16, charW = 8),
        line(680, 1180, "USE", "BY↑", h = 16, charW = 8),
    )

    @Test fun jarPrice() = assertEquals(140.0, LabelParser.parse(jar, today).prices.first().value, 0.0)

    @Test fun jarDates() {
        val r = LabelParser.parse(jar, today)
        assertEquals(LocalDate.of(2026, 9, 12), r.mfgDate)
        assertEquals(LocalDate.of(2027, 6, 11), r.expiryDate)
    }

    @Test fun inlineKeywords() {
        val r = LabelParser.parse(
            listOf(
                line(0, 0, "MRP", "₹20.00"),
                line(0, 30, "MFG:", "08/2026"),
                line(0, 60, "EXP:", "07/2027"),
            ), today,
        )
        assertEquals(20.0, r.prices.first().value, 0.0)
        assertEquals(LocalDate.of(2026, 8, 1), r.mfgDate)
        assertEquals(LocalDate.of(2027, 7, 31), r.expiryDate)
    }

    @Test fun bestBeforeMonths() {
        val r = LabelParser.parse(
            listOf(line(0, 0, "PKD", "ON:", "05.10.2026"), line(0, 30, "BEST", "BEFORE", "9", "MONTHS", "FROM", "PACKAGING")),
            today,
        )
        assertEquals(LocalDate.of(2026, 10, 5), r.mfgDate)
        assertEquals(LocalDate.of(2027, 7, 5), r.expiryDate)
    }

    @Test fun twoUnlabelledDates() {
        val r = LabelParser.parse(listOf(line(0, 0, "15/03/2026"), line(0, 30, "14/03/2027")), today)
        assertEquals(LocalDate.of(2026, 3, 15), r.mfgDate)
        assertEquals(LocalDate.of(2027, 3, 14), r.expiryDate)
    }

    @Test fun noDates() {
        val r = LabelParser.parse(listOf(line(0, 0, "MRP", "Rs", "10")), today)
        assertNull(r.mfgDate)
        assertNull(r.expiryDate)
        assertEquals(10.0, r.prices.first().value, 0.0)
    }

    @Test fun dateFormats() {
        assertEquals(listOf(LocalDate.of(2027, 6, 11)), LabelParser.findDates("11JUN.27"))
        assertEquals(listOf(LocalDate.of(2026, 9, 12)), LabelParser.findDates("12 SEP. 26"))
        assertEquals(listOf(LocalDate.of(2026, 1, 1)), LabelParser.findDates("JAN 2026"))
        assertEquals(listOf(LocalDate.of(2026, 3, 15)), LabelParser.findDates("15-03-26"))
    }
}

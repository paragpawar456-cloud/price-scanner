package com.parag.pricescanner.export

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import com.parag.pricescanner.data.Purchase
import com.parag.pricescanner.util.Fmt
import java.io.File
import java.time.LocalDateTime

/** Builds an A4 expense report: summary, then a date-wise table with daily subtotals. */
object PdfExporter {

    private const val W = 595
    private const val H = 842
    private const val M = 36f

    private val indigo = Color.rgb(79, 70, 229)
    private val ink = Color.rgb(15, 23, 42)
    private val muted = Color.rgb(100, 116, 139)
    private val line = Color.rgb(226, 232, 240)
    private val band = Color.rgb(238, 242, 255)

    fun export(context: Context, purchases: List<Purchase>, periodLabel: String): File {
        val items = purchases.sortedBy { it.timestamp }
        val now = LocalDateTime.now()
        val doc = PdfDocument()

        val title = paint(20f, ink, bold = true)
        val h2 = paint(11f, muted)
        val head = paint(9.5f, Color.WHITE, bold = true)
        val cell = paint(9.5f, ink)
        val cellMuted = paint(9f, muted)
        val cellBold = paint(9.5f, ink, bold = true)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Table columns: Date | Product | Qty | Unit price | Amount
        val colX = floatArrayOf(M, M + 80, M + 330, M + 380, M + 460)
        val right = W - M

        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun footer() {
            val p = paint(8.5f, muted)
            c.drawLine(M, H - 34f, right, H - 34f, stroke(line))
            c.drawText("PriceScan expense report · generated ${Fmt.stamp(now)}", M, H - 20f, p)
            p.textAlign = Paint.Align.RIGHT
            c.drawText("Page $pageNo", right, H - 20f, p)
        }

        fun tableHeader() {
            fill.color = indigo
            c.drawRoundRect(RectF(M, y, right, y + 22f), 6f, 6f, fill)
            val ty = y + 15f
            c.drawText("Date", colX[0] + 8, ty, head)
            c.drawText("Product", colX[1], ty, head)
            drawRight(c, "Qty", colX[3] - 12, ty, head)
            drawRight(c, "Unit price", colX[4] - 12, ty, head)
            drawRight(c, "Amount", right - 8, ty, head)
            y += 30f
        }

        fun newPage(first: Boolean) {
            if (pageNo > 0) { footer(); doc.finishPage(page) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page.canvas
            y = M
            if (first) {
                fill.color = indigo
                c.drawRoundRect(RectF(M, y, M + 6f, y + 44f), 3f, 3f, fill)
                c.drawText("Expense Report", M + 16, y + 20f, title)
                c.drawText(periodLabel, M + 16, y + 40f, h2)
                y += 64f

                // Summary boxes
                val total = items.sumOf { it.total }
                val qty = items.sumOf { it.quantity }
                val days = items.map { Fmt.date(it.timestamp) }.distinct().size
                val boxes = listOf(
                    "Total spent" to Fmt.money(total),
                    "Items bought" to "$qty",
                    "Entries" to "${items.size}",
                    "Avg per day" to Fmt.money(if (days > 0) total / days else 0.0),
                )
                val bw = (right - M - 3 * 10f) / 4
                boxes.forEachIndexed { i, (label, value) ->
                    val x = M + i * (bw + 10f)
                    fill.color = band
                    c.drawRoundRect(RectF(x, y, x + bw, y + 54f), 10f, 10f, fill)
                    c.drawText(label, x + 12, y + 20f, paint(9f, muted))
                    c.drawText(value, x + 12, y + 41f, paint(14f, indigo, bold = true))
                }
                y += 76f

                // Top products
                val top = items.groupBy { it.productName.trim().lowercase() }
                    .map { (_, l) -> Triple(l.first().productName, l.sumOf { it.quantity }, l.sumOf { it.total }) }
                    .sortedByDescending { it.third }.take(5)
                if (top.isNotEmpty()) {
                    c.drawText("Top products", M, y, paint(12f, ink, bold = true))
                    y += 16f
                    top.forEach { (name, q, amt) ->
                        c.drawText(ellipsize(name, cell, 300f), M, y, cell)
                        drawRight(c, "qty $q", colX[3] - 12, y, cellMuted)
                        drawRight(c, Fmt.money(amt), right - 8, y, cellBold)
                        y += 15f
                    }
                    y += 14f
                }
                c.drawText("Date-wise records", M, y, paint(12f, ink, bold = true))
                y += 10f
            }
            tableHeader()
        }

        newPage(first = true)

        if (items.isEmpty()) {
            c.drawText("No purchases recorded for this period.", M, y + 10f, cellMuted)
        }

        val byDay = items.groupBy { Fmt.date(it.timestamp) }.toSortedMap()
        for ((day, list) in byDay) {
            if (y > H - 90f) newPage(first = false)
            var firstRow = true
            for (p in list) {
                if (y > H - 70f) { newPage(first = false); firstRow = true }
                if (firstRow) c.drawText(Fmt.dayShort(day) + " " + day.year, colX[0] + 8, y, cellBold)
                c.drawText(ellipsize(p.productName, cell, 230f), colX[1], y, cell)
                drawRight(c, "${p.quantity}", colX[3] - 12, y, cell)
                drawRight(c, Fmt.money(p.unitPrice), colX[4] - 12, y, cell)
                drawRight(c, Fmt.money(p.total), right - 8, y, cell)
                y += 6f
                c.drawLine(colX[1], y, right, y, stroke(line))
                y += 12f
                firstRow = false
            }
            // Day subtotal
            fill.color = band
            c.drawRoundRect(RectF(colX[1] - 6, y - 11f, right, y + 7f), 4f, 4f, fill)
            c.drawText("Day total · ${list.sumOf { it.quantity }} items", colX[1], y + 2f, cellMuted)
            drawRight(c, Fmt.money(list.sumOf { it.total }), right - 8, y + 2f, cellBold)
            y += 24f
        }

        if (items.isNotEmpty()) {
            if (y > H - 80f) newPage(first = false)
            fill.color = indigo
            c.drawRoundRect(RectF(M, y, right, y + 30f), 8f, 8f, fill)
            c.drawText("Grand total", M + 12, y + 20f, paint(11f, Color.WHITE, bold = true))
            drawRight(c, Fmt.money(items.sumOf { it.total }), right - 12, y + 20f, paint(12f, Color.WHITE, bold = true))
        }
        footer()
        doc.finishPage(page)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "PriceScan_Report_${Fmt.fileStamp(now)}.pdf")
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
        return file
    }

    fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)

    fun share(context: Context, file: File) {
        val uri = uriFor(context, file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "PriceScan expense report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share report").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun open(context: Context, file: File): Boolean {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uriFor(context, file), "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching { context.startActivity(view) }.isSuccess
    }

    private fun paint(size: Float, color: Int, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size
        this.color = color
        typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun stroke(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeWidth = 0.8f }

    private fun drawRight(c: Canvas, text: String, x: Float, y: Float, p: Paint) {
        c.drawText(text, x - p.measureText(text), y, p)
    }

    private fun ellipsize(text: String, p: Paint, maxWidth: Float): String {
        if (p.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && p.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}

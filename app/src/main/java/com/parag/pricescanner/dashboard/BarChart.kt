package com.parag.pricescanner.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parag.pricescanner.util.Fmt

@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.outlineVariant
    val measurer = rememberTextMeasurer()
    var selected by remember(bars.size) { mutableIntStateOf(bars.lastIndex) }
    val grow = remember { Animatable(0f) }
    LaunchedEffect(bars) {
        grow.snapTo(0f)
        grow.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val max = (bars.maxOfOrNull { it.value } ?: 0.0).takeIf { it > 0 } ?: 1.0

    Column(modifier) {
        val sel = bars.getOrNull(selected)
        if (sel != null) {
            Text(sel.detail, style = MaterialTheme.typography.labelMedium, color = labelColor)
            Text(Fmt.money(sel.value), style = MaterialTheme.typography.titleLarge, color = primary)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .pointerInput(bars) {
                    detectTapGestures { pos ->
                        if (bars.isNotEmpty()) {
                            val slot = size.width / bars.size
                            selected = (pos.x / slot).toInt().coerceIn(0, bars.lastIndex)
                        }
                    }
                },
        ) {
            if (bars.isEmpty()) return@Canvas
            val labelSpace = 22.dp.toPx()
            val chartH = size.height - labelSpace
            val slot = size.width / bars.size
            val barW = (slot * if (bars.size > 14) 0.62f else 0.5f).coerceAtMost(28.dp.toPx())
            val radius = CornerRadius(barW / 2.5f)

            // dashed guide lines
            for (i in 0..3) {
                val y = chartH * i / 3f
                drawLine(
                    grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
                )
            }
            drawText(
                measurer, Fmt.compact(max), topLeft = Offset(0f, 2.dp.toPx()),
                style = TextStyle(color = labelColor, fontSize = 10.sp),
            )

            bars.forEachIndexed { i, bar ->
                val x = slot * i + (slot - barW) / 2
                drawRoundRect(track, Offset(x, 0f), Size(barW, chartH), radius, alpha = 0.55f)
                val h = (bar.value / max * chartH * grow.value).toFloat()
                if (h > 0f) {
                    val isSel = i == selected
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(primary, primary.copy(alpha = if (isSel) 0.85f else 0.55f)),
                            startY = chartH - h, endY = chartH,
                        ),
                        topLeft = Offset(x, chartH - h),
                        size = Size(barW, h),
                        cornerRadius = radius,
                        alpha = if (isSel || selected !in bars.indices) 1f else 0.6f,
                    )
                }
                if (bar.label.isNotEmpty()) {
                    val layout = measurer.measure(bar.label, TextStyle(color = labelColor, fontSize = 11.sp))
                    drawText(
                        layout,
                        topLeft = Offset(
                            (slot * i + slot / 2 - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width),
                            chartH + 6.dp.toPx(),
                        ),
                    )
                }
            }
        }
    }
}

package com.parag.pricescanner.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.parag.pricescanner.ui.theme.AppTheme

/** The dark "ink" card used for hero numbers. Stays dark in both themes. */
@Composable
fun InkCard(modifier: Modifier = Modifier, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(AppTheme.brand.ink)
            .padding(padding),
        content = content,
    )
}

/** A number that counts up to [value] whenever it changes. */
@Composable
fun CountUpText(
    value: Double,
    format: (Double) -> String,
    style: TextStyle,
    color: Color = Color.Unspecified,
    modifier: Modifier = Modifier,
) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(value) { anim.animateTo(value.toFloat(), tween(900, easing = FastOutSlowInEasing)) }
    Text(
        format(anim.value.toDouble()),
        style = style,
        color = color,
        maxLines = 1,
        modifier = modifier.semantics { contentDescription = format(value) },
    )
}

/** Circular progress ring with content in the middle. Animates from empty. */
@Composable
fun Ring(
    progress: Float,
    color: Color,
    track: Color,
    modifier: Modifier = Modifier,
    size: Dp = 84.dp,
    stroke: Dp = 9.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val p by animateFloatAsState(
        progress.coerceIn(0f, 1f),
        spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow),
        label = "ring",
    )
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(s))
            if (p > 0f) drawArc(color, -90f, 360f * p, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Round))
        }
        center()
    }
}

data class Slice(val value: Double, val color: Color)

/** Donut chart; segments sweep in on first show. */
@Composable
fun Donut(
    slices: List<Slice>,
    modifier: Modifier = Modifier,
    size: Dp = 112.dp,
    stroke: Dp = 16.dp,
    center: @Composable BoxScope.() -> Unit = {},
) {
    val sweep = remember { Animatable(0f) }
    LaunchedEffect(Unit) { sweep.animateTo(1f, tween(1000, easing = FastOutSlowInEasing)) }
    val total = slices.sumOf { it.value }.takeIf { it > 0 } ?: 1.0
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val s = stroke.toPx()
            val d = this.size.minDimension - s
            val tl = Offset(s / 2, s / 2)
            val gap = if (slices.size > 1) 2.5f else 0f
            var start = -90f
            slices.forEach { sl ->
                val full = (sl.value / total * 360f).toFloat()
                val drawn = (full - gap).coerceAtLeast(0.5f) * sweep.value
                drawArc(sl.color, start, drawn, false, tl, Size(d, d), style = Stroke(s, cap = StrokeCap.Butt))
                start += full * sweep.value
            }
        }
        center()
    }
}

/** Fades and lifts a block in once, after [index] × 60 ms, for a staggered screen entrance. */
fun Modifier.enterUp(index: Int): Modifier = composed {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(index * 60L)
        a.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
    }
    graphicsLayer {
        alpha = a.value
        translationY = (1f - a.value) * 24.dp.toPx()
    }
}

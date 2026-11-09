package com.parag.pricescanner.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.parag.pricescanner.R

// Semantic colours (category, status, charts). Names kept for older call sites.
val Indigo = Color(0xFF4C4DDC)
val IndigoDeep = Color(0xFF3536B0)
val Violet = Color(0xFF7C3AED)
val Mint = Color(0xFF16A34A)
val Amber = Color(0xFFD97706)
val Rose = Color(0xFFE11D48)
val Sky = Color(0xFF0EA5E9)

// Brand: ink + lime
val Ink = Color(0xFF10131F)
val InkRaised = Color(0xFF1E2234)
val InkLine = Color(0xFF2A2F42)
val Lime = Color(0xFFC8F25C)
val Paper = Color(0xFFF4F5FA)

/** Colours for the dark "ink" surfaces (hero card, nav bar, scanner) that stay dark in both themes. */
@Immutable
data class Brand(
    val ink: Color = Ink,
    val inkRaised: Color = InkRaised,
    val inkLine: Color = InkLine,
    val onInk: Color = Color.White,
    val onInkMuted: Color = Color(0xFFB7BCCB),
    /** Accent used on ink: lime by default, the wallpaper colour with dynamic colour on. */
    val accent: Color = Lime,
    val onAccent: Color = Ink,
    val good: Color = Mint,
    val warn: Color = Amber,
    val bad: Color = Rose,
)

val LocalBrand = staticCompositionLocalOf { Brand() }

object AppTheme {
    val brand: Brand @Composable get() = LocalBrand.current
}

private val Light = lightColorScheme(
    primary = Ink,
    onPrimary = Color.White,
    primaryContainer = Lime,
    onPrimaryContainer = Ink,
    secondary = Indigo,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE4E4FB),
    onSecondaryContainer = Color(0xFF1E1B4B),
    tertiary = Amber,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEEF0F5),
    onSurfaceVariant = Color(0xFF5B6275),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F1F6),
    surfaceContainerHigh = Color(0xFFE9EBF2),
    surfaceContainerHighest = Color(0xFFE2E5EE),
    outline = Color(0xFFC9CDD9),
    outlineVariant = Color(0xFFE6E8F0),
    error = Color(0xFFC81E4A),
)

private val Dark = darkColorScheme(
    primary = Lime,
    onPrimary = Ink,
    primaryContainer = InkLine,
    onPrimaryContainer = Lime,
    secondary = Color(0xFFA5A6F6),
    onSecondary = Color(0xFF1E1B4B),
    secondaryContainer = Color(0xFF2E2F6B),
    onSecondaryContainer = Color(0xFFE4E4FB),
    tertiary = Color(0xFFFBBF24),
    background = Color(0xFF0B0D16),
    onBackground = Color(0xFFF2F3F8),
    surface = Color(0xFF0B0D16),
    onSurface = Color(0xFFF2F3F8),
    surfaceVariant = Color(0xFF1E2234),
    onSurfaceVariant = Color(0xFF9CA2B5),
    surfaceContainerLowest = Color(0xFF07080F),
    surfaceContainerLow = Color(0xFF171A27),
    surfaceContainer = Color(0xFF1A1D2B),
    surfaceContainerHigh = Color(0xFF222636),
    surfaceContainerHighest = Color(0xFF2A2F42),
    outline = Color(0xFF3A3F55),
    outlineVariant = Color(0xFF262A3B),
    error = Color(0xFFFB7185),
)

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

/** Headings: Plus Jakarta Sans. Body: Inter. Both are variable fonts bundled at build time. */
val Display = FontFamily(listOf(500, 600, 700, 800).map { variable(R.font.plus_jakarta_sans, it) })
val Body = FontFamily(listOf(400, 500, 600, 700).map { variable(R.font.inter, it) })

private val AppTypography = Typography().run {
    fun TextStyle.d(w: FontWeight, ls: Float = 0f) = copy(fontFamily = Display, fontWeight = w, letterSpacing = ls.sp)
    fun TextStyle.b(w: FontWeight? = null) = copy(fontFamily = Body, fontWeight = w ?: fontWeight)
    copy(
        displayLarge = displayLarge.d(FontWeight.ExtraBold, -1f),
        displayMedium = displayMedium.d(FontWeight.ExtraBold, -0.8f),
        displaySmall = displaySmall.d(FontWeight.ExtraBold, -0.6f),
        headlineLarge = headlineLarge.d(FontWeight.ExtraBold, -0.5f),
        headlineMedium = headlineMedium.d(FontWeight.ExtraBold, -0.4f),
        headlineSmall = headlineSmall.d(FontWeight.Bold, -0.2f),
        titleLarge = titleLarge.d(FontWeight.Bold, -0.1f),
        titleMedium = titleMedium.d(FontWeight.Bold),
        titleSmall = titleSmall.d(FontWeight.Bold),
        bodyLarge = bodyLarge.b(),
        bodyMedium = bodyMedium.b(),
        bodySmall = bodySmall.b(),
        labelLarge = labelLarge.b(FontWeight.SemiBold),
        labelMedium = labelMedium.b(FontWeight.SemiBold),
        labelSmall = labelSmall.b(FontWeight.Medium),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun PriceScanTheme(
    dark: Boolean = isSystemInDarkTheme(),
    dynamic: Boolean = true,
    content: @Composable () -> Unit,
) {
    val useDynamic = dynamic && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme: ColorScheme
    val brand: Brand
    if (useDynamic) {
        val ctx = LocalContext.current
        val dl = dynamicLightColorScheme(ctx)
        val dd = dynamicDarkColorScheme(ctx)
        // Keep our neutral surfaces and ink identity; take the accent from the wallpaper.
        scheme = if (dark) Dark.copy(primary = dd.primary, onPrimary = dd.onPrimary, onPrimaryContainer = dd.primary, secondary = dd.secondary)
        else Light.copy(primaryContainer = dl.primaryContainer, onPrimaryContainer = dl.onPrimaryContainer, secondary = dl.primary)
        brand = Brand(accent = dd.primary, onAccent = dd.onPrimary)
    } else {
        scheme = if (dark) Dark else Light
        brand = Brand()
    }
    CompositionLocalProvider(LocalBrand provides brand) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

val MoneyStyle = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp)

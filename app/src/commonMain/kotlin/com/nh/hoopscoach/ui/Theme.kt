package com.nh.hoopscoach.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.min

/**
 * Hoops Dynasty design tokens.
 * A quiet neutral base (cool paper in light, graphite in dark) with one accent
 * taken from the school you coach, so the app wears your team's colors.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val ink: Color,
    val sub: Color,
    val faint: Color,
    val line: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val good: Color,
    val bad: Color,
    val warn: Color,
    val gold: Color,
    /** Big feature cards: the accent in light mode, a deep school color with white text in dark mode. */
    val hero: Color,
    val onHero: Color,
)

private val LightBase = Palette(
    dark = false,
    bg = Color(0xFFF1F2F5), surface = Color(0xFFFFFFFF), surface2 = Color(0xFFF5F6F8),
    ink = Color(0xFF11141B), sub = Color(0xFF5E6573), faint = Color(0xFF9BA1AD), line = Color(0xFFE4E7EC),
    accent = Color(0xFF2456E5), onAccent = Color.White, accentSoft = Color(0xFFE6ECFC),
    good = Color(0xFF15803D), bad = Color(0xFFD12C2C), warn = Color(0xFFB45309), gold = Color(0xFFB7791F),
    hero = Color(0xFF2456E5), onHero = Color.White,
)

private val DarkBase = Palette(
    dark = true,
    bg = Color(0xFF0C0E12), surface = Color(0xFF161920), surface2 = Color(0xFF1E222B),
    ink = Color(0xFFEEF0F4), sub = Color(0xFF9CA3B0), faint = Color(0xFF5F6674), line = Color(0xFF272C36),
    accent = Color(0xFF6E95FF), onAccent = Color(0xFF0C0E12), accentSoft = Color(0xFF1C2540),
    good = Color(0xFF4ADE80), bad = Color(0xFFF87171), warn = Color(0xFFFBBF24), gold = Color(0xFFFACC15),
    hero = Color(0xFF2A4BB8), onHero = Color.White,
)

val LocalPalette = staticCompositionLocalOf { LightBase }

/** Shorthand used across screens: `P.ink`, `P.accent`… */
val P: Palette @Composable get() = LocalPalette.current

// ---------- color math ----------

fun contrast(a: Color, b: Color): Float {
    val la = a.luminance() + 0.05f
    val lb = b.luminance() + 0.05f
    return max(la, lb) / min(la, lb)
}

fun mix(a: Color, b: Color, t: Float): Color = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t,
    alpha = 1f,
)

fun saturation(c: Color): Float {
    val mx = max(c.red, max(c.green, c.blue))
    val mn = min(c.red, min(c.green, c.blue))
    return if (mx == 0f) 0f else (mx - mn) / mx
}

/** Black or white, whichever reads better on [bg]. */
fun onColor(bg: Color): Color = if (contrast(bg, Color.White) >= contrast(bg, Color(0xFF11141B))) Color.White else Color(0xFF11141B)

/** Turns a school's colors into an accent that reads well on the current background. */
fun accentFrom(primary: Color, secondary: Color, dark: Boolean): Color {
    var c = if (saturation(primary) < 0.18f && saturation(secondary) >= 0.3f) secondary else primary
    if (saturation(c) < 0.12f) c = if (dark) Color(0xFF6E95FF) else Color(0xFF2456E5)
    val surface = if (dark) DarkBase.surface else LightBase.surface
    val target = if (dark) Color.White else Color.Black
    var t = 0f
    var out = c
    while (contrast(out, surface) < 3.6f && t < 0.9f) {
        t += 0.06f
        out = mix(c, target, t)
    }
    return out
}

/** A school color deep enough to carry white text, for feature cards in dark mode. */
fun heroFrom(primary: Color, secondary: Color): Color {
    var c = if (saturation(primary) < 0.18f && saturation(secondary) >= 0.3f) secondary else primary
    if (saturation(c) < 0.12f) c = Color(0xFF2A4BB8)
    var out = c
    var t = 0f
    while (contrast(out, Color.White) < 4.5f && t < 0.9f) {
        t += 0.06f
        out = mix(c, Color.Black, t)
    }
    return out
}

fun palette(dark: Boolean, primary: Color?, secondary: Color?): Palette {
    val base = if (dark) DarkBase else LightBase
    if (primary == null) return base
    val accent = accentFrom(primary, secondary ?: primary, dark)
    return base.copy(
        accent = accent,
        onAccent = onColor(accent),
        accentSoft = mix(base.surface, accent, if (dark) 0.2f else 0.12f),
        hero = if (dark) heroFrom(primary, secondary ?: primary) else accent,
        onHero = if (dark) Color.White else onColor(accent),
    )
}


// ---------- type ----------

/** Condensed arena-style face for scores, ratings and titles. */
val Display = com.nh.hoopscoach.hoopsDisplayFont()

/** Clean geometric sans for everything you read. */
val Body = com.nh.hoopscoach.hoopsBodyFont()

object T {
    val hero = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 40.sp, lineHeight = 40.sp, letterSpacing = 0.3.sp)
    val title = TextStyle(fontFamily = Display, fontWeight = FontWeight.ExtraBold, fontSize = 32.sp, lineHeight = 34.sp, letterSpacing = 0.4.sp)
    val h2 = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 26.sp, letterSpacing = 0.3.sp)
    val num = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 26.sp, lineHeight = 28.sp)
    val numSm = TextStyle(fontFamily = Display, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 20.sp)
    val head = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 17.sp, lineHeight = 22.sp)
    val bodyB = TextStyle(fontFamily = Body, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp)
    val body = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 21.sp)
    val small = TextStyle(fontFamily = Body, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 17.sp)
    val label = TextStyle(fontFamily = Body, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp, letterSpacing = 1.1.sp)
}

@Composable
fun HoopsTheme(primary: Color? = null, secondary: Color? = null, content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val p = remember(dark, primary, secondary) { palette(dark, primary, secondary) }
    val scheme = if (dark) darkColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.ink,
        onBackground = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.sub, outline = p.line,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerLow = p.surface, error = p.bad,
    ) else lightColorScheme(
        primary = p.accent, onPrimary = p.onAccent, background = p.bg, surface = p.surface, onSurface = p.ink,
        onBackground = p.ink, surfaceVariant = p.surface2, onSurfaceVariant = p.sub, outline = p.line,
        surfaceContainer = p.surface, surfaceContainerHigh = p.surface2, surfaceContainerLow = p.surface, error = p.bad,
    )
    CompositionLocalProvider(LocalPalette provides p) {
        MaterialTheme(colorScheme = scheme, typography = Typography(bodyLarge = T.body.copy(color = p.ink), bodyMedium = T.body.copy(color = p.ink)), content = content)
    }
}

/** Ratings as the screens show them: a pro league's on a 2K-style scale (the app sets this), college ratings as they are. */
val LocalRatingScale = androidx.compose.runtime.staticCompositionLocalOf<(Int) -> Int> { { it } }

/** [v] as the screens show it (see [LocalRatingScale]). */
@Composable
fun shown(v: Int): Int = LocalRatingScale.current(v)

/** Tier color for a 0-99 rating. */
@Composable
fun tier(v: Int): Color {
    val d = P.dark
    return when {
        v >= 85 -> if (d) Color(0xFF34D399) else Color(0xFF059669)
        v >= 75 -> if (d) Color(0xFF60A5FA) else Color(0xFF2563EB)
        v >= 65 -> if (d) Color(0xFFFBBF24) else Color(0xFFB45309)
        else -> if (d) Color(0xFFF87171) else Color(0xFFC2410C)
    }
}

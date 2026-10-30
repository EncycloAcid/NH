package com.nh.hoopscoach.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.R

/** Dark court-side palette: near-black floor, orange ball, green for good ratings. */
object C {
    val bg = Color(0xFF111214)
    val card = Color(0xFF1C1D22)
    val card2 = Color(0xFF26272E)
    val line = Color(0xFF2A2B32)
    val text = Color(0xFFF2F2F4)
    val sub = Color(0xFF8E909A)
    val dim = Color(0xFF5D5F69)
    val orange = Color(0xFFF26B21)
    val orangeDim = Color(0xFF3A251B)
    val orangeText = Color(0xFFFF8A45)
    val green = Color(0xFF4ADE80)
    val greenDim = Color(0xFF15301F)
    val yellow = Color(0xFFFACC15)
    val red = Color(0xFFF04438)
    val redDim = Color(0xFF3A1A1A)
    val blue = Color(0xFF4F8EF7)
    val navy = Color(0xFF1D2A44)
}

@OptIn(ExperimentalTextApi::class)
private fun lexend(w: Int) = Font(
    R.font.lexend,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Lexend = FontFamily(lexend(300), lexend(400), lexend(500), lexend(600), lexend(700), lexend(800))

private val base = TextStyle(fontFamily = Lexend, color = C.text)

val HoopsType = Typography(
    displayLarge = base.copy(fontSize = 40.sp, fontWeight = FontWeight.Bold),
    headlineLarge = base.copy(fontSize = 32.sp, fontWeight = FontWeight.Bold),
    headlineMedium = base.copy(fontSize = 26.sp, fontWeight = FontWeight.Bold),
    headlineSmall = base.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = base.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = base.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = base.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
    bodyLarge = base.copy(fontSize = 16.sp),
    bodyMedium = base.copy(fontSize = 14.sp),
    bodySmall = base.copy(fontSize = 12.sp, color = C.sub),
    labelLarge = base.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = base.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
    labelSmall = base.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.8.sp),
)

@Composable
fun HoopsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.orange, onPrimary = Color.Black, background = C.bg, surface = C.card,
            onSurface = C.text, onBackground = C.text, surfaceVariant = C.card2, onSurfaceVariant = C.sub,
            outline = C.line, secondary = C.orangeText, error = C.red,
            surfaceContainer = C.card, surfaceContainerHigh = C.card2, surfaceContainerLow = C.card,
        ),
        typography = HoopsType,
        content = content,
    )
}

fun ratingColor(v: Int): Color = when {
    v >= 80 -> C.green
    v >= 70 -> C.yellow
    v >= 60 -> C.orange
    else -> C.red
}

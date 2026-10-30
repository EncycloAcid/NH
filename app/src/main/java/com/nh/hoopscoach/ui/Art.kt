package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Team

private val Shield = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.08f, 0f)
    lineTo(w * 0.92f, 0f)
    lineTo(w * 0.92f, h * 0.55f)
    quadraticTo(w * 0.9f, h * 0.85f, w * 0.5f, h)
    quadraticTo(w * 0.1f, h * 0.85f, w * 0.08f, h * 0.55f)
    close()
}

/** A drawn badge in the school's colors. No real-world marks are used. */
@Composable
fun TeamLogo(t: Team, size: Dp, modifier: Modifier = Modifier) {
    val shape = when (t.logo) { 0 -> CircleShape; 1 -> RoundedCornerShape(size * 0.24f); 2 -> Shield; else -> CutCornerShape(size * 0.28f) }
    val primary = Color(t.primary)
    var secondary = Color(t.secondary)
    if (secondary == primary) secondary = Color.White
    val letters = if (t.logo == 2 || t.abbr.length > 3) t.abbr.take(1) else t.abbr
    val fontSize = with(LocalDensity.current) { (size * if (letters.length == 1) 0.52f else if (letters.length == 2) 0.38f else 0.3f).toSp() }
    Box(
        modifier.size(size).clip(shape).background(primary).border(size * 0.06f, secondary, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letters,
            style = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = secondary, textAlign = TextAlign.Center, letterSpacing = (-0.5).sp),
            maxLines = 1,
        )
    }
}

private val SKIN = listOf(0xFFFFDBAC, 0xFFF1C27D, 0xFFE0AC69, 0xFFC68642, 0xFF8D5524, 0xFF6B4423, 0xFF4A2E1A).map { Color(it) }
private val HAIR = listOf(0xFF1A1110, 0xFF2C1B12, 0xFF3B2416, 0xFF5A3A22, 0xFF8B5A2B, 0xFFB8894A, 0xFF101010).map { Color(it) }

/** Simple flat portrait built from a seed: skin, hair, jersey number or a coach's suit. */
@Composable
fun Avatar(look: Int, jersey: Color, number: Int?, size: Dp, modifier: Modifier = Modifier, coach: Boolean = false, trim: Color = Color.White) {
    val measurer = rememberTextMeasurer()
    val skin = SKIN[(look / 7) % SKIN.size]
    val hairColor = HAIR[(look / 53) % HAIR.size]
    val hairStyle = (look / 389) % 6
    val beard = coach && (look / 2203) % 3 == 0 || !coach && (look / 2203) % 9 == 0
    val glasses = coach && (look / 911) % 3 == 0
    val headband = !coach && hairStyle == 5
    Canvas(modifier.size(size).clip(CircleShape)) {
        val w = this.size.width
        val h = this.size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF2E3D5C), Color(0xFF1B2438))))
        // body
        if (coach) {
            val suit = Color(0xFF262B36)
            drawRoundRect(suit, Offset(w * 0.12f, h * 0.74f), Size(w * 0.76f, h * 0.4f), CornerRadius(w * 0.2f))
            val shirt = Path().apply { moveTo(w * 0.4f, h * 0.74f); lineTo(w * 0.6f, h * 0.74f); lineTo(w * 0.5f, h * 0.95f); close() }
            drawPath(shirt, Color.White)
            val tie = Path().apply { moveTo(w * 0.47f, h * 0.76f); lineTo(w * 0.53f, h * 0.76f); lineTo(w * 0.55f, h * 0.98f); lineTo(w * 0.5f, h * 1.02f); lineTo(w * 0.45f, h * 0.98f); close() }
            drawPath(tie, Color(0xFFD7263D))
        } else {
            drawRoundRect(jersey, Offset(w * 0.14f, h * 0.74f), Size(w * 0.72f, h * 0.4f), CornerRadius(w * 0.18f))
            val neckCut = Path().apply { moveTo(w * 0.38f, h * 0.74f); lineTo(w * 0.62f, h * 0.74f); lineTo(w * 0.5f, h * 0.84f); close() }
            drawPath(neckCut, trim)
            drawPath(Path().apply { moveTo(w * 0.41f, h * 0.74f); lineTo(w * 0.59f, h * 0.74f); lineTo(w * 0.5f, h * 0.81f); close() }, skin)
            if (number != null) {
                val style = TextStyle(fontFamily = Lexend, fontWeight = FontWeight.Bold, fontSize = (h * 0.17f / density).sp, color = trim)
                val layout = measurer.measure(number.toString(), style)
                drawText(layout, topLeft = Offset(w * 0.5f - layout.size.width / 2f, h * 0.83f))
            }
        }
        // neck
        drawRect(skin.darken(0.12f), Offset(w * 0.43f, h * 0.6f), Size(w * 0.14f, h * 0.15f))
        // hair behind head (afro / locs)
        if (hairStyle == 2) drawCircle(hairColor, w * 0.27f, Offset(w * 0.5f, h * 0.38f))
        if (hairStyle == 3) for (i in 0..6) {
            val x = w * (0.3f + i * 0.067f)
            drawRoundRect(hairColor, Offset(x, h * 0.28f), Size(w * 0.045f, h * 0.3f), CornerRadius(w * 0.02f))
        }
        // ears and head
        drawOval(skin.darken(0.05f), Offset(w * 0.29f, h * 0.38f), Size(w * 0.08f, h * 0.11f))
        drawOval(skin.darken(0.05f), Offset(w * 0.63f, h * 0.38f), Size(w * 0.08f, h * 0.11f))
        drawOval(skin, Offset(w * 0.32f, h * 0.2f), Size(w * 0.36f, h * 0.46f))
        // hair on top
        when (hairStyle) {
            0, 3 -> drawArc(hairColor, 180f, 180f, true, Offset(w * 0.31f, h * 0.17f), Size(w * 0.38f, h * 0.26f))
            1 -> drawArc(hairColor, 190f, 160f, true, Offset(w * 0.32f, h * 0.19f), Size(w * 0.36f, h * 0.18f))
            2 -> drawArc(hairColor, 180f, 180f, true, Offset(w * 0.3f, h * 0.14f), Size(w * 0.4f, h * 0.3f))
            5 -> {
                drawArc(hairColor, 180f, 180f, true, Offset(w * 0.31f, h * 0.17f), Size(w * 0.38f, h * 0.24f))
            }
            else -> {}
        }
        if (headband) drawRect(trim, Offset(w * 0.32f, h * 0.27f), Size(w * 0.36f, h * 0.045f))
        // face
        val eye = Color(0xFF1B1B1B)
        drawCircle(eye, w * 0.022f, Offset(w * 0.43f, h * 0.43f))
        drawCircle(eye, w * 0.022f, Offset(w * 0.57f, h * 0.43f))
        drawLine(skin.darken(0.3f), Offset(w * 0.45f, h * 0.55f), Offset(w * 0.55f, h * 0.55f), strokeWidth = w * 0.018f)
        if (beard) {
            drawArc(hairColor, 0f, 180f, true, Offset(w * 0.33f, h * 0.42f), Size(w * 0.34f, h * 0.24f))
            drawLine(skin.darken(0.3f), Offset(w * 0.45f, h * 0.55f), Offset(w * 0.55f, h * 0.55f), strokeWidth = w * 0.02f)
        }
        if (glasses) {
            val g = Stroke(width = w * 0.018f)
            drawCircle(Color(0xFF111111), w * 0.055f, Offset(w * 0.43f, h * 0.43f), style = g)
            drawCircle(Color(0xFF111111), w * 0.055f, Offset(w * 0.57f, h * 0.43f), style = g)
            drawLine(Color(0xFF111111), Offset(w * 0.485f, h * 0.43f), Offset(w * 0.515f, h * 0.43f), strokeWidth = w * 0.015f)
        }
    }
}

private fun Color.darken(f: Float) = Color(red * (1 - f), green * (1 - f), blue * (1 - f), alpha)

@Suppress("unused")
private fun DrawScope.noop() = Unit

/** Small colored square used as a stand-in when a team isn't known. */
@Composable
fun Dot(color: Color, size: Dp = 8.dp) = Box(Modifier.size(size).clip(CircleShape).background(color))

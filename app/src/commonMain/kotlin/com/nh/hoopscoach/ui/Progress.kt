package com.nh.hoopscoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A Material 3 progress bar: the filled part, a small gap, the rest of the track, and a stop dot
 * at the end of the track. The fill springs to its new value. [wavy] draws the filled part as a
 * moving wave (Material 3 Expressive), for progress toward a goal: XP, followers, a long sim.
 */
@Composable
fun Bar(fraction: Float, modifier: Modifier = Modifier, color: Color = P.accent, track: Color = P.surface2, height: Dp = 6.dp, wavy: Boolean = false) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = Spring.StiffnessLow), label = "bar")
    val phase = if (wavy) {
        val wave = rememberInfiniteTransition(label = "wave")
        wave.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "phase").value
    } else 0f
    Canvas(modifier.height(if (wavy) max(height.value, 10f).dp else height)) {
        if (wavy) wavyBar(f, phase, color, track) else flatBar(f, color, track)
    }
}

private fun DrawScope.flatBar(f: Float, color: Color, track: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f) return
    val r = CornerRadius(h / 2, h / 2)
    val gap = (h * 0.6f).coerceIn(2.dp.toPx(), 4.dp.toPx())
    // The filled part is never thinner than it is tall, so a little progress still reads as a pill.
    val active = when { f <= 0f -> 0f; f >= 1f -> w; else -> max(f * w, h).coerceAtMost(w) }
    if (active > 0f) drawRoundRect(color, size = Size(active, h), cornerRadius = r)
    val start = if (active > 0f) active + gap else 0f
    if (w - start >= h) {
        drawRoundRect(track, topLeft = Offset(start, 0f), size = Size(w - start, h), cornerRadius = r)
        // The stop dot marks where the bar ends.
        if (h >= 3.dp.toPx() && w - start >= h * 2) drawCircle(color, radius = min(h / 2, 2.dp.toPx()) * 0.9f, center = Offset(w - h / 2, h / 2))
    }
}

private fun DrawScope.wavyBar(f: Float, phase: Float, color: Color, track: Color) {
    val w = size.width
    val h = size.height
    if (w <= 0f) return
    val stroke = min(4.dp.toPx(), h * 0.45f)
    val cy = h / 2
    // The wave flattens as it starts and as it finishes.
    val ramp = when { f < 0.08f -> f / 0.08f; f > 0.96f -> (1f - f) / 0.04f; else -> 1f }.coerceIn(0f, 1f)
    val amp = (h - stroke) / 2 * ramp
    val wavelength = 26.dp.toPx()
    val gap = 4.dp.toPx() + stroke / 2
    val end = when { f <= 0f -> 0f; f >= 1f -> w - stroke / 2; else -> max(f * w, stroke * 1.5f).coerceAtMost(w - stroke / 2) }
    if (end > stroke / 2) {
        val path = Path()
        var x = stroke / 2
        path.moveTo(x, cy + amp * sin((2 * PI * (x / wavelength - phase)).toFloat()))
        while (x < end) {
            x = min(x + 2f, end)
            path.lineTo(x, cy + amp * sin((2 * PI * (x / wavelength - phase)).toFloat()))
        }
        drawPath(path, color, style = Stroke(stroke, cap = StrokeCap.Round))
    }
    val start = if (end > stroke / 2) end + gap else stroke / 2
    if (w - stroke / 2 - start > 0f) {
        drawLine(track, Offset(start, cy), Offset(w - stroke / 2, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        if (w - start >= stroke * 3) drawCircle(color, radius = stroke / 2 * 0.8f, center = Offset(w - stroke / 2, cy))
    }
}

// ---------- the loading indicator ----------

/** A shape on the polar plane: its radius (around 1) at each angle. */
private typealias Shape = (Double) -> Double

private fun cookie(lobes: Int, depth: Double): Shape = { t -> 1.0 - depth + depth * cos(lobes * t) }

/** A polygon with straight edges and rounded corners: the exact polygon with its corners trimmed smoothly by a circle. */
private fun polygon(sides: Int, cap: Double = 0.94): Shape = { t ->
    val seg = 2 * PI / sides
    val a = ((t % seg) + seg) % seg - seg / 2
    val poly = cos(PI / sides) / cos(a)
    val k = 48.0
    val soft = -kotlin.math.ln(kotlin.math.exp(-k * poly) + kotlin.math.exp(-k * cap)) / k
    soft / cap
}

private fun oval(ratio: Double): Shape = { t -> 1.0 / kotlin.math.sqrt(cos(t).pow(2) + (sin(t) / ratio).pow(2)) }

/** The shapes the indicator morphs through, like Material 3's: burst, cookie, pentagon, pill, sun, four-leaf cookie. */
private val SHAPES: List<Shape> = listOf(
    cookie(10, 0.10), cookie(9, 0.07), polygon(5), oval(0.62), cookie(8, 0.12), cookie(4, 0.16),
)

private fun easeInOut(x: Float) = if (x < 0.5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun easeOutBack(x: Float): Float { val c1 = 1.5f; val c3 = c1 + 1; return 1 + c3 * (x - 1).pow(3) + c1 * (x - 1).pow(2) }

/**
 * A Material 3 Expressive loading indicator: a shape that morphs (burst, cookie, pentagon, pill,
 * sun...) and turns, in the accent color, on a soft round container.
 */
@Composable
fun LoadingIndicator(modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = P.accent, container: Color = P.accent.copy(alpha = 0.14f)) {
    val n = SHAPES.size
    val clock = rememberInfiniteTransition(label = "loader")
    val time by clock.animateFloat(0f, n.toFloat(), infiniteRepeatable(tween(n * 700, easing = LinearEasing)), label = "time")
    LoadingFrame(time, modifier, size, color, container)
}

/** One frame of the loading indicator, [time] shapes into the cycle. */
@Composable
internal fun LoadingFrame(time: Float, modifier: Modifier = Modifier, size: Dp = 56.dp, color: Color = P.accent, container: Color = P.accent.copy(alpha = 0.14f)) {
    val n = SHAPES.size
    val points = 144
    val path = remember { Path() }
    Canvas(modifier.size(size)) {
        val c = Offset(this.size.width / 2, this.size.height / 2)
        drawCircle(container, radius = this.size.minDimension / 2, center = c)
        val step = floor(time).toInt()
        val f = time - step
        // Hold each shape a moment, then morph to the next one.
        val m = easeInOut(((f - 0.3f) / 0.7f).coerceIn(0f, 1f))
        val from = SHAPES[step % n]
        val to = SHAPES[(step + 1) % n]
        // A third of a turn per shape with a little overshoot; six shapes make two full turns, so the loop is seamless.
        val spin = ((step + easeOutBack(((f - 0.3f) / 0.7f).coerceIn(0f, 1f))) * 120f) * (PI / 180).toFloat()
        val radius = this.size.minDimension / 2 * 0.66f
        path.reset()
        for (k in 0..points) {
            val t = 2 * PI * k / points
            val r = (from(t) * (1 - m) + to(t) * m) * radius
            val x = c.x + (r * cos(t + spin)).toFloat()
            val y = c.y + (r * sin(t + spin)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

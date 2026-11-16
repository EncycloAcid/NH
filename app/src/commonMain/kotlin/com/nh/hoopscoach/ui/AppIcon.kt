package com.nh.hoopscoach.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.painter.Painter

/**
 * The app's icon: a shaded basketball over a warm glow on a deep navy tile, with the arc of a three-point line
 * below. Drawn in code so the window, the taskbar and the installer all use the same art (the Android launcher
 * icon is the same drawing in res/drawable).
 */
object AppIcon {
    val NAVY_TOP = Color(0xFF243049)
    val NAVY_BOTTOM = Color(0xFF0A0D14)
    val ORANGE = Color(0xFFF07A22)
    val ORANGE_LIGHT = Color(0xFFFFB46B)
    val ORANGE_DARK = Color(0xFFA9480C)
    val SEAM = Color(0xFF2A1407)

    /** The icon as a painter. [tile] draws the rounded navy square; without it, just the ball. */
    class Art(private val tile: Boolean = true) : Painter() {
        override val intrinsicSize = Size(512f, 512f)
        override fun DrawScope.onDraw() = draw(this, tile)
    }

    fun draw(d: DrawScope, tile: Boolean = true) = with(d) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        fun p(x: Float, y: Float) = Offset(o.x + x * s, o.y + y * s)
        if (tile) {
            drawRoundRect(Brush.verticalGradient(listOf(NAVY_TOP, NAVY_BOTTOM), startY = o.y, endY = o.y + s), o, Size(s, s), CornerRadius(s * 0.22f))
            // The three-point line, faint, under the ball.
            clipPath(Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(o.x, o.y, o.x + s, o.y + s, CornerRadius(s * 0.22f))) }) {
                drawCircle(ORANGE.copy(alpha = 0.22f), s * 0.43f, p(0.5f, 1.1f), style = Stroke(s * 0.016f))
                drawCircle(ORANGE.copy(alpha = 0.12f), s * 0.2f, p(0.5f, 1.1f), style = Stroke(s * 0.012f))
            }
        }
        val c = p(0.5f, 0.46f)
        val r = s * (if (tile) 0.29f else 0.36f)
        // A warm glow behind it.
        drawCircle(Brush.radialGradient(listOf(ORANGE.copy(alpha = 0.38f), ORANGE.copy(alpha = 0f)), c, r * 1.55f), r * 1.55f, c)
        // Its shadow on the floor.
        drawOval(Color.Black.copy(alpha = 0.38f), Offset(c.x - r * 0.62f, c.y + r * 1.13f), Size(r * 1.24f, r * 0.2f))
        // The ball: lit from the upper left.
        drawCircle(Brush.radialGradient(listOf(ORANGE_LIGHT, ORANGE, ORANGE_DARK), Offset(c.x - r * 0.35f, c.y - r * 0.4f), r * 1.55f), r, c)
        val ball = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(ball) {
            val w = r * 0.075f
            val seam = Stroke(w, cap = StrokeCap.Round)
            drawLine(SEAM, Offset(c.x - r, c.y), Offset(c.x + r, c.y), w, StrokeCap.Round)
            drawLine(SEAM, Offset(c.x, c.y - r), Offset(c.x, c.y + r), w, StrokeCap.Round)
            for (side in listOf(-1f, 1f)) {
                drawPath(Path().apply {
                    moveTo(c.x + side * r * 0.72f, c.y - r * 0.72f)
                    cubicTo(c.x + side * r * 0.32f, c.y - r * 0.38f, c.x + side * r * 0.32f, c.y + r * 0.38f, c.x + side * r * 0.72f, c.y + r * 0.72f)
                }, SEAM, style = seam)
            }
            // A soft shine on top.
            drawOval(Color.White.copy(alpha = 0.16f), Offset(c.x - r * 0.62f, c.y - r * 0.86f), Size(r * 0.8f, r * 0.42f))
        }
        drawCircle(SEAM.copy(alpha = 0.55f), r, c, style = Stroke(r * 0.04f))
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The app's logo on the PC (window, taskbar, installer): the same art as the Android launcher icon
 * (res/drawable/ic_launcher_*.xml), drawn from the same shapes: a white ball, shaded from the upper left, with
 * dark seams and a faint shadow, on a dark tile a touch lighter at the top.
 */
object AppIcon {
    val TILE_TOP = Color(0xFF1C2230)
    val TILE_BOTTOM = Color(0xFF0C0F15)
    val SEAM = Color(0xFF11141B)

    /** The launcher art's seams, in its 108-unit space. */
    private val seams by lazy {
        PathParser().parsePathString(
            "M28,54h52 M54,28v52 M35.6,35.6c7.4,5.6 11.2,12 11.2,18.4s-3.8,12.8 -11.2,18.4 M72.4,35.6c-7.4,5.6 -11.2,12 -11.2,18.4s3.8,12.8 11.2,18.4"
        ).toPath()
    }

    /** The logo as a painter. [tile] draws the rounded dark square; without it, just the ball. */
    class Art(private val tile: Boolean = true) : Painter() {
        override val intrinsicSize = Size(512f, 512f)
        override fun DrawScope.onDraw() = draw(this, tile)
    }

    fun draw(d: DrawScope, tile: Boolean = true) = with(d) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        if (tile) drawRoundRect(Brush.verticalGradient(listOf(TILE_TOP, TILE_BOTTOM), o.y, o.y + s), o, Size(s, s), CornerRadius(s * 0.22f))
        // The launcher shows the middle 72 of its 108 units; the tile here is that same view.
        val view = if (tile) 72f else 60f
        translate(o.x, o.y) {
            scale(s / view, s / view, pivot = Offset.Zero) {
                translate(-(108f - view) / 2, -(108f - view) / 2) {
                    val c = Offset(54f, 54f)
                    drawOval(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent), Offset(54f, 82.5f), 17f),
                        Offset(37f, 79.5f), Size(34f, 6f))
                    drawCircle(Brush.radialGradient(0f to Color.White, 0.55f to Color(0xFFF0F1F4), 1f to Color(0xFFC9CDD5), center = Offset(45f, 41f), radius = 44f), 26f, c)
                    clipPath(Path().apply { addOval(Rect(c, 26f)) }) {
                        drawPath(seams, SEAM, style = Stroke(2.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}

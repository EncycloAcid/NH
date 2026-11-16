package com.nh.hoopscoach.ui

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.PathParser

/**
 * The app's logo on the PC (window, taskbar, installer): the same white ball with dark seams on a dark tile as
 * the Android launcher icon (res/drawable/ic_launcher_foreground.xml), drawn from the same paths.
 */
object AppIcon {
    val TILE = Color(0xFF11141B)
    val BALL = Color(0xFFF2F3F5)

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
        if (tile) drawRoundRect(TILE, o, Size(s, s), CornerRadius(s * 0.22f))
        // The launcher shows the middle 72 of its 108 units; the tile here is that same view.
        val view = if (tile) 72f else 54f
        translate(o.x, o.y) {
            scale(s / view, s / view, pivot = Offset.Zero) {
                translate(-(108f - view) / 2, -(108f - view) / 2) {
                    drawCircle(BALL, 26f, Offset(54f, 54f))
                    drawPath(seams, TILE, style = Stroke(2.8f))
                }
            }
        }
    }
}

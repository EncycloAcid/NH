package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.ShotChart
import kotlin.math.acos
import kotlin.math.hypot
import kotlin.math.roundToInt

/** The half court's lines, in feet scaled to the canvas: baseline at the top, the rim near it. */
private fun DrawScope.court(line: Color, paint: Color) {
    val k = size.width / 50f
    fun pt(x: Double, y: Double) = Offset(((x + 25) * k).toFloat(), (y * k).toFloat())
    val w = 2.dp.toPx()
    // The paint, filled, and its lines.
    drawRect(paint, pt(-8.0, 0.0), Size(16 * k, 19 * k))
    drawRect(line, pt(-8.0, 0.0), Size(16 * k, 19 * k), style = Stroke(w))
    // Free-throw circle, the rim, the backboard, the restricted area.
    drawCircle(line, 6 * k, pt(0.0, 19.0), style = Stroke(w))
    drawCircle(line, 0.75f * k, pt(0.0, ShotChart.RIM_Y), style = Stroke(w))
    drawLine(line, pt(-3.0, 4.0), pt(3.0, 4.0), w * 1.4f)
    drawArc(line, 0f, 180f, false, pt(-4.0, ShotChart.RIM_Y - 4), Size(8 * k, 8 * k), style = Stroke(w))
    // The three-point line: straight in the corners, then the arc.
    val r = 23.75
    val cornerY = ShotChart.RIM_Y + kotlin.math.sqrt(r * r - 22.0 * 22.0)
    drawLine(line, pt(-22.0, 0.0), pt(-22.0, cornerY), w)
    drawLine(line, pt(22.0, 0.0), pt(22.0, cornerY), w)
    val a = Math.toDegrees(acos(22.0 / r)).toFloat()
    drawArc(line, a, 180f - 2 * a, false, pt(-r, ShotChart.RIM_Y - r), Size((2 * r * k).toFloat(), (2 * r * k).toFloat()), style = Stroke(w))
    // Half court.
    drawLine(line, pt(-25.0, 46.8), pt(25.0, 46.8), w)
    drawCircle(line, 6 * k, pt(0.0, 47.0), style = Stroke(w))
    drawRect(line, Offset.Zero, size, style = Stroke(w))
}

/** Every shot in a game, made (circles) and missed (crosses), for one team and optionally one player. */
@Composable
fun ShotChartView(lg: League, shots: List<Long>, side: Int, pid: Int? = null) {
    val list = shots.map { ShotChart.unpack(it) }.filter { it.side == side && (pid == null || it.pid == pid) }
    val line = P.faint.copy(alpha = 0.7f)
    val paint = P.accent.copy(alpha = 0.06f)
    val good = P.good
    val bad = P.bad
    Column {
        Box(Modifier.fillMaxWidth().aspectRatio(50f / 47f).clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).background(P.surface2)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(50f / 47f)) {
                court(line, paint)
                val k = size.width / 50f
                val rad = 4.5.dp.toPx()
                val sw = 2.dp.toPx()
                for (s in list) {
                    val c = Offset(((s.x + 25) * k).toFloat(), (s.y * k).toFloat())
                    if (s.made) {
                        drawCircle(good.copy(alpha = 0.85f), rad, c)
                        drawCircle(Color.White.copy(alpha = 0.7f), rad, c, style = Stroke(sw * 0.6f))
                    } else {
                        drawLine(bad.copy(alpha = 0.8f), c + Offset(-rad, -rad), c + Offset(rad, rad), sw)
                        drawLine(bad.copy(alpha = 0.8f), c + Offset(-rad, rad), c + Offset(rad, -rad), sw)
                    }
                }
            }
        }
        val made = list.count { it.made }
        val threes = list.filter { it.type == 2 }
        Text("$made of ${list.size} from the field · ${threes.count { it.made }} of ${threes.size} from three", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
    }
}

/** His five zones this season, colored hot or cold against the league, with his percentage in each. */
@Composable
fun HotZones(lg: League, p: Player) {
    if (p.zoneSeason != lg.season || p.zoneA.sum() < 20) return
    val league = ShotChart.leagueRates(lg)
    val line = P.faint.copy(alpha = 0.7f)
    val hot = Color(0xFFE5484D)
    val cold = Color(0xFF3B82F6)
    val neutral = P.surface2
    fun tint(i: Int): Color {
        if (p.zoneA[i] < 4) return neutral
        val diff = (p.zoneM[i].toDouble() / p.zoneA[i] - league[i]).coerceIn(-0.15, 0.15) / 0.15
        return if (diff >= 0) hot.copy(alpha = (0.15 + 0.6 * diff).toFloat()) else cold.copy(alpha = (0.15 - 0.6 * diff).toFloat())
    }
    val tints = (0..4).map { tint(it) }
    Section("Hot zones")
    Panel(padding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(50f / 34f)) {
            Canvas(Modifier.fillMaxWidth().aspectRatio(50f / 34f)) {
                val k = size.width / 50f
                // Each zone filled cell by cell.
                val step = 0.8
                var y = 0.0
                while (y < 34) {
                    var x = -25.0
                    while (x < 25) {
                        val cx = x + step / 2; val cy = y + step / 2
                        val three = hypot(cx, cy - ShotChart.RIM_Y) > 23.75 || (kotlin.math.abs(cx) > 22 && cy < 14)
                        val z = ShotChart.zone(if (three) 2 else 1, cx, cy)
                        drawRect(tints[z], Offset(((x + 25) * k).toFloat(), (y * k).toFloat()), Size((step * k).toFloat() + 1f, (step * k).toFloat() + 1f))
                        x += step
                    }
                    y += step
                }
                court(line, Color.Transparent)
            }
            // Percentages on top of each zone.
            val spots = listOf(0.0 to 1.6, 0.0 to 12.0, -14.0 to 13.0, -23.0 to 6.0, 16.0 to 28.0)
            for ((i, xy) in spots.withIndex()) {
                val a = p.zoneA[i]
                val label = if (a == 0) "—" else "${(p.zoneM[i] * 100.0 / a).roundToInt()}%"
                ZoneLabel(label, "${p.zoneM[i]}/$a", xy.first, xy.second)
                if (i == 3) ZoneLabel(label, "", 23.0, xy.second)
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(hot.copy(alpha = 0.7f)))
            Text("Better than the league", style = T.small, color = P.sub)
            Box(Modifier.size(10.dp).clip(CircleShape).background(cold.copy(alpha = 0.7f)))
            Text("Worse", style = T.small, color = P.sub)
        }
    }
}

/** A zone's percentage, placed at (x, y) feet on the 50-by-34 court box. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ZoneLabel(pct: String, count: String, x: Double, y: Double) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.matchParentSize()) {
        val k = maxWidth / 50f
        Column(
            Modifier.padding(start = k * ((x + 25 - 4).toFloat()).coerceAtLeast(0f), top = k * ((y - 1.6).toFloat()).coerceAtLeast(0f)).width(k * 8f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(pct, style = T.bodyB, color = P.ink)
            if (count.isNotEmpty()) Text(count, style = T.small.copy(fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp)), color = P.sub)
        }
    }
}

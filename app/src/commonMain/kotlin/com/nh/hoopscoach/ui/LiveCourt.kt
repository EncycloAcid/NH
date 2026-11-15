package com.nh.hoopscoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.GameEngine
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.PlayEvent
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/*
 * The live court: the home floor with the ten players on it, in their teams' colors, running their sets.
 * Each play moves them: the offense sets up at the end it's attacking and the defense guards it, the
 * shooter goes to the spot the shot came from and the ball arcs to the rim, a block knocks it away, a
 * steal takes it the other way. The big ones (a dunk, a three, a block, a steal, an and-one) get a callout.
 *
 * Positions are in feet on a 94 x 50 floor, x from the left baseline, y from the top sideline.
 */

/** Where the offense stands, in feet from the baseline it attacks and from the middle: point, wing, corner, elbow, block. */
private val OFFENSE = listOf(25f to 0f, 20f to 14f, 5f to -17f, 15f to -8f, 6f to 7f)


/** One frame of the court: where everyone is headed, who has the ball, and what the ball is doing. */
private class Scene(
    val pos: Map<Int, Offset>,
    val side: Map<Int, Int>,
    /** Who holds the ball while players move (or shoots it). */
    val handler: Int,
    /** A shot: from the handler to the rim, then [make] in or out. */
    val shot: Boolean,
    val make: Boolean,
    val rim: Offset,
    /** A block knocks the ball out this way. */
    val away: Offset?,
    /** Ball at a player's hands after the play (steal, rebound). */
    val after: Int,
    val arms: Set<Int>,
    val jump: Set<Int>,
    val arc: Float,
    val callout: Pair<String, String>?,
    val calloutSide: Int,
)

/** Which end a side attacks: home goes right in the first half, then they switch. */
private fun attacksRight(side: Int, period: Int, regs: Int) = (side == 0) xor (period > regs / 2)

private fun at(right: Boolean, depth: Float, lateral: Float) =
    Offset(if (right) 94f - depth else depth, (25f + lateral * 0.85f).coerceIn(9f, 47f))

private fun scene(lg: League, e: GameEngine, ev: PlayEvent?, lastOff: Int): Scene? {
    val off = ev?.off?.takeIf { it >= 0 } ?: lastOff
    val period = ev?.period ?: e.period
    val right = attacksRight(off, period, e.regPeriods)
    fun sorted(ids: List<Int>) = ids.mapNotNull { lg.playerOrNull(it) }.sortedBy { it.pos.ordinal }.map { it.id }
    val o = sorted(e.floor[off])
    val d = sorted(e.floor[1 - off])
    if (o.isEmpty() || d.isEmpty()) return null
    val pos = HashMap<Int, Offset>()
    val side = HashMap<Int, Int>()
    o.forEachIndexed { i, id -> val (dep, lat) = OFFENSE[i % 5]; pos[id] = at(right, dep, lat); side[id] = off }
    // Each defender between his man and the rim.
    d.forEachIndexed { i, id -> val (dep, lat) = OFFENSE[i % 5]; pos[id] = at(right, (dep - 3.5f).coerceAtLeast(2f), lat * 0.85f); side[id] = 1 - off }
    val rim = at(right, 5.25f, 0f)
    fun guard(of: Int) = d.getOrNull(o.indexOf(of))
    fun near(p: Offset, toward: Offset, ft: Float): Offset {
        val v = toward - p; val len = v.getDistance().coerceAtLeast(0.01f)
        return p + v * (ft / len)
    }
    var handler = o.first()
    var shot = false; var make = false; var away: Offset? = null; var after = -1; var arc = 0.18f
    val arms = HashSet<Int>(); val jump = HashSet<Int>()
    var callout: Pair<String, String>? = null
    var cside = off
    fun name(id: Int) = lg.playerOrNull(id)?.short ?: ""
    val kind = ev?.kind.orEmpty()
    when (kind) {
        "dunk", "layup", "jumper", "three", "miss", "block" -> {
            val shooter = if (kind == "block") ev!!.pid2 else ev!!.pid
            if (shooter in pos) {
                val spot = if (kind == "dunk") near(rim, at(right, 12f, ev.sx), 2f)
                    else at(right, ev.sy.coerceIn(1f, 30f), ev.sx)
                pos[shooter] = spot
                handler = shooter; shot = true; arms += shooter
                make = kind != "miss" && kind != "block"
                arc = when { kind == "three" -> 0.22f; kind == "jumper" || kind == "miss" -> 0.16f; kind == "dunk" -> 0.03f; else -> 0.08f }
                if (kind == "dunk") jump += shooter
                guard(shooter)?.let { pos[it] = near(spot, rim, 2.4f) }
                if (kind == "block") {
                    val b = ev.pid
                    if (b in pos) { pos[b] = near(spot, rim, 2f); arms += b; jump += b }
                    away = spot + Offset(if (right) -9f else 9f, if (spot.y < 25f) -7f else 7f)
                    callout = "BLOCKED!" to name(b); cside = 1 - off
                }
                callout = callout ?: when (kind) {
                    "dunk" -> "DUNK!" to name(shooter)
                    "three" -> "THREE!" to name(shooter)
                    "layup", "jumper" -> if (ev.points > 0) "+2" to name(shooter) else null
                    else -> null
                }
            }
        }
        "steal" -> {
            val loser = ev!!.pid2; val stealer = ev.pid
            if (loser in pos && stealer in pos) {
                pos[stealer] = pos.getValue(loser) + Offset(if (right) 2f else -2f, 1.5f)
                handler = loser; after = stealer
                callout = "STEAL!" to name(stealer); cside = 1 - off
            }
        }
        "ft" -> {
            val shooter = ev!!.pid
            if (shooter in pos) {
                pos[shooter] = at(right, 15f, 0f)
                // Everyone else along the lane or back at the arc.
                val lane = listOf(7f to -8f, 7f to 8f, 11f to -8f, 11f to 8f)
                val back = listOf(28f to -10f, 28f to 10f, 30f to 0f)
                var li = 0; var bi = 0
                for (id in (o + d)) {
                    if (id == shooter) continue
                    val (dep, lat) = if (li < lane.size && (id in d || li < 2)) lane[li++] else back[bi++ % back.size]
                    pos[id] = Offset(if (right) 94f - dep else dep, 25f + lat)
                }
                handler = shooter; shot = true; make = ev.points > 0; arms += shooter; arc = 0.12f
            }
        }
        "oreb" -> {
            val r = ev!!.pid
            if (r in pos) { pos[r] = near(rim, at(right, 10f, 4f), 3f); handler = r; arms += r; jump += r }
        }
        "tov", "foul", "andone" -> {
            val who = if (kind == "tov") ev!!.pid else ev!!.pid2
            if (who in pos) handler = who
            if (kind == "andone") { callout = "AND ONE!" to name(ev.pid2); cside = off }
        }
    }
    return Scene(pos, side, handler, shot, make, rim, away, after, arms, jump, arc, callout, cside)
}

/**
 * The live court, filling its box: the home team's floor art cropped to fit, and the callout for a big play
 * near the bottom (the plays are still staged in feet so each callout lands in rhythm with the game). [tick] changes with each step of the game.
 */
@Composable
fun LiveCourtView(lg: League, e: GameEngine, home: Team, away: Team, tick: Int, speed: Int, spotlight: Boolean, modifier: Modifier = Modifier) {
    val art = if (home.customKit) null else CourtArt.image(home)
    val anims = remember(e) { HashMap<Int, Animatable<Offset, AnimationVector2D>>() }
    val flight = remember(e) { Animatable(1f) }
    var current by remember(e) { mutableStateOf<Scene?>(null) }
    var shown by remember(e) { mutableStateOf(0) }
    var callout by remember(e) { mutableStateOf<Triple<String, String, Int>?>(null) }
    var calloutKey by remember(e) { mutableStateOf(0) }
    val handled = remember(e) { intArrayOf(-1, 0) }   // last event index drawn, the side that had the ball

    // Each new play in turn (the last few, when the game runs ahead): players run to their spots, then the ball flies.
    LaunchedEffect(e, tick) {
        val evs = e.events
        val fresh = (handled[0] + 1 until evs.size).filter { evs[it].kind.isNotEmpty() }.takeLast(3)
        if (fresh.isEmpty()) {
            if (current == null) scene(lg, e, null, handled[1])?.let { s -> current = s; for ((id, p) in s.pos) anims[id] = Animatable(p, Offset.VectorConverter) }
            return@LaunchedEffect
        }
        // Fast games move fast; spotlight and 1x give the play room to breathe.
        val total = if (spotlight) 1600 else when (speed) { 1 -> 600; 2 -> 250; 3 -> 66; else -> 500 }
        val budget = (total / fresh.size).coerceAtLeast(40)
        for (idx in fresh) {
            handled[0] = idx
            val ev = evs[idx]
            val s = scene(lg, e, ev, handled[1]) ?: continue
            // Who has it next: the other team after a make, a steal or a defensive stop.
            if (ev.off >= 0) handled[1] = when (ev.kind) {
                "dunk", "layup", "jumper", "three", "steal" -> 1 - ev.off
                "ft" -> if (ev.points > 0) 1 - ev.off else ev.off
                "block", "miss" -> 1 - ev.off
                else -> ev.off
            }
            val move = (budget * if (s.shot) 0.5f else 0.85f).toInt().coerceAtLeast(1)
            val fly = (budget * 0.45f).toInt().coerceAtLeast(1)
            current = s
            flight.snapTo(if (s.shot || s.after >= 0) 0f else 1f)
            coroutineScope {
                for ((id, target) in s.pos) {
                    val a = anims.getOrPut(id) { Animatable(target, Offset.VectorConverter) }
                    launch { a.animateTo(target, tween(move, easing = FastOutSlowInEasing)) }
                }
            }
            shown++
            if (s.shot || s.after >= 0) flight.animateTo(1f, tween(fly, easing = LinearEasing))
            s.callout?.let { (big, who) -> callout = Triple(big, who, s.calloutSide); calloutKey++ }
        }
    }
    LaunchedEffect(calloutKey) { if (callout != null) { delay(if (spotlight) 1800 else 1300); callout = null } }


    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF101318))) {
        Canvas(Modifier.fillMaxSize()) {
            // The floor art, cropped to fill; the floor's feet-to-pixels from where the court sits in the art.
            val iw = 1024f; val ih = 576f
            val sc = max(size.width / iw, size.height / ih)
            val dw = iw * sc; val dh = ih * sc
            val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
            if (art != null) drawImage(art, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
            else plainCourt(home, Offset(ox, oy), Size(dw, dh))
        }
        callout?.let { (big, who, side) ->
            val c = Color(if (side == 0) home.primary else away.primary)
            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp).popIn(calloutKey), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(big, style = T.hero.copy(fontSize = 30.sp, lineHeight = 30.sp), color = Color.White, textAlign = TextAlign.Center,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(c.copy(alpha = 0.9f)).padding(horizontal = 12.dp, vertical = 2.dp))
                if (who.isNotEmpty()) Text(who.uppercase(), style = T.label.copy(fontSize = 11.sp), color = Color.White,
                    modifier = Modifier.padding(top = 3.dp).clip(RoundedCornerShape(6.dp)).background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 8.dp, vertical = 1.dp))
            }
        }
    }
}

/** A floor for a team without court art: wood, a border in its color, both lanes and the center circle. */
private fun DrawScope.plainCourt(t: Team, at: Offset, sz: Size) {
    drawRect(Color(t.primary), at, sz)
    val fx = at.x + sz.width * (54f / 1024f); val fy = at.y + sz.height * (48f / 576f)
    val fw = sz.width * (917f / 1024f); val fh = sz.height * (480f / 576f)
    drawRect(Color(0xFFE2BC86), Offset(fx, fy), Size(fw, fh))
    val line = Color.White
    val st = Stroke((fh * 0.008f).coerceAtLeast(1.5f))
    drawRect(line, Offset(fx, fy), Size(fw, fh), style = st)
    drawLine(line, Offset(fx + fw / 2, fy), Offset(fx + fw / 2, fy + fh), strokeWidth = st.width)
    drawCircle(line, fh * 0.12f, Offset(fx + fw / 2, fy + fh / 2), style = st)
    for (left in listOf(true, false)) {
        val lw = fw * 19f / 94f; val lh = fh * 16f / 50f
        val lx = if (left) fx else fx + fw - lw
        drawRect(Color(t.primary).copy(alpha = 0.85f), Offset(lx, fy + (fh - lh) / 2), Size(lw, lh))
        drawRect(line, Offset(lx, fy + (fh - lh) / 2), Size(lw, lh), style = st)
    }
}

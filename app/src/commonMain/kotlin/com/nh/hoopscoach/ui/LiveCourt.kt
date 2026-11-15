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

private val HAIR = listOf(0xFF2E2A2A, 0xFF4A2E1E, 0xFF6E4628, 0xFFA2562A, 0xFFDAB458, 0xFFECE6D4, 0xFF8E9096, 0xFFC22E2E)

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
 * The live court, filling its box: the home team's floor art cropped to fit, the players over it, and the
 * callout for a big play near the bottom. [tick] changes with each step of the game.
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

    val t by rememberInfiniteTransition(label = "court").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "run")
    val homeKit = kit(home, true)
    val awayKit = kit(away, false)
    val faces = remember(e) { HashMap<Int, Pair<Color, Color?>>() }
    val heroId = if (lg.playerMode) lg.heroId else -1

    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color(0xFF101318))) {
        Canvas(Modifier.fillMaxSize()) {
            // The floor art, cropped to fill; the floor's feet-to-pixels from where the court sits in the art.
            val iw = 1024f; val ih = 576f
            val sc = max(size.width / iw, size.height / ih)
            val dw = iw * sc; val dh = ih * sc
            val ox = (size.width - dw) / 2; val oy = (size.height - dh) / 2
            if (art != null) drawImage(art, dstOffset = IntOffset(ox.toInt(), oy.toInt()), dstSize = IntSize(dw.toInt(), dh.toInt()), filterQuality = FilterQuality.None)
            else plainCourt(home, Offset(ox, oy), Size(dw, dh))
            val fx0 = ox + dw * (54f / 1024f); val fx1 = ox + dw * (971f / 1024f)
            val fy0 = oy + dh * (48f / 576f); val fy1 = oy + dh * (528f / 576f)
            val ftx = (fx1 - fx0) / 94f; val fty = (fy1 - fy0) / 50f
            fun px(p: Offset) = Offset(fx0 + p.x * ftx, fy0 + p.y * fty)
            val s = current ?: return@Canvas
            val unit = fty * 8f / 14f   // one sprite pixel: a figure is 14 of them, drawn a little larger than life so he reads
            val moving = anims.values.any { it.isRunning }
            // Players, back to front.
            val ids = s.pos.keys.sortedBy { anims[it]?.value?.y ?: s.pos.getValue(it).y }
            var handAt = Offset.Zero
            var afterAt = Offset.Zero
            for (id in ids) {
                val p = anims[id]?.value ?: s.pos.getValue(id)
                val sideOf = s.side[id] ?: 0
                val (skin, hair) = faces.getOrPut(id) {
                    val pl = lg.playerOrNull(id)
                    val f = pl?.let { Face.of(it) }
                    val sk = Color(SkinSwatches[(f?.skin ?: 4).coerceIn(0, SkinSwatches.lastIndex)])
                    sk to (if (f == null || f.hair == 0) null else Color(HAIR[f.hairColor.coerceIn(0, HAIR.lastIndex)]))
                }
                val k = if (sideOf == 0) homeKit else awayKit
                val bob = sin(t + id % 7) * 0.25f * unit
                val jumpUp = if (id in s.jump && flight.value < 1f) sin(flight.value * PI.toFloat()) * unit * 5f else 0f
                val runFrame = if (moving && (anims[id]?.isRunning == true)) (if (sin(t * 3 + id) > 0) 1 else -1) else 0
                val feet = px(p) + Offset(0f, bob)
                figure(feet, unit, skin, hair, k.first, k.second, k.third, id in s.arms, jumpUp, runFrame, id == heroId)
                if (id == s.handler) handAt = feet + Offset(unit * 3.6f, -unit * 6f - jumpUp - if (id in s.arms) unit * 6f else 0f)
                if (id == s.after) afterAt = feet + Offset(unit * 3.6f, -unit * 6f)
            }
            // The ball: in his hands, or in the air.
            val rimPx = px(s.rim)
            val f = flight.value
            val ball: Offset; val lift: Float
            when {
                s.shot && f < 1f && s.away != null && f > 0.55f -> {           // blocked: knocked away
                    val g = (f - 0.55f) / 0.45f
                    val from = handAt; val to = px(s.away)
                    ball = from + (to - from) * g; lift = sin(g * PI.toFloat()) * unit * 6f
                }
                s.shot && f < 1f -> {
                    val g = if (s.away != null) f / 0.55f * 0.35f else f
                    ball = handAt + (rimPx - handAt) * g; lift = sin(g * PI.toFloat()) * (fy1 - fy0) * s.arc
                }
                s.shot && !s.make && s.away == null -> { ball = rimPx + Offset(if (rimPx.x < size.width / 2) unit * 8 else -unit * 8, unit * 5); lift = 0f }
                s.shot && s.away != null -> { ball = px(s.away); lift = 0f }
                s.shot -> { ball = rimPx + Offset(0f, unit * 2); lift = 0f }
                s.after >= 0 -> { ball = handAt + (afterAt - handAt) * f; lift = sin(f * PI.toFloat()) * unit * 2f }
                else -> { ball = handAt; lift = 0f }
            }
            val r = unit * 1.5f
            val bc = ball - Offset(0f, lift)
            drawCircle(Color.Black.copy(alpha = 0.3f), r * 0.9f, ball + Offset(0f, unit * 1.5f))
            drawCircle(Color(0xFFEF7D2B), r, bc)
            drawCircle(Color(0xFFFFB46A), r * 0.4f, bc - Offset(r * 0.35f, r * 0.35f))
            drawCircle(Color(0xFF3A1A08), r, bc, style = Stroke(unit * 0.45f))
            // The net snaps on a make.
            if (s.shot && s.make && f >= 0.98f && f <= 1f) drawCircle(Color.White.copy(alpha = 0.8f), unit * 2.6f, rimPx, style = Stroke(unit * 0.5f))
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

/** A team's uniform on the court: home in white with its color as trim, away in its color. (jersey, trim, shorts) */
private fun kit(t: Team, home: Boolean): Triple<Color, Color, Color> {
    val p = Color(t.primary); val s = Color(t.secondary)
    val light = contrast(p, Color.White) < 1.5f
    return if (home) Triple(Color(0xFFF4F2EC), if (light) s else p, Color(0xFFE6E2D8))
    else if (light) Triple(s, p, s) else Triple(p, if (contrast(s, p) < 1.5f) Color.White else s, p)
}

/**
 * One player, pixel by pixel, standing on [feet]: shoes, legs, shorts, jersey with trim, arms (up when he
 * shoots or blocks), neck and head with his hair. [jump] lifts him off his shadow; [run] swaps his stride.
 */
private fun DrawScope.figure(feet: Offset, u: Float, skin: Color, hair: Color?, jersey: Color, trim: Color, shorts: Color, armsUp: Boolean, jump: Float, run: Int, hero: Boolean) {
    drawOval(Color.Black.copy(alpha = 0.32f), Offset(feet.x - u * 3.2f, feet.y - u * 0.9f), Size(u * 6.4f, u * 1.8f))
    val base = Offset(feet.x, feet.y - jump)
    fun px(col: Int, row: Int, c: Color, w: Int = 1, h: Int = 1) =
        drawRect(c, Offset(base.x + (col - 0.5f) * u, base.y - (row + h) * u), Size(u * w + 0.6f, u * h + 0.6f))
    val shoe = Color(0xFF1E1E22)
    val outline = Color(0xFF14110F)
    // Legs and shoes, in stride when running.
    val l = if (run > 0) -1 else 0
    val r = if (run < 0) 1 else 0
    px(-2 + l, 0, shoe, 2); px(1 + r, 0, shoe, 2)
    px(-1 + l, 1, skin, 1, 2); px(1 + r, 1, skin, 1, 2)
    // Shorts and jersey with an outline so they read on any floor.
    px(-3, 3, outline, 7, 7)
    px(-2, 3, shorts, 5, 2)
    px(-2, 5, jersey, 5, 5)
    px(-2, 9, trim); px(2, 9, trim); px(-2, 5, trim, 5, 1)
    // Arms: down at his sides, or up over his head.
    if (armsUp) { px(-3, 9, skin, 1, 5); px(3, 9, skin, 1, 5) }
    else { px(-3, 5, skin, 1, 4); px(3, 5, skin, 1, 4) }
    // Neck and head.
    px(0, 10, skin)
    px(-2, 11, outline, 5, 4)
    px(-1, 11, skin, 3, 3)
    if (hair != null) { px(-1, 13, hair, 3, 1); px(-2, 12, hair, 1, 2); px(2, 12, hair, 1, 2) }
    if (hero) {
        val top = base.y - 17f * u
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(base.x - u * 1.6f, top); lineTo(base.x + u * 1.6f, top); lineTo(base.x, top + u * 1.8f); close()
        }
        drawPath(path, Color(0xFFFFC94D))
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

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
    val looks = remember(e) { HashMap<Int, Look>() }
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
            val unit = fty * 8.6f / 21f   // one sprite pixel: a figure is 21 of them, a little larger than life so he reads
            val moving = anims.values.any { it.isRunning }
            val offense = s.side[s.handler] ?: 0
            // Players, back to front.
            val ids = s.pos.keys.sortedBy { anims[it]?.value?.y ?: s.pos.getValue(it).y }
            var handAt = Offset.Zero
            var afterAt = Offset.Zero
            for (id in ids) {
                val p = anims[id]?.value ?: s.pos.getValue(id)
                val sideOf = s.side[id] ?: 0
                val look = looks.getOrPut(id) { lookOf(lg, id) }
                val k = if (sideOf == 0) homeKit else awayKit
                val running = moving && (anims[id]?.isRunning == true)
                val bob = if (running) 0f else sin(t + id % 7) * 0.25f * unit
                val jumpUp = if (id in s.jump && flight.value < 1f) sin(flight.value * PI.toFloat()) * unit * 6f else 0f
                val runFrame = if (running) (if (sin(t * 3 + id) > 0) 1 else -1) else 0
                val pose = when {
                    id in s.arms && (s.shot || s.jump.contains(id)) && flight.value < 1f -> UP
                    id in s.arms && !s.shot -> UP
                    id == s.handler && !s.shot && s.after < 0 -> DRIBBLE
                    sideOf != offense && !running -> GUARD
                    else -> STAND
                }
                val feet = px(p) + Offset(0f, bob)
                val hand = figure(feet, unit, look, k.first, k.second, k.third, pose, jumpUp, runFrame, id == heroId, t)
                if (id == s.handler) handAt = hand
                if (id == s.after) afterAt = feet + Offset(5f * unit, -9f * unit)
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
            val r = unit * 1.7f
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

/** How a player looks on the court, worked out once from his face and build. */
private class Look(val skin: Color, val shade: Color, val hair: Color?, val hairKind: Int, val band: Color?, val beard: Boolean, val scale: Float, val number: Int)

/** His hairstyle as one of a few shapes a sprite this small can show. */
private fun hairKind(h: Int) = when (h) {
    0 -> 0                                    // bald
    4, 6, 33 -> 2                             // flat-top, high-top: a tall box
    5, 19, 31 -> 3                            // afros: round and wide
    9, 10, 16, 17, 37, 38, 41 -> 4            // locs and twists hanging down
    13, 27, 28, 36 -> 5                       // a bun or puff on top
    11, 15, 25, 29, 30, 40 -> 6               // mohawks
    14 -> 7                                   // afro puffs
    else -> 1                                 // short
}

private fun lookOf(lg: League, id: Int): Look {
    val pl = lg.playerOrNull(id)
    val f = pl?.let { Face.of(it) }
    val skin = Color(SkinSwatches[(f?.skin ?: 4).coerceIn(0, SkinSwatches.lastIndex)])
    val hair = if (f == null || f.hair == 0) null else Color(HAIR[f.hairColor.coerceIn(0, HAIR.lastIndex)])
    val band = when { f == null -> null; f.acc == 1 -> Color.White; f.acc == 2 -> Color(0xFFD63A3A); f.acc == 3 || f.hair == 7 -> Color(0xFF1A1A1E); else -> null }
    val scale = ((pl?.height ?: 78) / 78f).coerceIn(0.88f, 1.14f)
    return Look(skin, lerp(skin, Color.Black, 0.22f), hair, hairKind(f?.hair ?: 1), band, (f?.beard ?: 0) >= 2, scale, pl?.number ?: 0)
}

/** Digits on a 3x5 grid, for the number on his jersey. */
private val DIGITS = listOf("111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
    "111100111001111", "111100111101111", "111001010010010", "111101111101111", "111101111001111")

/** Poses: standing, a defensive crouch, arms up to shoot or block, and dribbling. */
private const val STAND = 0
private const val GUARD = 1
private const val UP = 2
private const val DRIBBLE = 3

/**
 * One player on [feet], pixel by pixel on a 9x21 grid with a dark outline: shoes in his team's trim,
 * socks, legs, shorts with a stripe, a jersey with straps, a V-neck and his number, arms set by [pose],
 * his head with his ears, eyes, beard and hair, a headband if he wears one. [run] is his stride (and arm
 * swing) while he runs; [jump] lifts him off his shadow. Returns where the ball sits in his hands.
 */
private fun DrawScope.figure(feet: Offset, unit: Float, look: Look, jersey: Color, trim: Color, shorts: Color, pose: Int, jump: Float, run: Int, hero: Boolean, t: Float): Offset {
    val u = unit * look.scale
    drawOval(Color.Black.copy(alpha = 0.3f), Offset(feet.x - u * 4f, feet.y - u * 1f), Size(u * 8f, u * 2f))
    val base = Offset(feet.x, feet.y - jump)
    val cells = ArrayList<Triple<Int, Int, Color>>(220)
    fun c(col: Int, row: Int, color: Color) { cells += Triple(col, row, color) }
    fun rect(c0: Int, r0: Int, c1: Int, r1: Int, color: Color) { for (cc in c0..c1) for (rr in r0..r1) c(cc, rr, color) }
    val skin = look.skin; val shade = look.shade
    val light = lerp(jersey, Color.White, 0.14f); val dark = lerp(jersey, Color.Black, 0.18f)
    val sock = Color(0xFFF2F2F2)
    val shoe = if (contrast(trim, Color(0xFFD9C29A)) > 1.4f) trim else Color(0xFF1C1C20)
    // Legs: apart in a crouch, one up then the other while he runs.
    val wide = if (pose == GUARD) 1 else 0
    val lUp = if (run > 0) 1 else 0
    val rUp = if (run < 0) 1 else 0
    rect(-3 - wide, lUp, -1 - wide, lUp, shoe); rect(1 + wide, rUp, 3 + wide, rUp, shoe)
    rect(-2 - wide, 1 + lUp, -1 - wide, 1 + lUp, sock); rect(1 + wide, 1 + rUp, 2 + wide, 1 + rUp, sock)
    rect(-2 - wide, 2 + lUp, -1 - wide, 4, skin); rect(1 + wide, 2 + rUp, 2 + wide, 4, shade)
    // Shorts with a stripe down each side.
    rect(-3, 5, 3, 7, shorts); rect(-3, 5, -3, 7, trim); rect(3, 5, 3, 7, trim)
    if (pose == GUARD) rect(-1, 5, 1, 5, lerp(shorts, Color.Black, 0.2f))
    // The jersey: lit on his left, shaded on his right, straps and a V in the trim, the number on the chest.
    rect(-3, 8, 3, 14, jersey); rect(-3, 8, -2, 14, light); rect(2, 8, 3, 14, dark)
    rect(-3, 14, -2, 14, trim); rect(2, 14, 3, 14, trim)
    c(-1, 14, skin); c(0, 14, skin); c(1, 14, skin); c(0, 13, skin); c(-1, 13, trim); c(1, 13, trim)
    val num = look.number.coerceIn(0, 99)
    val digits = if (num >= 10) listOf(num / 10 to -3, num % 10 to 1) else listOf(num to -1)
    for ((dgt, x0) in digits) {
        val g = DIGITS[dgt]
        for (rr in 0 until 5) for (cc in 0 until 3) if (g[rr * 3 + cc] == '1') c(x0 + cc, 12 - rr, trim)
    }
    // Arms.
    val swing = if (run != 0) run else 0
    when (pose) {
        UP -> { rect(-4, 13, -4, 21, skin); rect(4, 13, 4, 21, shade) }
        GUARD -> { rect(-6, 12, -4, 13, skin); rect(4, 12, 6, 13, shade); c(-6, 14, skin); c(6, 14, shade) }
        DRIBBLE -> { rect(-4, 9 + swing.coerceAtLeast(0), -4, 14, skin); rect(4, 10, 4, 14, shade); rect(5, 9, 5, 10, shade) }
        else -> { rect(-4, 8 + swing.coerceAtLeast(0), -4, 14, skin); rect(4, 8 + (-swing).coerceAtLeast(0), 4, 14, shade) }
    }
    // Neck and head: ears, eyes, a beard.
    rect(-1, 15, 1, 15, shade)
    rect(-2, 16, 2, 20, skin); rect(2, 16, 2, 20, shade)
    c(-3, 18, skin); c(3, 18, shade)
    c(-1, 18, Color(0xFF15100E)); c(1, 18, Color(0xFF15100E))
    val hc = look.hair ?: Color(0xFF2E2A2A)
    if (look.beard) { rect(-1, 16, 1, 16, hc); c(-2, 17, hc); c(2, 17, hc) }
    // Hair, by its shape.
    val hl = lerp(hc, Color.White, 0.18f)
    when (look.hairKind) {
        1 -> { rect(-2, 20, 2, 21, hc); c(-2, 19, hc); c(2, 19, hc); c(-1, 21, hl) }
        2 -> { rect(-2, 20, 2, 24, hc); rect(-1, 24, 0, 24, hl); c(-2, 19, hc); c(2, 19, hc) }
        3 -> { rect(-3, 19, 3, 23, hc); rect(-2, 24, 2, 24, hc); c(-4, 20, hc); c(4, 20, hc); c(-4, 21, hc); c(4, 21, hc); rect(-1, 23, 0, 24, hl) }
        4 -> { rect(-2, 20, 2, 21, hc); rect(-3, 14, -3, 20, hc); rect(3, 14, 3, 20, hc); c(-2, 19, hc); c(2, 19, hc); c(-1, 21, hl) }
        5 -> { rect(-2, 20, 2, 21, hc); rect(-1, 22, 1, 24, hc); c(0, 24, hl) }
        6 -> { rect(-1, 20, 1, 24, hc); c(0, 24, hl); c(-2, 20, lerp(hc, skin, 0.5f)); c(2, 20, lerp(hc, skin, 0.5f)) }
        7 -> { rect(-2, 20, 2, 21, hc); rect(-5, 21, -3, 23, hc); rect(3, 21, 5, 23, hc) }
        else -> {}
    }
    if (look.hairKind == 0) { rect(-2, 21, 2, 21, skin); c(-1, 21, lerp(skin, Color.White, 0.25f)) }
    look.band?.let { rect(-2, 19, 2, 19, it); if (look.hairKind == 4) { c(-3, 19, it); c(3, 19, it) } }
    // Outline first, then the colors over it.
    val ol = u * 0.42f
    val outline = Color(0xFF120F0D)
    for ((col, row, _) in cells) drawRect(outline, Offset(base.x + (col - 0.5f) * u - ol, base.y - (row + 1) * u - ol), Size(u + ol * 2, u + ol * 2))
    for ((col, row, color) in cells) drawRect(color, Offset(base.x + (col - 0.5f) * u, base.y - (row + 1) * u), Size(u + 0.6f, u + 0.6f))
    if (hero) {
        val top = base.y - 28f * u
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(base.x - u * 2f, top); lineTo(base.x + u * 2f, top); lineTo(base.x, top + u * 2.2f); close()
        }
        drawPath(path, Color(0xFFFFC94D))
        drawPath(path, outline, style = Stroke(u * 0.4f))
    }
    // Where the ball is: overhead to shoot, bouncing at his right hand on the dribble, at his hip otherwise.
    return when (pose) {
        UP -> base + Offset(0f, -23f * u)
        DRIBBLE -> base + Offset(6f * u, -(1.5f + kotlin.math.abs(sin(t * 2.6f)) * 7.5f) * u)
        else -> base + Offset(5f * u, -9f * u)
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

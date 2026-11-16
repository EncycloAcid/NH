package com.nh.hoopscoach.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.nh.hoopscoach.game.Ratings
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ---------- numbers that count ----------

/**
 * A number that counts to its value: up from zero when it first shows on a screen that's opening, and from
 * the old value to the new one whenever it changes. [format] turns the running value into text.
 */
@Composable
fun CountUpText(value: Long, style: TextStyle, color: Color, modifier: Modifier = Modifier, durationMs: Int = 700, format: (Long) -> String = { "$it" }) {
    val start = LocalScreenStart.current
    val opening = remember { now() - start < 700 }
    val a = remember { Animatable(if (opening) 0f else value.toFloat()) }
    LaunchedEffect(value) { a.animateTo(value.toFloat(), tween(durationMs, easing = FastOutSlowInEasing)) }
    Text(format(a.value.roundToInt().toLong().let { if (a.isRunning) it else value }), style = style, color = color, maxLines = 1, modifier = modifier)
}

// ---------- confetti ----------

private class Bit(val x: Float, val vx: Float, val vy: Float, val spin: Float, val w: Float, val h: Float, val color: Color, val delay: Float)

/**
 * A burst of confetti over its box that falls once for each [key]: strips in [colors] thrown up from the
 * top middle, tumbling down and fading out.
 */
@Composable
fun ConfettiBurst(key: Any, colors: List<Color>, modifier: Modifier = Modifier, count: Int = 90, durationMs: Int = 2600) {
    val p = remember(key) { Animatable(0f) }
    LaunchedEffect(key) { p.animateTo(1f, tween(durationMs, easing = LinearEasing)) }
    val bits = remember(key) {
        val r = kotlin.random.Random(key.hashCode())
        List(count) {
            Bit(0.3f + r.nextFloat() * 0.4f, (r.nextFloat() - 0.5f) * 1.5f, -0.5f - r.nextFloat() * 0.9f, (r.nextFloat() - 0.5f) * 14f,
                4f + r.nextFloat() * 5f, 7f + r.nextFloat() * 8f, colors[it % colors.size], r.nextFloat() * 0.18f)
        }
    }
    if (p.value >= 1f) return
    Canvas(modifier) {
        val t0 = p.value
        for (b in bits) {
            val t = ((t0 - b.delay) / (1f - b.delay)).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val sec = t * 2.4f
            val x = (b.x + b.vx * sec * 0.35f + sin(sec * 3f + b.spin) * 0.02f) * size.width
            val y = (0.18f + b.vy * sec * 0.55f + 0.62f * sec * sec * 0.5f) * size.height
            val alpha = (1f - ((t - 0.75f) / 0.25f).coerceIn(0f, 1f))
            rotate(b.spin * sec * 40f, Offset(x, y)) {
                val flip = kotlin.math.abs(cos(sec * b.spin))
                drawRect(b.color.copy(alpha = alpha), Offset(x - b.w / 2 * density / 2, y - b.h / 2 * density / 2), Size(b.w * density / 2, b.h * flip * density / 2 + 1f))
            }
        }
    }
}

/**
 * A celebration across the top of the whole app: a banner in [color] with [title] and [sub] that slides
 * in over a burst of confetti, then leaves on its own.
 */
@Composable
fun CelebrationOverlay(c: Celebration, onDone: () -> Unit) {
    val enter = remember(c) { Animatable(0f) }
    LaunchedEffect(c) {
        enter.animateTo(1f, Motion.Pop)
        kotlinx.coroutines.delay(2300)
        enter.animateTo(0f, tween(260))
        onDone()
    }
    Box(Modifier.fillMaxSize()) {
        ConfettiBurst(c, listOf(c.color, P.gold, Color.White, lerp(c.color, Color.White, 0.5f)), Modifier.fillMaxSize(), count = 120)
        Column(
            Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 18.dp, start = 24.dp, end = 24.dp)
                .graphicsLayer { val v = enter.value; alpha = v.coerceIn(0f, 1f); translationY = (1f - v) * -60f; scaleX = 0.9f + 0.1f * v; scaleY = 0.9f + 0.1f * v }
                .clip(RoundedCornerShape(18.dp)).background(c.color).border(2.dp, P.gold, RoundedCornerShape(18.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(c.title, style = T.hero.copy(fontSize = 28.sp, lineHeight = 28.sp), color = onColor(c.color), maxLines = 1)
            if (c.sub.isNotEmpty()) Text(c.sub, style = T.bodyB.copy(fontSize = 13.sp), color = onColor(c.color).copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** One celebration for [CelebrationOverlay]: the stamp keeps two alike ones apart. */
class Celebration(val title: String, val sub: String, val color: Color, val stamp: Long = System.nanoTime())

// ---------- the arena behind a portrait ----------

private val blurred = HashMap<String, ImageBitmap>()

/** The middle of a court's art shrunk to a few dozen pixels: drawn back up with smoothing, it's a soft blur. */
private fun softCourt(t: Team): ImageBitmap? {
    if (t.customKit) return null
    val art = CourtArt.image(t) ?: return null
    val key = t.slot + "|" + t.abbr
    return blurred.getOrPut(key) {
        val small = ImageBitmap(40, 22)
        val cv = androidx.compose.ui.graphics.Canvas(small)
        cv.drawImageRect(art, IntOffset(260, 150), IntSize(504, 276), IntOffset.Zero, IntSize(40, 22), Paint().apply { filterQuality = FilterQuality.Medium })
        small
    }
}

/**
 * A packed arena behind a player, like a trading card's photo: the crowd in the stands up top (dots in the
 * team's colors and street clothes, darker toward the rafters), lights, and his home floor below, soft.
 */
@Composable
fun ArenaBackdrop(t: Team?, modifier: Modifier = Modifier) {
    val floor = remember(t?.id) { t?.let { softCourt(it) } }
    val primary = t?.let { Color(it.primary) } ?: Color(0xFF3A3F47)
    val second = t?.let { Color(it.secondary) } ?: Color(0xFF8A8F99)
    val seed = t?.id ?: 7
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color(0xFF07080B), lerp(Color(0xFF14161C), primary, 0.25f), Color(0xFF0E0F13))))
        // The floor, soft, across the bottom.
        val fy = h * 0.6f
        if (floor != null) drawImage(floor, dstOffset = IntOffset(-(w * 0.15f).toInt(), fy.toInt()), dstSize = IntSize((w * 1.3f).toInt(), (h - fy + 2).toInt()), filterQuality = FilterQuality.Low)
        else drawRect(Brush.verticalGradient(listOf(Color(0xFFB98D5B), Color(0xFFD8B07A))), Offset(0f, fy), Size(w, h - fy))
        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), startY = fy, endY = fy + (h - fy) * 0.6f), Offset(0f, fy), Size(w, h - fy))
        // The crowd: rows of heads, smaller and dimmer as they go up.
        val r = kotlin.random.Random(seed)
        val tones = listOf(primary, second, Color(0xFF6B6F7A), Color(0xFF9A8C7A), Color(0xFF4A4E58), Color.White)
        val rows = 20
        for (row in 0 until rows) {
            val y = fy - (row + 0.6f) * (fy / rows)
            val depth = row / rows.toFloat()
            val rad = (w * 0.011f) * (1f - depth * 0.45f)
            var x = -rad + r.nextFloat() * rad * 2
            while (x < w + rad) {
                val c = tones[r.nextInt(tones.size)]
                drawCircle(lerp(c, Color.Black, 0.45f + depth * 0.45f).copy(alpha = 0.7f), rad, Offset(x, y + (r.nextFloat() - 0.5f) * rad))
                x += rad * (2.1f + r.nextFloat() * 0.8f)
            }
        }
        // Lights from the rafters, and camera flashes in the stands.
        for (i in 0 until 3) {
            val cx = w * (0.18f + i * 0.32f)
            drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), Offset(cx, -h * 0.05f), h * 0.55f), h * 0.55f, Offset(cx, -h * 0.05f))
        }
        repeat(9) { drawCircle(Color.White.copy(alpha = 0.55f + r.nextFloat() * 0.4f), w * 0.006f, Offset(r.nextFloat() * w, r.nextFloat() * fy * 0.9f)) }
        // Vignette.
        drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)), Offset(w / 2, h * 0.55f), maxOf(w, h) * 0.75f))
    }
}

// ---------- the trading card ----------

/** A card's rarity by the rating it shows: bronze, silver, gold, diamond. */
private class Rarity(val name: String, val edge: List<Color>, val ink: Color)

private fun rarityOf(v: Int) = when {
    v >= 90 -> Rarity("DIAMOND", listOf(Color(0xFF7DF9FF), Color(0xFFB98CFF), Color(0xFFFF8CE0), Color(0xFF7DF9FF)), Color(0xFF0B1020))
    v >= 80 -> Rarity("GOLD", listOf(Color(0xFFFFE08A), Color(0xFFC9962E), Color(0xFFFFE9A8), Color(0xFFB07F22)), Color(0xFF2A1E05))
    v >= 70 -> Rarity("SILVER", listOf(Color(0xFFF2F4F7), Color(0xFF9BA3AE), Color(0xFFE3E7EC), Color(0xFF7D8590)), Color(0xFF1A1D22))
    else -> Rarity("BRONZE", listOf(Color(0xFFE2A46E), Color(0xFF8A5530), Color(0xFFD99A62), Color(0xFF6E4122)), Color(0xFF221208))
}

/**
 * A collectible card for a player: a rarity edge by his rating, his arena behind him, his rating and
 * position, his team's badge, a nameplate in its colors with this season's line, his signature shoe if he
 * has one, and foil (a moving rainbow shine) for a legend: a legacy tier of All-Star caliber or better for
 * your player, a long list of honors for anyone else.
 */
@Composable
private fun CardFront(lg: League, p: Player, width: Dp, tilt: Float) {
    val t = lg.teams.getOrNull(p.teamId) ?: lg.teams.getOrNull(p.lastTeam)
    val v = shown(p.ovr)
    val rar = rarityOf(v)
    val primary = t?.let { Color(it.primary) } ?: Color(0xFF3A3F47)
    val secondary = t?.let { Color(it.secondary) } ?: Color.White
    val hero = p.id == lg.heroId && lg.playerMode
    val majors = p.awards.count { a -> listOf("MVP", "Champion", "All-NBA", "All-America", "All-American", "Nat MVP", "Finals MVP", "All-Star").any { a.contains(it) } }
    val foil = (hero && lg.legacyTier >= 3) || majors >= 4
    val shoe = if (hero) lg.road?.shoe?.let { com.nh.hoopscoach.game.Shoes.shoeName(lg, it) } else null
    val shine by rememberInfiniteTransition(label = "foil").animateFloat(-0.6f, 1.6f, infiniteRepeatable(tween(3200, easing = LinearEasing), RepeatMode.Restart), label = "f")
    // Everything is sized off the card's width, so a small card is the big one shrunk, not a cramped copy.
    val k = width.value / 240f
    fun sz(x: Float) = (x * k).sp
    val plateH = width * 0.3f
    val ink = onColor(primary)
    // The nameplate's spot color: the school's second color if it reads on the first, else the rarity's.
    val spot = if (contrast(secondary, primary) >= 2.0f) secondary else rar.edge[0]
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp * k)).rarityEdge(rar).padding(5.dp * k)) {
        Box(Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp * k))) {
            // The art: his arena, a glow in his team's color behind him, and the player from the waist up,
            // standing on the nameplate so his number shows.
            Box(Modifier.fillMaxSize().padding(bottom = plateH)) {
                ArenaBackdrop(t, Modifier.fillMaxSize())
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(Brush.radialGradient(listOf(lerp(primary, Color.White, 0.25f).copy(alpha = 0.55f), Color.Transparent),
                        Offset(size.width / 2, size.height * 0.62f), size.width * 0.62f), size.width * 0.62f, Offset(size.width / 2, size.height * 0.62f))
                    // Shade the top-left corner so the rating reads over a bright crowd.
                    drawRect(Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent), Offset(0f, 0f), size.width * 0.5f))
                }
                // His school's mark, faint, behind him.
                t?.let { Box(Modifier.align(Alignment.Center).padding(bottom = width * 0.12f).graphicsLayer { alpha = 0.13f }) { TeamBadge(it, width * 0.62f) } }
                Box(Modifier.fillMaxWidth(1.02f).aspectRatio(1f).align(Alignment.BottomCenter).offset(y = 2.dp)) {
                    BasketballCharacterCanvas(
                        face = Face.of(p), jerseyKey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name),
                        primary = (t?.primary ?: 0xFF5C6B7AL).toInt(), secondary = (t?.secondary ?: 0xFFFFFFFFL).toInt(),
                        number = p.number, gear = Gear.decode(p.gear), label = t?.abbr, background = Color.Transparent, corner = 0.dp,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                // Foil: a slow rainbow band across the art (never over the nameplate's words), and a few glints.
                if (foil) Canvas(Modifier.fillMaxSize()) {
                    val x = (shine + tilt / 40f) * size.width
                    drawRect(Brush.linearGradient(
                        listOf(Color.Transparent, Color(0x2600FFFF), Color(0x30FF66FF), Color(0x26FFFF66), Color.Transparent),
                        start = Offset(x - size.width * 0.45f, 0f), end = Offset(x + size.width * 0.45f, size.height),
                    ))
                    val r = kotlin.random.Random(p.id)
                    // Small soft glints that twinkle in turn, kept off his face.
                    repeat(6) {
                        val gx = r.nextFloat() * size.width
                        val gy = r.nextFloat() * size.height * 0.45f
                        val a = (sin((shine * 4f + it * 0.7f) * PI.toFloat())).coerceIn(0f, 1f) * 0.75f
                        if (a <= 0.02f) return@repeat
                        val g = size.width * 0.012f
                        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = a * 0.6f), Color.Transparent), Offset(gx, gy), g * 2.2f), g * 2.2f, Offset(gx, gy))
                        drawLine(Color.White.copy(alpha = a), Offset(gx - g * 1.6f, gy), Offset(gx + g * 1.6f, gy), strokeWidth = g * 0.28f)
                        drawLine(Color.White.copy(alpha = a), Offset(gx, gy - g * 1.6f), Offset(gx, gy + g * 1.6f), strokeWidth = g * 0.28f)
                    }
                }
            }
            // Rating and position, top left; the badge, top right.
            Column(Modifier.align(Alignment.TopStart).padding(10.dp * k), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$v", style = T.hero.copy(fontSize = sz(40f), lineHeight = sz(40f)), color = Color.White)
                Text(p.posLabel, style = T.label.copy(fontSize = sz(11f)), color = Color.White.copy(alpha = 0.9f))
            }
            if (p.style != com.nh.hoopscoach.game.Arch.MANUAL) Text(p.style.label.uppercase(), style = T.label.copy(fontSize = sz(7.5f), lineHeight = sz(8f), letterSpacing = 0.8.sp),
                color = rar.ink, maxLines = 1,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp * k, bottom = plateH + 6.dp * k)
                    .clip(RoundedCornerShape(4.dp * k)).background(Brush.linearGradient(rar.edge)).padding(horizontal = 5.dp * k, vertical = 1.5.dp * k))
            t?.let { Box(Modifier.align(Alignment.TopEnd).padding(10.dp * k)) { TeamBadge(it, width * 0.16f) } }
            // The nameplate: a stripe in the spot color, then the name and his line.
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(plateH)
                .background(Brush.verticalGradient(listOf(primary, lerp(primary, Color.Black, 0.4f))))) {
                Box(Modifier.fillMaxWidth().height(3.dp * k).background(spot))
                Column(Modifier.fillMaxSize().padding(horizontal = 10.dp * k, vertical = 5.dp * k), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.first.uppercase(), style = T.label.copy(fontSize = sz(9.5f), lineHeight = sz(10f)), color = ink.copy(alpha = 0.8f), maxLines = 1)
                            FitText(p.last.uppercase(), T.title.copy(fontSize = sz(22f), lineHeight = sz(23f)), ink, minScale = 0.55f)
                        }
                        Text(rar.name + if (foil) " ★" else "", style = T.label.copy(fontSize = sz(8.5f), lineHeight = sz(9f)), color = rar.ink, maxLines = 1,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp * k)).background(Brush.linearGradient(rar.edge)).padding(horizontal = 5.dp * k, vertical = 2.dp * k))
                    }
                    // This season's line as three cells, or last season's, or where he plays if he hasn't yet.
                    val s = p.stats.takeIf { it.gp > 0 } ?: p.career.lastOrNull { it.s.gp > 0 }?.s
                    if (s != null) Row(Modifier.fillMaxWidth()) {
                        for ((label, value) in listOf("PTS" to s.ppg, "REB" to s.rpg, "AST" to s.apg)) {
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.Bottom) {
                                Text("%.1f".format(value), style = T.numSm.copy(fontSize = sz(15f), lineHeight = sz(15f)), color = ink, maxLines = 1)
                                Text(" $label", style = T.label.copy(fontSize = sz(8f), lineHeight = sz(9f)), color = ink.copy(alpha = 0.7f), maxLines = 1, modifier = Modifier.padding(bottom = 1.dp))
                            }
                        }
                    } else Text(listOfNotNull(if (lg.pro) "Rookie" else p.cls, t?.let { if (lg.pro) it.name else it.school }).joinToString(" · "),
                        style = T.small.copy(fontSize = sz(11f), lineHeight = sz(12f)), color = ink.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (shoe != null) Text("THE ${shoe.uppercase()}", style = T.label.copy(fontSize = sz(8f), lineHeight = sz(9f)), color = spot, maxLines = 1)
                }
            }
            // A thin inner frame, like the print line on a real card.
            Box(Modifier.fillMaxSize().padding(3.dp * k).border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp * k)))
        }
    }
}

/** The card's metal edge; gold and diamond catch the light as it slowly moves across them. */
@Composable
private fun Modifier.rarityEdge(rar: Rarity): Modifier {
    val moving = rar.name == "GOLD" || rar.name == "DIAMOND"
    val sweep = if (moving) rememberInfiniteTransition(label = "edge").animateFloat(0f, 1f, infiniteRepeatable(tween(4200, easing = LinearEasing), RepeatMode.Reverse), label = "e").value else 0.5f
    return this.drawBehind {
        drawRect(Brush.linearGradient(rar.edge, start = Offset(size.width * (sweep - 0.6f), 0f), end = Offset(size.width * (sweep + 0.6f), size.height), tileMode = TileMode.Mirror))
        if (rar.name == "DIAMOND") drawRect(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.45f), Color.Transparent),
            start = Offset(size.width * (sweep * 1.6f - 0.5f), 0f), end = Offset(size.width * (sweep * 1.6f - 0.2f), size.height)))
    }
}

/**
 * The back of the card: his name and team, the bio (height, weight, age or class, hometown), his best
 * ratings as bars, his last few seasons, his honors, and the set line with the card number.
 */
@Composable
private fun CardBack(lg: League, p: Player, width: Dp) {
    val t = lg.teams.getOrNull(p.teamId) ?: lg.teams.getOrNull(p.lastTeam)
    val rar = rarityOf(shown(p.ovr))
    val primary = t?.let { Color(it.primary) } ?: Color(0xFF3A3F47)
    val k = width.value / 240f
    fun sz(x: Float) = (x * k).sp
    val ink = Color.White
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp * k)).rarityEdge(rar).padding(5.dp * k)) {
        Column(
            Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp * k))
                .background(Brush.verticalGradient(listOf(lerp(primary, Color.Black, 0.55f), Color(0xFF0D0F14))))
                .padding(horizontal = 10.dp * k, vertical = 9.dp * k),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.name.uppercase(), style = T.title.copy(fontSize = sz(17f), lineHeight = sz(18f)), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(p.posLabel, t?.let { if (lg.pro) it.name else it.school }).joinToString(" · "), style = T.label.copy(fontSize = sz(8.5f)), color = ink.copy(alpha = 0.7f), maxLines = 1)
                    if (p.id == lg.heroId) com.nh.hoopscoach.game.LegacyMode.father(lg)?.let { Text("SON OF ${it.name.uppercase()}", style = T.label.copy(fontSize = sz(8f)), color = P.gold, maxLines = 1) }
                    if (p.id == lg.legacySonId) Text("THE COACH'S SON", style = T.label.copy(fontSize = sz(8f)), color = P.gold, maxLines = 1)
                }
                t?.let { TeamBadge(it, width * 0.13f) }
            }
            Spacer(Modifier.height(6.dp * k))
            // The bio, four boxes.
            Row(Modifier.fillMaxWidth()) {
                listOf("HT" to Ratings.heightLabel(p.height), "WT" to "${p.weight}", (if (lg.pro) "AGE" else "CLASS") to (if (lg.pro) "${p.age}" else p.cls),
                    "FROM" to p.hometown.substringBefore(",").ifEmpty { "—" }).forEach { (l, v) ->
                    Column(Modifier.weight(1f)) {
                        Text(l, style = T.label.copy(fontSize = sz(7f)), color = ink.copy(alpha = 0.55f))
                        Text(v, style = T.bodyB.copy(fontSize = sz(10.5f), lineHeight = sz(11f)), color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.height(7.dp * k))
            // His best six ratings.
            Text("TOP RATINGS", style = T.label.copy(fontSize = sz(7.5f)), color = rar.edge[0])
            com.nh.hoopscoach.game.Attr.entries.sortedByDescending { p[it] }.take(6).forEach { a ->
                val r = shown(p[a])
                Row(Modifier.fillMaxWidth().padding(top = 2.dp * k), verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, style = T.small.copy(fontSize = sz(9f), lineHeight = sz(10f)), color = ink.copy(alpha = 0.85f), modifier = Modifier.width(width * 0.33f), maxLines = 1)
                    Box(Modifier.weight(1f).height(5.dp * k).clip(RoundedCornerShape(3.dp * k)).background(ink.copy(alpha = 0.12f))) {
                        Box(Modifier.fillMaxWidth((r / 99f).coerceIn(0.05f, 1f)).height(5.dp * k).clip(RoundedCornerShape(3.dp * k)).background(Brush.horizontalGradient(rar.edge)))
                    }
                    Text("$r", style = T.numSm.copy(fontSize = sz(11f), lineHeight = sz(11f)), color = ink, modifier = Modifier.width(width * 0.1f).padding(start = 4.dp * k), maxLines = 1)
                }
            }
            Spacer(Modifier.height(7.dp * k))
            // His last seasons.
            val rows = (p.career.filter { it.s.gp > 0 }.map { Triple("${it.season}", it.team, it.s) } +
                (if (p.stats.gp > 0) listOf(Triple("${lg.season}", t?.abbr ?: "", p.stats)) else emptyList())).takeLast(3)
            if (rows.isNotEmpty()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("SEASON", style = T.label.copy(fontSize = sz(7f)), color = rar.edge[0], modifier = Modifier.weight(1f))
                    listOf("PTS", "REB", "AST").forEach { Text(it, style = T.label.copy(fontSize = sz(7f)), color = ink.copy(alpha = 0.55f), modifier = Modifier.width(width * 0.13f)) }
                }
                rows.forEach { (yr, team, st) ->
                    Row(Modifier.fillMaxWidth().padding(top = 1.dp * k)) {
                        Text("$yr ${team.take(14)}", style = T.small.copy(fontSize = sz(9f), lineHeight = sz(10f)), color = ink.copy(alpha = 0.85f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        listOf(st.ppg, st.rpg, st.apg).forEach { Text("%.1f".format(it), style = T.numSm.copy(fontSize = sz(10.5f), lineHeight = sz(11f)), color = ink, modifier = Modifier.width(width * 0.13f)) }
                    }
                }
            } else Text("No games yet: his story starts this season.", style = T.small.copy(fontSize = sz(9f)), color = ink.copy(alpha = 0.7f))
            // The scouting note: how he plays, and who he plays like.
            Spacer(Modifier.height(8.dp * k))
            Text("SCOUTING REPORT", style = T.label.copy(fontSize = sz(7.5f)), color = rar.edge[0])
            Text(p.style.blurb.trimEnd('.') + "." + (p.likeA.takeIf { it.isNotBlank() }?.let { " Plays like $it." } ?: ""),
                style = T.small.copy(fontSize = sz(9f), lineHeight = sz(11f)), color = ink.copy(alpha = 0.85f), maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp * k))
            Spacer(Modifier.weight(1f))
            if (p.awards.isNotEmpty()) Text("HONORS · ${p.awards.size}  " + p.awards.takeLast(2).reversed().joinToString(" · "), style = T.label.copy(fontSize = sz(7.5f), lineHeight = sz(9f)),
                color = P.gold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp * k))
            Text("HOOPS DYNASTY · ${lg.season} · NO. ${(p.id % 499) + 1} · ${rar.name}", style = T.label.copy(fontSize = sz(7f), letterSpacing = 1.sp), color = ink.copy(alpha = 0.45f), maxLines = 1)
        }
    }
}

/**
 * A collectible card for a player: the front (his picture, rating, nameplate, foil for a legend) and the
 * back (bio, ratings, seasons, honors). Tap it to flip it over; drag it side to side to tilt it and watch
 * the foil move. [interactive] off leaves taps to whatever holds it.
 */
@Composable
fun TradingCard(lg: League, p: Player, modifier: Modifier = Modifier, width: Dp = 240.dp, interactive: Boolean = true) {
    var back by remember(p.id) { mutableStateOf(false) }
    val flip by androidx.compose.animation.core.animateFloatAsState(if (back) 180f else 0f, tween(560, easing = FastOutSlowInEasing), label = "flip")
    val tilt = remember(p.id) { Animatable(0f) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val gestures = if (!interactive) Modifier else Modifier
        .pointerInput(p.id) { detectTapGestures { back = !back } }
        .pointerInput(p.id) {
            detectHorizontalDragGestures(
                onDragEnd = { scope.launch { tilt.animateTo(0f, androidx.compose.animation.core.spring(dampingRatio = 0.45f, stiffness = 180f)) } },
                onDragCancel = { scope.launch { tilt.animateTo(0f) } },
            ) { change, dx -> change.consume(); scope.launch { tilt.snapTo((tilt.value + dx / size.width * 40f).coerceIn(-18f, 18f)) } }
        }
    Box(
        modifier.width(width).aspectRatio(0.71f).then(gestures).graphicsLayer {
            rotationY = flip + tilt.value
            cameraDistance = 14f * density
        },
    ) {
        if (flip <= 90f) CardFront(lg, p, width, tilt.value)
        else Box(Modifier.fillMaxSize().graphicsLayer { rotationY = 180f }) { CardBack(lg, p, width) }
    }
}

private fun Modifier.offset(y: Dp) = this.then(Modifier.padding(top = 0.dp)).graphicsLayer { translationY = y.value * density }

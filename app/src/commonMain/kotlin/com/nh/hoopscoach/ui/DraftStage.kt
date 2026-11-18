package com.nh.hoopscoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.EraData
import com.nh.hoopscoach.game.NbaData
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.assetBytes
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.math.sin

/**
 * The Draft Night art (assets/draft, from the user's retro draft pack): the hall's pieces, drawn back to front.
 * The center banner and the podium come with their year painted out; the year is written on them here.
 */
object DraftArt {
    private fun a(name: String) = Logos.asset("draft/$name.png")
    val banner get() = a("center_banner_blank")
    val podium get() = a("podium_blank")
    val commissioner get() = a("commissioner_full_body")
    val crowd get() = a("arena_crowd_and_railings")
    val curtain get() = a("blue_curtain_tall")
    val league get() = a("league_vertical_banner")

    /** One club in the team pack's teams.json (assets/teams, from the user's Kotlin draft pack): its pennant and badge. */
    @Serializable
    private class PackTeam(val name: String = "", val abbreviation: String = "", val banner: String = "", val badge: String = "")

    /** The 30 franchises in the team pack, by today's abbreviation. */
    private val PACK: Map<String, PackTeam> by lazy {
        val text = assetBytes("teams/teams.json")?.decodeToString() ?: return@lazy emptyMap()
        runCatching { Json { ignoreUnknownKeys = true }.decodeFromString<List<PackTeam>>(text) }.getOrDefault(emptyList()).associateBy { it.abbreviation }
    }

    fun pennant(slot: String) = PACK[slot]?.banner?.let { Logos.asset(it) }
    fun badge(slot: String) = PACK[slot]?.badge?.let { Logos.asset(it) }

    /**
     * A club on Draft Night: [slot] is its franchise (today's abbreviation, which picks its pennant and badge), [abbr],
     * [city] and [nick] what it was called that year. A club that went by another name then (the Vancouver Grizzlies
     * in '98) gets its pennant's and badge's name plates painted over.
     */
    class Club(val slot: String, val abbr: String, val city: String = "", val nick: String = "") {
        val renamed get() = abbr != slot && abbr in RENAMED && PACK.containsKey(slot)
    }

    /** The clubs that had another name (not just another spelling: PHO and BRK are still the Suns and the Nets). */
    private val RENAMED = setOf("NJN", "SEA", "VAN", "WSB", "NOH", "NOK")

    fun club(t: Team) = Club(t.slot.ifEmpty { t.abbr }, t.abbr, t.school, t.mascot)

    /** A club by its name on the draft board ("Vancouver Grizzlies"), in that season's league. */
    fun club(name: String, season: Int): Club? {
        for (y in listOf(season + 1, season)) EraData.teams(y)?.firstOrNull { "${it.city} ${it.name}" == name }?.let { return Club(it.slot, it.abbr, it.city, it.name) }
        return NbaData.TEAMS.firstOrNull { it.csv == name || "${it.city} ${it.name}" == name }?.let { Club(it.abbr, it.abbr, it.city, it.name) }
    }

    /** The pennant's plates (in its 96 x 160 pixels): the club's name across the top, its abbreviation at the foot. */
    const val PW = 96f
    val NAME_PLATE = floatArrayOf(17f, 19f, 62f, 21f)
    val ABBR_PLATE = floatArrayOf(25f, 113f, 46f, 12f)
    /** The badge's abbreviation plate (in its 64 x 64 pixels). */
    val BADGE_PLATE = floatArrayOf(13f, 43f, 38f, 11f)
    val PLATE = Color(0xFF101522)
    val PLATE_TEXT = Color(0xFFEDEDE8)

    /** The six pennants over the stage: the clubs on the clock and up next, from pick [at] of [order] (the last six once it's nearly over). */
    fun <T> window(order: List<T>, at: Int): Pair<List<T>, Int> {
        if (order.isEmpty()) return emptyList<T>() to -1
        val from = at.coerceAtMost(order.size - 6).coerceAtLeast(0)
        return order.subList(from, minOf(order.size, from + 6)) to (if (at < order.size) at - from else -1)
    }
}

/** The stage art is laid out on its own canvas: [SW] wide, [SH] tall (the dialogue box covers the stage's front edge, as on TV). */
private const val SW = 540f
private const val SH = 830f

/**
 * Draft Night on stage: the dark hall and its lights, the curtain, the draft banner with [year], the pennants of
 * [clubs] (the six picking now and next, in order; [onClock] is lit up, under a spotlight) and the league's banners,
 * the crowd at its rails, the commissioner behind the podium (the envelope up), and the podium's plate with [year].
 * [overlay] goes on top (the commissioner's dialogue box, over the floor).
 */
@Composable
fun DraftStage(year: Int, clubs: List<DraftArt.Club>, onClock: Int, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    val art = remember {
        object {
            val banner = DraftArt.banner; val podium = DraftArt.podium; val man = DraftArt.commissioner
            val crowd = DraftArt.crowd; val curtain = DraftArt.curtain; val league = DraftArt.league
        }
    }
    val pennants = remember(clubs.map { it.slot }) { clubs.map { DraftArt.pennant(it.slot) ?: DraftArt.league } }
    val anim = rememberInfiniteTransition(label = "stage")
    val clock by anim.animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "clock")
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(SW / SH)) {
        val u: Dp = maxWidth / SW
        /** [img] at ([x], [y]) on the stage's canvas, [w] wide, its own shape kept; crisp pixels. */
        @Composable
        fun put(img: ImageBitmap?, x: Float, y: Float, w: Float, m: Modifier = Modifier, filter: ColorFilter? = null) {
            if (img == null) return
            val h = w * img.height / img.width
            Image(img, null, m.offset(x = u * x, y = u * y).size(u * w, u * h), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None, colorFilter = filter)
        }
        // 1. The hall: dark, a truss of lights overhead (twinkling), the floor in front.
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            drawRect(Brush.verticalGradient(listOf(Color(0xFF05070D), Color(0xFF0C1430), Color(0xFF13245C)), endY = 700 * k))
            drawRect(Color(0xFF13286B), Offset(0f, 700 * k), Size(size.width, size.height - 700 * k))
            drawRect(Color(0xFF2A2F3B), Offset(0f, 30 * k), Size(size.width, 9 * k))
            var x = 0f
            while (x < SW) { drawRect(Color(0xFF454C5E), Offset(x * k, 31 * k), Size(2 * k, 7 * k)); x += 10f }
            val lights = listOf(Color.White, Color(0xFF6FA8FF), Color(0xFFFFE08A), Color(0xFFFF5A5A))
            for ((i, lx) in (24 until 540 step 62).withIndex()) {
                val c = lights[i % lights.size]
                val on = 0.75f + 0.25f * sin(clock * 2.6f + i * 1.3f)
                drawCircle(c.copy(alpha = 0.18f * on), 11 * k, Offset(lx * k, 44 * k))
                drawCircle(c.copy(alpha = on), 4 * k, Offset(lx * k, 44 * k))
            }
        }
        // 2. The tall curtain behind the commissioner.
        put(art.curtain, 150f, 214f, 240f)
        // 3. The draft banner, with this year's draft on its shield.
        put(art.banner, 178f, 58f, 184f)
        Box(Modifier.offset(x = u * 236f, y = u * 186f).size(u * 70f, u * 42f), contentAlignment = Alignment.Center) {
            Text("'${(year % 100).toString().padStart(2, '0')}", style = T.title.copy(fontSize = (u.value * 38).sp, lineHeight = (u.value * 38).sp,
                shadow = Shadow(Color(0xFF14102A), Offset(0f, u.value * 2.5f), 0f)), color = Color(0xFFF4F1EA))
        }
        // The pennants, three to a side in pick order, all at one pixel scale, each centered on its spot: the club
        // on the clock under a spotlight from the truss, swaying, the ones up next a shade darker. A club with
        // another name that year gets its name plate painted.
        val spots = listOf(30f, 88f, 146f, 394f, 452f, 510f)
        val pw = 58f
        val dim = ColorFilter.tint(Color(0x66000000), BlendMode.SrcAtop)
        if (onClock in pennants.indices && onClock < spots.size) Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            val cx = spots[onClock] * k
            val glow = 0.16f + 0.05f * sin(clock * 3f)
            drawPath(Path().apply {
                moveTo(cx - 5 * k, 44 * k); lineTo(cx + 5 * k, 44 * k); lineTo(cx + 40 * k, 172 * k); lineTo(cx - 40 * k, 172 * k); close()
            }, Brush.verticalGradient(listOf(Color(0xFFFFF3C4).copy(alpha = glow * 1.6f), Color(0xFFFFF3C4).copy(alpha = 0f)), startY = 44 * k, endY = 172 * k))
        }
        pennants.forEachIndexed { i, img ->
            if (i >= spots.size || img == null) return@forEachIndexed
            val lit = i == onClock
            val sway = if (sin(clock * (if (lit) 2.4f else 1.2f) + i * 1.7f) > (if (lit) 0.3f else 0.85f)) 1f else 0f
            val league = img === art.league
            val w = if (league) 50f else pw
            val x = spots[i] - w / 2
            val y = 52f + sway
            put(img, x, y, w, filter = if (lit || onClock < 0) null else dim)
            val c = clubs[i]
            if (c.renamed && !league) {
                // That year's name on the plates: the city and the nickname across the top, the abbreviation at the foot.
                val k = pw / DraftArt.PW
                @Composable
                fun plate(r: FloatArray, content: @Composable () -> Unit) =
                    Box(Modifier.offset(x = u * (x + r[0] * k), y = u * (y + r[1] * k)).size(u * (r[2] * k), u * (r[3] * k)).background(DraftArt.PLATE)
                        .then(if (lit || onClock < 0) Modifier else Modifier.background(Color(0x66000000))), contentAlignment = Alignment.Center) { content() }
                val lines = listOf(c.city.uppercase(), c.nick.uppercase()).filter { it.isNotEmpty() }
                val f = minOf(6.6f, 58f / (lines.maxOf { it.length }.coerceAtLeast(1) * 0.66f)) * k
                plate(DraftArt.NAME_PLATE) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        for (l in lines) Text(l, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (u.value * f).sp, lineHeight = (u.value * f * 1.15f).sp),
                            color = DraftArt.PLATE_TEXT, maxLines = 1, softWrap = false)
                    }
                }
                plate(DraftArt.ABBR_PLATE) {
                    Text(c.abbr, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (u.value * 9.6f * k).sp, lineHeight = (u.value * 10f * k).sp),
                        color = DraftArt.PLATE_TEXT, maxLines = 1, softWrap = false)
                }
            }
        }
        if (onClock in pennants.indices && onClock < spots.size) {
            val tx = (spots[onClock] - 34f).coerceIn(2f, SW - 70f)
            Box(Modifier.offset(x = u * tx, y = u * 158f).size(u * 68f, u * 13f).background(Color(0xE60A0A10)).border(u * 1f, Color(0xFFF5C518)),
                contentAlignment = Alignment.Center) {
                Text("ON THE CLOCK", style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (u.value * 7.5f).sp, lineHeight = (u.value * 8f).sp),
                    color = Color(0xFFF5C518), maxLines = 1, softWrap = false)
            }
        }
        put(art.league, 8f, 184f, 50f)
        put(art.league, 482f, 184f, 50f)
        // 4. The crowd and its rails, side to side (the podium hides the aisle).
        put(art.crowd, 0f, 330f, 540f)
        // 6 before 5: the commissioner stands behind the podium, so it's drawn over his feet.
        val bob = if (sin(clock * 2.1f) > 0.6f) -1f else 0f
        put(art.man, 159f, 242.5f + bob, 250f * 1.067f)
        // 5. The podium, with this year's draft on its plate.
        put(art.podium, 109.3f, 462.1f, 490f * 0.656f)
        Column(Modifier.offset(x = u * 240f, y = u * 481f).size(u * 76f, u * 50f), horizontalAlignment = Alignment.CenterHorizontally) {
            val plate = T.title.copy(fontSize = (u.value * 21).sp, lineHeight = (u.value * 22).sp)
            Text("DRAFT", style = plate, color = Color(0xFFEDEDED))
            Text("$year", style = plate, color = Color(0xFFEDEDED))
        }
        // 7. On top: the dialogue box.
        overlay()
    }
}

/** "first", "fifth", "21st". */
fun pickWord(n: Int): String = listOf("", "first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth", "ninth", "tenth").getOrNull(n) ?: ordinal(n)

/**
 * The commissioner's line: "With the first pick in the 1998 NBA Draft, the Los Angeles Clippers select..." for the
 * first pick (and the start of the second round), "With the fifth pick, the Toronto Raptors select..." after that;
 * then, once it's read, who.
 */
fun announce(year: Int, pick: Int, team: String, who: String?, roundTwoAt: Int = 31): String =
    "With the ${pickWord(pick)} pick" + (if (pick == 1 || pick == roundTwoAt) " in the $year NBA Draft" else "") + ", the $team select" + (if (who == null) "..." else " $who")

/**
 * The commissioner's box: a black box with a double white border, the speaker in yellow, the words typed out a
 * letter at a time (paced to fit [paceMs] when given), and a blinking arrow once he's done. A tap finishes the line.
 */
@Composable
fun DialogueBox(speaker: String, text: String, modifier: Modifier = Modifier, paceMs: Long = 0L, portrait: (@Composable () -> Unit)? = null, onDone: () -> Unit = {}) {
    var shown by remember(text) { mutableIntStateOf(0) }
    val stepMs = if (paceMs > 0) (paceMs * 0.7 / text.length.coerceAtLeast(1)).toLong().coerceIn(4L, 32L) else 30L
    LaunchedEffect(text) {
        while (shown < text.length) { delay(stepMs); shown++ }
        onDone()
    }
    val blink = rememberInfiniteTransition(label = "arrow")
    val a by blink.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
    val mono = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 22.sp)
    Box(
        modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A10))
            .border(3.dp, Color(0xFFE8E8E8), RoundedCornerShape(8.dp)).padding(3.dp)
            .border(1.5.dp, Color(0xFF6C6C78), RoundedCornerShape(6.dp))
            .clickable { shown = text.length }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row {
            // The club's badge beside the line, when he's naming one.
            if (portrait != null) Box(Modifier.padding(end = 12.dp, top = 1.dp)) { portrait() }
            Text(buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFFF5C518))) { append("$speaker: ") }
                withStyle(SpanStyle(color = Color.White)) { append(text.take(shown)) }
            }, style = mono, modifier = Modifier.weight(1f).padding(end = 12.dp, bottom = 6.dp))
        }
        if (shown >= text.length && a < 0.6f) Text("▼", style = mono.copy(fontSize = 14.sp), color = Color(0xFFF5C518), modifier = Modifier.align(Alignment.BottomEnd))
    }
}

/** [club]'s badge from the team pack, [size] square; a club with another name that year gets its abbreviation plate painted. */
@Composable
fun ClubBadge(club: DraftArt.Club, size: Dp = 44.dp) {
    val img = DraftArt.badge(club.slot) ?: return
    Box(Modifier.size(size)) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.None)
        if (club.renamed) {
            val k = size / 64f
            val r = DraftArt.BADGE_PLATE
            Box(Modifier.offset(x = k * r[0], y = k * r[1]).size(k * r[2], k * r[3]).background(DraftArt.PLATE), contentAlignment = Alignment.Center) {
                Text(club.abbr, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (k.value * 9f).sp, lineHeight = (k.value * 9.4f).sp),
                    color = DraftArt.PLATE_TEXT, maxLines = 1, softWrap = false)
            }
        }
    }
}

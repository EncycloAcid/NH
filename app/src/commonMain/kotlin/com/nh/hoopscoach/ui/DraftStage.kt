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
import androidx.compose.foundation.layout.heightIn
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
import com.nh.hoopscoach.game.ProLeague
import androidx.compose.ui.graphics.Paint
import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.geometry.CornerRadius

/**
 * The Draft Night art (assets/draft, from the user's retro draft packs): the hall's pieces, drawn back to front.
 * The center banner and the lectern's plate come with their year painted out; the year is written on them here.
 * The stage, the crowd at its tables and the team reps standing behind it are cut from the stage atlas.
 */
object DraftArt {
    private fun a(name: String) = Logos.asset("draft/$name.png")
    val banner get() = a("center_banner_blank")
    val lectern get() = a("podium_lectern")
    val commissioner get() = a("commissioner_full_body")
    val crowd get() = a("crowd_table")
    val reps get() = a("team_reps")
    val stage get() = a("stage_platform")
    val curtain get() = a("blue_curtain_tall")
    val league get() = a("league_vertical_banner")
    /** The plate's logos, from the stage atlas. */
    val nbaLogo get() = a("nba_logo")
    val sponsor get() = a("sprite_logo")

    /** The club's own logo (assets/nba/logos) for its pennant and badge; none for a club with another name that year. */
    fun teamLogo(c: Club): ImageBitmap? = if (c.renamed) null else ProLeague.logoKey(c.slot)?.let { Logos.asset("nba/logos/$it.webp") }

    /**
     * Pixel art made [n] times bigger pixel for pixel ([sw] x [sh] of it from ([sx], [sy])): drawn back down with smoothing,
     * its pixels stay crisp and come out even, where stretching it a little straight would double some pixels and not others.
     */
    fun sharp(img: ImageBitmap?, n: Int, sx: Int = 0, sy: Int = 0, sw: Int = img?.width ?: 0, sh: Int = img?.height ?: 0): ImageBitmap? {
        if (img == null || sw <= 0 || sh <= 0) return img
        val out = ImageBitmap(sw * n, sh * n)
        androidx.compose.ui.graphics.Canvas(out).drawImageRect(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset.Zero, IntSize(sw * n, sh * n),
            Paint().apply { filterQuality = FilterQuality.None })
        return out
    }

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

/** The truss of lights arcs a little lower in the middle; the pennants hang from it, the middle ones lower still. */
private fun truss(x: Float): Float { val t = (x - SW / 2) / (SW / 2); return 34f + 14f * (1 - t * t) }
private fun hang(x: Float): Float { val t = (x - SW / 2) / (SW / 2); return 38f + 26f * (1 - t * t) }

/** The lectern: [LECTERN_W] wide, from [LECTERN_Y] down [LECTERN_H] to the dais (the broadcast's proportions). */
private const val LECTERN_W = 226f
private const val LECTERN_Y = 408f
private const val LECTERN_H = 189f

/** Rows of stripes added to every pennant so they hang long (its rows 48 on are plain stripes across). */
private const val PENNANT_EXTRA = 24
/** Where the six pennants hang (their centers), three to a side; how wide; how long. */
private val SPOTS = listOf(30f, 90f, 150f, 390f, 450f, 510f)
private const val PENNANT_W = 68f
private val PENNANT_H = PENNANT_W / DraftArt.PW * (160 + PENNANT_EXTRA)

/** Part of [img] ([sx], [sy], [sw], [sh] in its pixels) into ([x], [y], [w], [h]) on the stage, [k] pixels to a stage unit, smoothed. */
private fun DrawScope.blit(img: ImageBitmap, sx: Int, sy: Int, sw: Int, sh: Int, x: Float, y: Float, w: Float, h: Float, k: Float, filter: ColorFilter? = null) {
    val x0 = (x * k).roundToInt(); val y0 = (y * k).roundToInt()
    drawImage(img, IntOffset(sx, sy), IntSize(sw, sh), IntOffset(x0, y0), IntSize(((x + w) * k).roundToInt() - x0, ((y + h) * k).roundToInt() - y0),
        colorFilter = filter, filterQuality = FilterQuality.High)
}

/** All of [img] into ([x], [y], [w], [h]). */
private fun DrawScope.blit(img: ImageBitmap, x: Float, y: Float, w: Float, h: Float, k: Float, filter: ColorFilter? = null) =
    blit(img, 0, 0, img.width, img.height, x, y, w, h, k, filter)

/** The cream disc for a club's logo: [r] across the middle of its medallion, the team color still ringing it. */
private val LOGO_DISC = Color(0xFFF4F1EA)

/** [logo] on a cream disc of radius [r] around ([cx], [cy]). */
private fun DrawScope.logoDisc(logo: ImageBitmap, cx: Float, cy: Float, r: Float, k: Float, filter: ColorFilter?) {
    drawCircle(LOGO_DISC, r * k, Offset(cx * k, cy * k), colorFilter = filter)
    val side = r * 1.62f
    blit(logo, cx - side / 2, cy - side / 2, side, side, k, filter)
}

/**
 * A team pennant, long: its top (rod and name plate), a stripe row stretched [PENNANT_EXTRA] rows, then the rest; the
 * club's [logo], when there is one, on the medallion (its ball at 48, 78 of the pennant's 96 x 160 pixels).
 */
private fun DrawScope.pennant(img: ImageBitmap, logo: ImageBitmap?, x: Float, y: Float, k: Float, filter: ColorFilter?) {
    val n = img.width / DraftArt.PW.toInt()
    val s = PENNANT_W / DraftArt.PW
    blit(img, 0, 0, img.width, 48 * n, x, y, PENNANT_W, 48 * s, k, filter)
    blit(img, 0, 48 * n, img.width, n, x, y + 48 * s, PENNANT_W, PENNANT_EXTRA * s, k, filter)
    blit(img, 0, 48 * n, img.width, img.height - 48 * n, x, y + (48 + PENNANT_EXTRA) * s, PENNANT_W, (img.height / n - 48) * s, k, filter)
    if (logo != null) logoDisc(logo, x + 48 * s, y + (78 + PENNANT_EXTRA) * s, 19 * s, k, filter)
}

/**
 * Draft Night on stage, like a 90s broadcast: the dark hall under its arc of lights, the pennants of [clubs] (the six
 * picking now and next, in order; [onClock] lit up under a spotlight) hung long from the truss, the draft banner with
 * [year], the league's banners, the lattice and the curtains, the stage house behind the commissioner, the team reps
 * standing at the back and the crowd at its draped tables, the commissioner with the envelope up, his microphone, the
 * wood stage with its red carpet, and the lectern with [year] on its plate. [overlay] goes on top (his dialogue box,
 * over the front of the stage).
 */
@Composable
fun DraftStage(year: Int, clubs: List<DraftArt.Club>, onClock: Int, modifier: Modifier = Modifier, overlay: @Composable BoxScope.() -> Unit = {}) {
    // The pixel art enlarged once (two or three times, enough for a phone's pixels), then drawn smoothed.
    val art = remember {
        object {
            val banner = DraftArt.banner; val league = DraftArt.league
            val lectern = DraftArt.sharp(DraftArt.lectern, 2); val man = DraftArt.sharp(DraftArt.commissioner, 3)
            val crowdLeft = DraftArt.crowd?.let { DraftArt.sharp(it, 3, 0, 0, 122, it.height) }
            val crowdRight = DraftArt.crowd?.let { DraftArt.sharp(it, 3, 150, 0, 122, it.height) }
            val reps = DraftArt.sharp(DraftArt.reps, 2); val stage = DraftArt.sharp(DraftArt.stage, 3)
            val curtain = DraftArt.sharp(DraftArt.curtain, 3)
            val nbaLogo = DraftArt.nbaLogo; val sponsor = DraftArt.sponsor
        }
    }
    val pennants = remember(clubs.map { it.slot }) { clubs.map { DraftArt.pennant(it.slot) ?: DraftArt.league } }
    val logos = remember(clubs.map { it.slot + it.abbr }) { clubs.map { DraftArt.teamLogo(it) } }
    val anim = rememberInfiniteTransition(label = "stage")
    val clock by anim.animateFloat(0f, 3600f, infiniteRepeatable(tween(3_600_000, easing = LinearEasing)), label = "clock")
    BoxWithConstraints(modifier.fillMaxWidth().aspectRatio(SW / SH).clip(RectangleShape)) {
        val u: Dp = maxWidth / SW
        /** [img] at ([x], [y]) on the stage's canvas, [w] wide, its own shape kept; crisp pixels. */
        @Composable
        fun put(img: ImageBitmap?, x: Float, y: Float, w: Float) {
            if (img == null) return
            val h = w * img.height / img.width
            Image(img, null, Modifier.offset(x = u * x, y = u * y).size(u * w, u * h), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        }
        val lights = listOf(Color.White, Color(0xFF6FA8FF), Color(0xFFFFE08A), Color(0xFFFF5A5A))
        // 1. The hall: the dark dome and its ribs, the back wall with its lattice, the red curtains at the sides,
        // the stage house (a beam of little lights over the curtain behind him), and the blue floor.
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            drawRect(Brush.verticalGradient(listOf(Color(0xFF030408), Color(0xFF0A1234)), endY = 560 * k))
            for (i in 0 until 3) {
                val r = 520f + 100 * i
                drawArc(Color(0xFF141A2C), 190f, 160f, false, Offset((270 - r) * k, (60 - r * 0.42f + 40 * i) * k), Size(2 * r * k, 2 * r * 0.42f * k), style = Stroke(2 * k))
            }
            drawRect(Color(0xFF080C20), Offset(0f, 150 * k), Size(size.width, 410 * k))
            for ((x0, x1) in listOf(96f to 186f, 354f to 444f)) {
                drawRect(Color(0xFF0B112C), Offset(x0 * k, 170 * k), Size((x1 - x0) * k, 250 * k))
                var gx = x0; while (gx <= x1) { drawRect(Color(0xFF161E40), Offset(gx * k, 170 * k), Size(k, 250 * k)); gx += 15f }
                var gy = 170f; while (gy <= 420f) { drawRect(Color(0xFF161E40), Offset(x0 * k, gy * k), Size((x1 - x0) * k, k)); gy += 17f }
            }
            art.curtain?.let { c ->
                // Red curtains: the blue one, dyed.
                val red = ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(0f, 0f, 0.62f, 0f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f)))
                blit(c, 0, 0, c.width, c.height, 66f, 160f, 34f, 280f, k, red)
                blit(c, 0, 0, c.width, c.height, 440f, 160f, 34f, 280f, k, red)
                blit(c, 0, 0, c.width, c.height, 184f, 262f, 172f, 230f, k)
            }
            drawRect(Color(0xFF16192A), Offset(180 * k, 244 * k), Size(180 * k, 20 * k))
            drawRect(Color(0xFF282E42), Offset(180 * k, 262 * k), Size(180 * k, 3 * k))
            for ((i, lx) in (192..351 step 16).withIndex()) {
                val c = listOf(Color(0xFFC8C8C8), Color(0xFF5A82D2), Color(0xFFD2B96E))[i % 3]
                drawCircle(c.copy(alpha = 0.7f + 0.3f * sin(clock * 3.1f + i)), 2 * k, Offset(lx * k, 253 * k))
            }
            drawRect(Color(0xFF121E64), Offset(0f, 548 * k), Size(size.width, size.height - 548 * k))
        }
        // 2. The draft banner, with this year's draft on its shield.
        put(art.banner, 188f, 84f, 164f)
        Box(Modifier.offset(x = u * 239.7f, y = u * 198f).size(u * 62.4f, u * 37.4f), contentAlignment = Alignment.Center) {
            Text("'${(year % 100).toString().padStart(2, '0')}", style = T.title.copy(fontSize = (u.value * 33.9f).sp, lineHeight = (u.value * 33.9f).sp,
                shadow = Shadow(Color(0xFF14102A), Offset(0f, u.value * 2.2f), 0f)), color = Color(0xFFF4F1EA))
        }
        // 3. The truss and its lights (twinkling); the pennants, three to a side in pick order: the club on the clock
        // under a spotlight, swaying, the ones up next a shade darker; the league's banners under them.
        val dim = ColorFilter.tint(Color(0x66000000), BlendMode.SrcAtop)
        val lit = onClock.takeIf { it in pennants.indices && it < SPOTS.size } ?: -1
        val sways = pennants.indices.map { i -> if (sin(clock * (if (i == lit) 2.4f else 1.2f) + i * 1.7f) > (if (i == lit) 0.3f else 0.85f)) 1f else 0f }
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            val arc = Path().apply { moveTo(0f, truss(0f) * k); var x = 4f; while (x <= SW) { lineTo(x * k, truss(x) * k); x += 4f } }
            drawPath(arc, Color(0xFF2A2F3B), style = Stroke(8 * k))
            drawPath(arc, Color(0xFF454C5E), style = Stroke(1.2f * k))
            for ((i, lx) in (22..539 step 62).withIndex()) {
                val c = lights[i % lights.size]
                val on = 0.75f + 0.25f * sin(clock * 2.6f + i * 1.3f)
                val ly = truss(lx.toFloat()) + 9
                drawCircle(c.copy(alpha = 0.16f * on), 12 * k, Offset(lx * k, ly * k))
                drawCircle(c.copy(alpha = on), 4 * k, Offset(lx * k, ly * k))
            }
            if (lit >= 0) {
                val cx = SPOTS[lit]; val top = truss(cx) + 9; val bottom = hang(cx) + PENNANT_H
                val glow = 0.16f + 0.05f * sin(clock * 3f)
                drawPath(Path().apply {
                    moveTo((cx - 5) * k, top * k); lineTo((cx + 5) * k, top * k); lineTo((cx + 44) * k, bottom * k); lineTo((cx - 44) * k, bottom * k); close()
                }, Brush.verticalGradient(listOf(Color(0xFFFFF3C4).copy(alpha = glow * 1.6f), Color(0xFFFFF3C4).copy(alpha = 0f)), startY = top * k, endY = bottom * k))
            }
            pennants.forEachIndexed { i, img ->
                if (i >= SPOTS.size || img == null) return@forEachIndexed
                val filter = if (i == lit || lit < 0) null else dim
                val y = hang(SPOTS[i]) + sways[i]
                if (img === art.league) blit(img, 0, 0, img.width, img.height, SPOTS[i] - 25, y, 50f, 50f * img.height / img.width, k, filter)
                else pennant(img, logos.getOrNull(i), SPOTS[i] - PENNANT_W / 2, y, k, filter)
            }
        }
        // A club with another name that year: its name and abbreviation painted on the plates.
        pennants.forEachIndexed { i, img ->
            val c = clubs.getOrNull(i) ?: return@forEachIndexed
            if (i >= SPOTS.size || img == null || img === art.league || !c.renamed) return@forEachIndexed
            val x = SPOTS[i] - PENNANT_W / 2
            val y = hang(SPOTS[i]) + sways[i]
            val k = PENNANT_W / DraftArt.PW
            val shade = i != lit && lit >= 0
            @Composable
            fun plate(r: FloatArray, dy: Float, content: @Composable () -> Unit) =
                Box(Modifier.offset(x = u * (x + r[0] * k), y = u * (y + (r[1] + dy) * k)).size(u * (r[2] * k), u * (r[3] * k)).background(DraftArt.PLATE)
                    .then(if (shade) Modifier.background(Color(0x66000000)) else Modifier), contentAlignment = Alignment.Center) { content() }
            val lines = listOf(c.city.uppercase(), c.nick.uppercase()).filter { it.isNotEmpty() }
            val f = minOf(6.6f, 58f / (lines.maxOf { it.length }.coerceAtLeast(1) * 0.66f)) * k
            plate(DraftArt.NAME_PLATE, 0f) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (l in lines) Text(l, style = TextStyle(fontWeight = FontWeight.Bold, fontSize = (u.value * f).sp, lineHeight = (u.value * f * 1.15f).sp),
                        color = DraftArt.PLATE_TEXT, maxLines = 1, softWrap = false)
                }
            }
            plate(DraftArt.ABBR_PLATE, PENNANT_EXTRA.toFloat()) {
                Text(c.abbr, style = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = (u.value * 9.6f * k).sp, lineHeight = (u.value * 10f * k).sp),
                    color = DraftArt.PLATE_TEXT, maxLines = 1, softWrap = false)
            }
        }
        if (lit >= 0) {
            val tx = (SPOTS[lit] - 34f).coerceIn(2f, SW - 70f)
            Box(Modifier.offset(x = u * tx, y = u * (hang(SPOTS[lit]) + PENNANT_H + 4)).size(u * 68f, u * 13f).background(Color(0xE60A0A10)).border(u * 1f, Color(0xFFF5C518)),
                contentAlignment = Alignment.Center) {
                Text("ON THE CLOCK", style = TextStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, fontSize = (u.value * 7.5f).sp, lineHeight = (u.value * 8f).sp),
                    color = Color(0xFFF5C518), maxLines = 1, softWrap = false)
            }
        }
        put(art.league, 8f, 196f, 56f)
        put(art.league, 476f, 196f, 56f)
        // 4. The team reps standing at the back, then the crowd at its draped tables, one section to a side.
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            art.reps?.let { r ->
                // Three-quarter size (it's enlarged two times).
                val w = r.width / 2 * 0.75f; val h = r.height / 2 * 0.75f
                blit(r, 84f, 312f, w, h, k)
                val x = SW - 84f - w
                withTransform({ scale(-1f, 1f, Offset((x + w / 2) * k, 0f)) }) { blit(r, x, 312f, w, h, k) }
            }
            val ch = (art.crowdLeft?.height ?: 0) / 3f
            art.crowdLeft?.let { blit(it, 0f, 556f - ch, 122f, ch, k) }
            art.crowdRight?.let { blit(it, SW - 122f, 556f - ch, 122f, ch, k) }
            if (ch > 0) {
                // Each section fades into the dark toward the aisle, where the stage lights don't reach.
                val top = (556f - ch) * k; val h = ch * k
                val dark = Color(0xFF080C20)
                drawRect(Brush.horizontalGradient(listOf(Color.Transparent, dark), startX = 100 * k, endX = 123 * k), Offset(100 * k, top), Size(23 * k, h))
                drawRect(Brush.horizontalGradient(listOf(dark, Color.Transparent), startX = (SW - 123) * k, endX = (SW - 100) * k), Offset((SW - 123) * k, top), Size(23 * k, h))
            }
        }
        // 5. The commissioner, the envelope up; his microphone; the stage; the lectern in front of him.
        val bob = if (sin(clock * 2.1f) > 0.6f) -1f else 0f
        put(art.man, 175.3f, 247.5f + bob, 218f)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / SW
            drawLine(Color(0xFF1C1C22), Offset(271 * k, 410 * k), Offset(270 * k, 372 * k), 2.2f * k)
            drawRoundRect(Color(0xFF2B2B33), Offset(266.5f * k, 361 * k), Size(7 * k, 12 * k), CornerRadius(3 * k))
            drawRect(Color(0xFF55555F), Offset(267.5f * k, 362.5f * k), Size(1.5f * k, 8 * k))
            art.stage?.let { blit(it, -40f, 556f, 620f, 620f * it.height / it.width, k) }
            // The lectern, tall and slim like the broadcast's: its top and plate as drawn, the plain wood panel under the
            // plate (vertical grain, its rows 150 to 212) drawn long, then its foot, standing on the dais.
            art.lectern?.let { l ->
                val n = l.width / 318
                val s = LECTERN_W / 318f
                val x = 270f - LECTERN_W / 2
                val top = 150 * s; val foot = (223 - 212) * s
                blit(l, 0, 0, l.width, 150 * n, x, LECTERN_Y, LECTERN_W, top, k)
                blit(l, 0, 150 * n, l.width, 62 * n, x, LECTERN_Y + top, LECTERN_W, LECTERN_H - top - foot, k)
                blit(l, 0, 212 * n, l.width, l.height - 212 * n, x, LECTERN_Y + LECTERN_H - foot, LECTERN_W, foot, k)
                // The plate, laid out like the broadcast's (in the lectern's pixels): the league's logo, then the sponsor's.
                fun lx(v: Float) = x + v * s
                fun ly(v: Float) = LECTERN_Y + v * s
                art.nbaLogo?.let { logo -> val h = 64f; val w = h * logo.width / logo.height; blit(logo, lx(86f), ly(25f), w * s, h * s, k) }
                art.sponsor?.let { logo -> val h = 45f; val w = h * logo.width / logo.height; blit(logo, lx(229f) - w * s, ly(97f), w * s, h * s, k) }
            }
        }
        // The plate's words: "DRAFT" and the year beside the league's logo, "Presented by" beside the sponsor's.
        val ls = LECTERN_W / 318f
        val px = 270f - LECTERN_W / 2
        Column(Modifier.offset(x = u * (px + 126 * ls), y = u * (LECTERN_Y + 22 * ls)).size(u * (106 * ls), u * (72 * ls)),
            horizontalAlignment = Alignment.CenterHorizontally) {
            val plate = T.title.copy(fontSize = (u.value * 21.6f).sp, lineHeight = (u.value * 23f).sp, shadow = Shadow(Color.Black, Offset(0f, u.value * 0.8f), 0f))
            Text("DRAFT", style = plate, color = Color(0xFFF2F2F2))
            Text("$year", style = plate, color = Color(0xFFF2F2F2))
        }
        Box(Modifier.offset(x = u * (px + 82 * ls), y = u * (LECTERN_Y + 104 * ls)).size(u * (92 * ls), u * (30 * ls)), contentAlignment = Alignment.CenterEnd) {
            Text("Presented by", style = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = (u.value * 8f).sp, lineHeight = (u.value * 9f).sp),
                color = Color(0xFFD9D9D9), maxLines = 1, softWrap = false)
        }
        // 6. The broadcast's dark edges, then on top: the dialogue box.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Transparent, Color(0x73000000)), center = Offset(size.width / 2, size.height * 0.45f), radius = size.height * 0.62f))
        }
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
        modifier.fillMaxWidth().heightIn(min = 100.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF0A0A10))
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

/** [club]'s badge from the team pack, [size] square, with the club's logo; a club with another name that year gets its abbreviation plate painted instead. */
@Composable
fun ClubBadge(club: DraftArt.Club, size: Dp = 44.dp) {
    val img = DraftArt.badge(club.slot) ?: return
    val logo = remember(club.slot, club.abbr) { DraftArt.teamLogo(club) }
    Box(Modifier.size(size)) {
        Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        // The club's logo over the badge's ball (its center at 31.5, 26.5 of the badge's 64 x 64 pixels).
        if (logo != null) Canvas(Modifier.fillMaxSize()) { val k = this.size.width / 64f; logoDisc(logo, 31.5f, 26.5f, 16.5f, k, null) }
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

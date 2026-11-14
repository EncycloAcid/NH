package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Challenges
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch

private val SWATCHES = listOf(
    0xFF0C2340, 0xFF003087, 0xFF1D428A, 0xFF4B9CD3, 0xFF00843D, 0xFF154734, 0xFF7BAFD4, 0xFF500000,
    0xFF9E1B32, 0xFFCC0000, 0xFFE87722, 0xFFFFC72C, 0xFFF1BE48, 0xFF4E2A84, 0xFF8F2BB0, 0xFF000000,
    0xFF53565A, 0xFFFFFFFF,
)

/** The uniform and court designer: the program's colors, the kit the players wear, and the court. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DesignerScreen(vm: GameViewModel, lg: League) {
    val t = lg.user
    val star = lg.rosterOf(t).maxByOrNull { it.ovr }
    var which by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Uniforms & court", t.school, onBack = { vm.back() })
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (star != null) PixelPortrait(star, t, 140.dp)
                CourtPreview(t, Modifier.weight(1f))
            }
            Section("Colors")
            Segmented(listOf("Main color", "Second color"), which, { which = it })
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val now = if (which == 0) t.primary else t.secondary
                for (c in SWATCHES) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(Color(c))
                            .border(if (c == now) 3.dp else 1.dp, if (c == now) P.ink else P.line, CircleShape)
                            .clickable {
                                vm.act { if (which == 0) it.user.primary = c else it.user.secondary = c }
                                Pixels.clear()
                            },
                    )
                }
            }
            Text("Your colors are the app's colors too.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
            Section("Uniform")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Wear the designed kit", style = T.bodyB, color = P.ink)
                        Text("Your two colors with ${t.abbr} across the chest, instead of the school's stock jersey art.", style = T.small, color = P.sub)
                    }
                    AppSwitch(t.customKit, { on -> vm.act { it.user.customKit = on }; SpriteSheets.customKits = if (on) SpriteSheets.customKits + t.abbr.uppercase() else SpriteSheets.customKits - t.abbr.uppercase(); Pixels.clear() })
                }
            }
            Section("Court")
            if (!t.customKit && CourtArt.image(t) != null)
                Text("${t.school} plays on its own court. Switch on the designed kit above to design one in your colors.", style = T.small, color = P.sub,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp))
            else {
                Chips(listOf("Team color paint", "Second color paint", "Wood paint"), t.courtPaint, { i -> vm.act { it.user.courtPaint = i } })
                Spacer(Modifier.height(8.dp))
                Chips(CourtLook.STYLES, CourtLook.style(t), { i -> vm.act { it.user.courtStyle = i } })
                Spacer(Modifier.height(8.dp))
                Chips(CourtLook.FLOORS, CourtLook.floor(t), { i -> vm.act { it.user.courtFloor = i } })
                Spacer(Modifier.height(8.dp))
                Chips(CourtLook.APRONS, CourtLook.apron(t), { i -> vm.act { it.user.courtApron = i } })
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The program's court: its own art (assets/courts), or, in a designed kit or without art, one drawn in its colors. */
@Composable
fun CourtPreview(t: Team, modifier: Modifier = Modifier) {
    val art = if (t.customKit) null else CourtArt.image(t)
    if (art != null) {
        androidx.compose.foundation.Image(art, "${t.school} court", modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        return
    }
    DrawnCourt(t, modifier)
}

/** Court art from assets/courts, by the same key as the jersey art. Decoded once and kept. */
object CourtArt {
    private val cache = HashMap<String, androidx.compose.ui.graphics.ImageBitmap?>()

    fun path(t: Team): String? {
        val key = SpriteSheets.artKey(t.abbr, t.slot) ?: return null
        return if (com.nh.hoopscoach.assetBytes("courts/nba/$key.png") != null) "courts/nba/$key.png" else "courts/college/$key.png"
    }

    @Synchronized fun image(t: Team): androidx.compose.ui.graphics.ImageBitmap? {
        val p = path(t) ?: return null
        return cache.getOrPut(p) { runCatching { com.nh.hoopscoach.assetBytes(p)?.let { com.nh.hoopscoach.decodeImageBitmap(it) } }.getOrNull() }
    }
}

/** The home court across the top of a live game: the center-court strip of the art, or the drawn court. */
@Composable
fun CourtBanner(t: Team, modifier: Modifier = Modifier) {
    val art = if (t.customKit) null else CourtArt.image(t)
    Box(modifier.clip(RoundedCornerShape(14.dp))) {
        if (art != null) androidx.compose.foundation.Image(art, "${t.school} court", Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, filterQuality = androidx.compose.ui.graphics.FilterQuality.None)
        else DrawnCourt(t, Modifier.fillMaxSize())
    }
}

/** How a school's drawn court looks: its own choices, or ones picked from its name so each school differs (and keeps its look). */
object CourtLook {
    private fun seed(t: Team) = (t.abbr.hashCode() * 31 + t.school.hashCode()) and 0x7fffffff
    fun floor(t: Team) = if (t.courtFloor >= 0) t.courtFloor else seed(t) % 3
    fun style(t: Team) = if (t.courtStyle >= 0) t.courtStyle else (seed(t) / 3) % 3
    fun apron(t: Team) = if (t.courtApron >= 0) t.courtApron else (seed(t) / 9) % 3

    val FLOORS = listOf("Maple", "Honey", "Dark wood")
    val STYLES = listOf("Solid lane", "Outlined lane", "Two-tone lane")
    val APRONS = listOf("Main color border", "Second color border", "Dark border")
}

/**
 * A full court drawn for a school: planked wood, a border in its colors with the mascot on it, both lanes,
 * the arcs and rims, and the abbreviation at center court.
 */
@Composable
fun DrawnCourt(t: Team, modifier: Modifier = Modifier) {
    val primary = Color(t.primary)
    val second = Color(t.secondary)
    val (woodA, woodB) = when (CourtLook.floor(t)) {
        1 -> Color(0xFFD9A35F) to Color(0xFFC98C45)
        2 -> Color(0xFFA8744A) to Color(0xFF8E5F3A)
        else -> Color(0xFFE6C08A) to Color(0xFFD7AB70)
    }
    val apron = when (CourtLook.apron(t)) { 1 -> second; 2 -> Color(0xFF1A1D24); else -> primary }
    val paint = when (t.courtPaint) { 1 -> second; 2 -> lerp(woodB, Color.Black, 0.15f); else -> primary }
    val accent = if (paint == second) primary else second
    val style = CourtLook.style(t)
    val center = if (contrast(primary, woodA) >= 1.6f) primary else second
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp))) {
        val hPx = with(androidx.compose.ui.platform.LocalDensity.current) { maxHeight.toPx() }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(apron)
            // The floor, inset in the border, in planks.
            val fx = w * 0.05f
            val fy = h * 0.075f
            val fw = w - fx * 2
            val fh = h - fy * 2
            val rows = 18
            val rh = fh / rows
            for (r in 0 until rows) {
                drawRect(if (r % 2 == 0) woodA else lerp(woodA, woodB, 0.6f), Offset(fx, fy + r * rh), Size(fw, rh + 0.5f))
                var x = fx + ((r * 37) % 100) / 100f * fw * 0.14f
                while (x < fx + fw) {
                    drawLine(woodB, Offset(x, fy + r * rh), Offset(x, fy + (r + 1) * rh), strokeWidth = (h * 0.004f).coerceAtLeast(1f))
                    x += fw * 0.14f
                }
            }
            val lw = (h * 0.009f).coerceAtLeast(1.5f)
            val line = Color.White
            val st = Stroke(lw)
            drawRect(line, Offset(fx, fy), Size(fw, fh), style = st)
            drawLine(line, Offset(w / 2, fy), Offset(w / 2, fy + fh), strokeWidth = lw)
            // Both ends, mirrored.
            val laneW = fw * 0.19f
            val laneH = fh * 0.32f
            val cy = h / 2
            for (end in 0..1) {
                val left = end == 0
                val laneX = if (left) fx else fx + fw - laneW
                val lane = Offset(laneX, cy - laneH / 2)
                when (style) {
                    1 -> {}
                    2 -> { drawRect(paint, lane, Size(laneW, laneH)); drawRect(accent, Offset(laneX, cy - laneH / 6), Size(laneW, laneH / 3)) }
                    else -> drawRect(paint, lane, Size(laneW, laneH))
                }
                drawRect(line, lane, Size(laneW, laneH), style = st)
                // Free-throw circle at the top of the key.
                val ftX = if (left) fx + laneW else fx + fw - laneW
                drawCircle(line, laneH / 2, Offset(ftX, cy), style = st)
                // Three-point line: corners, then the arc.
                val rimX = if (left) fx + fw * 0.055f else fx + fw * 0.945f
                val arcR = fh * 0.44f
                // The straight corner lines run from the baseline to exactly where the arc begins.
                val half = 68f
                val rad = Math.toRadians(half.toDouble())
                val ey = (arcR * kotlin.math.sin(rad)).toFloat()
                val ex = (arcR * kotlin.math.cos(rad)).toFloat()
                val from = if (left) fx else rimX - ex
                val to = if (left) rimX + ex else fx + fw
                drawLine(line, Offset(from, cy - ey), Offset(to, cy - ey), strokeWidth = lw)
                drawLine(line, Offset(from, cy + ey), Offset(to, cy + ey), strokeWidth = lw)
                drawArc(line, if (left) -half else 180f - half, half * 2, false, Offset(rimX - arcR, cy - arcR), Size(arcR * 2, arcR * 2), style = st)
                // Backboard and rim.
                val bx = if (left) fx + fw * 0.035f else fx + fw * 0.965f
                drawLine(line, Offset(bx, cy - fh * 0.05f), Offset(bx, cy + fh * 0.05f), strokeWidth = lw * 1.4f)
                drawCircle(Color(0xFFE8622A), fh * 0.022f, Offset(rimX, cy), style = Stroke(lw * 1.2f))
            }
            // Center court.
            val r = fh * 0.16f
            drawCircle(center, r, Offset(w / 2, cy))
            drawCircle(line, r, Offset(w / 2, cy), style = st)
            drawCircle(accent.copy(alpha = 0.9f), r * 0.82f, Offset(w / 2, cy), style = Stroke(lw * 0.8f))
        }
        val abbrSize = with(androidx.compose.ui.platform.LocalDensity.current) { (hPx * 0.12f).toSp() }
        val sideSize = with(androidx.compose.ui.platform.LocalDensity.current) { (hPx * 0.045f).toSp() }
        Text(t.abbr, style = T.title.copy(fontSize = abbrSize, lineHeight = abbrSize), color = onColor(center), modifier = Modifier.align(Alignment.Center))
        val onApron = onColor(apron)
        Text(t.mascot.uppercase(), style = T.label.copy(fontSize = sideSize, lineHeight = sideSize, letterSpacing = 2.sp), color = onApron,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = with(androidx.compose.ui.platform.LocalDensity.current) { (hPx * 0.012f).toDp() }))
        Text(t.mascot.uppercase(), style = T.label.copy(fontSize = sideSize, lineHeight = sideSize, letterSpacing = 2.sp), color = onApron,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = with(androidx.compose.ui.platform.LocalDensity.current) { (hPx * 0.012f).toDp() }))
    }
}

/** A season as a picture to save or post: the record, how it ended, the best players. */
@Composable
fun SeasonCardScreen(vm: GameViewModel, lg: League) {
    val t = lg.user
    val layer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    var png by remember { mutableStateOf<ByteArray?>(null) }
    val save = com.nh.hoopscoach.rememberSaveExporter(
        suggestedName = "${t.abbr.lowercase()}-${lg.season}.png", getBytes = { png },
        onResult = { ok -> vm.toast(if (ok) "Season card saved" else "Couldn't save the picture") }, mime = "image/png",
    )
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Season card", "${lg.season} · ${t.school}", onBack = { vm.back() }) }
        item {
            Box(
                Modifier.padding(horizontal = Gutter).fillMaxWidth().drawWithContent {
                    layer.record { this@drawWithContent.drawContent() }
                    drawLayer(layer)
                },
            ) { SeasonCard(lg) }
            Spacer(Modifier.height(14.dp))
            PrimaryButton("Save picture", Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                scope.launch {
                    png = com.nh.hoopscoach.encodePng(layer.toImageBitmap())
                    if (png == null) vm.toast("Couldn't make the picture") else save()
                }
            }
            Text("Saves a PNG you can post or send.", style = T.small, color = P.faint, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(8.dp))
        }
    }
}

@Composable
private fun SeasonCard(lg: League) {
    val t = lg.user
    val c = lg.userCoach
    val base = teamCard(t)
    val fg = onColor(base)
    val accent = Color(t.secondary).takeIf { contrast(it, base) >= 2.2f } ?: P.gold
    val last = t.history.lastOrNull { it.season == lg.season }
    val result = last?.result ?: when (lg.phase) {
        com.nh.hoopscoach.game.Phase.REGULAR -> "Season in progress"
        else -> "Postseason"
    }
    val top = lg.rosterOf(t).filter { it.stats.gp > 0 }.sortedByDescending { it.stats.ppg }.take(3)
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.6f)))).padding(22.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HOOPS DYNASTY", style = T.label, color = fg.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            Text("${lg.season - 1}-${(lg.season % 100).toString().padStart(2, '0')}", style = T.label, color = fg.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(t, 64.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(t.school.uppercase(), style = T.title, color = fg, maxLines = 2)
                Text("Coach ${c.name}", style = T.small, color = fg.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(t.record, style = T.hero.copy(fontSize = 64.sp, lineHeight = 64.sp), color = fg)
        Text("${t.confRecord} ${lg.conf(t.confId).abbr}" + (if (t.ap > 0) " · AP No. ${t.ap}" else ""), style = T.bodyB, color = fg.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.background(accent, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
            Text(result.uppercase(), style = T.label, color = onColor(accent))
        }
        if (top.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("LEADING THE WAY", style = T.label, color = fg.copy(alpha = 0.75f))
            for (p in top) Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = T.bodyB, color = fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("%.1f pts · %.1f reb · %.1f ast".format(p.stats.ppg, p.stats.rpg, p.stats.apg), style = T.small, color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

/** Challenge mode: the challenge this career is playing, and every medal won, across careers. */
@Composable
fun ChallengesScreen(vm: GameViewModel, lg: League) {
    val run = lg.challenge
    val medals = remember { Challenges.medals() }
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Challenges", "${medals.size} medal${if (medals.size == 1) "" else "s"} won", onBack = { vm.back() }) }
        item {
            Section("This career")
            Panel {
                if (run == null) Text("This career isn't a challenge. Pick one when you start a new career.", style = T.body, color = P.sub)
                else {
                    val d = Challenges.def(run.id)
                    Text(d?.name ?: run.id, style = T.head, color = P.ink)
                    Text(d?.goal ?: "", style = T.small, color = P.sub)
                    Spacer(Modifier.height(8.dp))
                    Text(when (run.result) {
                        "won" -> "Done: ${run.medal.uppercase()} medal."
                        "missed" -> "Time ran out."
                        "forfeited" -> "Forfeited when you left ${lg.teams.getOrNull(run.teamId)?.school}."
                        else -> "Season ${lg.season - run.start + 1} of ${run.deadline - run.start + 1}."
                    }, style = T.bodyB, color = when (run.result) { "won" -> P.gold; "" -> P.accent; else -> P.bad })
                }
            }
        }
        item {
            Section("Medals")
            if (medals.isEmpty()) Panel { Empty("No medals yet.") }
            else GroupList(medals.reversed()) { m ->
                ListRow {
                    Text(when (m.medal) { "gold" -> "🥇"; "silver" -> "🥈"; else -> "🥉" }, style = T.head)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Challenges.def(m.id)?.name ?: m.id, style = T.bodyB, color = P.ink)
                        Text("${m.school} · ${m.season}", style = T.small, color = P.sub)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

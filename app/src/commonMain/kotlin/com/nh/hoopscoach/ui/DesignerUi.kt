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
            Chips(listOf("Team color paint", "Second color paint", "Natural wood"), t.courtPaint, { i -> vm.act { it.user.courtPaint = i } })
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** A half court in the program's colors, its abbreviation at center court. */
@Composable
fun CourtPreview(t: Team, modifier: Modifier = Modifier) {
    val primary = Color(t.primary)
    val second = Color(t.secondary)
    val paint = when (t.courtPaint) { 0 -> primary; 1 -> second; else -> Color(0xFFC8955A) }
    val label = onColor(primary)
    Box(modifier.aspectRatio(1.1f).clip(RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(Brush.verticalGradient(listOf(Color(0xFFE2B47C), Color(0xFFC8955A))))
            val w = size.width
            val h = size.height
            val line = Color.White.copy(alpha = 0.85f)
            val stroke = Stroke(w * 0.012f)
            // The lane at the bottom, the arc, the center circle at the top.
            drawRect(paint, Offset(w * 0.34f, h * 0.55f), Size(w * 0.32f, h * 0.45f))
            drawRect(line, Offset(w * 0.34f, h * 0.55f), Size(w * 0.32f, h * 0.45f), style = stroke)
            drawArc(line, 180f, 180f, false, Offset(w * 0.08f, h * 0.38f), Size(w * 0.84f, h * 1.1f), style = stroke)
            drawCircle(primary, w * 0.17f, Offset(w / 2, 0f))
            drawCircle(line, w * 0.17f, Offset(w / 2, 0f), style = stroke)
            drawRect(line, Offset.Zero, size, style = stroke)
        }
        Text(t.abbr, style = T.title.copy(fontSize = 22.sp), color = label, modifier = Modifier.align(Alignment.TopCenter).padding(top = 4.dp))
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

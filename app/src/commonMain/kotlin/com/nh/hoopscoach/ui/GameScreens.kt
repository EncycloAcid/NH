package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.animateFloat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import com.nh.hoopscoach.game.Ollama
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Defense
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offense
import com.nh.hoopscoach.game.PlayEvent
import com.nh.hoopscoach.game.PlayerLine
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.delay
import kotlin.math.exp
import kotlin.math.sqrt

private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

/** Seconds elapsed in the game for a period/clock pair: [regs] regulation periods of [len] seconds, then 5-minute overtimes. */
private fun elapsed(period: Int, clock: Int, len: Int = 1200, regs: Int = 2) =
    if (period <= regs) (period - 1) * len + (len - clock) else regs * len + (period - regs - 1) * 300 + (300 - clock)

/** Home win probability given the score margin, time left and the pregame edge. */
private fun winProb(margin: Int, secLeft: Int, pregameEdge: Double, game: Int = 2400): Double {
    val f = (secLeft / game.toDouble()).coerceIn(0.0, 1.0)
    if (secLeft <= 0) return if (margin > 0) 1.0 else if (margin < 0) 0.0 else 0.5
    val z = (margin + pregameEdge * f) / (11.0 * sqrt(f) + 0.6)
    return 1.0 / (1.0 + exp(-1.7 * z))
}

@Composable
fun LiveScreen(vm: GameViewModel, lg: League) {
    val lv = vm.live ?: run { Column { BackBar("Game", onBack = { vm.back() }) }; return }
    val e = lv.engine
    @Suppress("UNUSED_VARIABLE") val tick = lv.tick
    LaunchedEffect(lv) {
        while (!e.finished) {
            // In player mode the game stops for big moments until the user picks a play.
            // Spotlight jumps from one of his plays to the next, so a game takes about a minute.
            val spot = lg.road?.spotlight == true && lg.playerMode
            if (lv.speed > 0 && lv.moment == null && lv.call == null) {
                val m = e.pendingMoment()
                val c = if (m == null) e.pendingCall() else null
                if (m != null) lv.moment = m else if (c != null) lv.call = c else if (spot) e.stepToHero() else e.step()
                lv.tick++
            }
            delay(
                if (spot) when (lv.speed) { 0 -> 150L; 1 -> 4500L; 2 -> 3000L; else -> 1300L }
                else when (lv.speed) { 0 -> 150L; 1 -> 650L; 2 -> 260L; else -> 70L },
            )
        }
        vm.completeLive()
    }
    var view by remember { mutableIntStateOf(0) }
    val home = e.teams[0]
    val away = e.teams[1]
    val userHome = home.id == lg.userTeamId
    val edge = (home.elo - away.elo + Rankings.homeElo(lg, e.game)) / 28.0
    Column(Modifier.fillMaxSize()) {
        BackBar(e.game.label.ifEmpty { gameKind(lg, e.game) }, if (e.finished) "Final" else "${e.periodName} · ${clock(e.clock.toInt().coerceAtLeast(0))}", onBack = { vm.back() })
        // A big moment or a coaching call is the whole screen until it's answered: the chart and the
        // play-by-play step aside and the rest scrolls, so the card and its buttons always fit.
        val focus = !e.finished && (lv.moment != null || lv.call != null)
        Column(if (focus) Modifier.weight(1f).verticalScroll(androidx.compose.foundation.rememberScrollState()) else Modifier) {
        Scoreboard(away, home, e.score[1], e.score[0], if (e.finished) "FINAL" else clock(e.clock.toInt().coerceAtLeast(0)), if (e.finished) "" else e.periodLabel, lg.userTeamId)
        Spacer(Modifier.height(8.dp))
        CrowdLine(lg, e.game)
        if (e.bigNight.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Tag(e.bigNight.uppercase(), P.bad, filled = true)
                Spacer(Modifier.width(8.dp))
                Text("Up to five big moments tonight", style = T.small, color = P.sub)
            }
        }
        if (lg.playerMode && com.nh.hoopscoach.game.Rival.isDuel(lg, e.game)) {
            val rv = com.nh.hoopscoach.game.Rival.active(lg)
            val me = lg.playerOrNull(lg.heroId)
            if (rv != null && me != null) {
                fun pts(id: Int) = (e.lines[0][id] ?: e.lines[1][id])?.pts ?: 0
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag("RIVAL", P.bad)
                    Spacer(Modifier.width(8.dp))
                    Text("You ${pts(me.id)} pts · ${rv.last} ${pts(rv.id)} pts", style = T.bodyB, color = P.ink)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (!focus) Panel(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
            val pts = remember(lv.tick) {
                val half = e.half.toInt()
                val len = e.periodLen.toInt()
                val list = mutableListOf(0f to winProb(0, 2 * half, edge, 2 * half).toFloat())
                for (ev in e.events) {
                    val at = elapsed(ev.period, ev.clock, len, e.regPeriods)
                    list += at.toFloat() to winProb(ev.hs - ev.aws, (2 * half - at).coerceAtLeast(0), edge, 2 * half).toFloat()
                }
                list
            }
            val now = pts.last().second
            val mine = if (userHome) now else 1 - now
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${(mine * 100).toInt()}%", style = T.num, color = P.ink)
                Text("  ${lg.user.abbr} win probability", style = T.small, color = P.sub, modifier = Modifier.padding(bottom = 3.dp))
            }
            Spacer(Modifier.height(6.dp))
            WinChart(pts, if (userHome) teamInk(away) else teamInk(home), P.accent, userHome, Modifier.fillMaxWidth().height(96.dp), e.half.toFloat())
        }
        Spacer(Modifier.height(10.dp))
        lv.moment?.let { m -> MomentCard(lg, lv, m); Spacer(Modifier.height(8.dp)) }
        lv.call?.let { c -> CallCard(lg, lv, c); Spacer(Modifier.height(8.dp)) }
        lv.momentResult?.takeIf { lv.moment == null && lv.call == null }?.let {
            Panel(Modifier.popIn(it), color = P.accentSoft, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Text("★ $it", style = T.bodyB, color = P.ink) }
            Spacer(Modifier.height(8.dp))
        }
        if (!lv.done) {
            if (lv.moment == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Segmented(listOf(if (lv.speed == 0) "▶" else "❚❚", "1×", "2×", "4×"), lv.speed, { lv.speed = if (it == 0 && lv.speed == 0) 2 else it }, Modifier.weight(1f).padding(horizontal = 0.dp))
                    if (e.coachSide >= 0) SmallButton("Timeout · ${e.timeoutsLeft}", enabled = e.timeoutsLeft > 0) { if (e.callTimeout()) { lv.momentResult = e.lastCall; lv.tick++ } }
                    SmallButton("Finish", primary = true) { vm.completeLive() }
                }
                Spacer(Modifier.height(8.dp))
                val t = e.teams[if (userHome) 0 else 1]
                if (lg.playerMode) {
                    val spot = lg.road?.spotlight == true
                    Chips(listOf("Spotlight", "Every play"), if (spot) 0 else 1, { i -> vm.act { it.road?.spotlight = i == 0 }; lv.tick++ })
                    Spacer(Modifier.height(6.dp))
                    Text(
                        (if (spot) "Spotlight: the game jumps to your plays. " else "") +
                            "Coach ${lg.coachOf(t).last} calls it: ${t.plan.offense.label} offense, ${t.plan.defense.label} defense.",
                        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp),
                    )
                } else Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Picker("Offense", t.plan.offense.label, Offense.entries.map { it.label to it.blurb }, Modifier.weight(1f)) { i -> t.plan.offense = Offense.entries[i]; lv.tick++ }
                    Picker("Defense", t.plan.defense.label, Defense.entries.map { it.label to it.blurb }, Modifier.weight(1f)) { i -> t.plan.defense = Defense.entries[i]; lv.tick++ }
                }
            }
        } else {
            val won = e.score[if (userHome) 0 else 1] > e.score[if (userHome) 1 else 0]
            PrimaryButton(if (won) "Win · Continue" else "Continue", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { vm.closeLive() }
        }
        Spacer(Modifier.height(if (focus) 24.dp else 10.dp))
        }
        if (!focus) {
            Segmented(listOf("Plays", away.abbr, home.abbr), view, { view = it })
            Spacer(Modifier.height(6.dp))
            if (view == 0) {
                val events = e.events.asReversed()
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    // Each play keeps its key, so a new one slides in at the top and the rest move down.
                    items(events.size, key = { events.size - it }) { i ->
                        Box(Modifier.animateItem(fadeInSpec = tween(320), placementSpec = androidx.compose.animation.core.spring(dampingRatio = 0.85f, stiffness = 420f, visibilityThreshold = androidx.compose.ui.unit.IntOffset.VisibilityThreshold))) {
                            PlayRow(events[i], home, away)
                        }
                    }
                }
            } else {
                val box = e.boxScore()
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) { item { BoxTable(vm, lg, if (view == 1) away else home, if (view == 1) box.away else box.home) } }
            }
        }
    }
}

@Composable
private fun PlayRow(ev: PlayEvent, home: Team, away: Team) {
    val color = when (ev.side) { 0 -> teamInk(home); 1 -> teamInk(away); else -> P.line }
    Row(Modifier.fillMaxWidth().then(if (ev.hero) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = Gutter + 4.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(clock(ev.clock), style = T.small.copy(fontSize = 12.sp), color = P.faint, modifier = Modifier.width(44.dp))
        Box(Modifier.size(width = 3.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(10.dp))
        Text(ev.text, style = if (ev.points > 0) T.bodyB else T.body, color = if (ev.points > 0) P.ink else P.sub, modifier = Modifier.weight(1f))
        if (ev.points > 0) { Spacer(Modifier.width(8.dp)); Text("${ev.aws}-${ev.hs}", style = T.numSm.copy(fontSize = 16.sp), color = P.ink) }
    }
}

@Composable
private fun WinChart(points: List<Pair<Float, Float>>, oppColor: Color, myColor: Color, userHome: Boolean, modifier: Modifier, half: Float = 1200f) {
    val line = P.line
    val grid = P.faint
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val total = maxOf(2 * half, points.lastOrNull()?.first ?: 2 * half)
        drawLine(line, Offset(0f, h / 2), Offset(w, h / 2), strokeWidth = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        drawLine(grid.copy(alpha = 0.4f), Offset(w * half / total, 0f), Offset(w * half / total, h), strokeWidth = 2f)
        if (points.size < 2) return@Canvas
        // y: 0 = my team certain to win, h = opponent certain to win
        fun y(p: Float) = (if (userHome) 1 - p else p) * h
        val path = Path()
        points.forEachIndexed { i, (x, p) ->
            val px = w * x / total
            if (i == 0) path.moveTo(px, y(p)) else path.lineTo(px, y(p))
        }
        val last = points.last()
        val myFill = Path().apply { addPath(path); lineTo(w * last.first / total, h / 2); lineTo(0f, h / 2); close() }
        drawPath(myFill, myColor.copy(alpha = 0.12f))
        drawPath(path, myColor, style = Stroke(width = 5f, cap = StrokeCap.Round))
        drawCircle(myColor, 8f, Offset(w * last.first / total, y(last.second)))
        @Suppress("UNUSED_VARIABLE") val o = oppColor
    }
}

@Composable
fun Scoreboard(away: Team, home: Team, as_: Int, hs: Int, center: String, sub: String, user: Int) {
    Row(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(P.surface)) {
        ScoreHalf(away, as_, as_ > hs, center == "FINAL", Modifier.weight(1f), user)
        Column(Modifier.width(84.dp).padding(vertical = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(center, style = T.h2, color = if (center == "FINAL") P.accent else P.ink)
            if (sub.isNotEmpty()) Text(sub.uppercase(), style = T.label, color = P.sub)
        }
        ScoreHalf(home, hs, hs > as_, center == "FINAL", Modifier.weight(1f), user)
    }
}

@Composable
private fun ScoreHalf(t: Team, score: Int, ahead: Boolean, final: Boolean, modifier: Modifier, user: Int) {
    Column(modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(t, 52.dp)
        Spacer(Modifier.height(6.dp))
        Text(t.abbr, style = T.label, color = if (t.id == user) P.accent else P.sub)
        // Once it's final, the losing score steps back.
        // The score rolls up and gives a little bounce on every basket.
        val c by androidx.compose.animation.animateColorAsState(if (final && !ahead) P.sub else P.ink, tween(500), label = "score")
        RollingText("$score", T.hero.copy(fontSize = 48.sp, lineHeight = 48.sp), c, Modifier.pulseOn(score, 0.1f))
    }
}

@Composable
fun BoxTable(vm: GameViewModel, lg: League, t: Team, lines: List<PlayerLine>) {
    val cols = listOf("MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG", "3PT", "FT", "PF")
    val widths = listOf(38, 40, 40, 40, 38, 38, 34, 52, 48, 48, 32)
    fun values(s: StatLine) = listOf(
        "${kotlin.math.round(s.sec / 60.0).toInt()}", "${s.pts}", "${s.reb}", "${s.ast}", "${s.stl}", "${s.blk}", "${s.tov}",
        "${s.fgm}-${s.fga}", "${s.tpm}-${s.tpa}", "${s.ftm}-${s.fta}", "${s.pf}",
    )
    val total = StatLine()
    lines.forEach { total.add(it.s) }
    Panel(padding = PaddingValues(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(144.dp).padding(start = 14.dp)) {
                Text("PLAYER", style = T.label, color = P.sub, modifier = Modifier.height(28.dp).padding(top = 8.dp))
                lines.forEach { l ->
                    Row(Modifier.height(38.dp).clickable { vm.openPlayer(l.pid) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, style = if (l.starter) T.bodyB.copy(fontSize = 14.sp) else T.body.copy(fontSize = 14.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(" ${l.pos}", style = T.small.copy(fontSize = 10.sp), color = P.faint)
                    }
                }
                Text("TEAM", style = T.label, color = P.ink, modifier = Modifier.height(38.dp).padding(top = 12.dp))
            }
            val hs = rememberScrollState()
            val surface = P.surface
            Box(Modifier.weight(1f)) {
            Column(Modifier.horizontalScroll(hs).padding(end = 12.dp)) {
                Row(Modifier.height(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    cols.forEachIndexed { i, c -> Text(c, style = T.label, color = P.sub, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End) }
                }
                lines.forEach { l ->
                    Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                        values(l.s).forEachIndexed { i, v ->
                            Text(v, style = if (i == 1) T.numSm.copy(fontSize = 16.sp) else T.small, color = if (i == 1) P.ink else P.sub, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End)
                        }
                    }
                }
                Row(Modifier.height(38.dp), verticalAlignment = Alignment.CenterVertically) {
                    values(total).forEachIndexed { i, v -> Text(if (i == 0) "" else v, style = T.bodyB.copy(fontSize = 13.sp), color = P.ink, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End) }
                }
            }
            // More stats to the right: a fade at the edge until you've scrolled to the end.
            if (hs.value < hs.maxValue) Box(Modifier.align(Alignment.CenterEnd).width(28.dp).fillMaxHeight()
                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(surface.copy(alpha = 0f), surface))))
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = t.id + lg.season
}

@Composable
fun BoxScoreScreen(vm: GameViewModel, lg: League, gameId: Int) {
    val g: Game = lg.games.firstOrNull { it.id == gameId } ?: run { Column { BackBar("Box score", onBack = { vm.back() }); Empty("This game is no longer available.") }; return }
    val home = lg.team(g.home)
    val away = lg.team(g.away)
    var side by remember(gameId) { mutableIntStateOf(if (g.away == lg.userTeamId) 0 else 1) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Box score", g.label.ifEmpty { "${gameKind(lg, g)}${if (g.day <= Schedule.REG_DAYS) " · game day ${g.day}" else ""}" }, onBack = { vm.back() })
            Scoreboard(away, home, g.aws.coerceAtLeast(0), g.hs.coerceAtLeast(0), if (g.played) "FINAL" else "—", if (g.ot > 0) "${if (g.ot > 1) "${g.ot}" else ""}OT" else "", lg.userTeamId)
            Spacer(Modifier.height(8.dp))
            CrowdLine(lg, g)
            if (g.played && lg.rivalry(g) != null) { Spacer(Modifier.height(10.dp)); NewspaperBanner(vm, lg, g, onClick = null) }
            if (g.played) PlayerOfTheGame(vm, lg, g)
            if (g.played && lg.ollama.enabled && lg.ollama.gameRecaps) {
                Spacer(Modifier.height(10.dp))
                OllamaGameReportCard(vm, lg, g)
            }
            if (g.played && g.involves(lg.userTeamId) && (lg.ollama.enabled && lg.ollama.playerInterviews || g.interviews.isNotEmpty())) {
                Spacer(Modifier.height(10.dp))
                PlayerInterviewCard(vm, lg, g)
            }
            val b = g.box
            if (b != null) {
                Spacer(Modifier.height(10.dp))
                Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    // Pro games by quarter, college by half; then overtimes.
                    val regs = if (b.quarters) 4 else 2
                    val heads = b.halves.indices.map { i ->
                        when {
                            i < regs -> if (b.quarters) "Q${i + 1}" else if (i == 0) "1ST" else "2ND"
                            i == regs -> "OT"
                            else -> "${i - regs + 1}OT"
                        }
                    } + "T"
                    PeriodRow("", heads, P.sub, true)
                    PeriodRow(away.abbr, b.halves.map { "${it[1]}" } + "${g.aws}", P.ink, false)
                    PeriodRow(home.abbr, b.halves.map { "${it[0]}" } + "${g.hs}", P.ink, false)
                }
                Section("Team comparison")
                Panel {
                    val at = StatLine().also { s -> b.away.forEach { s.add(it.s) } }
                    val ht = StatLine().also { s -> b.home.forEach { s.add(it.s) } }
                    val accent = P.accent
                    val faint = P.faint
                    val (ac, hc) = when (lg.userTeamId) {
                        away.id -> accent to teamInk(home).let { if (contrast(it, accent) < 1.4f) faint else it }
                        home.id -> teamInk(away).let { if (contrast(it, accent) < 1.4f) faint else it } to accent
                        else -> teamInk(away) to teamInk(home).let { if (contrast(it, teamInk(away)) < 1.4f) faint else it }
                    }
                    Compare("Field goal %", (at.fgPct * 100).toInt(), (ht.fgPct * 100).toInt(), ac, hc)
                    Compare("3-pointers", at.tpm, ht.tpm, ac, hc)
                    Compare("Rebounds", at.reb, ht.reb, ac, hc)
                    Compare("Assists", at.ast, ht.ast, ac, hc)
                    Compare("Turnovers", at.tov, ht.tov, ac, hc)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        val b = g.box
        if (b == null) item { Empty(if (g.played) "Box scores are kept for your games and the tournaments." else "This game hasn't been played yet.") }
        else item {
            Segmented(listOf(away.school, home.school), side, { side = it })
            Spacer(Modifier.height(8.dp))
            BoxTable(vm, lg, if (side == 0) away else home, if (side == 0) b.away else b.home)
            // Where the shots came from (the user's games keep every one).
            if (g.shots.isNotEmpty()) {
                Section("Shot chart · ${if (side == 0) away.abbr else home.abbr}")
                Panel(padding = PaddingValues(12.dp)) { ShotChartView(lg, g.shots, if (side == 0) 1 else 0) }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PeriodRow(label: String, values: List<String>, color: Color, header: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = T.bodyB.copy(fontSize = 14.sp), color = color, modifier = Modifier.width(70.dp))
        values.forEachIndexed { i, v ->
            val last = i == values.lastIndex
            Text(v, style = if (header) T.label else if (last) T.numSm.copy(fontSize = 17.sp) else T.body, color = if (header) P.sub else color,
                modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = if (last && !header) FontWeight.Bold else null)
        }
    }
}

/** Coach mode: a call to make (a timeout, the closing five, the last play on either end). */
@Composable
private fun CallCard(lg: League, lv: LiveGame, c: com.nh.hoopscoach.game.GameEngine.CoachCall) {
    val e = lv.engine
    Panel(Modifier.popIn(c), color = P.hero) {
        Text(when (c.kind) { "timeout" -> "THEIR RUN"; "closers" -> "CRUNCH TIME"; "defense" -> "ONE STOP"; "halftime" -> "HALFTIME"; else -> "YOUR CALL" }, style = T.label, color = heroKicker)
        Text(c.title, style = T.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = P.onHero)
        Text(c.sub, style = T.small, color = P.onHero.copy(alpha = 0.85f))
        if (c.kind == "halftime" && lg.ollama.enabled && lg.ollama.halftimeSpeeches) {
            val usScore = if (e.coachSide >= 0) e.score[e.coachSide] else e.score[0]
            val themScore = if (e.coachSide >= 0) e.score[1 - e.coachSide] else e.score[1]
            var speech by remember(e.game.id, usScore, themScore) { mutableStateOf<Ollama.HalftimeSpeech?>(null) }
            var loadingSpeech by remember(e.game.id) { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            LaunchedEffect(e.game.id) {
                val cacheKey = "halftime_${e.game.id}_${usScore}_${themScore}_${lg.ollama.activeModelName}"
                Ollama.Cache.get<Ollama.HalftimeSpeech>(cacheKey)?.let { speech = it }
            }
            Spacer(Modifier.height(8.dp))
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(P.onHero.copy(alpha = 0.12f))
                    .padding(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("LOCKER ROOM", style = T.label, color = P.onHero, modifier = Modifier.weight(1f))
                    AiBadge(speech?.isAi == true, lg.ollama.providerDisplayName, fallbackReason = speech?.fallbackReason.orEmpty())
                    if (speech == null && !loadingSpeech) {
                        SmallButton("Generate AI Speech", primary = true) {
                            loadingSpeech = true
                            scope.launch {
                                val res = Ollama.generateHalftimeSpeech(lg.ollama, lg, e.game, usScore, themScore)
                                loadingSpeech = false
                                res.onSuccess { speech = it }
                            }
                        }
                    }
                }
                if (loadingSpeech) {
                    Spacer(Modifier.height(6.dp))
                    Text("AI is delivering halftime locker room address...", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                } else if (speech != null) {
                    val s = speech!!
                    Spacer(Modifier.height(6.dp))
                    Text("LOCKER ROOM ADDRESS", style = T.label.copy(fontSize = 9.sp), color = heroKicker)
                    Spacer(Modifier.height(2.dp))
                    Text("\u201c${s.lockerRoomSpeech}\u201d", style = T.bodyB.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 13.sp), color = P.onHero)
                    Spacer(Modifier.height(6.dp))
                    Text("TACTICAL ADJUSTMENT", style = T.label.copy(fontSize = 9.sp), color = heroKicker)
                    Spacer(Modifier.height(2.dp))
                    Text(s.tacticalAdjustment, style = T.small.copy(fontSize = 12.sp), color = P.onHero.copy(alpha = 0.9f))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        fun choose(id: String) { e.playCall(id); lv.momentResult = e.lastCall; lv.call = null; lv.tick++ }
        if (c.options.size > 4) {
            // Many choices: two to a row, so they all fit on screen.
            for (pair in c.options.chunked(2)) Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                for ((id, label, detail) in pair) Column(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(14.dp)).background(P.onHero.copy(alpha = 0.14f))
                        .clickable { choose(id) }.padding(horizontal = 12.dp, vertical = 9.dp),
                ) {
                    Text(label, style = T.bodyB.copy(fontSize = 14.sp), color = P.onHero, maxLines = 2)
                    if (detail.isNotEmpty()) Text(detail, style = T.small.copy(fontSize = 12.sp), color = P.onHero.copy(alpha = 0.8f), maxLines = 2)
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        } else for ((id, label, detail) in c.options) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(P.onHero.copy(alpha = 0.14f))
                    .clickable { choose(id) }.padding(horizontal = 14.dp, vertical = 11.dp),
            ) {
                Text(label, style = T.bodyB, color = P.onHero)
                if (detail.isNotEmpty()) Text(detail, style = T.small, color = P.onHero.copy(alpha = 0.8f))
            }
        }
    }
}

/** Player mode: late and close, the ball is yours. Pick the play; your ratings decide the rest. */
@Composable
private fun MomentCard(lg: League, lv: LiveGame, m: com.nh.hoopscoach.game.GameEngine.BigMoment) {
    val e = lv.engine
    Panel(Modifier.popIn(m), color = P.hero) {
        lv.momentResult?.let {
            Text("LAST TIME: $it", style = T.small, color = P.onHero.copy(alpha = 0.85f), modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth()
                .clip(RoundedCornerShape(10.dp)).background(P.onHero.copy(alpha = 0.1f)).padding(horizontal = 10.dp, vertical = 6.dp))
        }
        Text("BIG MOMENT", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        val sit = when { m.margin < 0 -> "down ${-m.margin}"; m.margin > 0 -> "up ${m.margin}"; else -> "tied" }
        Text("${clock(m.clock.toInt())} left · $sit · your ball", style = T.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = P.onHero)
        Text("${lg.hero?.name ?: "You"} has it at the top of the key. What's the play?", style = T.small, color = P.onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        // His own shot: the timing meter decides the release.
        var aiming by remember(m) { androidx.compose.runtime.mutableStateOf<com.nh.hoopscoach.game.GameEngine.MomentChoice?>(null) }
        val meterOn = lg.road?.shotMeter != false
        val aim = aiming
        if (aim != null) ShotMeter(lg, aim) { grade, bonus -> e.playMoment(aim, bonus); lv.momentResult = "$grade ${e.lastMoment ?: ""}".trim(); lv.moment = null; lv.tick++ }
        else for ((choice, label, pct) in m.options) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(P.onHero.copy(alpha = 0.14f))
                    .clickable {
                        if (meterOn && choice != com.nh.hoopscoach.game.GameEngine.MomentChoice.KICK) { aiming = choice; return@clickable }
                        e.playMoment(choice)
                        lv.momentResult = e.lastMoment
                        lv.moment = null
                        lv.tick++
                    }.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(label, style = T.bodyB, color = P.onHero, modifier = Modifier.weight(1f))
                Text("$pct%", style = T.numSm, color = P.onHero)
            }
        }
        if (aim == null) Text("Chance to score, from your ratings against this defense.", style = T.small, color = P.onHero.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
    }
}


/**
 * The shot meter: a marker sweeps across the bar; stop it in the green. The green is wider for a better
 * shooter (his three, midrange or inside rating for this shot). Perfect is the middle of the green.
 */
@Composable
private fun ShotMeter(lg: League, choice: com.nh.hoopscoach.game.GameEngine.MomentChoice, onRelease: (String, Double) -> Unit) {
    val h = lg.hero
    val drive = choice == com.nh.hoopscoach.game.GameEngine.MomentChoice.DRIVE
    val rating = when {
        h == null -> 70
        drive -> h[com.nh.hoopscoach.game.Attr.INS]
        h[com.nh.hoopscoach.game.Attr.OUT] >= h[com.nh.hoopscoach.game.Attr.MID] + 3 -> h[com.nh.hoopscoach.game.Attr.OUT]
        else -> h[com.nh.hoopscoach.game.Attr.MID]
    }
    val window = (0.08f + (rating - 60) / 40f * 0.11f).coerceIn(0.06f, 0.2f)
    // The marker runs on the screen's frame clock, not the animation system, so it still sweeps on a
    // phone with animations turned off or reduced (battery saver, accessibility settings).
    var pos by remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    var released by remember { androidx.compose.runtime.mutableStateOf(false) }
    val period = if (drive) 820.0 else 940.0
    LaunchedEffect(Unit) {
        val start = androidx.compose.runtime.withFrameNanos { it }
        while (!released) androidx.compose.runtime.withFrameNanos { t ->
            val phase = ((t - start) / 1_000_000.0 % (2 * period)) / period
            pos = (if (phase <= 1.0) phase else 2.0 - phase).toFloat()
        }
    }
    val green = P.good
    val track = P.onHero.copy(alpha = 0.18f)
    val marker = P.onHero
    Text(if (drive) "Time your finish at the rim" else "Time your release", style = T.bodyB, color = P.onHero)
    Spacer(Modifier.height(10.dp))
    androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(34.dp)) {
        val r = androidx.compose.ui.geometry.CornerRadius(10.dp.toPx())
        drawRoundRect(track, cornerRadius = r)
        val gx = size.width * (0.5f - window / 2)
        drawRoundRect(green.copy(alpha = 0.55f), androidx.compose.ui.geometry.Offset(gx, 0f), androidx.compose.ui.geometry.Size(size.width * window, size.height), r)
        val px = size.width * (0.5f - window * 0.35f / 2)
        drawRect(green, androidx.compose.ui.geometry.Offset(px, 0f), androidx.compose.ui.geometry.Size(size.width * window * 0.35f, size.height))
        val mx = size.width * pos
        drawRect(marker, androidx.compose.ui.geometry.Offset(mx - 2.dp.toPx(), -2f), androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height + 4f))
    }
    Spacer(Modifier.height(12.dp))
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(P.gold).clickable(enabled = !released) {
            released = true
            val off = kotlin.math.abs(pos - 0.5f)
            val half = window / 2
            val (grade, bonus) = when {
                off <= half * 0.35f -> "Perfect release!" to 0.12
                off <= half -> "Good release." to 0.05
                off <= half * 2 -> (if (pos < 0.5f) "A little early." else "A little late.") to -0.04
                else -> (if (pos < 0.5f) "Way early." else "Way late.") to -0.12
            }
            onRelease(grade, bonus)
        }.padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) { Text(if (drive) "FINISH" else "RELEASE", style = T.head, color = onColor(P.gold)) }
    Text("Stop the marker in the green. The middle is perfect.", style = T.small, color = P.onHero.copy(alpha = 0.7f), modifier = Modifier.padding(top = 6.dp))
}

/** Postgame player interviews — answers reflect personality, performance, confidence, and coach bond. */
@Composable
fun PlayerInterviewCard(vm: GameViewModel, lg: League, g: Game) {
    var interviews by remember(g.id, vm.rev) { mutableStateOf(g.interviews.toList()) }
    var writing by remember(g.id) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun refresh() {
        writing = true
        scope.launch {
            val res = Ollama.generatePlayerInterviews(lg.ollama, lg, g)
            writing = false
            res.onSuccess { list ->
                vm.act {
                    val target = it.games.firstOrNull { game -> game.id == g.id } ?: return@act
                    target.interviews.clear()
                    target.interviews.addAll(list)
                }
                interviews = list
                vm.toast(if (list.any { it.isAi }) "Player interviews ready" else "Game-written interviews ready")
            }.onFailure { err ->
                vm.toast("Interviews: ${err.message ?: "failed"}")
            }
        }
    }
    Section(
        "Player interviews",
        action = when {
            writing -> "Interviewing…"
            interviews.isEmpty() -> "${lg.ollama.shortActionName} Interview"
            else -> "Re-interview"
        },
        onAction = { if (!writing) refresh() },
    )
    Panel {
        if (writing) {
            Text("${lg.ollama.providerDisplayName} is interviewing your players…", style = T.small, color = P.accent)
        } else if (interviews.isEmpty()) {
            Text("Hear your players after the game — answers follow personality, tonight's line, confidence, and how they feel about the coach.", style = T.small, color = P.sub)
            Spacer(Modifier.height(8.dp))
            SmallButton("Run interviews", primary = true) { refresh() }
        } else {
            interviews.forEachIndexed { i, iv ->
                if (i > 0) {
                    Spacer(Modifier.height(10.dp))
                    Hairline()
                    Spacer(Modifier.height(10.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(iv.name.uppercase(), style = T.label, color = P.accent, modifier = Modifier.weight(1f))
                    if (iv.tone.isNotBlank()) Tag(iv.tone.uppercase(), when (iv.tone.lowercase()) {
                        "confident" -> P.warn
                        "humble" -> P.good
                        "frustrated" -> P.bad
                        else -> P.sub
                    })
                    Spacer(Modifier.width(6.dp))
                    AiBadge(iv.isAi, iv.reporter.substringAfterLast(" · ", lg.ollama.shortActionName), fallbackReason = iv.fallbackReason)
                }
                if (iv.reporter.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(iv.reporter, style = T.small, color = P.faint)
                }
                Spacer(Modifier.height(6.dp))
                Text("\u201c${iv.question}\u201d", style = T.bodyB, color = P.ink)
                Spacer(Modifier.height(6.dp))
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.surface2).padding(10.dp),
                ) {
                    Text("\u201c${iv.answer}\u201d", style = T.body.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = P.ink)
                }
                lg.playerOrNull(iv.pid)?.let { p ->
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = { vm.openPlayer(p.id) }) {
                        Text("Open ${p.short}'s page", color = P.accent)
                    }
                }
            }
        }
    }
}

/** Beat writer postgame report and quotes generated from simulated stats. */
@Composable
fun OllamaGameReportCard(vm: GameViewModel, lg: League, g: Game) {
    if (!g.played) return
    var report by remember(g.id) { mutableStateOf<Ollama.PostgameReport?>(null) }
    var writing by remember(g.id) { mutableStateOf(false) }
    var streamingText by remember(g.id) { mutableStateOf("") }
    var errorMsg by remember(g.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    // Instant load from cache if previously generated
    LaunchedEffect(g.id) {
        if (report == null) {
            val cacheKey = "recap_${g.id}_${lg.ollama.activeModelName}_${lg.ollama.speedMode}"
            Ollama.Cache.get<Ollama.PostgameReport>(cacheKey)?.let { report = it }
        }
    }

    Section(
        "Beat report & quotes",
        action = if (report == null && !writing) "${lg.ollama.shortActionName} Report ✍️" else if (writing) "Writing..." else "Rewrite",
        onAction = {
            writing = true
            streamingText = ""
            errorMsg = ""
            scope.launch {
                val res = Ollama.generatePostgameReport(lg.ollama, lg, g) { token ->
                    streamingText += token
                }
                writing = false
                res.onSuccess { rep ->
                    report = rep
                }.onFailure { err ->
                    errorMsg = err.message ?: "Generation failed"
                    vm.toast("AI: $errorMsg")
                }
            }
        },
    )

    Panel {
        if (writing) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = P.accent,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (streamingText.isNotEmpty()) "${lg.ollama.providerDisplayName} is generating live..."
                        else "${lg.ollama.providerDisplayName} is writing the beat report & quotes...",
                        style = T.small,
                        color = P.accent,
                    )
                }
                if (streamingText.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        streamingText.takeLast(320),
                        style = T.small.copy(fontSize = 11.sp),
                        color = P.sub,
                        maxLines = 4,
                    )
                }
            }
        } else if (report != null) {
            val rep = report!!
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(rep.outlet.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.weight(1f))
                AiBadge(rep.isAi, lg.ollama.providerDisplayName, fallbackReason = rep.fallbackReason)
            }
            Spacer(Modifier.height(4.dp))
            Text(rep.headline, style = T.head, color = P.ink)
            Spacer(Modifier.height(8.dp))
            Text(rep.story, style = T.body, color = P.ink)
            Spacer(Modifier.height(12.dp))
            // Coach's quote
            if (rep.coachQuote.isNotBlank()) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("COACH'S PODIUM", style = T.label, color = P.accent, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Text("\u201c${rep.coachQuote}\u201d", style = T.bodyB.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = P.ink)
            }
            Spacer(Modifier.height(8.dp))
            // Star player's quote
            if (rep.starQuote.isNotBlank()) Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(P.surface2, RoundedCornerShape(10.dp))
                    .padding(10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("LOCKER ROOM REACTION", style = T.label, color = P.gold, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(2.dp))
                Text("\u201c${rep.starQuote}\u201d", style = T.bodyB.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = P.ink)
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("${lg.ollama.providerDisplayName} Beat Report", style = T.bodyB, color = P.ink)
                    Text("Generate a postgame story with coach and locker room quotes using ${lg.ollama.providerDisplayName} (${lg.ollama.activeModelName}). Labeled by AI.", style = T.small, color = P.sub)
                    if (errorMsg.isNotEmpty()) {
                        Text(errorMsg, style = T.small, color = P.bad)
                    }
                }
                Spacer(Modifier.width(8.dp))
                SmallButton("Generate", primary = true) {
                    writing = true
                    streamingText = ""
                    errorMsg = ""
                    scope.launch {
                        val res = Ollama.generatePostgameReport(lg.ollama, lg, g) { token ->
                            streamingText += token
                        }
                        writing = false
                        res.onSuccess { rep ->
                            report = rep
                        }.onFailure { err ->
                            errorMsg = err.message ?: "Generation failed"
                            vm.toast("AI: $errorMsg")
                        }
                    }
                }
            }
        }
    }
}

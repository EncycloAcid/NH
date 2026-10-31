package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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

/** Seconds elapsed in the game for a period/clock pair (two 20-minute halves, 5-minute overtimes). */
private fun elapsed(period: Int, clock: Int, half: Int = 1200) = if (period <= 2) (period - 1) * half + (half - clock) else 2 * half + (period - 3) * 300 + (300 - clock)

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
            if (lv.speed > 0 && lv.moment == null) {
                val m = e.pendingMoment()
                if (m != null) lv.moment = m else if (spot) e.stepToHero() else e.step()
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
        BackBar(e.game.label.ifEmpty { gameKind(lg, e.game) }, if (e.finished) "Final" else "${e.periodLabel} half · ${clock(e.clock.toInt().coerceAtLeast(0))}", onBack = { vm.back() })
        Scoreboard(away, home, e.score[1], e.score[0], if (e.finished) "FINAL" else clock(e.clock.toInt().coerceAtLeast(0)), if (e.finished) "" else e.periodLabel, lg.userTeamId)
        Spacer(Modifier.height(8.dp))
        CrowdLine(lg, e.game)
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
        Panel(padding = PaddingValues(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp)) {
            val pts = remember(lv.tick) {
                val half = e.half.toInt()
                val list = mutableListOf(0f to winProb(0, 2 * half, edge, 2 * half).toFloat())
                for (ev in e.events) list += elapsed(ev.period, ev.clock, half).toFloat() to winProb(ev.hs - ev.aws, (2 * half - elapsed(ev.period, ev.clock, half)).coerceAtLeast(0), edge, 2 * half).toFloat()
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
        lv.momentResult?.takeIf { lv.moment == null }?.let {
            Panel(color = P.accentSoft, padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) { Text("★ $it", style = T.bodyB, color = P.ink) }
            Spacer(Modifier.height(8.dp))
        }
        if (!lv.done) {
            if (lv.moment == null) {
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Segmented(listOf(if (lv.speed == 0) "▶" else "❚❚", "1×", "2×", "4×"), lv.speed, { lv.speed = if (it == 0 && lv.speed == 0) 2 else it }, Modifier.weight(1f).padding(horizontal = 0.dp))
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
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Plays", away.abbr, home.abbr), view, { view = it })
        Spacer(Modifier.height(6.dp))
        if (view == 0) {
            val events = e.events.asReversed()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(events.size) { i -> PlayRow(events[i], home, away) }
            }
        } else {
            val box = e.boxScore()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) { item { BoxTable(vm, lg, if (view == 1) away else home, if (view == 1) box.away else box.home) } }
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
        Text("$score", style = T.hero.copy(fontSize = 48.sp, lineHeight = 48.sp), color = if (final && !ahead) P.sub else P.ink)
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
            Column(Modifier.width(128.dp).padding(start = 14.dp)) {
                Text("PLAYER", style = T.label, color = P.sub, modifier = Modifier.height(28.dp).padding(top = 8.dp))
                lines.forEach { l ->
                    Row(Modifier.height(38.dp).clickable { vm.openPlayer(l.pid) }, verticalAlignment = Alignment.CenterVertically) {
                        Text(l.name, style = if (l.starter) T.bodyB.copy(fontSize = 14.sp) else T.body.copy(fontSize = 14.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Text(" ${l.pos}", style = T.small.copy(fontSize = 10.sp), color = P.faint)
                    }
                }
                Text("TEAM", style = T.label, color = P.ink, modifier = Modifier.height(38.dp).padding(top = 12.dp))
            }
            Column(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = 12.dp)) {
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
            val b = g.box
            if (b != null) {
                Spacer(Modifier.height(10.dp))
                Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
                    val heads = b.halves.indices.map { if (it == 0) "1ST" else if (it == 1) "2ND" else if (it == 2) "OT" else "${it - 1}OT" } + "T"
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

/** Player mode: late and close, the ball is yours. Pick the play; your ratings decide the rest. */
@Composable
private fun MomentCard(lg: League, lv: LiveGame, m: com.nh.hoopscoach.game.GameEngine.BigMoment) {
    val e = lv.engine
    Panel(color = P.hero) {
        Text("BIG MOMENT", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        val sit = when { m.margin < 0 -> "down ${-m.margin}"; m.margin > 0 -> "up ${m.margin}"; else -> "tied" }
        Text("${clock(m.clock.toInt())} left · $sit · your ball", style = T.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = P.onHero)
        Text("${lg.hero?.name ?: "You"} has it at the top of the key. What's the play?", style = T.small, color = P.onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        for ((choice, label, pct) in m.options) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp)).background(P.onHero.copy(alpha = 0.14f))
                    .clickable {
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
        Text("Chance to score, from your ratings against this defense.", style = T.small, color = P.onHero.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp))
    }
}


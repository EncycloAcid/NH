package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.Series

// ---------- the playoff bracket ----------

private val CARD_W = 128.dp
private val CARD_H = 74.dp
private val ROW_GAP = 14.dp
private val COL_GAP = 22.dp
private val LABEL_H = 28.dp

/** The series in [round] at [slot] for conference [conf] (-1 for the Finals), if it's been set. */
private fun series(lg: League, round: Int, conf: Int, slot: Int): Series? =
    lg.proState?.series?.firstOrNull { it.round == round && it.slot == slot && (round == 4 || it.conf == conf) }

/** Who's already through to a matchup that hasn't been set: the winners of its two feeder series. */
private fun feeders(lg: League, round: Int, conf: Int, slot: Int): Pair<Int, Int> = when (round) {
    4 -> (series(lg, 3, 0, 0)?.winner ?: -1) to (series(lg, 3, 1, 0)?.winner ?: -1)
    else -> (series(lg, round - 1, conf, slot * 2)?.winner ?: -1) to (series(lg, round - 1, conf, slot * 2 + 1)?.winner ?: -1)
}

/**
 * The playoffs as a real bracket: the West on the left, the East on the right, both converging on the
 * Finals in the middle. Lines join each round to the next and light up as teams advance. Tapping a
 * matchup opens that round's series list. It scrolls sideways and opens on the user's side.
 */
@Composable
fun PlayoffBracket(vm: GameViewModel, lg: League) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Columns, left to right: West first round, semis, conference finals, the Finals, then the East mirrored.
    val cols = listOf(1 to 1, 1 to 2, 1 to 3, -1 to 4, 0 to 3, 0 to 2, 0 to 1)
    val totalW = CARD_W * 7 + COL_GAP * 6
    val totalH = LABEL_H + CARD_H * 4 + ROW_GAP * 3
    fun x(col: Int): Dp = (CARD_W + COL_GAP) * col
    fun yCenter(round: Int, slot: Int): Dp {
        val r1 = { i: Int -> LABEL_H + (CARD_H + ROW_GAP) * i + CARD_H / 2 }
        return when (round) {
            1 -> r1(slot)
            2 -> (r1(slot * 2) + r1(slot * 2 + 1)) / 2
            else -> (r1(0) + r1(3)) / 2
        }
    }
    // Open on the user's conference: the East is on the right.
    LaunchedEffect(scroll.maxValue > 0) { if (lg.user.confId == 0 && scroll.maxValue > 0) scroll.scrollTo(scroll.maxValue) }
    val line = P.line
    val lit = P.accent
    Box(Modifier.fillMaxWidth().horizontalScroll(scroll).padding(horizontal = Gutter, vertical = 6.dp)) {
        Box(
            Modifier.size(totalW, totalH).drawBehind {
                fun px(d: Dp) = with(density) { d.toPx() }
                val stroke = px(2.dp)
                fun seg(x0: Dp, y0: Dp, x1: Dp, y1: Dp, on: Boolean) =
                    drawLine(if (on) lit else line, Offset(px(x0), px(y0)), Offset(px(x1), px(y1)), stroke, StrokeCap.Round)
                // Each side: child pairs into their parent, then each conference final into the Finals.
                for ((conf, dir) in listOf(1 to 1, 0 to -1)) {
                    for (round in 1..2) {
                        val childCol = if (dir == 1) round - 1 else 7 - round
                        val parentCol = childCol + dir
                        val edge = if (dir == 1) x(childCol) + CARD_W else x(childCol)
                        val into = if (dir == 1) x(parentCol) else x(parentCol) + CARD_W
                        val mid = (edge + into) / 2
                        for (k in 0 until (if (round == 1) 2 else 1)) {
                            val a = yCenter(round, k * 2)
                            val b = yCenter(round, k * 2 + 1)
                            val p = yCenter(round + 1, k)
                            val aOn = series(lg, round, conf, k * 2)?.done == true
                            val bOn = series(lg, round, conf, k * 2 + 1)?.done == true
                            seg(edge, a, mid, a, aOn); seg(mid, a, mid, p, aOn)
                            seg(edge, b, mid, b, bOn); seg(mid, b, mid, p, bOn)
                            seg(mid, p, into, p, aOn && bOn)
                        }
                    }
                    val cf = yCenter(3, 0)
                    val on = series(lg, 3, conf, 0)?.done == true
                    if (dir == 1) seg(x(2) + CARD_W, cf, x(3), cf, on) else seg(x(4), cf, x(3) + CARD_W, cf, on)
                }
            },
        ) {
            // Round labels.
            val labels = listOf("FIRST ROUND", "CONF SEMIS", "WEST FINALS", "NBA FINALS", "EAST FINALS", "CONF SEMIS", "FIRST ROUND")
            labels.forEachIndexed { i, l ->
                Text(l, style = T.label.copy(fontSize = 10.sp), color = if (i == 3) P.gold else P.sub, textAlign = TextAlign.Center,
                    modifier = Modifier.offset(x(i), 4.dp).width(CARD_W), maxLines = 1)
            }
            cols.forEachIndexed { col, (conf, round) ->
                val count = when (round) { 1 -> 4; 2 -> 2; else -> 1 }
                for (slot in 0 until count) {
                    val s = series(lg, round, conf, slot)
                    val (fa, fb) = if (s == null && round > 1) feeders(lg, round, conf, slot) else -1 to -1
                    val finals = round == 4
                    val h = if (finals) CARD_H + 18.dp else CARD_H
                    MatchCard(lg, s, fa, fb, finals, Modifier.offset(x(col), yCenter(round, slot) - h / 2).size(CARD_W, h)) {
                        vm.proRound = round; vm.playoffView = 1
                    }
                }
            }
        }
    }
}

/** One matchup: two teams with seeds, logos and series wins; the winner bold, the loser faded, the user's team highlighted. */
@Composable
private fun MatchCard(lg: League, s: Series?, feedA: Int, feedB: Int, finals: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val u = lg.userTeamId
    val teams = if (s != null) listOf(s.hi, s.lo) else listOf(feedA, feedB)
    val wins = if (s != null) ProSeason.seriesScore(lg, s, s.hi).let { listOf(it.first, it.second) } else listOf(-1, -1)
    val winner = s?.winner ?: -1
    val live = s != null && !s.done && s.gameIds.isNotEmpty()
    val mine = teams.any { it == u && it >= 0 }
    val border = when { finals -> P.gold; mine -> P.accent; live -> P.accent.copy(alpha = 0.6f); else -> P.line }
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (mine) P.accentSoft else P.surface)
            .border(if (finals || mine) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = s != null) { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        if (finals) Text("🏆 FINALS", style = T.label.copy(fontSize = 9.sp), color = P.gold, modifier = Modifier.padding(bottom = 2.dp))
        for (i in 0..1) {
            val tid = teams[i]
            val t = lg.teams.getOrNull(tid)
            val out = winner >= 0 && winner != tid
            Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (t == null) {
                    Text("TBD", style = T.small.copy(fontSize = 12.sp), color = P.faint)
                } else {
                    val seed = ProSeason.seed(lg, tid)
                    Text(if (seed > 0) "$seed" else "", style = T.small.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.width(13.dp))
                    TeamBadge(t, 18.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(t.mascot, style = (if (winner == tid || tid == u) T.bodyB else T.body).copy(fontSize = 12.5.sp),
                        color = if (out) P.faint else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (s != null && s.gameIds.isNotEmpty()) Text("${wins[i]}", style = T.numSm.copy(fontSize = 15.sp), color = if (winner == tid) P.accent else if (out) P.faint else P.ink)
                }
            }
        }
        // What's happening in the series, in a few words.
        val note = when {
            s == null -> if (teams.any { it >= 0 }) "Waiting" else ""
            s.done -> "${lg.team(s.winner).mascot} win ${maxOf(wins[0], wins[1])}-${minOf(wins[0], wins[1])}"
            s.gameIds.isEmpty() -> "Starts soon"
            wins[0] == wins[1] -> "Tied ${wins[0]}-${wins[1]}"
            else -> "${lg.team(if (wins[0] > wins[1]) s.hi else s.lo).mascot} lead ${maxOf(wins[0], wins[1])}-${minOf(wins[0], wins[1])}"
        }
        if (note.isNotEmpty()) Text(note, style = T.small.copy(fontSize = 10.5.sp), color = if (live) P.accent else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * The top of the playoffs page: the champion's banner in its own colors once it's over, or the round in
 * progress with the user's series (or the Finals) while it's on.
 */
@Composable
fun PlayoffHeader(lg: League) {
    val ps = lg.proState ?: return
    val champ = lg.teams.getOrNull(ps.champion)
    if (champ != null) {
        val c = Color(champ.primary)
        val fg = onColor(c)
        val finals = ps.series.firstOrNull { it.round == 4 }
        Column(
            Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(c, lerp(c, Color.Black, 0.55f)))).padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏆 ${lg.season} NBA CHAMPIONS", style = T.label, color = fg.copy(alpha = 0.85f))
                    Text(champ.name.uppercase(), style = T.hero.copy(fontSize = 30.sp, lineHeight = 32.sp), color = fg)
                }
                TeamBadge(champ, 64.dp)
            }
            finals?.let { f ->
                val other = lg.team(if (f.hi == champ.id) f.lo else f.hi)
                val (a, b) = ProSeason.seriesScore(lg, f, champ.id)
                Spacer(Modifier.height(8.dp))
                Text("Beat the ${other.name} $a-$b in the Finals", style = T.bodyB, color = fg)
            }
            lg.seasonAwards.firstOrNull { it.award == "Finals MVP" }?.let { m ->
                Text("Finals MVP: ${m.name} · ${m.line}", style = T.small, color = fg.copy(alpha = 0.9f))
            }
        }
        return
    }
    val active = ps.series.filter { !it.done }
    val round = active.minOfOrNull { it.round } ?: ps.series.maxOfOrNull { it.round } ?: 1
    val mine = ps.series.lastOrNull { it.involves(lg.userTeamId) }
    val focus = mine?.takeIf { !it.done } ?: ps.series.firstOrNull { it.round == 4 } ?: active.firstOrNull()
    Column(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(P.hero, lerp(P.hero, Color.Black, 0.45f)))).padding(18.dp),
    ) {
        Text("${lg.season} PLAYOFFS", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text(if (round == 0) "PLAY-IN" else com.nh.hoopscoach.game.ProLeague.ROUND_NAMES[round - 1].uppercase(), style = T.hero.copy(fontSize = 28.sp, lineHeight = 30.sp), color = P.onHero)
        Text("${active.size} ${if (active.size == 1) "series" else "series"} still going · ${ps.series.count { it.round >= 1 && it.done }} decided", style = T.small, color = P.onHero.copy(alpha = 0.85f))
        focus?.let { s ->
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.12f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(lg.team(s.hi), 30.dp)
                Column(Modifier.weight(1f).padding(horizontal = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val (a, b) = ProSeason.seriesScore(lg, s, s.hi)
                    Text("$a – $b", style = T.numSm.copy(fontSize = 22.sp), color = P.onHero)
                    Text(if (s.involves(lg.userTeamId)) "Your series · ${ProSeason.roundName(s)}" else ProSeason.roundName(s), style = T.small, color = P.onHero.copy(alpha = 0.85f), maxLines = 1)
                    ProSeason.nextGame(lg, s).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.small, color = P.onHero.copy(alpha = 0.75f), maxLines = 1) }
                }
                TeamBadge(lg.team(s.lo), 30.dp)
            }
        }
    }
}

/** The play-in games, three a conference, as small cards. */
@Composable
fun PlayInStrip(lg: League) {
    val ps = lg.proState ?: return
    val games = ps.series.filter { it.round == 0 }
    if (games.isEmpty()) return
    Section("Play-in")
    for (c in listOf(1, 0)) {
        val cs = games.filter { it.conf == c }.sortedBy { it.slot }
        if (cs.isEmpty()) continue
        Text(lg.conf(c).name.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 4.dp, bottom = 4.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            for (s in cs) {
                val g = s.gameIds.lastOrNull()?.let { lg.game(it) }?.takeIf { it.played }
                Column(
                    Modifier.width(150.dp).clip(RoundedCornerShape(12.dp)).background(if (s.involves(lg.userTeamId)) P.accentSoft else P.surface)
                        .border(1.dp, P.line, RoundedCornerShape(12.dp)).padding(10.dp),
                ) {
                    Text(ProSeason.playInName(s).uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.gold)
                    for (tid in listOf(s.hi, s.lo)) {
                        val t = lg.team(tid)
                        val out = s.done && s.winner != tid
                        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            TeamBadge(t, 18.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(t.mascot, style = T.body.copy(fontSize = 12.5.sp), color = if (out) P.faint else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (g != null) Text("${g.scoreFor(tid)}", style = T.numSm.copy(fontSize = 14.sp), color = if (s.winner == tid) P.accent else P.faint)
                        }
                    }
                }
            }
        }
    }
}

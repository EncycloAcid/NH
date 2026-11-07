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
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Season

// ---------- college brackets, drawn as trees ----------

private val GW = 150.dp
private val GH = 70.dp
private val VGAP = 10.dp
private val HGAP = 22.dp
private val TOP = 26.dp

/** The team in round [r] at [slot]: a team id, -1 for a bye, -2 for not decided yet. */
private fun slotOf(b: Bracket, r: Int, slot: Int): Int = b.rounds.getOrNull(r)?.getOrNull(slot) ?: -2

private fun gameOf(lg: League, b: Bracket, r: Int, pair: Int): Game? =
    b.gameIds.getOrNull(r)?.getOrNull(pair)?.takeIf { it >= 0 }?.let { lg.game(it) }

/**
 * A bracket tree: [rounds] rounds starting at round [r0], for the [teams] slots that start at [start] in
 * round [r0]'s order (a region, a quarter, a whole conference tournament). Games line up in columns,
 * each one centered between the two that feed it, with lines that light up as winners move on, and the
 * winner of the last round in a box at the end. Tap a played game for its box score.
 */
@Composable
fun TreeBracket(vm: GameViewModel, lg: League, b: Bracket, r0: Int, rounds: Int, start: Int, teams: Int, ncaa: Boolean, winnerLabel: String) {
    val density = LocalDensity.current
    val first = teams / 2
    fun x(col: Int): Dp = (GW + HGAP) * col
    fun yCenter(col: Int, i: Int): Dp {
        if (col == 0) return TOP + (GH + VGAP) * i + GH / 2
        return (yCenter(col - 1, i * 2) + yCenter(col - 1, i * 2 + 1)) / 2
    }
    val totalW = GW * (rounds + 1) + HGAP * rounds
    val totalH = TOP + GH * first + VGAP * (first - 1).coerceAtLeast(0)
    val line = P.line
    val lit = P.accent
    val winner = slotOf(b, r0 + rounds, start shr rounds)
    Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter, vertical = 6.dp)) {
        Box(
            Modifier.size(totalW, totalH).drawBehind {
                fun px(d: Dp) = with(density) { d.toPx() }
                fun seg(x0: Dp, y0: Dp, x1: Dp, y1: Dp, on: Boolean) =
                    drawLine(if (on) lit else line, Offset(px(x0), px(y0)), Offset(px(x1), px(y1)), px(2.dp), StrokeCap.Round)
                for (col in 0 until rounds) {
                    val games = first shr col
                    val edge = x(col) + GW
                    val into = x(col + 1)
                    val mid = (edge + into) / 2
                    val r = r0 + col
                    // A game is decided once its winner shows up in the next round.
                    fun decided(i: Int) = slotOf(b, r + 1, (start shr (col + 1)) + i) >= 0
                    if (col == rounds - 1) { seg(edge, yCenter(col, 0), into, yCenter(col, 0), decided(0)); continue }
                    for (k in 0 until games / 2) {
                        val a = yCenter(col, 2 * k)
                        val c = yCenter(col, 2 * k + 1)
                        val p = yCenter(col + 1, k)
                        seg(edge, a, mid, a, decided(2 * k)); seg(mid, a, mid, p, decided(2 * k))
                        seg(edge, c, mid, c, decided(2 * k + 1)); seg(mid, c, mid, p, decided(2 * k + 1))
                        seg(mid, p, into, p, decided(2 * k) && decided(2 * k + 1))
                    }
                }
            },
        ) {
            for (col in 0..rounds) {
                val label = if (col == rounds) winnerLabel.uppercase() else roundTitle(b, r0 + col, ncaa).uppercase()
                Text(label, style = T.label.copy(fontSize = 10.sp), color = if (col == rounds) P.gold else P.sub, textAlign = TextAlign.Center,
                    maxLines = 1, modifier = Modifier.offset(x(col), 4.dp).width(GW))
            }
            for (col in 0 until rounds) {
                val r = r0 + col
                val base = start shr col
                for (i in 0 until (first shr col)) {
                    val a = slotOf(b, r, base + 2 * i)
                    val c = slotOf(b, r, base + 2 * i + 1)
                    val g = gameOf(lg, b, r, base / 2 + i)
                    // First-round slots still waiting on a First Four game say who's playing for them.
                    fun tbd(slot: Int) = if (r == 0 && ncaa) b.playIns.firstOrNull { it.slot == slot }?.let { "${lg.team(it.a).abbr}/${lg.team(it.b).abbr}" } else null
                    GameCard(vm, lg, b, a, c, g, tbd(base + 2 * i), tbd(base + 2 * i + 1), ncaa,
                        Modifier.offset(x(col), yCenter(col, i) - GH / 2).size(GW, GH))
                }
            }
            // The winner's box.
            val wy = yCenter(rounds - 1, 0)
            val wt = lg.teams.getOrNull(winner)
            Column(
                Modifier.offset(x(rounds), wy - 30.dp).size(GW, 60.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (wt != null) Brush.verticalGradient(listOf(Color(wt.primary), lerp(Color(wt.primary), Color.Black, 0.45f))) else Brush.verticalGradient(listOf(P.surface, P.surface)))
                    .border(2.dp, P.gold, RoundedCornerShape(14.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                if (wt == null) Text("TBD", style = T.bodyB, color = P.faint)
                else {
                    val fg = onColor(Color(wt.primary))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(wt, 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text("🏆 ${b.seeds[wt.id]?.let { "No. $it" } ?: ""}", style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.85f))
                            Text(wt.school, style = T.bodyB.copy(fontSize = 13.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** One game: two teams with seeds and logos, the score once it's played, and a line on its status. */
@Composable
private fun GameCard(vm: GameViewModel, lg: League, b: Bracket, a: Int, c: Int, g: Game?, tbdA: String?, tbdC: String?, ncaa: Boolean, modifier: Modifier) {
    val played = g != null && g.played
    val mine = a == lg.userTeamId || c == lg.userTeamId
    val w = if (played) g!!.winner() else -1
    val upset = played && ncaa && w >= 0 && (b.seeds[w] ?: 0) - (b.seeds[g!!.opp(w)] ?: 0) >= 5
    val border = when { upset -> P.bad; mine -> P.accent; else -> P.line }
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(if (mine) P.accentSoft else P.surface)
            .border(if (mine || upset) 2.dp else 1.dp, border, RoundedCornerShape(12.dp))
            .clickable(enabled = played) { vm.push(Route.Box(g!!.id)) }.padding(horizontal = 8.dp, vertical = 5.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        for ((id, tbd) in listOf(a to tbdA, c to tbdC)) {
            Row(Modifier.fillMaxWidth().height(22.dp), verticalAlignment = Alignment.CenterVertically) {
                val t = lg.teams.getOrNull(id)
                if (t == null) {
                    Text(if (id == -1) "Bye" else tbd ?: "TBD", style = T.small.copy(fontSize = 12.sp), color = P.faint, modifier = Modifier.padding(start = 15.dp))
                } else {
                    val out = played && w != id
                    Text("${b.seeds[id] ?: ""}", style = T.small.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.width(15.dp))
                    TeamBadge(t, 18.dp)
                    Spacer(Modifier.width(5.dp))
                    Text(t.school, style = (if (w == id || id == lg.userTeamId) T.bodyB else T.body).copy(fontSize = 12.5.sp),
                        color = if (out) P.faint else if (id == lg.userTeamId) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (played) Text("${g!!.scoreFor(id)}", style = T.numSm.copy(fontSize = 14.sp), color = if (w == id) P.ink else P.faint)
                }
            }
        }
        val status = when {
            upset -> "UPSET · final${if (g!!.ot > 0) "/OT" else ""}"
            played -> "Final${if (g!!.ot > 0) "/OT" else ""}"
            a == -1 || c == -1 -> "Bye"
            g != null -> if (g.day == lg.day) "Today" else "Day ${g.day}"
            else -> ""
        }
        if (status.isNotEmpty()) Text(status, style = T.small.copy(fontSize = 10.sp), color = if (upset) P.bad else if (status == "Today") P.accent else P.sub, maxLines = 1)
    }
}

/** The top of a bracket page: the champion's banner in its colors, or the round in progress and where the user's team stands. */
@Composable
fun BracketHeader(lg: League, b: Bracket, ncaa: Boolean) {
    val champ = lg.teams.getOrNull(b.champion)
    val c = champ?.let { Color(it.primary) } ?: P.hero
    val fg = if (champ != null) onColor(c) else P.onHero
    Column(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(Brush.verticalGradient(listOf(c, lerp(c, Color.Black, 0.5f)))).padding(18.dp),
    ) {
        if (champ != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("🏆 ${lg.season} ${if (ncaa) "NATIONAL CHAMPIONS" else b.name.uppercase() + " CHAMPIONS"}", style = T.label, color = fg.copy(alpha = 0.85f))
                    Text(champ.school.uppercase(), style = T.hero.copy(fontSize = 30.sp, lineHeight = 32.sp), color = fg)
                    Text("${champ.record}${b.seeds[champ.id]?.let { " · No. $it seed" } ?: ""}", style = T.small, color = fg.copy(alpha = 0.9f))
                }
                TeamBadge(champ, 64.dp)
            }
        } else {
            val now = if (ncaa && Season.firstFourDay(lg)) "First Four" else roundTitle(b, b.current, ncaa)
            Text("${lg.season} ${b.name.uppercase()}", style = T.label, color = fg.copy(alpha = 0.8f))
            Text(now.uppercase(), style = T.hero.copy(fontSize = 28.sp, lineHeight = 30.sp), color = fg)
            val alive = b.rounds.lastOrNull()?.count { it >= 0 } ?: 0
            val u = lg.userTeamId
            val status = when {
                !b.hasTeam(u) -> "$alive teams left."
                b.rounds.lastOrNull()?.contains(u) == true -> "$alive teams left, and ${lg.user.school} is one of them."
                else -> "$alive teams left. ${lg.user.school} is out."
            }
            Text(status, style = T.small, color = fg.copy(alpha = 0.85f))
        }
    }
}

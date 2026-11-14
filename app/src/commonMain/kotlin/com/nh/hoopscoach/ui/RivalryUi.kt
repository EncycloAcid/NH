package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Rivalries
import com.nh.hoopscoach.game.Rivalry
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Team
import java.util.Locale

fun fans(n: Int): String = String.format(Locale.US, "%,d", n)

/** "Duke leads 3-1", "Series tied 2-2" or "First meeting". */
fun seriesLine(lg: League, r: Rivalry): String {
    val a = lg.team(r.a)
    val b = lg.team(r.b)
    return when {
        r.aWins + r.bWins == 0 -> "First meeting of the dynasty"
        r.aWins == r.bWins -> "Series tied ${r.aWins}-${r.bWins}"
        r.aWins > r.bWins -> "${a.school} leads ${r.aWins}-${r.bWins}"
        else -> "${b.school} leads ${r.bWins}-${r.aWins}"
    }
}

/** Red "RIVALRY" ribbon with the rivalry's name. */
@Composable
fun RivalryRibbon(r: Rivalry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tag("RIVALRY", P.bad, filled = true)
        if (r.name.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(r.name.uppercase(), style = T.label, color = P.bad, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pregame note for a rivalry game: the crowd and what it is worth. */
@Composable
fun RivalryPreview(lg: League, g: Game, r: Rivalry) {
    val crowd = if (g.att > 0) g.att else Rivalries.attendance(lg, g)
    val user = lg.userTeamId
    val edge = when {
        g.neutral -> "Neutral floor, split crowd."
        g.home == user -> "Your crowd is worth +${Rivalries.HOME_BOOST.toInt()} more Elo tonight."
        else -> "Their crowd is worth +${Rivalries.HOME_BOOST.toInt()} more Elo tonight."
    }
    RivalryRibbon(r)
    Spacer(Modifier.height(6.dp))
    Text("Sellout crowd of ${fans(crowd)}. $edge", style = T.small, color = P.ink)
    Text("${seriesLine(lg, r)} · Winner takes prestige from the loser.", style = T.small, color = P.sub)
}

/** Crowd line for live games and box scores. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrowdLine(lg: League, g: Game, modifier: Modifier = Modifier) {
    if (g.att <= 0) return
    val r = lg.rivalry(g)
    // Ribbon and crowd share a line when they fit; on narrow screens the crowd drops below.
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (r != null) Box(Modifier.padding(end = 10.dp)) { RivalryRibbon(r) }
        Text("${fans(g.att)} fans" + if (Rivalries.sellout(lg, g)) " · sellout" else "", style = T.small, color = P.sub, maxLines = 1)
    }
}

/**
 * Hoops Wire rivalry card after a finished rivalry game: kicker, headline, score lines,
 * prestige swing, and top performers — same chrome as the rest of the game UI.
 */
@Composable
fun NewspaperBanner(vm: GameViewModel, lg: League, g: Game, onClick: (() -> Unit)? = { vm.push(Route.Box(g.id)) }) {
    val r = lg.rivalry(g) ?: return
    if (!g.played) return
    val w = lg.team(g.winner())
    val l = lg.team(g.loser())
    val home = lg.team(g.home)
    val ot = if (g.ot > 0) " (${if (g.ot > 1) "${g.ot}" else ""}OT)" else ""
    val where = if (g.neutral) "on a neutral floor" else "at ${home.school}"
    val crowd = if (g.att > 0) " before a sellout crowd of ${fans(g.att)} $where" else ""
    val dateline = if (g.day <= Schedule.REG_DAYS) "Game ${g.day}" else g.label.ifEmpty { "Postseason" }
    val story = lg.news.indexOfLast { it.gameId == g.id && it.body.isNotEmpty() }
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RIVALRY", style = T.label, color = P.bad, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("HOOPS WIRE · ${lg.season} · $dateline", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        }
        Spacer(Modifier.height(8.dp))
        Text(r.title.uppercase(), style = T.label, color = P.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(Rivalries.headline(lg, g), style = T.head, color = P.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            "${w.school} ${g.scoreFor(w.id)}, ${l.school} ${g.scoreFor(l.id)}$ot$crowd.",
            style = T.body,
            color = P.sub,
        )
        Spacer(Modifier.height(12.dp))
        Hairline()
        ScoreLine(w, g.scoreFor(w.id), g.swing, winner = true)
        Hairline()
        ScoreLine(l, g.scoreFor(l.id), -g.swing, winner = false)
        Hairline()
        g.box?.let { box ->
            val tops = listOf(g.home to box.home, g.away to box.away).mapNotNull { (tid, lines) ->
                lines.maxByOrNull { it.s.pts + 0.7 * (it.s.orb + it.s.drb) + 0.9 * it.s.ast }?.let { tid to it }
            }
            if (tops.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("TOP PERFORMERS", style = T.label, color = P.sub)
                for ((tid, pl) in tops) {
                    val me = pl.pid == lg.heroId
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lg.team(tid).abbr, style = T.small, color = P.sub, modifier = Modifier.width(44.dp), maxLines = 1)
                        Text(
                            pl.name + if (me) " (you)" else "",
                            style = T.bodyB,
                            color = if (me) P.accent else P.ink,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text("${pl.s.pts} PTS · ${pl.s.orb + pl.s.drb} REB · ${pl.s.ast} AST", style = T.small, color = P.ink, maxLines = 1)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${seriesLine(lg, r)} in your dynasty", style = T.small, color = P.sub, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Text(
                    if (story >= 0) "Read story ›" else "Box score ›",
                    style = T.bodyB,
                    color = P.accent,
                    maxLines = 1,
                    modifier = if (story >= 0) Modifier.clickable { vm.push(Route.Article(story)) } else Modifier,
                )
            }
        }
    }
}

@Composable
private fun ScoreLine(t: Team, score: Int, swing: Int, winner: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (winner) P.accentSoft else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                t.school,
                style = T.bodyB,
                color = if (winner) P.ink else P.sub,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (swing != 0) {
                Text(
                    "${if (swing > 0) "+" else "−"}${kotlin.math.abs(swing)} prestige",
                    style = T.small,
                    color = if (swing > 0) P.good else P.bad,
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text("$score", style = T.num, color = if (winner) P.accent else P.sub, maxLines = 1)
    }
}

/** A program's rivals with the series record and the next meeting. */
@Composable
fun RivalsList(vm: GameViewModel, lg: League, t: Team) {
    val rivals = lg.rivalsOf(t.id)
    if (rivals.isEmpty()) return
    Section("Rivals")
    GroupList(rivals) { r ->
        val o = lg.team(r.other(t.id))
        val next = lg.games.filter { !it.played && it.involves(t.id) && it.involves(o.id) }.minByOrNull { it.day }
        val w = r.winsFor(t.id)
        val l = r.winsFor(o.id)
        ListRow(onClick = { vm.push(Route.TeamPage(o.id)) }) {
            TeamBadge(o, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(o.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val when_ = next?.let { if (it.day <= Schedule.REG_DAYS) "Next: game ${it.day}" else "Next: ${it.label.substringAfter("· ")}" } ?: "No meeting left this season"
                Text("${r.title} · $when_", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$w-$l", style = T.numSm, color = when { w > l -> P.good; w < l -> P.bad; else -> P.ink })
                Text("SERIES", style = T.label.copy(fontSize = 9.sp), color = P.faint)
            }
        }
    }
}

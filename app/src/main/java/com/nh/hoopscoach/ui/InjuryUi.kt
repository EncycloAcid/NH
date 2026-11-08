package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Injuries
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Team

/** [t]'s injury report: who's out, with what, and for how long. Nothing when everyone is healthy. */
@Composable
fun InjuryReport(vm: GameViewModel, lg: League, t: Team) {
    val hurt = Injuries.report(lg, t)
    if (hurt.isEmpty()) return
    Section("Injury report · ${hurt.size}")
    GroupList(hurt, inset = 70.dp) { p ->
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            PlayerJersey(p, t, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Injuries.tag(p), style = T.small, color = injuryColor(p), maxLines = 2)
            }
            Tag("INJ", P.bad)
        }
    }
}

/** The league's notable injuries, for the News page. */
@Composable
fun LeagueInjuries(vm: GameViewModel, lg: League) {
    val hurt = Injuries.league(lg).take(12)
    if (hurt.isEmpty()) return
    Section("Around the league: injuries")
    GroupList(hurt, inset = 70.dp) { (p, t) ->
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            PlayerJersey(p, t, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.name} · ${t.abbr}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Injuries.tag(p), style = T.small, color = injuryColor(p), maxLines = 2, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

/** How bad it is, at a glance: amber for day-to-day, red for anything longer. */
@Composable
fun injuryColor(p: com.nh.hoopscoach.game.Player): androidx.compose.ui.graphics.Color = if (p.injGames <= 3 && p.injTotal <= 3) P.warn else P.bad

/** The user's team is defending a title: what it's chasing and how it's going. */
@Composable
fun TitleDefenseCard(lg: League) {
    val d = com.nh.hoopscoach.game.TitleDefense.userDefending(lg) ?: return
    if (lg.phase != com.nh.hoopscoach.game.Phase.REGULAR && lg.phase != com.nh.hoopscoach.game.Phase.PLAYOFFS && lg.phase != com.nh.hoopscoach.game.Phase.CONF_TOURNEY && lg.phase != com.nh.hoopscoach.game.Phase.NCAA) return
    val u = lg.user
    Spacer(Modifier.height(6.dp))
    Panel(color = P.hero) {
        Text(if (d.streak >= 2) "${com.nh.hoopscoach.game.TitleDefense.runWords(d.streak).uppercase()} · DEFENDING CHAMPIONS" else "DEFENDING CHAMPIONS", style = T.label, color = heroKicker)
        Text("Chasing ${com.nh.hoopscoach.game.TitleDefense.chasing(d.streak)}", style = T.head, color = P.onHero)
        Text("${u.record} so far. Everyone circles your game on their schedule.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
    }
}

/** The big nights in the next few days, league-wide: Christmas, national TV, top-10 showdowns, rivalry games. */
@Composable
fun MarqueeCard(vm: GameViewModel, lg: League) {
    val games = com.nh.hoopscoach.game.Marquee.upcoming(lg)
    if (games.isEmpty()) return
    Section("Big games")
    GroupList(games) { g ->
        val h = lg.team(g.home); val a = lg.team(g.away)
        val mine = g.involves(lg.userTeamId)
        ListRow(onClick = { vm.push(Route.TeamPage(if (mine) lg.team(g.opp(lg.userTeamId)).id else h.id)) }) {
            TeamBadge(a, 30.dp)
            Spacer(Modifier.width(6.dp))
            TeamBadge(h, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${if (lg.pro) a.name.substringAfterLast(' ') else a.school} at ${if (lg.pro) h.name.substringAfterLast(' ') else h.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (g.day == lg.day) "Today" else "Day ${g.day}", style = T.small, color = P.sub)
            }
            Tag(com.nh.hoopscoach.game.Marquee.label(lg, g).uppercase(), if (mine) P.accent else P.gold)
        }
    }
}

/** The user's coaching rivals, for the Career screen. */
@Composable
fun CoachRivalsCard(lg: League) {
    val rivals = com.nh.hoopscoach.game.CoachRivals.rivals(lg)
    if (rivals.isEmpty()) return
    Section("Coaching rivals")
    GroupList(rivals) { r ->
        val c = lg.coaches.firstOrNull { it.id == r.coachId }
        ListRow {
            Column(Modifier.weight(1f)) {
                Text(c?.name ?: "A former rival", style = T.bodyB, color = P.ink)
                Text("${lg.teams.getOrNull(c?.teamId ?: -1)?.let { if (lg.pro) it.name else it.school } ?: "Out of coaching"} · you're ${r.w}-${r.l}" + if (r.big > 0) " · ${r.big} postseason meeting${if (r.big == 1) "" else "s"}" else "",
                    style = T.small, color = P.sub)
            }
            Tag(com.nh.hoopscoach.game.CoachRivals.heatLabel(r.heat).uppercase(), if (r.heat >= 6) P.bad else P.warn)
        }
    }
}

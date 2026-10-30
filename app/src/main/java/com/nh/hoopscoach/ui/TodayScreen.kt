package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team

@Composable
fun TodayScreen(vm: GameViewModel, lg: League) {
    when (lg.phase) {
        Phase.REGULAR -> SeasonToday(vm, lg)
        Phase.CONF_TOURNEY, Phase.NCAA -> TourneyToday(vm, lg)
        Phase.WRAP_UP -> WrapUpFlow(vm, lg)
        Phase.OFFSEASON -> OffseasonFlow(vm, lg)
    }
}

fun recordLine(lg: League, t: Team): String {
    val parts = mutableListOf("${t.record}", "${t.confRecord} ${lg.conf(t.confId).abbr}")
    if (t.ap > 0) parts += "AP #${t.ap}"
    if (t.gp > 0) parts += "NET ${t.net}"
    return parts.joinToString("  ·  ")
}

@Composable
private fun SeasonToday(vm: GameViewModel, lg: League) {
    val u = lg.user
    val today = Season.userGameToday(lg) ?: Season.nextUserGame(lg)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Season ${lg.season} · Game ${(u.gp + 1).coerceAtMost(Schedule.REG_DAYS)} of ${Schedule.REG_DAYS}", u.school, recordLine(lg, u)) {
                TeamBadge(u, 46.dp)
            }
        }
        if (today != null) item { NextUp(vm, lg, today) }
        item { Form(vm, lg, u) }
        item { ConfSnapshot(vm, lg, u) }
        item { TopPlayers(vm, lg, u) }
        item { Headlines(lg) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun NextUp(vm: GameViewModel, lg: League, g: Game, title: String? = null) {
    val u = lg.user
    val opp = lg.team(g.opp(u.id))
    val site = when { g.neutral -> 0; g.home == u.id -> 1; else -> -1 }
    val p = Rankings.winProb(u.elo, opp.elo, site)
    val where = when (site) { 0 -> "Neutral site"; 1 -> "Home"; else -> "Away" }
    val kind = when (g.type) {
        GameType.CONF -> lg.conf(u.confId).abbr
        GameType.NONCONF -> "Non-conference"
        else -> g.label.substringBefore(" ·")
    }
    val seeds = (if (lg.phase == Phase.NCAA) lg.ncaa else lg.confBrackets.firstOrNull { it.confId == u.confId && lg.phase == Phase.CONF_TOURNEY })?.seeds
    Section(title ?: "Next up", action = "Scout", onAction = { vm.push(Route.TeamPage(opp.id)) })
    Panel {
        Text("$kind · $where".uppercase(), style = T.label, color = P.sub)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Side(lg, u, seeds?.get(u.id), Modifier.weight(1f)) { vm.push(Route.TeamPage(u.id)) }
            Text(if (site >= 0) "VS" else "AT", style = T.h2, color = P.faint, modifier = Modifier.padding(horizontal = 6.dp))
            Side(lg, opp, seeds?.get(opp.id), Modifier.weight(1f)) { vm.push(Route.TeamPage(opp.id)) }
        }
        Spacer(Modifier.height(16.dp))
        val mine = P.accent
        val theirs = teamInk(opp).let { if (contrast(it, mine) < 1.4f) P.faint else it }
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${(p * 100).toInt()}%", style = T.num, color = P.ink)
            Text("  win chance", style = T.small, color = P.sub, modifier = Modifier.padding(bottom = 3.dp).weight(1f))
            Text("${100 - (p * 100).toInt()}%", style = T.num, color = P.sub)
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
            Box(Modifier.weight(p.toFloat().coerceIn(0.02f, 0.98f)).height(8.dp).background(mine))
            Spacer(Modifier.width(3.dp))
            Box(Modifier.weight((1 - p).toFloat().coerceIn(0.02f, 0.98f)).height(8.dp).background(theirs))
        }
        Spacer(Modifier.height(12.dp))
        Compare("Overall", Gameplan.teamRating(lg, u), Gameplan.teamRating(lg, opp), mine, theirs)
        Compare("Offense", Gameplan.teamRating(lg, u) { Ratings.offense(it) }, Gameplan.teamRating(lg, opp) { Ratings.offense(it) }, mine, theirs)
        Compare("Defense", Gameplan.teamRating(lg, u) { Ratings.defense(it) }, Gameplan.teamRating(lg, opp) { Ratings.defense(it) }, mine, theirs)
    }
}

@Composable
private fun Side(lg: League, t: Team, seed: Int?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(t, 58.dp)
        Spacer(Modifier.height(8.dp))
        Text((seed?.let { "($it) " } ?: if (t.ap > 0) "#${t.ap} " else "") + t.school, style = T.head, color = if (t.id == lg.userTeamId) P.accent else P.ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${t.mascot} · ${t.record}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Form(vm: GameViewModel, lg: League, t: Team) {
    val recent = lg.games.filter { it.played && it.involves(t.id) }.sortedByDescending { it.day }.take(5)
    Section("Recent form", action = "Schedule", onAction = { vm.push(Route.Schedule(t.id)) })
    if (recent.isEmpty()) {
        Panel { Text("The season tips off with your next game.", style = T.body, color = P.sub) }
        return
    }
    Panel(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            recent.reversed().forEach { g ->
                val won = g.winner() == t.id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.push(Route.Box(g.id)) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ResultDot(won)
                    Spacer(Modifier.height(6.dp))
                    Text("${g.scoreFor(t.id)}-${g.scoreAgainst(t.id)}", style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
                    Text("${if (g.home == t.id || g.neutral) "" else "@"}${lg.team(g.opp(t.id)).abbr}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                }
            }
            repeat(5 - recent.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ConfSnapshot(vm: GameViewModel, lg: League, t: Team) {
    val st = Season.standings(lg, t.confId)
    val idx = st.indexOf(t)
    val shown = (st.take(4).mapIndexed { i, x -> i to x } + if (idx >= 4) listOf(idx to t) else emptyList())
    Section("${lg.conf(t.confId).name} standings", action = "All", onAction = { vm.leagueConf = t.confId; vm.leagueTab = 1; vm.select(Tab.LEAGUE) })
    GroupList(shown) { (i, x) -> StandingRow(vm, lg, i + 1, x) }
}

@Composable
fun StandingRow(vm: GameViewModel, lg: League, rank: Int, t: Team) {
    val mine = t.id == lg.userTeamId
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
        Text("$rank", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(30.dp))
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(teamLabel(t), style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.confRecord, style = T.numSm.copy(fontSize = 16.sp), color = P.ink, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
        Text(t.record, style = T.numSm.copy(fontSize = 16.sp), color = P.sub, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TopPlayers(vm: GameViewModel, lg: League, t: Team) {
    val ps = lg.rosterOf(t)
    val top = if (t.gp > 0) ps.sortedByDescending { it.stats.ppg }.take(3) else ps.sortedByDescending { it.ovr }.take(3)
    Section(if (t.gp > 0) "Top performers" else "Key players", action = "Roster", onAction = { vm.teamTab = 0; vm.select(Tab.TEAM) })
    GroupList(top, inset = 70.dp) { p -> PlayerLine(vm, lg, t, p, if (t.gp > 0) statLine(p) else "${p.pos} · ${p.cls} · ${p.style.label}") }
}

fun statLine(p: Player) = "${f1(p.stats.ppg)} pts · ${f1(p.stats.rpg)} reb · ${f1(p.stats.apg)} ast"

@Composable
fun PlayerLine(vm: GameViewModel, lg: League, t: Team?, p: Player, sub: String, trailing: (@Composable () -> Unit)? = null) {
    val team = t ?: if (p.teamId >= 0) lg.team(p.teamId) else null
    ListRow(onClick = { vm.openPlayer(p.id) }) {
        PlayerJersey(p, team, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sub, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else Ring(p.ovr, 42.dp)
    }
}

@Composable
fun PlayerJersey(p: Player, t: Team?, size: androidx.compose.ui.unit.Dp) {
    val primary = t?.let { Color(it.primary) } ?: P.faint
    val secondary = t?.let { Color(it.secondary) } ?: Color.White
    Jersey(p.number, primary, secondary, size)
}

@Composable
private fun Headlines(lg: League) {
    val items = lg.news.filter { it.season == lg.season }.takeLast(6).reversed()
    if (items.isEmpty()) return
    Section("Around the league")
    GroupList(items, inset = 34.dp) { n ->
        ListRow {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (n.mine) P.accent else P.line))
            Spacer(Modifier.width(12.dp))
            Text(n.text, style = T.body, color = if (n.mine) P.ink else P.sub)
        }
    }
}

// ---------- tournaments ----------

@Composable
private fun TourneyToday(vm: GameViewModel, lg: League) {
    val u = lg.user
    val ncaa = lg.phase == Phase.NCAA
    val b: Bracket = (if (ncaa) lg.ncaa else lg.confBrackets.firstOrNull { it.confId == u.confId }) ?: return
    val today = Season.userGameToday(lg)
    val roundName = roundTitle(b, b.current, ncaa)
    val alive = b.current < b.rounds.size && u.id in b.rounds[b.current]
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            val sub = if (ncaa) Season.regionOf(lg, u.id)?.let { "$it Region · No. ${b.seeds[u.id]} seed" } ?: "${u.school} missed the field"
            else "No. ${b.seeds[u.id] ?: "–"} seed · ${recordLine(lg, u)}"
            ScreenHeader(if (ncaa) "NCAA Tournament" else "${lg.conf(u.confId).abbr} Tournament", roundName, sub) { TeamBadge(u, 46.dp) }
        }
        item { PathStepper(b, u.id, ncaa) }
        when {
            today != null -> item { NextUp(vm, lg, today, "Your game") }
            alive -> item { Section("Your game"); Panel { Text("You have a bye this round. Sim it to see who you'll face.", style = T.body, color = P.sub) } }
            else -> item {
                Section("Your season")
                Panel { Text(if (ncaa && u.id !in b.rounds[0]) "${u.school} wasn't selected. Follow the bracket from here." else "${u.school} has been eliminated. Sim ahead to finish the season.", style = T.body, color = P.sub) }
            }
        }
        item {
            val games = (b.gameIds.getOrNull(b.current) ?: emptyList()).filter { it >= 0 }.map { lg.game(it) }
            val region = Season.regionOf(lg, u.id)
            val shown = if (ncaa && b.current < 4 && region != null) games.filter { it.label.contains(region) } else games
            Section("$roundName games", action = "Bracket", onAction = { vm.push(Route.BracketView(if (ncaa) -1 else u.confId)) })
            if (shown.isEmpty()) Panel { Text("No games left this round.", style = T.body, color = P.sub) }
            GroupList(shown.take(8)) { g -> MatchRow(vm, lg, b, g) }
        }
        item { Headlines(lg) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

fun roundTitle(b: Bracket, r: Int, ncaa: Boolean): String = if (ncaa) when (r) {
    0 -> "First Round"; 1 -> "Second Round"; 2 -> "Sweet 16"; 3 -> "Elite Eight"; 4 -> "Final Four"; 5 -> "Championship"; else -> "Champions"
} else when (b.roundNames.getOrNull(r)) { "R1" -> "First Round"; "QF" -> "Quarterfinals"; "SF" -> "Semifinals"; "Final" -> "Championship"; else -> "Champions" }

@Composable
fun PathStepper(b: Bracket, teamId: Int, ncaa: Boolean) {
    Section("Road to the title")
    Panel(padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            b.roundNames.forEachIndexed { i, name ->
                val reached = b.rounds.getOrNull(i)?.contains(teamId) == true
                val advanced = b.rounds.getOrNull(i + 1)?.contains(teamId) == true || (i == b.roundNames.lastIndex && b.champion == teamId)
                val current = i == b.current && reached
                val lost = reached && !advanced && i < b.current
                val color = when { advanced -> P.good; lost -> P.bad; current -> P.accent; else -> P.line }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (advanced || current) color.copy(alpha = 0.15f) else Color.Transparent).border(2.dp, color, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (advanced) "✓" else if (lost) "×" else "${i + 1}", style = T.label, color = color)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(if (ncaa) name else name, style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = if (current) P.accent else P.sub, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun MatchRow(vm: GameViewModel, lg: League, b: Bracket, g: Game) {
    val h = lg.team(g.home)
    val a = lg.team(g.away)
    ListRow(onClick = { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(if (h.id == lg.userTeamId) a.id else h.id)) }) {
        Column(Modifier.weight(1f)) {
            MatchTeam(lg, b, h, g)
            Spacer(Modifier.height(6.dp))
            MatchTeam(lg, b, a, g)
        }
        Chevron()
    }
}

@Composable
private fun MatchTeam(lg: League, b: Bracket, t: Team, g: Game) {
    val won = g.played && g.winner() == t.id
    val lost = g.played && !won
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${b.seeds[t.id] ?: ""}", style = T.small, color = P.faint, modifier = Modifier.width(24.dp))
        TeamBadge(t, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(t.school, style = T.bodyB, color = when { t.id == lg.userTeamId -> P.accent; lost -> P.faint; else -> P.ink }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (g.played) Text("${g.scoreFor(t.id)}", style = T.numSm.copy(fontSize = 16.sp), color = if (won) P.ink else P.faint)
    }
}

@Suppress("unused")
private val keepArrangement = Arrangement.Center

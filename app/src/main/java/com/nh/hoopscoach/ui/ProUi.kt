package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.PRO_YEAR
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Rival
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Series
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Summer
import com.nh.hoopscoach.game.Team

// ---------- small helpers ----------

/** Planned minutes: the plan is written per 40, and a pro game runs 48. */
fun planMin(lg: League, t: Team, pid: Int): Int {
    val m = com.nh.hoopscoach.game.Gameplan.plannedMinutes(t, pid)
    return if (lg.pro) (m * 1.2).toInt() else m
}

/** Where an MVP-race rank puts him. */
fun proPace(rank: Int) = when {
    rank == 0 -> "Not ranked yet"
    rank == 1 -> "MVP pace"
    rank <= 5 -> "All-League First Team pace"
    rank <= 10 -> "All-League Second Team pace"
    rank <= 15 -> "All-League Third Team pace"
    rank <= 24 -> "All-Star pace"
    else -> "Outside the award races"
}

private fun proPhase(lg: League) = when (lg.phase) {
    Phase.REGULAR -> if (lg.day == ProLeague.ALL_STAR_DAY) "All-Star break" else "Game ${(lg.user.gp + 1).coerceAtMost(ProLeague.GAMES)} of ${ProLeague.GAMES}"
    Phase.PLAYOFFS -> "Playoffs"
    else -> "Offseason"
}

private fun seedText(lg: League, t: Team): String {
    val s = ProSeason.seed(lg, t.id)
    if (s > 0) return "No. $s seed, ${lg.conf(t.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }}"
    val pos = ProSeason.standings(lg, t.confId).indexOfFirst { it.id == t.id } + 1
    return "${ordinal(pos)} in the ${lg.conf(t.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }}"
}

/** The series the user's team is in right now (or the last one it played). */
private fun userSeries(lg: League): Series? = lg.proState?.series?.filter { it.involves(lg.userTeamId) }?.maxByOrNull { it.round }

private fun seriesLine(lg: League, s: Series, from: Int): String {
    val mine = if (s.hi == from) s.hiWins else s.loWins
    val theirs = if (s.hi == from) s.loWins else s.hiWins
    val opp = lg.team(if (s.hi == from) s.lo else s.hi)
    return when {
        s.done && s.winner == from -> "Won $mine-$theirs over the ${opp.name}"
        s.done -> "Lost $mine-$theirs to the ${opp.name}"
        mine == theirs -> "Tied $mine-$theirs with the ${opp.name}"
        mine > theirs -> "Lead the ${opp.name} $mine-$theirs"
        else -> "Trail the ${opp.name} $mine-$theirs"
    }
}

private fun teamLine(lg: League, t: Team) = "${t.w}-${t.l} · ${seedText(lg, t)}"

// ---------- Today ----------

@Composable
fun ProTodayScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    if (r.ended) { ProEnding(vm, lg); return }
    if (lg.phase == Phase.OFFSEASON) { ProOffseasonFlow(vm, lg); return }
    val u = lg.user
    val ps = lg.proState
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("${lg.season} · ${proPhase(lg)}", u.name, teamLine(lg, u)) { TeamBadge(u, 50.dp) }
        }
        if (r.tradedSeason == lg.season && r.tradeNote.isNotEmpty() && lg.phase == Phase.REGULAR && lg.day - r.tradedDay in 0..7) item {
            Section("Trade")
            Panel(color = P.hero) {
                Text("TRADED", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text(u.name.uppercase(), style = T.hero.copy(fontSize = 28.sp), color = P.onHero)
                Text(r.tradeNote, style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        item { HeroTodayCard(vm, lg) }
        if (r.allTimeNote.isNotEmpty() && r.allTimeSeason == lg.season && lg.day - r.allTimeDay in 0..5 && lg.phase != Phase.OFFSEASON) item {
            Section("All-time list", action = "See it", onAction = { vm.select(Tab.CAREER) })
            Panel(color = P.hero) {
                Text("ALL-TIME LIST", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text("NO. ${r.allTimeRank}", style = T.hero.copy(fontSize = 30.sp), color = P.onHero)
                Text(r.allTimeNote, style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        if (r.press != null) item { PressCard(vm, lg) }
        if (r.suitors.isNotEmpty()) item {
            Section("Trade request")
            Panel(onClick = { vm.select(Tab.RECRUIT) }) {
                Text("${r.suitors.size} teams want you. Pick one on the Me tab.", style = T.bodyB, color = P.ink)
                Text("If you don't choose by the deadline, the front office takes the first offer.", style = T.small, color = P.sub)
            }
        }
        if (lg.phase == Phase.REGULAR && lg.day in (com.nh.hoopscoach.game.Trades.DEADLINE - 10) until com.nh.hoopscoach.game.Trades.DEADLINE) item {
            val left = com.nh.hoopscoach.game.Trades.DEADLINE - lg.day
            val mood = com.nh.hoopscoach.game.Trades.mood(lg)
            Section("Trade deadline", action = "Me", onAction = { vm.select(Tab.RECRUIT) })
            Panel {
                Text(if (left == 1) "Tomorrow" else "In $left days", style = T.head, color = P.ink)
                Text("You're ${mood.label.lowercase()}." + if (com.nh.hoopscoach.game.Trades.canRequest(lg)) " You can still ask for a trade." else "", style = T.small, color = P.sub)
            }
        }
        if (ps != null && ps.allStarSeason == lg.season && ps.allStarNote.isNotEmpty() && lg.phase == Phase.REGULAR && lg.day <= ProLeague.ALL_STAR_DAY + 7) item {
            Section("All-Star Weekend")
            Panel(color = if (lg.heroId in ps.allStars) P.hero else P.surface) {
                Text(ps.allStarNote, style = T.body, color = if (lg.heroId in ps.allStars) P.onHero else P.ink)
            }
        }
        if (lg.phase == Phase.PLAYOFFS) item {
            Section("Playoffs", action = "Bracket", onAction = { vm.leagueTab = 1; vm.select(Tab.LEAGUE) })
            val s = userSeries(lg)
            Panel {
                if (s == null) Text("The ${u.name} missed the playoffs. The postseason goes on without you.", style = T.body, color = P.sub)
                else {
                    Text(ProSeason.roundName(s).uppercase(), style = T.label, color = P.accent)
                    Text(seriesLine(lg, s, u.id), style = T.head, color = P.ink)
                    if (s.done && s.winner != u.id) Text("Your season is over. Sim ahead to see who wins it all.", style = T.small, color = P.sub)
                }
            }
        }
        Season.userGameToday(lg)?.let { g ->
            item {
                val opp = lg.team(g.opp(u.id))
                Section("Next up")
                Panel {
                    Text((if (g.label.isNotEmpty()) g.label else if (g.home == u.id) "Home" else "Away").uppercase(), style = T.label, color = P.accent)
                    val stakes = com.nh.hoopscoach.game.BigNights.stakes(lg, g)
                    if (stakes.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag(stakes.uppercase(), P.bad, filled = true)
                            Spacer(Modifier.width(8.dp))
                            Text("A big night: more big moments, and they start earlier.", style = T.small, color = P.sub)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MatchSide(lg, u, Modifier.weight(1f))
                        Text(if (g.home == u.id) "VS" else "AT", style = T.h2, color = P.faint)
                        MatchSide(lg, opp, Modifier.weight(1f))
                    }
                    Rival.active(lg)?.takeIf { Rival.isDuel(lg, g) }?.let { rv ->
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Tag("RIVAL GAME", P.bad)
                            Spacer(Modifier.width(8.dp))
                            Text("${rv.name} is on the other side.", style = T.small, color = P.ink)
                        }
                    }
                }
            }
        }
        val recent = lg.games.filter { it.played && it.involves(u.id) }.sortedByDescending { it.day }.take(5)
        if (recent.isNotEmpty()) item {
            Section("Recent")
            GroupList(recent, inset = 52.dp) { g ->
                val won = g.winner() == u.id
                ListRow(onClick = { vm.push(Route.Box(g.id)) }) {
                    ResultDot(won)
                    Spacer(Modifier.width(12.dp))
                    Text("${g.site(u.id)} ${lg.team(g.opp(u.id)).name}", style = T.body, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)}", style = T.numSm, color = P.ink)
                }
            }
        }
        item {
            Section("${lg.conf(u.confId).name}", action = "Standings", onAction = { vm.leagueTab = 0; vm.select(Tab.LEAGUE) })
            val st = ProSeason.standings(lg, u.confId)
            val idx = st.indexOfFirst { it.id == u.id }
            val show = (st.take(6) + listOfNotNull(st.getOrNull(idx))).distinct()
            GroupList(show) { t -> StandingRow(vm, lg, st.indexOf(t) + 1, t, st.first()) }
        }
        val news = lg.news.filter { it.season >= lg.season - 1 }.takeLast(6).reversed()
        if (news.isNotEmpty()) item {
            Section("Around the league")
            GroupList(news) { n -> Text(n.text, style = if (n.mine) T.bodyB else T.body, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun MatchSide(lg: League, t: Team, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(t, 56.dp)
        Spacer(Modifier.height(6.dp))
        Text(t.school, style = T.bodyB, color = if (t.id == lg.userTeamId) P.accent else P.ink, maxLines = 1)
        Text("${t.mascot} · ${t.w}-${t.l}", style = T.small, color = P.sub, maxLines = 1)
    }
}

@Composable
private fun StandingRow(vm: GameViewModel, lg: League, rank: Int, t: Team, leader: Team) {
    val mine = t.id == lg.userTeamId
    val gb = ((leader.w - t.w) + (t.l - leader.l)) / 2.0
    ListRow(modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
        Text("$rank", style = T.numSm, color = if (rank <= 8) P.accent else P.faint, modifier = Modifier.width(28.dp))
        TeamBadge(t, 32.dp)
        Spacer(Modifier.width(10.dp))
        Text(t.name, style = if (mine) T.bodyB else T.body, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${t.w}-${t.l}", style = T.numSm, color = P.ink, modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
        Text(if (gb <= 0) "—" else if (gb % 1.0 == 0.0) "${gb.toInt()}" else "%.1f".format(gb), style = T.small, color = P.sub, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
    }
}

// ---------- the bottom bar ----------

@Composable
fun ProContinueBar(vm: GameViewModel, lg: League) {
    val u = lg.user
    val game = Season.userGameToday(lg)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp)).background(P.surface).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (lg.phase == Phase.OFFSEASON || lg.road?.ended == true) {
            Column(Modifier.weight(1f)) {
                Text(if (lg.road?.ended == true) "CAREER COMPLETE" else "OFFSEASON", style = T.label, color = P.accent)
                Text(if (lg.road?.ended == true) "See how it ended" else "Pick up where you left off", style = T.bodyB, color = P.ink, maxLines = 1)
            }
            SmallButton("Continue", primary = true) { vm.select(Tab.TODAY) }
            return@Row
        }
        Column(Modifier.weight(1f)) {
            if (game != null) {
                val opp = lg.team(game.opp(u.id))
                val tag = if (game.type == com.nh.hoopscoach.game.GameType.PLAYOFF) game.label.uppercase() else "GAME ${u.gp + 1} · ${if (game.home == u.id) "HOME" else "AWAY"}"
                Text(tag, style = T.label, color = P.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${if (game.home == u.id) "vs" else "at"} ${opp.name}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else {
                Text(proPhase(lg).uppercase(), style = T.label, color = P.accent, maxLines = 1)
                Text(if (lg.phase == Phase.PLAYOFFS && userSeries(lg)?.let { it.done && it.winner != u.id } != false) "Your season is over" else "No game today", style = T.bodyB, color = P.ink, maxLines = 1)
            }
        }
        Box {
            Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                Icon(Icons.Default.MoreVert, "More sim options", tint = P.sub)
            }
            DropdownMenu(menu, { menu = false }, Modifier.background(P.surface)) {
                @Composable
                fun item(label: String, a: () -> Unit) = DropdownMenuItem(text = { Text(label, style = T.body, color = P.ink) }, onClick = { menu = false; a() })
                if (lg.phase == Phase.REGULAR) {
                    item("Sim one week") { vm.simDays(7) }
                    if (lg.day < ProLeague.ALL_STAR_DAY) item("Sim to the All-Star break") { vm.simUntil("Simulating to the All-Star break") { it.day >= ProLeague.ALL_STAR_DAY } }
                    item("Sim to the playoffs") { vm.simToPhase(Phase.PLAYOFFS, "Simulating the regular season") }
                }
                item("Sim to the end of the season") { vm.simUntil("Simulating the rest of the season") { it.phase == Phase.OFFSEASON } }
            }
        }
        Spacer(Modifier.width(4.dp))
        if (game != null) {
            SmallButton("Sim") { vm.simDay() }
            Spacer(Modifier.width(6.dp))
            SmallButton("Play", primary = true) { vm.startLive() }
        } else SmallButton("Sim day", primary = true) { vm.simDay() }
    }
}

// ---------- Team ----------

@Composable
fun ProTeamScreen(vm: GameViewModel, lg: League) {
    val u = lg.user
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("${lg.season} · ${lg.conf(u.confId).name}", u.name, "${teamLine(lg, u)} · Coach ${lg.coachOf(u).last}") { TeamBadge(u, 50.dp) }
            Segmented(listOf("Roster", "Schedule"), vm.teamTab.coerceIn(0, 1), { vm.teamTab = it })
            Spacer(Modifier.height(8.dp))
        }
        if (vm.teamTab != 1) {
            val plan = u.plan.slots
            val starters = plan.map { it.starter }.toSet()
            val roster = lg.rosterOf(u).sortedWith(compareByDescending<Player> { it.id in starters }.thenByDescending { com.nh.hoopscoach.game.Gameplan.plannedMinutes(u, it.id) }.thenByDescending { it.ovr })
            item {
                Section("Roster · ${roster.size} players")
                GroupList(roster, inset = 66.dp) { p ->
                    val me = p.id == lg.heroId
                    ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (me) Modifier.background(P.accentSoft) else Modifier) {
                        PlayerJersey(p, u, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (me) "${p.name} (you)" else p.name, style = T.bodyB, color = if (me) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val mins = planMin(lg, u, p.id)
                            Text("${p.pos} · age ${p.age} · ${if (p.id in starters) "Starter" else if (mins > 0) "$mins min" else "Bench"}" +
                                if (p.stats.gp > 0) " · ${f1(p.stats.ppg)} ppg" else "", style = T.small, color = P.sub, maxLines = 1)
                        }
                        Ring(p.ovr, 40.dp)
                    }
                }
            }
        } else {
            val games = lg.games.filter { it.involves(u.id) }.sortedBy { it.day }
            item {
                Section("Schedule · ${games.size} games")
                GroupList(games, inset = 52.dp) { g ->
                    val opp = lg.team(g.opp(u.id))
                    ListRow(onClick = if (g.played) ({ vm.push(Route.Box(g.id)) }) else null) {
                        if (g.played) ResultDot(g.winner() == u.id) else Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) { Text("${g.day}", style = T.small, color = P.faint) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${g.site(u.id)} ${opp.name}", style = T.body, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (g.label.isNotEmpty()) Text(g.label, style = T.small, color = P.accent)
                        }
                        Text(if (g.played) "${g.scoreFor(u.id)}-${g.scoreAgainst(u.id)}" else "${opp.w}-${opp.l}", style = T.numSm, color = if (g.played) P.ink else P.faint)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

// ---------- League ----------

@Composable
fun ProLeagueScreen(vm: GameViewModel, lg: League) {
    val tab = vm.leagueTab.coerceIn(0, 3)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("${lg.season} · ${proPhase(lg)}", "The League", null)
            Segmented(listOf("Standings", "Playoffs", "Leaders", "History"), tab, { vm.leagueTab = it }, scroll = false)
            Spacer(Modifier.height(8.dp))
        }
        when (tab) {
            0 -> for (c in 0..1) item {
                val st = ProSeason.standings(lg, c)
                Section(lg.conf(c).name)
                GroupList(st) { t -> StandingRow(vm, lg, st.indexOf(t) + 1, t, st.first()) }
                Text("Top eight make the playoffs.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
            }
            1 -> playoffs(vm, lg)
            2 -> leaders(vm, lg)
            else -> history(lg)
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

private fun LazyListScope.playoffs(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    if (ps.series.isEmpty()) {
        item {
            Panel { Text("The playoffs start after game ${ProLeague.GAMES}: the top eight in each conference, best of seven all the way.", style = T.body, color = P.sub) }
            for (c in 0..1) {
                Section("${lg.conf(c).abbr} if the season ended today")
                val st = ProSeason.standings(lg, c).take(8)
                GroupList(listOf(0 to 7, 3 to 4, 2 to 5, 1 to 6)) { (a, b) ->
                    Text("${a + 1} ${st.getOrNull(a)?.name ?: ""}  vs  ${b + 1} ${st.getOrNull(b)?.name ?: ""}", style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
                }
            }
        }
        return
    }
    if (ps.champion >= 0) item {
        Panel(color = P.hero) {
            Text("CHAMPIONS", style = T.label, color = P.onHero.copy(alpha = 0.8f))
            Text(lg.team(ps.champion).name.uppercase(), style = T.hero.copy(fontSize = 30.sp), color = P.onHero)
        }
    }
    for (round in 4 downTo 1) {
        val list = ps.series.filter { it.round == round }.sortedWith(compareBy<Series> { it.conf }.thenBy { it.slot })
        if (list.isEmpty()) continue
        item {
            Section(ProLeague.ROUND_NAMES[round - 1])
            GroupList(list) { s -> SeriesRow(lg, s) }
        }
    }
}

@Composable
private fun SeriesRow(lg: League, s: Series) {
    val mine = s.involves(lg.userTeamId)
    Column(Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        for ((tid, wins) in listOf(s.hi to s.hiWins, s.lo to s.loWins)) {
            val t = lg.team(tid)
            val won = s.winner == tid
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text("${ProSeason.seed(lg, tid).takeIf { it > 0 } ?: ""}", style = T.small, color = P.faint, modifier = Modifier.width(22.dp))
                TeamBadge(t, 26.dp)
                Spacer(Modifier.width(8.dp))
                Text(t.name, style = if (won) T.bodyB else T.body, color = if (s.done && !won) P.faint else P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                Text("$wins", style = T.numSm, color = if (won) P.accent else P.ink)
            }
        }
    }
}

private fun LazyListScope.leaders(vm: GameViewModel, lg: League) {
    val pool = lg.teams.flatMap { t -> lg.rosterOf(t).filter { it.stats.gp > 0 && it.stats.gp * 2 >= t.gp }.map { it to t } }
    item {
        val ladder = Awards.ladder(lg).take(10)
        Section("MVP race")
        if (ladder.isEmpty()) Panel { Text("The race starts once games are played.", style = T.body, color = P.sub) }
        else GroupList(ladder.withIndex().toList()) { (i, pt) -> val s = pt.first.stats; LeaderRow(vm, lg, i + 1, pt.first, pt.second, "${f1(s.ppg)} · ${f1(s.rpg)} · ${f1(s.apg)}") }
        if (ladder.isNotEmpty()) Text("Points · rebounds · assists a game", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
    for ((label, f) in listOf<Pair<String, (StatLine) -> Double>>("Points" to { it.ppg }, "Rebounds" to { it.rpg }, "Assists" to { it.apg })) item {
        Section(label)
        val top = pool.sortedByDescending { f(it.first.stats) }.take(5)
        if (top.isEmpty()) Panel { Text("No games yet.", style = T.body, color = P.sub) }
        else GroupList(top.withIndex().toList()) { (i, pt) -> LeaderRow(vm, lg, i + 1, pt.first, pt.second, "%.1f".format(f(pt.first.stats))) }
    }
}

@Composable
private fun LeaderRow(vm: GameViewModel, lg: League, rank: Int, p: Player, t: Team, value: String) {
    val me = p.id == lg.heroId
    ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (me) Modifier.background(P.accentSoft) else Modifier) {
        Text("$rank", style = T.numSm, color = P.accent, modifier = Modifier.width(28.dp))
        PlayerJersey(p, t, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = if (me) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.name} · ${p.pos}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, style = if (value.length > 6) T.small else T.numSm, color = P.ink, textAlign = TextAlign.End, modifier = Modifier.widthIn(value))
    }
}

private fun Modifier.widthIn(v: String) = if (v.length > 6) this.width(118.dp) else this

private fun LazyListScope.history(lg: League) {
    val ps = lg.proState ?: return
    val recent = ps.trades.filter { it.season >= lg.season - 1 }.takeLast(15).reversed()
    if (recent.isNotEmpty()) item {
        Section("Trades")
        GroupList(recent) { t -> TradeRow(lg, t) }
    }
    if (lg.seasonAwards.isNotEmpty()) item {
        Section("${lg.season} awards")
        GroupList(lg.seasonAwards) { a ->
            Column(Modifier.fillMaxWidth().then(if (a.pid == lg.heroId) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(a.award.uppercase(), style = T.label, color = P.accent)
                Text("${a.name} · ${lg.team(a.teamId).name}", style = T.bodyB, color = P.ink)
                Text(a.line, style = T.small, color = P.sub)
            }
        }
    }
    if (ps.allStars.isNotEmpty() && ps.allStarSeason == lg.season) item {
        Section("${lg.season} All-Stars")
        Panel { Text(ps.allStars.mapNotNull { lg.playerOrNull(it)?.name }.joinToString(", "), style = T.small, color = P.ink) }
    }
    item {
        Section("Champions")
        if (ps.history.isEmpty()) Panel { Text("No titles decided yet.", style = T.body, color = P.sub) }
        else GroupList(ps.history.reversed()) { h ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text("${h.season} · ${lg.team(h.champion).name}", style = T.bodyB, color = P.ink)
                Text("Over the ${lg.team(h.runnerUp).name} · Finals MVP ${h.finalsMvp} · MVP ${h.mvp} · Rookie of the Year ${h.roy}", style = T.small, color = P.sub)
                if (h.heroResult.isNotEmpty()) Text("You: ${h.heroResult}", style = T.small, color = P.accent)
            }
        }
    }
}

// ---------- Me ----------

@Composable
fun ProHeroScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId)
    if (h == null || h.teamId < 0) { ProEnding(vm, lg); return }
    val t = lg.team(h.teamId)
    val c = r.contract
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Pro career · year ${h.proYears + 1}", h.last, "${h.pos} · age ${h.age} · ${t.name}") { PlayerJersey(h, t, 50.dp) }
            Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(h.ovr, 54.dp, label = "OVR", textSize = 19.sp)
                    Spacer(Modifier.width(8.dp))
                    Ring(h.pot, 54.dp, label = "POT", textSize = 19.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("CONTRACT", style = T.label, color = P.sub)
                        if (c != null) {
                            Text("${money(c.salary)} a season", style = T.head, color = P.accent)
                            Text("${c.kind} · ${if (c.years <= 0) "expiring" else "${c.years} ${if (c.years == 1) "year" else "years"} left"}" + if (c.option) " · player option" else "", style = T.small, color = P.sub)
                        } else Text("Free agent", style = T.head, color = P.accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
                StatRow(listOf(
                    Triple("RINGS", "${r.rings}", if (r.rings > 0) P.accent else null), Triple("ALL-STAR", "${r.allStars}", null),
                    Triple("MVP", "${r.mvps}", null), Triple("BANK", money(Road.bank(lg)), P.good),
                ), style = T.numSm)
            }
        }
        if (r.press != null) item { PressCard(vm, lg) }
        item { SituationPanel(vm, lg) }
        item { ContenderPanel(vm, lg) }
        item { RacePanel(vm, lg) }
        item { AllTimePanel(vm, lg) }
        item { RivalPanel(vm, lg) }
        item {
            val mins = com.nh.hoopscoach.game.Gameplan.plannedMinutes(t, h.id)
            Section("Your role")
            Panel {
                Text(Road.role(lg).uppercase(), style = T.label, color = P.accent)
                Text(if (mins > 0) "${planMin(lg, t, h.id)} minutes a game in the plan" else "Out of the rotation", style = T.head, color = P.ink)
                Text("Coach ${lg.coachOf(t).last} plays his best. Outplay the man ahead of you to start.", style = T.small, color = P.sub)
                if (h.stats.gp > 0) {
                    Spacer(Modifier.height(10.dp))
                    StatRow(listOf(
                        Triple("PPG", f1(h.stats.ppg), null), Triple("RPG", f1(h.stats.rpg), null),
                        Triple("APG", f1(h.stats.apg), null), Triple("MPG", f1(h.stats.mpg), null),
                    ), style = T.numSm)
                }
            }
            Section("Depth chart · ${h.pos}")
            GroupList(Road.depth(lg), inset = 64.dp) { p ->
                ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == h.id) Modifier.background(P.accentSoft) else Modifier) {
                    PlayerJersey(p, t, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.id == h.id) "${p.name} (you)" else p.name, style = T.bodyB, color = if (p.id == h.id) P.accent else P.ink, maxLines = 1)
                        Text("Age ${p.age} · ${planMin(lg, t, p.id)} min planned", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 40.dp)
                }
            }
        }
        item { StaffPanel(vm, lg) }
        item { PracticePanel(vm, lg) }
        item { MediaPanel(lg) }
        item {
            Section("Development")
            Panel {
                Text("Training focus: ${h.focus.label}", style = T.bodyB, color = P.ink)
                Text(if (h.age >= 30) "From 30 on, summers take a little off. A personal trainer slows it down." else "Every level-up raises its three ratings. You grow until you reach your potential.", style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Bar(h.xp / com.nh.hoopscoach.game.Ratings.xpNeeded().toFloat(), Modifier.fillMaxWidth(), height = 6.dp)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Training focus", Modifier.weight(1f), primary = true) { vm.push(Route.Focus(h.id)) }
                    SmallButton("Player card", Modifier.weight(1f)) { vm.openPlayer(h.id) }
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// ---------- playoff reel, records book, building a contender ----------

@Composable
private fun ReelRow(e: com.nh.hoopscoach.game.ReelEntry) {
    Column(Modifier.fillMaxWidth().then(if (e.big) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text("${e.season} · ${e.title}".uppercase(), style = T.label, color = if (e.big) P.accent else P.sub)
        Text(e.line, style = T.small, color = P.ink)
    }
}

private fun LazyListScope.reelSection(lg: League, limit: Int?) {
    val r = lg.road ?: return
    if (r.reel.isEmpty()) return
    item {
        Section("Playoff highlights")
        val list = r.reel.sortedWith(compareByDescending<com.nh.hoopscoach.game.ReelEntry> { it.big }.thenByDescending { it.season }).let { if (limit != null) it.take(limit) else it.take(20) }
        GroupList(list) { e -> ReelRow(e) }
    }
}

private fun LazyListScope.recordsBook(lg: League) {
    val book = lg.proState?.records ?: return
    if (book.isEmpty()) return
    val rb = com.nh.hoopscoach.game.RecordBook
    for (scope in listOf("Game", "Season", "Career")) item {
        Section(when (scope) { "Game" -> "Records book · single game"; "Season" -> "Records book · season"; else -> "Records book · career" })
        GroupList(book.filter { it.scope == scope }) { rec ->
            val mine = rb.mine(lg, rec)
            Column(Modifier.fillMaxWidth().then(if (rec.mine) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rec.label, style = T.bodyB, color = if (rec.mine) P.accent else P.ink)
                        Text("${rec.holder}, ${rec.season}", style = T.small, color = P.sub)
                    }
                    Text(rb.format(rec, rec.value), style = T.numSm, color = P.ink)
                }
                if (!rec.mine && mine > 0) {
                    Spacer(Modifier.height(4.dp))
                    Bar((mine / rec.value).toFloat().coerceIn(0f, 1f), Modifier.fillMaxWidth(), height = 4.dp)
                    Text("You: ${rb.format(rec, mine)}", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                }
            }
        }
        if (scope == "Career") Text("Career totals count each 58-game season as a full 82. Breaking a record adds 3 legacy points.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
}

/** Recruit a star: pitch a free agent in the summer, or push for a trade before the deadline. Shows title odds either way. */
@Composable
internal fun ContenderPanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    if (h.teamId < 0) return
    val sr = com.nh.hoopscoach.game.StarRecruit
    val pitch = sr.canPitch(lg)
    val push = sr.canPush(lg)
    val targets = remember(vm.rev, lg) {
        if (pitch) lg.proState!!.freeStars.mapNotNull { lg.playerOrNull(it) } else if (push) sr.tradeTargets(lg) else emptyList()
    }
    val odds = remember(vm.rev, lg) { sr.odds(lg)[lg.userTeamId] ?: 0.0 }
    if (targets.isEmpty() && r.recruitNote.isEmpty()) return
    Section("Build a contender")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("TITLE ODDS", style = T.label, color = P.sub)
                Text("%.0f%%".format(odds * 100), style = T.title.copy(fontSize = 28.sp), color = P.accent)
            }
            Text(if (pitch) "Pitch one star\na summer" else if (push) "One push\nbefore the deadline" else "", style = T.small, color = P.sub, textAlign = TextAlign.End)
        }
        if (r.recruitNote.isNotEmpty()) Text(r.recruitNote, style = T.small, color = P.accent, modifier = Modifier.padding(top = 6.dp))
        for (p in targets) {
            val t = lg.team(p.teamId)
            val after = remember(vm.rev, p.id) { sr.oddsWith(lg, p) }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, t, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${p.name} · ${p.ovr}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.pos} · age ${p.age} · ${t.name}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("With him: %.0f%% title odds".format(after * 100) + if (pitch) " · %.0f%% he says yes".format(sr.chance(lg, p) * 100) else "", style = T.small, color = P.good)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(if (pitch) "Pitch" else "Push", primary = true) {
                    vm.act { val msg = if (pitch) sr.pitch(it, p.id) else sr.push(it, p.id); if (msg.isNotEmpty()) vm.toast(msg) }
                }
            }
        }
    }
}

// ---------- the all-time list ----------

/** Where he stands among the greats, and who's next. */
@Composable
internal fun AllTimePanel(vm: GameViewModel, lg: League) {
    val h = lg.hero ?: return
    val at = com.nh.hoopscoach.game.AllTime
    val score = remember(vm.rev, lg) { at.heroScore(lg) }
    val rank = remember(vm.rev, lg) { at.rank(lg) }
    val next = remember(vm.rev, lg) { at.next(lg) }
    Section("All-time list", action = "See it", onAction = { vm.select(Tab.CAREER) })
    Panel(onClick = { vm.select(Tab.CAREER) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ALL-TIME RANK", style = T.label, color = P.sub)
                Text(if (rank > 0) "No. $rank" else "Not on the list yet", style = T.title.copy(fontSize = if (rank > 0) 30.sp else 22.sp), color = P.accent)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${at.points(score)}", style = T.num, color = P.ink)
                Text("LEGACY POINTS", style = T.label.copy(fontSize = 9.sp), color = P.sub)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            when {
                rank == 0 -> "${at.points(at.FLOOR - score)} more points to crack the list."
                next != null -> "Next: ${next.name} (${at.points(next.score)}), ${at.points(next.score - score)} points away."
                else -> "Nobody is above you. The greatest of all time."
            },
            style = T.small, color = P.ink,
        )
        val count = { a: String -> h.awards.count { it.substringAfter(" ") == a } }
        Text(
            "Rings ${count("Champion")} · MVP ${count("MVP")} · Finals MVP ${count("Finals MVP")} · All-League ${count("All-League 1st") + count("All-League 2nd") + count("All-League 3rd")} · All-Star ${count("All-Star")}",
            style = T.small, color = P.sub,
        )
        Text("MVP 8 · Finals MVP 6 · ring 5 · All-League 3, 1.5 or 1 · All-Star 1 · 0.6 per 1,000 points", style = T.small.copy(fontSize = 11.sp), color = P.faint)
    }
}

/** The list itself: the top 15, and the names around him. */
private fun LazyListScope.allTimeList(vm: GameViewModel, lg: League) {
    val l = com.nh.hoopscoach.game.AllTime.list(lg)
    val me = l.indexOfFirst { it.status == "You" }
    val onList = com.nh.hoopscoach.game.AllTime.heroScore(lg) >= com.nh.hoopscoach.game.AllTime.FLOOR
    val show = (l.indices.take(15) + (if (me >= 0) ((me - 2).coerceAtLeast(0)..(me + 2).coerceAtMost(l.size - 1)).toList() else emptyList())).distinct().sorted()
    item {
        Section("All-time list")
        GroupList(show) { i ->
            val e = l[i]
            val you = e.status == "You"
            val gap = i > 0 && i - 1 !in show
            Column {
                if (gap) Text("···", style = T.small, color = P.faint, modifier = Modifier.padding(start = 18.dp, top = 4.dp))
                ListRow(onClick = if (e.pid >= 0 && lg.anyPlayer(e.pid) != null) ({ vm.openPlayer(e.pid) }) else null, modifier = if (you) Modifier.background(P.accentSoft) else Modifier) {
                    Text(if (you && !onList) "–" else "${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(36.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (you) "${e.name} (you)" else e.name, style = T.bodyB, color = if (you) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val team = lg.playerOrNull(e.pid)?.takeIf { it.teamId >= 0 }?.let { lg.team(it.teamId).name }
                        Text(if (you && !onList) "Not on the list yet" else listOfNotNull(e.status, team).joinToString(" · "), style = T.small, color = P.sub, maxLines = 1)
                    }
                    Text("${com.nh.hoopscoach.game.AllTime.points(e.score)}", style = T.numSm, color = P.ink)
                }
            }
        }
        Text("Scores: MVP 8 · Finals MVP 6 · ring 5 · All-League 3, 1.5 or 1 · All-Star 1 · 0.6 per 1,000 points. Today's stars carry their real careers and keep adding.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
}

// ---------- trades ----------

@Composable
private fun TradeRow(lg: League, t: com.nh.hoopscoach.game.Trade) {
    val a = lg.team(t.teamA)
    val b = lg.team(t.teamB)
    Column(Modifier.fillMaxWidth().then(if (t.hero) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
        Text(if (t.offseason) "${t.season} OFFSEASON" else "${t.season} · DAY ${t.day}", style = T.label, color = if (t.hero) P.accent else P.sub)
        for ((team, gets) in listOf(b to t.toB, a to t.toA)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                TeamBadge(team, 24.dp)
                Spacer(Modifier.width(8.dp))
                Text("${team.mascot} get ${gets.joinToString(", ")}", style = T.small, color = P.ink, maxLines = 2)
            }
        }
    }
}

/** How he feels where he is, and the trade request: ask out, see who calls, pick one or stay. */
@Composable
internal fun SituationPanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    if (h.teamId < 0) return
    val trades = com.nh.hoopscoach.game.Trades
    val mood = remember(vm.rev, lg) { trades.mood(lg) }
    Section("Your situation")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MOOD", style = T.label, color = P.sub)
                Text(mood.label, style = T.head, color = when { mood.score >= 70 -> P.good; mood.score >= 50 -> P.ink; mood.score >= 30 -> P.warn; else -> P.bad })
            }
            Text("${mood.score}", style = T.num, color = P.ink)
        }
        Spacer(Modifier.height(6.dp))
        Bar(mood.score / 100f, Modifier.fillMaxWidth(), color = when { mood.score >= 70 -> P.good; mood.score >= 50 -> P.accent; mood.score >= 30 -> P.warn; else -> P.bad })
        if (mood.reasons.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(mood.reasons.joinToString(" · "), style = T.small, color = P.sub)
        }
        if (r.suitors.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("TEAMS THAT WANT YOU", style = T.label, color = P.accent)
            for ((i, s) in r.suitors.withIndex()) {
                val st = lg.team(s.teamId)
                val back = s.sends.mapNotNull { lg.playerOrNull(it) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(st, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(st.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${st.w}-${st.l} · ${s.role} there", style = T.small, color = P.sub, maxLines = 1)
                        Text("They'd send ${back.joinToString(", ") { "${it.name} (${it.ovr})" }}", style = T.small, color = P.ink, maxLines = 2)
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("Go", primary = true) { vm.act { trades.accept(it, i) }; vm.toast("You're headed to the ${st.name}") }
                }
            }
            SmallButton("Withdraw the request", Modifier.fillMaxWidth()) { vm.act { trades.withdraw(it) } }
        } else if (trades.canRequest(lg)) {
            Spacer(Modifier.height(12.dp))
            Text(
                if (lg.phase == Phase.REGULAR) "Want out? Ask before the deadline (day ${trades.DEADLINE}). Your fans won't like it; your agent finds who's interested and you pick."
                else "Want a fresh start? Ask now and your agent finds who's interested. Your fans won't like it.",
                style = T.small, color = P.sub,
            )
            Spacer(Modifier.height(8.dp))
            SecondaryButton("Request a trade", Modifier.fillMaxWidth()) {
                vm.act { if (trades.request(it) == 0) vm.toast("Nobody made an offer. You're staying put.") }
            }
        } else if (lg.phase == Phase.REGULAR && lg.day >= trades.DEADLINE) {
            Spacer(Modifier.height(8.dp))
            Text("The trade deadline has passed. You can ask again in the offseason.", style = T.small, color = P.sub)
        }
        if (r.tradedSeason == lg.season && r.tradeNote.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(r.tradeNote, style = T.small, color = P.accent)
        }
    }
}

// ---------- Career ----------

private fun proTotals(h: Player): StatLine {
    val tot = StatLine()
    h.career.filter { it.cls == PRO_YEAR }.forEach { tot.add(it.s) }
    if (h.teamId >= 0) tot.add(h.stats)
    return tot
}

@Composable
private fun TotalsRow(label: String, tot: StatLine) {
    if (tot.gp == 0) return
    Panel(padding = PaddingValues(vertical = 12.dp, horizontal = 8.dp), modifier = Modifier.padding(top = 8.dp)) {
        Text(label.uppercase(), style = T.label, color = P.sub, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp))
        StatRow(listOf(
            Triple("GP", "${tot.gp}", null), Triple("PPG", f1(tot.ppg), null),
            Triple("RPG", f1(tot.rpg), null), Triple("APG", f1(tot.apg), null),
        ), style = T.numSm)
    }
}

@Composable
fun ProCareerScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero ?: return
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Road to the League · Pro", h.name, "${r.collegeSchools} → ${r.proTeams.joinToString(" → ") { lg.team(it).name }}") {
                IconCircle({ vm.push(Route.Settings) }) { Icon(NavIcons.Gear, "Settings", tint = P.sub, modifier = Modifier.size(22.dp)) }
            }
            Panel(color = P.hero) {
                Text("${h.proYears} PRO ${if (h.proYears == 1) "SEASON" else "SEASONS"}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text(if (r.draftPick > 0) "NO. ${r.draftPick} PICK" else "UNDRAFTED", style = T.hero.copy(fontSize = 32.sp), color = P.onHero)
                val track = if (ProCareer.hofScore(lg) >= 18) " · On a Hall of Fame track" else ""
                Text("${r.rings} ${if (r.rings == 1) "ring" else "rings"} · ${r.allStars} All-Star · ${r.mvps} MVP$track", style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        item { TotalsRow("Pro career", proTotals(h)) }
        allTimeList(vm, lg)
        reelSection(lg, null)
        recordsBook(lg)
        item { r.collegeTotals?.let { TotalsRow("College · ${r.collegeSeasons} ${if (r.collegeSeasons == 1) "season" else "seasons"}", it) } }
        if (r.proStory.isNotEmpty()) item {
            Section("The pro years")
            GroupList(r.proStory.reversed()) { line -> Text(line, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item { CareerStory(lg) }
        if (h.awards.isNotEmpty()) item {
            Section("Honors")
            GroupList(h.awards.reversed()) { a -> Text(a, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item {
            Panel(Modifier.padding(top = 14.dp)) {
                Text("Career earnings: ${money(r.earned)}", style = T.bodyB, color = P.good)
                if (r.spent > 0) Text("Invested in your own team: ${money(r.spent)}", style = T.small, color = P.ink)
                Text("${Road.count(r.followers)} followers · ${Road.personaLabel(r.persona)}", style = T.small, color = P.ink)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- offseason ----------

@Composable
fun ProOffseasonFlow(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    val t = lg.team(h.teamId)
    val rookie = h.proYears == 0 && ps.history.isEmpty()
    val steps = if (rookie) listOf("Contract", "Summer") else listOf("Review", "Draft", "Free agency", "Summer")
    val idx = if (rookie) (if (ps.step == ProStep.SUMMER) 1 else 0) else ps.step.ordinal
    val needsSigning = ps.step == ProStep.FREE_AGENCY && (r.optionPending || ((r.contract?.years ?: 0) <= 0 && r.proOffers.isNotEmpty()))
    val label = when (ps.step) {
        ProStep.REVIEW -> "Next: the draft"
        ProStep.DRAFT -> "Next: free agency"
        ProStep.FREE_AGENCY -> if (r.optionPending) "Decide on your option" else if (needsSigning) "Pick a team above" else "Next: the summer"
        ProStep.SUMMER -> "Start the ${lg.season + 1} season"
    }
    Column(Modifier.fillMaxSize()) {
        StepHeader("Offseason · ${lg.season}", steps, idx)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (ps.step) {
                ProStep.REVIEW -> reviewStep(vm, lg, h, t)
                ProStep.DRAFT -> draftStep(vm, lg, t)
                ProStep.FREE_AGENCY -> contractStep(vm, lg, h, t, rookie)
                ProStep.SUMMER -> proSummerStep(vm, lg, h)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        FlowFooter(null, label, enabled = !needsSigning) { vm.proNext() }
    }
}

private fun LazyListScope.reviewStep(vm: GameViewModel, lg: League, h: Player, t: Team) {
    val r = lg.road!!
    val ps = lg.proState!!
    item {
        val s = h.career.lastOrNull { it.season == lg.season && it.cls == PRO_YEAR }?.s ?: h.stats
        Panel(color = P.hero) {
            Text("${lg.season} · ${t.name.uppercase()}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
            Text(ProSeason.resultFor(lg, t.id).uppercase(), style = T.hero.copy(fontSize = 28.sp), color = P.onHero)
            Text("${t.w}-${t.l} · ${f1(s.ppg)} ppg, ${f1(s.rpg)} rpg, ${f1(s.apg)} apg", style = T.small, color = P.onHero.copy(alpha = 0.9f))
        }
        val mine = h.awards.filter { it.startsWith("${lg.season} ") }.map { it.removePrefix("${lg.season} ") }
        val atr = com.nh.hoopscoach.game.AllTime.rank(lg)
        if (mine.isNotEmpty() || atr > 0) {
            Section("Your honors")
            if (atr > 0) Panel(Modifier.padding(bottom = 8.dp)) {
                Text("No. $atr on the all-time list", style = T.bodyB, color = P.accent)
                com.nh.hoopscoach.game.AllTime.next(lg)?.let { n -> Text("Next up: ${n.name}, ${com.nh.hoopscoach.game.AllTime.points(n.score - com.nh.hoopscoach.game.AllTime.heroScore(lg))} points away", style = T.small, color = P.sub) }
            }
        }
        if (mine.isNotEmpty()) {
            Panel { mine.forEach { Text("★ $it", style = T.bodyB, color = P.ink) } }
        }
        val reel = r.reel.filter { it.season == lg.season }
        if (reel.isNotEmpty()) {
            Section("Playoff reel")
            GroupList(reel.reversed()) { e -> ReelRow(e) }
        }
        ps.history.lastOrNull { it.season == lg.season }?.let { s ->
            Section("Around the league")
            Panel {
                Text("Champions: ${lg.team(s.champion).name}", style = T.bodyB, color = P.ink)
                Text("Finals MVP ${s.finalsMvp} · MVP ${s.mvp} · Rookie of the Year ${s.roy}", style = T.small, color = P.sub)
            }
        }
        Section("Your contract")
        Panel {
            val c = r.contract
            Text(if (c == null || c.years <= 0) "Your deal is up. Free agency is next." else "${c.years} ${if (c.years == 1) "year" else "years"} left at ${money(c.salary)} a season.", style = T.body, color = P.ink)
            Text("Endorsements this season: ${money(ProCareer.endorsements(lg))}", style = T.small, color = P.good)
        }
        if (ProCareer.canRetire(lg)) {
            Section("Your future")
            Panel {
                Text("You're ${h.age}. You can keep going or walk away on your terms.", style = T.body, color = P.ink)
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Retire", Modifier.fillMaxWidth()) { vm.act { ProCareer.retire(it) } }
            }
        }
    }
}

private fun LazyListScope.draftStep(vm: GameViewModel, lg: League, t: Team) {
    val ps = lg.proState!!
    item {
        Panel {
            Text("DRAFT NIGHT", style = T.label, color = P.accent)
            Text("Sixty rookies join the league. The worst teams picked first.", style = T.body, color = P.ink)
        }
        val mine = ps.draftedByUser.mapNotNull { lg.playerOrNull(it) }
        if (mine.isNotEmpty()) {
            Section("The ${t.name} drafted")
            GroupList(mine, inset = 64.dp) { p ->
                ListRow(onClick = { vm.openPlayer(p.id) }) {
                    PlayerJersey(p, t, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink)
                        Text("${p.pos} · age ${p.age} · ${p.awards.lastOrNull { it.contains("Draft #") }?.substringAfter(" ") ?: ""}", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 40.dp)
                }
            }
        }
        if (ps.retiredThisSummer.isNotEmpty()) {
            Section("Retirements")
            Panel { Text(ps.retiredThisSummer.joinToString(", "), style = T.small, color = P.ink) }
        }
        val summer = ps.trades.filter { it.season == lg.season && it.offseason }.reversed()
        if (summer.isNotEmpty()) {
            Section("Trades this summer")
            GroupList(summer) { tr -> TradeRow(lg, tr) }
        }
    }
    item { SituationPanel(vm, lg) }
    item { ContenderPanel(vm, lg) }
}

private fun LazyListScope.contractStep(vm: GameViewModel, lg: League, h: Player, t: Team, rookie: Boolean) {
    val r = lg.road!!
    val c = r.contract
    item {
        if (rookie && c != null) {
            Panel(color = P.hero) {
                Text("WELCOME TO THE LEAGUE", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text(t.name.uppercase(), style = T.hero.copy(fontSize = 28.sp), color = P.onHero)
                Text("${c.kind} deal: ${c.years} years, ${money(c.salary)} a season. It lands in your bank when each season starts.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
            Section("Your new team")
            GroupList(lg.rosterOf(t).sortedByDescending { it.ovr }.take(8), inset = 64.dp) { p ->
                ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == h.id) Modifier.background(P.accentSoft) else Modifier) {
                    PlayerJersey(p, t, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.id == h.id) "${p.name} (you)" else p.name, style = T.bodyB, color = if (p.id == h.id) P.accent else P.ink, maxLines = 1)
                        Text("${p.pos} · age ${p.age}", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 40.dp)
                }
            }
            return@item
        }
        if (c != null && c.years > 0 && r.optionPending) {
            Panel(color = P.hero) {
                Text("PLAYER OPTION", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text("${money(c.salary)} for one more year", style = T.head, color = P.onHero)
                Text("Opt in and stay with the ${lg.team(c.teamId).name} for the last year of your deal, or opt out and see what the market pays. Your agent thinks you're worth about ${money(ProCareer.marketValue(lg, h))} a season.",
                    style = T.small, color = P.onHero.copy(alpha = 0.9f))
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Opt in", Modifier.weight(1f)) { vm.act { ProCareer.optIn(it) } }
                    SmallButton("Opt out", Modifier.weight(1f), primary = true) { vm.act { ProCareer.optOut(it) } }
                }
            }
            return@item
        }
        if (c != null && c.years > 0) {
            Panel {
                Text("UNDER CONTRACT", style = T.label, color = P.accent)
                Text("${money(c.salary)} a season with the ${lg.team(c.teamId).name}", style = T.head, color = P.ink)
                Text("${c.kind} · ${c.years} ${if (c.years == 1) "year" else "years"} left" + (if (c.option) ", the last one a player option." else "."), style = T.small, color = P.sub)
            }
            SituationPanel(vm, lg)
            ContenderPanel(vm, lg)
            return@item
        }
        Panel {
            Text("FREE AGENT", style = T.label, color = P.accent)
            Text("Your contract is up. Pick where you play next.", style = T.head, color = P.ink)
            Text("Your agent's read: you're worth about ${money(ProCareer.marketValue(lg, h))} a season.", style = T.small, color = P.sub)
        }
        Section("Offers")
        Text("Tap an offer to negotiate: counter, set the length, add a player option, or take less to join a contender.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
    }
    for (i in r.proOffers.indices) item(key = "offer-${r.proOffers[i].teamId}") {
        Spacer(Modifier.height(8.dp))
        OfferCard(vm, lg, i, r.proOffers[i], current = r.proOffers[i].teamId == t.id)
    }
}

/** One free-agent offer: tap to open the negotiation. */
@Composable
private fun OfferCard(vm: GameViewModel, lg: League, i: Int, o: com.nh.hoopscoach.game.ProOffer, current: Boolean) {
    val ot = lg.team(o.teamId)
    var open by remember(o.teamId) { mutableStateOf(false) }
    var years by remember(o.teamId) { mutableStateOf(o.years) }
    var option by remember(o.teamId) { mutableStateOf(false) }
    val contender = ProCareer.contender(lg, o.teamId)
    val pay = if (option && years >= 2) (o.salary * 0.95).toInt() / 10 * 10 else o.salary
    Panel(onClick = { open = !open }, color = if (open) P.accentSoft else P.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(ot, 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ot.name + if (current) " (re-sign)" else "", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${o.role} · ${ot.w}-${ot.l}" + if (contender) " · contender" else "", style = T.small, color = if (contender) P.accent else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(o.salary), style = T.numSm, color = P.good)
                Text("PER YEAR", style = T.label.copy(fontSize = 9.sp), color = P.sub)
            }
        }
        if (o.note.isNotEmpty()) Text(o.note, style = T.small, color = P.accent, modifier = Modifier.padding(top = 6.dp))
        if (!open) return@Panel
        Spacer(Modifier.height(12.dp))
        Text("LENGTH", style = T.label, color = P.sub)
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (y in 1..ProCareer.maxYears(lg)) SmallButton("$y yr", Modifier.weight(1f), primary = y == years) { years = y }
        }
        if (years >= 2) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { option = !option }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(20.dp).clip(RoundedCornerShape(5.dp)).background(if (option) P.accent else P.line), contentAlignment = Alignment.Center) {
                    if (option) Text("✓", style = T.small, color = P.onAccent)
                }
                Spacer(Modifier.width(10.dp))
                Text("Player option on the last year (5% less a season). Opt out early if you outplay the deal.", style = T.small, color = P.ink)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("${money(pay)} a season · ${money(pay * years)} over $years ${if (years == 1) "year" else "years"}", style = T.bodyB, color = P.ink)
        Spacer(Modifier.height(10.dp))
        if (!o.countered && o.salary < ProCareer.MAX_SALARY) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallButton("Ask 10% more", Modifier.weight(1f)) { vm.act { ProCareer.counter(it, i, 0.10) } }
            SmallButton("Ask 20% more", Modifier.weight(1f)) { vm.act { ProCareer.counter(it, i, 0.20) } }
        }
        if (!o.countered && o.salary < ProCareer.MAX_SALARY) Text("Asking more can raise the offer, or make them walk.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        PrimaryButton("Sign for ${money(pay)}", Modifier.fillMaxWidth()) {
            vm.act { val msg = ProCareer.signWith(it, i, years, option, false); vm.toast(msg) }
        }
        if (contender) {
            Spacer(Modifier.height(6.dp))
            SecondaryButton("Ring discount: sign for ${money((pay * 0.85).toInt() / 10 * 10)}", Modifier.fillMaxWidth()) {
                vm.act { val msg = ProCareer.signWith(it, i, years, option, true); vm.toast(msg) }
            }
            Text("They'll spend what you leave on the table on a good veteran.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

private fun LazyListScope.proSummerStep(vm: GameViewModel, lg: League, h: Player) {
    val r = lg.road!!
    val ps = lg.proState!!
    item {
        val before = ps.before[h.id]
        Panel {
            Text("SUMMER", style = T.label, color = P.accent)
            if (before != null && before != h.ovr) Text("$before → ${h.ovr} overall", style = T.title.copy(fontSize = 30.sp), color = P.ink)
            else Text("${h.ovr} overall at age ${h.age}", style = T.head, color = P.ink)
            Text(if (h.age >= 30) "Thirty-plus: the summer takes a little off the top." else if (h.ovr >= h.pot) "You're at your ceiling. Now it's about winning." else "Still climbing toward ${h.pot}.", style = T.small, color = P.sub)
        }
    }
    if (Summer.open(lg)) item {
        Section("Summer invitations")
        Text("Pick one.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
        for (ev in Summer.invites(lg)) {
            Spacer(Modifier.height(8.dp))
            SummerCard(lg, ev) { vm.act { Summer.attend(it, ev) } }
        }
    } else if (r.summer == lg.season && r.summerNote.isNotEmpty()) item {
        Section("Your summer")
        Panel(color = P.accentSoft) { Text(r.summerNote, style = T.body, color = P.ink) }
    }
}

// ---------- the end ----------

@Composable
fun ProEnding(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Road to the League", "Career complete", null)
            Panel(color = P.hero) {
                Text("RETIRED · ${ProCareer.hofVerdict(lg).uppercase()}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text("${h?.proYears ?: 0} PRO SEASONS", style = T.hero, color = P.onHero)
                Text(r.ending, style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        if (r.jerseys.isNotEmpty()) item {
            Section("Jersey night")
            for (j in r.jerseys) {
                val team = lg.teams.firstOrNull { j.startsWith("The ${it.name} ") }
                Panel(Modifier.padding(bottom = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (team != null) { TeamBadge(team, 44.dp); Spacer(Modifier.width(12.dp)) }
                        Column(Modifier.weight(1f)) {
                            Text("NO. ${h?.number ?: 0} IN THE RAFTERS", style = T.label, color = P.accent)
                            Text(j, style = T.bodyB, color = P.ink)
                        }
                    }
                }
            }
        }
        if (r.hofClass > 0) item {
            Section("Hall of Fame night")
            Panel(color = P.hero) {
                Text("CLASS OF ${r.hofClass}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text("${h?.name?.uppercase() ?: ""}", style = T.hero.copy(fontSize = 30.sp), color = P.onHero)
                Text(ProCareer.hofVerdict(lg), style = T.small, color = P.onHero.copy(alpha = 0.9f))
                Spacer(Modifier.height(10.dp))
                for (line in com.nh.hoopscoach.game.Afterward.highlights(lg)) Text("★ $line", style = T.small, color = P.onHero)
                com.nh.hoopscoach.game.Rival.player(lg)?.let { rv -> Spacer(Modifier.height(8.dp)); Text("Presented by your old rival, ${rv.name}.", style = T.small, color = P.onHero.copy(alpha = 0.85f)) }
            }
        }
        if (h != null) {
            item { TotalsRow("Pro career", proTotals(h)) }
            item { r.collegeTotals?.let { TotalsRow("College", it) } }
        }
        reelSection(lg, 8)
        if (r.proStory.isNotEmpty()) item {
            Section("The pro years")
            GroupList(r.proStory) { line -> Text(line, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item { CareerStory(lg) }
        item {
            Panel(Modifier.padding(top = 14.dp)) {
                Text("Career earnings: ${money(r.earned)}", style = T.bodyB, color = P.good)
                Text("${r.rings} ${if (r.rings == 1) "ring" else "rings"} · ${r.allStars} All-Star · ${r.mvps} MVP · ${Road.count(r.followers)} followers", style = T.small, color = P.ink)
                Text("College: ${r.collegeSchools} · Pro: ${r.proTeams.joinToString(" → ") { lg.team(it).name }}", style = T.small, color = P.sub)
            }
        }
        if (r.retired) item {
            val jobs = remember(lg) { com.nh.hoopscoach.game.Afterward.coachingJobs(lg) }
            Section("What's next: coaching")
            Text("Programs want you on the sideline. Taking a job starts a new save in the coach game, with your name and reputation.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
            GroupList(jobs, inset = 16.dp) { j ->
                ListRow(onClick = { vm.retireToCoach(j.school) }) {
                    Column(Modifier.weight(1f)) {
                        Text(j.school, style = T.bodyB, color = P.ink)
                        Text(j.why, style = T.small, color = P.sub)
                    }
                    SmallButton("Coach here", primary = true) { vm.retireToCoach(j.school) }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = Gutter)) {
                SecondaryButton("Start a new player career", Modifier.fillMaxWidth()) { vm.abandonCareer() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Between Draft Night and the pro league: one tap builds the league. */
@Composable
fun GoProScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero ?: return
    val pick = lg.draftHistory.lastOrNull { it.pid == h.id }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                ScreenHeader("Road to the League", "Going pro", null)
                Panel(color = P.hero) {
                    Text(if (pick != null) "NO. ${pick.pick} OVERALL" else "UNDRAFTED FREE AGENT", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text((pick?.proTeam ?: "Two-way deal").uppercase(), style = T.hero.copy(fontSize = 30.sp), color = P.onHero)
                    Text("${h.name} · ${h.ovr} overall. Your college career is in the books; your pro career starts now.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                }
            }
            item { CareerStory(lg) }
        }
        FlowFooter(null, "Start your pro career") { vm.goPro() }
    }
}

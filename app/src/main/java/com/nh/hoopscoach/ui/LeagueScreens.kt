package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team

@Composable
fun LeagueScreen(vm: GameViewModel, lg: League) {
    if (vm.leagueConf < 0) vm.leagueConf = lg.user.confId
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("${lg.teams.size} teams · ${lg.confs.size} conferences", "League", Season.statusLine(lg))
            Segmented(listOf("Polls", "Standings", "Leaders", "Postseason", "History"), vm.leagueTab, { vm.leagueTab = it }, scroll = true)
        }
        when (vm.leagueTab) {
            0 -> polls(vm, lg)
            1 -> standings(vm, lg)
            2 -> leaders(vm, lg)
            3 -> postseason(vm, lg)
            else -> history(vm, lg)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun RankRow(vm: GameViewModel, lg: League, rank: String, t: Team, right: String, right2: String? = null) {
    val mine = t.id == lg.userTeamId
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
        Text(rank, style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(34.dp))
        TeamBadge(t, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(lg.conf(t.confId).abbr, style = T.small.copy(fontSize = 12.sp), color = P.sub)
        }
        if (right2 != null) Text(right2, style = T.small, color = P.sub, modifier = Modifier.padding(end = 12.dp))
        Text(right, style = T.numSm.copy(fontSize = 17.sp), color = P.ink, textAlign = TextAlign.End)
    }
}

private fun LazyListScope.polls(vm: GameViewModel, lg: League) {
    val ap = lg.teams.filter { it.ap > 0 }.sortedBy { it.ap }
    item {
        Section(if (lg.user.gp == 0) "Preseason AP Top 25" else "AP Top 25")
        GroupList(ap) { t -> RankRow(vm, lg, "${t.ap}", t, t.record, "NET ${t.net}") }
        Section("NET rankings · top 50")
        GroupList(lg.teams.sortedBy { it.net }.take(50)) { t -> RankRow(vm, lg, "${t.net}", t, t.record, "PWR ${Rankings.power(t)}") }
        val u = lg.user
        if (u.net > 50) {
            Spacer(Modifier.height(10.dp))
            GroupList(listOf(u)) { t -> RankRow(vm, lg, "${t.net}", t, t.record, "PWR ${Rankings.power(t)}") }
        }
    }
}

private fun LazyListScope.standings(vm: GameViewModel, lg: League) {
    item {
        Spacer(Modifier.height(10.dp))
        Chips(lg.confs.map { it.abbr }, vm.leagueConf, { vm.leagueConf = it })
        val c = lg.conf(vm.leagueConf)
        Section(c.name, action = if (lg.confBrackets.any { it.confId == c.id }) "Tournament" else null, onAction = { vm.push(Route.BracketView(c.id)) })
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 14.dp, vertical = 4.dp)) {
            Text("TEAM", style = T.label, color = P.sub, modifier = Modifier.weight(1f))
            Text("CONF", style = T.label, color = P.sub, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
            Text("ALL", style = T.label, color = P.sub, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
        }
        GroupList(Season.standings(lg, c.id).withIndex().toList()) { (i, t) -> StandingRow(vm, lg, i + 1, t) }
    }
}

// ---------- leaders ----------

val CATS = listOf<Triple<String, (StatLine) -> Double, (Double) -> String>>(
    Triple("Points", { it.ppg }, ::f1), Triple("Rebounds", { it.rpg }, ::f1), Triple("Assists", { it.apg }, ::f1),
    Triple("Steals", { it.spg }, ::f1), Triple("Blocks", { it.bpg }, ::f1),
    Triple("FG%", { if (it.fga >= it.gp * 4) it.fgPct else 0.0 }, ::pct),
    Triple("3P%", { if (it.tpa >= it.gp * 2) it.tpPct else 0.0 }, ::pct),
    Triple("FT%", { if (it.fta >= it.gp * 2) it.ftPct else 0.0 }, ::pct),
)

fun qualified(lg: League, confId: Int?): List<Player> = lg.players.filter { p ->
    p.teamId >= 0 && (confId == null || lg.team(p.teamId).confId == confId) && p.stats.gp >= (lg.team(p.teamId).gp * 0.5).coerceAtLeast(1.0)
}

private fun LazyListScope.leaders(vm: GameViewModel, lg: League) {
    item {
        val top = lg.players.filter { it.teamId >= 0 }.sortedByDescending { it.ovr }.take(5)
        Section("Best players")
        GroupList(top.withIndex().toList(), inset = 70.dp) { (i, p) -> LeaderRow(vm, lg, "${i + 1}", p, null) }
        val pool = qualified(lg, null)
        for ((label, f, fmt) in CATS.take(5)) {
            val list = pool.sortedByDescending { f(it.stats) }.take(3)
            Section(label, action = if (label == "Points") "All stats" else null, onAction = { vm.push(Route.Leaders) })
            if (list.isEmpty()) Panel { Text("Leaders appear once games are played.", style = T.body, color = P.sub) }
            GroupList(list.withIndex().toList(), inset = 70.dp) { (i, p) -> LeaderRow(vm, lg, "${i + 1}", p, fmt(f(p.stats))) }
        }
    }
}

@Composable
fun LeaderRow(vm: GameViewModel, lg: League, rank: String, p: Player, value: String?) {
    val t = lg.team(p.teamId)
    ListRow(onClick = { vm.openPlayer(p.id) }) {
        Text(rank, style = T.numSm, color = P.sub, modifier = Modifier.width(28.dp))
        PlayerJersey(p, t, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = if (t.id == lg.userTeamId) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.school} · ${p.cls} · ${p.pos}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (value != null) Text(value, style = T.numSm, color = P.ink) else Ring(p.ovr, 40.dp, textSize = 16.sp)
    }
}

@Composable
fun LeadersScreen(vm: GameViewModel, lg: League) {
    var cat by remember { mutableIntStateOf(0) }
    var scope by remember { mutableIntStateOf(0) }
    val (_, f, fmt) = CATS[cat]
    val list = qualified(lg, if (scope == 0) null else lg.user.confId).sortedByDescending { f(it.stats) }.take(50)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Stat leaders", "Players in at least half their team's games", onBack = { vm.back() })
            Segmented(listOf("National", lg.conf(lg.user.confId).abbr), scope, { scope = it })
            Spacer(Modifier.height(10.dp))
            Chips(CATS.map { it.first }, cat, { cat = it })
            Spacer(Modifier.height(10.dp))
            if (list.isEmpty()) Empty("No qualified players yet.")
            GroupList(list.withIndex().toList(), inset = 70.dp) { (i, p) -> LeaderRow(vm, lg, "${i + 1}", p, fmt(f(p.stats))) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- postseason ----------

private fun LazyListScope.postseason(vm: GameViewModel, lg: League) {
    item {
        val ncaa = lg.ncaa
        if (ncaa != null) {
            Section("NCAA Tournament", action = "Bracket", onAction = { vm.push(Route.BracketView(-1)) })
            Panel(onClick = { vm.push(Route.BracketView(-1)) }) {
                Text(if (ncaa.done) "Champion: ${lg.team(ncaa.champion).name}" else "Now: ${roundTitle(ncaa, ncaa.current, true)}", style = T.head, color = P.ink)
                Text("64 teams · 4 regions", style = T.small, color = P.sub)
            }
        } else {
            Section("Bracketology · projected top seeds")
            val proj = lg.teams.sortedBy { it.net }.take(16)
            GroupList(proj.withIndex().toList()) { (i, t) -> RankRow(vm, lg, "${i / 4 + 1}", t, t.record, Season.REGIONS[if ((i / 4) % 2 == 0) i % 4 else 3 - i % 4]) }
            Text("Seed lines follow NET. Conference champions get automatic bids.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
        }
        if (lg.confBrackets.isNotEmpty()) {
            Section("Conference tournaments")
            GroupList(lg.confBrackets) { b ->
                ListRow(onClick = { vm.push(Route.BracketView(b.confId)) }) {
                    Text(b.short, style = T.numSm, color = P.accent, modifier = Modifier.width(64.dp))
                    Text(if (b.done) "Won by ${lg.team(b.champion).school}" else roundTitle(b, b.current, false), style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Chevron()
                }
            }
        }
    }
}

@Composable
fun BracketScreen(vm: GameViewModel, lg: League, confId: Int) {
    val b: Bracket? = if (confId < 0) lg.ncaa else lg.confBrackets.firstOrNull { it.confId == confId }
    if (b == null) { Column { BackBar("Bracket", onBack = { vm.back() }); Empty("The bracket isn't set yet.") }; return }
    val ncaa = confId < 0
    var region by remember { mutableIntStateOf(Season.REGIONS.indexOf(Season.regionOf(lg, lg.userTeamId) ?: "East").coerceAtLeast(0)) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar(b.name, if (b.done) "Champion: ${lg.team(b.champion).school}" else roundTitle(b, b.current, ncaa), onBack = { vm.back() })
            if (ncaa) { Segmented(Season.REGIONS + "Final Four", region, { region = it }, scroll = true); Spacer(Modifier.height(6.dp)) }
        }
        val rounds = if (!ncaa) b.roundNames.indices.toList() else if (region < 4) (0..3).toList() else listOf(4, 5)
        for (r in rounds) {
            val slots = b.rounds.getOrNull(r) ?: List(b.rounds[0].size shr r) { -2 }
            val pairs = slots.chunked(2)
            val range = if (ncaa && region < 4) { val per = pairs.size / 4; (region * per until (region + 1) * per) } else pairs.indices
            item {
                Section(roundTitle(b, r, ncaa))
                GroupList(range.toList()) { i ->
                    val pair = pairs[i]
                    val gid = b.gameIds.getOrNull(r)?.getOrNull(i) ?: -1
                    BracketPair(vm, lg, b, pair.getOrElse(0) { -2 }, pair.getOrElse(1) { -2 }, if (gid >= 0) lg.game(gid) else null)
                }
            }
        }
        if (b.done) item { Section("Champion"); GroupList(listOf(lg.team(b.champion))) { t -> RankRow(vm, lg, "🏆", t, t.record) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BracketPair(vm: GameViewModel, lg: League, b: Bracket, a: Int, c: Int, g: Game?) {
    ListRow(onClick = if (g != null && g.played) ({ vm.push(Route.Box(g.id)) }) else null, padding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
        Column(Modifier.weight(1f)) {
            BracketLine(lg, b, a, g)
            Spacer(Modifier.height(5.dp))
            BracketLine(lg, b, c, g)
        }
    }
}

@Composable
private fun BracketLine(lg: League, b: Bracket, id: Int, g: Game?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (id < 0) { Text(if (id == -1) "Bye" else "TBD", style = T.small, color = P.faint, modifier = Modifier.padding(start = 26.dp)); return@Row }
        val t = lg.team(id)
        val won = g != null && g.played && g.winner() == id
        val lost = g != null && g.played && !won
        Text("${b.seeds[id] ?: ""}", style = T.small, color = P.faint, modifier = Modifier.width(26.dp))
        TeamBadge(t, 20.dp)
        Spacer(Modifier.width(8.dp))
        Text(t.school, style = T.bodyB.copy(fontSize = 14.sp), color = when { id == lg.userTeamId -> P.accent; lost -> P.faint; else -> P.ink }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (g != null && g.played) Text("${g.scoreFor(id)}", style = T.numSm.copy(fontSize = 16.sp), color = if (won) P.ink else P.faint)
    }
}

// ---------- history ----------

private fun LazyListScope.history(vm: GameViewModel, lg: League) {
    item {
        Section("Champions")
        if (lg.history.isEmpty()) Panel { Text("Finish a season to start the record book.", style = T.body, color = P.sub) }
        GroupList(lg.history.reversed()) { s ->
            val t = lg.team(s.champion)
            ListRow(onClick = { vm.push(Route.History(HistoryKind.SEASONS)) }) {
                Text("${s.season}", style = T.numSm, color = P.accent, modifier = Modifier.width(56.dp))
                TeamBadge(t, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("over ${lg.team(s.runnerUp).school}", style = T.small, color = P.sub)
                }
            }
        }
        Section("Archive")
        GroupList(HistoryKind.entries) { k ->
            ListRow(onClick = { vm.push(Route.History(k)) }) {
                Column(Modifier.weight(1f)) {
                    Text(k.title, style = T.bodyB, color = P.ink)
                    Text(k.sub, style = T.small, color = P.sub)
                }
                Chevron()
            }
        }
    }
}

@Composable
fun HistoryScreen(vm: GameViewModel, lg: League, kind: HistoryKind) {
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar(kind.title, kind.sub, onBack = { vm.back() }) }
        when (kind) {
            HistoryKind.SEASONS -> {
                if (lg.history.isEmpty()) item { Empty("Finish a season to start the record.") }
                for (s in lg.history.reversed()) item {
                    Section("${s.season}")
                    Panel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TeamBadge(lg.team(s.champion), 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text("Champion: ${lg.team(s.champion).name}", style = T.bodyB, color = P.ink)
                                Text("Runner-up: ${lg.team(s.runnerUp).school}", style = T.small, color = P.sub)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Final Four: " + s.finalFour.joinToString(", ") { lg.team(it).school }, style = T.small, color = P.sub)
                        s.awards.firstOrNull { it.award == "Nat MVP" }?.let { Text("Player of the Year: ${it.name}, ${lg.team(it.teamId).school}", style = T.small, color = P.ink) }
                        if (s.cotyName.isNotEmpty()) Text("Coach of the Year: ${s.cotyName}", style = T.small, color = P.ink)
                    }
                }
            }
            HistoryKind.AWARDS -> {
                if (lg.history.isEmpty()) item { Empty("Awards are handed out after the title game.") }
                for (s in lg.history.reversed()) item {
                    Section("${s.season}")
                    GroupList(s.awards.filter { it.award in setOf("Nat MVP", "DPOY", "FOY", "MOP", "COTY") }) { a ->
                        ListRow {
                            Text(awardName(a.award), style = T.small, color = P.accent, modifier = Modifier.width(110.dp))
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = T.bodyB, color = P.ink)
                                Text(lg.team(a.teamId).school, style = T.small, color = P.sub)
                            }
                        }
                    }
                }
            }
            HistoryKind.DRAFT -> {
                if (lg.draftHistory.isEmpty()) item { Empty("No drafts yet.") }
                for ((season, picks) in lg.draftHistory.groupBy { it.season }.toSortedMap(compareByDescending { it })) item {
                    Section("$season draft")
                    GroupList(picks.take(30)) { d ->
                        ListRow {
                            Text("${d.pick}", style = T.numSm, color = P.accent, modifier = Modifier.width(36.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.playerName, style = T.bodyB, color = if (d.teamId == lg.userTeamId) P.accent else P.ink)
                                Text("${d.school} · ${com.nh.hoopscoach.game.classLabel(d.cls)} · ${d.pos}", style = T.small, color = P.sub)
                            }
                            Text("${d.ovr}", style = T.numSm, color = tier(d.ovr))
                        }
                    }
                }
            }
            HistoryKind.HOF -> item {
                Spacer(Modifier.height(8.dp))
                if (lg.hallOfFame.isEmpty()) Empty("Nobody has been inducted yet. Repeat All-Americans and national players of the year get in.")
                GroupList(lg.hallOfFame.reversed()) { h ->
                    ListRow {
                        Column(Modifier.weight(1f)) {
                            Text(h.name + if (h.isCoach) " · Coach" else " · ${h.pos}", style = T.bodyB, color = P.ink)
                            Text("${h.school} · ${h.summary}", style = T.small, color = P.sub)
                        }
                        Text("${h.season}", style = T.numSm, color = P.sub)
                    }
                }
            }
            HistoryKind.RECORDS -> item {
                Spacer(Modifier.height(8.dp))
                GroupList(lg.records) { r ->
                    ListRow {
                        Column(Modifier.weight(1f)) {
                            Text(r.label, style = T.bodyB, color = P.ink)
                            Text(if (r.holder.isEmpty()) "Not set yet" else "${r.holder} · ${r.detail}", style = T.small, color = P.sub)
                        }
                        Text("${r.value}", style = T.num, color = P.accent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

fun awardName(code: String) = when (code) {
    "Nat MVP" -> "Player of the Year"; "DPOY" -> "Defensive POY"; "FOY" -> "Freshman of the Year"; "MOP" -> "Most Outstanding"
    "COTY" -> "Coach of the Year"; "AA 1st" -> "1st Team All-American"; "AA 2nd" -> "2nd Team All-American"; "AA 3rd" -> "3rd Team All-American"
    "All-Tourn" -> "All-Tournament"; "Pos OY" -> "Best at position"; "Conf POY" -> "Conference POY"; "All-Conf 1st" -> "All-Conference"
    "Conf DPOY" -> "Conference DPOY"; "Conf FOY" -> "Conference FOY"; "Conf COTY" -> "Conference COTY"
    else -> code
}

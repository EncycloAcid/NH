package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Bracketology
import com.nh.hoopscoach.game.Bubble
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch

val LEAGUE_TABS = listOf("Polls", "Standings", "Leaders", "Bubble Watch", "Postseason", "History")
const val TAB_BUBBLE = 3

@Composable
fun LeagueScreen(vm: GameViewModel, lg: League) {
    if (vm.leagueConf < 0) vm.leagueConf = lg.user.confId
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    // The projection is a few milliseconds of work, so it is only redone when the calendar moves.
    val board = if (vm.leagueTab == TAB_BUBBLE || vm.leagueTab == TAB_BUBBLE + 1) remember(lg, lg.day, lg.phase) { Bracketology.board(lg) } else null
    LazyColumn(Modifier.fillMaxWidth(), state = listState) {
        item {
            ScreenHeader("${lg.teams.size} teams · ${lg.confs.size} conferences", "League", Season.statusLine(lg))
            Segmented(LEAGUE_TABS, vm.leagueTab, { vm.leagueTab = it }, scroll = true)
        }
        when (vm.leagueTab) {
            0 -> polls(vm, lg)
            1 -> standings(vm, lg)
            2 -> leaders(vm, lg)
            TAB_BUBBLE -> bubbleWatch(vm, lg, board) { scope.launch { listState.animateScrollToItem(1) } }
            TAB_BUBBLE + 1 -> postseason(vm, lg, board)
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

private fun LazyListScope.postseason(vm: GameViewModel, lg: League, board: Bubble?) {
    item {
        val ncaa = lg.ncaa
        if (ncaa != null) {
            Section("NCAA Tournament", action = "Bracket", onAction = { vm.push(Route.BracketView(-1)) })
            Panel(onClick = { vm.push(Route.BracketView(-1)) }) {
                Text(eventStatus(lg, ncaa), style = T.head, color = P.ink)
                Text(if (ncaa.playIns.isNotEmpty()) "68 teams · First Four · 4 regions" else "64 teams · 4 regions", style = T.small, color = P.sub)
            }
            lg.nit?.let { nit ->
                Section("NIT", action = "Bracket", onAction = { vm.push(Route.BracketView(Season.NIT_ID)) })
                Panel(onClick = { vm.push(Route.BracketView(Season.NIT_ID)) }) {
                    Text(eventStatus(lg, nit), style = T.head, color = P.ink)
                    Text("32 teams that just missed the NCAA field", style = T.small, color = P.sub)
                }
            }
        } else if (board != null) {
            Section("Bracketology · projected top seeds", action = "Bubble Watch", onAction = { vm.leagueTab = TAB_BUBBLE })
            val top = board.seeds.filterValues { it <= 4 }.keys.sortedBy { board.sheet(it)?.rank ?: 999 }
            GroupList(top.withIndex().toList()) { (i, id) ->
                val t = lg.team(id)
                RankRow(vm, lg, "${board.seeds[id]}", t, t.record, Season.REGIONS[if ((i / 4) % 2 == 0) i % 4 else 3 - i % 4])
            }
            Text(
                "68 teams: 32 automatic bids and 36 at-large. The last four at-large teams and the four lowest automatic qualifiers open in the First Four. The next 32 teams play in the NIT.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )
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
    val b: Bracket? = when (confId) {
        -1 -> lg.ncaa
        Season.NIT_ID -> lg.nit
        else -> lg.confBrackets.firstOrNull { it.confId == confId }
    }
    if (b == null) { Column { BackBar("Bracket", onBack = { vm.back() }); Empty("The bracket isn't set yet.") }; return }
    val ncaa = confId == -1
    val nit = confId == Season.NIT_ID
    // The NCAA splits into four regions; the NIT into four brackets named for their top seeds.
    val tabs = when {
        ncaa -> Season.REGIONS + "Final Four"
        nit -> (0 until 4).map { q -> b.rounds[0].getOrNull(q * 8)?.takeIf { it >= 0 }?.let { lg.team(it).abbr + " bracket" } ?: "Bracket ${q + 1}" } + "Semis & final"
        else -> emptyList()
    }
    val mine = when {
        ncaa -> Season.REGIONS.indexOf(Season.regionOf(lg, lg.userTeamId) ?: "East").coerceAtLeast(0)
        nit -> (b.rounds[0].indexOf(lg.userTeamId) / 8).coerceAtLeast(0)
        else -> 0
    }
    var region by remember { mutableIntStateOf(mine) }
    val split = if (ncaa) 4 else 3
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val now = if (ncaa && Season.firstFourDay(lg)) "First Four" else roundTitle(b, b.current, ncaa)
            BackBar(b.name, if (b.done) "Champion: ${lg.team(b.champion).school}" else now, onBack = { vm.back() })
            if (tabs.isNotEmpty()) { Segmented(tabs, region, { region = it }, scroll = true); Spacer(Modifier.height(6.dp)) }
        }
        if (ncaa && region < 4) {
            val pis = b.playIns.filter { it.slot / 16 == region }
            if (pis.isNotEmpty()) item {
                Section("First Four · Dayton")
                GroupList(pis) { pi -> BracketPair(vm, lg, b, pi.a, pi.b, pi.gameId.takeIf { it >= 0 }?.let { lg.game(it) }) }
            }
        }
        val rounds = when {
            tabs.isEmpty() -> b.roundNames.indices.toList()
            region < 4 -> (0 until split).toList()
            else -> (split until b.roundNames.size).toList()
        }
        for (r in rounds) {
            val slots = b.rounds.getOrNull(r) ?: List(b.rounds[0].size shr r) { -2 }
            val pairs = slots.chunked(2)
            val range = if (tabs.isNotEmpty() && region < 4) { val per = pairs.size / 4; (region * per until (region + 1) * per) } else pairs.indices
            item {
                Section(roundTitle(b, r, ncaa))
                GroupList(range.toList()) { i ->
                    val pair = pairs[i]
                    val gid = b.gameIds.getOrNull(r)?.getOrNull(i) ?: -1
                    fun tbd(slot: Int) = if (r == 0) b.playIns.firstOrNull { it.slot == slot }?.let { "${lg.team(it.a).abbr} / ${lg.team(it.b).abbr} winner" } else null
                    BracketPair(vm, lg, b, pair.getOrElse(0) { -2 }, pair.getOrElse(1) { -2 }, if (gid >= 0) lg.game(gid) else null, tbd(2 * i), tbd(2 * i + 1))
                }
            }
        }
        if (b.done) item { Section("Champion"); GroupList(listOf(lg.team(b.champion))) { t -> RankRow(vm, lg, "🏆", t, t.record) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BracketPair(vm: GameViewModel, lg: League, b: Bracket, a: Int, c: Int, g: Game?, tbdA: String? = null, tbdC: String? = null) {
    ListRow(onClick = if (g != null && g.played) ({ vm.push(Route.Box(g.id)) }) else null, padding = PaddingValues(horizontal = 14.dp, vertical = 9.dp)) {
        Column(Modifier.weight(1f)) {
            BracketLine(lg, b, a, g, tbdA)
            Spacer(Modifier.height(5.dp))
            BracketLine(lg, b, c, g, tbdC)
        }
    }
}

@Composable
private fun BracketLine(lg: League, b: Bracket, id: Int, g: Game?, tbd: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (id < 0) { Text(if (id == -1) "Bye" else tbd ?: "TBD", style = T.small, color = P.faint, modifier = Modifier.padding(start = 26.dp)); return@Row }
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

// ---------- bubble watch ----------

/** Status colors shared by Bubble Watch and the Today outlook card. */
@Composable
fun groupColor(key: String?): androidx.compose.ui.graphics.Color = when (key) {
    "lock", "safe" -> P.good
    "bubble" -> P.warn
    "ffo", "nfo" -> P.bad
    "auto" -> P.accent
    else -> P.sub
}

/** The two teams in the resume comparison: the user's picks, or a sensible pair around the cut line. */
private fun comparePicks(vm: GameViewModel, lg: League, b: Bubble): List<Int> {
    val picked = vm.bubblePick.filter { b.sheet(it) != null }
    if (picked.isNotEmpty()) return picked
    val lastIn = b.groups.firstOrNull { it.key == "bubble" }?.ids?.lastOrNull()
    val firstOut = b.groups.firstOrNull { it.key == "ffo" }?.ids?.firstOrNull()
    val u = lg.userTeamId
    val rival = when (b.groupOf(u)?.key) { null, "ffo", "nfo" -> lastIn; else -> firstOut }
    val mine = listOfNotNull(rival, u).distinct().filter { b.sheet(it) != null }
    return if (mine.size == 2) mine else listOfNotNull(lastIn, firstOut)
}

private fun LazyListScope.bubbleWatch(vm: GameViewModel, lg: League, b: Bubble?, onCompared: () -> Unit) {
    if (b == null) {
        item { Spacer(Modifier.height(10.dp)); Empty("Bubble Watch returns when the next season tips off.") }
        return
    }
    val picks = comparePicks(vm, lg, b)
    val toggle = { id: Int ->
        val cur = picks.toMutableList()
        val adding = id !in cur
        if (adding) { if (cur.size >= 2) cur.removeAt(0); cur += id } else cur.remove(id)
        vm.bubblePick.clear()
        vm.bubblePick.addAll(cur)
        if (adding && cur.size == 2) onCompared()
    }
    item {
        BubbleIntro(lg, b)
        ResumeCompare(vm, lg, b, picks) { vm.bubblePick.clear(); vm.bubblePick.addAll(picks.reversed()) }
    }
    for (g in b.groups) {
        if (g.ids.isEmpty()) continue
        item {
            if (g.key == "ffo") CutLine()
            Section("${g.title} · ${g.ids.size}")
            Text(g.note, style = T.small, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, bottom = 8.dp))
            when (g.key) {
                "bubble" -> {
                    val (lastIn, byes) = g.ids.partition { (b.atLargeRank[it] ?: 0) > b.atLargeSpots - 4 }
                    if (byes.isNotEmpty()) {
                        SubLabel("Last four byes")
                        GroupList(byes) { id -> BubbleRow(vm, lg, b, id, picks, toggle) }
                    }
                    if (lastIn.isNotEmpty()) {
                        SubLabel("Last four in · First Four")
                        GroupList(lastIn) { id -> BubbleRow(vm, lg, b, id, picks, toggle) }
                    }
                }
                "auto" -> {
                    var open by remember { mutableStateOf(false) }
                    val shown: List<Int> = if (open || g.ids.size <= 5) g.ids else g.ids.take(4) + MORE
                    GroupList(shown) { id ->
                        if (id == MORE) ListRow(onClick = { open = true }) {
                            Text("Show all ${g.ids.size}", style = T.bodyB, color = P.accent, modifier = Modifier.weight(1f))
                            Chevron()
                        } else BubbleRow(vm, lg, b, id, picks, toggle)
                    }
                }
                else -> GroupList(g.ids) { id -> BubbleRow(vm, lg, b, id, picks, toggle) }
            }
        }
    }
}

private const val MORE = -999

@Composable
private fun SubLabel(text: String) {
    Text(text.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = Gutter + 4.dp, top = 6.dp, bottom = 6.dp))
}

@Composable
private fun CutLine() {
    Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.weight(1f).height(2.dp).background(P.bad.copy(alpha = 0.45f)))
        Text("CUT LINE", style = T.label, color = P.bad, modifier = Modifier.padding(horizontal = 10.dp))
        Box(Modifier.weight(1f).height(2.dp).background(P.bad.copy(alpha = 0.45f)))
    }
}

@Composable
private fun BubbleIntro(lg: League, b: Bubble) {
    val (eyebrow, text) = when {
        b.final -> "Selection Sunday ${b.season}" to "The committee's final call. The last four in opened in the First Four; the first teams out headed to the NIT."
        lg.phase == com.nh.hoopscoach.game.Phase.CONF_TOURNEY -> "Conference tournament week" to "The field if the season ended today. Every conference tournament upset can steal a bid and shrink the bubble."
        else -> "Projection" to "The field if the season ended today. It firms up as conference play goes on."
    }
    Spacer(Modifier.height(10.dp))
    Panel {
        Text(eyebrow.uppercase(), style = T.label, color = P.accent)
        Spacer(Modifier.height(4.dp))
        Text(text, style = T.body, color = P.ink)
        Spacer(Modifier.height(8.dp))
        Text("The committee ranks resumes by NET and Wins Above Bubble (WAB) equally. Quadrant 1 wins add credit and Quadrant 4 losses cost the most.", style = T.small, color = P.sub)
        if (!b.final && lg.user.gp < 8) {
            Spacer(Modifier.height(8.dp))
            Tag("EARLY SEASON", P.warn)
        }
    }
}

@Composable
private fun BubbleRow(vm: GameViewModel, lg: League, b: Bubble, id: Int, picks: List<Int>, onToggle: (Int) -> Unit) {
    val t = lg.team(id)
    val s = b.sheet(id) ?: return
    val mine = id == lg.userTeamId
    val seed = b.seeds[id]
    val pick = picks.indexOf(id)
    ListRow(onClick = { onToggle(id) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier, padding = PaddingValues(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp)) {
        Column(Modifier.width(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(seed?.toString() ?: "–", style = T.numSm.copy(fontSize = 18.sp), color = if (seed != null) P.ink else P.faint)
            Text(if (seed != null) "SEED" else "OUT", style = T.label.copy(fontSize = 8.sp, letterSpacing = 0.5.sp), color = P.faint)
        }
        Spacer(Modifier.width(6.dp))
        TeamBadge(t, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.school, style = T.bodyB, color = if (mine) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${s.record} · NET ${s.net} · Q1 ${s.quad(1)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 6.dp)) {
            Text("%+.1f".format(s.wab), style = T.numSm.copy(fontSize = 16.sp), color = if (s.wab >= 0) P.good else P.bad)
            Text("WAB", style = T.label.copy(fontSize = 8.sp), color = P.faint)
        }
        Spacer(Modifier.width(10.dp))
        PickDot(pick)
    }
}

@Composable
private fun PickDot(index: Int) {
    val on = index >= 0
    Box(
        Modifier.size(24.dp).clip(CircleShape).background(if (on) P.accent else androidx.compose.ui.graphics.Color.Transparent)
            .border(2.dp, if (on) P.accent else P.line, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (on) Text(if (index == 0) "A" else "B", style = T.label.copy(fontSize = 11.sp), color = P.onAccent)
    }
}

/** Side-by-side team sheets. Better numbers are green. */
@Composable
private fun ResumeCompare(vm: GameViewModel, lg: League, b: Bubble, picks: List<Int>, onSwap: () -> Unit) {
    Section("Resume comparison", action = if (picks.size == 2) "Swap" else null, onAction = onSwap)
    if (picks.size < 2) {
        Panel {
            Text(
                if (picks.isEmpty()) "Tap two teams below to compare their resumes side by side." else "Tap one more team to compare it with ${lg.team(picks[0]).school}.",
                style = T.body, color = P.sub,
            )
        }
        return
    }
    val a = b.sheet(picks[0]) ?: return
    val c = b.sheet(picks[1]) ?: return
    Panel {
        Row(verticalAlignment = Alignment.Top) {
            CompareHead(vm, lg, b, a.teamId, 0, Modifier.weight(1f))
            Text("VS", style = T.h2, color = P.faint, modifier = Modifier.padding(horizontal = 6.dp, vertical = 12.dp))
            CompareHead(vm, lg, b, c.teamId, 1, Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        fun cmp(x: Double, y: Double) = if (x > y) 1 else if (x < y) -1 else 0
        fun quad(n: Int) = cmp(a.qw(n) - c.qw(n) + (c.ql(n) - a.ql(n)) * 0.001, 0.0)
        val lowA = a.ql(3) + a.ql(4)
        val lowC = c.ql(3) + c.ql(4)
        ResumeLine("Record", a.record, c.record, cmp(a.w.toDouble() / (a.w + a.l).coerceAtLeast(1), c.w.toDouble() / (c.w + c.l).coerceAtLeast(1)))
        ResumeLine("NET", "${a.net}", "${c.net}", cmp(c.net.toDouble(), a.net.toDouble()))
        ResumeLine("Quad 1", a.quad(1), c.quad(1), quad(1))
        ResumeLine("Quad 2", a.quad(2), c.quad(2), quad(2))
        ResumeLine("Quad 3-4", "${a.qw(3) + a.qw(4)}-$lowA", "${c.qw(3) + c.qw(4)}-$lowC", cmp(lowC.toDouble(), lowA.toDouble()))
        ResumeLine("SOS", ordinal(a.sos), ordinal(c.sos), cmp(c.sos.toDouble(), a.sos.toDouble()))
        ResumeLine("Non-conf SOS", ordinal(a.ncSos), ordinal(c.ncSos), cmp(c.ncSos.toDouble(), a.ncSos.toDouble()))
        ResumeLine("WAB", "%+.1f".format(a.wab), "%+.1f".format(c.wab), cmp(a.wab, c.wab))
        Hairline(Modifier.padding(vertical = 6.dp))
        ResumeLine("Resume rank", "#${a.rank}", "#${c.rank}", cmp(c.rank.toDouble(), a.rank.toDouble()))
        Spacer(Modifier.height(8.dp))
        val better = lg.team(if (a.rank < c.rank) a.teamId else c.teamId)
        Text("${better.school} has the stronger resume.", style = T.small, color = P.sub)
    }
}

@Composable
private fun CompareHead(vm: GameViewModel, lg: League, b: Bubble, id: Int, side: Int, modifier: Modifier) {
    val t = lg.team(id)
    val key = b.groupOf(id)?.key
    val seed = b.seeds[id]
    val align = if (side == 0) Alignment.Start else Alignment.End
    Column(modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.push(Route.TeamPage(id)) }.padding(4.dp), horizontalAlignment = align) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (side == 1) { PickDot(1); Spacer(Modifier.width(8.dp)) }
            TeamBadge(t, 40.dp)
            if (side == 0) { Spacer(Modifier.width(8.dp)); PickDot(0) }
        }
        Spacer(Modifier.height(6.dp))
        Text(t.school, style = T.bodyB, color = if (id == lg.userTeamId) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${lg.conf(t.confId).abbr} · " + (seed?.let { if (b.final) "No. $it seed" else "Proj. No. $it" } ?: "Out of the field"), style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Tag(Bracketology.status(b, id).uppercase(), groupColor(key))
    }
}

@Composable
private fun ResumeLine(label: String, a: String, c: String, better: Int) {
    @Composable
    fun side(v: String, wins: Boolean, loses: Boolean, align: TextAlign, m: Modifier) =
        Text(v, style = T.numSm.copy(fontSize = 17.sp), color = when { wins -> P.good; loses -> P.sub; else -> P.ink }, textAlign = align, modifier = m)
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        side(a, better > 0, better < 0, TextAlign.Start, Modifier.weight(1f))
        Text(label, style = T.small, color = P.sub, textAlign = TextAlign.Center, modifier = Modifier.width(110.dp))
        side(c, better < 0, better > 0, TextAlign.End, Modifier.weight(1f))
    }
}

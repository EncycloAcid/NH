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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team

private val NAT_TABS = listOf("Rankings", "Stats", "Coaches", "History")
private val CONF_TABS = listOf("Standings", "Stats", "Coaches", "History")

@Composable
fun LeagueScreen(vm: GameViewModel, lg: League) {
    if (vm.leagueConf < 0) vm.leagueConf = lg.user.confId
    val conf = lg.conf(vm.leagueConf)
    val national = vm.leagueScope == 0
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenTitle("League")
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Segmented(listOf("National", conf.abbr), vm.leagueScope, { vm.leagueScope = it }, Modifier.weight(1f))
                ConfPicker(lg, vm.leagueConf) { vm.leagueConf = it; vm.leagueScope = 1 }
                Spacer(Modifier.width(Gutter))
            }
            Spacer(Modifier.height(10.dp))
            Segmented(if (national) NAT_TABS else CONF_TABS, vm.leagueTab, { vm.leagueTab = it })
        }
        when (vm.leagueTab) {
            0 -> if (national) rankings(vm, lg) else standings(vm, lg, conf.id)
            1 -> leaders(vm, lg, if (national) null else conf.id)
            2 -> coaches(vm, lg, if (national) null else conf.id)
            else -> history(vm, lg, if (national) null else conf.id)
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun ConfPicker(lg: League, current: Int, onPick: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(C.card).clickable { open = true }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.ArrowDropDown, "Choose conference", tint = C.sub)
        }
        DropdownMenu(open, { open = false }, Modifier.background(C.card2)) {
            lg.confs.forEach { c ->
                DropdownMenuItem(
                    text = { Text("${c.abbr} · ${c.name}", color = if (c.id == current) C.orangeText else C.text) },
                    onClick = { open = false; onPick(c.id) },
                )
            }
        }
    }
}

private fun LazyListScope.rankings(vm: GameViewModel, lg: League) {
    val ap = lg.teams.filter { it.ap > 0 }.sortedBy { it.ap }
    item { SectionLabel(if (lg.user.gp == 0) "Preseason AP Top 25" else "AP Top 25") }
    item { TableHeader(listOf("NET" to 46, "REC" to 64)) }
    itemsIndexed(ap, key = { _, t -> "ap${t.id}" }) { i, t -> TeamRankRow(vm, lg, "${i + 1}", t, listOf("${t.net}" to 46, t.record to 64)) }
    val net = lg.teams.sortedBy { it.net }.take(100)
    item { SectionLabel("NET Rankings · Top 100") }
    item { TableHeader(listOf("PWR" to 46, "REC" to 64)) }
    items(net, key = { "net${it.id}" }) { t -> TeamRankRow(vm, lg, "${t.net}", t, listOf("${Rankings.power(t)}" to 46, t.record to 64)) }
}

@Composable
private fun TableHeader(cols: List<Pair<String, Int>>) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
        Text("TEAM", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
        cols.forEach { (c, w) -> Text(c, style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.width(w.dp), textAlign = TextAlign.End) }
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

@Composable
private fun TeamRankRow(vm: GameViewModel, lg: League, rank: String, t: Team, cols: List<Pair<String, Int>>) {
    val mine = t.id == lg.userTeamId
    Row(Modifier.fillMaxWidth().clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = Gutter, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank, color = if (mine) C.orangeText else C.text, fontSize = 17.sp, modifier = Modifier.width(36.dp))
        TeamLogo(t, 30.dp)
        Spacer(Modifier.width(10.dp))
        Text(teamLabel(t), color = if (mine) C.orangeText else C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        cols.forEach { (v, w) -> Text(v, color = C.sub, fontSize = 16.sp, modifier = Modifier.width(w.dp), textAlign = TextAlign.End) }
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

private fun LazyListScope.standings(vm: GameViewModel, lg: League, confId: Int) {
    val c = lg.conf(confId)
    val teams = Season.standings(lg, confId)
    item {
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row {
                val exposure = when (c.tier) { 0 -> 90; 1 -> 68; else -> 45 }
                StatCell("Exposure", "$exposure", Modifier.weight(1f), ratingColor(exposure), 28.sp)
                val pull = teams.map { it.prestige }.average().toInt()
                StatCell("Avg Prestige", "$pull", Modifier.weight(1f), ratingColor(pull), 28.sp)
                StatCell("Teams", "${teams.size}", Modifier.weight(1f), C.text, 28.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        TableHeader(listOf("NET" to 46, "CONF" to 58, "ALL" to 58))
    }
    itemsIndexed(teams, key = { _, t -> "st${t.id}" }) { i, t -> TeamRankRow(vm, lg, "${i + 1}", t, listOf("${t.net}" to 46, t.confRecord to 58, t.record to 58)) }
}

// ---------- stats ----------

private val CATS = listOf<Triple<String, (StatLine) -> Double, (Double) -> String>>(
    Triple("PPG", { it.ppg }, ::f1), Triple("RPG", { it.rpg }, ::f1), Triple("APG", { it.apg }, ::f1),
    Triple("SPG", { it.spg }, ::f1), Triple("BPG", { it.bpg }, ::f1),
)

fun qualified(lg: League, confId: Int?): List<Player> = lg.players.filter { p ->
    p.teamId >= 0 && (confId == null || lg.team(p.teamId).confId == confId) && p.stats.gp >= (lg.team(p.teamId).gp * 0.5).coerceAtLeast(1.0)
}

private fun LazyListScope.leaders(vm: GameViewModel, lg: League, confId: Int?) {
    val pool = qualified(lg, confId)
    val top = lg.players.filter { it.teamId >= 0 && (confId == null || lg.team(it.teamId).confId == confId) }.sortedByDescending { it.ovr }.take(5)
    item { SectionLabel("Top Players") }
    itemsIndexed(top, key = { _, p -> "tp${p.id}" }) { i, p -> LeaderRow(vm, lg, "#${i + 1}", p, f1(p.stats.ppg), showOvr = true) }
    for ((label, f, fmt) in CATS) {
        val list = pool.sortedByDescending { f(it.stats) }.take(5)
        item { SectionLabel("$label Leaders", action = if (label == "PPG") "All stats" else null, onAction = { vm.push(Route.Leaders) }) }
        if (list.isEmpty()) item { EmptyNote("Leaders appear once games are played.") }
        itemsIndexed(list, key = { _, p -> "$label${p.id}" }) { i, p -> LeaderRow(vm, lg, "${i + 1}", p, fmt(f(p.stats))) }
    }
    val teams = lg.teams.filter { confId == null || it.confId == confId }.filter { it.gp > 0 }
    item { SectionLabel("Team Leaders · Points per game") }
    items(teams.sortedByDescending { it.totals.pts.toDouble() / it.gp }.take(5), key = { "tl${it.id}" }) { t ->
        TeamRankRow(vm, lg, "", t, listOf(f1(t.totals.pts.toDouble() / t.gp) to 64))
    }
    item { SectionLabel("Team Leaders · Fewest allowed") }
    items(teams.sortedBy { it.opp.pts.toDouble() / it.gp }.take(5), key = { "td${it.id}" }) { t ->
        TeamRankRow(vm, lg, "", t, listOf(f1(t.opp.pts.toDouble() / t.gp) to 64))
    }
}

@Composable
fun LeaderRow(vm: GameViewModel, lg: League, rank: String, p: Player, value: String, showOvr: Boolean = false) {
    val t = lg.team(p.teamId)
    val mine = t.id == lg.userTeamId
    Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(rank, color = C.sub, fontSize = 16.sp, modifier = Modifier.width(36.dp))
        PlayerAvatar(p, lg, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.short, color = if (mine) C.orangeText else C.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
            Text("${t.school} · ${p.cls} · ${p.pos}", color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(value, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
        if (showOvr) RatingNum(p.ovr, 20.sp, Modifier.width(44.dp))
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

@Composable
fun LeadersScreen(vm: GameViewModel, lg: League) {
    var cat by remember { mutableIntStateOf(0) }
    val confId = if (vm.leagueScope == 1) vm.leagueConf else null
    val all = CATS + listOf<Triple<String, (StatLine) -> Double, (Double) -> String>>(
        Triple("FG%", { if (it.fga >= it.gp * 4) it.fgPct else 0.0 }, ::pct),
        Triple("3P%", { if (it.tpa >= it.gp * 2) it.tpPct else 0.0 }, ::pct),
        Triple("FT%", { if (it.fta >= it.gp * 2) it.ftPct else 0.0 }, ::pct),
        Triple("MPG", { it.mpg }, ::f1),
    )
    val (label, f, fmt) = all[cat]
    val list = qualified(lg, confId).sortedByDescending { f(it.stats) }.take(60)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader("Stat Leaders", if (confId == null) "National" else lg.conf(confId).name, onBack = { vm.back() })
            Segmented(all.map { it.first }, cat, { cat = it }, scroll = true)
            Spacer(Modifier.height(8.dp))
        }
        if (list.isEmpty()) item { EmptyNote("No qualified players yet.") }
        itemsIndexed(list, key = { _, p -> "l${p.id}" }) { i, p -> LeaderRow(vm, lg, "${i + 1}", p, fmt(f(p.stats))) }
        item { Text("Leaders need to have played in half of their team's games.", color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(Gutter)) }
        @Suppress("UNUSED_EXPRESSION") label
    }
}

// ---------- coaches ----------

private fun LazyListScope.coaches(vm: GameViewModel, lg: League, confId: Int?) {
    val list = lg.teams.filter { confId == null || it.confId == confId }.map { lg.coachOf(it) }.sortedByDescending { it.rating }.take(if (confId == null) 50 else 20)
    item { SectionLabel("Coach Power Rankings") }
    itemsIndexed(list, key = { _, c -> "c${c.id}" }) { i, c ->
        val t = lg.team(c.teamId)
        Row(Modifier.fillMaxWidth().clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${i + 1}", color = C.sub, fontSize = 17.sp, modifier = Modifier.width(36.dp))
            Avatar(c.look, Color.Unspecified, null, 46.dp, coach = true)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name, color = if (c.isUser) C.orangeText else C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamLogo(t, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("${t.school} · Age ${c.age}", color = C.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                RatingNum(c.rating, 20.sp)
                Text(if (c.titles > 0) "${c.titles} title${if (c.titles > 1) "s" else ""}" else "${c.w}-${c.l}", color = C.sub, fontSize = 13.sp)
            }
        }
        Divider(Modifier.padding(horizontal = Gutter))
    }
}

// ---------- history ----------

private fun LazyListScope.history(vm: GameViewModel, lg: League, confId: Int?) {
    if (confId != null) {
        item { SectionLabel("Conference Tournament Champions") }
        val seasons = lg.history.reversed().filter { it.confChamps[confId] != null }
        if (seasons.isEmpty()) item { EmptyNote("Champions will be listed after the first season.") }
        items(seasons, key = { "ch${it.season}" }) { s ->
            val t = lg.team(s.confChamps.getValue(confId))
            TeamRankRow(vm, lg, "${s.season}", t, emptyList())
        }
        item { SectionLabel("Titles") }
        val teams = lg.conf(confId).teamIds.map { lg.team(it) }.sortedWith(compareByDescending<Team> { it.titles }.thenByDescending { it.confTitles })
        items(teams, key = { "tt${it.id}" }) { t -> TeamRankRow(vm, lg, "", t, listOf("${t.titles}" to 40, "${t.confTitles}" to 48)) }
        return
    }
    item { Spacer(Modifier.height(10.dp)) }
    items(HistoryKind.entries) { k ->
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), onClick = { vm.push(Route.History(k)) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(C.orangeDim), contentAlignment = Alignment.Center) {
                    Icon(if (k == HistoryKind.HOF) AppIcons.Crown else AppIcons.Trophy, null, tint = C.orange, modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(k.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(k.sub, color = C.sub, fontSize = 14.sp)
                }
                Chevron()
            }
        }
    }
}

// ---------- team page, schedule, bracket ----------

@Composable
fun TeamScreen(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    var tab by remember(teamId) { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader(t.name, "${lg.conf(t.confId).name} · ${prestigeLabel(t.prestige)}", onBack = { vm.back() })
            TeamHeader(vm, lg, t)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Pill("Prestige ${t.prestige}", C.orangeText)
                Pill("NET ${t.net}", C.text)
                if (t.ap > 0) Pill("AP #${t.ap}", C.yellow)
                Pill("${t.titles} titles", C.sub)
            }
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("Roster", "Schedule", "History"), tab, { tab = it })
        }
        when (tab) {
            0 -> items(lg.rosterOf(t).sortedByDescending { it.ovr }, key = { "tr${it.id}" }) { p -> PlayerRow(vm, lg, t, p) }
            1 -> items(Schedule.teamGames(lg, t.id), key = { "tg${it.id}" }) { g -> ResultRow(vm, lg, g, t.id, showGame = true); Divider(Modifier.padding(horizontal = Gutter)) }
            else -> {
                if (t.history.isEmpty()) item { EmptyNote("No completed seasons yet.") }
                items(t.history.reversed(), key = { "th${it.season}" }) { h ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${h.season}", color = C.sub, fontSize = 16.sp, modifier = Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${h.w}-${h.l} (${h.cw}-${h.cl})", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("${h.result} · ${h.coach}", color = C.sub, fontSize = 13.sp)
                        }
                        Text("${h.prestige}", color = ratingColor(h.prestige), fontSize = 17.sp)
                    }
                    Divider(Modifier.padding(horizontal = Gutter))
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
fun ScheduleScreen(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    val games = Schedule.teamGames(lg, teamId)
    val reg = games.filter { it.type == GameType.NONCONF || it.type == GameType.CONF || it.type == GameType.EVENT }
    val post = games.filter { it.type == GameType.CONF_TOURNEY || it.type == GameType.NCAA }
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackHeader("Results & Schedule", "${t.school} · ${t.record} (${t.confRecord})", onBack = { vm.back() }) }
        item { SectionLabel("Regular Season") }
        items(reg, key = { "s${it.id}" }) { g -> ResultRow(vm, lg, g, teamId, showGame = true); Divider(Modifier.padding(horizontal = Gutter)) }
        if (post.isNotEmpty()) {
            item { SectionLabel("Postseason") }
            items(post, key = { "p${it.id}" }) { g -> ResultRow(vm, lg, g, teamId); Divider(Modifier.padding(horizontal = Gutter)) }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
fun BracketScreen(vm: GameViewModel, lg: League, confId: Int) {
    val b: Bracket? = if (confId < 0) lg.ncaa else lg.confBrackets.firstOrNull { it.confId == confId }
    if (b == null) { Column { BackHeader("Bracket", onBack = { vm.back() }); EmptyNote("The bracket isn't set yet.") }; return }
    val ncaa = confId < 0
    val userRegion = if (ncaa) Season.REGIONS.indexOf(Season.regionOf(lg, lg.userTeamId) ?: "East").coerceAtLeast(0) else 0
    var region by remember { mutableIntStateOf(userRegion) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader(b.name, if (b.done) "Champion: ${lg.team(b.champion).school}" else "Round: ${b.roundNames.getOrElse(b.current) { "" }}", onBack = { vm.back() })
            if (ncaa) {
                Segmented(Season.REGIONS + "Final Four", region, { region = it }, scroll = true)
                Spacer(Modifier.height(6.dp))
            }
        }
        val roundsToShow = if (!ncaa) b.roundNames.indices.toList() else if (region < 4) (0..3).toList() else listOf(4, 5)
        for (r in roundsToShow) {
            val slots = b.rounds.getOrNull(r) ?: List(b.rounds[0].size shr r) { -2 }
            val pairs = slots.chunked(2)
            val range = if (ncaa && region < 4) {
                val per = pairs.size / 4
                (region * per until (region + 1) * per)
            } else pairs.indices
            item { SectionLabel(roundTitle(b, r, ncaa)) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                    for (i in range) {
                        val pair = pairs[i]
                        val gid = b.gameIds.getOrNull(r)?.getOrNull(i) ?: -1
                        val g = if (gid >= 0) lg.game(gid) else null
                        BracketMatch(vm, lg, b, pair.getOrElse(0) { -2 }, pair.getOrElse(1) { -2 }, g)
                        if (i != range.last) Divider()
                    }
                }
            }
        }
        if (b.done) item {
            SectionLabel("Champion")
            TeamRankRow(vm, lg, "🏆", lg.team(b.champion), emptyList())
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

private fun roundTitle(b: Bracket, r: Int, ncaa: Boolean): String = if (ncaa) when (r) {
    0 -> "First Round"; 1 -> "Second Round"; 2 -> "Sweet 16"; 3 -> "Elite Eight"; 4 -> "Final Four"; else -> "National Championship"
} else when (b.roundNames.getOrNull(r)) { "R1" -> "First Round"; "QF" -> "Quarterfinals"; "SF" -> "Semifinals"; "Final" -> "Championship"; else -> "Round ${r + 1}" }

@Composable
private fun BracketMatch(vm: GameViewModel, lg: League, b: Bracket, a: Int, c: Int, g: com.nh.hoopscoach.game.Game?) {
    Column(Modifier.fillMaxWidth().clickable(enabled = g != null && g.played) { g?.let { vm.push(Route.Box(it.id)) } }.padding(vertical = 8.dp)) {
        BracketLine(lg, b, a, g)
        BracketLine(lg, b, c, g)
    }
}

@Composable
private fun BracketLine(lg: League, b: Bracket, id: Int, g: com.nh.hoopscoach.game.Game?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (id < 0) {
            Text(if (id == -1) "Bye" else "TBD", color = C.dim, fontSize = 15.sp, modifier = Modifier.padding(start = 34.dp))
            return@Row
        }
        val t = lg.team(id)
        val won = g != null && g.played && g.winner() == id
        val lost = g != null && g.played && g.winner() != id
        Text("${b.seeds[id] ?: ""}", color = C.dim, fontSize = 14.sp, modifier = Modifier.width(26.dp))
        TeamLogo(t, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(
            t.school, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            color = when { id == lg.userTeamId -> C.orangeText; lost -> C.dim; else -> C.text }, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal,
        )
        if (g != null && g.played) Text("${g.scoreFor(id)}", fontSize = 15.sp, color = if (won) C.text else C.dim, fontWeight = if (won) FontWeight.Bold else FontWeight.Normal)
    }
}

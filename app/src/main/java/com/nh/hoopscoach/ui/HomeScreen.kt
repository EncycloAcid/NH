package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team

@Composable
fun HomeScreen(vm: GameViewModel, lg: League) {
    when (lg.phase) {
        Phase.REGULAR -> RegularHome(vm, lg)
        Phase.CONF_TOURNEY, Phase.NCAA -> TourneyHome(vm, lg)
        Phase.WRAP_UP -> WrapUpScreen(vm, lg)
        Phase.OFFSEASON -> OffseasonScreen(vm, lg)
    }
}

fun teamLabel(t: Team, withRank: Boolean = true) = if (withRank && t.ap in 1..25) "#${t.ap} ${t.school}" else t.school

@Composable
fun SimMenu(vm: GameViewModel, lg: League) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(12.dp)).background(C.card).clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = C.sub, modifier = Modifier.size(20.dp))
            Text("Sim", color = C.text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Icon(Icons.Default.ArrowDropDown, null, tint = C.sub, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, modifier = Modifier.background(C.card2)) {
            @Composable
            fun item(label: String, action: () -> Unit) = DropdownMenuItem(text = { Text(label, color = C.text) }, onClick = { open = false; action() })
            when (lg.phase) {
                Phase.REGULAR -> {
                    item("Sim next game") { vm.simDay() }
                    item("Sim one week") { vm.simDays(7) }
                    item("Sim to conference tournament") { vm.simToPhase(Phase.CONF_TOURNEY, "Simulating the regular season") }
                    item("Sim to NCAA Tournament") { vm.simToPhase(Phase.NCAA, "Simulating to Selection Sunday") }
                    item("Sim to end of season") { vm.simToPhase(Phase.WRAP_UP, "Simulating the rest of the season") }
                }
                Phase.CONF_TOURNEY -> {
                    item("Sim round") { vm.simDay() }
                    item("Sim to NCAA Tournament") { vm.simToPhase(Phase.NCAA, "Simulating conference tournaments") }
                    item("Sim to end of season") { vm.simToPhase(Phase.WRAP_UP, "Simulating the rest of the season") }
                }
                Phase.NCAA -> {
                    item("Sim round") { vm.simDay() }
                    item("Sim to end of season") { vm.simToPhase(Phase.WRAP_UP, "Simulating March") }
                }
                else -> item("Nothing to sim") {}
            }
        }
    }
}

@Composable
fun SettingsButton(vm: GameViewModel) {
    Box(Modifier.size(40.dp).clip(CircleShape).clickable { vm.push(Route.Settings) }, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.Settings, "Settings", tint = C.sub)
    }
}

@Composable
private fun RegularHome(vm: GameViewModel, lg: League) {
    val u = lg.user
    val today = Season.userGameToday(lg)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenTitle("Home") { SimMenu(vm, lg); SettingsButton(vm) }
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp)) {
                Text("Season ${lg.season}", fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(Season.statusLine(lg), fontSize = 19.sp, color = C.dim)
            }
            Spacer(Modifier.height(10.dp))
            if (today != null) MatchCard(vm, lg, today, gameTypeLabel(lg, today, u.id))
            else Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                Text("No game today.", color = C.sub)
                Spacer(Modifier.height(12.dp))
                BigButton("Sim Day", Modifier.fillMaxWidth()) { vm.simDay() }
            }
        }
        item { RecentResults(vm, lg) }
        item {
            Spacer(Modifier.height(16.dp))
            TeamStatGrid(lg, u)
        }
        item { Leaders(vm, lg, u) }
        item { NewsList(lg) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

fun gameTypeLabel(lg: League, g: Game, me: Int): String {
    val kind = when (g.type) {
        GameType.NONCONF -> "Non-Conference"
        GameType.CONF -> lg.conf(lg.team(me).confId).abbr
        GameType.EVENT -> g.label.substringBefore(" ·")
        GameType.CONF_TOURNEY -> g.label.substringAfter("· ")
        GameType.NCAA -> g.label.substringAfter("· ")
    }
    val site = when { g.neutral -> "Neutral"; g.home == me -> "Home"; else -> "Away" }
    return "Next Game · $kind · $site"
}

@Composable
fun MatchCard(vm: GameViewModel, lg: League, g: Game, label: String) {
    val u = lg.user
    val (left, right) = if (g.home == u.id || (g.neutral && g.away != u.id)) lg.team(g.home) to lg.team(g.away) else lg.team(g.away) to lg.team(g.home)
    val opp = if (left.id == u.id) right else left
    val seeds = (lg.ncaa?.takeIf { lg.phase == Phase.NCAA } ?: lg.confBrackets.firstOrNull { it.confId == u.confId && lg.phase == Phase.CONF_TOURNEY })?.seeds
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Preview ›", color = C.orangeText, fontSize = 14.sp, modifier = Modifier.clickable { vm.push(Route.TeamPage(opp.id)) })
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchSide(vm, lg, left, seeds?.get(left.id), Modifier.weight(1f))
            Text("VS", color = C.dim, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            MatchSide(vm, lg, right, seeds?.get(right.id), Modifier.weight(1f))
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigButton("Play", Modifier.weight(1f)) { vm.startLive() }
            BigButton("Sim", Modifier.weight(1f), primary = false) { vm.simDay() }
        }
    }
}

@Composable
private fun MatchSide(vm: GameViewModel, lg: League, t: Team, seed: Int?, modifier: Modifier) {
    val mine = t.id == lg.userTeamId
    Column(modifier.clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(t, 58.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (seed != null) "($seed) ${t.school}" else teamLabel(t),
            color = if (mine) C.orangeText else C.text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
        Text("${t.mascot} · ${t.record}", color = C.sub, fontSize = 14.sp, maxLines = 1)
    }
}

@Composable
private fun RecentResults(vm: GameViewModel, lg: League) {
    val u = lg.user
    val recent = lg.games.filter { it.played && it.involves(u.id) }.sortedByDescending { it.day }.take(3)
    SectionLabel("Recent Results", action = "Results & Schedule", onAction = { vm.push(Route.Schedule(u.id)) })
    if (recent.isEmpty()) EmptyNote("Your season tips off with the next game.")
    recent.forEachIndexed { i, g -> ResultRow(vm, lg, g, u.id, showGame = true); if (i < recent.lastIndex) Divider(Modifier.padding(horizontal = Gutter)) }
}

@Composable
fun ResultRow(vm: GameViewModel, lg: League, g: Game, me: Int, showGame: Boolean = false) {
    val opp = lg.team(g.opp(me))
    val won = g.played && g.winner() == me
    Row(
        Modifier.fillMaxWidth().clickable { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(opp.id)) }.padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (g.played) WLBadge(won) else Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Text(if (g.day <= Schedule.REG_DAYS) "${g.day}" else "—", color = C.dim, fontSize = 13.sp) }
        Spacer(Modifier.width(10.dp))
        Text(g.site(me), color = C.sub, fontSize = 15.sp, modifier = Modifier.width(24.dp))
        TeamLogo(opp, 24.dp)
        Spacer(Modifier.width(8.dp))
        Text(teamLabel(opp), fontSize = 17.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (showGame) Text(if (g.day <= Schedule.REG_DAYS) "GM ${g.day}" else g.type.short, color = C.dim, fontSize = 13.sp, modifier = Modifier.padding(end = 10.dp))
        if (g.played) {
            val s = "${g.scoreFor(me)}-${g.scoreAgainst(me)}${if (g.ot > 0) " OT" else ""}"
            Text(s, color = if (won) C.green else C.red, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        } else Text(gameTypeShort(g), color = C.sub, fontSize = 14.sp)
        Spacer(Modifier.width(4.dp))
        Chevron()
    }
}

fun gameTypeShort(g: Game) = when (g.type) {
    GameType.CONF -> "Conf"; GameType.NONCONF -> "Non-Conf"; GameType.EVENT -> "Event"; GameType.CONF_TOURNEY -> "Conf Tourney"; GameType.NCAA -> "NCAA"
}

@Composable
fun TeamStatGrid(lg: League, t: Team) {
    val s = t.totals
    val o = t.opp
    val gp = t.gp.coerceAtLeast(1).toDouble()
    val strk = when { t.streak > 0 -> "W${t.streak}"; t.streak < 0 -> "L${-t.streak}"; else -> "—" }
    StatGrid(
        listOf(
            Triple("Record", t.record, C.text),
            Triple("Conf", t.confRecord, C.text),
            Triple("Strk", strk, if (t.streak > 0) C.green else if (t.streak < 0) C.red else C.text),
            Triple("NET", t.net.toString(), C.orangeText),
            Triple("AP", if (t.ap > 0) "#${t.ap}" else "NR", C.text),
            Triple("PPG", f1(s.pts / gp), C.text),
            Triple("OPPG", f1(o.pts / gp), C.text),
            Triple("RPG", f1(s.reb / gp), C.text),
            Triple("APG", f1(s.ast / gp), C.text),
            Triple("TO", f1(s.tov / gp), C.text),
        ),
    )
}

@Composable
private fun Leaders(vm: GameViewModel, lg: League, t: Team) {
    val players = lg.rosterOf(t)
    val top = if (t.gp > 0) players.sortedByDescending { it.stats.ppg }.take(3) else players.sortedByDescending { it.ovr }.take(3)
    SectionLabel("Your Leaders")
    top.forEachIndexed { i, p ->
        Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(p.look, Color(t.primary), p.number, 50.dp, trim = Color(t.secondary).let { if (it == Color(t.primary)) Color.White else it })
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.short, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                Text("${p.pos} · ${p.cls}", color = C.sub, fontSize = 14.sp)
            }
            if (t.gp > 0) {
                MiniStat(f1(p.stats.ppg), "PPG")
                MiniStat(f1(p.stats.apg), "APG")
                MiniStat(f1(p.stats.rpg), "RPG")
            } else {
                MiniStat("${p.ovr}", "OVR")
                MiniStat("${p.pot}", "POT")
                MiniStat(p.style.short, "STYLE")
            }
            Chevron()
        }
        if (i < top.lastIndex) Divider(Modifier.padding(horizontal = Gutter))
    }
}

@Composable
fun MiniStat(v: String, label: String) {
    Column(Modifier.width(54.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(v, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Text(label, fontSize = 11.sp, color = C.sub)
    }
}

@Composable
fun NewsList(lg: League, count: Int = 6) {
    val items = lg.news.filter { it.season == lg.season }.takeLast(count).reversed()
    if (items.isEmpty()) return
    SectionLabel("Headlines")
    items.forEachIndexed { i, n ->
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Dot(if (n.mine) C.orange else C.dim)
            Spacer(Modifier.width(10.dp))
            Text(n.text, fontSize = 15.sp, color = if (n.mine) C.text else C.sub)
        }
        if (i < items.lastIndex) Divider(Modifier.padding(horizontal = Gutter))
    }
}

// ---------- tournaments ----------

@Composable
private fun TourneyHome(vm: GameViewModel, lg: League) {
    val u = lg.user
    val ncaa = lg.phase == Phase.NCAA
    val b: Bracket = (if (ncaa) lg.ncaa else lg.confBrackets.firstOrNull { it.confId == u.confId }) ?: return
    val today = Season.userGameToday(lg)
    val roundName = b.roundNames.getOrElse(b.current) { "" }
    val alive = b.current < b.rounds.size && u.id in b.rounds[b.current]
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenTitle(if (ncaa) "NCAA Tournament" else "${lg.conf(u.confId).abbr} Tournament", color = C.orangeText, size = 24.sp) { SimMenu(vm, lg); SettingsButton(vm) }
            Text(
                if (ncaa) (Season.regionOf(lg, u.id)?.let { "$it Region · No. ${b.seeds[u.id]} seed · $roundName" } ?: roundName) else "Round: $roundName",
                color = C.sub, fontSize = 17.sp, modifier = Modifier.padding(horizontal = Gutter),
            )
            Spacer(Modifier.height(14.dp))
            when {
                today != null -> MatchCard(vm, lg, today, today.label.substringAfter("· ").let { "$it · ${if (today.neutral) "Neutral" else if (today.home == u.id) "Home" else "Away"}" })
                alive -> TourneyNote(vm, "You Have A Bye", "Sim this round to see who advances to face you.", "Sim Round")
                ncaa && u.id !in b.rounds[0] -> TourneyNote(vm, "Not Selected", "${u.school} missed the field. You can still follow the bracket.", "Sim Round")
                else -> TourneyNote(vm, "Season Over", "${u.school} has been eliminated.", "Sim Round")
            }
        }
        item {
            SectionLabel("Your Path")
            PathRow(lg, b, u.id)
        }
        item {
            val games = (b.gameIds.getOrNull(b.current) ?: emptyList()).filter { it >= 0 }.map { lg.game(it) }
            val shown = if (ncaa) {
                val region = Season.regionOf(lg, u.id)
                games.filter { region == null || it.label.contains(region) || b.current >= 4 }
            } else games
            SectionLabel("$roundName Matchups", action = "View Bracket", onAction = { vm.push(Route.BracketView(if (ncaa) -1 else u.confId)) })
            if (shown.isEmpty()) EmptyNote("No games left in this round.")
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)) {
                shown.take(8).forEachIndexed { i, g ->
                    MatchupRow(vm, lg, b, g)
                    if (i < shown.size - 1 && i < 7) Divider()
                }
            }
        }
        item { NewsList(lg, 5) }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun TourneyNote(vm: GameViewModel, title: String, body: String, button: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Text(title, color = C.orangeText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, color = C.sub, fontSize = 15.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        BigButton(button, Modifier.fillMaxWidth()) { vm.simDay() }
    }
}

@Composable
fun PathRow(lg: League, b: Bracket, teamId: Int) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = androidx.compose.foundation.layout.PaddingValues(vertical = 18.dp, horizontal = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            b.roundNames.forEachIndexed { i, name ->
                val reached = b.rounds.getOrNull(i)?.contains(teamId) == true
                val advanced = b.rounds.getOrNull(i + 1)?.contains(teamId) == true || (i == b.roundNames.lastIndex && b.champion == teamId)
                val current = i == b.current && reached
                val lost = reached && !advanced && i < b.current
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    val color = when { advanced -> C.green; lost -> C.red; current -> C.orange; else -> C.dim }
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).border(2.dp, color, CircleShape).background(if (advanced) C.greenDim else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (advanced) Icon(Icons.Default.Check, null, tint = C.green, modifier = Modifier.size(16.dp))
                        else Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(name, color = if (current) C.orangeText else C.sub, fontSize = 12.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun MatchupRow(vm: GameViewModel, lg: League, b: Bracket, g: Game) {
    val h = lg.team(g.home)
    val a = lg.team(g.away)
    Row(
        Modifier.fillMaxWidth().clickable { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(if (h.id == lg.userTeamId) a.id else h.id)) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("${b.seeds[h.id] ?: ""}", color = C.dim, fontSize = 15.sp, modifier = Modifier.width(26.dp))
        TeamLogo(h, 26.dp)
        Spacer(Modifier.width(8.dp))
        Text(h.abbr, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (h.id == lg.userTeamId) C.orangeText else C.text, modifier = Modifier.weight(1f))
        Text(if (g.played) "${g.hs}-${g.aws}" else "vs", color = C.dim, fontSize = 15.sp)
        Text(a.abbr, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = if (a.id == lg.userTeamId) C.orangeText else C.text, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
        Spacer(Modifier.width(8.dp))
        TeamLogo(a, 26.dp)
        Text("${b.seeds[a.id] ?: ""}", color = C.dim, fontSize = 15.sp, modifier = Modifier.width(26.dp), textAlign = TextAlign.End)
        Chevron()
    }
}

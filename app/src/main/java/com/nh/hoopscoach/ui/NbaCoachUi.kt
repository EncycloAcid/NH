package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NbaCoach
import com.nh.hoopscoach.game.NbaData
import com.nh.hoopscoach.game.Payroll
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Picks
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.RealSeasons
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.game.Trades

/** The years a coaching career can start: 1986-87 through today's league (2026-27). */
private val COACH_YEARS = (1987..2027).toList().reversed()

private fun seasonLabel(y: Int) = if (y >= 2027) "Today · 2026-27" else "${y - 1}-${(y % 100).toString().padStart(2, '0')}"

// ---------- new career ----------

@Composable
fun NbaCoachNewGame(vm: GameViewModel, header: @Composable () -> Unit) {
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var year by remember { mutableIntStateOf(2027) }
    var decade by remember { mutableIntStateOf(0) }
    var pick by remember { mutableStateOf<String?>(null) }
    val teams = remember(year) { ProLeague.seasonTeams(year) }
    // Each team's two best players that season, for the list.
    val stars = remember(year) {
        if (year >= 2027 || !RealSeasons.has(year)) teams.associate { t -> t.slot to NbaData.PLAYERS.filter { it.team == t.id }.sortedByDescending { it.ovr }.take(2).map { it.name } }
        else RealSeasons.season(year).groupBy { RealSeasons.slotOf(it.first) }.mapValues { (_, l) -> l.sortedByDescending { RealSeasons.rating2k(it) * 100 + it.mpg }.take(2).map { it.name } }
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                header()
                Section("Your coach")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(first, { first = it.take(16) }, placeholder = { Text("First name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(last, { last = it.take(18) }, placeholder = { Text("Last name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                Section("Season")
                val decades = listOf("Today", "2020s", "2010s", "2000s", "1990s", "1980s")
                Segmented(decades, decade, { d ->
                    decade = d
                    year = when (d) { 0 -> 2027; 1 -> 2026; 2 -> 2020; 3 -> 2010; 4 -> 2000; else -> 1990 }
                    pick = null
                }, scroll = true)
                if (decade > 0) {
                    Spacer(Modifier.height(8.dp))
                    val years = COACH_YEARS.filter { y -> y < 2027 && when (decade) { 1 -> y >= 2021; 2 -> y in 2011..2020; 3 -> y in 2001..2010; 4 -> y in 1991..2000; else -> y <= 1990 } }
                    Chips(years.map { seasonLabel(it) }, years.indexOf(year).coerceAtLeast(0), { year = years[it]; pick = null })
                }
                Text(
                    "${seasonLabel(year)}: ${teams.size} teams with their real rosters" + if (year < 2027) " that season, rated from their numbers (and their 2K cards where they have one)." else ", rated from NBA 2K27.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                )
                Section("Choose a team")
                GroupList(teams.sortedBy { it.name }, inset = 66.dp) { t ->
                    val sel = pick == t.slot
                    ListRow(onClick = { pick = t.slot }, modifier = if (sel) Modifier.background(P.accentSoft) else Modifier) {
                        TeamBadge(t, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(stars[t.slot].orEmpty().joinToString(" · ").ifEmpty { "—" }, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (sel) Tag("PICKED", P.accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(P.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
            val t = teams.firstOrNull { it.slot == pick }
            PrimaryButton(if (t == null) "Pick a team" else "Coach the ${t.name}", Modifier.fillMaxWidth(), enabled = t != null) {
                vm.startNbaCoach(year, t!!.slot, first.trim().ifBlank { "Coach" }, last.trim().ifBlank { "Rookie" })
            }
        }
    }
}

// ---------- today ----------

@Composable
fun NbaCoachToday(vm: GameViewModel, lg: League) {
    if (lg.phase == Phase.OFFSEASON) { NbaCoachOffseason(vm, lg); return }
    val u = lg.user
    val ps = lg.proState ?: return
    val c = lg.userCoach
    LazyColumn(Modifier.fillMaxSize()) {
        item { ScreenHeader("${lg.season} · ${proPhase(lg)}", u.name, "Coach ${c.name} · ${teamLine(lg, u)}") { TeamBadge(u, 50.dp) } }
        if (lg.phase == Phase.PLAYOFFS) item { PlayoffCard(vm, lg) }
        Season.userGameToday(lg)?.let { g ->
            item {
                val opp = lg.team(g.opp(u.id))
                val win = com.nh.hoopscoach.game.Rankings.winProbFor(lg, g, u.id)
                Section("Next up")
                Panel {
                    Text((if (g.label.isNotEmpty()) g.label else if (g.home == u.id) "Home" else "Away").uppercase(), style = T.label, color = P.accent)
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MatchSide(lg, u, Modifier.weight(1f))
                        Text(if (g.home == u.id) "VS" else "AT", style = T.h2, color = P.faint)
                        MatchSide(lg, opp, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Our chance to win: ${(win * 100).toInt()}%. Set the rotation and game plan on the Team tab.", style = T.small, color = P.sub)
                }
            }
        }
        item { NbaPreviewCard(vm, lg) }
        item {
            val games = (u.w + u.l).coerceAtLeast(1)
            val total = ProLeague.seasonGames(lg)
            val pace = (u.w.toDouble() / games * total).toInt()
            Section("The owner")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(NbaCoach.securityLabel(c.security).uppercase(), style = T.label, color = securityColor(c.security))
                        Text(if (ps.expect >= 0) "Expects about ${ps.expect} wins" else "Expects a winning team", style = T.head, color = P.ink)
                        if (u.w + u.l > 0) Text("On pace for $pace. ${if (pace >= ps.expect) "He likes what he sees." else "He's watching."}", style = T.small, color = P.sub)
                    }
                    Ring(c.security, 48.dp, label = "JOB")
                }
                Spacer(Modifier.height(8.dp))
                GrowBar(c.security / 100f, Modifier.fillMaxWidth(), securityColor(c.security), 8.dp)
                if (lg.phase == Phase.REGULAR) {
                    val dl = Trades.deadlineDay(lg)
                    Text(if (lg.day <= dl) "Trade deadline: day $dl. Make deals on the Trades tab." else "The trade deadline has passed.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            val st = ProSeason.standings(lg, u.confId)
            val me = st.indexOf(u)
            val shown = st.withIndex().filter { (i, _) -> i < 6 || kotlin.math.abs(i - me) <= 1 }.map { it.value }.distinct()
            Section(lg.conf(u.confId).name, action = "All", onAction = { vm.leagueTab = 0; vm.select(Tab.LEAGUE) })
            GroupList(shown) { t -> StandingRow(vm, lg, st.indexOf(t) + 1, t, st.first()) }
        }
        item {
            val leaders = lg.rosterOf(u).filter { it.stats.gp > 0 }.sortedByDescending { it.stats.ppg }.take(4)
            if (leaders.isNotEmpty()) {
                Section("Your leaders")
                GroupList(leaders, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "%.1f pts · %.1f reb · %.1f ast · %.1f min".format(p.stats.ppg, p.stats.rpg, p.stats.apg, p.stats.mpg)) }
            }
        }
        item {
            val news = lg.news.filter { it.season == lg.season }.takeLast(8).reversed()
            if (news.isNotEmpty()) {
                Section("Around the league")
                GroupList(news) { n -> ListRow { Text(n.text, style = T.small, color = if (n.mine) P.ink else P.sub, modifier = Modifier.weight(1f)) } }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun securityColor(v: Int) = when { v >= 55 -> P.good; v >= 30 -> P.warn; else -> P.bad }

// ---------- team ----------

@Composable
fun NbaCoachTeam(vm: GameViewModel, lg: League) {
    val u = lg.user
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("${lg.conf(u.confId).name} · Coach ${lg.userCoach.last}", u.name, "${teamLine(lg, u)} · ${Payroll.status(lg, u)}") { TeamBadge(u, 46.dp) }
            Segmented(listOf("Roster", "Lineup", "Payroll"), vm.teamTab.coerceIn(0, 2), { vm.teamTab = it })
            Spacer(Modifier.height(6.dp))
        }
        when (vm.teamTab.coerceIn(0, 2)) {
            0 -> rosterList(vm, lg, u, mine = true)
            1 -> lineup(vm, lg, u)
            else -> item { PayrollPanel(vm, lg, u) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PayrollPanel(vm: GameViewModel, lg: League, t: Team) {
    val total = Payroll.total(lg, t)
    val cap = Payroll.cap(lg)
    val tax = Payroll.tax(lg)
    Section("Payroll")
    Panel {
        Row {
            Stat("Payroll", Road.dollars(total), Modifier.weight(1f), style = T.numSm)
            Stat("Cap", Road.dollars(cap), Modifier.weight(1f), style = T.numSm)
            Stat("Room", Road.dollars((cap - total).coerceAtLeast(0)), Modifier.weight(1f), if (total < cap) P.good else P.sub, T.numSm)
        }
        Spacer(Modifier.height(10.dp))
        GrowBar((total.toFloat() / tax).coerceAtMost(1f), Modifier.fillMaxWidth(), if (total > tax) P.bad else if (total > cap) P.warn else P.good, 8.dp)
        Text("${Payroll.status(lg, t)}. Tax line ${Road.dollars(tax)}; mid-level exception ${Road.dollars(Payroll.mle(lg))}.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
    }
    Section("Contracts")
    GroupList(lg.rosterOf(t).sortedByDescending { Payroll.salaryOf(lg, it) }, inset = 70.dp) { p ->
        PlayerLine(vm, lg, t, p, "${Road.dollars(Payroll.salaryOf(lg, p))} · ${if (p.contractYears <= 1) "expires this summer" else "${p.contractYears} years left"}") {
            Ring(p.ovr, 38.dp, textSize = 14.sp)
        }
    }
    val picks = Picks.owned(lg, t)
    if (picks.isNotEmpty()) {
        Section("First-round picks")
        Panel { Text(picks.joinToString(" · ") { Picks.label(lg, it).removePrefix("a ") }, style = T.small, color = P.ink) }
    }
}

// ---------- trades ----------

@Composable
fun NbaTradeScreen(vm: GameViewModel, lg: League) {
    val u = lg.user
    var otherId by remember { mutableIntStateOf(-1) }
    val give = remember { mutableStateListOf<Int>() }
    val get = remember { mutableStateListOf<Int>() }
    val givePicks = remember { mutableStateListOf<Int>() }
    val getPicks = remember { mutableStateListOf<Int>() }
    var result by remember { mutableStateOf<String?>(null) }
    val open = NbaCoach.canTrade(lg)
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Front office", "Trades", if (open) "Build a deal: pick a team, then what you send and what you want." else if (lg.phase == Phase.REGULAR) "The deadline has passed. Trades open again in the summer." else "Trades open after the draft.")
        }
        if (!open) return@LazyColumn
        val other = lg.teams.getOrNull(otherId)
        item {
            Section("Trade with")
            Panel {
                Picker("Team", other?.name ?: "Choose a team", lg.teams.filter { it.id != u.id }.sortedBy { it.name }.map { it.name to "${it.w}-${it.l} · ${Payroll.status(lg, it)}" }) { i ->
                    otherId = lg.teams.filter { it.id != u.id }.sortedBy { it.name }[i].id
                    get.clear(); getPicks.clear(); result = null
                }
            }
        }
        if (other == null) return@LazyColumn
        item {
            Section("You send")
            GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }) { p -> TradeRow(lg, p, p.id in give) { if (p.id in give) give.remove(p.id) else give.add(p.id); result = null } }
            val mine = Picks.owned(lg, u)
            if (mine.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Chips(mine.map { Picks.label(lg, it).removePrefix("a ") + if (Picks.key(it) in givePicks) " ✓" else "" }, -1, { i ->
                    val k = Picks.key(mine[i]); if (k in givePicks) givePicks.remove(k) else givePicks.add(k); result = null
                })
            }
            Section("You get")
            GroupList(lg.rosterOf(other).sortedByDescending { it.ovr }) { p -> TradeRow(lg, p, p.id in get) { if (p.id in get) get.remove(p.id) else get.add(p.id); result = null } }
            val theirs = Picks.owned(lg, other)
            if (theirs.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Chips(theirs.map { Picks.label(lg, it).removePrefix("a ") + if (Picks.key(it) in getPicks) " ✓" else "" }, -1, { i ->
                    val k = Picks.key(theirs[i]); if (k in getPicks) getPicks.remove(k) else getPicks.add(k); result = null
                })
            }
        }
        item {
            val gp = lg.rosterOf(u).filter { it.id in give }
            val gt = lg.rosterOf(other).filter { it.id in get }
            val pa = lg.proState?.picks.orEmpty().filter { Picks.key(it) in givePicks && it.owner == u.id }
            val pb = lg.proState?.picks.orEmpty().filter { Picks.key(it) in getPicks && it.owner == other.id }
            val v = NbaCoach.judge(lg, other, gp, gt, pa, pb)
            Section("The deal")
            Panel {
                Text("Salaries: you send ${Road.dollars(gp.sumOf { Payroll.salaryOf(lg, it) })}, you take back ${Road.dollars(gt.sumOf { Payroll.salaryOf(lg, it) })}.", style = T.small, color = P.sub)
                if (gp.isNotEmpty() || gt.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    val ratio = if (v.get > 0) (v.give / v.get).coerceIn(0.0, 2.0) else 0.0
                    Text("How the ${other.name} see it", style = T.label, color = P.sub)
                    GrowBar((ratio / 2).toFloat(), Modifier.fillMaxWidth().padding(vertical = 4.dp), if (v.ok) P.good else if (ratio >= 0.85) P.warn else P.bad, 8.dp)
                    Text(v.text, style = T.bodyB, color = if (v.ok) P.good else P.ink)
                }
                result?.let { Text(it, style = T.bodyB, color = P.accent, modifier = Modifier.padding(top = 6.dp)) }
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Propose the trade", Modifier.fillMaxWidth(), enabled = v.ok) {
                    vm.act { l ->
                        val r = NbaCoach.trade(l, l.team(other.id), l.rosterOf(l.user).filter { it.id in give }, l.rosterOf(l.team(other.id)).filter { it.id in get },
                            l.proState?.picks.orEmpty().filter { Picks.key(it) in givePicks && it.owner == l.userTeamId }, l.proState?.picks.orEmpty().filter { Picks.key(it) in getPicks && it.owner == other.id })
                        result = if (r.ok) "Done. The trade is official." else r.text
                    }
                    give.clear(); get.clear(); givePicks.clear(); getPicks.clear()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TradeRow(lg: League, p: Player, picked: Boolean, onClick: () -> Unit) {
    ListRow(onClick = onClick, modifier = if (picked) Modifier.background(P.accentSoft) else Modifier) {
        PlayerJersey(p, if (p.teamId >= 0) lg.team(p.teamId) else null, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))} · ${Trades.tier(p)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
        }
        Ring(p.ovr, 36.dp, textSize = 13.sp)
        Spacer(Modifier.width(6.dp))
        Text(if (picked) "✓" else "+", style = T.numSm, color = if (picked) P.accent else P.faint, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
    }
}

// ---------- the offseason ----------

@Composable
fun NbaCoachOffseason(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    val u = lg.user
    val c = lg.userCoach
    if (ps.fired) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                ScreenHeader("${lg.season} · Offseason", "You've been fired", "${c.name} · ${c.w}-${c.l} career")
                Panel(color = P.hero) {
                    Text("THE OWNER HAS DECIDED", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text("The ${u.name} are moving on.", style = T.head, color = P.onHero)
                    Text("Teams with openings have called. Pick your next job.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                }
                Section("Job offers")
                GroupList(ps.jobOffers.map { lg.team(it) }) { t ->
                    ListRow {
                        TeamBadge(t, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = T.bodyB, color = P.ink)
                            Text("${t.w}-${t.l} last season · ${Payroll.status(lg, t)}", style = T.small, color = P.sub)
                        }
                        SmallButton("Take it", primary = true) { vm.act { NbaCoach.takeJob(it, t.id) } }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        return
    }
    val step = ps.step
    Column(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
        item { ScreenHeader("${lg.season} · Offseason", when (step) { ProStep.REVIEW -> "Season review"; ProStep.DRAFT -> "Draft and free agents"; ProStep.FREE_AGENCY -> "Free agency"; ProStep.SUMMER -> "Training camp" }, "Coach ${c.name} · ${u.name}") { TeamBadge(u, 46.dp) } }
        when (step) {
            ProStep.REVIEW -> item {
                val last = c.history.lastOrNull()
                Panel(color = P.hero) {
                    Text("${lg.season} · ${last?.result?.uppercase() ?: ""}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text("${u.w}-${u.l}", style = T.hero, color = P.onHero)
                    Text("Projected ${ps.expect} wins. Job security: ${NbaCoach.securityLabel(c.security).lowercase()} (${c.security}).", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                }
                ps.history.lastOrNull()?.let { h ->
                    Section("Around the league")
                    Panel {
                        Text("Champion: ${lg.teams.getOrNull(h.champion)?.name ?: "—"}", style = T.bodyB, color = P.ink)
                        if (h.mvp.isNotEmpty()) Text("MVP: ${h.mvp}", style = T.small, color = P.sub)
                        if (h.finalsMvp.isNotEmpty()) Text("Finals MVP: ${h.finalsMvp}", style = T.small, color = P.sub)
                        if (h.roy.isNotEmpty()) Text("Rookie of the Year: ${h.roy}", style = T.small, color = P.sub)
                    }
                }
                Section("Your career")
                Panel { Text("${c.w}-${c.l} · ${c.tourneys} playoff trip${if (c.tourneys == 1) "" else "s"} · ${c.titles} title${if (c.titles == 1) "" else "s"}", style = T.bodyB, color = P.ink) }
                Text("Next: the lottery and the draft, then your free agents.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 10.dp))
            }
            ProStep.DRAFT -> {
                item {
                    val rookies = lg.rosterOf(u).filter { it.proYears == 0 }
                    Section("Your draft")
                    if (rookies.isEmpty()) Panel { Text("No picks this year.", style = T.body, color = P.sub) }
                    GroupList(rookies, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.awards.firstOrNull { it.contains("Draft #") } ?: "Rookie"} · ${p.posLabel} · ${p.highSchool}") }
                    Section("Your free agents")
                    Text("Check the players you want back. They ask for their market value, and other teams can still outbid you.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
                }
                item {
                    val exp = NbaCoach.expiring(lg)
                    if (exp.isEmpty()) Panel { Text("Nobody's contract is up.", style = T.body, color = P.sub) }
                    GroupList(exp) { p ->
                        val keep = p.id in ps.userKeep
                        ListRow(onClick = { vm.act { if (keep) it.proState!!.userKeep.remove(p.id) else it.proState!!.userKeep.add(p.id) } }, modifier = if (keep) Modifier.background(P.accentSoft) else Modifier) {
                            PlayerJersey(p, u, 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1)
                                Text("${p.posLabel} · age ${p.age} · asks ${Road.dollars(NbaCoach.ask(lg, p))}", style = T.small.copy(fontSize = 12.sp), color = P.sub)
                            }
                            Ring(p.ovr, 36.dp, textSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Tag(if (keep) "RE-SIGN" else "LET GO", if (keep) P.good else P.faint)
                        }
                    }
                }
                item {
                    val room = NbaCoach.room(lg)
                    Section("Free agent market")
                    Panel {
                        Row {
                            Stat("Cap room", Road.dollars(room.coerceAtLeast(0)), Modifier.weight(1f), if (room > 0) P.good else P.sub, T.numSm)
                            Stat("Mid-level", Road.dollars(Payroll.mle(lg)), Modifier.weight(1f), style = T.numSm)
                            Stat("Offers", "${ps.userOffers.size}", Modifier.weight(1f), style = T.numSm)
                        }
                        Text("Offer what a player asks. With cap room you can pay up to it; over the cap, one offer up to the mid-level exception.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                item {
                    GroupList(NbaCoach.market(lg).take(30), inset = 70.dp) { p ->
                        val offered = p.id in ps.userOffers
                        val ask = NbaCoach.ask(lg, p)
                        ListRow(onClick = { vm.act { if (offered) it.proState!!.userOffers.remove(p.id) else it.proState!!.userOffers[p.id] = ask } }, modifier = if (offered) Modifier.background(P.accentSoft) else Modifier) {
                            PlayerJersey(p, lg.team(p.teamId), 38.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${lg.team(p.teamId).abbr} · ${p.posLabel} · age ${p.age} · asks ${Road.dollars(ask)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                            }
                            Ring(p.ovr, 36.dp, textSize = 13.sp)
                            Spacer(Modifier.width(8.dp))
                            Tag(if (offered) "OFFERED" else "OFFER", if (offered) P.accent else P.faint)
                        }
                    }
                    Text("You can also make trades now, on the Trades tab.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 10.dp))
                }
            }
            ProStep.FREE_AGENCY -> item {
                Section("Your summer")
                if (ps.userMoves.isEmpty()) Panel { Text("No changes to your roster in free agency.", style = T.body, color = P.sub) }
                GroupList(ps.userMoves) { line -> ListRow { Text(line, style = T.body, color = P.ink, modifier = Modifier.weight(1f)) } }
                if (ps.faMoves.isNotEmpty()) {
                    Section("Around the league")
                    GroupList(ps.faMoves.take(12)) { line -> ListRow { Text(line, style = T.small, color = P.sub, modifier = Modifier.weight(1f)) } }
                }
                Section("Your roster · ${lg.rosterOf(u).size} players")
                GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))}") }
            }
            ProStep.SUMMER -> item {
                Section("Camp report")
                val grown = lg.rosterOf(u).map { it to (it.ovr - (ps.before[it.id] ?: it.ovr)) }.sortedByDescending { it.second }
                GroupList(grown, inset = 70.dp) { (p, d) ->
                    PlayerLine(vm, lg, u, p, "${p.posLabel} · age ${p.age}") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (d != 0) Text(if (d > 0) "+$d" else "$d", style = T.numSm.copy(fontSize = 15.sp), color = if (d > 0) P.good else P.bad, modifier = Modifier.padding(end = 6.dp))
                            Ring(p.ovr, 36.dp, textSize = 13.sp)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
    Box(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp)) {
        PrimaryButton(
            when (step) { ProStep.REVIEW -> "Lottery and draft →"; ProStep.DRAFT -> "Open free agency →"; ProStep.FREE_AGENCY -> "Training camp →"; ProStep.SUMMER -> "Start the season →" },
            Modifier.fillMaxWidth(),
        ) { vm.proNext() }
    }
    }
}

// ---------- career ----------

@Composable
fun NbaCoachCareer(vm: GameViewModel, lg: League) {
    val c = lg.userCoach
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Career", c.name, "Head coach · ${lg.user.name}")
            Panel {
                Row {
                    Stat("Record", "${c.w}-${c.l}", Modifier.weight(1f), style = T.numSm)
                    Stat("Playoffs", "${c.tourneys}", Modifier.weight(1f), style = T.numSm)
                    Stat("Titles", "${c.titles}", Modifier.weight(1f), if (c.titles > 0) P.gold else P.ink, T.numSm)
                    Stat("Job", "${c.security}", Modifier.weight(1f), style = T.numSm)
                }
            }
            lg.proState?.collegeResume?.takeIf { it.isNotEmpty() }?.let {
                Section("Before the NBA")
                Panel { Text(it, style = T.bodyB, color = P.ink) }
            }
            Section("Season by season")
            if (c.history.isEmpty()) Panel { Text("Your first season is under way.", style = T.body, color = P.sub) }
            GroupList(c.history.reversed()) { h ->
                ListRow {
                    Text("${h.season}", style = T.numSm, color = P.accent, modifier = Modifier.width(56.dp))
                    Column(Modifier.weight(1f)) {
                        Text(h.team, style = T.bodyB, color = P.ink, maxLines = 1)
                        Text(h.result.ifEmpty { "—" }, style = T.small, color = if (h.result == "Champion") P.gold else P.sub)
                    }
                    Text("${h.w}-${h.l}", style = T.numSm, color = P.ink)
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = Gutter)) { SecondaryButton("Start a new career", Modifier.fillMaxWidth()) { vm.abandonCareer() } }
            Spacer(Modifier.height(24.dp))
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = GameType.NCAA to Ratings
}

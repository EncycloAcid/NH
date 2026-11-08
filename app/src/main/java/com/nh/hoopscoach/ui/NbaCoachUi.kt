package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.FaCase
import com.nh.hoopscoach.game.DraftNight
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Market
import com.nh.hoopscoach.game.NbaCoach
import com.nh.hoopscoach.game.NbaData
import com.nh.hoopscoach.game.Needs
import com.nh.hoopscoach.game.Payroll
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Picks
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.ProCareer
import com.nh.hoopscoach.game.ProLeague
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.ProStep
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.RealSeasons
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.game.TradeFinder
import com.nh.hoopscoach.game.Trades
import kotlin.math.abs
import kotlin.math.roundToInt

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
                    val big = com.nh.hoopscoach.game.Marquee.label(lg, g)
                    Text((if (big.isNotEmpty()) big else if (g.label.isNotEmpty()) g.label else if (g.home == u.id) "Home" else "Away").uppercase(), style = T.label, color = if (big.isNotEmpty()) P.gold else P.accent)
                    com.nh.hoopscoach.game.CoachRivals.pregame(lg, g)?.let { Text(it, style = T.small, color = P.bad) }
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
                ProjectionLines(lg)
                if (lg.phase == Phase.REGULAR) {
                    val dl = Trades.deadlineDay(lg)
                    Text(if (lg.day <= dl) "Trade deadline: day $dl. Make deals on the Trades tab." else "The trade deadline has passed.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
        item {
            val st = ProSeason.standings(lg, u.confId)
            val me = st.indexOf(u)
            val shown = st.withIndex().filter { (i, _) -> i < 6 || abs(i - me) <= 1 }.map { it.value }.distinct()
            Section(lg.conf(u.confId).name, action = "All", onAction = { vm.leagueTab = 0; vm.select(Tab.LEAGUE) })
            GroupList(shown) { t -> StandingRow(vm, lg, st.indexOf(t) + 1, t, st.first()) }
        }
        item { TitleDefenseCard(lg) }
        item { MarqueeCard(vm, lg) }
        item { GoalsCard(lg) }
        item { InjuryReport(vm, lg, u) }
        item {
            val leaders = lg.rosterOf(u).filter { it.stats.gp > 0 }.sortedByDescending { it.stats.ppg }.take(4)
            if (leaders.isNotEmpty()) {
                Section("Your leaders")
                GroupList(leaders, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "%.1f pts · %.1f reb · %.1f ast · %.1f min".format(p.stats.ppg, p.stats.rpg, p.stats.apg, p.stats.mpg)) }
            }
        }
        item {
            val news = lg.news.filter { it.season == lg.season }.takeLast(6).reversed()
            if (news.isNotEmpty()) {
                Section("Around the league", action = "All news", onAction = { vm.push(Route.News) })
                GroupList(news, inset = 34.dp) { n -> HeadlineRow(vm, lg, n) }
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
            Segmented(listOf("Roster", "Lineup", "Payroll", "Business", "Staff"), vm.teamTab.coerceIn(0, 4), { vm.teamTab = it })
            Spacer(Modifier.height(6.dp))
        }
        when (vm.teamTab.coerceIn(0, 4)) {
            0 -> rosterList(vm, lg, u, mine = true)
            1 -> lineup(vm, lg, u)
            2 -> item { PayrollPanel(vm, lg, u) }
            3 -> item { BusinessPanel(vm, lg, u) }
            else -> { staffItems(vm, lg); item { Section("Summer plans"); StaffLink(vm, lg) } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun PayrollPanel(vm: GameViewModel, lg: League, t: Team) {
    val total = Payroll.total(lg, t)
    val cap = Payroll.cap(lg)
    val tax = Payroll.tax(lg)
    val dead = Payroll.deadCap(lg, t)
    val retained = Payroll.retainedList(lg, t)
    var buyoutTarget by remember { mutableStateOf<Player?>(null) }
    var buyoutNotice by remember { mutableStateOf<String?>(null) }

    Section("Payroll & Cap Sheet")
    Panel {
        Row {
            Stat("Payroll", Road.dollars(total), Modifier.weight(1f), style = T.numSm)
            Stat("Cap", Road.dollars(cap), Modifier.weight(1f), style = T.numSm)
            Stat("Room", Road.dollars((cap - total).coerceAtLeast(0)), Modifier.weight(1f), if (total < cap) P.good else P.sub, T.numSm)
            if (dead > 0) Stat("Dead Cap", Road.dollars(dead), Modifier.weight(1f), P.warn, T.numSm)
        }
        Spacer(Modifier.height(10.dp))
        GrowBar((total.toFloat() / tax).coerceAtMost(1f), Modifier.fillMaxWidth(), if (total > tax) P.bad else if (total > cap) P.warn else P.good, 8.dp)
        Text("${Payroll.status(lg, t)}. Tax line ${Road.dollars(tax)}; apron ${Road.dollars(Payroll.apron(lg))}; MLE ${Road.dollars(Payroll.mle(lg))}.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
        buyoutNotice?.let { Text(it, style = T.small, color = P.accent, modifier = Modifier.padding(top = 6.dp)) }
    }

    if (retained.isNotEmpty()) {
        Section("Retained & Buyout Dead Cap (${retained.size}/${Payroll.MAX_RETAINED})")
        GroupList(retained) { r ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(r.name, style = T.bodyB, color = P.ink)
                    Text("${r.kind} · ${r.yearsLeft} ${if (r.yearsLeft == 1) "yr" else "yrs"} remaining", style = T.small, color = P.sub)
                }
                Text(Road.dollars(r.amount) + "/yr", style = T.numSm, color = P.warn)
            }
        }
    }

    val canBuyout = t.id == lg.userTeamId && lg.rosterOf(t).size > 10 && Payroll.canRetainMore(lg, t)
    Section("Contracts (${lg.rosterOf(t).size}/${ProLeague.MAX_ROSTER})")
    GroupList(lg.rosterOf(t).sortedByDescending { Payroll.salaryOf(lg, it) }, inset = 70.dp) { p ->
        val sal = Payroll.salaryOf(lg, p)
        val delta = Trades.contractDelta(lg, p)
        val clShort = Payroll.clauseShort(p.clause)
        val stLabel = if (p.structure == "FRONT" || p.structure == "BACK") " · ${Payroll.structureLabel(p.structure)}" else ""
        val yrsText = if (p.contractYears <= 1) "1 yr (expiring)" else "${p.contractYears} yrs"
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            PlayerJersey(p, t, 38.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (clShort.isNotEmpty()) Tag(clShort, if (p.clause == "NTC") P.bad else P.accent)
                    if (delta >= 1.2) Tag("BARGAIN", P.good)
                    else if (delta <= -1.2) Tag("OVERPAID", P.bad)
                }
                Text("${p.posLabel} · age ${p.age} · ${Road.dollars(sal)}/yr · $yrsText$stLabel", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
            }
            Ring(p.ovr, 34.dp, textSize = 12.sp)
            if (canBuyout && p.id != lg.heroId) {
                Spacer(Modifier.width(6.dp))
                SmallButton("Buyout", danger = true) { buyoutTarget = p }
            }
        }
    }

    val picks = Picks.owned(lg, t)
    if (picks.isNotEmpty()) {
        Section("First-round picks (${picks.size})")
        Panel {
            Text(picks.joinToString(" · ") { Picks.label(lg, it).removePrefix("a ") + " (${Picks.points(lg, it)} pts)" }, style = T.small, color = P.ink)
        }
    }

    buyoutTarget?.let { p ->
        val (stretchPerYr, stretchYrs, savedNow) = Payroll.buyoutPreview(lg, p)
        AlertDialog(
            onDismissRequest = { buyoutTarget = null },
            containerColor = P.surface,
            title = { Text("Buy Out & Stretch ${p.name}?", style = T.head, color = P.ink) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Current deal: ${Road.dollars(Payroll.salaryOf(lg, p))}/yr for ${p.contractYears.coerceAtLeast(1)} yr(s).", style = T.body, color = P.ink)
                    Text("Waiving & stretching spreads ${Road.dollars(stretchPerYr)}/yr in dead cap over $stretchYrs seasons.", style = T.small, color = P.sub)
                    Text("Immediate cap savings this season: +${Road.dollars(savedNow)}.", style = T.bodyB, color = P.good)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.act { l ->
                        val team = l.team(t.id)
                        val pl = l.playerOrNull(p.id)
                        if (pl != null && Payroll.buyout(l, team, pl)) {
                            buyoutNotice = "Bought out ${pl.name}, freeing ${Road.dollars(savedNow)} in cap space."
                        }
                    }
                    buyoutTarget = null
                }) { Text("Confirm Buyout", color = P.bad, style = T.bodyB) }
            },
            dismissButton = {
                TextButton(onClick = { buyoutTarget = null }) { Text("Cancel", color = P.sub, style = T.bodyB) }
            },
        )
    }
}

// ---------- trades (BLM 27 Overhaul) ----------

@Composable
fun NbaTradeScreen(vm: GameViewModel, lg: League) {
    var page by remember { mutableIntStateOf(0) }
    val open = NbaCoach.canTrade(lg)
    val ps = lg.proState
    val offers = ps?.tradeOffers.orEmpty().mapNotNull { o -> TradeFinder.dealOf(lg, o)?.let { o to it } }

    // Shared Trade Builder state so any DealCard or Trade Block item can load directly into the Builder.
    var otherId by remember { mutableIntStateOf(-1) }
    val give = remember { mutableStateListOf<Int>() }
    val get = remember { mutableStateListOf<Int>() }
    val givePicks = remember { mutableStateListOf<Int>() }
    val getPicks = remember { mutableStateListOf<Int>() }
    val retainMine = remember { mutableStateMapOf<Int, Int>() }
    val retainTheirs = remember { mutableStateMapOf<Int, Int>() }
    val protMine = remember { mutableStateMapOf<Int, Int>() }
    val protTheirs = remember { mutableStateMapOf<Int, Int>() }
    var builderStatus by remember { mutableStateOf<String?>(null) }
    var shopPlayerId by remember { mutableIntStateOf(-1) }
    var targetPlayerId by remember { mutableIntStateOf(-1) }
    var deadlineFeed by remember { mutableStateOf<List<String>>(emptyList()) }

    fun clearBuilder() {
        give.clear(); get.clear(); givePicks.clear(); getPicks.clear()
        retainMine.clear(); retainTheirs.clear(); protMine.clear(); protTheirs.clear()
    }

    fun loadDeal(d: TradeFinder.Deal, msg: String = "The ${d.other.name} deal is in the builder. Change anything you like.") {
        otherId = d.other.id
        clearBuilder()
        give.addAll(d.give.map { it.id })
        get.addAll(d.get.map { it.id })
        givePicks.addAll(d.givePicks.map { Picks.key(it) })
        getPicks.addAll(d.getPicks.map { Picks.key(it) })
        retainMine.putAll(d.retainGive)
        retainTheirs.putAll(d.retainGet)
        protMine.putAll(d.protGive)
        protTheirs.putAll(d.protGet)
        builderStatus = msg
        page = 0
    }

    val blockCount = Needs.onBlockIds(lg, lg.user).size
    val tabs = listOf(
        "Build",
        "Block" + if (blockCount > 0) " ($blockCount)" else "",
        "Finder",
        "Offers" + if (offers.isNotEmpty()) " (${offers.size})" else "",
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader(
                "Front office · ${Needs.direction(lg, lg.user).label}",
                "Trade center",
                if (open) "Team overall ${TradeFinder.overall(lg, lg.user)} · ${Payroll.status(lg, lg.user)} · Needs: ${Needs.needsOf(lg, lg.user).joinToString(", ") { it.label }}"
                else if (lg.phase == Phase.REGULAR) "The trade deadline has passed. Trades open again in the offseason."
                else "Trades open after the season review.",
            )
            if (open) Segmented(tabs, page, { page = it })
        }
        if (!open) return@LazyColumn

        // BLM 27 Trade Deadline Day Countdown Banner when within 5 days of the deadline (or on deadline day)
        if (NbaCoach.isDeadlineWindow(lg) && ps != null) {
            item {
                Spacer(Modifier.height(10.dp))
                Panel(color = P.hero) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("TRADE DEADLINE · HOUR ${ps.deadlineHour} OF 6", style = T.label, color = P.onHero.copy(alpha = 0.85f))
                            Text(
                                if (lg.day == Trades.deadlineDay(lg)) "Deadline day. Last chance to make a move."
                                else "${(Trades.deadlineDay(lg) - lg.day).let { if (it == 1) "1 day" else "$it days" }} until the trade deadline (day ${Trades.deadlineDay(lg)}).",
                                style = T.bodyB, color = P.onHero,
                            )
                        }
                        SmallButton("Next hour", primary = true, enabled = ps.deadlineHour < 6) {
                            vm.act { l -> deadlineFeed = NbaCoach.deadlineStep(l) }
                        }
                    }
                    if (deadlineFeed.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        for (line in deadlineFeed.take(4)) {
                            Text("• $line", style = T.small, color = P.onHero.copy(alpha = 0.92f))
                        }
                    }
                }
            }
        }

        when (page) {
            0 -> item {
                TradeBuilder(
                    vm = vm, lg = lg,
                    otherId = otherId, onOtherChange = { otherId = it; clearBuilder(); builderStatus = null },
                    give = give, get = get, givePicks = givePicks, getPicks = getPicks,
                    retainMine = retainMine, retainTheirs = retainTheirs,
                    protMine = protMine, protTheirs = protTheirs,
                    status = builderStatus, onStatus = { builderStatus = it },
                )
            }
            1 -> item {
                TradeBlockPage(
                    vm = vm, lg = lg,
                    onShopPlayer = { pid -> shopPlayerId = pid; targetPlayerId = -1; page = 2 },
                    onTargetPlayer = { pid -> targetPlayerId = pid; shopPlayerId = -1; page = 2 },
                    onStartTradeWith = { tid, pid ->
                        otherId = tid
                        clearBuilder()
                        if (pid != null) get.add(pid)
                        builderStatus = null
                        page = 0
                    },
                )
            }
            2 -> item {
                TradeFinderPage(
                    vm = vm, lg = lg,
                    initialShopId = shopPlayerId,
                    initialTargetId = targetPlayerId,
                    onClearInitial = { shopPlayerId = -1; targetPlayerId = -1 },
                    onEditInBuilder = { d -> loadDeal(d) },
                )
            }
            else -> item {
                TradeOffersPage(
                    vm = vm, lg = lg,
                    offers = offers,
                    onEditInBuilder = { d -> loadDeal(d, "The ${d.other.name} offer is in the builder: counter it or change it.") },
                )
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** BLM 27 Trade Builder with visual Trade Value Bar, Auto-Balance magic button, Salary Retention (0/25/50%), and Pick Protections. */
@Composable
private fun TradeBuilder(
    vm: GameViewModel,
    lg: League,
    otherId: Int,
    onOtherChange: (Int) -> Unit,
    give: MutableList<Int>,
    get: MutableList<Int>,
    givePicks: MutableList<Int>,
    getPicks: MutableList<Int>,
    retainMine: MutableMap<Int, Int>,
    retainTheirs: MutableMap<Int, Int>,
    protMine: MutableMap<Int, Int>,
    protTheirs: MutableMap<Int, Int>,
    status: String?,
    onStatus: (String?) -> Unit,
) {
    val u = lg.user
    val sortedTeams = remember(lg.teams.size, u.id) { lg.teams.filter { it.id != u.id }.sortedBy { it.name } }
    val other = lg.teams.getOrNull(otherId)?.takeIf { it.id != u.id }

    Section("Trade partner")
    Panel {
        Picker(
            "Partner",
            other?.name ?: "Choose an NBA team",
            sortedTeams.map { t ->
                val dir = Needs.direction(lg, t).label
                val needs = Needs.needsOf(lg, t).joinToString("/") { it.label }
                t.name to "${t.w}-${t.l} · $dir · Needs $needs · ${Payroll.status(lg, t)}"
            },
        ) { i -> onOtherChange(sortedTeams[i].id) }

        if (other != null) {
            Spacer(Modifier.height(10.dp))
            val dir = Needs.direction(lg, other)
            val needs = Needs.needsOf(lg, other)
            val surplus = Needs.surplusPositions(lg, other)
            val blockNames = lg.rosterOf(other).filter { Needs.isOnBlock(lg, other, it) }.take(3).joinToString(", ") { "${it.last} (${Ratings.shownPro(it.ovr)})" }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TeamBadge(other, 34.dp)
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${other.name} (${other.w}-${other.l})", style = T.bodyB, color = P.ink)
                        Tag(dir.label.uppercase(), when (dir) { Needs.Direction.CONTENDING -> P.good; Needs.Direction.NEUTRAL -> P.accent; Needs.Direction.REBUILDING -> P.warn })
                    }
                    Text(
                        "Needs: ${needs.joinToString(", ") { it.label }.ifEmpty { "None" }}" +
                            (if (surplus.isNotEmpty()) " · Surplus: ${surplus.joinToString(", ") { it.label }}" else "") +
                            (if (blockNames.isNotEmpty()) " · Block: $blockNames" else ""),
                        style = T.small, color = P.sub,
                    )
                }
            }
        }
    }
    if (other == null) return

    val gp = lg.rosterOf(u).filter { it.id in give }
    val gt = lg.rosterOf(other).filter { it.id in get }
    val pa = lg.proState?.picks.orEmpty().filter { Picks.key(it) in givePicks && it.owner == u.id }
    val pb = lg.proState?.picks.orEmpty().filter { Picks.key(it) in getPicks && it.owner == other.id }
    val v = NbaCoach.judge(lg, other, gp, gt, pa, pb, retainMine, retainTheirs, protMine, protTheirs)
    val outSal = gp.sumOf { Trades.effectiveSalary(lg, it, retainMine[it.id] ?: 0) }
    val inSal = gt.sumOf { Trades.effectiveSalary(lg, it, retainTheirs[it.id] ?: 0) }

    // BLM 27 Live Trade Value Bar & Controls Card
    Section("Value and cap check", action = if (give.isNotEmpty() || get.isNotEmpty() || givePicks.isNotEmpty() || getPicks.isNotEmpty()) "Clear" else null, onAction = {
        give.clear(); get.clear(); givePicks.clear(); getPicks.clear()
        retainMine.clear(); retainTheirs.clear(); protMine.clear(); protTheirs.clear()
        onStatus(null)
    })
    Panel {
        val totalPts = (v.givePts + v.getPts).coerceAtLeast(1)
        val myShare = if (v.givePts == 0 && v.getPts == 0) 0.5f else (v.givePts.toFloat() / totalPts).coerceIn(0.08f, 0.92f)
        val barColor = when {
            v.ok -> P.good
            v.getPts > 0 && v.givePts >= v.getPts * 0.85 -> P.warn
            else -> P.bad
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("YOU OFFER", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Text("${v.givePts} pts", style = T.numSm, color = if (v.ok) P.good else P.ink)
            }
            Tag(if (v.ok) "THEY'D ACCEPT" else if (v.givePts == 0 && v.getPts == 0) "PICK PLAYERS" else "NOT ENOUGH YET", if (v.ok) P.good else if (v.givePts == 0 && v.getPts == 0) P.sub else P.bad)
            Column(horizontalAlignment = Alignment.End) {
                Text("${other.abbr} WANTS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Text("${v.getPts} pts", style = T.numSm, color = P.ink)
            }
        }
        Spacer(Modifier.height(6.dp))
        GrowBar(myShare, Modifier.fillMaxWidth(), barColor, 10.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            "Salaries: you send ${Road.dollars(outSal)} · you take back ${Road.dollars(inSal)}",
            style = T.small, color = P.sub,
        )
        if (gp.isNotEmpty() || gt.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            ImpactRow("Your team", TradeFinder.impact(lg, u, gp, gt, retainMine, retainTheirs))
            ImpactRow("The ${other.name.substringAfterLast(' ')}", TradeFinder.impact(lg, other, gt, gp, retainTheirs, retainMine))
        }
        Spacer(Modifier.height(6.dp))
        Text(v.text, style = T.bodyB, color = if (v.ok) P.good else P.ink)
        status?.let { Text(it, style = T.small, color = P.accent, modifier = Modifier.padding(top = 4.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Balance it", Modifier.weight(1f)) {
                val gSet = give.toMutableSet()
                val tSet = get.toMutableSet()
                val gpSet = givePicks.toMutableSet()
                val tpSet = getPicks.toMutableSet()
                val msg = NbaCoach.autoBalance(lg, other, gSet, tSet, gpSet, tpSet, retainMine, retainTheirs, protMine, protTheirs)
                give.clear(); give.addAll(gSet)
                get.clear(); get.addAll(tSet)
                givePicks.clear(); givePicks.addAll(gpSet)
                getPicks.clear(); getPicks.addAll(tpSet)
                onStatus(msg)
            }
            PrimaryButton("Propose trade", Modifier.weight(1f), enabled = v.ok) {
                vm.act { l ->
                    val partner = l.team(other.id)
                    val res = NbaCoach.trade(
                        l, partner,
                        l.rosterOf(l.user).filter { it.id in give },
                        l.rosterOf(partner).filter { it.id in get },
                        l.proState?.picks.orEmpty().filter { Picks.key(it) in givePicks && it.owner == l.userTeamId },
                        l.proState?.picks.orEmpty().filter { Picks.key(it) in getPicks && it.owner == partner.id },
                        retainMine.toMap(), retainTheirs.toMap(), protMine.toMap(), protTheirs.toMap(),
                    )
                    onStatus(if (res.ok) "Done. The trade with the ${partner.name} is official." else res.text)
                    if (res.ok) l.proState?.tradeOffers?.removeAll { o -> o.teamId == partner.id }
                }
                give.clear(); get.clear(); givePicks.clear(); getPicks.clear()
                retainMine.clear(); retainTheirs.clear(); protMine.clear(); protTheirs.clear()
            }
        }
    }

    // ---------- You Send ----------
    Section("You Send (${u.name})")
    GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }) { p ->
        val picked = p.id in give
        val retPct = retainMine[p.id] ?: 0
        Column {
            TradeAssetRow(
                lg = lg, p = p, targetTeam = other, picked = picked, retainPct = retPct,
                onClick = {
                    if (picked) { give.remove(p.id); retainMine.remove(p.id) } else give.add(p.id)
                    onStatus(null)
                },
            )
            if (picked) {
                RetainBar(
                    label = "Retain salary on ${p.last}:",
                    salary = Payroll.salaryOf(lg, p),
                    currentPct = retPct,
                    canRetain = Payroll.canRetainMore(lg, u) || retPct > 0,
                    onSelect = { pct -> if (pct == 0) retainMine.remove(p.id) else retainMine[p.id] = pct; onStatus(null) },
                )
            }
        }
    }
    val myPicks = Picks.owned(lg, u)
    if (myPicks.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Panel(padding = PaddingValues(12.dp)) {
            Text("YOUR 1ST-ROUND PICKS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Spacer(Modifier.height(6.dp))
            for (pk in myPicks) {
                val k = Picks.key(pk)
                val picked = k in givePicks
                val prot = protMine[k] ?: pk.protection
                PickTradeRow(
                    lg = lg, pk = pk, picked = picked, prot = prot,
                    onToggle = {
                        if (picked) { givePicks.remove(k); protMine.remove(k) } else { givePicks.add(k); protMine[k] = pk.protection }
                        onStatus(null)
                    },
                    onProtChange = { newProt -> protMine[k] = newProt; onStatus(null) },
                )
            }
        }
    }

    // ---------- You Get ----------
    Section("You Receive (${other.name})")
    GroupList(lg.rosterOf(other).sortedByDescending { it.ovr }) { p ->
        val picked = p.id in get
        val retPct = retainTheirs[p.id] ?: 0
        Column {
            TradeAssetRow(
                lg = lg, p = p, targetTeam = u, picked = picked, retainPct = retPct,
                onClick = {
                    if (picked) { get.remove(p.id); retainTheirs.remove(p.id) } else get.add(p.id)
                    onStatus(null)
                },
            )
            if (picked) {
                RetainBar(
                    label = "Ask ${other.abbr} to retain:",
                    salary = Payroll.salaryOf(lg, p),
                    currentPct = retPct,
                    canRetain = Payroll.canRetainMore(lg, other) || retPct > 0,
                    onSelect = { pct -> if (pct == 0) retainTheirs.remove(p.id) else retainTheirs[p.id] = pct; onStatus(null) },
                )
            }
        }
    }
    val theirPicks = Picks.owned(lg, other)
    if (theirPicks.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Panel(padding = PaddingValues(12.dp)) {
            Text("${other.abbr} 1ST-ROUND PICKS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Spacer(Modifier.height(6.dp))
            for (pk in theirPicks) {
                val k = Picks.key(pk)
                val picked = k in getPicks
                val prot = protTheirs[k] ?: pk.protection
                PickTradeRow(
                    lg = lg, pk = pk, picked = picked, prot = prot,
                    onToggle = {
                        if (picked) { getPicks.remove(k); protTheirs.remove(k) } else { getPicks.add(k); protTheirs[k] = pk.protection }
                        onStatus(null)
                    },
                    onProtChange = { newProt -> protTheirs[k] = newProt; onStatus(null) },
                )
            }
        }
    }
}

@Composable
private fun TradeAssetRow(
    lg: League,
    p: Player,
    targetTeam: Team,
    picked: Boolean,
    retainPct: Int = 0,
    onClick: () -> Unit,
) {
    val owner = if (p.teamId >= 0) lg.team(p.teamId) else lg.user
    val untouch = Needs.isUntouchable(lg, owner, p)
    val onBlock = Needs.isOnBlock(lg, owner, p)
    val fitsTarget = Needs.fitsNeed(lg, targetTeam, p)
    val pts = Trades.pointsFor(lg, targetTeam, p, retainPct)
    val effSal = Trades.effectiveSalary(lg, p, retainPct)
    val clShort = Payroll.clauseShort(p.clause)

    ListRow(onClick = onClick, modifier = if (picked) Modifier.background(P.accentSoft) else Modifier) {
        PlayerJersey(p, if (p.teamId >= 0) owner else null, 38.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (p.clause == "NTC") Tag("NTC", P.bad)
                else if (clShort.isNotEmpty()) Tag(clShort, P.accent)
                if (untouch) Tag("UNTOUCHABLE", P.bad)
                else if (onBlock) Tag("BLOCK", P.warn)
                if (fitsTarget) Tag("FITS NEED", P.good)
            }
            Text(
                "${p.posLabel} · age ${p.age} · ${Road.dollars(effSal)}${if (retainPct > 0) " ($retainPct% ret)" else ""} · ${p.contractYears.coerceAtLeast(1)}y",
                style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 6.dp)) {
            Text(if (pts >= 0) "+$pts" else "$pts", style = T.numSm.copy(fontSize = 13.sp), color = if (pts < 0) P.bad else if (pts >= 600) P.good else P.ink)
            Text("VAL", style = T.label.copy(fontSize = 9.sp), color = P.faint)
        }
        Ring(p.ovr, 34.dp, textSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Text(if (picked) "✓" else "+", style = T.numSm, color = if (picked) P.accent else P.faint, modifier = Modifier.width(18.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun RetainBar(
    label: String,
    salary: Int,
    currentPct: Int,
    canRetain: Boolean,
    onSelect: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().background(P.surface2.copy(alpha = 0.55f)).padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(label, style = T.small.copy(fontSize = 12.sp), color = P.sub, modifier = Modifier.weight(1f))
        for (pct in listOf(0, 25, 50)) {
            val sel = currentPct == pct
            val enabled = pct == 0 || canRetain
            val amt = (salary * (pct / 100.0)).roundToInt() / 10 * 10
            val txt = if (pct == 0) "0%" else "$pct% (${Road.dollars(amt)})"
            Box(
                Modifier.clip(RoundedCornerShape(8.dp))
                    .background(if (sel) P.accent else P.surface)
                    .border(1.dp, if (sel) P.accent else P.line, RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled) { onSelect(pct) }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(txt, style = T.small.copy(fontSize = 11.sp), color = if (sel) P.onAccent else if (enabled) P.ink else P.faint)
            }
        }
    }
}

@Composable
private fun PickTradeRow(
    lg: League,
    pk: com.nh.hoopscoach.game.PickAsset,
    picked: Boolean,
    prot: Int,
    onToggle: () -> Unit,
    onProtChange: (Int) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                .background(if (picked) P.accentSoft else Color.Transparent)
                .clickable(onClick = onToggle)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                Picks.label(lg, pk, prot).removePrefix("a ").replaceFirstChar { it.uppercase() },
                style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, modifier = Modifier.weight(1f),
            )
            Text("+${Picks.points(lg, pk, prot)} pts", style = T.numSm.copy(fontSize = 13.sp), color = P.accent)
            Spacer(Modifier.width(8.dp))
            Text(if (picked) "✓" else "+", style = T.numSm, color = if (picked) P.accent else P.faint)
        }
        if (picked) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 8.dp, top = 4.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Protection:", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                for (pVal in Picks.PROTECTIONS) {
                    val sel = prot == pVal
                    val lbl = if (pVal == 0) "Unprotected" else Picks.protectionLabel(pVal).removeSuffix(" prot.")
                    Box(
                        Modifier.clip(RoundedCornerShape(8.dp))
                            .background(if (sel) P.accent else P.surface)
                            .border(1.dp, if (sel) P.accent else P.line, RoundedCornerShape(8.dp))
                            .clickable { onProtChange(pVal) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(lbl, style = T.small.copy(fontSize = 11.sp), color = if (sel) P.onAccent else P.sub)
                    }
                }
            }
        }
    }
}

/** BLM 27 Trade Block: manage your own Trade Block / Untouchables / Needs / Surplus, inspect any team's block, or browse the League Trade Block. */
@Composable
private fun TradeBlockPage(
    vm: GameViewModel,
    lg: League,
    onShopPlayer: (Int) -> Unit,
    onTargetPlayer: (Int) -> Unit,
    onStartTradeWith: (Int, Int?) -> Unit,
) {
    var mode by remember { mutableIntStateOf(0) }
    var inspectTeamId by remember { mutableIntStateOf(lg.teams.firstOrNull { it.id != lg.userTeamId }?.id ?: 0) }
    val u = lg.user

    Spacer(Modifier.height(10.dp))
    Chips(listOf("My Block & Needs", "Team Blocks", "League Block"), mode, { mode = it })

    when (mode) {
        0 -> {
            val myNeeds = Needs.needsOf(lg, u)
            val mySurplus = Needs.surplusPositions(lg, u)
            Section("Your needs and direction", action = "Suggest", onAction = { vm.act { Needs.autoDetectBlock(it, it.user) } })
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Direction:", style = T.small, color = P.sub)
                    val dir = Needs.direction(lg, u)
                    Tag(dir.label.uppercase(), P.accent)
                    Spacer(Modifier.weight(1f))
                    Text("Tap a position to change it", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
                Spacer(Modifier.height(10.dp))
                Text("POSITIONS YOU NEED (TEAMS OFFER FOR THESE)", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pos in Pos.entries) {
                        val active = pos in myNeeds
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (active) P.good else P.surface2)
                                .clickable { vm.act { Needs.toggleNeedPos(it, it.user, pos) } }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(pos.label, style = T.bodyB.copy(fontSize = 13.sp), color = if (active) Color.White else P.sub) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text("POSITIONAL SURPLUS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (pos in Pos.entries) {
                        val active = pos in mySurplus
                        Box(
                            Modifier.clip(RoundedCornerShape(10.dp))
                                .background(if (active) P.warn else P.surface2)
                                .clickable { vm.act { Needs.toggleSurplusPos(it, it.user, pos) } }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        ) { Text(pos.label, style = T.bodyB.copy(fontSize = 13.sp), color = if (active) Color.White else P.sub) }
                    }
                }
            }

            Section("Your roster: shop or protect")
            GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }) { p ->
                val onBlock = Needs.isOnBlock(lg, u, p)
                val untouch = Needs.isUntouchable(lg, u, p)
                // The player on one line, his three buttons on the next, so names never get squeezed.
                Column {
                    ListRow(onClick = { vm.openPlayer(p.id) }) {
                        PlayerJersey(p, u, 36.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))} · trade value ${Trades.points(lg, p)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                        }
                        Ring(p.ovr, 32.dp, textSize = 12.sp)
                    }
                    Row(Modifier.fillMaxWidth().padding(start = 60.dp, end = 14.dp, bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallButton(if (onBlock) "On the block" else "Put on block", primary = onBlock) {
                            vm.act { Needs.toggleBlock(it, it.user, p.id) }
                        }
                        SmallButton(if (untouch) "Untouchable" else "Protect", danger = untouch) {
                            vm.act { Needs.toggleUntouchable(it, it.user, p.id) }
                        }
                        SmallButton("Find offers") { onShopPlayer(p.id) }
                    }
                }
            }
        }
        1 -> {
            val otherTeams = lg.teams.filter { it.id != u.id }.sortedBy { it.name }
            val t = lg.teams.getOrNull(inspectTeamId)?.takeIf { it.id != u.id } ?: otherTeams.first()
            Section("See their block")
            Panel {
                Picker("Team", t.name, otherTeams.map { it.name to "${it.w}-${it.l} · ${Needs.direction(lg, it).label}" }) { i ->
                    inspectTeamId = otherTeams[i].id
                }
                Spacer(Modifier.height(10.dp))
                val dir = Needs.direction(lg, t)
                val needs = Needs.needsOf(lg, t)
                val surplus = Needs.surplusPositions(lg, t)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(t, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${t.name} (${t.w}-${t.l})", style = T.bodyB, color = P.ink)
                        Text("${dir.label} · ${Payroll.status(lg, t)}", style = T.small, color = P.sub)
                    }
                    SmallButton("Build deal", primary = true) { onStartTradeWith(t.id, null) }
                }
                Spacer(Modifier.height(8.dp))
                Text("Needs: ${needs.joinToString(", ") { it.label }.ifEmpty { "None" }} · Surplus: ${surplus.joinToString(", ") { it.label }.ifEmpty { "None" }}", style = T.small, color = P.ink)
            }
            val blockList = lg.rosterOf(t).filter { Needs.isOnBlock(lg, t, it) }.sortedByDescending { it.ovr }
            Section("On the Block (${blockList.size})")
            if (blockList.isEmpty()) Panel { Text("The ${t.name} haven't placed anyone on the trade block.", style = T.small, color = P.sub) }
            GroupList(blockList) { p ->
                ListRow(onClick = { onTargetPlayer(p.id) }) {
                    PlayerJersey(p, t, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink)
                        Text("${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))} · ${Trades.points(lg, p)} pts", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 34.dp, textSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    SmallButton("Target", primary = true) { onTargetPlayer(p.id) }
                }
            }
            val untouchList = lg.rosterOf(t).filter { Needs.isUntouchable(lg, t, it) }.sortedByDescending { it.ovr }
            Section("Untouchables (${untouchList.size})")
            if (untouchList.isEmpty()) Panel { Text("Nobody on the ${t.name} is marked untouchable.", style = T.small, color = P.sub) }
            GroupList(untouchList) { p ->
                ListRow(onClick = { vm.openPlayer(p.id) }) {
                    PlayerJersey(p, t, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink)
                        Text("${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))}", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 34.dp, textSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Tag("UNTOUCHABLE", P.bad)
                }
            }
        }
        else -> {
            var posFilter by remember { mutableIntStateOf(0) }
            Spacer(Modifier.height(6.dp))
            Chips(listOf("All", "PG", "SG", "SF", "PF", "C", "Fits a need"), posFilter, { posFilter = it })
            val allOnBlock = lg.teams.filter { it.id != u.id }.flatMap { t ->
                val bIds = Needs.onBlockIds(lg, t)
                lg.rosterOf(t).filter { it.id in bIds }
            }.filter { p ->
                when (posFilter) {
                    0 -> true
                    in 1..5 -> p.pos.ordinal == posFilter - 1 || p.pos2?.ordinal == posFilter - 1
                    else -> Needs.fitsNeed(lg, u, p)
                }
            }.sortedByDescending { it.ovr }

            Section("League-Wide Trade Block (${allOnBlock.size})")
            if (allOnBlock.isEmpty()) Panel { Text("No matching players on the league trade block.", style = T.body, color = P.sub) }
            GroupList(allOnBlock) { p ->
                val t = lg.team(p.teamId)
                ListRow(onClick = { onTargetPlayer(p.id) }) {
                    PlayerJersey(p, t, 38.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (Needs.fitsNeed(lg, u, p)) Tag("FITS NEED", P.good)
                        }
                        Text("${t.abbr} · ${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))} · ${Trades.points(lg, p)} pts", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    }
                    Ring(p.ovr, 34.dp, textSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    SmallButton("Packages", primary = true) { onTargetPlayer(p.id) }
                    Spacer(Modifier.width(4.dp))
                    SmallButton("Build") { onStartTradeWith(t.id, p.id) }
                }
            }
        }
    }
}

/** BLM 27 Trade Finder: Shop My Player, Find a Player, Fits My Needs, or Cap Relief / Salary Dumps. */
@Composable
private fun TradeFinderPage(
    vm: GameViewModel,
    lg: League,
    initialShopId: Int,
    initialTargetId: Int,
    onClearInitial: () -> Unit,
    onEditInBuilder: (TradeFinder.Deal) -> Unit,
) {
    var mode by remember(initialShopId, initialTargetId) {
        mutableIntStateOf(if (initialTargetId >= 0) 1 else 0)
    }
    var pick by remember(initialShopId, initialTargetId) {
        mutableIntStateOf(if (initialShopId >= 0) initialShopId else if (initialTargetId >= 0) initialTargetId else -1)
    }
    var pos by remember { mutableIntStateOf(0) }
    val u = lg.user

    Spacer(Modifier.height(10.dp))
    Chips(listOf("Shop my player", "Find a player", "Fits my needs", "Cap relief"), mode, {
        mode = it
        pick = -1
        onClearInitial()
    })

    when (mode) {
        2 -> {
            val needed = Needs.needsOf(lg, u)
            Text(
                "Deals for players around the league at your needed positions (${needed.joinToString(", ") { it.label }}), prioritizing players on other teams' Trade Blocks.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
            )
            val deals = remember(vm.rev, needed) { TradeFinder.matchingNeeds(lg) }
            if (deals.isEmpty()) Panel { Text("No clean packages found for your current positional needs.", style = T.body, color = P.sub) }
            for (d in deals) {
                DealCard(vm, lg, d, "Targets your need at ${d.get.firstOrNull()?.pos?.label ?: ""}", onEditInBuilder) {}
                Spacer(Modifier.height(10.dp))
            }
            return
        }
        3 -> {
            Text(
                "Cap-relief trades that shed expensive or overpaid contracts from your books (using picks or 25% salary retention if needed).",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
            )
            val deals = remember(vm.rev) { TradeFinder.salaryDumps(lg) }
            if (deals.isEmpty()) Panel { Text("No salary-dump deals available right now.", style = T.body, color = P.sub) }
            for (d in deals) {
                val saved = Payroll.total(lg, u) - TradeFinder.mine(lg, d).payroll
                DealCard(vm, lg, d, "Saves ${Road.dollars(saved.coerceAtLeast(0))} in immediate cap commitments", onEditInBuilder) {}
                Spacer(Modifier.height(10.dp))
            }
            return
        }
    }

    Text(
        if (mode == 0) "Pick one of your players: every team's best offer for him, with what each deal does to your team."
        else "Pick a player you want: the packages his team would accept for him, best for your team first.",
        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
    )
    val p = lg.playerOrNull(pick)
    if (p == null) {
        if (mode == 0) {
            GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }) { x -> TradeAssetRow(lg, x, u, false) { pick = x.id } }
        } else {
            Chips(listOf("All", "PG", "SG", "SF", "PF", "C"), pos, { pos = it })
            Spacer(Modifier.height(6.dp))
            val list = lg.teams.filter { it.id != u.id }.flatMap { lg.rosterOf(it) }
                .filter { pos == 0 || it.pos.ordinal == pos - 1 || it.pos2?.ordinal == pos - 1 }
                .sortedByDescending { it.ovr }.take(45)
            GroupList(list) { x -> TradeAssetRow(lg, x, u, false) { pick = x.id } }
        }
        return
    }
    Section(if (mode == 0) "Offers for ${p.name}" else "Ways to get ${p.name}", action = "Change", onAction = { pick = -1; onClearInitial() })
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerJersey(p, lg.team(p.teamId), 44.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink)
                Text("${lg.team(p.teamId).name} · ${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))} · ${Trades.points(lg, p)} pts", style = T.small, color = P.sub, maxLines = 1)
            }
            Ring(p.ovr, 40.dp, textSize = 14.sp)
        }
    }
    val deals = remember(p.id, vm.rev, mode) { if (mode == 0) TradeFinder.shop(lg, p) else TradeFinder.target(lg, p) }
    Spacer(Modifier.height(10.dp))
    if (deals.isEmpty()) {
        Panel {
            Text(
                if (mode == 0) "Nobody bites at his salary and value right now (try offering him in the Builder with salary retention)."
                else if (Needs.isUntouchable(lg, lg.team(p.teamId), p)) "The ${lg.team(p.teamId).name} have marked ${p.name} as Untouchable."
                else "No package from your non-untouchable roster gets it done.",
                style = T.body, color = P.sub,
            )
        }
    }
    for (d in deals) {
        DealCard(vm, lg, d, null, onEditInBuilder) { pick = -1; onClearInitial() }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun TradeOffersPage(
    vm: GameViewModel,
    lg: League,
    offers: List<Pair<com.nh.hoopscoach.game.TradeOffer, TradeFinder.Deal>>,
    onEditInBuilder: (TradeFinder.Deal) -> Unit,
) {
    Section("Incoming Trade Offers (${offers.size})", action = "Ask for offers", onAction = { vm.act { TradeFinder.makeOffers(it) } })
    if (offers.isEmpty()) {
        Panel {
            Text(
                "No offers on your desk right now. Put players on your Trade Block on the Block tab or tap 'Solicit Offers' to call around the league.",
                style = T.body, color = P.sub,
            )
        }
        return
    }
    for ((o, d) in offers) {
        DealCard(
            vm = vm, lg = lg, d = d, note = o.note,
            onEditInBuilder = onEditInBuilder,
            onDecline = { vm.act { l -> l.proState?.tradeOffers?.remove(o) } },
            onDone = { vm.act { l -> l.proState?.tradeOffers?.remove(o) } },
        )
        Spacer(Modifier.height(10.dp))
    }
}

/** A deal: who goes where, then what it does to each team, with buttons to Make Trade, Edit in Builder, or Decline. */
@Composable
private fun DealCard(
    vm: GameViewModel,
    lg: League,
    d: TradeFinder.Deal,
    note: String?,
    onEditInBuilder: ((TradeFinder.Deal) -> Unit)? = null,
    onDecline: (() -> Unit)? = null,
    onDone: () -> Unit,
) {
    val mine = TradeFinder.mine(lg, d)
    val theirs = TradeFinder.theirs(lg, d)
    val v = NbaCoach.judge(lg, d.other, d.give, d.get, d.givePicks, d.getPicks, d.retainGive, d.retainGet, d.protGive, d.protGet)
    var result by remember { mutableStateOf<String?>(null) }
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(d.other, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${d.other.name} (${d.other.w}-${d.other.l})", style = T.bodyB, color = P.ink, maxLines = 1)
                Text(note ?: "${Needs.direction(lg, d.other).label} · ${Payroll.status(lg, d.other)}", style = T.small, color = if (note != null) P.accent else P.sub, maxLines = 2)
            }
            Tag("${v.givePts} vs ${v.getPts} pts", if (v.ok) P.good else P.warn)
        }
        Spacer(Modifier.height(10.dp))
        Text("YOU GET", style = T.label.copy(fontSize = 10.sp), color = P.good)
        for (x in d.get) DealPlayer(lg, x, d.retainGet[x.id] ?: 0)
        for (k in d.getPicks) Text(Picks.label(lg, k, d.protGet[Picks.key(k)]).replaceFirstChar { it.uppercase() }, style = T.small, color = P.ink, modifier = Modifier.padding(vertical = 2.dp))
        Spacer(Modifier.height(6.dp))
        Text("YOU SEND", style = T.label.copy(fontSize = 10.sp), color = P.bad)
        for (x in d.give) DealPlayer(lg, x, d.retainGive[x.id] ?: 0)
        for (k in d.givePicks) Text(Picks.label(lg, k, d.protGive[Picks.key(k)]).replaceFirstChar { it.uppercase() }, style = T.small, color = P.ink, modifier = Modifier.padding(vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        ImpactRow("Your team", mine)
        ImpactRow("The ${d.other.name.substringAfterLast(' ')}", theirs)
        result?.let { Text(it, style = T.bodyB, color = P.accent, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onDecline != null) {
                SmallButton("Decline", danger = true, onClick = onDecline)
            }
            if (onEditInBuilder != null) {
                SecondaryButton("Edit", Modifier.weight(1f)) { onEditInBuilder(d) }
            }
            PrimaryButton("Make trade", Modifier.weight(1f)) {
                vm.act { l ->
                    val t = l.team(d.other.id)
                    val r = NbaCoach.trade(
                        l, t,
                        l.rosterOf(l.user).filter { x -> d.give.any { it.id == x.id } },
                        l.rosterOf(t).filter { x -> d.get.any { it.id == x.id } },
                        d.givePicks, d.getPicks,
                        d.retainGive, d.retainGet, d.protGive, d.protGet,
                    )
                    result = if (r.ok) "Done. The trade is official." else r.text
                    if (r.ok) l.proState?.tradeOffers?.removeAll { o -> o.teamId == t.id }
                }
                onDone()
            }
        }
    }
}

@Composable
private fun DealPlayer(lg: League, p: Player, retainPct: Int = 0) {
    val sal = Trades.effectiveSalary(lg, p, retainPct)
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(p.name, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (Needs.fitsNeed(lg, lg.user, p) && p.teamId != lg.userTeamId) {
            Tag("NEED", P.good, Modifier.padding(end = 6.dp))
        }
        Text(
            "${p.posLabel} · ${p.age} · ${Road.dollars(sal)}${if (retainPct > 0) " ($retainPct% ret)" else ""}",
            style = T.small.copy(fontSize = 12.sp), color = P.sub, modifier = Modifier.padding(horizontal = 6.dp),
        )
        Ring(p.ovr, 30.dp, textSize = 12.sp)
    }
}

/** "Your team: 84.2 → 85.0 (+0.8) · +2.3 wins · over the cap". */
@Composable
private fun ImpactRow(label: String, i: TradeFinder.Impact) {
    val d = Math.round((i.after - i.before) * 10) / 10.0
    val color = when { d >= 0.1 || i.wins >= 0.5 -> P.good; d <= -0.1 || i.wins <= -0.5 -> P.bad; else -> P.sub }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.small, color = P.sub, modifier = Modifier.width(84.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${"%.1f".format(i.before)} → ${"%.1f".format(i.after)}", style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
        Text(if (abs(d) < 0.1) "  (=)" else "  (${if (d > 0) "+" else "−"}${"%.1f".format(abs(d))})", style = T.small, color = color)
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text("${if (i.wins >= 0) "+" else "−"}${"%.1f".format(abs(i.wins))} wins", style = T.bodyB.copy(fontSize = 13.sp), color = color)
            Text(i.status, style = T.small.copy(fontSize = 11.sp), color = P.faint)
        }
    }
}

// ---------- BLM 27 Offseason, Contract Extensions & Multi-Round Free Agency ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NbaCoachOffseason(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    val u = lg.user
    val c = lg.userCoach
    var negotiatePlayer by remember { mutableStateOf<Player?>(null) }
    var negotiateIsExtension by remember { mutableStateOf(false) }
    var actionBanner by remember { mutableStateOf<String?>(null) }
    val draftUi = rememberDraftUi()

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
    val draftOn = step == ProStep.DRAFT && ps.draftNight?.done == false
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                ScreenHeader(
                    "${lg.season} · Offseason",
                    when (step) {
                        ProStep.REVIEW -> "Season Review"
                        ProStep.DRAFT -> if (draftOn) "Draft Night" else "Draft & Contracts"
                        ProStep.FREE_AGENCY -> "Free agency"
                        ProStep.SUMMER -> "Training Camp"
                    },
                    "Coach ${c.name} · ${u.name} · ${Payroll.status(lg, u)}",
                ) { TeamBadge(u, 46.dp) }
                actionBanner?.let { msg ->
                    Panel(color = P.accentSoft, modifier = Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(msg, style = T.small, color = P.ink, modifier = Modifier.weight(1f))
                            Text("✕", style = T.bodyB, color = P.sub, modifier = Modifier.clickable { actionBanner = null }.padding(start = 8.dp))
                        }
                    }
                }
            }
            when (step) {
                ProStep.REVIEW -> item {
                    val last = c.history.lastOrNull()
                    Panel(color = P.hero) {
                        Text("${lg.season} · ${last?.result?.uppercase() ?: ""}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                        Text("${u.w}-${u.l}", style = T.hero, color = P.onHero)
                        Text("Projected ${ps.expect} wins. Job security: ${NbaCoach.securityLabel(c.security).lowercase()} (${c.security}).", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                    }
                    ps.history.lastOrNull()?.let { h ->
                        Section("Around the league", action = "All news", onAction = { vm.push(Route.News) })
                        Panel {
                            Text("Champion: ${lg.teams.getOrNull(h.champion)?.name ?: "—"}", style = T.bodyB, color = P.ink)
                            if (h.mvp.isNotEmpty()) Text("MVP: ${h.mvp}", style = T.small, color = P.sub)
                            if (h.finalsMvp.isNotEmpty()) Text("Finals MVP: ${h.finalsMvp}", style = T.small, color = P.sub)
                            if (h.roy.isNotEmpty()) Text("Rookie of the Year: ${h.roy}", style = T.small, color = P.sub)
                        }
                    }
                    Section("Your career")
                    Panel { Text("${c.w}-${c.l} · ${c.tourneys} playoff trip${if (c.tourneys == 1) "" else "s"} · ${c.titles} title${if (c.titles == 1) "" else "s"}", style = T.bodyB, color = P.ink) }
                    Text("Next: the draft lottery, rookie selections, team options, qualifying offers, and contract extensions.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 10.dp))
                }

                ProStep.DRAFT -> if (draftOn) draftNightItems(vm, lg, ps.draftNight!!, draftUi) else {
                    ps.draftNight?.let { dn -> item { DraftRecap(vm, lg, dn) } }
                    item {
                        val rookies = lg.rosterOf(u).filter { it.proYears == 0 }
                        Section("Your Draft Class")
                        if (rookies.isEmpty()) Panel { Text("No draft picks this year.", style = T.body, color = P.sub) }
                        GroupList(rookies, inset = 70.dp) { p ->
                            PlayerLine(vm, lg, u, p, "${p.awards.firstOrNull { it.contains("Draft #") } ?: "Rookie"} · ${p.posLabel} · POT ${Ratings.shownPro(p.pot)} · ${Road.dollars(Payroll.salaryOf(lg, p))}")
                        }
                    }
                    item {
                        Section("Expiring Contracts, Options & Extensions")
                        Text(
                            "Pick up or decline team options, make qualifying offers to keep the right to match offers for restricted free agents, or extend players now, before free agency opens.",
                            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                        )
                        val exp = NbaCoach.expiring(lg)
                        if (exp.isEmpty()) Panel { Text("All players on your roster are signed beyond this summer.", style = T.body, color = P.sub) }
                        GroupList(exp) { p ->
                            val ask = NbaCoach.ask(lg, p)
                            val curSal = Payroll.salaryOf(lg, p)
                            val keep = p.id in ps.userKeep
                            Column {
                                ListRow(onClick = { vm.openPlayer(p.id) }) {
                                    PlayerJersey(p, u, 38.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (p.clause.isNotEmpty()) Tag(Payroll.clauseShort(p.clause).uppercase(), if (p.clause == "TO" || p.clause == "QO") P.good else P.accent)
                                        }
                                        Text("${p.posLabel} · age ${p.age} · makes ${Road.dollars(curSal)} · asks ${Market.desiredYears(p)} yr, ${Road.dollars(ask)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                                    }
                                    Ring(p.ovr, 34.dp, textSize = 12.sp)
                                }
                                FlowRow(
                                    Modifier.fillMaxWidth().padding(start = 58.dp, end = 14.dp, bottom = 10.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    when (p.clause) {
                                        "TO" -> {
                                            SmallButton("Pick up option (${Road.dollars(curSal)})", primary = true) {
                                                vm.act { actionBanner = Market.decideTeamOption(it, p, true) }
                                            }
                                            SmallButton("Decline option", danger = true) {
                                                vm.act { actionBanner = Market.decideTeamOption(it, p, false) }
                                            }
                                        }
                                        "RFA", "QO" -> {
                                            val qoSal = ((curSal * 1.25).roundToInt() / 10 * 10).coerceIn(ProCareer.minSalary(lg, p.proYears), ProCareer.maxSalary(lg, p.proYears))
                                            val hasQo = p.clause == "QO"
                                            SmallButton(if (hasQo) "Qualifying offer made (${Road.dollars(qoSal)})" else "Qualifying offer (${Road.dollars(qoSal)})", primary = hasQo) {
                                                vm.act { actionBanner = Market.tenderQualifyingOffer(it, p, !hasQo) }
                                            }
                                            SmallButton("Extend") {
                                                negotiateIsExtension = true
                                                negotiatePlayer = p
                                            }
                                        }
                                        else -> {
                                            SmallButton("Extend", primary = true) {
                                                negotiateIsExtension = true
                                                negotiatePlayer = p
                                            }
                                            SmallButton(if (keep) "We'll bid in free agency" else "Let him test the market") {
                                                vm.act {
                                                    if (keep) it.proState!!.userKeep.remove(p.id) else it.proState!!.userKeep.add(p.id)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (ps.userMoves.isNotEmpty()) {
                        item {
                            Section("Before the market opens")
                            GroupList(ps.userMoves) { line -> ListRow { Text(line, style = T.small, color = P.ink, modifier = Modifier.weight(1f)) } }
                        }
                    }
                    item {
                        Section("Next summer's free agents")
                        Text(
                            "Star players across the NBA with expiring contracts. Tap any player to add them to your Watchlist for Free Agency Round 1.",
                            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
                        )
                        GroupList(NbaCoach.market(lg).take(25), inset = 70.dp) { p ->
                            val watched = p.id in ps.faWatchlist
                            val ask = NbaCoach.ask(lg, p)
                            val eval = Market.evaluateOffer(lg, u, p, ask, Market.desiredYears(p), Market.desiredClause(p), "FLAT", fromTeamId = p.teamId)
                            ListRow(
                                onClick = {
                                    vm.act {
                                        if (watched) it.proState!!.faWatchlist.remove(p.id) else it.proState!!.faWatchlist.add(p.id)
                                    }
                                },
                                modifier = if (watched) Modifier.background(P.accentSoft) else Modifier,
                            ) {
                                PlayerJersey(p, lg.team(p.teamId), 38.dp)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (Needs.fitsNeed(lg, u, p)) Tag("NEED", P.good)
                                        Tag("${eval.pct}% INTEREST", interestColor(eval.pct))
                                    }
                                    Text("${lg.team(p.teamId).abbr} · ${p.posLabel} · age ${p.age} · asks ${Market.desiredYears(p)}y/${Road.dollars(ask)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                                }
                                Ring(p.ovr, 34.dp, textSize = 12.sp)
                                Spacer(Modifier.width(8.dp))
                                Tag(if (watched) "WATCHING" else "WATCH", if (watched) P.accent else P.faint)
                            }
                        }
                    }
                }

                ProStep.FREE_AGENCY -> item {
                    SummerLeaguePanel(vm, lg)
                    FreeAgencyFrenzyView(
                        vm = vm, lg = lg,
                        onNegotiate = { p -> negotiateIsExtension = false; negotiatePlayer = p },
                        onBanner = { actionBanner = it },
                    )
                }

                ProStep.SUMMER -> item {
                    TeamUsaPanel(vm, lg)
                    if (ps.userMoves.isNotEmpty()) {
                        Section("Your Offseason Summary")
                        GroupList(ps.userMoves) { line -> ListRow { Text(line, style = T.small, color = P.ink, modifier = Modifier.weight(1f)) } }
                    }
                    Section("Training Camp Report")
                    val grown = lg.rosterOf(u).map { it to (it.ovr - (ps.before[it.id] ?: it.ovr)) }.sortedByDescending { it.second }
                    GroupList(grown, inset = 70.dp) { (p, d) ->
                        PlayerLine(vm, lg, u, p, "${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))}") {
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
            val faActive = step == ProStep.FREE_AGENCY && ps.faRound in 1..Market.ROUNDS
            PrimaryButton(
                when {
                    step == ProStep.REVIEW -> "Lottery and draft"
                    draftOn -> "Sim the rest of the draft"
                    step == ProStep.DRAFT -> "Open free agency"
                    faActive -> "Finish free agency"
                    step == ProStep.FREE_AGENCY -> "Training camp"
                    else -> "Start the season"
                },
                Modifier.fillMaxWidth(),
            ) { if (draftOn) vm.draftSim("Finishing the draft") { DraftNight.simRest(it) } else vm.proNext() }
        }
    }

    negotiatePlayer?.let { p ->
        val fc = ps.faCases.firstOrNull { it.pid == p.id }
        val existingBid = fc?.bids?.firstOrNull { it.teamId == u.id }
        ContractNegotiationDialog(
            lg = lg,
            p = p,
            isExtension = negotiateIsExtension,
            fromTeamId = fc?.fromTeamId ?: p.teamId,
            initialSalary = existingBid?.salary ?: fc?.askSalary ?: NbaCoach.ask(lg, p),
            initialYears = existingBid?.years ?: fc?.askYears ?: Market.desiredYears(p),
            initialClause = existingBid?.clause ?: fc?.askClause ?: Market.desiredClause(p),
            initialStructure = existingBid?.structure ?: "FLAT",
            onDismiss = { negotiatePlayer = null },
            onSubmit = { sal, yrs, cl, st ->
                vm.act { l ->
                    val pl = l.playerOrNull(p.id) ?: return@act
                    val (_, msg) = if (negotiateIsExtension) {
                        Market.negotiateExtension(l, pl, sal, yrs, cl, st)
                    } else {
                        Market.submitUserBid(l, pl.id, sal, yrs, cl, st)
                    }
                    actionBanner = msg
                }
                negotiatePlayer = null
            },
        )
    }
}

@Composable
private fun FreeAgencyFrenzyView(
    vm: GameViewModel,
    lg: League,
    onNegotiate: (Player) -> Unit,
    onBanner: (String) -> Unit,
) {
    val ps = lg.proState ?: return
    val u = lg.user
    var tab by remember { mutableIntStateOf(0) }
    var posFilter by remember { mutableIntStateOf(0) }
    var typeFilter by remember { mutableIntStateOf(0) }
    var sortMode by remember { mutableIntStateOf(0) }
    var lastRoundEvents by remember { mutableStateOf<List<String>>(emptyList()) }

    val unsignedCases = ps.faCases.filter { it.signedTeamId < 0 && lg.playerOrNull(it.pid) != null }
    val myActiveCases = unsignedCases.filter { fc -> fc.bids.any { it.teamId == u.id } }
    val room = Market.availableRoom(lg, u)
    val mleUsed = u.id in ps.faUsedMle
    val inProgress = ps.faRound in 1..Market.ROUNDS

    // Frenzy Header Panel
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (inProgress) "FREE AGENCY FRENZY · ROUND ${ps.faRound} OF ${Market.ROUNDS}"
                    else "FREE AGENCY COMPLETE · ALL ROUNDS FINISHED",
                    style = T.label, color = if (inProgress) P.accent else P.good,
                )
                Text(
                    if (inProgress) "Make offers, outbid other teams, then move to the next round."
                    else "All free agents have signed or training camp rosters are set.",
                    style = T.small, color = P.sub,
                )
            }
            Tag("${lg.rosterOf(u).size}/${ProLeague.MAX_ROSTER} ROSTER", P.ink)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Cap Space", Road.dollars(room.coerceAtLeast(0)), Modifier.weight(1f), if (room > 0) P.good else P.sub, T.numSm)
            Stat("MLE", if (mleUsed) "Used" else Road.dollars(Payroll.mle(lg)), Modifier.weight(1f), if (mleUsed) P.faint else P.ink, T.numSm)
            Stat("Your offers", "${myActiveCases.size}", Modifier.weight(1f), P.accent, T.numSm)
            Stat("Available", "${unsignedCases.size}", Modifier.weight(1f), style = T.numSm)
        }
        if (inProgress) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PrimaryButton("Next round (${ps.faRound} of ${Market.ROUNDS})", Modifier.weight(1.3f)) {
                    vm.act { l ->
                        val events = Market.runRound(l)
                        lastRoundEvents = events
                        if (events.isNotEmpty()) onBanner("Round ${ps.faRound - 1} complete: ${events.first()}")
                    }
                }
                SecondaryButton("Sim the rest", Modifier.weight(0.7f)) {
                    vm.act { l ->
                        Market.finish(l)
                        onBanner("Free agency simmed to the end.")
                    }
                }
            }
        }
        if (lastRoundEvents.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (ev in lastRoundEvents.take(4)) {
                Text("• $ev", style = T.small, color = P.ink)
            }
        }
    }

    // Pending Restricted Free Agent (RFA) Offer Sheets to Match or Decline!
    if (ps.pendingRfaMatches.isNotEmpty()) {
        Section("RFA Offer Sheets to Match (${ps.pendingRfaMatches.size})")
        for ((pid, sheet) in ps.pendingRfaMatches.entries.toList()) {
            val p = lg.playerOrNull(pid) ?: continue
            val other = lg.team(sheet.teamId)
            Panel(color = P.accentSoft, modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(p, u, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(p.name, style = T.bodyB, color = P.ink)
                            Tag("OFFER SHEET", P.warn)
                        }
                        Text(
                            "The ${other.name} signed ${p.last} for ${sheet.years} ${if (sheet.years == 1) "year" else "years"}, ${Road.dollars(sheet.salary)} a year${if (sheet.clause.isNotEmpty()) " (${Payroll.clauseShort(sheet.clause)})" else ""}.",
                            style = T.small, color = P.ink,
                        )
                    }
                    Ring(p.ovr, 36.dp, textSize = 13.sp)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Match the offer sheet", Modifier.weight(1f)) {
                        vm.act { l -> onBanner(Market.resolveRfaMatch(l, pid, true)) }
                    }
                    SecondaryButton("Let him walk", Modifier.weight(1f)) {
                        vm.act { l -> onBanner(Market.resolveRfaMatch(l, pid, false)) }
                    }
                }
            }
        }
    }

    Spacer(Modifier.height(10.dp))
    val subTabs = listOf(
        "Market (${unsignedCases.size})",
        "My Bids (${myActiveCases.size})",
        "Signings (${ps.userMoves.size + ps.faMoves.size})",
        "Roster (${lg.rosterOf(u).size})",
    )
    Segmented(subTabs, tab, { tab = it })

    when (tab) {
        0 -> {
            Spacer(Modifier.height(8.dp))
            Chips(listOf("All positions", "PG", "SG", "SF", "PF", "C"), posFilter, { posFilter = it })
            Spacer(Modifier.height(6.dp))
            Chips(listOf("Everyone", "Fits a need", "Yours (Bird rights)", "RFA", "Watchlist"), typeFilter, { typeFilter = it })
            Spacer(Modifier.height(6.dp))
            Chips(listOf("Sort: OVR", "Sort: Interest", "Sort: Ask", "Sort: Most Bids"), sortMode, { sortMode = it })

            val filtered = unsignedCases.mapNotNull { fc ->
                val p = lg.playerOrNull(fc.pid) ?: return@mapNotNull null
                if (posFilter in 1..5 && p.pos.ordinal != posFilter - 1 && p.pos2?.ordinal != posFilter - 1) return@mapNotNull null
                when (typeFilter) {
                    1 -> if (!Needs.fitsNeed(lg, u, p)) return@mapNotNull null
                    2 -> if (fc.fromTeamId != u.id) return@mapNotNull null
                    3 -> if (!fc.restricted) return@mapNotNull null
                    4 -> if (p.id !in ps.faWatchlist) return@mapNotNull null
                }
                val eval = Market.evaluateOffer(lg, u, p, fc.askSalary, fc.askYears, fc.askClause, "FLAT", fc.fromTeamId)
                Triple(fc, p, eval)
            }.let { list ->
                when (sortMode) {
                    1 -> list.sortedByDescending { it.third.pct * 100 + it.second.ovr }
                    2 -> list.sortedByDescending { it.first.askSalary }
                    3 -> list.sortedByDescending { it.first.bids.size * 100 + it.second.ovr }
                    else -> list.sortedByDescending { it.second.ovr * 100 + it.third.pct }
                }
            }

            Section("Available Free Agents (${filtered.size})")
            if (filtered.isEmpty()) {
                Panel { Text("No unsigned free agents match the current filter.", style = T.body, color = P.sub) }
            }
            GroupList(filtered.take(50)) { (fc, p, eval) ->
                FaMarketRow(
                    lg = lg,
                    fc = fc,
                    p = p,
                    eval = eval,
                    onNegotiate = { onNegotiate(p) },
                    onWithdraw = { vm.act { Market.withdrawUserBid(it, p.id) } },
                )
            }
        }

        1 -> {
            Section("Your Standing Offers (${myActiveCases.size})")
            if (myActiveCases.isEmpty()) {
                Panel { Text("You have no active contract offers on the table. Tap any player in the Market tab to negotiate a deal.", style = T.body, color = P.sub) }
            }
            GroupList(myActiveCases) { fc ->
                val p = lg.playerOrNull(fc.pid) ?: return@GroupList
                val eval = Market.evaluateOffer(lg, u, p, fc.askSalary, fc.askYears, fc.askClause, "FLAT", fc.fromTeamId)
                FaMarketRow(
                    lg = lg,
                    fc = fc,
                    p = p,
                    eval = eval,
                    onNegotiate = { onNegotiate(p) },
                    onWithdraw = { vm.act { Market.withdrawUserBid(it, p.id) } },
                )
            }
        }

        2 -> {
            Section("Your Summer Moves (${ps.userMoves.size})")
            if (ps.userMoves.isEmpty()) Panel { Text("No signings or departures for the ${u.name} yet.", style = T.body, color = P.sub) }
            GroupList(ps.userMoves.reversed()) { line ->
                ListRow { Text(line, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f)) }
            }
            Section("Signings around the league (${ps.faMoves.size})", action = "All news", onAction = { vm.push(Route.News) })
            if (ps.faMoves.isEmpty()) Panel { Text("Big signings show up here as each round finishes.", style = T.small, color = P.sub) }
            GroupList(ps.faMoves.reversed().take(25)) { line ->
                ListRow { Text(line, style = T.small, color = P.sub, modifier = Modifier.weight(1f)) }
            }
        }

        else -> {
            PayrollPanel(vm, lg, u)
        }
    }
}

@Composable
private fun FaMarketRow(
    lg: League,
    fc: FaCase,
    p: Player,
    eval: Market.InterestEval,
    onNegotiate: () -> Unit,
    onWithdraw: () -> Unit,
) {
    val u = lg.user
    val myBid = fc.bids.firstOrNull { it.teamId == u.id }
    val leader = Market.leadingBid(lg, fc)
    val prevTeam = lg.teams.getOrNull(fc.fromTeamId)
    val askCl = Payroll.clauseShort(fc.askClause)

    Column(Modifier.fillMaxWidth().background(if (myBid != null) P.accentSoft else Color.Transparent)) {
        ListRow(onClick = onNegotiate) {
            PlayerJersey(p, prevTeam, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Tag("${eval.pct}% INTERESTED", interestColor(eval.pct))
                    if (fc.restricted) Tag("RESTRICTED", P.warn)
                    if (fc.fromTeamId == u.id) Tag("YOUR PLAYER", P.accent)
                    if (Needs.fitsNeed(lg, u, p)) Tag("FITS A NEED", P.good)
                }
                Text(
                    "${p.posLabel} · age ${p.age} · asks ${fc.askYears} ${if (fc.askYears == 1) "year" else "years"}, ${Road.dollars(fc.askSalary)} a year${if (askCl.isNotEmpty()) " ($askCl)" else ""}",
                    style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1,
                )
                val bidLine = when {
                    leader == null -> "No offers yet"
                    leader.teamId == u.id -> "Your offer leads: ${leader.years} yr, ${Road.dollars(leader.salary)} a year (${fc.bids.size} offer${if (fc.bids.size == 1) "" else "s"})"
                    myBid != null -> "${lg.team(leader.teamId).name} lead (${leader.years} yr, ${Road.dollars(leader.salary)}) · yours: ${myBid.years} yr, ${Road.dollars(myBid.salary)}"
                    else -> "${fc.bids.size} offer${if (fc.bids.size == 1) "" else "s"} · the ${lg.team(leader.teamId).name} lead (${leader.years} yr, ${Road.dollars(leader.salary)})"
                }
                Text(
                    bidLine,
                    style = T.small.copy(fontSize = 11.sp),
                    color = when {
                        leader?.teamId == u.id -> P.good
                        myBid != null -> P.warn
                        else -> P.faint
                    },
                    maxLines = 1,
                )
            }
            Ring(p.ovr, 36.dp, textSize = 13.sp)
            Spacer(Modifier.width(6.dp))
            if (myBid != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SmallButton("Adjust", primary = true, onClick = onNegotiate)
                    SmallButton("Pull", danger = true, onClick = onWithdraw)
                }
            } else {
                SmallButton("Offer", primary = true, onClick = onNegotiate)
            }
        }
    }
}

/**
 * BLM 27 Contract Negotiation Modal: used for both Pre-FA Contract Extensions and Multi-Round Free Agency Offers.
 * Shows a live 0–100% Signing Interest Bar, positive/negative Interest Factors, Years (1–5), Salary controls,
 * Structure (`FLAT`/`FRONT`/`BACK`), and Clauses (`None`/`PO`/`TO`/`NTC`).
 */
@Composable
private fun ContractNegotiationDialog(
    lg: League,
    p: Player,
    isExtension: Boolean,
    fromTeamId: Int,
    initialSalary: Int,
    initialYears: Int,
    initialClause: String,
    initialStructure: String,
    onDismiss: () -> Unit,
    onSubmit: (salary: Int, years: Int, clause: String, structure: String) -> Unit,
) {
    val u = lg.user
    val minSal = ProCareer.minSalary(lg, p.proYears)
    val maxSal = ProCareer.maxSalary(lg, p.proYears)
    val maxYears = if (isExtension || fromTeamId == u.id) 5 else 4

    var years by remember(p.id) { mutableIntStateOf(initialYears.coerceIn(1, maxYears)) }
    var clause by remember(p.id) { mutableStateOf(initialClause) }
    var structure by remember(p.id) { mutableStateOf(initialStructure.ifEmpty { "FLAT" }) }
    var salary by remember(p.id) { mutableIntStateOf(initialSalary.coerceIn(minSal, maxSal)) }

    val eval = Market.evaluateOffer(lg, u, p, salary, years, clause, structure, fromTeamId = fromTeamId, isExtension = isExtension)
    val intColor = interestColor(eval.pct)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = P.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, if (p.teamId >= 0) lg.team(p.teamId) else u, 42.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (isExtension) "Extend ${p.name}" else "Sign ${p.name}", style = T.head, color = P.ink, maxLines = 1)
                    Text("${p.posLabel} · age ${p.age} · OVR ${Ratings.shownPro(p.ovr)} (POT ${Ratings.shownPro(p.pot)})", style = T.small, color = P.sub)
                }
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Signing Interest Bar
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.surface2).padding(10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("SIGNING INTEREST", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                        Text("${eval.pct}% · ${eval.label.uppercase()}", style = T.bodyB, color = intColor)
                    }
                    Spacer(Modifier.height(6.dp))
                    GrowBar(eval.pct / 100f, Modifier.fillMaxWidth(), intColor, 9.dp)
                    Spacer(Modifier.height(6.dp))
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        for ((factor, positive) in eval.factors) {
                            Tag(factor, if (positive) P.good else P.bad)
                        }
                    }
                }

                // Contract Length (Years)
                Text("YEARS (PREFERS ${Market.desiredYears(p)} YRS)", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (y in 1..maxYears) {
                        val sel = years == y
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) P.accent else P.surface2)
                                .clickable { years = y }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("${y}y", style = T.bodyB.copy(fontSize = 13.sp), color = if (sel) P.onAccent else P.ink)
                        }
                    }
                }

                // Annual Salary & Quick Buttons
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column {
                        Text("SALARY / YR (ASKS ${Road.dollars(eval.effectiveAsk)})", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                        Text("${Road.dollars(salary)}/yr · Total ${Road.dollars(salary * years)}", style = T.numSm, color = P.ink)
                    }
                    Tag(eval.capHow.uppercase(), if (eval.legal) P.accent else P.bad)
                }
                HoopsSlider(
                    value = salary.toFloat(),
                    onValueChange = { salary = ((it.roundToInt() / 50) * 50).coerceIn(minSal, maxSal) },
                    range = minSal.toFloat()..maxSal.coerceAtLeast(minSal + 100).toFloat(),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    SmallButton("Min") { salary = minSal }
                    SmallButton("-1M") { salary = (salary - 1_000).coerceAtLeast(minSal) }
                    SmallButton("-250K") { salary = (salary - 250).coerceAtLeast(minSal) }
                    SmallButton("Match his ask", primary = true) { salary = eval.effectiveAsk }
                    SmallButton("+250K") { salary = (salary + 250).coerceAtMost(maxSal) }
                    SmallButton("+1M") { salary = (salary + 1_000).coerceAtMost(maxSal) }
                    if (!isExtension && Payroll.room(lg, u) < maxSal && u.id !in lg.proState?.faUsedMle.orEmpty()) {
                        SmallButton("MLE") { salary = Payroll.mle(lg).coerceIn(minSal, maxSal) }
                    }
                    SmallButton("Max") { salary = maxSal }
                }

                // Contract Clause
                Text("CONTRACT OPTION", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                val clauses = listOf("" to "None", "PO" to "Player", "TO" to "Team", "NTC" to "No-trade")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((code, lbl) in clauses) {
                        val sel = clause == code
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) P.accent else P.surface2)
                                .clickable { clause = code }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(lbl, style = T.small.copy(fontSize = 11.sp), color = if (sel) P.onAccent else P.ink, maxLines = 1)
                        }
                    }
                }

                // Contract Structure
                Text("SALARY STRUCTURE", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                val structs = listOf("FLAT" to "Flat", "FRONT" to "Front (-2%)", "BACK" to "Back (+2%)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for ((code, lbl) in structs) {
                        val sel = structure == code
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                                .background(if (sel) P.accent else P.surface2)
                                .clickable { structure = code }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(lbl, style = T.small.copy(fontSize = 11.sp), color = if (sel) P.onAccent else P.ink, maxLines = 1)
                        }
                    }
                }

                if (!eval.legal) {
                    Text(eval.illegalReason, style = T.small, color = P.bad)
                } else if (isExtension && eval.pct < 52) {
                    Text("He needs to be at least 52% interested to sign an extension now.", style = T.small, color = P.warn)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = eval.legal,
                onClick = { onSubmit(salary, years, clause, structure) },
            ) {
                Text(
                    if (isExtension) "Offer extension" else "Make offer",
                    style = T.bodyB,
                    color = if (eval.legal) P.accent else P.faint,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", style = T.bodyB, color = P.sub) }
        },
    )
}

@Composable
private fun interestColor(pct: Int): Color = when {
    pct >= 62 -> P.good
    pct >= 48 -> P.warn
    else -> P.bad
}

// ---------- career ----------

@Composable
fun NbaCoachCareer(vm: GameViewModel, lg: League) {
    val c = lg.userCoach
    val u = lg.user
    val nba = c.history.filter { it.teamId >= 0 }
    val college = c.history.filter { it.teamId < 0 }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Head coach · age ${c.age}", c.name, "${u.name} · ${ordinal(NbaCoach.seasonsHere(lg) + 1)} season") {
                IconCircle({ vm.push(Route.Settings) }) { Icon(NavIcons.Gear, "Settings", tint = P.sub, modifier = Modifier.size(22.dp)) }
            }
            TrophyRoomLink(vm, lg)
            CoachRivalsCard(lg)
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoachBadge(c.first, c.last, 60.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${c.w}-${c.l}", style = T.hero, color = P.ink)
                        Text("NBA record" + if (c.w + c.l > 0) " · ${"%.3f".format(c.winPct).removePrefix("0")}" else "", style = T.small, color = P.sub)
                    }
                    Ring(c.rating, 58.dp, label = "RATING", textSize = 20.sp)
                }
                Spacer(Modifier.height(14.dp))
                StatRow(
                    listOf(
                        Triple(if (nba.size == 1) "Season" else "Seasons", "${nba.size}", null), Triple("Playoffs", "${c.tourneys}", null),
                        Triple(if (c.titles == 1) "Title" else "Titles", "${c.titles}", if (c.titles > 0) P.gold else null),
                        nba.map { it.teamId }.toSet().size.coerceAtLeast(1).let { n -> Triple(if (n == 1) "Team" else "Teams", "$n", null) },
                    ),
                    style = T.numSm,
                )
            }
            Section("Job security")
            Panel {
                val col = securityColor(c.security)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(NbaCoach.securityLabel(c.security), style = T.head, color = col, modifier = Modifier.weight(1f))
                    Text("${c.security}/100", style = T.numSm, color = P.sub)
                }
                Spacer(Modifier.height(8.dp))
                Bar(c.security / 100f, Modifier.fillMaxWidth(), color = col, height = 8.dp)
                Spacer(Modifier.height(8.dp))
                Text("The owner expects the preseason projection. Wins above it, playoff series and titles raise it; falling short lowers it.", style = T.small, color = P.sub)
            }
            Section("Coaching skills")
            Panel {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Ring(c.off, 46.dp, label = "OFF"); Ring(c.def, 46.dp, label = "DEF"); Ring(c.rec, 46.dp, label = "REC"); Ring(c.dev, 46.dp, label = "DEV")
                }
            }
            lg.proState?.collegeResume?.takeIf { it.isNotEmpty() }?.let {
                Section("College coaching")
                Panel { Text(it, style = T.bodyB, color = P.ink) }
            }
            PlayingPast(lg)
            Section("Season by season")
            val rows = mutableListOf<@Composable () -> Unit>()
            if (lg.phase != Phase.OFFSEASON && nba.none { it.season == lg.season }) rows += {
                ListRow {
                    TeamBadge(u, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${u.name} · ${u.w}-${u.l}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("In progress", style = T.small, color = P.accent)
                    }
                    Text("${lg.season}", style = T.numSm, color = P.sub)
                }
            }
            for (h in c.history.reversed()) rows += {
                ListRow {
                    if (h.teamId >= 0) TeamBadge(lg.team(h.teamId), 32.dp)
                    else Box(Modifier.size(32.dp).background(P.surface2, CircleShape), contentAlignment = Alignment.Center) {
                        Text("NCAA", style = T.label.copy(fontSize = 8.sp), color = P.sub)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${h.team} · ${h.w}-${h.l}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(h.result.ifEmpty { "—" }, style = T.small, color = if (h.result.contains("Champion")) P.gold else P.sub)
                    }
                    Text("${h.season}", style = T.numSm, color = P.sub)
                }
            }
            if (rows.isEmpty()) Panel { Text("Your first season starts after the summer.", style = T.body, color = P.sub) }
            GroupList(rows) { it() }
            if (college.isNotEmpty()) Text("NCAA rows are your college seasons.", style = T.small.copy(fontSize = 12.sp), color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
            Spacer(Modifier.height(20.dp))
            Box(Modifier.padding(horizontal = Gutter)) { SecondaryButton("Start a new career", Modifier.fillMaxWidth()) { vm.abandonCareer() } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The latest projection for the user's team, and how it's made. */
@Composable
fun ProjectionLines(lg: League) {
    val ps = lg.proState ?: return
    val o = com.nh.hoopscoach.game.NbaPreviews.latest(lg)[lg.userTeamId] ?: return
    val pre = ps.preview?.takeIf { it.season == lg.season }?.proj?.get(lg.userTeamId)
    Spacer(Modifier.height(10.dp))
    Row {
        Stat(if (ps.liveDay > 0) "Projected now" else "Projected", "${o.w}-${o.l}", Modifier.weight(1f), style = T.numSm)
        Stat("Playoffs", odd(o.playoffs), Modifier.weight(1f), style = T.numSm)
        Stat("Title", odd(o.title), Modifier.weight(1f), style = T.numSm)
    }
    com.nh.hoopscoach.game.Takes.nbaPulse(lg, lg.user)?.let { Text(it, style = T.small, color = P.ink, modifier = Modifier.padding(top = 8.dp)) }
    var how by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (ps.liveDay > 0) "Updated day ${ps.liveDay}" + (pre?.let { " · preseason ${it.w}-${it.l}" } ?: "") else "Preseason projection",
            style = T.small.copy(fontSize = 12.sp), color = P.sub, modifier = Modifier.weight(1f),
        )
        Text(if (how) "Hide" else "How we project", style = T.small.copy(fontSize = 12.sp), color = P.accent, modifier = Modifier.clickable { how = !how })
    }
    if (how) Text(
        "We play the rest of the season 400 times: games already played count as they finished, the rest go by each team's strength now (its best eight players, plus every result so far). It updates every week, and after trades.",
        style = T.small.copy(fontSize = 12.sp), color = P.sub, modifier = Modifier.padding(top = 4.dp),
    )
}

/** A trade tier as the rows show it: "Starter value", so a bench player rated as one doesn't read as a starter. */
fun tierWords(tier: String) = if (tier == "Franchise") "Franchise piece" else "$tier value"

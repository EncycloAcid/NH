package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.AwardWinner
import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.Coaching
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Tryout
import com.nh.hoopscoach.game.Tryouts
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.WrapUp

// ======================================================================
// Season review
// ======================================================================

private val COACH_PAGES = listOf("Season", "Awards", "Seniors", "Program", "Coach")
private val PLAYER_PAGES = listOf("Season", "Awards", "You", "Program")

@Composable
fun WrapUpFlow(vm: GameViewModel, lg: League) {
    val w = lg.wrapUp ?: run { Empty("Season complete."); return }
    val WRAP_PAGES = if (lg.playerMode) PLAYER_PAGES else COACH_PAGES
    val page = vm.wrapPage.coerceIn(0, WRAP_PAGES.lastIndex)
    Column(Modifier.fillMaxSize()) {
        StepHeader("Season review · ${lg.season}", WRAP_PAGES, page) { vm.wrapPage = it }
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (WRAP_PAGES[page]) {
                "Season" -> seasonPage(vm, lg, w)
                "Awards" -> awardsPage(vm, lg)
                "Seniors" -> seniorsPage(vm, lg)
                "You" -> heroSeasonPage(vm, lg)
                "Program" -> programPage(vm, lg, w)
                else -> coachPage(vm, lg, w)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        FlowFooter(
            back = if (page > 0) ({ vm.wrapPage = page - 1 }) else null,
            label = if (page < WRAP_PAGES.lastIndex) "Next: ${WRAP_PAGES[page + 1]}" else "Start the offseason",
        ) { if (page < WRAP_PAGES.lastIndex) vm.wrapPage = page + 1 else vm.beginOffseason() }
    }
}

@Composable
fun StepHeader(eyebrow: String, steps: List<String>, current: Int, onPick: ((Int) -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter, top = 16.dp, bottom = 8.dp)) {
        Text(eyebrow.uppercase(), style = T.label, color = P.accent)
        Text(steps[current].uppercase(), style = T.title, color = P.ink)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            steps.forEachIndexed { i, _ ->
                Box(
                    Modifier.weight(1f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= current) P.accent else P.line)
                        .then(if (onPick != null && i < current) Modifier.clickable { onPick(i) } else Modifier),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("Step ${current + 1} of ${steps.size}", style = T.small, color = P.sub)
    }
}

@Composable
fun FlowFooter(back: (() -> Unit)?, label: String, enabled: Boolean = true, onNext: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(P.bg).padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        if (back != null) {
            SecondaryButton("Back", Modifier.width(96.dp)) { back() }
            Spacer(Modifier.width(10.dp))
        }
        PrimaryButton(label, Modifier.weight(1f), enabled = enabled, onClick = onNext)
    }
}

private fun LazyListScope.seasonPage(vm: GameViewModel, lg: League, w: WrapUp) {
    val t = lg.team(w.teamId)
    item {
        Panel(color = P.hero) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    val label = when {
                        w.event == "NIT" -> "NIT · NO. ${w.seed} SEED"
                        w.seed > 0 -> "NCAA TOURNAMENT · NO. ${w.seed} SEED"
                        else -> "SEASON RESULT"
                    }
                    Text(label, style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text(w.result.uppercase(), style = T.hero, color = P.onHero)
                    Text("${t.school} · ${w.w}-${w.l}", style = T.bodyB, color = P.onHero.copy(alpha = 0.85f))
                }
                TeamBadge(t, 60.dp)
            }
            if (w.path.isNotEmpty()) {
                Spacer(Modifier.height(14.dp))
                val big = w.path.size <= 6
                Row(horizontalArrangement = Arrangement.spacedBy(if (big) 8.dp else 6.dp)) {
                    w.path.forEachIndexed { i, opp ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.size(if (big) 40.dp else 36.dp).clip(CircleShape).background(if (w.pathWon[i]) P.good else P.bad).padding(3.dp), contentAlignment = Alignment.Center) {
                                TeamBadge(lg.team(opp), if (big) 34.dp else 30.dp)
                            }
                            Text(w.pathRounds.getOrNull(i) ?: Season.NCAA_ROUNDS.getOrElse(i) { "" }, style = T.label.copy(fontSize = 9.sp), color = P.onHero)
                        }
                    }
                }
            }
        }
        w.nitReward?.let { r ->
            Section("NIT champions")
            GroupList(
                listOf(
                    Triple("+${r.prestige}", "Program prestige", "On top of the season's normal change"),
                    Triple("+${r.points}", "Coaching points", "Extra points to spend on your coach"),
                    Triple("+${r.xp}", "Player XP", "For each of ${r.players} returning players"),
                ),
            ) { (v, title, sub) ->
                ListRow {
                    Text(v, style = T.numSm, color = P.good, modifier = Modifier.width(64.dp))
                    Column(Modifier.weight(1f)) {
                        Text(title, style = T.bodyB, color = P.ink)
                        Text(sub, style = T.small, color = P.sub)
                    }
                }
            }
        }
        Section("By the numbers")
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
            val diff = w.w - w.expWins
            StatRow(listOf(Triple("Record", "${w.w}-${w.l}", null), Triple("Conf", "${w.cw}-${w.cl}", null), Triple("Expected", "%.0f".format(w.expWins), null), Triple("vs exp", "%+.0f".format(diff), if (diff >= 0) P.good else P.bad)), style = T.numSm)
            Spacer(Modifier.height(12.dp))
            StatRow(listOf(Triple("NET", "${w.net}", null), Triple("AP", if (w.ap > 0) "#${w.ap}" else "NR", null), Triple("Conf finish", ordinal(w.confFinish), null), Triple("SOS", "#${w.sosRank}", null)), style = T.numSm)
            Spacer(Modifier.height(12.dp))
            StatRow(listOf(Triple("Q1 record", w.q1, null), Triple("Q2 record", w.q2, null), Triple("WAB", "%+.1f".format(w.wab), if (w.wab >= 0) P.good else P.bad), Triple("Prestige", "${w.prestige}", null)), style = T.numSm)
        }
    }
    val reg = lg.games.filter { it.played && it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
    val best = reg.filter { it.winner() == t.id }.minByOrNull { lg.team(it.opp(t.id)).net }
    val worst = reg.filter { it.winner() != t.id }.maxByOrNull { lg.team(it.opp(t.id)).net }
    item {
        Section("Signature results", action = "All games", onAction = { vm.push(Route.Schedule(t.id)) })
        GroupList(listOfNotNull(best?.let { "Best win" to it }, worst?.let { "Worst loss" to it })) { (label, g) ->
            ListRow(onClick = { vm.push(Route.Box(g.id)) }) {
                ResultDot(g.winner() == t.id)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${g.site(t.id).let { if (it == "N") "vs" else it }} ${teamLabel(lg.team(g.opp(t.id)))}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(label, style = T.small, color = P.sub)
                }
                Text("${g.scoreFor(t.id)}-${g.scoreAgainst(t.id)}", style = T.numSm, color = P.ink)
            }
        }
        Section("Leading scorers")
        GroupList(lg.rosterOf(t).sortedByDescending { it.stats.pts }.take(5), inset = 70.dp) { p -> PlayerLine(vm, lg, t, p, statLine(p)) }
        val reel = com.nh.hoopscoach.game.Moments.season(lg, lg.season).sortedWith(compareByDescending<com.nh.hoopscoach.game.Moment> { it.mine }.thenByDescending { it.weight }).take(4)
        if (reel.isNotEmpty()) {
            Section("March moments", action = "Highlight reel", onAction = { vm.push(Route.History(HistoryKind.MOMENTS)) })
            reel.forEach { m -> MomentCard(vm, lg, m); Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun LazyListScope.awardsPage(vm: GameViewModel, lg: League) {
    item {
        Spacer(Modifier.height(6.dp))
        Segmented(listOf("National", lg.conf(lg.user.confId).abbr), vm.awardsScope, { vm.awardsScope = it })
    }
    val national = vm.awardsScope == 0
    val list = if (national) lg.seasonAwards else lg.confAwards.filter { lg.team(it.teamId).confId == lg.user.confId }
    val mine = list.filter { it.teamId == lg.userTeamId }
    if (mine.isNotEmpty()) item {
        Section("Your honors")
        GroupList(mine.distinctBy { it.award + it.pid }) { a -> AwardRow(vm, lg, a, awardName(a.award)) }
    }
    val singles = if (national) listOf("Nat MVP", "DPOY", "FOY", "MOP", "COTY") else listOf("Conf POY", "Conf DPOY", "Conf FOY", "Conf COTY")
    item {
        Section("Top awards")
        GroupList(singles.mapNotNull { code -> list.firstOrNull { it.award == code } }) { a -> AwardRow(vm, lg, a, awardName(a.award)) }
    }
    val teams = if (national) listOf("AA 1st", "AA 2nd", "All-Tourn") else listOf("All-Conf 1st")
    teams.forEach { code ->
        val members = list.filter { it.award == code }
        if (members.isNotEmpty()) item {
            Section(awardName(code) + if (code.startsWith("AA")) " team" else "")
            GroupList(members) { a -> AwardRow(vm, lg, a, null) }
        }
    }
    if (list.isEmpty()) item { Empty("Awards are announced at the end of the season.") }
}

@Composable
private fun AwardRow(vm: GameViewModel, lg: League, a: AwardWinner, title: String?) {
    val t = lg.team(a.teamId)
    val p = if (a.pid > 0) lg.playerOrNull(a.pid) else null
    ListRow(onClick = if (p != null) ({ vm.openPlayer(p.id) }) else null, modifier = if (a.teamId == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier) {
        if (p != null) PlayerJersey(p, t, 42.dp) else {
            val c = lg.coaches.firstOrNull { it.id == -a.pid }
            CoachBadge(c?.first ?: "C", c?.last ?: "", 42.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) Text(title.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent)
            Text(a.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.school} · ${a.line}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p != null) Ring(p.ovr, 40.dp, textSize = 15.sp)
    }
}

private fun LazyListScope.seniorsPage(vm: GameViewModel, lg: League) {
    val seniors = lg.rosterOf(lg.user).filter { it.year >= 4 }.sortedByDescending { it.ovr }
    val board = Offseason.draftBoard(lg)
    item {
        Panel(padding = PaddingValues(vertical = 14.dp)) {
            StatRow(listOf(Triple("Seniors leaving", "${seniors.size}", null), Triple("NIL freed", money(seniors.sumOf { it.nil }), P.good)))
        }
        Spacer(Modifier.height(10.dp))
        if (seniors.isEmpty()) Empty("No seniors on the roster this year.")
        GroupList(seniors, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.user, p, "${statLine(p)}\n${Offseason.gradNote(board[p.id])}") }
    }
}

private fun LazyListScope.programPage(vm: GameViewModel, lg: League, w: WrapUp) {
    val t = lg.team(w.teamId)
    item {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(t, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(prestigeLabel(t.prestige).uppercase(), style = T.label, color = P.accent)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${w.prestigeBefore} → ${w.prestige}", style = T.hero, color = P.ink)
                        Text("  %+d".format(w.prestigeDelta), style = T.bodyB, color = if (w.prestigeDelta > 0) P.good else if (w.prestigeDelta < 0) P.bad else P.sub, modifier = Modifier.padding(bottom = 6.dp))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    w.prestigeDelta >= 3 -> "A ${w.w}-${w.l} season and a ${w.result.lowercase()} finish lifted the program."
                    w.prestigeDelta > 0 -> "A solid year nudged the program forward. Prestige moves slowly; March runs move it most."
                    w.prestigeDelta == 0 -> "The program held steady. A deeper tournament run would move the needle."
                    else -> "The program slipped this year. Prestige follows several seasons of results, so one good year starts the climb back."
                },
                style = T.small, color = P.sub,
            )
        }
        Section("Season signals")
        GroupList(w.signals) { s ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = T.bodyB, color = P.ink)
                    Text(s.detail, style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                Tag(s.value.uppercase(), if (s.good) P.good else P.bad)
            }
        }
        val movers = lg.teams.sortedByDescending { it.prestige - it.prevPrestige }
        Section("Biggest risers")
        GroupList(movers.take(5)) { x -> Mover(vm, lg, x) }
        Section("Biggest fallers")
        GroupList(movers.takeLast(5).reversed()) { x -> Mover(vm, lg, x) }
    }
}

@Composable
private fun Mover(vm: GameViewModel, lg: League, t: com.nh.hoopscoach.game.Team) {
    val d = t.prestige - t.prevPrestige
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(t.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1)
        Text("${t.prestige}", style = T.numSm, color = P.ink, modifier = Modifier.padding(end = 12.dp))
        Text("%+d".format(d), style = T.numSm, color = if (d > 0) P.good else if (d < 0) P.bad else P.sub, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
    }
}

private fun LazyListScope.coachPage(vm: GameViewModel, lg: League, w: WrapUp) {
    item {
        val c = lg.userCoach
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoachBadge(c.first, c.last, 56.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = T.head, color = P.ink)
                    Text("${lg.team(w.teamId).school} · ${ordinal(c.history.size)} season", style = T.small, color = P.sub)
                }
                Ring(c.rating, 54.dp, label = "RATING", textSize = 19.sp)
            }
            if (w.coachAwards.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { w.coachAwards.forEach { Tag(it.uppercase(), P.gold) } }
            }
        }
        Section("Reputation")
        GroupList(w.drivers) { d ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(d.label, style = T.bodyB, color = P.ink)
                    Text(d.detail, style = T.small, color = P.sub)
                }
                Text(d.value, style = T.numSm, color = if (d.good) P.good else P.bad)
            }
        }
        Section("Spend your points")
        CoachPoints(vm, lg, earned = w.coachPoints)
    }
}

// ======================================================================
// Offseason
// ======================================================================

@Composable
fun OffseasonFlow(vm: GameViewModel, lg: League) {
    val os = lg.offseason ?: return
    val steps = OffStep.entries.map { it.title }
    val next = when (os.step) {
        OffStep.ROSTER -> "Go to Draft Night"
        OffStep.DRAFT -> "Next: Career decision"
        OffStep.CAREER -> if (os.fired) "Accept a job to continue" else "Stay at ${lg.user.school}"
        OffStep.DEVELOPMENT -> "Next: NIL budget"
        OffStep.BUDGET -> "Start recruiting"
        OffStep.RECRUITING -> if (os.round >= Offseason.ROUNDS) "Lock in the final round" else "Lock in round ${os.round} of ${Offseason.ROUNDS}"
        OffStep.TRYOUTS -> "Start the ${lg.season + 1} season"
    }
    Column(Modifier.fillMaxSize()) {
        StepHeader("Offseason · ${lg.season}", steps, os.step.ordinal)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (os.step) {
                OffStep.ROSTER -> rosterStep(vm, lg)
                OffStep.DRAFT -> draftStep(vm, lg)
                OffStep.CAREER -> careerStep(vm, lg)
                OffStep.DEVELOPMENT -> developmentStep(vm, lg)
                OffStep.BUDGET -> budgetStep(lg)
                OffStep.RECRUITING -> {
                    item {
                        Panel(color = P.accentSoft) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("ROUND ${os.round} OF ${Offseason.ROUNDS}", style = T.label, color = P.accent)
                                    Text("Rivals have made their moves. Set your points, preview your position, then lock in.", style = T.small, color = P.ink)
                                }
                                Spacer(Modifier.width(8.dp))
                                GuideButton { vm.openGuide(Guide.RECRUITING) }
                            }
                            Spacer(Modifier.height(10.dp))
                            SmallButton("Auto-assign my free points", Modifier.fillMaxWidth(), enabled = com.nh.hoopscoach.game.Recruiting.available(lg) >= 10) {
                                vm.act { val n = com.nh.hoopscoach.game.Recruiting.autoPlan(it); vm.toast(if (n > 0) "Points spread over $n prospects" else "No open scholarships to recruit for") }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                    }
                    recruitBoard(vm, lg)
                }
                OffStep.TRYOUTS -> tryoutsStep(vm, lg)
            }
            item {
                Text("Skip ahead: sim the rest of the offseason", style = T.small, color = P.sub, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().clickable { vm.simOffseason() }.padding(16.dp))
            }
        }
        FlowFooter(null, next, enabled = !(os.step == OffStep.CAREER && os.fired)) { vm.offseasonNext() }
    }
}

private fun LazyListScope.rosterStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val t = lg.user
    val board = Offseason.draftBoard(lg)
    val entrants = lg.rosterOf(t).filter { it.year in 1..3 && (it.id in os.entrants || it.id in os.pitched) }
    val below = Offseason.belowMarket(lg)
    val rest = lg.rosterOf(t).filter { it.year < 4 && it !in below && it !in entrants }.sortedByDescending { it.ovr }
    item {
        Panel(padding = PaddingValues(vertical = 14.dp)) {
            StatRow(listOf(
                Triple("Committed", money(Finance.committed(lg, t, includeSeniors = false) + Finance.promised(lg, t)), null),
                Triple("Available", money(Finance.available(lg, t, includeSeniors = false)), P.good),
                Triple("Budget", money(Finance.budget(lg, t)), null),
            ), style = T.numSm)
        }
        Section("Draft decisions")
        Text(
            "Your best underclassmen can declare early. Pitch them to come back, or let them go: every player drafted from your program lifts recruiting for four years.",
            style = T.small, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, bottom = 8.dp),
        )
        if (entrants.isEmpty()) Panel { Text("No underclassmen are declaring for the draft.", style = T.body, color = P.sub) }
    }
    entrants.forEach { p ->
        item {
            val leaving = p.id in os.entrants
            Panel(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(p, t, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink)
                        Text("${p.pos} · ${p.cls} · ${Offseason.projection(board[p.id], p.year)} grade · ${money(p.nil)}", style = T.small, color = P.sub)
                        Text(if (leaving) "Declaring for the draft" else "Returning next season", style = T.small, color = if (leaving) P.warn else P.good)
                    }
                    Ring(p.ovr, 42.dp)
                }
                if (leaving && p.id !in os.pitched) {
                    Spacer(Modifier.height(10.dp))
                    SecondaryButton("Pitch him to come back", Modifier.fillMaxWidth()) {
                        vm.act { if (Offseason.pitchReturn(it, p.id)) vm.toast("${p.short} is coming back!") else vm.toast("${p.short} is staying in the draft") }
                    }
                }
            }
        }
    }
    item {
        Section("Below market value")
        if (below.isEmpty()) Panel { Text("Everyone is paid fairly.", style = T.body, color = P.sub) }
    }
    below.forEach { p ->
        item {
            val ask = Ratings.expectedNil(p)
            Panel(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(p, t, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink)
                        Text("${money(p.nil)} now · asking ${money(ask)}", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 42.dp)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PrimaryButton("Raise +${money(ask - p.nil)}", Modifier.weight(1f)) { vm.act { if (!Offseason.raiseNil(it, p.id)) vm.toast("Not enough NIL budget available") } }
                    ReleaseButton(vm, p)
                }
            }
        }
    }
    item {
        Section("Rest of the roster")
        GroupList(rest, inset = 70.dp) { p ->
            PlayerLine(vm, lg, t, p, if (p.recruitRank == 0) "${p.pos} · ${p.cls} · walk-on" else "${p.pos} · ${p.cls} · ${money(p.nil)}") {
                Row(verticalAlignment = Alignment.CenterVertically) { Ring(p.ovr, 40.dp, textSize = 15.sp); Spacer(Modifier.width(8.dp)); ReleaseButton(vm, p, compact = true) }
            }
        }
    }
}

@Composable
private fun ReleaseButton(vm: GameViewModel, p: Player, compact: Boolean = false) {
    var confirm by remember { mutableStateOf(false) }
    SmallButton(if (compact) "Cut" else "Release", danger = true) { confirm = true }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = P.surface,
        title = { Text("Release ${p.name}?", style = T.head, color = P.ink) },
        text = { Text("He enters the transfer portal and his scholarship opens up.", style = T.body, color = P.sub) },
        confirmButton = { TextButton({ confirm = false; vm.act { Offseason.release(it, p.id) } }) { Text("Release", color = P.bad) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep", color = P.sub) } },
    )
}

private fun LazyListScope.draftStep(vm: GameViewModel, lg: League) {
    val picks = lg.draftHistory.filter { it.season == lg.season }
    val mine = picks.filter { it.teamId == lg.userTeamId }
    item {
        Panel(padding = PaddingValues(vertical = 14.dp)) {
            StatRow(listOf(Triple("Picks", "${picks.size}", null), Triple("Your players", "${mine.size}", P.accent), Triple("Lottery", "${picks.count { it.pick <= 14 }}", null)))
        }
        HonorsCard(lg.user, lg.offseason?.honors ?: emptyList())
        PipelinePanel(vm, lg, lg.user)
    }
    for ((label, range) in listOf("First round" to 1..30, "Second round" to 31..60)) {
        val list = picks.filter { it.pick in range }
        if (list.isEmpty()) continue
        item {
            Section(label)
            GroupList(list) { d ->
                val t = lg.team(d.teamId)
                val my = d.teamId == lg.userTeamId
                ListRow(onClick = if (d.pid >= 0 && lg.anyPlayer(d.pid) != null) ({ vm.openPlayer(d.pid) }) else null, modifier = if (my) Modifier.background(P.accentSoft) else Modifier) {
                    Text("${d.pick}", style = T.numSm, color = P.accent, modifier = Modifier.width(34.dp))
                    Jersey(d.number, Color(t.primary), Color(t.secondary), 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.playerName, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.school} · ${com.nh.hoopscoach.game.classLabel(d.cls)} · ${d.pos}", style = T.small, color = P.sub, maxLines = 1)
                    }
                    Text("${d.ovr}", style = T.numSm, color = tier(d.ovr))
                }
            }
        }
    }
}

private fun LazyListScope.careerStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val u = lg.user
    val c = lg.userCoach
    item {
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(u, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (os.fired) "${u.school} let you go" else "Your job: ${u.school}", style = T.bodyB, color = if (os.fired) P.bad else P.ink)
                    Text("Prestige ${u.prestige} · budget ${money(Finance.budget(lg, u))} · security ${c.security}/100", style = T.small, color = P.sub)
                }
            }
        }
        Section(if (os.offers.isEmpty()) "No programs called" else "Programs that want you")
        if (os.offers.isEmpty()) Panel { Text("Win more to get noticed. You're staying at ${u.school}.", style = T.body, color = P.sub) }
        GroupList(os.offers) { o ->
            val t = lg.team(o.teamId)
            var ask by remember { mutableStateOf(false) }
            ListRow {
                TeamBadge(t, 40.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${lg.conf(t.confId).abbr} · prestige ${t.prestige} · ${money(Finance.budget(lg, t))} budget", style = T.small, color = P.sub, maxLines = 1)
                    Text(o.note, style = T.small, color = P.accent, maxLines = 1)
                }
                SmallButton("Talk") { ask = true }
            }
            if (ask) AlertDialog(
                onDismissRequest = { ask = false }, containerColor = P.surface,
                title = { Text("Take the ${t.school} job?", style = T.head, color = P.ink) },
                text = { Text("You'll leave ${u.school} and take over the ${t.mascot}: their roster, budget and recruiting class.", style = T.body, color = P.sub) },
                confirmButton = { TextButton({ ask = false; vm.act { Coaching.acceptOffer(it, t.id) }; vm.toast("Welcome to ${t.school}") }) { Text("Accept", color = P.accent) } },
                dismissButton = { TextButton({ ask = false }) { Text("Not now", color = P.sub) } },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.developmentStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    item {
        val avg = if (os.growth.isEmpty()) 0.0 else os.growth.map { it.after - it.before }.average()
        Panel(padding = PaddingValues(vertical = 14.dp)) {
            StatRow(listOf(Triple("Players", "${os.growth.size}", null), Triple("Avg gain", "%+.1f".format(avg), if (avg >= 0) P.good else P.bad), Triple("Biggest", "%+d".format(os.growth.maxOfOrNull { it.after - it.before } ?: 0), P.good)))
        }
        val breakouts = os.breakouts.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId == lg.userTeamId }
        if (breakouts.isNotEmpty()) {
            Section("Walk-on breakout")
            for (p in breakouts) {
                val gr = os.growth.firstOrNull { it.pid == p.id }
                Panel(Modifier.padding(bottom = 8.dp), color = P.hero, onClick = { vm.openPlayer(p.id) }) {
                    Text("LATE BLOOMER", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text(p.name.uppercase(), style = T.title, color = P.onHero)
                    Text(
                        "The former walk-on came back a different player: ${gr?.before ?: "?"} → ${p.ovr} overall, with a ceiling of ${p.pot}.",
                        style = T.small, color = P.onHero.copy(alpha = 0.9f),
                    )
                }
            }
        }
        Section("Summer growth")
        if (os.growth.isEmpty()) Panel { Text("No returning players to develop.", style = T.body, color = P.sub) }
        GroupList(os.growth, inset = 70.dp) { gr ->
            val p = lg.playerOrNull(gr.pid)
            if (p != null) Column(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(p, lg.user, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1)
                        Text("${p.pos} · now a ${p.cls}", style = T.small, color = P.sub)
                    }
                    Text("${gr.before} → ", style = T.small, color = P.sub)
                    Text("${gr.after}", style = T.numSm, color = tier(gr.after))
                    val d = gr.after - gr.before
                    Text("  %+d".format(d), style = T.numSm.copy(fontSize = 16.sp), color = if (d > 0) P.good else if (d < 0) P.bad else P.sub)
                }
                val chips = Attr.entries.filter { gr.gains[it.ordinal] != 0 }.sortedByDescending { gr.gains[it.ordinal] }.take(6)
                if (chips.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    FlowRow(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        chips.forEach { a -> val g = gr.gains[a.ordinal]; Tag("${a.short} ${if (g > 0) "+" else ""}$g", if (g > 0) P.good else P.bad) }
                    }
                }
            }
        }
    }
}

// ---------- walk-on tryouts ----------

private fun LazyListScope.tryoutsStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val t = lg.user
    val cap = Tryouts.capacity(lg)
    val kept = Tryouts.kept(os)
    item {
        Panel(color = P.accentSoft) {
            Text("PRESEASON TRYOUTS", style = T.label, color = P.accent)
            Text(
                "${os.tryouts.size} students tried out. Nobody has rated them, so go on the drills and your gut. Most are practice bodies, but once in a while a walk-on turns into a star.",
                style = T.small, color = P.ink,
            )
        }
        Spacer(Modifier.height(10.dp))
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
            StatRow(
                listOf(
                    Triple("Open spots", "$cap", null),
                    Triple("Keeping", "$kept of $cap", if (kept > 0) P.good else null),
                    Triple("Closer looks", "${os.looks}", if (os.looks > 0) P.accent else P.faint),
                ),
                style = T.numSm,
            )
        }
        Spacer(Modifier.height(10.dp))
    }
    val current = lg.rosterOf(t).filter { it.walkOn }
    if (current.isNotEmpty()) item {
        Section("Your walk-ons")
        GroupList(current, inset = 70.dp) { p ->
            PlayerLine(vm, lg, t, p, "${p.pos} · ${p.cls} · ${if (p.tryout > 0) "made the team in ${p.tryout}" else "walk-on"}") {
                SmallButton("Release", danger = true) { vm.act { Tryouts.release(it, p.id) } }
            }
        }
        Text(
            if (Tryouts.openWalkOnSpots(lg, t) == 0) "Both walk-on spots are taken. Release a walk-on to make room for someone new." else "Walk-ons keep their spot until they graduate or you release them.",
            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
        )
        Section("Trying out")
    }
    os.tryouts.forEachIndexed { i, tr ->
        item { TryoutCard(vm, lg, i, tr, os.looks, canKeep = tr.kept || kept < cap) }
    }
    item {
        Text(
            "Walk-ons take two extra roster spots with no scholarship and no NIL deal. Any scholarship you leave open goes to a walk-on too. You can award a walk-on a scholarship later from his player card.",
            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TryoutCard(vm: GameViewModel, lg: League, i: Int, tr: Tryout, looks: Int, canKeep: Boolean) {
    val p = tr.p
    Panel(Modifier.padding(bottom = 10.dp), color = if (tr.kept) P.accentSoft else P.surface) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerJersey(p, lg.user, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${p.pos} · ${com.nh.hoopscoach.game.classLabel(p.year)} · ${Ratings.heightLabel(p.height)} · ${p.weight} lbs", style = T.small, color = P.sub, maxLines = 1)
                Text(p.hometown, style = T.small.copy(fontSize = 12.sp), color = P.faint, maxLines = 1)
            }
            if (tr.looked) Ring(p.ovr, 46.dp, label = "OVR", textSize = 16.sp) else UnknownRating()
        }
        Spacer(Modifier.height(10.dp))
        tr.notes.forEachIndexed { k, lvl -> DrillRow(Tryouts.DRILLS[k], Tryouts.GRADES[k][lvl], lvl, gut = k == 3) }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SmallButton(if (tr.looked) "Looked closer" else "Closer look", enabled = !tr.looked && looks > 0) { vm.act { Tryouts.look(it, i) } }
            Spacer(Modifier.weight(1f))
            SmallButton(if (tr.kept) "Keeping ✓" else "Keep him", primary = !tr.kept, enabled = canKeep) { vm.act { Tryouts.toggle(it, i) } }
        }
    }
}

@Composable
private fun UnknownRating() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(46.dp).clip(CircleShape).border(3.dp, P.line, CircleShape), contentAlignment = Alignment.Center) {
            Text("?", style = T.numSm.copy(fontSize = 20.sp), color = P.sub)
        }
        Text("OVR", style = T.label.copy(fontSize = 9.sp), color = P.faint)
    }
}

@Composable
private fun DrillRow(drill: String, grade: String, level: Int, gut: Boolean) {
    val color = when (level) { 0 -> P.bad; 1 -> P.warn; 2 -> P.good; else -> if (gut) P.gold else P.good }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(drill, style = T.small, color = P.sub, modifier = Modifier.width(92.dp))
        Text(grade, style = T.bodyB.copy(fontSize = 14.sp), color = if (gut && level == 3) P.gold else P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(4) { k -> Box(Modifier.size(width = 12.dp, height = 6.dp).clip(RoundedCornerShape(3.dp)).background(if (k <= level) color else P.line)) }
        }
    }
}

private fun LazyListScope.budgetStep(lg: League) {
    val t = lg.user
    val os = lg.offseason!!
    val donor = Finance.donor(t.prestige)
    val rev = Finance.revShare(lg, t)
    val applied = Finance.appliedBonus(lg, t)
    val total = Finance.budget(lg, t)
    val returning = Finance.committed(lg, t)
    val avail = total - returning - Finance.promised(lg, t)
    item {
        Panel {
            Text("NIL ROOM FOR NEW PLAYERS", style = T.label, color = P.sub)
            Text(money(avail), style = T.hero.copy(fontSize = 52.sp, lineHeight = 52.sp), color = P.ink)
            val d = total - Finance.budgetAt(lg, t, t.prevPrestige)
            Text(if (d == 0) "Total budget unchanged from last year" else "${if (d > 0) "▲" else "▼"} ${money(kotlin.math.abs(d))} total budget vs last year", style = T.small, color = if (d > 0) P.good else if (d < 0) P.bad else P.sub)
        }
        Section("Where the money comes from")
        GroupList(
            listOf(
                Triple("Donor NIL pool", "Tracks prestige · ${prestigeLabel(t.prestige).lowercase()}", donor),
                Triple("Fan support", "Fans are ${com.nh.hoopscoach.game.Fans.mood(t.fans).lowercase()}${if (t.homeGames > 0) " · ${fans(com.nh.hoopscoach.game.Fans.avgCrowd(t))} a night" else ""}", Finance.fanMoney(t)),
                Triple("Conference revenue share", lg.conf(t.confId).name, rev),
                Triple("Performance bonuses", "${t.bonuses.size} active, capped at ${money(Finance.bonusCap(lg, t))}", applied),
            ),
        ) { (a, b, v) ->
            ListRow {
                Column(Modifier.weight(1f)) { Text(a, style = T.bodyB, color = P.ink); Text(b, style = T.small, color = P.sub) }
                Text((if (v < 0) "−" else "") + money(kotlin.math.abs(v)), style = T.numSm, color = if (v < 0) P.bad else P.good)
            }
        }
        Spacer(Modifier.height(10.dp))
        GroupList(
            listOf(
                Triple("Total budget", "", total),
                Triple("Returning roster salaries", "", -returning),
                Triple("Freed by departures", "Graduated ${money(os.freedGrad)} · drafted ${money(os.freedDraft)} · other ${money(os.freedOther)}", os.freedGrad + os.freedDraft + os.freedOther),
            ),
        ) { (a, b, v) ->
            ListRow {
                Column(Modifier.weight(1f)) { Text(a, style = T.bodyB, color = P.ink); if (b.isNotEmpty()) Text(b, style = T.small, color = P.sub) }
                Text((if (v < 0) "−" else "") + money(kotlin.math.abs(v)), style = T.numSm, color = if (v < 0) P.bad else P.ink)
            }
        }
        if (t.bonuses.isNotEmpty()) {
            Section("Active bonuses")
            GroupList(t.bonuses.sortedByDescending { it.amount }) { b ->
                ListRow {
                    Column(Modifier.weight(1f)) { Text(b.label, style = T.bodyB, color = P.ink); Text(Finance.bonusNote(b), style = T.small, color = P.sub) }
                    Text(money(b.amount), style = T.numSm, color = P.good)
                }
            }
        }
    }
}

@Suppress("unused")
private fun keepAwards() = Awards

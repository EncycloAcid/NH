package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Recruit
import com.nh.hoopscoach.game.Recruiting
import com.nh.hoopscoach.game.Team

@Composable
fun OffseasonScreen(vm: GameViewModel, lg: League) {
    val os = lg.offseason ?: return
    val next = when (os.step) {
        OffStep.ROSTER -> "Continue to Draft Night"
        OffStep.DRAFT -> "Continue"
        OffStep.CAREER -> if (os.fired) "Choose a new job above" else "Continue at ${lg.user.school}"
        OffStep.DEVELOPMENT -> "Review NIL Budget"
        OffStep.BUDGET -> "Start Recruiting"
        OffStep.RECRUITING -> if (os.round >= Offseason.ROUNDS) "Finish Signing Day" else "Lock In Round ${os.round}"
    }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Row(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("OFFSEASON · STEP ${os.step.ordinal + 1} OF ${OffStep.entries.size}", style = HoopsType.labelMedium, color = C.orangeText, modifier = Modifier.weight(1f))
                    Text("Sim offseason", color = C.sub, fontSize = 14.sp, modifier = Modifier.clickable { vm.simOffseason() }.padding(6.dp))
                }
            }
            when (os.step) {
                OffStep.ROSTER -> rosterStep(vm, lg)
                OffStep.DRAFT -> draftStep(vm, lg)
                OffStep.CAREER -> careerStep(vm, lg)
                OffStep.DEVELOPMENT -> developmentStep(vm, lg)
                OffStep.BUDGET -> budgetStep(lg)
                OffStep.RECRUITING -> recruitingStep(vm, lg)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        Column(Modifier.fillMaxWidth().background(C.bg).padding(horizontal = Gutter, vertical = 10.dp)) {
            BigButton(next, Modifier.fillMaxWidth(), enabled = !(os.step == OffStep.CAREER && os.fired)) { vm.offseasonNext() }
        }
    }
}

@Composable
private fun StepTitle(title: String, sub: String) {
    Column {
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Gutter))
        Text(sub, color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun MoneyBar(lg: League) {
    val t = lg.user
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Row {
            StatCell("Committed", money(Finance.committed(lg, t, includeSeniors = false) + Finance.promised(lg, t)), Modifier.weight(1f), C.text, 22.sp)
            StatCell("Available", money(Finance.available(lg, t, includeSeniors = false)), Modifier.weight(1f), C.green, 22.sp)
            StatCell("Budget", money(Finance.budget(lg, t)), Modifier.weight(1f), C.text, 22.sp)
        }
    }
}

// ---------- step 1: roster & NIL ----------

private fun LazyListScope.rosterStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val board = Offseason.draftBoard(lg)
    val entrants = lg.rosterOf(lg.user).filter { it.year in 1..3 && (it.id in os.entrants || it.id in os.pitched) }
    val below = Offseason.belowMarket(lg)
    val rest = lg.rosterOf(lg.user).filter { it.year < 4 && it !in below && it !in entrants }.sortedByDescending { it.ovr }
    item {
        StepTitle("Roster & NIL", "Underclassmen with first-round grades head to Draft Night. Pay your players fairly or they may hit the portal.")
        MoneyBar(lg)
    }
    item { SectionLabel("Draft Feedback") }
    if (entrants.isEmpty()) item { EmptyNote("No underclassmen are declaring for the draft.") }
    items(entrants, key = { "e${it.id}" }) { p ->
        val rank = board[p.id]
        val leaving = p.id in os.entrants
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(p, lg, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${Offseason.projection(rank, p.year)} grade", color = C.sub, fontSize = 14.sp)
                    Text("Frees ${money(p.nil)} if selected", color = C.sub, fontSize = 14.sp)
                    Text(if (leaving) "Entering Draft Night" else "Returning next season", color = if (leaving) C.orangeText else C.green, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                Text("${p.cls} ${p.pos}", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                RatingNum(p.ovr, 26.sp)
            }
            if (leaving && p.id !in os.pitched) {
                Spacer(Modifier.height(10.dp))
                SmallButton("Pitch Him To Return", Modifier.fillMaxWidth()) {
                    vm.act { if (Offseason.pitchReturn(it, p.id)) vm.toast("${p.short} is coming back!") else vm.toast("${p.short} is staying in the draft") }
                }
            }
        }
    }
    item {
        SectionLabel("Below Market")
        Text("Raise their NIL to keep them happy.", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = Gutter))
    }
    if (below.isEmpty()) item { EmptyNote("Everyone is paid at market value.") }
    items(below, key = { "b${it.id}" }) { p -> NilCard(vm, lg, p) }
    item { SectionLabel("Remaining Roster") }
    items(rest, key = { "r${it.id}" }) { p -> ReleaseRow(vm, lg, p, if (p.recruitRank == 0) "Walk-On" else "${roleWord(lg.user, p)} · Current deal ${money(p.nil)}") }
}

private fun roleWord(t: Team, p: Player) = com.nh.hoopscoach.game.Gameplan.role(t, p.id)

@Composable
private fun NilCard(vm: GameViewModel, lg: League, p: Player) {
    val ask = Ratings.expectedNil(p)
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p, lg, 52.dp)
            Spacer(Modifier.width(12.dp))
            Text(p.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${p.cls} ${p.pos}", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            RatingNum(p.ovr, 26.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            StatCell("Current", money(p.nil), Modifier.weight(1f), C.text, 18.sp)
            StatCell("Asking", money(ask), Modifier.weight(1f), C.text, 18.sp)
            StatCell("Raise", "+${money(ask - p.nil)}", Modifier.weight(1f), C.green, 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.weight(1f))
            SmallButton("Raise") { vm.act { if (!Offseason.raiseNil(it, p.id)) vm.toast("Not enough NIL budget available") } }
            ReleaseButton(vm, p)
        }
    }
}

@Composable
private fun ReleaseRow(vm: GameViewModel, lg: League, p: Player, sub: String) {
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p, lg, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(sub, color = C.sub, fontSize = 13.sp, maxLines = 1)
            }
            Text("${p.cls} ${p.pos}", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
            RatingNum(p.ovr, 24.sp)
            Spacer(Modifier.width(8.dp))
            ReleaseButton(vm, p)
        }
    }
}

@Composable
private fun ReleaseButton(vm: GameViewModel, p: Player) {
    var confirm by remember { mutableStateOf(false) }
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(C.redDim).clickable { confirm = true }.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text("Release", color = C.red, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = C.card2,
        title = { Text("Release ${p.name}?", color = C.text) },
        text = { Text("He enters the transfer portal and his scholarship opens up.", color = C.sub) },
        confirmButton = { TextButton({ confirm = false; vm.act { Offseason.release(it, p.id) } }) { Text("Release", color = C.red) } },
        dismissButton = { TextButton({ confirm = false }) { Text("Keep", color = C.sub) } },
    )
}

// ---------- step 2: draft night ----------

private fun LazyListScope.draftStep(vm: GameViewModel, lg: League) {
    val picks = lg.draftHistory.filter { it.season == lg.season }
    val mine = picks.filter { it.teamId == lg.userTeamId }
    item {
        Row(Modifier.fillMaxWidth().padding(end = Gutter), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { StepTitle("Draft Night", "Who heard their name called.") }
            Text("${picks.size} picks", color = C.dim, fontSize = 15.sp)
        }
        Segmented(listOf("Full Draft", "Your Picks (${mine.size})"), vm.draftTab, { vm.draftTab = it })
    }
    val list = if (vm.draftTab == 0) picks else mine
    if (list.isEmpty()) item { EmptyNote("None of your players were drafted.") }
    var lastRound = 0
    for (d in list) {
        val round = if (d.pick <= 30) 1 else 2
        if (round != lastRound) { lastRound = round; item { SectionLabel("Round $round") } }
        item(key = "d${d.pick}") {
            val t = lg.team(d.teamId)
            val (j, trim) = jerseyOf(t)
            val my = d.teamId == lg.userTeamId
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("#${d.pick}", color = C.orangeText, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(54.dp))
                Avatar(d.look, j, d.number, 46.dp, trim = trim)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(d.playerName, color = if (my) C.orangeText else C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(d.school, color = C.sub, fontSize = 14.sp, maxLines = 1)
                }
                Text("${com.nh.hoopscoach.game.classLabel(d.cls)}  ${d.pos}", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 8.dp))
                RatingNum(d.ovr, 24.sp)
            }
            Divider(Modifier.padding(horizontal = Gutter))
        }
    }
}

// ---------- step 3: career decision ----------

private fun LazyListScope.careerStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val u = lg.user
    val c = lg.userCoach
    item {
        StepTitle("Career Decision", if (os.fired) "${u.school} has let you go. Pick your next job." else "Stay put or take an open job.")
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(u, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(u.school, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Prestige ${u.prestige} · ${u.record}", color = C.sub, fontSize = 14.sp)
                    Text("${money(Finance.available(lg, u, false))} avail · ${money(Finance.budget(lg, u))} cap", color = C.sub, fontSize = 13.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(c.name, fontSize = 15.sp, maxLines = 1)
                    Text("Job security ${c.security}", color = if (c.security >= 50) C.green else C.red, fontSize = 13.sp)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        SectionLabel(if (os.offers.isEmpty()) "No programs reached out" else "↗ Programs approaching you")
    }
    if (os.offers.isEmpty()) item { EmptyNote("Win more to get noticed. You'll stay at ${u.school}.") }
    items(os.offers, key = { "o${it.teamId}" }) { o ->
        val t = lg.team(o.teamId)
        var ask by remember { mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${t.prestige}", color = ratingColor(t.prestige), fontSize = 24.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(46.dp))
            TeamLogo(t, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(t.school, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(lg.conf(t.confId).abbr, C.orangeText)
                    Text("  ${t.record}", color = C.sub, fontSize = 14.sp)
                }
                Text("${money(Finance.budget(lg, t))} cap · ${o.note}", color = C.sub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("Talk") { ask = true }
        }
        Divider(Modifier.padding(horizontal = Gutter))
        if (ask) AlertDialog(
            onDismissRequest = { ask = false }, containerColor = C.card2,
            title = { Text("Take the ${t.school} job?", color = C.text) },
            text = { Text("You'll leave ${u.school} and take over the ${t.mascot} roster, budget and recruiting class.", color = C.sub) },
            confirmButton = { TextButton({ ask = false; vm.act { com.nh.hoopscoach.game.Coaching.acceptOffer(it, t.id) }; vm.toast("Welcome to ${t.school}!") }) { Text("Accept", color = C.orangeText) } },
            dismissButton = { TextButton({ ask = false }) { Text("Not now", color = C.sub) } },
        )
    }
}

// ---------- step 4: player development ----------

@OptIn(ExperimentalLayoutApi::class)
private fun LazyListScope.developmentStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    item { StepTitle("Player Development", "How your returning players grew over the summer.") }
    if (os.growth.isEmpty()) item { EmptyNote("No returning players to develop.") }
    items(os.growth, key = { "gr${it.pid}" }) { gr ->
        val p = lg.playerOrNull(gr.pid) ?: return@items
        val d = gr.after - gr.before
        Column(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(p, lg, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.short, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("${p.cls} · ${p.pos} · ${p.style.label}", color = C.sub, fontSize = 14.sp)
                }
                Text("${gr.before} → ", color = C.sub, fontSize = 16.sp)
                RatingNum(gr.after, 28.sp)
                Text(if (d > 0) "  +$d" else if (d < 0) "  $d" else "  0", color = if (d > 0) C.green else if (d < 0) C.red else C.sub, fontSize = 17.sp, modifier = Modifier.width(44.dp))
            }
            val chips = Attr.entries.filter { gr.gains[it.ordinal] != 0 }.sortedByDescending { gr.gains[it.ordinal] }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.padding(start = 64.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { a ->
                        val g = gr.gains[a.ordinal]
                        Box(Modifier.clip(RoundedCornerShape(8.dp)).background(C.card2).padding(horizontal = 8.dp, vertical = 4.dp)) {
                            Text("${a.short} ${if (g > 0) "+" else ""}$g", color = if (g > 0) C.green else C.red, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            if (gr.weight != 0) Text("↗ ${if (gr.weight > 0) "+" else ""}${gr.weight} lbs to ${p.weight} lbs", color = C.sub, fontSize = 13.sp, modifier = Modifier.padding(start = 64.dp, top = 6.dp))
        }
        Divider(Modifier.padding(horizontal = Gutter))
    }
}

// ---------- step 5: NIL budget ----------

private fun LazyListScope.budgetStep(lg: League) {
    val t = lg.user
    val os = lg.offseason!!
    val donor = Finance.donor(t.prestige)
    val rev = Finance.revShare(lg, t)
    val applied = Finance.appliedBonus(lg, t)
    val total = Finance.budget(lg, t)
    val returning = Finance.committed(lg, t)
    val avail = total - returning - Finance.promised(lg, t)
    val lastYear = Finance.budgetAt(lg, t, t.prevPrestige)
    item {
        Text("OFFSEASON · NIL BUDGET", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.padding(horizontal = Gutter, vertical = 4.dp))
        Text("Season ${lg.season + 1} Budget", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = Gutter))
        Spacer(Modifier.height(16.dp))
        Text("YOU HAVE", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text(money(avail), fontSize = 46.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        Text("to spend on recruiting", color = C.sub, fontSize = 16.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        val d = total - lastYear
        Text((if (d >= 0) "↑ +" else "↓ -") + money(kotlin.math.abs(d)) + " cap vs last year", color = if (d >= 0) C.green else C.red, fontSize = 15.sp, modifier = Modifier.fillMaxWidth().padding(top = 6.dp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row {
                Text("DONOR NIL SUPPORT", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
                Text(if (t.prestige > t.prevPrestige) "Rising" else if (t.prestige < t.prevPrestige) "Falling" else "Steady", color = if (t.prestige >= t.prevPrestige) C.green else C.red, fontSize = 14.sp)
            }
            Text(money(donor), fontSize = 26.sp, fontWeight = FontWeight.Bold)
            val tier = when { t.prestige >= 90 -> "S"; t.prestige >= 75 -> "A"; t.prestige >= 60 -> "B"; t.prestige >= 40 -> "C"; else -> "D" }
            Text("$tier Tier · prestige ${t.prestige}", color = C.sub, fontSize = 15.sp)
            Spacer(Modifier.height(10.dp))
            ProgressBar(donor / Finance.donor(99).toFloat(), Modifier.fillMaxWidth(), color = C.green)
            Spacer(Modifier.height(10.dp))
            Text("Donor support follows prestige. It moves slowly between offseasons.", color = C.sub, fontSize = 14.sp)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Text("BUDGET SOURCES", style = HoopsType.labelMedium, color = C.sub)
            Spacer(Modifier.height(8.dp))
            MoneyLine("Total NIL budget", total, C.green, big = true)
            MoneyLine("· NIL pool", donor, C.sub)
            MoneyLine("· Conference rev-share", rev, C.sub)
            MoneyLine("· Performance adjustments (${t.bonuses.size} active)", applied, C.sub)
            Divider(Modifier.padding(vertical = 8.dp))
            MoneyLine("Returning roster NIL salaries", -returning, C.red, big = true)
            Divider(Modifier.padding(vertical = 8.dp))
            MoneyLine("Available", avail, C.green, big = true)
            Divider(Modifier.padding(vertical = 8.dp))
            Text("OFFSEASON PAYROLL FREED", style = HoopsType.labelMedium, color = C.sub)
            MoneyLine("Departing contracts", os.freedGrad + os.freedDraft + os.freedOther, C.green, big = true)
            MoneyLine("· graduated", os.freedGrad, C.sub)
            MoneyLine("· drafted", os.freedDraft, C.sub)
            MoneyLine("· transferred or released", os.freedOther, C.sub)
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Text("ACTIVE PERFORMANCE ADJUSTMENTS", style = HoopsType.labelMedium, color = C.sub)
            if (t.bonuses.isEmpty()) Text("None yet. Deep tournament runs add money to next year's pool.", color = C.sub, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
            t.bonuses.sortedByDescending { it.amount }.forEach { b ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.label, fontSize = 16.sp)
                        Text(Finance.bonusNote(b), color = C.sub, fontSize = 13.sp)
                    }
                    Text(money(b.amount), color = C.green, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            if (t.bonuses.isNotEmpty()) {
                Divider(Modifier.padding(vertical = 10.dp))
                MoneyLine("Net adjustment", Finance.bonusTotal(t), C.green)
                MoneyLine("Cap adjustment", applied - Finance.bonusTotal(t), C.red)
                MoneyLine("Applied to budget (capped)", applied, C.green, big = true)
            }
        }
    }
}

@Composable
private fun MoneyLine(label: String, amount: Int, color: Color, big: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = if (big) 17.sp else 15.sp, color = if (big) C.text else C.sub, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
        Text((if (amount < 0) "-" else "") + money(kotlin.math.abs(amount)), color = color, fontSize = if (big) 18.sp else 15.sp, fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal)
    }
}

// ---------- step 6: recruiting rounds ----------

fun grade(v: Int): String = when {
    v >= 88 -> "A+"; v >= 84 -> "A"; v >= 80 -> "A-"; v >= 77 -> "B+"; v >= 73 -> "B"; v >= 70 -> "B-"
    v >= 66 -> "C+"; v >= 62 -> "C"; v >= 58 -> "C-"; v >= 54 -> "D+"; v >= 50 -> "D"; else -> "F"
}

private fun LazyListScope.recruitingStep(vm: GameViewModel, lg: League) {
    val os = lg.offseason!!
    val u = lg.user
    val open = Recruiting.openSpots(lg, u)
    val room = Finance.available(lg, u)
    val counts = Pos.entries.associateWith { pos -> u.roster.count { lg.player(it).pos == pos } + lg.recruits.count { it.committed == u.id && it.p.pos == pos } }
    item {
        Row(Modifier.fillMaxWidth().padding(end = Gutter), verticalAlignment = Alignment.CenterVertically) {
            Text("Recruiting", fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = Gutter).weight(1f))
            repeat(Offseason.ROUNDS) { i -> Box(Modifier.padding(horizontal = 3.dp).size(9.dp).clip(CircleShape).background(if (i + 1 == os.round) C.orange else if (i + 1 < os.round) C.orangeDim else C.dim)) }
            Text("  Round ${os.round} of ${Offseason.ROUNDS}", color = C.sub, fontSize = 15.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp)) {
            Text("${lg.recruitPoints} pts · ", color = C.orangeText, fontSize = 16.sp)
            Text("${money(room)} offer room", color = C.orangeText, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text("$open", color = C.orangeText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(" open", color = C.sub, fontSize = 16.sp)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pos.entries.forEach { pos ->
                val sel = vm.recruitPos == pos
                val n = counts.getValue(pos)
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) C.orangeDim else C.card).clickable { vm.recruitPos = if (sel) null else pos }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row {
                        Text("${pos.name} ", color = if (sel) C.orangeText else C.sub, fontSize = 14.sp)
                        Text("$n", color = if (n <= 1) C.red else if (n == 2) C.yellow else C.green, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("All", "Gettable", "Starters", "Upside", "Transfers").forEachIndexed { i, f ->
                val sel = vm.recruitFilter == i
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(if (sel) C.orangeDim else C.card).clickable { vm.recruitFilter = i }.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(f, color = if (sel) C.orangeText else C.sub, fontSize = 13.sp, maxLines = 1) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Recruits", "Targets", "Roster"), vm.offRecruitTab, { vm.offRecruitTab = it })
        Spacer(Modifier.height(6.dp))
    }
    if (vm.offRecruitTab == 2) {
        val roster = lg.rosterOf(u).sortedBy { it.pos.ordinal }
        item { SectionLabel("Current Roster · ${roster.size}") }
        items(roster, key = { "rr${it.id}" }) { p -> ReleaseRow(vm, lg, p, if (p.nil > 0) "Current deal ${money(p.nil)}" else "Walk-on (no NIL)") }
        val commits = lg.recruits.filter { it.committed == u.id }
        if (commits.isNotEmpty()) {
            item { SectionLabel("Committed · ${commits.size}") }
            items(commits, key = { "cm${it.p.id}" }) { r -> OffRecruitRow(vm, lg, r) }
        }
        return
    }
    val fifth = lg.rosterOf(u).map { it.ovr }.sortedDescending().getOrElse(4) { 60 }
    val base = if (vm.offRecruitTab == 1) lg.recruits.filter { it.onBoard || u.id in it.offers || it.committed == u.id } else lg.recruits.filter { it.committed < 0 }
    val list = base.filter { vm.recruitPos == null || it.p.pos == vm.recruitPos }.filter { r ->
        when (vm.recruitFilter) {
            1 -> Recruiting.fit(lg, u, r) >= 0.85
            2 -> r.estOvr >= fifth - 3
            3 -> r.estPot >= 85
            4 -> r.transfer
            else -> true
        }
    }.sortedWith(compareByDescending<Recruit> { it.transfer && vm.recruitFilter == 4 }.thenByDescending { if (it.transfer) it.p.ovr + 10 else it.estOvr + it.p.stars * 3 })
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.Bottom) {
            Text("${list.size}", fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Text(" prospects", color = C.sub, fontSize = 15.sp, modifier = Modifier.weight(1f))
            Text("INT", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
            Text("POS", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
            Text("OVR\nPOT", style = HoopsType.labelSmall, color = C.sub, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
        Divider()
    }
    if (list.isEmpty()) item { EmptyNote(if (vm.offRecruitTab == 1) "No targets yet. Open a prospect to call, visit or offer." else "No prospects match these filters.") }
    items(list.take(150), key = { "or${it.p.id}" }) { r -> OffRecruitRow(vm, lg, r) }
}

@Composable
private fun OffRecruitRow(vm: GameViewModel, lg: League, r: Recruit) {
    val u = lg.user
    val mine = (r.interest[u.id] ?: 0).coerceAtMost(100)
    val rivals = r.interest.filter { it.key != u.id && it.value >= 45 }
    val leader = r.interest.entries.filter { it.key != u.id }.maxByOrNull { it.value }
    val status = when {
        r.committed == u.id -> "Committed to you" to C.green
        r.committed >= 0 -> "Signed with ${lg.team(r.committed).abbr}" to C.dim
        rivals.size >= 3 -> "Heavily contested" to C.red
        leader != null && leader.value >= 80 -> "Leaning ${lg.team(leader.key).abbr}" to C.yellow
        else -> "Open race" to C.green
    }
    Row(Modifier.fillMaxWidth().clickable { vm.push(Route.RecruitView(r.p.id)) }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(r.p.look, if (r.transfer) Color(0xFF3B4A6B) else Color(0xFF3A3F4B), null, 50.dp)
            Text(if (r.transfer) "TR" else "#${r.p.recruitRank}", color = C.sub, fontSize = 12.sp)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(r.p.short, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (r.transfer) {
                val from = if (r.p.transferFrom >= 0) lg.team(r.p.transferFrom).school else "Portal"
                Text("Transfer from $from · ${r.p.cls}", color = C.blue, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else Stars(r.p.stars, 14.sp)
            Row {
                Text(money(r.nilAsk), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(" · ${status.first}", color = status.second, fontSize = 13.sp, maxLines = 1)
            }
        }
        Text("$mine%", color = if (mine >= 60) C.green else if (mine > 0) C.text else C.dim, fontSize = 17.sp, modifier = Modifier.width(48.dp), textAlign = TextAlign.End)
        Text(r.p.pos.name, color = C.sub, fontSize = 15.sp, modifier = Modifier.width(40.dp), textAlign = TextAlign.End)
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
            Text(grade(r.estOvr), color = ratingColor(r.estOvr + 4), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(grade(r.estPot), color = C.yellow, fontSize = 14.sp)
        }
    }
    Divider(Modifier.padding(horizontal = Gutter))
}

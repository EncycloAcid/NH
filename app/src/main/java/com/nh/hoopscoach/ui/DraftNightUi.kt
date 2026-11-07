package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.DraftNight
import com.nh.hoopscoach.game.DraftState
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Needs
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Prospect

// ---------- Draft night: the board, the mock, the picks and the phones ----------

/** What the draft screen remembers between recompositions: the tab, the position filter and the open row. */
class DraftUi {
    var tab by mutableIntStateOf(0)
    var pos by mutableIntStateOf(0)
    var open by mutableIntStateOf(-1)
}

@Composable
fun rememberDraftUi() = remember { DraftUi() }

private val POS_FILTERS = listOf("All", "PG", "SG", "SF", "PF", "C", "Needs")

private fun gradeColor(g: String): Color = when (g.firstOrNull()) {
    'A' -> Color(0xFF2E9E5B)
    'B' -> Color(0xFF3B7DD8)
    'C' -> Color(0xFFD89A1E)
    else -> Color(0xFFD64545)
}

private fun heightText(h: Int) = "${h / 12}'${h % 12}\""

/** Draft night while it's on: the clock, the actions, then the tab the user picked. */
internal fun LazyListScope.draftNightItems(vm: GameViewModel, lg: League, dn: DraftState, ui: DraftUi) {
    item { DraftHero(vm, lg, dn) }
    item {
        val offers = DraftNight.offers(lg)
        Spacer(Modifier.height(4.dp))
        Segmented(
            listOf("Big board", "Mock", "Picks · ${dn.log.size}", if (offers.isEmpty()) "Trades" else "Trades · ${offers.size}"),
            ui.tab, { ui.tab = it },
        )
        Spacer(Modifier.height(8.dp))
    }
    when (ui.tab) {
        0 -> {
            item {
                Chips(POS_FILTERS, ui.pos, { ui.pos = it })
                Text(
                    "${dn.prospects.size} prospects left · ranked by the consensus board. Ranges are what your scouts can see; " +
                        "scouting a player narrows them.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
                )
            }
            val needs = Needs.needsOf(lg, lg.user)
            val shown = dn.prospects.sortedBy { it.rank }.filter { pr ->
                when (val f = POS_FILTERS[ui.pos]) {
                    "All" -> true
                    "Needs" -> pr.p.pos in needs || pr.p.pos2 in needs
                    else -> pr.p.pos.name == f || pr.p.pos2?.name == f
                }
            }
            if (shown.isEmpty()) item { Panel { Text("Nobody left at that spot.", style = T.body, color = P.sub) } }
            items(shown, key = { it.p.id }) { pr ->
                ProspectCard(vm, lg, dn, pr, pr.p.pos in needs || pr.p.pos2 in needs, ui.open == pr.p.id) { ui.open = if (ui.open == pr.p.id) -1 else pr.p.id }
            }
        }
        1 -> item {
            val mock = DraftNight.mock(lg, 12)
            Text("The next ${mock.size} picks as the league's front offices see it right now. It changes with every pick and every trade.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
            GroupList(mock, inset = 66.dp) { m ->
                val t = lg.team(m.teamId)
                val mine = t.id == lg.userTeamId
                ListRow(modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
                    PickNumber(m.slot, mine)
                    Spacer(Modifier.width(8.dp))
                    TeamBadge(t, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${m.pr.p.name} · ${m.pr.p.posLabel}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.abbr} · ${m.why}", style = T.small, color = P.sub, maxLines = 2)
                    }
                    Text("#${m.pr.rank}", style = T.small, color = P.faint)
                }
            }
        }
        2 -> item {
            if (dn.log.isEmpty()) Panel { Text("No picks yet. The first team is on the clock.", style = T.body, color = P.sub) }
            GroupList(dn.log.reversed(), inset = 66.dp) { m ->
                val t = lg.team(m.teamId)
                val mine = t.id == lg.userTeamId
                ListRow(onClick = { vm.openPlayer(m.pid) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
                    PickNumber(m.pick, mine)
                    Spacer(Modifier.width(8.dp))
                    TeamBadge(t, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${m.name} · ${m.pos}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${t.abbr} · board No. ${m.rank}" + when {
                                m.pick - m.rank >= 6 -> " · steal"
                                m.rank - m.pick >= 6 -> " · reach"
                                else -> ""
                            },
                            style = T.small, color = P.sub,
                        )
                    }
                    GradeChip(m.grade)
                }
            }
        }
        else -> item { TradesTab(vm, lg) }
    }
    item { Spacer(Modifier.height(8.dp)) }
}

@Composable
private fun PickNumber(slot: Int, mine: Boolean) {
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(if (mine) P.accent else P.surface2),
        contentAlignment = Alignment.Center,
    ) { Text("$slot", style = T.bodyB.copy(fontSize = 13.sp), color = if (mine) Color.White else P.ink) }
}

@Composable
private fun GradeChip(g: String) {
    val c = gradeColor(g)
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(c.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(g, style = T.bodyB.copy(fontSize = 14.sp), color = c)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DraftHero(vm: GameViewModel, lg: League, dn: DraftState) {
    val ps = lg.proState ?: return
    val clockTeam = lg.teams.getOrNull(dn.order.getOrNull(dn.at) ?: -1)
    val mine = DraftNight.onTheClock(lg)
    val next = DraftNight.nextUserSlot(lg)
    val base = clockTeam?.let { Color(it.primary) } ?: P.hero
    val fg = onColor(base)
    val accent = if (mine) P.gold else fg.copy(alpha = 0.85f)
    Column(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(lerp(base, Color.Black, 0.2f), lerp(base, Color.Black, 0.75f)))).padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("DRAFT NIGHT · ${dn.season}", style = T.label, color = fg.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            Text("ROUND ${if (dn.at < lg.teams.size) 1 else 2}", style = T.label, color = fg.copy(alpha = 0.8f))
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (mine) "YOU'RE ON THE CLOCK" else "ON THE CLOCK", style = T.label.copy(fontSize = 12.sp, letterSpacing = 2.sp), color = accent)
                Text("No. ${dn.at + 1}", style = T.hero.copy(fontSize = 54.sp, lineHeight = 54.sp), color = fg)
                Text(clockTeam?.name ?: "", style = T.head, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            clockTeam?.let { TeamBadge(it, 76.dp) }
        }
        Spacer(Modifier.height(10.dp))
        // The lottery, in a line.
        if (ps.lotterySeason == lg.season && ps.lotteryPicks.isNotEmpty()) {
            val top = ps.lotteryPicks.first()
            val from = ps.lotteryPre.indexOf(top) + 1
            Text(
                "Lottery: the ${lg.team(top).name} won the No. 1 pick" + (if (from > 3) ", jumping from No. $from." else "."),
                style = T.small, color = fg.copy(alpha = 0.85f),
            )
        }
        val myPicks = (dn.at until dn.order.size).filter { dn.order[it] == lg.userTeamId }.map { it + 1 }
        Text(
            if (myPicks.isEmpty()) "You have no picks left tonight. Watch the board, or trade back in." else "Your picks: " + myPicks.joinToString(", ") { "No. $it" },
            style = T.bodyB, color = fg, modifier = Modifier.padding(top = 4.dp),
        )
        Text("Scouting: ${dn.scoutPoints} visit${if (dn.scoutPoints == 1) "" else "s"} left", style = T.small, color = fg.copy(alpha = 0.8f))
        // The last few picks.
        if (dn.log.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(fg.copy(alpha = 0.25f)))
            Spacer(Modifier.height(8.dp))
            for (m in dn.log.takeLast(3).reversed()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("${m.pick}", style = T.bodyB, color = fg, modifier = Modifier.width(28.dp))
                    Text("${lg.team(m.teamId).abbr} · ${m.name}, ${m.pos}", style = T.small, color = fg.copy(alpha = 0.9f), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.grade, style = T.bodyB, color = fg)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (mine) SmallButton("Auto-pick (best on your board)", primary = true) { vm.act { DraftNight.autoPick(it) } }
            else if (next >= 0) SmallButton("Sim to my pick (No. ${next + 1})", primary = true) { vm.draftSim("On the clock soon") { DraftNight.simToUser(it) } }
            if (!mine) SmallButton("Next pick") { vm.act { DraftNight.aiPick(it) } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProspectCard(vm: GameViewModel, lg: League, dn: DraftState, pr: Prospect, need: Boolean, open: Boolean, onToggle: () -> Unit) {
    val p = pr.p
    val e = DraftNight.estimate(pr)
    val canDraft = DraftNight.onTheClock(lg)
    Column(
        Modifier.padding(horizontal = Gutter, vertical = 3.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.surface),
    ) {
        ListRow(onClick = onToggle) {
            Text("${pr.rank}", style = T.numSm.copy(fontSize = 16.sp), color = P.faint, textAlign = TextAlign.Center, modifier = Modifier.width(28.dp))
            PlayerJersey(p, null, 40.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (need) Tag("NEED", P.good)
                }
                Text("${p.posLabel} · ${p.age} · ${pr.school}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                ScoutDots(pr.scout)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(if (e.oLo == e.oHi) "${e.oLo}" else "${e.oLo}–${e.oHi}", style = T.bodyB, color = P.ink)
                Text("POT ${e.pLo}–${e.pHi}", style = T.small.copy(fontSize = 11.sp), color = P.accent)
            }
        }
        if (open) {
            Column(Modifier.padding(start = 52.dp, end = 14.dp, bottom = 12.dp)) {
                Text("${heightText(p.height)} · ${p.weight} lb · ${p.style.label}", style = T.small, color = P.sub)
                Text("Last season: ${pr.line}", style = T.small, color = P.sub)
                if (pr.scout == 0) {
                    Text("Your scouts haven't seen him up close. A visit narrows his ranges and tells you what he does best.", style = T.small, color = P.ink, modifier = Modifier.padding(top = 6.dp))
                } else {
                    val (best, worst) = DraftNight.report(pr)
                    Text("Best: ${best.joinToString(" and ") { it.label }}. Needs work: ${worst.label}.", style = T.small, color = P.ink, modifier = Modifier.padding(top = 6.dp))
                    if (pr.scout >= 2) {
                        val gap = e.p - e.o
                        Text(
                            when {
                                gap >= 14 -> "Our read: a long way from finished, with a lot of room to grow."
                                gap >= 8 -> "Our read: a couple of years from being who he'll be."
                                else -> "Our read: close to finished. What you see is about what you get."
                            },
                            style = T.small, color = P.ink,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (pr.scout < 3) SmallButton("Scout him (${dn.scoutPoints} left)", enabled = dn.scoutPoints > 0) { vm.act { DraftNight.scout(it, p.id) } }
                    if (canDraft) SmallButton("Draft ${p.last.ifEmpty { p.name }}", primary = true) { vm.act { DraftNight.pick(it, p.id) } }
                }
            }
        }
    }
}

@Composable
private fun ScoutDots(level: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
        repeat(3) { i ->
            Box(Modifier.padding(end = 3.dp).size(6.dp).clip(CircleShape).background(if (i < level) P.accent else P.line))
        }
        Text(
            when (level) { 0 -> "unscouted"; 1 -> "seen once"; 2 -> "well scouted"; else -> "fully scouted" },
            style = T.small.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = 3.dp),
        )
    }
}

@Composable
private fun TradesTab(vm: GameViewModel, lg: League) {
    val offers = DraftNight.offers(lg)
    Text(
        "Teams around your pick call with offers. Moving down adds picks; moving up costs one. Values use the draft-day chart.",
        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp),
    )
    if (offers.isEmpty()) {
        Panel { Text("No offers right now. The phones ring when your pick is close and someone wants it.", style = T.body, color = P.sub) }
        return
    }
    for (o in offers) {
        val t = lg.team(o.partner)
        val giveV = o.give.sumOf { DraftNight.chart(it + 1) } + o.giveFuture.sumOf { DraftNight.chartFuture(lg, it) }
        val getV = o.get.sumOf { DraftNight.chart(it + 1) } + o.getFuture.sumOf { DraftNight.chartFuture(lg, it) }
        Panel(Modifier.padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(t, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (o.up) "Move up to No. ${o.get.first() + 1}" else "Move down from No. ${o.give.first() + 1}", style = T.bodyB, color = P.ink)
                    Text("with the ${t.name}", style = T.small, color = P.sub)
                }
                Tag(if (o.up) "UP" else "DOWN", if (o.up) P.accent else P.good)
            }
            Spacer(Modifier.height(8.dp))
            Row {
                Column(Modifier.weight(1f)) {
                    Text("YOU GIVE", style = T.label, color = P.faint)
                    Text(DraftNight.describe(lg, o, give = true), style = T.small, color = P.ink)
                    Text("${giveV.toInt()} pts", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                }
                Column(Modifier.weight(1f)) {
                    Text("YOU GET", style = T.label, color = P.faint)
                    Text(DraftNight.describe(lg, o, give = false), style = T.small, color = P.ink)
                    Text("${getV.toInt()} pts", style = T.small.copy(fontSize = 11.sp), color = P.sub)
                }
            }
            Spacer(Modifier.height(8.dp))
            SmallButton("Accept", primary = true) { vm.act { DraftNight.accept(it, o) } }
        }
    }
}

/** After the draft: the user's class with grades, and the best and worst nights around the league. */
@Composable
internal fun DraftRecap(vm: GameViewModel, lg: League, dn: DraftState) {
    val grades = DraftNight.teamGrades(dn)
    val mine = dn.log.filter { it.teamId == lg.userTeamId }
    val myGrade = grades[lg.userTeamId]?.second
    Section("Draft night grades")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR CLASS", style = T.label, color = P.faint)
                Text(if (mine.isEmpty()) "No picks this year" else mine.joinToString(", ") { "${it.name} (No. ${it.pick})" }, style = T.bodyB, color = P.ink)
            }
            if (myGrade != null) GradeChip(myGrade)
        }
        val first = lg.teams.size
        val ranked = grades.filterKeys { id -> dn.log.any { it.teamId == id && it.pick <= first } }.entries.sortedByDescending { it.value.first }
        if (ranked.size >= 2) {
            Spacer(Modifier.height(10.dp))
            val best = ranked.first()
            val worst = ranked.last()
            Text("Best night: the ${lg.team(best.key).name} (${best.value.second})", style = T.small, color = P.ink)
            Text("Toughest night: the ${lg.team(worst.key).name} (${worst.value.second})", style = T.small, color = P.ink)
        }
        dn.log.maxByOrNull { it.pick - it.rank }?.takeIf { it.pick - it.rank >= 6 }?.let {
            Text("Steal: ${it.name} at No. ${it.pick} (board No. ${it.rank})", style = T.small, color = P.sub)
        }
    }
}

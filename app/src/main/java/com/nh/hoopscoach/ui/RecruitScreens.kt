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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Recruit
import com.nh.hoopscoach.game.Recruiting

@Composable
fun RecruitScreen(vm: GameViewModel, lg: League) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            val off = lg.phase == Phase.OFFSEASON
            ScreenHeader(if (off) "Offseason recruiting" else "Class of ${lg.season + 1}", "Recruiting",
                if (off) "Recruiting rounds run in the offseason steps on Today." else "Points refresh every game day.")
        }
        recruitBoard(vm, lg)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Shared by the Recruit tab and the offseason recruiting step. */
fun LazyListScope.recruitBoard(vm: GameViewModel, lg: League) {
    val u = lg.user
    if (lg.recruits.isEmpty()) {
        item { Empty("The next recruiting class opens when the new season starts.") }
        return
    }
    val open = Recruiting.openSpots(lg, u)
    val commits = lg.recruits.filter { it.committed == u.id }
    item {
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                Stat("Points", "${lg.recruitPoints}", Modifier.weight(1f), P.accent)
                Stat("Open spots", "$open", Modifier.weight(1f), if (open > 0) P.good else P.bad)
                Stat("Commits", "${commits.size}", Modifier.weight(1f))
                val rank = Recruiting.classRank(lg, u.id)
                Stat("Class", if (rank > 0) "#$rank" else "–", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Scout ${Recruiting.COST_SCOUT} · Call ${Recruiting.COST_CALL} · Visit ${Recruiting.COST_VISIT} pts. NIL room ${money(Finance.available(lg, u))}. A prospect commits once his interest tops 100 and you've offered.",
                style = T.small, color = P.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("Targets", "Prospects", "Transfers", "Commits"), vm.recruitView, { vm.recruitView = it })
        Spacer(Modifier.height(10.dp))
        val opts = listOf("All") + Pos.entries.map { pos ->
            val n = u.roster.count { lg.player(it).pos == pos && Recruiting.stays(lg, lg.player(it)) } + lg.recruits.count { it.committed == u.id && it.p.pos == pos }
            "${pos.name} · $n"
        }
        Chips(opts, vm.recruitPos?.let { it.ordinal + 1 } ?: 0, { i -> vm.recruitPos = if (i == 0) null else Pos.entries[i - 1] })
        Spacer(Modifier.height(10.dp))
    }
    val list = when (vm.recruitView) {
        0 -> lg.recruits.filter { it.onBoard || u.id in it.offers || it.committed == u.id }
        1 -> lg.recruits.filter { it.committed < 0 && !it.transfer }
        2 -> lg.recruits.filter { it.committed < 0 && it.transfer }
        else -> commits
    }.filter { vm.recruitPos == null || it.p.pos == vm.recruitPos }
        .sortedWith(compareByDescending<Recruit> { it.committed == u.id }.thenByDescending { if (it.transfer) it.p.ovr + 8 else it.estOvr + it.p.stars * 3 })
    if (list.isEmpty()) item {
        Empty(when (vm.recruitView) {
            0 -> "No targets yet. Open a prospect and call, visit or offer to add him here."
            2 -> if (lg.phase == Phase.OFFSEASON) "No transfers match this filter." else "The transfer portal opens in the offseason."
            3 -> "No commitments yet."
            else -> "No prospects match this filter."
        })
    }
    item { GroupList(list.take(120), inset = 70.dp) { r -> RecruitRow(vm, lg, r) } }
}

@Composable
fun RecruitRow(vm: GameViewModel, lg: League, r: Recruit) {
    val u = lg.user
    val mine = (r.interest[u.id] ?: 0)
    val leader = r.interest.entries.filter { it.key != u.id }.maxByOrNull { it.value }
    ListRow(onClick = { vm.push(Route.RecruitView(r.p.id)) }) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(P.surface2), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (r.transfer) Text("TR", style = T.numSm, color = P.accent)
                else {
                    Text("${r.p.stars}★", style = T.numSm.copy(fontSize = 16.sp), color = P.gold)
                    Text("#${r.p.recruitRank}", style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.3.sp), color = P.sub)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val from = if (r.transfer && r.p.transferFrom >= 0) "from ${lg.team(r.p.transferFrom).school}" else r.p.hometown
            Text("${r.p.pos} · ${Ratings.heightLabel(r.p.height)} · $from", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            when {
                r.committed == u.id -> Text("Committed to you", style = T.small, color = P.good)
                r.committed >= 0 -> Text("Signed with ${lg.team(r.committed).school}", style = T.small, color = P.faint)
                else -> {
                    Bar(mine / 100f, Modifier.fillMaxWidth(0.92f), color = if (u.id in r.offers) P.accent else P.faint, height = 5.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "You $mine" + (leader?.let { " · ${lg.team(it.key).abbr} ${it.value}" } ?: "") + (if (u.id in r.offers) " · offered" else "") + " · ask ${money(r.nilAsk)}",
                        style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(38.dp)) {
            Text(grade(r.estOvr), style = T.numSm, color = tier(r.estOvr + 4))
            Text(grade(r.estPot), style = T.small.copy(fontSize = 12.sp), color = P.sub)
        }
    }
}

@Composable
fun RecruitDetail(vm: GameViewModel, lg: League, playerId: Int) {
    val r = lg.recruits.firstOrNull { it.p.id == playerId }
    if (r == null) {
        Column { BackBar("Prospect", onBack = { vm.back() }); Empty("This prospect has already signed.") }
        return
    }
    val u = lg.user
    val p = r.p
    val committed = r.committed >= 0
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar(p.name, if (r.transfer) "Transfer · ${p.pos} · ${p.cls}" else "${p.stars}-star ${p.pos} · No. ${p.recruitRank} nationally · No. ${p.posRank} ${p.pos}", onBack = { vm.back() })
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (!r.transfer) Stars(p.stars, 18.sp)
                        Text("${Ratings.heightLabel(p.height)} · ${p.weight} lbs", style = T.bodyB, color = P.ink)
                        Text(if (r.transfer && p.transferFrom >= 0) "Leaving ${lg.team(p.transferFrom).school}" else "${p.hometown} · ${p.highSchool}", style = T.small, color = P.sub)
                        if (p.traits.isNotEmpty()) Text(p.traits.joinToString(" · ") { it.label }, style = T.small, color = P.sub)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Ring(r.estOvr, 52.dp, label = if (r.scouted) "OVR" else "EST", textSize = 18.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Ring(r.estPot, 52.dp, label = "POT", textSize = 18.sp)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Stat("Style", p.style.short, Modifier.weight(1f), style = T.numSm)
                    Stat("NIL ask", money(r.nilAsk), Modifier.weight(1f), style = T.numSm)
                    Stat("Scouted", if (r.scouted) "Yes" else "No", Modifier.weight(1f), if (r.scouted) P.good else P.sub, T.numSm)
                }
            }
        }
        item {
            Section("Your pitch · ${lg.recruitPoints} points left")
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Scout · ${Recruiting.COST_SCOUT}", Modifier.weight(1f), enabled = !r.scouted) { vm.act { if (!Recruiting.scout(it, r)) vm.toast("Not enough points") } }
                    SmallButton("Call · ${Recruiting.COST_CALL}", Modifier.weight(1f), enabled = !committed) { vm.act { if (!Recruiting.call(it, r)) vm.toast("Not enough points") } }
                    SmallButton("Visit · ${Recruiting.COST_VISIT}", Modifier.weight(1f), enabled = !committed && Recruiting.visitReady(lg, r)) { vm.act { if (!Recruiting.visit(it, r)) vm.toast("Not enough points") } }
                }
                val pipe = com.nh.hoopscoach.game.Pipeline.points(lg, u.id)
                if (pipe > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Pro pipeline: +${(com.nh.hoopscoach.game.Pipeline.bonus(pipe, com.nh.hoopscoach.game.Pipeline.isTop(r)) * 100).toInt()}% interest from every pitch, thanks to your recent draft picks.",
                        style = T.small, color = P.good,
                    )
                }
                Spacer(Modifier.height(10.dp))
                if (u.id in r.offers) SecondaryButton("Withdraw scholarship offer", Modifier.fillMaxWidth(), enabled = !committed) { vm.act { Recruiting.withdraw(it, r) } }
                else PrimaryButton("Offer scholarship", Modifier.fillMaxWidth(), enabled = !committed) { vm.act { if (!Recruiting.offer(it, r)) vm.toast("No scholarships left to offer") } }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("NIL OFFER", style = T.label, color = P.sub)
                        Text(money(r.nilOffer), style = T.num, color = P.ink)
                    }
                    SmallButton("−") { vm.act { Recruiting.setNil(it, r, r.nilOffer - 25) } }
                    Spacer(Modifier.width(6.dp))
                    SmallButton("+") { vm.act { Recruiting.setNil(it, r, r.nilOffer + 25) } }
                    Spacer(Modifier.width(6.dp))
                    SmallButton("Match ask") { vm.act { Recruiting.setNil(it, r, r.nilAsk) } }
                }
                if (!r.onBoard && !committed) {
                    Spacer(Modifier.height(12.dp))
                    SecondaryButton("Add to targets", Modifier.fillMaxWidth()) { vm.act { Recruiting.addToBoard(it, r) } }
                }
            }
        }
        item {
            Section("Who's in the race")
            val ranked = r.interest.entries.sortedByDescending { it.value }.take(6)
            GroupList(ranked) { (tid, v) ->
                val t = lg.team(tid)
                ListRow(onClick = { vm.push(Route.TeamPage(tid)) }) {
                    TeamBadge(t, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row {
                            Text(t.school, style = T.bodyB, color = if (tid == u.id) P.accent else P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            if (tid in r.offers) Tag("OFFERED", P.good)
                        }
                        Spacer(Modifier.height(5.dp))
                        Bar(v / 100f, Modifier.fillMaxWidth(), color = if (tid == u.id) P.accent else P.faint, height = 5.dp)
                    }
                    Text("$v", style = T.numSm, color = P.ink, modifier = Modifier.width(42.dp), textAlign = TextAlign.End)
                }
            }
            if (committed) Panel(Modifier.padding(top = 10.dp)) { Text("Committed to ${lg.team(r.committed).school}", style = T.bodyB, color = P.good) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.Defense
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.Tactics
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Mood
import com.nh.hoopscoach.game.Morale
import com.nh.hoopscoach.game.Offense
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Redshirts
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.game.Trait

@Composable
fun TeamScreen(vm: GameViewModel, lg: League) {
    val t = lg.user
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("${lg.conf(t.confId).name} · Coach ${lg.coachOf(t).last}", t.school, "${t.mascot}  ·  ${recordLine(lg, t)}") {
                GuideButton { vm.openGuide(Guide.PROGRAM, if (lg.playerMode) (if (vm.teamTab == 0) 0 else 4) else when (vm.teamTab) { 0 -> 0; 1 -> 2; 2 -> 3; else -> 4 }) }
                TeamBadge(t, 46.dp)
            }
            TeamRings(lg, t)
            Spacer(Modifier.height(14.dp))
            // In player mode the coach runs the team: you see the roster and the program, not the controls.
            if (lg.playerMode) Segmented(listOf("Roster", "Program"), vm.teamTab.coerceIn(0, 1), { vm.teamTab = it })
            else Segmented(listOf("Roster", "Lineup", "Develop", "Program"), vm.teamTab, { vm.teamTab = it })
        }
        if (lg.playerMode) {
            if (vm.teamTab == 0) rosterList(vm, lg, t, mine = false)
            else item { ProgramNumbers(vm, lg, t); FanPanel(t); RivalsList(vm, lg, t); ProgramLegends(t) { vm.openPlayer(it) } }
        } else when (vm.teamTab) {
            0 -> rosterList(vm, lg, t, mine = true)
            1 -> lineup(vm, lg, t)
            2 -> develop(vm, lg, t)
            else -> program(vm, lg, t)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun TeamRings(lg: League, t: Team) {
    Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            RingStat("Overall", Tactics.teamRating(lg, t))
            RingStat("Offense", Tactics.teamRating(lg, t) { Ratings.offense(it) })
            RingStat("Defense", Tactics.teamRating(lg, t) { Ratings.defense(it) })
            RingStat("Power", Rankings.power(lg, t))
            RingStat("Prestige", t.prestige)
        }
    }
}

@Composable
private fun RingStat(label: String, v: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Ring(v, 50.dp, textSize = 19.sp)
        Spacer(Modifier.height(4.dp))
        Text(label.uppercase(), style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.6.sp), color = P.sub)
    }
}

// ---------- roster ----------

fun LazyListScope.rosterList(vm: GameViewModel, lg: League, t: Team, mine: Boolean) {
    val roster = lg.rosterOf(t)
    val redshirts = roster.filter { Redshirts.sitting(it) }.sortedByDescending { it.ovr }
    val active = roster.filter { !Redshirts.sitting(it) }
    val starters = t.plan.slots.mapNotNull { s -> active.firstOrNull { it.id == s.starter } }
    val rotation = t.plan.slots.mapNotNull { s -> active.firstOrNull { it.id == s.reserve } }
    val bench = active.filter { it !in starters && it !in rotation }.sortedByDescending { it.ovr }
    for ((label, list) in listOf("Starting five" to starters, "Rotation" to rotation, "Bench" to bench, "Redshirts" to redshirts)) {
        if (list.isEmpty()) continue
        item { Section(label) }
        item { GroupList(list, inset = 70.dp) { p -> RosterRow(vm, lg, t, p, mine) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RosterRow(vm: GameViewModel, lg: League, t: Team, p: Player, mine: Boolean) {
    val mins = Tactics.plannedMinutes(t, p.id)
    val you = p.id == lg.heroId
    ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (you) Modifier.background(P.accentSoft) else Modifier) {
        PlayerJersey(p, t, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (you) { Spacer(Modifier.width(6.dp)); Tag("YOU", P.accent, filled = true) }
                if (p.walkOn) { Spacer(Modifier.width(6.dp)); Tag("WALK-ON", P.sub) }
                if (p.out) { Spacer(Modifier.width(6.dp)); Tag("INJ", P.bad, filled = true) }
                if (Redshirts.sitting(p)) { Spacer(Modifier.width(6.dp)); Tag("REDSHIRT", P.accent) }
                else if (p.rsClass) { Spacer(Modifier.width(6.dp)); Tag("RS", P.sub) }
                if (mine) when (Morale.mood(p)) {
                    Mood.TRANSFER -> { Spacer(Modifier.width(6.dp)); Tag("AT RISK", P.bad) }
                    Mood.UNHAPPY -> { Spacer(Modifier.width(6.dp)); Tag("UNHAPPY", P.warn) }
                    else -> {}
                }
            }
            Text(
                // Pros show their age (every pro is a "PRO"); college players their class.
                "${p.posLabel} · ${if (p.year == com.nh.hoopscoach.game.PRO_YEAR) "age ${p.age}" else p.cls} · ${Ratings.heightLabel(p.height)}" +
                    when {
                        Redshirts.sitting(p) -> " · sitting out"
                        mins > 0 -> " · $mins min"
                        else -> ""
                    },
                style = T.small, color = P.sub, maxLines = 1,
            )
            if (p.out) Text(com.nh.hoopscoach.game.Injuries.tag(p), style = T.small, color = injuryColor(p), maxLines = 1)
            if (p.stats.gp > 0) Text(statLine(p), style = T.small, color = P.sub, maxLines = 1)
            if (p.traits.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    p.traits.forEach { Tag(it.label.uppercase(), traitColor(it)) }
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Ring(p.ovr, 44.dp)
    }
}

@Composable
fun traitColor(t: Trait): Color = when (t) {
    Trait.AMBITIOUS -> P.warn; Trait.BAG_CHASER -> P.gold; Trait.DISLOYAL -> P.bad
    Trait.LOYAL, Trait.TEAM_PLAYER, Trait.PASS_FIRST -> P.good
    Trait.GYM_RAT, Trait.CLUTCH -> P.accent
    Trait.LATE_BLOOMER -> P.gold
    Trait.GUNNER -> P.warn
}

// ---------- lineup ----------

internal fun LazyListScope.lineup(vm: GameViewModel, lg: League, t: Team) {
    item {
        Section("Game plan", action = if (t.plan.auto) "Auto ✓" else "Auto-set", onAction = {
            vm.act { Tactics.autoAllocate(it, it.user); it.user.plan.auto = true }
            vm.toast("Rotation set automatically")
        })
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Picker("Offense", t.plan.offense.label, Offense.entries.map { it.label to it.blurb }, Modifier.weight(1f)) { i -> vm.act { it.user.plan.offense = Offense.entries[i] } }
            Picker("Defense", t.plan.defense.label, Defense.entries.map { it.label to it.blurb }, Modifier.weight(1f)) { i -> vm.act { it.user.plan.defense = Defense.entries[i] } }
        }
        Section("Rotation · 200 minutes")
    }
    items(Pos.entries.size) { i -> SlotPanel(vm, lg, t, i) }
}

@Composable
private fun SlotPanel(vm: GameViewModel, lg: League, t: Team, idx: Int) {
    val slot = t.plan.slots.getOrNull(idx) ?: return
    val st = lg.playerOrNull(slot.starter)
    val rs = lg.playerOrNull(slot.reserve)
    var choose by remember { mutableStateOf<Boolean?>(null) }
    var minutes by remember(slot.starter, slot.reserve, vm.rev) { mutableIntStateOf(slot.starterMin) }
    Panel(Modifier.padding(bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(P.accentSoft), contentAlignment = Alignment.Center) {
                Text(Pos.entries[idx].name, style = T.numSm.copy(fontSize = 16.sp), color = P.accent)
            }
            Spacer(Modifier.width(12.dp))
            SlotPick("Starter", st, Modifier.weight(1f)) { choose = true }
            Spacer(Modifier.width(8.dp))
            SlotPick("Backup", rs, Modifier.weight(1f)) { choose = false }
        }
        Spacer(Modifier.height(10.dp))
        // The split of the position's minutes: the starter's on the left, the backup's on the right.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(44.dp)) {
                Text("$minutes", style = T.numSm, color = P.ink)
                Text("START", style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = P.sub)
            }
            HoopsSlider(
                minutes.toFloat(), { minutes = kotlin.math.round(it).toInt() }, 0f..40f, Modifier.weight(1f), enabled = rs != null,
                onFinished = { val m = minutes; vm.act { slot.starterMin = m; it.user.plan.auto = false } },
            )
            Column(Modifier.width(44.dp), horizontalAlignment = Alignment.End) {
                Text("${40 - minutes}", style = T.numSm, color = P.sub)
                Text("BENCH", style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.5.sp), color = P.sub)
            }
        }
        Expandable("Usage") {
            UsageRow("Starter shot share", slot.starterUsg) { v -> vm.act { slot.starterUsg = v; it.user.plan.auto = false } }
            if (rs != null) UsageRow("Backup shot share", slot.reserveUsg) { v -> vm.act { slot.reserveUsg = v; it.user.plan.auto = false } }
        }
    }
    if (choose != null) {
        val starter = choose == true
        AlertDialog(
            onDismissRequest = { choose = null }, containerColor = P.surface,
            title = { Text("${if (starter) "Starting" else "Backup"} ${Pos.entries[idx].name}", style = T.head, color = P.ink) },
            text = {
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    items(lg.rosterOf(t).sortedWith(compareByDescending<Player> { !Redshirts.sitting(it) }.thenByDescending { it.pos.ordinal == idx }.thenByDescending { it.plays(Pos.entries[idx]) }.thenByDescending { it.ovr })) { p ->
                        val sat = Redshirts.sitting(p)
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).then(
                                if (sat) Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                                else Modifier.clickable {
                                    choose = null
                                    vm.act { Tactics.swapInto(it, it.user, idx, starter, p.id) }
                                }.padding(vertical = 8.dp, horizontal = 4.dp),
                            ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(p.posLabel, style = T.small, color = if (p.plays(Pos.entries[idx])) P.accent else P.sub, modifier = Modifier.width(50.dp))
                            Text(p.name, style = T.bodyB, color = if (sat) P.faint else P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(Tactics.role(lg, t, p.id), style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = 8.dp))
                            Ring(p.ovr, 34.dp, textSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ choose = null }) { Text("Close", color = P.accent) } },
        )
    }
}

@Composable
private fun SlotPick(label: String, p: Player?, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.clip(RoundedCornerShape(12.dp)).border(1.dp, P.line, RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label.uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.sub)
            Text(p?.short ?: "Choose", style = T.bodyB.copy(fontSize = 14.sp), color = if (p == null) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p != null) Text("${shown(p.ovr)}", style = T.numSm.copy(fontSize = 17.sp), color = tier(shown(p.ovr)))
    }
}

@Composable
private fun UsageRow(label: String, value: Int, onSet: (Int) -> Unit) {
    var v by remember(value) { mutableIntStateOf(value) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.small, color = P.sub, modifier = Modifier.width(118.dp))
        HoopsSlider(v.toFloat(), { v = kotlin.math.round(it).toInt() }, 1f..99f, Modifier.weight(1f), onFinished = { onSet(v) })
        Text("$v", style = T.numSm.copy(fontSize = 16.sp), color = P.ink, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

// ---------- develop ----------

private fun LazyListScope.develop(vm: GameViewModel, lg: League, t: Team) {
    item {
        Section("Training focus")
        Panel {
            Text("Players earn XP from games and practice. Every ${Ratings.xpNeeded()} XP raises the three ratings of their focus.", style = T.small, color = P.sub)
        }
        Spacer(Modifier.height(10.dp))
    }
    item {
        GroupList(lg.rosterOf(t).sortedByDescending { it.ovr }, inset = 70.dp) { p ->
            ListRow(onClick = { vm.push(Route.Focus(p.id)) }) {
                PlayerJersey(p, t, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.focus.label, style = T.small, color = P.accent, maxLines = 1)
                        if (p.banked > 0) { Spacer(Modifier.width(6.dp)); Tag("${p.banked} TO SPEND", P.gold) }
                    }
                    Spacer(Modifier.height(5.dp))
                    Bar(p.xp.toFloat() / Ratings.xpNeeded(), Modifier.fillMaxWidth(0.9f), height = 5.dp)
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("${shown(p.ovr)}", style = T.numSm, color = tier(shown(p.ovr)))
                    Text("POT ${shown(p.pot)}", style = T.label.copy(fontSize = 9.sp), color = P.sub)
                }
            }
        }
    }
}

@Composable
fun FocusScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId) ?: return
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Training focus", p.name, onBack = { vm.back() })
            if (p.banked > 0) Panel(color = P.accentSoft) {
                Text("${p.banked} banked level-up${if (p.banked > 1) "s" else ""}. Pick a focus to spend them.", style = T.bodyB, color = P.accent)
            }
            Spacer(Modifier.height(8.dp))
        }
        // Only the archetypes of his position (plus whatever he trains now, for older saves).
        items(Arch.entries.filter { it == Arch.MANUAL || it == p.focus || it in Ratings.stylesFor(p) }) { a ->
            val current = p.focus == a
            Panel(
                Modifier.padding(bottom = 8.dp),
                color = if (current) P.accentSoft else P.surface,
                onClick = {
                    vm.act { val pl = it.player(playerId); if (a != Arch.MANUAL && pl.banked > 0) Ratings.spendBanked(pl, a); pl.focus = a }
                    vm.back()
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, style = T.head, color = if (current) P.accent else P.ink, modifier = Modifier.weight(1f))
                    if (current) Tag("CURRENT", P.accent, filled = true) else if (a == p.style) Tag("NATURAL FIT", P.good)
                }
                Text(a.blurb, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
                if (a.boosts.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        a.boosts.forEach { at -> Tag("${at.label.uppercase()} +1", P.accent) }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------- program ----------

private fun LazyListScope.program(vm: GameViewModel, lg: League, t: Team) {
    val budget = Finance.budget(lg, t)
    val spent = Morale.nilSpent(lg, t)
    val players = lg.rosterOf(t)
    val moods = players.groupingBy { Morale.mood(it) }.eachCount()
    val risk = (moods[Mood.TRANSFER] ?: 0) + (moods[Mood.UNHAPPY] ?: 0)
    item {
        ProgramNumbers(vm, lg, t)
        FanPanel(t)
        if (t.id == lg.userTeamId && !lg.playerMode) BusinessPanel(vm, lg, t)
        RivalsList(vm, lg, t)
        Section("Apparel")
        Panel {
            val co = com.nh.hoopscoach.game.Shoes.of(t)
            val kit = com.nh.hoopscoach.game.Shoes.apparelMoney(t)
            if (co != null) {
                Text(co.label, style = T.head, color = P.ink)
                Text(co.blurb, style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("Kit money", money(kit), Modifier.weight(1f), P.good)
                    Stat("Pull", "+${co.pullBonus}", Modifier.weight(1f))
                    Stat("Left", "${t.apparelLeft} yr", Modifier.weight(1f))
                }
            } else Text("No apparel partner yet.", style = T.body, color = P.sub)
            val shoed = players.count { it.shoePay > 0 }
            Spacer(Modifier.height(8.dp))
            Text(
                if (shoed == 0) "No personal shoe deals on the roster."
                else "$shoed player${if (shoed == 1) " has" else "s have"} a personal shoe deal (paid by the brand, not your budget).",
                style = T.small, color = P.sub,
            )
        }
        Section("NIL")
        Panel {
            Row {
                Stat("Budget", money(budget), Modifier.weight(1f))
                Stat("Committed", money(spent), Modifier.weight(1f))
                Stat("Room", money(budget - spent), Modifier.weight(1f), if (budget >= spent) P.good else P.bad)
            }
            Spacer(Modifier.height(12.dp))
            Bar(spent.toFloat() / budget.coerceAtLeast(1), Modifier.fillMaxWidth(), color = if (spent > budget) P.bad else P.accent, height = 8.dp)
            val under = players.filter { val e = Ratings.expectedNil(it); e - it.nil > maxOf(25, e / 12) }
            Spacer(Modifier.height(8.dp))
            Text(if (under.isEmpty()) "Everyone is paid at market value." else "${under.size} player${if (under.size > 1) "s are" else " is"} below market value. Open a player to adjust his deal.", style = T.small, color = if (under.isEmpty()) P.sub else P.warn)
        }
        Section("Locker room")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (risk == 0) "All good" else "$risk need attention", style = T.head, color = if (risk == 0) P.good else P.bad, modifier = Modifier.weight(1f))
                Text("${moods[Mood.HAPPY] ?: 0} happy · ${moods[Mood.CONTENT] ?: 0} content", style = T.small, color = P.sub)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
                val total = players.size.coerceAtLeast(1).toFloat()
                for ((m, c) in listOf(Mood.HAPPY to P.good, Mood.CONTENT to P.line, Mood.UNHAPPY to P.warn, Mood.TRANSFER to P.bad)) {
                    val n = moods[m] ?: 0
                    if (n > 0) Box(Modifier.weight(n / total).height(8.dp).background(c))
                }
            }
        }
    }
    val attention = players.filter { Morale.mood(it) == Mood.TRANSFER || Morale.mood(it) == Mood.UNHAPPY }.sortedBy { it.happiness }
    if (attention.isNotEmpty()) item {
        Spacer(Modifier.height(10.dp))
        GroupList(attention, inset = 70.dp) { p -> PlayerLine(vm, lg, t, p, Morale.reason(lg, t, p)) }
    }
    item {
        val c = lg.coachOf(t)
        if (t.id == lg.userTeamId && !lg.playerMode) { Spacer(Modifier.height(8.dp)); StaffLink(vm, lg) }
        Section("Coaching staff", action = "Develop", onAction = { vm.push(Route.CoachDev) })
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                RingStat("Offense", c.off); RingStat("Defense", c.def); RingStat("Recruit", c.rec); RingStat("Develop", c.dev)
            }
        }
        PipelinePanel(vm, lg, t)
        HomeCourtPanel(lg, t)
        ProgramLegends(t) { vm.openPlayer(it) }
        Section("Program records")
        RecordBookView(vm, lg, t.id, n = 3)
        FormerPlayers(vm, lg, t)
    }
}

// ---------- other teams ----------

@Composable
fun TeamPage(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    var tab by remember(teamId) { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar(t.name, "${lg.conf(t.confId).name} · ${if (lg.pro) franchiseLabel(t.prestige) else prestigeLabel(t.prestige)}", onBack = { vm.back() }) {
                FollowTeamButton(vm, lg, t)
                SmallButton("Connections") { vm.push(Route.Web(com.nh.hoopscoach.game.WebNode.TEAM, t.id)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(t, 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.school.uppercase(), style = T.title, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(recordLine(lg, t), style = T.small, color = P.sub)
                    val c = lg.coachOf(t)
                    Text("Coach ${c.name} · ${c.w}-${c.l} career", style = T.small, color = P.sub)
                }
            }
            TeamRings(lg, t)
            com.nh.hoopscoach.game.WorldEvents.sanctionLine(lg, t).takeIf { it.isNotEmpty() }?.let { line ->
                Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag("NCAA SANCTIONS", P.bad, filled = true)
                    Spacer(Modifier.width(8.dp))
                    Text(line, style = T.small, color = P.bad)
                }
            }
            Spacer(Modifier.height(12.dp))
            Segmented(listOf("Roster", "Schedule", "History", if (lg.pro) "Franchise" else "Program"), tab, { tab = it })
        }
        when (tab) {
            0 -> rosterList(vm, lg, t, mine = t.id == lg.userTeamId)
            1 -> item {
                Spacer(Modifier.height(10.dp))
                GroupList(Schedule.teamGames(lg, t.id)) { g -> GameRow(vm, lg, g, t.id) }
            }
            3 -> item {
                ProgramNumbers(vm, lg, t)
                HomeCourtPanel(lg, t)
                Section("Program records")
                RecordBookView(vm, lg, t.id, n = 3)
                FanPanel(t)
                val co = com.nh.hoopscoach.game.Shoes.of(t)
                if (co != null) {
                    Section("Apparel")
                    Panel {
                        Text(co.label, style = T.head, color = P.ink)
                        Text("${co.blurb} · ${money(com.nh.hoopscoach.game.Shoes.apparelMoney(t))}/yr kit money", style = T.small, color = P.sub)
                    }
                }
                ProgramLegends(t) { vm.openPlayer(it) }
            }
            else -> item {
                com.nh.hoopscoach.game.Timeline.of(lg, t.id).takeIf { it.isNotEmpty() }?.let { events ->
                    Section("Timeline")
                    TimelineRows(vm, lg, events.sortedByDescending { it.season * 10 + it.weight }.take(12), showSchool = false)
                }
                ProgramLegends(t) { vm.openPlayer(it) }
                FormerPlayers(vm, lg, t)
                RivalsList(vm, lg, t)
                PipelinePanel(vm, lg, t)
                if (lg.pro) FranchiseHistory(lg, t) else CollegeTitles(lg, t)
                Section(if (lg.pro) "Seasons in this league" else "Seasons")
                if (t.history.isEmpty()) Empty("No completed seasons yet.")
                GroupList(t.history.reversed()) { h ->
                    ListRow {
                        Text("${h.season}", style = T.numSm, color = P.sub, modifier = Modifier.width(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${h.w}-${h.l}" + if (lg.pro) "" else " (${h.cw}-${h.cl})", style = T.bodyB, color = P.ink)
                            Text("${h.result} · ${h.coach}", style = T.small, color = if (h.result.contains("Champion")) P.gold else P.sub)
                        }
                        if (lg.pro) Text(if (h.ap > 0) "No. ${h.ap}" else "—", style = T.numSm, color = P.sub)
                        else Text("${h.prestige}", style = T.numSm, color = tier(h.prestige))
                    }
                }
                if (lg.pro) {
                    // The real seasons before this league began.
                    val first = com.nh.hoopscoach.game.NbaHistory.firstSeason(lg)
                    val real = (first - 1 downTo maxOf(1980, first - 15)).mapNotNull { y -> com.nh.hoopscoach.game.NbaHistory.real(y, t.slot)?.let { y to it } }
                    if (real.isNotEmpty()) {
                        Section("Before this league")
                        GroupList(real) { (y, r) ->
                            ListRow {
                                Text("$y", style = T.numSm, color = P.sub, modifier = Modifier.width(56.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(r.record, style = T.bodyB, color = P.ink)
                                    Text(r.result, style = T.small, color = if (r.result == "Champion") P.gold else P.sub)
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
fun GameRow(vm: GameViewModel, lg: League, g: com.nh.hoopscoach.game.Game, me: Int) {
    val opp = lg.team(g.opp(me))
    val won = g.played && g.winner() == me
    ListRow(onClick = { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(opp.id)) }) {
        if (g.played) ResultDot(won) else Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
            Text(if (g.day <= Schedule.REG_DAYS) "${g.day}" else "–", style = T.small, color = P.faint)
        }
        Spacer(Modifier.width(10.dp))
        Text(g.site(me).let { if (it == "N") "vs" else it }, style = T.small, color = P.sub, modifier = Modifier.width(24.dp))
        TeamBadge(opp, 24.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(teamLabel(opp), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(gameKind(lg, g), style = T.small.copy(fontSize = 12.sp), color = if (lg.rivalry(g) != null) P.bad else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (g.played) Text("${g.scoreFor(me)}-${g.scoreAgainst(me)}${if (g.ot > 0) " OT" else ""}", style = T.numSm, color = if (won) P.good else P.bad)
    }
}

fun gameKind(lg: League, g: com.nh.hoopscoach.game.Game): String {
    if (lg.pro) return g.label.ifEmpty { "Regular season" }
    val base = when (g.type) {
        com.nh.hoopscoach.game.GameType.CONF -> "Conference"
        com.nh.hoopscoach.game.GameType.NONCONF -> "Non-conference"
        else -> g.label
    }
    return lg.rivalry(g)?.let { "${it.title} · $base" } ?: base
}

@Composable
fun ScheduleScreen(vm: GameViewModel, lg: League, teamId: Int) {
    val t = lg.team(teamId)
    val games = Schedule.teamGames(lg, teamId)
    val reg = games.filter { it.day <= Schedule.REG_DAYS }
    val post = games.filter { it.day > Schedule.REG_DAYS }
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Schedule & results", "${t.school} · ${t.record} (${t.confRecord})", onBack = { vm.back() }) }
        item { Section("Regular season"); GroupList(reg) { g -> GameRow(vm, lg, g, teamId) } }
        if (post.isNotEmpty()) item { Section("Postseason"); GroupList(post) { g -> GameRow(vm, lg, g, teamId) } }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Suppress("unused")
private fun unusedStandings(lg: League) = Season.standings(lg, 0)

@Suppress("unused")
private val unusedShape = CircleShape


/** An NBA franchise's history: its titles (the real ones before this league, then this league's) and its run of playoff trips. */
@Composable
private fun FranchiseHistory(lg: League, t: Team) {
    val h = com.nh.hoopscoach.game.NbaHistory
    val titles = h.titles(lg, t)
    val streak = h.playoffStreak(lg, t)
    val misses = h.missStreak(lg, t)
    Section("Franchise history")
    Panel {
        Text(if (titles.isEmpty()) "No championships yet" else h.titleWords(titles.size).replaceFirstChar { it.uppercase() }, style = T.head, color = if (titles.isEmpty()) P.ink else P.gold)
        if (titles.isNotEmpty()) Text(titles.joinToString(", "), style = T.small, color = P.sub)
        val last = h.last(lg, t)
        if (last != null) Text("Last season: ${last.record}, ${h.phrase(last.result)}.", style = T.small, color = P.ink, modifier = Modifier.padding(top = 6.dp))
        when {
            streak >= 2 -> Text("$streak straight playoff trips.", style = T.small, color = P.good)
            misses >= 2 -> Text("$misses straight seasons without the playoffs.", style = T.small, color = P.bad)
        }
    }
}

/** A college program's national titles: the real ones before this league, then this league's. */
@Composable
private fun CollegeTitles(lg: League, t: Team) {
    val titles = com.nh.hoopscoach.game.CollegeHistory.titles(lg, t)
    if (titles.isEmpty()) return
    Section("National titles")
    Panel {
        Text(com.nh.hoopscoach.game.CollegeHistory.count(titles.size).replaceFirstChar { it.uppercase() }, style = T.head, color = P.gold)
        Text(titles.joinToString(", "), style = T.small, color = P.sub)
    }
}

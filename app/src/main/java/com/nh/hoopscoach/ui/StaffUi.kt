package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Assistants
import com.nh.hoopscoach.game.DevPlans
import com.nh.hoopscoach.game.League

/** The staff and development screen: assistants, summer plans, the coaching tree. */
@Composable
fun StaffScreen(vm: GameViewModel, lg: League) {
    var tab by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Staff & development", "Coach ${lg.userCoach.last} · ${if (lg.pro) lg.user.name else lg.user.school}", onBack = { vm.back() })
            Segmented(listOf("Staff", "Development", "Coaching tree"), tab, { tab = it })
            Spacer(Modifier.height(6.dp))
        }
        when (tab) {
            0 -> staffItems(vm, lg)
            1 -> devItems(vm, lg)
            else -> treeItems(vm, lg)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

internal fun LazyListScope.staffItems(vm: GameViewModel, lg: League) {
    item {
        Section("Your assistants · ${lg.staff.size} of ${Assistants.SLOTS}")
        if (lg.staff.isEmpty()) Panel { Text("No assistants yet. Hire up to three: each one adds up to 10 points to his specialty.", style = T.body, color = P.sub) }
        GroupList(lg.staff) { a ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = T.bodyB, color = P.ink)
                    Text("${Assistants.skillName(a.skill)} · age ${a.age} · ${a.seasons} season${if (a.seasons == 1) "" else "s"} with you", style = T.small, color = P.sub)
                }
                Ring(a.rating, 36.dp)
                Spacer(Modifier.width(8.dp))
                SmallButton("Let go", danger = true) { vm.act { Assistants.release(it, a.id) } }
            }
        }
        Text("Good assistants get better every year, and the best get hired away as head coaches: that's your coaching tree.",
            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
    item {
        val pool = Assistants.pool(lg)
        Section("Available this year")
        GroupList(pool) { a ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = T.bodyB, color = P.ink)
                    Text("${Assistants.skillName(a.skill)} · age ${a.age}", style = T.small, color = P.sub)
                }
                Ring(a.rating, 36.dp)
                Spacer(Modifier.width(8.dp))
                SmallButton("Hire", primary = true, enabled = lg.staff.size < Assistants.SLOTS) { vm.act { vm.toast(Assistants.hire(it, a.id)) } }
            }
        }
    }
}

internal fun LazyListScope.devItems(vm: GameViewModel, lg: League) {
    item {
        Text("Point each player's summer at one part of his game. The plan's ratings grow (more for young players) at the cost of a point somewhere else; conditioning keeps him healthier instead.",
            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
    val roster = lg.rosterOf(lg.user).sortedWith(compareBy<com.nh.hoopscoach.game.Player> { it.age }.thenByDescending { it.pot })
    for (p in roster) item {
        Panel(Modifier.padding(vertical = 3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, lg.user, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.posLabel} · age ${p.age} · plan: ${DevPlans.label(p.devPlan)}", style = T.small, color = P.sub)
                }
                Ring(p.ovr, 34.dp)
            }
            Spacer(Modifier.height(6.dp))
            Chips(DevPlans.PLANS.map { it.second }, DevPlans.PLANS.indexOfFirst { it.first == p.devPlan }.coerceAtLeast(0), { i -> vm.act { DevPlans.set(it, p.id, DevPlans.PLANS[i].first) } })
        }
    }
}

internal fun LazyListScope.treeItems(vm: GameViewModel, lg: League) {
    item {
        Section("The ${lg.userCoach.last} coaching tree · ${lg.tree.size}")
        if (lg.tree.isEmpty()) Panel { Text("Nobody from your staff has a head job yet. Hire good assistants and give them time.", style = T.body, color = P.sub) }
        GroupList(lg.tree.reversed()) { b ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(b.name, style = T.bodyB, color = P.ink)
                    Text("Left in ${b.left} for ${b.teamName} · ${Assistants.skillName(b.skill).lowercase()}, ${b.seasonsWithYou} seasons with you", style = T.small, color = P.sub)
                    Assistants.record(lg, b).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.small, color = P.accent) }
                }
            }
        }
    }
}

/** A row that opens the staff screen. */
@Composable
fun StaffLink(vm: GameViewModel, lg: League) {
    Panel(onClick = { vm.push(Route.Staff) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Staff & development", style = T.bodyB, color = P.ink)
                Text("${lg.staff.size} of ${Assistants.SLOTS} assistants · ${lg.rosterOf(lg.user).count { it.devPlan.isNotEmpty() }} players on a summer plan · tree of ${lg.tree.size}", style = T.small, color = P.sub)
            }
            Chevron()
        }
    }
}

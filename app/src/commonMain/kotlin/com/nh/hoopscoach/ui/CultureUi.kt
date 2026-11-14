package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Culture
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

/** The locker room: chemistry, the captains the players voted for, and who's dragging it down. */
@Composable
fun LockerRoomPanel(vm: GameViewModel, lg: League, t: Team) {
    if (lg.pro) return
    val chem = Culture.chemistry(lg, t)
    val color = when { chem >= 60 -> P.good; chem >= 42 -> P.warn; else -> P.bad }
    val captains = t.captains.mapNotNull { lg.playerOrNull(it)?.takeIf { p -> p.teamId == t.id } }
    val apples = lg.rosterOf(t).filter { Culture.badApple(it) }
    Section("Locker room")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Culture.label(chem), style = T.head, color = color)
                Text("Team chemistry $chem/100", style = T.small, color = P.sub)
            }
            Ring(chem, 48.dp, label = "CHEM")
        }
        Spacer(Modifier.height(8.dp))
        Bar(chem / 100f, Modifier.fillMaxWidth(), color = color, height = 6.dp)
        if (captains.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("CAPTAINS · VOTED BY THE PLAYERS", style = T.label, color = P.accent)
            captains.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.captainVotes.getOrNull(i) ?: 0} votes · leads ${Culture.leadership(lg, t, p)}", style = T.small, color = P.sub)
                }
            }
        }
        if (captains.isEmpty()) Text("The players vote for captains when the season starts.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 10.dp))
        if (apples.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("DRAGGING THE ROOM DOWN", style = T.label, color = P.bad)
            Text(apples.joinToString(", ") { it.name }, style = T.small, color = P.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                chem >= 60 -> "A together team plays a little better: cleaner possessions, better looks."
                chem >= 42 -> "Strong captains pull unhappy teammates back up. Playing time and NIL fix the rest."
                else -> "A fractured room costs you on the floor and drags every player's mood down. Fix the unhappy players first."
            },
            style = T.small, color = P.faint,
        )
    }
}

/** Mentors: pair each freshman with a junior or senior at his position; the freshman grows faster. */
@Composable
fun MentorsPanel(vm: GameViewModel, lg: League, t: Team) {
    if (lg.pro || lg.playerMode) return
    val freshmen = lg.rosterOf(t).filter { it.year == 1 }
    if (freshmen.isEmpty()) return
    var picking by remember { mutableStateOf<Player?>(null) }
    Section("Mentors")
    GroupList(freshmen) { f ->
        val m = lg.playerOrNull(f.mentorId)?.takeIf { it.teamId == t.id }
        ListRow(onClick = { picking = f }) {
            Column(Modifier.weight(1f)) {
                Text("${f.name} · ${f.posLabel}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m != null) "Mentored by ${m.name} (${m.cls}) · +20% practice growth" else "No mentor yet", style = T.small, color = if (m != null) P.good else P.sub)
            }
            Chevron()
        }
    }
    Text("A mentor speeds up a freshman's growth all season and gives him a bigger jump that summer. Mentors enjoy the role.",
        style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    picking?.let { f ->
        val options = Culture.mentorOptions(lg, t, f)
        AlertDialog(
            onDismissRequest = { picking = null }, containerColor = P.surface,
            title = { Text("A mentor for ${f.short}", style = T.head, color = P.ink) },
            text = {
                Column {
                    if (options.isEmpty()) Text("No junior or senior ${f.posLabel} is free to mentor him.", style = T.body, color = P.sub)
                    options.forEach { m ->
                        ListRow(onClick = { vm.act { it.playerOrNull(f.id)?.mentorId = m.id }; picking = null }) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = T.bodyB, color = P.ink)
                                Text("${m.cls} · leads ${Culture.leadership(lg, t, m)}" + m.traits.filter { it.good }.take(2).joinToString("") { " · ${it.label}" }, style = T.small, color = P.sub)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (f.mentorId >= 0) TextButton({ vm.act { it.playerOrNull(f.id)?.mentorId = -1 }; picking = null }) { Text("Remove mentor", color = P.bad) }
            },
            dismissButton = { TextButton({ picking = null }) { Text("Close", color = P.sub) } },
        )
    }
}

/** Midnight Madness, before the first game: how big a show to put on. */
@Composable
fun MidnightMadnessCard(vm: GameViewModel, lg: League) {
    if (!Culture.madnessOpen(lg)) return
    val t = lg.user
    com.nh.hoopscoach.game.Business.ensure(lg, t)
    val k = Culture.hype(t)
    Section("Midnight Madness")
    Panel(color = P.hero) {
        Text("THE SEASON TIPS OFF TONIGHT", style = T.label, color = heroKicker)
        Text("How big a show?", style = T.h2, color = P.onHero)
        Text("Your program's hype multiplies the night: ${"%.1f".format(k)}×. Upgrade budget: ${money(t.cash.toInt())}.", style = T.small, color = P.onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        for (s in Culture.SHOWS) {
            val afford = t.cash >= s.cost
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name + if (s.cost > 0) " · ${money(s.cost.toInt())}" else "", style = T.bodyB, color = P.onHero)
                    Text(s.blurb, style = T.small, color = P.onHero.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(8.dp))
                SmallButton("Host", primary = true, enabled = afford) {
                    vm.act { Culture.midnightMadness(it, s.id) }
                    vm.toast(lg.madnessNote.ifEmpty { "Midnight Madness is on" })
                }
            }
        }
    }
}

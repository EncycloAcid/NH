package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.TeamUSA

/** The last Summer League: the champion and MVP, the standouts and how the user's young players did. */
@Composable
fun SummerLeaguePanel(vm: GameViewModel, lg: League) {
    val sl = lg.proState?.summerLeague?.takeIf { it.season == lg.season } ?: return
    val champ = lg.teams.getOrNull(sl.champion)
    Section("Summer League · Las Vegas")
    Panel(color = P.hero) {
        Text("CHAMPIONS", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text(champ?.name ?: "", style = T.head, color = P.onHero)
        lg.playerOrNull(sl.mvp)?.let { Text("MVP: ${it.name} · ${sl.mvpLine}", style = T.small, color = P.onHero.copy(alpha = 0.9f)) }
    }
    val mine = sl.lines.filter { it.teamId == lg.userTeamId }
    val rest = sl.lines.filter { it.teamId != lg.userTeamId && it.note.isNotEmpty() }
    for ((title, list) in listOf("Your young players" to mine, "Around Las Vegas" to rest)) {
        if (list.isEmpty()) continue
        Section(title)
        GroupList(list) { l ->
            ListRow(onClick = { vm.openPlayer(l.pid) }) {
                Column(Modifier.weight(1f)) {
                    Text("${l.name} · ${l.pos}" + (lg.teams.getOrNull(l.teamId)?.let { if (it.id != lg.userTeamId) " · ${it.abbr}" else "" } ?: ""), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("%.1f pts · %.1f reb · %.1f ast".format(l.ppg, l.rpg, l.apg), style = T.small, color = P.sub)
                }
                if (l.note.isNotEmpty()) Tag(l.note.uppercase(), when (l.note) { "MVP" -> P.gold; "Standout" -> P.good; else -> P.bad })
            }
        }
    }
}

/** Team USA: the call, the roster picker, and the result. */
@Composable
fun TeamUsaPanel(vm: GameViewModel, lg: League) {
    val u = TeamUSA.active(lg) ?: return
    if (u.status == 2) return
    Section("Team USA · ${u.season} ${u.event}")
    when (u.status) {
        0 -> Panel(color = P.hero) {
            Text("USA BASKETBALL IS CALLING", style = T.label, color = P.onHero.copy(alpha = 0.8f))
            Text("Coach the ${u.event}?", style = T.head, color = P.onHero)
            Text(if (u.pro) "Pick twelve of the best Americans in the league and bring home gold." else "Pick twelve of the best American teenagers in college and bring home gold.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
            Spacer(Modifier.height(10.dp))
            Row {
                SmallButton("Take the job", primary = true) { vm.act { TeamUSA.respond(it, true) } }
                Spacer(Modifier.width(8.dp))
                SmallButton("Not this time") { vm.act { TeamUSA.respond(it, false) } }
            }
        }
        1 -> {
            Text("Your twelve: ${u.picked.size} of 12 picked. Tap to add or drop.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
            GroupList(u.pool.mapNotNull { lg.playerOrNull(it) }, inset = 60.dp) { p ->
                val on = p.id in u.picked
                ListRow(onClick = { vm.act { TeamUSA.toggle(it, p.id) } }, modifier = if (on) Modifier.background(P.accentSoft) else Modifier) {
                    lg.teams.getOrNull(p.teamId)?.let { PlayerJersey(p, it, 38.dp) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${p.posLabel} · ${lg.teams.getOrNull(p.teamId)?.abbr ?: ""} · age ${p.age}", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 34.dp)
                    Spacer(Modifier.width(8.dp))
                    Tag(if (on) "IN" else "ADD", if (on) P.good else P.faint)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(Gutter))
                PrimaryButton("Play the ${u.event}", Modifier.weight(1f), enabled = u.picked.size == 12) { vm.act { TeamUSA.play(it) } }
                Spacer(Modifier.width(Gutter))
            }
        }
        else -> Panel {
            Text(u.medal.uppercase(), style = T.head, color = if (u.medal == "Gold") P.gold else P.ink)
            u.games.forEach { Text(it, style = T.small, color = P.sub) }
        }
    }
}

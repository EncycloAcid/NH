package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Injuries
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Team

/** [t]'s injury report: who's out, with what, and for how long. Nothing when everyone is healthy. */
@Composable
fun InjuryReport(vm: GameViewModel, lg: League, t: Team) {
    val hurt = Injuries.report(lg, t)
    if (hurt.isEmpty()) return
    Section("Injury report · ${hurt.size}")
    GroupList(hurt, inset = 70.dp) { p ->
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            PlayerJersey(p, t, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Injuries.tag(p), style = T.small, color = P.bad, maxLines = 2)
            }
            Tag("INJ", P.bad)
        }
    }
}

/** The league's notable injuries, for the News page. */
@Composable
fun LeagueInjuries(vm: GameViewModel, lg: League) {
    val hurt = Injuries.league(lg).take(12)
    if (hurt.isEmpty()) return
    Section("Around the league: injuries")
    GroupList(hurt, inset = 70.dp) { (p, t) ->
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            PlayerJersey(p, t, 40.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${p.name} · ${t.abbr}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(Injuries.tag(p), style = T.small, color = P.bad, maxLines = 2, modifier = Modifier.padding(top = 1.dp))
            }
        }
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.WorldEvent

/** Rows of the world's timeline: the year, what happened, and the line recorded when it did. Tap one to open its school. */
@Composable
fun TimelineRows(vm: GameViewModel, lg: League, events: List<WorldEvent>, showSchool: Boolean = true) {
    GroupList(events, inset = 62.dp) { e ->
        val t = lg.teams.getOrNull(e.teamId)
        ListRow(onClick = if (t != null && showSchool) ({ vm.push(Route.TeamPage(t.id)) }) else null) {
            Column(Modifier.width(48.dp)) {
                Text("${e.season}", style = T.numSm, color = if (e.weight >= 4) P.gold else P.accent)
                Box(Modifier.size(6.dp).clip(CircleShape).background(if (e.weight >= 4) P.gold else P.line))
            }
            Column(Modifier.weight(1f)) {
                Text(headline(e.title), style = T.bodyB, color = P.ink)
                Text(e.text, style = T.small, color = P.sub)
            }
            if (t != null && showSchool) { Spacer(Modifier.width(8.dp)); TeamBadge(t, 28.dp) }
        }
    }
}

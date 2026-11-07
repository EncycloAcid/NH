package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Lifetime

/**
 * For a coach who played first: the whole path (player, college coach, NBA coach) and his playing
 * career. Shows nothing for a coach who didn't.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayingPast(lg: League) {
    val p = lg.playedAs ?: return
    Section("Your path")
    GroupList(Lifetime.path(lg)) { s ->
        ListRow {
            Text(s.years.ifEmpty { "—" }, style = T.numSm.copy(fontSize = 13.sp), color = P.accent, modifier = Modifier.width(92.dp), maxLines = 2)
            Column(Modifier.weight(1f)) {
                Text(s.role.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Text(s.where, style = T.bodyB, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (s.line.isNotEmpty()) Text(s.line, style = T.small, color = P.sub, maxLines = 2)
            }
        }
    }
    Section("As a player")
    Panel {
        Text("${p.name} · ${p.pos}", style = T.head, color = P.ink)
        Text(p.verdict + (if (p.allTime > 0) " · No. ${p.allTime} all-time" else ""), style = T.small, color = P.accent)
        Spacer(Modifier.height(10.dp))
        Text("COLLEGE · ${p.collegeYears}", style = T.label.copy(fontSize = 10.sp), color = P.sub)
        Text(p.college, style = T.bodyB, color = P.ink)
        Text(p.collegeLine, style = T.small, color = P.sub)
        if (p.collegeHonors.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                p.collegeHonors.forEach { Tag(it.uppercase(), P.accent) }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("DRAFT", style = T.label.copy(fontSize = 10.sp), color = P.sub)
        Text(p.draft, style = T.bodyB, color = P.ink)
        Spacer(Modifier.height(10.dp))
        if (p.proYears.isNotEmpty()) {
            Text("NBA · ${p.proYears}", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Text(p.proTeams.joinToString(" → "), style = T.bodyB, color = P.ink)
            Text(p.proLine, style = T.small, color = P.sub)
            if (p.honors.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    p.honors.forEach { Tag(it.uppercase(), P.gold) }
                }
            }
        } else {
            Text("PRO", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Text("Played overseas", style = T.bodyB, color = P.ink)
        }
        if (p.hofClass > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (p.inducted) "Hall of Fame, class of ${p.hofClass}" else "Hall of Fame class of ${p.hofClass}: you're inducted that year, wherever you're coaching",
                style = T.small, color = P.gold,
            )
        }
        if (!lg.pro) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Win here and NBA teams will call, on the Career decision step of the offseason." +
                    if (Lifetime.name(p) > 0) " Your NBA name gets those calls sooner, and your old team often calls first." else "",
                style = T.small.copy(fontSize = 12.sp), color = P.faint,
            )
        }
    }
}

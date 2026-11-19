package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Scout
import com.nh.hoopscoach.game.Team

/** The scout's full report on a team: the five and the bench, everything that stands out, the player to watch, the question and the range. */
@Composable
fun ScoutingReport(vm: GameViewModel, lg: League, t: Team, lo: Int, hi: Int) {
    val r = remember(t.id, lg.season, lg.day) { Scout.report(lg, t) }
    Section("Scouting report")
    Panel {
        Text("STARTING FIVE · ${com.nh.hoopscoach.game.Previews.ordinal(r.startRank)} of ${r.n}", style = T.label, color = P.sub)
        Text(r.starters.joinToString(" · ") { "${it.last} (${it.pos})" }, style = T.bodyB, color = P.ink)
        Spacer(Modifier.height(6.dp))
        Text("BENCH · ${com.nh.hoopscoach.game.Previews.ordinal(r.benchRank)} of ${r.n}", style = T.label, color = P.sub)
        Text(r.bench.joinToString(" · ") { "${it.last} (${it.pos})" }.ifEmpty { "—" }, style = T.body, color = P.ink)
        if (r.insights.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("WHAT STANDS OUT", style = T.label, color = P.sub)
            r.insights.take(6).forEach { ins ->
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
                    Text("•", style = T.bodyB, color = P.accent)
                    Spacer(Modifier.width(8.dp))
                    Text(ins.text, style = T.small, color = P.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        r.breakout?.let { b ->
            Spacer(Modifier.height(10.dp))
            Text("PLAYER TO WATCH", style = T.label, color = P.sub)
            Text(b.name, style = T.bodyB, color = P.accent, modifier = Modifier.padding(top = 2.dp))
            Text(r.breakoutWhy, style = T.small, color = P.ink)
            if (b.stats.gp >= 5) Text("So far: ${f1(b.stats.ppg)} points, ${f1(b.stats.rpg)} rebounds and ${f1(b.stats.apg)} assists in ${b.stats.gp} games.", style = T.small, color = P.accent)
        }
        Spacer(Modifier.height(10.dp))
        Text("THE QUESTION", style = T.label, color = P.sub)
        Text(r.question, style = T.bodyB, color = P.ink)
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.weight(1f)) {
                Text("BEST CASE", style = T.label, color = P.good)
                Text("$hi wins", style = T.numSm, color = P.ink)
                Text("if ${r.best}", style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("WORST CASE", style = T.label, color = P.bad)
                Text("$lo wins", style = T.numSm, color = P.ink)
                Text("if ${r.worst}", style = T.small, color = P.sub)
            }
        }
    }
}

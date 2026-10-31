package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Earned
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Leadership
import kotlin.math.roundToInt

/**
 * The Me tab's "Your team" panel: where you're taking your team (preseason projection against
 * the pace), your plus-minus, the lift your teammates get with you on the floor, your leadership,
 * and what this summer's growth looks like so far.
 */
@Composable
fun ImpactPanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    if (h.teamId < 0) return
    val t = lg.team(h.teamId)
    val s = h.stats
    Section("Leading your team")
    Panel {
        val (proj, pace) = Leadership.projection(lg, t)
        if (proj > 0) {
            Text("PROJECTED ${proj} WINS · ${if (t.w + t.l == 0) "SEASON AHEAD" else "ON PACE FOR $pace"}", style = T.label, color = P.accent)
            val diff = pace - proj
            Text(
                when {
                    t.w + t.l == 0 -> "The ${t.mascot} were picked for $proj wins. Beat that and it's your team."
                    diff >= 3 -> "+$diff over the projection. You're carrying them."
                    diff <= -3 -> "$diff under the projection. The team needs more from you."
                    else -> "Right about where they were picked."
                },
                style = T.bodyB, color = P.ink,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PLUS-MINUS", style = T.label, color = P.sub)
                Text(if (s.gp == 0) "–" else "%+.1f a game".format(s.pm.toDouble() / s.gp), style = T.head, color = if (s.pm >= 0) P.good else P.bad)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("LEADERSHIP", style = T.label, color = P.sub)
                Text("${Leadership.label(r.leadership)} · ${r.leadership}", style = T.bodyB, color = P.ink)
            }
        }
        val lift = Leadership.lift(lg, lg.rosterOf(t))
        Text(
            if (lift > 0.0005) "With you on the floor your teammates shoot about ${(lift * 1000).roundToInt() / 10.0}% better: defenses have to load up on you."
            else "Become the best player on the floor and your teammates get easier shots.",
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp),
        )
        Text("Leadership grows with seasons as a starter, winning beyond the projection and how you handle the press; a new team means earning it again.",
            style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
    Section("How you're growing")
    Panel {
        Text(Earned.outlook(lg, h), style = T.bodyB, color = P.ink)
        Text("Summer growth is earned: minutes, playing above your rating, and practice. Last summer: ${r.growthNote.ifEmpty { "not yet." }}",
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.RivalryPsyche

@Composable
private fun mindColor(score: Int): Color = when { score >= 2 -> P.bad; score == 1 -> P.warn; score == 0 -> P.ink; else -> P.sub }

@Composable
private fun MindLine(who: String, m: RivalryPsyche.Mind) {
    Row(Modifier.padding(top = 4.dp)) {
        Text("$who: ", style = T.small, color = P.sub)
        Column(Modifier.weight(1f)) {
            Text(m.label, style = T.bodyB.copy(fontSize = 14.sp), color = mindColor(m.score))
            if (m.reasons.isNotEmpty()) Text(m.reasons.joinToString(" · "), style = T.small, color = P.sub)
        }
    }
}

/**
 * Before a rivalry game: where each side's head is at (from the rivalry's history and what you've said), and your
 * stance going in, which moves both.
 */
@Composable
fun RivalryMindPanel(vm: GameViewModel, lg: League, g: Game) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    if (lg.rivalry(g) == null || !g.involves(lg.userTeamId)) return
    val u = lg.userTeamId
    val opp = lg.team(g.opp(u))
    val box = RoundedCornerShape(14.dp)
    Column(Modifier.fillMaxWidth().clip(box).border(1.dp, P.line, box).padding(12.dp)) {
        Text("RIVALRY PSYCHOLOGY", style = T.label.copy(fontSize = 10.sp), color = P.bad)
        MindLine("You", RivalryPsyche.mind(lg, g, u))
        MindLine(if (lg.pro) opp.name else opp.school, RivalryPsyche.mind(lg, g, opp.id))
        if (!g.played) {
            Spacer(Modifier.height(10.dp))
            Text("YOUR STANCE THIS WEEK", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            val cur = RivalryPsyche.stance(lg, g)
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RivalryPsyche.Stance.entries.forEach { s ->
                    SmallButton(s.label, Modifier.weight(1f), primary = cur == s) { vm.act { RivalryPsyche.setStance(it, g, s) } }
                }
            }
            Text((cur ?: RivalryPsyche.Stance.EMBRACE).let { if (cur == null) "Pick one before tip-off. Each point of mindset is a little sharper shooting and fewer turnovers." else it.hint },
                style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

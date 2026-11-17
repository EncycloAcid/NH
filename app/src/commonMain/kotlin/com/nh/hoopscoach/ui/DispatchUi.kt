package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.WeeklyDispatch

@Composable
private fun Bullet(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(10.dp))
        Text(text, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
    }
}

/**
 * On Today: this week's Hoops Dispatch. The game's own edition shows the moment the week ends; when AI is on,
 * the AI edition replaces it once it's written (one batched call a week).
 */
@Composable
fun DispatchCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val ed = WeeklyDispatch.latest(lg) ?: return
    if (ed.headlines.isEmpty()) return
    Section("The Hoops Dispatch · week ${ed.week}")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (ed.ai) "THIS WEEK'S BIGGEST STORIES" else "THIS WEEK AROUND THE LEAGUE", style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.weight(1f))
            AiBadge(ed.ai, ed.writer)
        }
        ed.headlines.take(5).forEach { Bullet(it, P.accent) }
        if (ed.rumors.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(if (lg.pro) "TRADE RUMORS" else "RUMOR MILL", style = T.label.copy(fontSize = 10.sp), color = P.gold)
            ed.rumors.take(3).forEach { Bullet(it, P.gold) }
        }
    }
}

/** On the Recruit tab during the rounds: where the top uncommitted players are leaning, and portal whispers. */
@Composable
fun CrystalBallCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val cb = WeeklyDispatch.latestCrystal(lg) ?: return
    if (cb.picks.isEmpty()) return
    Section("Crystal ball · round ${cb.round}")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("WHERE THEY'RE LEANING", style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.weight(1f))
            AiBadge(cb.ai, cb.writer)
        }
        cb.picks.take(5).forEach { Bullet(it, P.accent) }
        if (cb.rumors.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("PORTAL WHISPERS", style = T.label.copy(fontSize = 10.sp), color = P.gold)
            cb.rumors.take(3).forEach { Bullet(it, P.gold) }
        }
    }
}

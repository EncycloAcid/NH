package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Storyline
import com.nh.hoopscoach.game.Storylines

/** Three dots for a storyline's three parts, and a heat color once it's gotten personal. */
@Composable
private fun StageDots(s: Storyline) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        val c = if (s.heat >= 4) P.bad else P.accent
        for (i in 0 until 3) {
            val m = Modifier.size(8.dp).clip(CircleShape)
            Box(when {
                i < s.stage -> m.background(c)
                i == s.stage && s.pending && !s.done -> m.border(1.5.dp, c, CircleShape)
                else -> m.background(P.surface2)
            })
        }
    }
}

private fun heatWord(s: Storyline) = when { s.heat >= 6 -> "Boiling"; s.heat >= 4 -> "Heated"; s.heat >= 2 -> "Simmering"; else -> "Calm" }

/** One part of a storyline, waiting on your answer. */
@Composable
fun StoryBeatCard(vm: GameViewModel, lg: League, s: Storyline) {
    val b = Storylines.beat(lg, s) ?: return
    Section(Storylines.title(s), action = "Let it go", onAction = { vm.act { Storylines.choose(it, s, 1, auto = true) } })
    Panel(color = mix(P.surface, if (s.heat >= 4) P.bad else P.accent, if (P.dark) 0.10f else 0.05f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.kicker, style = T.label.copy(fontSize = 10.sp), color = if (s.heat >= 4) P.bad else P.accent, modifier = Modifier.weight(1f))
            StageDots(s)
        }
        Text(b.head, style = T.title.copy(fontSize = 20.sp), color = P.ink, modifier = Modifier.padding(top = 6.dp))
        Text(b.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        if (s.log.isNotEmpty()) Text("So far: " + s.log.joinToString(" · ") { it.substringAfter(": ") }, style = T.small, color = P.sub,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(bottom = 4.dp))
        b.choices.forEachIndexed { i, ch ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = Storylines.choose(it, s, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(ch.text, style = T.body, color = P.ink)
                    Text(ch.hint, style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        }
        Text(if (s.stage < 2) "Part ${s.stage + 2} comes later this season. What you say now follows you there." else "How this ends is up to you.",
            style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** On Today: the season's storylines, where each one stands. */
@Composable
fun StorylinesPanel(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val all = lg.storylines.filter { it.season == lg.season }
    if (all.isEmpty()) return
    Section("This season's storylines")
    Panel(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        all.forEachIndexed { i, s ->
            if (i > 0) Hairline(Modifier.padding(start = 14.dp))
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Storylines.title(s), style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    Text(if (s.done) "OVER" else heatWord(s).uppercase(), style = T.label.copy(fontSize = 10.sp),
                        color = when { s.done -> P.faint; s.heat >= 4 -> P.bad; s.heat >= 2 -> P.warn; else -> P.sub })
                    Spacer(Modifier.width(8.dp))
                    StageDots(s)
                }
                val line = when {
                    s.pending -> "Waiting on you: ${Storylines.beat(lg, s)?.head ?: "the next part"}."
                    s.log.isEmpty() -> "Just getting started."
                    else -> s.log.last()
                }
                Text(line, style = T.small, color = if (s.pending) P.warn else P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

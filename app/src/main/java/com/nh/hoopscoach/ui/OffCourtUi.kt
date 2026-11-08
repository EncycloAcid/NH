package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Jerseys
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Shoes
import com.nh.hoopscoach.game.TvShow
import kotlin.math.roundToInt

/** This week's TV debate about you, with your answer. Shown for six days after it airs. */
@Composable
fun TvCard(vm: GameViewModel, lg: League) {
    val seg = lg.tv ?: return
    if (seg.season != lg.season || lg.day - seg.day > 6) return
    Section("On TV · ${seg.show}")
    Panel(color = P.hero) {
        Text("THIS WEEK'S DEBATE", style = T.label, color = heroKicker)
        Text(seg.topic, style = T.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(22f, androidx.compose.ui.unit.TextUnitType.Sp)), color = P.onHero)
        // Last week's take, checked, in a box of its own.
        if (seg.recap.isNotEmpty()) Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(P.onHero.copy(alpha = 0.1f)).padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text("LAST WEEK'S CALL", style = T.label.copy(fontSize = 10.sp), color = heroKicker)
            Text(seg.recap.removePrefix("Last week: "), style = T.small, color = P.onHero.copy(alpha = 0.92f))
        }
        Spacer(Modifier.height(10.dp))
        for (take in seg.takes) {
            Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
                RealFace(take.handle.ifEmpty { take.name }, take.name, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(take.name, style = T.bodyB, color = P.onHero, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(6.dp))
                        Tag(take.side.uppercase(), if (take.side == "Yes") P.good else P.bad, filled = true)
                        if (take.persona.isNotEmpty()) { Spacer(Modifier.width(4.dp)); Tag(take.persona.uppercase(), P.onHero) }
                    }
                    take.lines.forEachIndexed { i, line ->
                        // The last line answers the other side.
                        val rebuttal = i == take.lines.lastIndex && take.lines.size >= 3
                        Text((if (rebuttal) "↪ " else "") + "“$line”", style = T.small.copy(fontStyle = if (rebuttal) androidx.compose.ui.text.font.FontStyle.Italic else null),
                            color = P.onHero.copy(alpha = if (rebuttal) 0.75f else 0.9f), modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        // What the fans think.
        if (seg.poll in 0..100) {
            Text("FANS: ${seg.poll}% YES · ${100 - seg.poll}% NO", style = T.label, color = P.onHero.copy(alpha = 0.8f))
            Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.bad.copy(alpha = 0.6f))) {
                if (seg.poll > 0) androidx.compose.foundation.layout.Box(Modifier.weight(seg.poll.toFloat()).height(8.dp).background(P.good))
                if (seg.poll < 100) Spacer(Modifier.weight((100 - seg.poll).toFloat()))
            }
        }
        if (seg.answered < 0) {
            Text("Your response", style = T.label, color = P.onHero.copy(alpha = 0.75f))
            seg.responses.forEachIndexed { i, label ->
                Text(label, style = T.bodyB, color = P.onHero, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(12.dp))
                    .background(P.onHero.copy(alpha = 0.14f)).clickable { vm.act { TvShow.respond(it, i) } }.padding(horizontal = 14.dp, vertical = 11.dp))
            }
        } else Text("You said: “${seg.quotes.getOrNull(seg.answered) ?: seg.responses[seg.answered]}”", style = T.small, color = P.onHero.copy(alpha = 0.9f))
    }
}

/** The league's top-selling jerseys, with movement since the last list. */
@Composable
fun JerseySales(vm: GameViewModel, lg: League) {
    val top = Jerseys.top(lg)
    Section("Jersey sales" + if (lg.jerseySeason == lg.season) "" else " · ${lg.jerseySeason}")
    if (top.isEmpty()) { Panel { Text("The first list comes out at the All-Star break.", style = T.small, color = P.sub) }; return }
    GroupList(top, inset = 64.dp) { p ->
        val rank = lg.jerseyTop.indexOf(p.id) + 1
        val was = lg.jerseyLast.indexOf(p.id) + 1
        val move = when { was == 0 -> "NEW"; was > rank -> "▲${was - rank}"; was < rank -> "▼${rank - was}"; else -> "–" }
        val t = lg.teams.getOrNull(p.teamId)
        ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == lg.heroId || p.teamId == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier) {
            Text("$rank", style = T.numSm, color = P.ink, modifier = Modifier.width(26.dp))
            PlayerJersey(p, t, 36.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${t?.name ?: ""} · ${f1(p.stats.ppg)} ppg", style = T.small, color = P.sub, maxLines = 1)
            }
            Text(move, style = T.small, color = when { move.startsWith("▲") || move == "NEW" -> P.good; move.startsWith("▼") -> P.bad; else -> P.faint })
        }
    }
}

/** His shoe: the brand, this model, and every season's sales and pay. */
@Composable
fun ShoePanel(lg: League) {
    val r = lg.road ?: return
    val d = r.shoe
    Section("Signature shoe")
    Panel {
        if (d == null) {
            Text("No shoe deal yet", style = T.bodyB, color = P.ink)
            Text("Brands come calling at season's end once you're an All-Star or have a million followers.", style = T.small, color = P.sub)
            return@Panel
        }
        Text(Shoes.shoeName(lg, d).uppercase(), style = T.label, color = P.accent)
        Text("${d.brand} · ${d.start}–${d.ends}", style = T.head, color = P.ink)
        Text("${Road.dollars(d.perYear)} a year guaranteed · ${(d.royalty * 100).roundToInt()}% royalty", style = T.small, color = P.sub)
        if (d.sales.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            d.sales.forEachIndexed { i, pairs ->
                Text("Model ${i + 1}: ${Road.count(pairs)} pairs · ${Road.dollars(d.paid.getOrElse(i) { 0 })}", style = T.small, color = P.ink)
            }
        } else Text("The first model drops this season.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
    }
}

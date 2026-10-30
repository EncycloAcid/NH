package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Fans
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Legend
import com.nh.hoopscoach.game.Pipeline
import com.nh.hoopscoach.game.Team
import kotlin.math.abs
import kotlin.math.roundToInt

fun signedMoney(k: Int) = (if (k < 0) "−" else "+") + money(abs(k))

@Composable
fun fanColor(fans: Double): Color = when {
    fans >= 72 -> P.good
    fans >= 55 -> P.accent
    fans >= 40 -> P.warn
    else -> P.bad
}

/** Fan mood, crowds and what the fans add to the NIL pool. */
@Composable
fun FanPanel(t: Team) {
    val boost = Finance.fanMoney(t)
    Section("Fans")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("FAN MOOD", style = T.label, color = P.sub)
                Text(Fans.mood(t.fans), style = T.head, color = fanColor(t.fans))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(signedMoney(boost), style = T.numSm, color = if (boost >= 0) P.good else P.bad)
                Text("NIL FROM FANS", style = T.label.copy(fontSize = 9.sp), color = P.faint)
            }
        }
        Spacer(Modifier.height(10.dp))
        Bar((t.fans / 100).toFloat(), Modifier.fillMaxWidth(), color = fanColor(t.fans), height = 8.dp)
        Spacer(Modifier.height(10.dp))
        Text(
            if (t.homeGames == 0) "No home games yet this season. Arena: ${fans(t.arena)} seats."
            else "Averaging ${fans(Fans.avgCrowd(t))} a night (${(Fans.avgFill(t) * 100).roundToInt()}% of ${fans(t.arena)}) with ${t.sellouts} sellout${if (t.sellouts == 1) "" else "s"}.",
            style = T.small, color = P.ink,
        )
        Text("Wins fill the arena and the NIL pool. Losses empty both.", style = T.small, color = P.sub)
    }
}

/** Recent draft picks and the recruiting pull they bring. */
@Composable
fun PipelinePanel(vm: GameViewModel, lg: League, t: Team) {
    val picks = Pipeline.picks(lg, t.id).sortedWith(compareByDescending<com.nh.hoopscoach.game.DraftPick> { it.season }.thenBy { it.pick })
    val pts = picks.sumOf { Pipeline.value(it.pick) }
    Section("Pro pipeline")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${picks.size} pick${if (picks.size == 1) "" else "s"} in the last ${Pipeline.DRAFTS} drafts", style = T.bodyB, color = P.ink)
                Text(
                    if (pts == 0) "Every player you send to the draft helps recruiting for four years."
                    else "+${(Pipeline.bonus(pts, true) * 100).roundToInt()}% recruiting pull with 4- and 5-star recruits, +${(Pipeline.bonus(pts, false) * 100).roundToInt()}% with everyone else.",
                    style = T.small, color = P.sub,
                )
            }
        }
        if (picks.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            for (d in picks.take(6)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("#${d.pick}", style = T.numSm.copy(fontSize = 16.sp), color = if (d.pick <= 14) P.gold else P.accent, modifier = Modifier.width(44.dp))
                    Text(d.playerName, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${d.season} · ${if (d.pick <= 14) "Lottery" else if (d.pick <= 30) "1st round" else "2nd round"}", style = T.small, color = P.sub)
                }
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = vm
}

private val BannerShape = GenericShape { size, _ ->
    moveTo(0f, 0f)
    lineTo(size.width, 0f)
    lineTo(size.width, size.height)
    lineTo(size.width / 2, size.height * 0.86f)
    lineTo(0f, size.height)
    close()
}

/** A retired jersey hanging in the rafters. */
@Composable
fun RetiredBanner(t: Team, l: Legend) {
    val primary = Color(t.primary)
    val secondary = Color(t.secondary)
    val fg = if (contrast(secondary, primary) >= 2.2f) secondary else onColor(primary)
    Column(Modifier.width(86.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(P.faint))
        Box(Modifier.width(74.dp).height(136.dp).clip(BannerShape).background(primary), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 5.dp).height(2.dp).background(fg.copy(alpha = 0.5f)))
            Column(Modifier.padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(t.abbr, style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.85f), maxLines = 1)
                Text("${l.number}", style = T.hero.copy(fontSize = 46.sp, lineHeight = 46.sp), color = fg, maxLines = 1)
                Text(l.name.substringAfterLast(' ').uppercase(), style = T.label.copy(fontSize = 10.sp, letterSpacing = 0.5.sp), color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                Text(l.years, style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.85f))
            }
        }
    }
}

/** The program's legends: retired jerseys in the rafters, then the Hall of Fame. */
@Composable
fun ProgramLegends(t: Team) {
    val retired = t.legends.filter { it.retired }.sortedBy { it.inducted }
    if (retired.isNotEmpty()) {
        Section("In the rafters")
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            retired.forEach { RetiredBanner(t, it) }
        }
    }
    Section("Hall of Fame · ${t.legends.size}")
    if (t.legends.isEmpty()) {
        Panel {
            Text(
                "No legends yet. When a great player leaves, he's judged on his whole career here: awards, titles, scoring and the draft. The best go into the Hall of Fame, and the all-time greats get their jersey retired.",
                style = T.small, color = P.sub,
            )
        }
        return
    }
    GroupList(t.legends.sortedWith(compareByDescending<Legend> { it.retired }.thenByDescending { it.score }), inset = 66.dp) { l -> LegendRow(t, l) }
}

@Composable
fun LegendRow(t: Team, l: Legend) {
    ListRow {
        if (l.coach) CoachBadge(l.name.substringBefore(' '), l.name.substringAfterLast(' '), 42.dp)
        else Jersey(l.number, Color(t.primary), Color(t.secondary), 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(l.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                when {
                    l.retired -> Tag("NO. ${l.number} RETIRED", P.gold)
                    l.coach -> Tag("COACH", P.accent)
                }
            }
            Text("${l.years}${l.pos?.let { " · $it" } ?: ""} · ${l.line}", style = T.small, color = P.sub, maxLines = 2)
            if (l.honors.isNotEmpty()) Text(l.honors.joinToString(" · "), style = T.small, color = P.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Offseason ceremony for the user's program. */
@Composable
fun HonorsCard(t: Team, honors: List<Legend>) {
    if (honors.isEmpty()) return
    Section("Honoring legends")
    val retired = honors.filter { it.retired }
    if (retired.isNotEmpty()) {
        Panel(color = P.accent) {
            Text("JERSEY RETIREMENT", style = T.label, color = P.onAccent.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    for (l in retired) {
                        Text("NO. ${l.number} · ${l.name.uppercase()}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onAccent)
                        Text("${l.years} · ${l.honors.firstOrNull() ?: l.line}", style = T.small, color = P.onAccent.copy(alpha = 0.9f))
                        Spacer(Modifier.height(4.dp))
                    }
                    Text("No one at ${t.school} will wear ${if (retired.size == 1) "it" else "them"} again.", style = T.small, color = P.onAccent.copy(alpha = 0.85f))
                }
                Spacer(Modifier.width(10.dp))
                RetiredBanner(t, retired.first())
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    val inducted = honors.filter { !it.retired }
    if (inducted.isNotEmpty()) GroupList(inducted, inset = 66.dp) { l -> LegendRow(t, l) }
}

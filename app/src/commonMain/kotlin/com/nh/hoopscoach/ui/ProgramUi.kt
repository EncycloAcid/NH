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
                    Column(Modifier.weight(1f)) {
                        Text(d.playerName, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val nba = if (d.nba.isNotEmpty()) com.nh.hoopscoach.game.Nba.short(d.nba) else null
                        val shoe = com.nh.hoopscoach.game.Shoes.byId(d.shoe)?.short
                        Text(
                            listOfNotNull(nba, shoe, "${d.season}").joinToString(" · "),
                            style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(if (d.pick <= 14) "Lottery" else if (d.pick <= 30) "1st" else "2nd", style = T.small, color = P.sub)
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
fun ProgramLegends(t: Team, onOpen: ((Int) -> Unit)? = null) {
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
    GroupList(t.legends.sortedWith(compareByDescending<Legend> { it.retired }.thenByDescending { it.score }), inset = 66.dp) { l -> LegendRow(t, l, onOpen) }
}

@Composable
fun LegendRow(t: Team, l: Legend, onOpen: ((Int) -> Unit)? = null) {
    ListRow(onClick = if (onOpen != null && l.pid >= 0) ({ onOpen(l.pid) }) else null) {
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

/** Former players with their career on record, newest first. */
@Composable
fun FormerPlayers(vm: GameViewModel, lg: League, t: Team) {
    val list = com.nh.hoopscoach.game.Careers.formerPlayers(lg, t.id)
    if (list.isEmpty()) return
    Section("Former players · ${list.size}")
    GroupList(list.take(10), inset = 70.dp) { p ->
        val tot = com.nh.hoopscoach.game.Careers.total(p)
        val seasons = p.career.filter { it.teamId == t.id || it.team == t.abbr }.map { it.season }
        val years = if (seasons.isEmpty()) "" else if (seasons.min() == seasons.max()) "${seasons.min()}" else "${seasons.min()}-${seasons.max() % 100}"
        ListRow(onClick = { vm.openPlayer(p.id) }) {
            Jersey(p.number, Color(t.primary), Color(t.secondary), 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOf(years, "${p.posLabel}", p.left).filter { it.isNotEmpty() }.joinToString(" · "), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(f1(tot.ppg), style = T.numSm, color = P.ink)
                Text("PPG", style = T.label.copy(fontSize = 8.sp), color = P.faint)
            }
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
        Panel(color = P.hero) {
            Text("JERSEY RETIREMENT", style = T.label, color = P.onHero.copy(alpha = 0.8f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    for (l in retired) {
                        Text("NO. ${l.number} · ${l.name.uppercase()}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onHero)
                        Text("${l.years} · ${l.honors.firstOrNull() ?: l.line}", style = T.small, color = P.onHero.copy(alpha = 0.9f))
                        Spacer(Modifier.height(4.dp))
                    }
                    Text("No one at ${t.school} will wear ${if (retired.size == 1) "it" else "them"} again.", style = T.small, color = P.onHero.copy(alpha = 0.85f))
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

/** Prestige and the numbers around it: Legacy, Buzz, Program Pull, Conference Exposure, Expected Wins and the resume ceiling. */
@Composable
fun ProgramNumbers(vm: GameViewModel, lg: League, t: Team) {
    val pull = com.nh.hoopscoach.game.Brand.pull(lg, t)
    val exposure = com.nh.hoopscoach.game.Brand.exposure(lg, t.confId)
    val ceiling = com.nh.hoopscoach.game.Brand.ceiling(t)
    Section("Program", action = "Guide", onAction = { vm.openGuide(Guide.PROGRAM, 4) })
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PRESTIGE", style = T.label, color = P.sub)
                Text(prestigeLabel(t.prestige), style = T.head, color = P.accent)
                val d = t.prestige - t.prevPrestige
                Text(
                    when {
                        t.history.isEmpty() -> "Moves once a season, at the end of it."
                        d == 0 -> "No change last season. Moves once a season."
                        else -> "${if (d > 0) "+" else "−"}${abs(d)} last season. Moves once a season."
                    },
                    style = T.small, color = P.sub,
                )
            }
            Ring(t.prestige, 58.dp, textSize = 22.sp)
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth()) {
            Stat("Legacy", "${t.legacy.roundToInt()}", Modifier.weight(1f), style = T.numSm)
            Stat("Buzz", "${t.buzz.roundToInt()}", Modifier.weight(1f), if (t.buzz >= 40) P.good else P.ink, T.numSm)
            Stat("Pull", "$pull", Modifier.weight(1f), P.accent, T.numSm)
            Stat("Exposure", "$exposure", Modifier.weight(1f), style = T.numSm)
        }
        Spacer(Modifier.height(12.dp))
        BrandLine("Buzz", "${com.nh.hoopscoach.game.Brand.buzzLabel(t.buzz)}: short-term heat from big wins and March. It cools fast.")
        BrandLine("Program Pull", "What recruits, coaches and players see. Recruits call it Recruiting Pull.")
        BrandLine("Conference", "${com.nh.hoopscoach.game.Brand.exposureLabel(exposure)} exposure in the ${lg.conf(t.confId).abbr}.")
        BrandLine("Resume ceiling", "${ceiling.roundToInt()} · ${com.nh.hoopscoach.game.Brand.ceilingLabel(ceiling)}. Above 70, Prestige won't stick far past this without more March wins.")
        // The season in progress: where Prestige is heading, and what March would add.
        if (!lg.pro && t.gp >= 5 && (lg.phase == com.nh.hoopscoach.game.Phase.REGULAR || lg.phase == com.nh.hoopscoach.game.Phase.CONF_TOURNEY)) {
            val (now, d) = com.nh.hoopscoach.game.Brand.outlook(lg, t, 0)
            val (_, s16) = com.nh.hoopscoach.game.Brand.outlook(lg, t, 3)
            val streak = com.nh.hoopscoach.game.Brand.streak(t)
            // Where the record is heading: wins so far, plus each game left at today's odds.
            val left = lg.games.filter { !it.played && it.involves(t.id) && (it.type == com.nh.hoopscoach.game.GameType.CONF || it.type == com.nh.hoopscoach.game.GameType.NONCONF || it.type == com.nh.hoopscoach.game.GameType.EVENT) }
            val projW = t.w + left.sumOf { com.nh.hoopscoach.game.Rankings.winProbFor(lg, it, t.id) }.roundToInt()
            val projL = t.w + t.l + left.size - projW
            BrandLine("Projected", com.nh.hoopscoach.game.Takes.collegePulse(lg, t, projW, projL)
                ?: "$projW-$projL in the regular season: your record so far, plus every game left at today's odds. It moves with every result.")
            BrandLine(
                "This season",
                "On pace to earn $now, for ${if (d >= 0) "+" else "−"}${abs(d)} Prestige. A Sweet 16 run would make it ${if (s16 >= 0) "+" else "−"}${abs(s16)}." +
                    if (streak > 0) " $streak rising season${if (streak == 1) "" else "s"} in a row: the climb is faster." else "",
            )
        }
        if (t.expWins > 0) BrandLine("Expected wins", "${t.expWins.roundToInt()}, the preseason target from this schedule. ${if (t.gp > 0) "${t.w} so far." else ""}")
    }
}

@Composable
private fun BrandLine(label: String, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = T.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = P.ink, modifier = Modifier.width(112.dp))
        Text(text, style = T.small, color = P.sub, modifier = Modifier.weight(1f))
    }
}

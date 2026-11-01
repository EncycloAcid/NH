package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Rivalries
import com.nh.hoopscoach.game.Rivalry
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Team
import java.util.Locale

fun fans(n: Int): String = String.format(Locale.US, "%,d", n)

/** "Duke leads 3-1", "Series tied 2-2" or "First meeting". */
fun seriesLine(lg: League, r: Rivalry): String {
    val a = lg.team(r.a)
    val b = lg.team(r.b)
    return when {
        r.aWins + r.bWins == 0 -> "First meeting of the dynasty"
        r.aWins == r.bWins -> "Series tied ${r.aWins}-${r.bWins}"
        r.aWins > r.bWins -> "${a.school} leads ${r.aWins}-${r.bWins}"
        else -> "${b.school} leads ${r.bWins}-${r.aWins}"
    }
}

/** Red "RIVALRY" ribbon with the rivalry's name. */
@Composable
fun RivalryRibbon(r: Rivalry) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Tag("RIVALRY", P.bad, filled = true)
        if (r.name.isNotEmpty()) {
            Spacer(Modifier.width(8.dp))
            Text(r.name.uppercase(), style = T.label, color = P.bad, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Pregame note for a rivalry game: the crowd and what it is worth. */
@Composable
fun RivalryPreview(lg: League, g: Game, r: Rivalry) {
    val crowd = if (g.att > 0) g.att else Rivalries.attendance(lg, g)
    val user = lg.userTeamId
    val edge = when {
        g.neutral -> "Neutral floor, split crowd."
        g.home == user -> "Your crowd is worth +${Rivalries.HOME_BOOST.toInt()} more Elo tonight."
        else -> "Their crowd is worth +${Rivalries.HOME_BOOST.toInt()} more Elo tonight."
    }
    RivalryRibbon(r)
    Spacer(Modifier.height(6.dp))
    Text("Sellout crowd of ${fans(crowd)}. $edge", style = T.small, color = P.ink)
    Text("${seriesLine(lg, r)} · Winner takes prestige from the loser.", style = T.small, color = P.sub)
}

/** Crowd line for live games and box scores. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrowdLine(lg: League, g: Game, modifier: Modifier = Modifier) {
    if (g.att <= 0) return
    val r = lg.rivalry(g)
    // Ribbon and crowd share a line when they fit; on narrow screens the crowd drops below.
    FlowRow(
        modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (r != null) Box(Modifier.padding(end = 10.dp)) { RivalryRibbon(r) }
        Text("${fans(g.att)} fans" + if (Rivalries.sellout(lg, g)) " · sellout" else "", style = T.small, color = P.sub, maxLines = 1)
    }
}

/**
 * Front page of the (fictional) Hoops Dispatch after a rivalry game: nameplate, kicker, headline,
 * deck, a two-line score and the prestige swing. Type sizes adapt so nothing is cut off on narrow
 * phones or with large system text.
 */
@Composable
fun NewspaperBanner(vm: GameViewModel, lg: League, g: Game, onClick: (() -> Unit)? = { vm.push(Route.Box(g.id)) }) {
    val r = lg.rivalry(g) ?: return
    if (!g.played) return
    val dark = P.dark
    val paper = if (dark) Color(0xFF221F1A) else Color(0xFFF6F1E5)
    val ink = if (dark) Color(0xFFEFE8D8) else Color(0xFF1B1915)
    val faded = ink.copy(alpha = if (dark) 0.62f else 0.58f)
    val red = if (dark) Color(0xFFF0806F) else Color(0xFFB3261E)
    val green = if (dark) Color(0xFF7BD389) else Color(0xFF2E7D32)
    val serif = FontFamily.Serif
    val w = lg.team(g.winner())
    val l = lg.team(g.loser())
    val home = lg.team(g.home)
    val ot = if (g.ot > 0) " (${if (g.ot > 1) "${g.ot}" else ""}OT)" else ""
    val where = if (g.neutral) "on a neutral floor" else "at ${home.school}"
    val crowd = if (g.att > 0) " before a sellout crowd of ${fans(g.att)} $where" else ""
    val dateline = if (g.day <= Schedule.REG_DAYS) "Game ${g.day}" else g.label.ifEmpty { "Postseason" }
    val small = TextStyle(fontFamily = serif, fontSize = 11.sp, lineHeight = 14.sp)
    val base = Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(paper)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 14.dp)) {
        // Edition line, then the nameplate across the full width.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EXTRA EDITION", style = small.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp), color = red, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            Text("${lg.season} · $dateline", style = small.copy(fontStyle = FontStyle.Italic), color = faded, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(2.dp))
        // Your school's own paper.
        FitText("The ${lg.user.school} Daily", TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, textAlign = TextAlign.Center), ink, 32f, 15f)
        Spacer(Modifier.height(4.dp))
        Rule(ink, 2.dp)
        Spacer(Modifier.height(2.dp))
        Rule(ink, 0.75.dp)
        Spacer(Modifier.height(5.dp))
        Text("SPORTS  ·  FINAL", style = small.copy(fontSize = 10.sp, letterSpacing = 2.sp), color = faded, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Text(r.title.uppercase(), style = small.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp), color = red, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(2.dp))
        Headline(Rivalries.headline(lg, g).uppercase(), TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, letterSpacing = (-0.3).sp), ink)
        Spacer(Modifier.height(8.dp))
        Text(
            "${w.school} ${g.scoreFor(w.id)}, ${l.school} ${g.scoreFor(l.id)}$ot$crowd.",
            style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp),
            color = ink.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        Rule(ink.copy(alpha = 0.4f), 0.75.dp)
        // A two-line score, winner on top, so long names and the prestige swing always fit.
        PaperLine(w, g.scoreFor(w.id), g.swing, true, ink, green, serif)
        Rule(ink.copy(alpha = 0.15f), 0.75.dp)
        PaperLine(l, g.scoreFor(l.id), -g.swing, false, ink, red, serif)
        Rule(ink.copy(alpha = 0.4f), 0.75.dp)
        // Top performers: each side's best line, yours marked.
        g.box?.let { box ->
            val tops = listOf(g.home to box.home, g.away to box.away).mapNotNull { (tid, lines) ->
                lines.maxByOrNull { it.s.pts + 0.7 * (it.s.orb + it.s.drb) + 0.9 * it.s.ast }?.let { tid to it }
            }
            if (tops.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("TOP PERFORMERS", style = small.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp), color = faded)
                for ((tid, pl) in tops) {
                    val me = pl.pid == lg.heroId
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(lg.team(tid).abbr, style = small.copy(fontWeight = FontWeight.Bold, fontSize = 11.sp), color = faded, modifier = Modifier.width(44.dp), maxLines = 1)
                        Text(pl.name + if (me) " (you)" else "", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 14.sp),
                            color = if (me) red else ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${pl.s.pts} PTS · ${pl.s.orb + pl.s.drb} REB · ${pl.s.ast} AST", style = small.copy(fontSize = 11.sp), color = ink, maxLines = 1)
                    }
                }
                Rule(ink.copy(alpha = 0.4f), 0.75.dp)
            }
        }
        Spacer(Modifier.height(8.dp))
        // The written story when there is one, or the box score.
        val story = lg.news.indexOfLast { it.gameId == g.id && it.body.isNotEmpty() }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${seriesLine(lg, r)} in your dynasty", style = small.copy(fontSize = 12.sp, lineHeight = 16.sp), color = faded, modifier = Modifier.weight(1f))
            if (onClick != null) {
                Spacer(Modifier.width(8.dp))
                Text(if (story >= 0) "Read the story ›" else "Box score ›", style = small.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp), color = ink, maxLines = 1,
                    modifier = if (story >= 0) Modifier.clickable { vm.push(Route.Article(story)) } else Modifier)
            }
        }
    }
}

@Composable
private fun Rule(color: Color, thickness: Dp) = Box(Modifier.fillMaxWidth().height(thickness).background(color))

@Composable
private fun PaperLine(t: Team, score: Int, swing: Int, winner: Boolean, ink: Color, swingColor: Color, serif: FontFamily) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.school, style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 19.sp),
                color = if (winner) ink else ink.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (swing != 0) Text(
                "${if (swing > 0) "+" else "−"}${kotlin.math.abs(swing)} prestige",
                style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 11.sp, lineHeight = 13.sp), color = swingColor, maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text("$score", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, fontSize = 28.sp, lineHeight = 30.sp),
            color = if (winner) ink else ink.copy(alpha = 0.6f), maxLines = 1)
    }
}

/** One line of text at the largest size (between [min] and [max] sp) that fits the width. */
@Composable
private fun FitText(text: String, style: TextStyle, color: Color, max: Float, min: Float) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val maxW = constraints.maxWidth
        val size = remember(text, maxW, style) {
            var s = max
            while (s > min && measurer.measure(text, style.copy(fontSize = s.sp), maxLines = 1, softWrap = false).size.width > maxW) s -= 1f
            s
        }
        Text(text, style = style.copy(fontSize = size.sp, lineHeight = (size * 1.15f).sp), color = color, maxLines = 1, modifier = Modifier.fillMaxWidth())
    }
}

/** A headline as big as it can be while every word fits on a line and it runs four lines at most. */
@Composable
private fun Headline(text: String, style: TextStyle, color: Color) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val measurer = rememberTextMeasurer()
        val maxW = constraints.maxWidth
        val size = remember(text, maxW, style) {
            val words = text.split(' ').filter { it.isNotEmpty() }
            fun fits(s: Float): Boolean {
                val st = style.copy(fontSize = s.sp, lineHeight = (s * 1.08f).sp)
                if (words.any { measurer.measure(it, st, maxLines = 1, softWrap = false).size.width > maxW }) return false
                return measurer.measure(text, st, constraints = Constraints(maxWidth = maxW)).lineCount <= 4
            }
            var s = 27f
            while (s > 17f && !fits(s)) s -= 1f
            s
        }
        Text(text, style = style.copy(fontSize = size.sp, lineHeight = (size * 1.08f).sp), color = color)
    }
}

/** A program's rivals with the series record and the next meeting. */
@Composable
fun RivalsList(vm: GameViewModel, lg: League, t: Team) {
    val rivals = lg.rivalsOf(t.id)
    if (rivals.isEmpty()) return
    Section("Rivals")
    GroupList(rivals) { r ->
        val o = lg.team(r.other(t.id))
        val next = lg.games.filter { !it.played && it.involves(t.id) && it.involves(o.id) }.minByOrNull { it.day }
        val w = r.winsFor(t.id)
        val l = r.winsFor(o.id)
        ListRow(onClick = { vm.push(Route.TeamPage(o.id)) }) {
            TeamBadge(o, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(o.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val when_ = next?.let { if (it.day <= Schedule.REG_DAYS) "Next: game ${it.day}" else "Next: ${it.label.substringAfter("· ")}" } ?: "No meeting left this season"
                Text("${r.title} · $when_", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$w-$l", style = T.numSm, color = when { w > l -> P.good; w < l -> P.bad; else -> P.ink })
                Text("SERIES", style = T.label.copy(fontSize = 8.sp), color = P.faint)
            }
        }
    }
}

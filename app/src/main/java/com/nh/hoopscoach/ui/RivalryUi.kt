package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
@Composable
fun CrowdLine(lg: League, g: Game, modifier: Modifier = Modifier) {
    if (g.att <= 0) return
    val r = lg.rivalry(g)
    Row(modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (r != null) { RivalryRibbon(r); Spacer(Modifier.width(10.dp)) }
        Text(
            "${fans(g.att)} fans" + if (Rivalries.sellout(lg, g)) " · sellout" else "",
            style = T.small, color = P.sub, modifier = Modifier.weight(1f), textAlign = TextAlign.End, maxLines = 1,
        )
    }
}

/**
 * Front page of the (fictional) Hoops Dispatch after a rivalry game: kicker, headline, deck,
 * score and the prestige swing.
 */
@Composable
fun NewspaperBanner(vm: GameViewModel, lg: League, g: Game, onClick: (() -> Unit)? = { vm.push(Route.Box(g.id)) }) {
    val r = lg.rivalry(g) ?: return
    if (!g.played) return
    val dark = P.dark
    val paper = if (dark) Color(0xFF24211C) else Color(0xFFF6F1E5)
    val ink = if (dark) Color(0xFFEFE8D8) else Color(0xFF1B1915)
    val faded = ink.copy(alpha = 0.6f)
    val red = if (dark) Color(0xFFF0806F) else Color(0xFFB3261E)
    val serif = FontFamily.Serif
    val w = lg.team(g.winner())
    val l = lg.team(g.loser())
    val home = lg.team(g.home)
    val ot = if (g.ot > 0) " (${if (g.ot > 1) "${g.ot}" else ""}OT)" else ""
    val where = if (g.neutral) "on a neutral floor" else "at ${home.school}"
    val crowd = if (g.att > 0) " before a sellout crowd of ${fans(g.att)} $where" else ""
    val dateline = if (g.day <= Schedule.REG_DAYS) "Game ${g.day}" else g.label.ifEmpty { "Postseason" }
    val base = Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(paper)
    Column((if (onClick != null) base.clickable(onClick = onClick) else base).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EXTRA", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.5.sp), color = red, modifier = Modifier.width(52.dp))
            Text("The Hoops Dispatch", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, fontSize = 22.sp), color = ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
            Text("${lg.season}", style = TextStyle(fontFamily = serif, fontSize = 11.sp), color = faded, textAlign = TextAlign.End, modifier = Modifier.width(52.dp))
        }
        Spacer(Modifier.height(6.dp))
        Rule(ink, 2.dp)
        Spacer(Modifier.height(2.dp))
        Rule(ink, 0.75.dp)
        Spacer(Modifier.height(4.dp))
        Row {
            Text(dateline, style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 11.sp), color = faded, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Sports · Final", style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 11.sp), color = faded)
        }
        Spacer(Modifier.height(12.dp))
        Text(r.title.uppercase(), style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp), color = red)
        Spacer(Modifier.height(2.dp))
        Text(
            Rivalries.headline(lg, g).uppercase(),
            style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, fontSize = 27.sp, lineHeight = 29.sp, letterSpacing = (-0.3).sp),
            color = ink,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "${w.school} ${g.scoreFor(w.id)}, ${l.school} ${g.scoreFor(l.id)}$ot$crowd.",
            style = TextStyle(fontFamily = serif, fontStyle = FontStyle.Italic, fontSize = 15.sp, lineHeight = 20.sp),
            color = ink.copy(alpha = 0.85f),
        )
        Spacer(Modifier.height(12.dp))
        Rule(ink.copy(alpha = 0.4f), 0.75.dp)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            PaperSide(w, g.scoreFor(w.id), "+${g.swing}", Modifier.weight(1f), ink, Color(0xFF2E7D32).let { if (dark) Color(0xFF7BD389) else it }, serif)
            Box(Modifier.width(1.dp).height(34.dp).background(ink.copy(alpha = 0.3f)))
            Spacer(Modifier.width(12.dp))
            PaperSide(l, g.scoreFor(l.id), "−${g.swing}", Modifier.weight(1f), ink.copy(alpha = 0.7f), red, serif)
        }
        Spacer(Modifier.height(8.dp))
        Text("${seriesLine(lg, r)} in your dynasty", style = TextStyle(fontFamily = serif, fontSize = 12.sp), color = faded)
    }
}

@Composable
private fun Rule(color: Color, thickness: Dp) = Box(Modifier.fillMaxWidth().height(thickness).background(color))

@Composable
private fun PaperSide(t: Team, score: Int, swing: String, modifier: Modifier, ink: Color, swingColor: Color, serif: FontFamily) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 30.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(t.abbr, style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 13.sp), color = ink)
            Text("$swing prestige", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Bold, fontSize = 11.sp), color = swingColor)
        }
        Text("$score", style = TextStyle(fontFamily = serif, fontWeight = FontWeight.Black, fontSize = 26.sp), color = ink, modifier = Modifier.padding(end = 12.dp))
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

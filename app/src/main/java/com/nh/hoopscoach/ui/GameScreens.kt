package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Defense
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offense
import com.nh.hoopscoach.game.PlayerLine
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.delay

private fun clock(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

@Composable
fun LiveScreen(vm: GameViewModel, lg: League) {
    val lv = vm.live ?: run { Column { BackHeader("Game", onBack = { vm.back() }) }; return }
    val e = lv.engine
    @Suppress("UNUSED_VARIABLE") val tick = lv.tick
    LaunchedEffect(lv) {
        while (!e.finished) {
            if (lv.speed > 0) {
                e.step()
                lv.tick++
            }
            delay(when (lv.speed) { 0 -> 150L; 1 -> 650L; 2 -> 260L; else -> 70L })
        }
        vm.completeLive()
    }
    var view by remember { mutableIntStateOf(0) }
    val home = e.teams[0]
    val away = e.teams[1]
    val userSide = if (home.id == lg.userTeamId) 0 else 1
    Column(Modifier.fillMaxSize()) {
        BackHeader(e.game.label.ifEmpty { if (e.game.type == com.nh.hoopscoach.game.GameType.CONF) "Conference Game" else "Non-Conference" }, onBack = { vm.back() })
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = PaddingValues(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreSide(away, e.score[1], Modifier.weight(1f), lg.userTeamId)
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(96.dp)) {
                    if (e.finished) Text("FINAL", color = C.orangeText, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    else {
                        Text(clock(e.clock.toInt().coerceAtLeast(0)), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        Text(e.periodLabel, color = C.sub, fontSize = 13.sp)
                    }
                }
                ScoreSide(home, e.score[0], Modifier.weight(1f), lg.userTeamId)
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                val done = e.periodScores.dropLast(1)
                val cur = intArrayOf(e.score[0] - done.sumOf { it[0] }, e.score[1] - done.sumOf { it[1] })
                val all = done + listOf(cur)
                Text(all.withIndex().joinToString("   ·   ") { (i, s) -> "${if (i == 0) "1st" else if (i == 1) "2nd" else if (i == 2) "OT" else "${i - 1}OT"} ${s[1]}-${s[0]}" }, color = C.sub, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (!lv.done) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                SpeedChip(if (lv.speed == 0) "▶ Resume" else "❚❚ Pause", lv.speed == 0) { lv.speed = if (lv.speed == 0) 2 else 0 }
                SpeedChip("1x", lv.speed == 1) { lv.speed = 1 }
                SpeedChip("2x", lv.speed == 2) { lv.speed = 2 }
                SpeedChip("4x", lv.speed == 3) { lv.speed = 3 }
                Spacer(Modifier.weight(1f))
                SmallButton("Sim to End", primary = true) { vm.completeLive() }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val t = e.teams[userSide]
                Column(Modifier.weight(1f)) { Picker(t.plan.offense.label, Offense.entries.map { it.label to it.blurb }) { i -> t.plan.offense = Offense.entries[i]; lv.tick++ } }
                Column(Modifier.weight(1f)) { Picker(t.plan.defense.label, Defense.entries.map { it.label to it.blurb }) { i -> t.plan.defense = Defense.entries[i]; lv.tick++ } }
            }
        } else {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val won = e.score[userSide] > e.score[1 - userSide]
                BigButton(if (won) "Win! Continue" else "Continue", Modifier.weight(1f)) { vm.closeLive() }
            }
        }
        Spacer(Modifier.height(10.dp))
        Segmented(listOf("Play-by-Play", away.abbr, home.abbr), view, { view = it })
        Spacer(Modifier.height(6.dp))
        if (view == 0) {
            val events = e.events.asReversed()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(events.size) { i ->
                    val ev = events[i]
                    val color = when (ev.side) { 0 -> Color(home.primary); 1 -> Color(away.primary); else -> C.dim }
                    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${clock(ev.clock)}", color = C.dim, fontSize = 13.sp, modifier = Modifier.width(44.dp))
                        Box(Modifier.size(width = 4.dp, height = 22.dp).clip(RoundedCornerShape(2.dp)).background(color))
                        Spacer(Modifier.width(10.dp))
                        Text(ev.text, fontSize = 15.sp, color = if (ev.points > 0) C.text else C.sub, fontWeight = if (ev.points > 0) FontWeight.Medium else FontWeight.Normal, modifier = Modifier.weight(1f))
                        if (ev.points > 0) { Spacer(Modifier.width(10.dp)); Text("${ev.aws}-${ev.hs}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = C.text) }
                    }
                }
            }
        } else {
            val box = e.boxScore()
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                item { BoxTable(vm, if (view == 1) box.away else box.home) }
            }
        }
    }
}

@Composable
private fun ScoreSide(t: Team, score: Int, modifier: Modifier, user: Int) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(t, 44.dp)
        Spacer(Modifier.height(4.dp))
        Text(t.abbr, color = if (t.id == user) C.orangeText else C.sub, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Text("$score", fontSize = 34.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeedChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(10.dp)).background(if (selected) C.orangeDim else C.card).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) { Text(label, color = if (selected) C.orangeText else C.sub, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
}

@Composable
fun BoxTable(vm: GameViewModel, lines: List<PlayerLine>) {
    val cols = listOf("MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG", "3PT", "FT", "PF")
    fun values(s: StatLine) = listOf(
        "${(s.sec / 60.0).let { kotlin.math.round(it).toInt() }}", "${s.pts}", "${s.reb}", "${s.ast}", "${s.stl}", "${s.blk}", "${s.tov}",
        "${s.fgm}-${s.fga}", "${s.tpm}-${s.tpa}", "${s.ftm}-${s.fta}", "${s.pf}",
    )
    val widths = listOf(40, 40, 40, 40, 40, 40, 36, 54, 50, 50, 34)
    val total = StatLine()
    lines.forEach { total.add(it.s) }
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(132.dp).padding(start = Gutter)) {
            Text("PLAYER", style = HoopsType.labelSmall, color = C.sub, modifier = Modifier.height(30.dp).padding(top = 8.dp))
            lines.forEach { l ->
                Row(Modifier.height(40.dp).clickable { vm.openPlayer(l.pid) }, verticalAlignment = Alignment.CenterVertically) {
                    Text(l.name, fontSize = 14.sp, fontWeight = if (l.starter) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(" ${l.pos}", color = C.dim, fontSize = 11.sp)
                }
            }
            Text("TOTAL", fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.height(40.dp).padding(top = 11.dp))
        }
        Column(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(end = Gutter)) {
            Row(Modifier.height(30.dp), verticalAlignment = Alignment.CenterVertically) {
                cols.forEachIndexed { i, c -> Text(c, style = HoopsType.labelSmall, color = C.sub, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End) }
            }
            lines.forEach { l ->
                Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                    values(l.s).forEachIndexed { i, v -> Text(v, fontSize = 14.sp, color = if (i == 1) C.text else C.sub, fontWeight = if (i == 1) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End) }
                }
            }
            Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                values(total).forEachIndexed { i, v -> Text(if (i == 0) "" else v, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(widths[i].dp), textAlign = TextAlign.End) }
            }
        }
    }
}

@Composable
fun BoxScoreScreen(vm: GameViewModel, lg: League, gameId: Int) {
    val g: Game = lg.games.firstOrNull { it.id == gameId } ?: run { Column { BackHeader("Box Score", onBack = { vm.back() }); EmptyNote("This game is no longer available.") }; return }
    val home = lg.team(g.home)
    val away = lg.team(g.away)
    var side by remember(gameId) { mutableIntStateOf(if (g.away == lg.userTeamId) 0 else 1) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackHeader("Box Score", g.label.ifEmpty { if (g.day <= com.nh.hoopscoach.game.Schedule.REG_DAYS) "Game ${g.day} · ${lg.season}" else "" }, onBack = { vm.back() })
            Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamTap(vm, away, Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (g.played) "${g.aws} - ${g.hs}" else "vs", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text(if (g.played) "Final${if (g.ot > 0) " (${if (g.ot > 1) "${g.ot}" else ""}OT)" else ""}" else "Day ${g.day}", color = C.sub, fontSize = 13.sp)
                    }
                    TeamTap(vm, home, Modifier.weight(1f))
                }
                val b = g.box
                if (b != null) {
                    Spacer(Modifier.height(12.dp))
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    PeriodRow("", b.halves.indices.map { if (it == 0) "1st" else if (it == 1) "2nd" else if (it == 2) "OT" else "${it - 1}OT" } + "T", C.sub)
                    PeriodRow(away.abbr, b.halves.map { "${it[1]}" } + "${g.aws}", C.text)
                    PeriodRow(home.abbr, b.halves.map { "${it[0]}" } + "${g.hs}", C.text)
                }
            }
            Spacer(Modifier.height(14.dp))
        }
        val b = g.box
        if (b == null) {
            item { EmptyNote(if (g.played) "Box scores are kept for your games and the tournaments." else "This game hasn't been played yet.") }
        } else {
            item {
                Segmented(listOf(away.school, home.school), side, { side = it })
                Spacer(Modifier.height(8.dp))
                BoxTable(vm, if (side == 0) b.away else b.home)
                Spacer(Modifier.height(30.dp))
            }
        }
    }
}

@Composable
private fun PeriodRow(label: String, values: List<String>, color: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = color, fontSize = 14.sp, modifier = Modifier.width(70.dp), fontWeight = FontWeight.Medium)
        values.forEachIndexed { i, v ->
            Text(v, color = if (i == values.lastIndex) C.text else color, fontSize = 14.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center,
                fontWeight = if (i == values.lastIndex) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
fun TeamTap(vm: GameViewModel, t: Team, modifier: Modifier) {
    Column(modifier.clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogo(t, 46.dp)
        Spacer(Modifier.height(6.dp))
        Text(t.school, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(t.record, fontSize = 13.sp, color = C.sub)
    }
}

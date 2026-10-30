package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Mood
import com.nh.hoopscoach.game.Morale
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Trait
import com.nh.hoopscoach.game.Tryouts
import com.nh.hoopscoach.game.classLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: GameViewModel, lg: League, p: Player) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null }, sheetState = state, containerColor = P.bg,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 4.dp).size(40.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(P.line)) },
    ) { PlayerCard(vm, lg, p, Modifier.fillMaxHeight(0.94f)) }
}

private fun traitBlurb(t: Trait) = when (t) {
    Trait.AMBITIOUS -> "Wants a big role and gets restless on the bench."
    Trait.BAG_CHASER -> "Cares a lot about his NIL deal."
    Trait.DISLOYAL -> "Quick to look at the transfer portal."
    Trait.LOYAL -> "Sticks with the program through ups and downs."
    Trait.TEAM_PLAYER -> "Accepts whatever role helps the team."
    Trait.GYM_RAT -> "Lives in the gym. Develops faster."
    Trait.CLUTCH -> "Shoots better with the game on the line."
    Trait.LATE_BLOOMER -> "A walk-on whose game suddenly took off. Develops faster."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerCard(vm: GameViewModel, lg: League, p: Player, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val team = if (p.teamId >= 0) lg.team(p.teamId) else null
    val mine = team?.id == lg.userTeamId
    var tab by remember(p.id) { mutableIntStateOf(0) }
    var editNil by remember { mutableStateOf(false) }
    val board = remember(p.id, vm.rev) { Offseason.draftBoard(lg) }
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, team, 72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.first.uppercase(), style = T.label, color = P.sub)
                    Text(p.last.uppercase(), style = T.title, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.pos} · ${p.cls} · ${Ratings.heightLabel(p.height)} · ${p.weight} lbs", style = T.small, color = P.sub)
                    if (team != null) Text("${team.school} · ${Gameplan.role(team, p.id)}", style = T.small, color = P.accent)
                }
            }
            Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Ring(p.ovr, 58.dp, label = "OVR", textSize = 21.sp)
                    Ring(p.pot, 58.dp, label = "POT", textSize = 21.sp)
                    Ring(Ratings.offense(p), 50.dp, label = "OFF")
                    Ring(Ratings.defense(p), 50.dp, label = "DEF")
                }
            }
            Spacer(Modifier.height(10.dp))
            FlowRow(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(p.style.label.uppercase(), P.accent)
                Tag("DRAFT: ${Offseason.projection(board[p.id], p.year).uppercase()}", P.sub)
                if (team != null) {
                    val m = Morale.mood(p)
                    Tag(m.label.uppercase(), when (m) { Mood.HAPPY -> P.good; Mood.CONTENT -> P.sub; Mood.UNHAPPY -> P.warn; Mood.TRANSFER -> P.bad })
                }
                if (p.walkOn) Tag("WALK-ON", P.sub)
                p.traits.forEach { Tag(it.label.uppercase(), traitColor(it)) }
                if (p.favorite) Tag("★ FAVORITE", P.gold)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine) SmallButton("NIL ${money(p.nil)}") { editNil = true }
                if (mine) SmallButton("Training focus") { vm.sheet = null; vm.push(Route.Focus(p.id)) }
                SmallButton(if (p.favorite) "Unstar" else "Star") { vm.act { p.favorite = !p.favorite } }
            }
            if (mine && p.walkOn && team != null) {
                val open = Tryouts.openScholarships(lg, team)
                Spacer(Modifier.height(10.dp))
                Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Walk-on", style = T.bodyB, color = P.ink)
                            Text(if (open > 0) "A scholarship is open. Awarding it makes him happier and often loyal." else "All 13 scholarships are taken. One opens when a scholarship player leaves.", style = T.small, color = P.sub)
                        }
                        Spacer(Modifier.width(10.dp))
                        SmallButton("Award scholarship", primary = true, enabled = open > 0) { vm.act { Tryouts.awardScholarship(it, p.id) } }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Segmented(listOf("Ratings", "Stats", "Growth", "Bio"), tab, { tab = it })
        }
        item {
            when (tab) {
                0 -> RatingsTab(p)
                1 -> StatsTab(vm, lg, p)
                2 -> GrowthTab(p)
                else -> BioTab(lg, p)
            }
            Spacer(Modifier.height(30.dp))
        }
    }
    if (editNil && team != null) {
        var amount by remember { mutableIntStateOf(p.nil) }
        val expected = Ratings.expectedNil(p)
        val room = Finance.available(lg, team) + p.nil
        AlertDialog(
            onDismissRequest = { editNil = false }, containerColor = P.surface,
            title = { Text("NIL deal · ${p.short}", style = T.head, color = P.ink) },
            text = {
                Column {
                    Text("Market value ${money(expected)} · Room ${money(room)}", style = T.small, color = P.sub)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallButton("−25") { amount = (amount - 25).coerceAtLeast(0) }
                        Text(money(amount), style = T.num, color = P.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        SmallButton("+25") { amount = (amount + 25).coerceAtMost(room.coerceAtLeast(amount)) }
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallButton("Match market value", Modifier.fillMaxWidth()) { amount = expected.coerceAtMost(room.coerceAtLeast(p.nil)) }
                }
            },
            confirmButton = {
                TextButton({
                    editNil = false
                    val a = amount
                    vm.act { val pl = it.player(p.id); val up = a > pl.nil; pl.nil = a; if (up) pl.happiness = (pl.happiness + 8).coerceAtMost(100) }
                }) { Text("Save", color = P.accent) }
            },
            dismissButton = { TextButton({ editNil = false }) { Text("Cancel", color = P.sub) } },
        )
    }
}

@Composable
private fun RatingsTab(p: Player) {
    Section("Offense")
    Panel { listOf(Attr.INS, Attr.MID, Attr.OUT, Attr.HND, Attr.PAS).forEach { AttrBar(it.label, p[it], p.seasonGains[it.ordinal]) } }
    Section("Defense")
    Panel { listOf(Attr.REB, Attr.PER, Attr.IDF, Attr.STL, Attr.BLK).forEach { AttrBar(it.label, p[it], p.seasonGains[it.ordinal]) } }
}

@Composable
private fun AttrBar(label: String, v: Int, gain: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.body, color = P.ink, modifier = Modifier.width(104.dp), maxLines = 1)
        Bar(v / 99f, Modifier.weight(1f), color = tier(v), height = 7.dp)
        Text("$v", style = T.numSm, color = P.ink, modifier = Modifier.width(36.dp), textAlign = TextAlign.End)
        Text(if (gain > 0) "+$gain" else "", style = T.small.copy(fontSize = 12.sp), color = P.good, modifier = Modifier.width(30.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun StatsTab(vm: GameViewModel, lg: League, p: Player) {
    val s = p.stats
    Section("This season")
    Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        StatRow(listOf(Triple("PTS", f1(s.ppg), null), Triple("REB", f1(s.rpg), null), Triple("AST", f1(s.apg), null), Triple("STL", f1(s.spg), null), Triple("BLK", f1(s.bpg), null)), style = T.numSm)
        Spacer(Modifier.height(10.dp))
        StatRow(listOf(Triple("GP", "${s.gp}", null), Triple("MIN", f1(s.mpg), null), Triple("FG%", pct(s.fgPct), null), Triple("3P%", pct(s.tpPct), null), Triple("FT%", pct(s.ftPct), null)), style = T.numSm)
    }
    if (p.log.isNotEmpty()) {
        Section("Game log")
        GroupList(p.log.reversed().take(8)) { e ->
            ListRow(onClick = { vm.sheet = null; vm.push(Route.Box(e.gameId)) }) {
                ResultDot(e.won)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("${e.site} ${lg.team(e.oppId).school} · ${e.score}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.pts} pts · ${e.reb} reb · ${e.ast} ast · ${e.min} min", style = T.small, color = P.sub)
                }
            }
        }
    }
    val rows = p.career.toMutableList()
    Section("Career")
    if (rows.isEmpty() && s.gp == 0) Panel { Text("No college stats yet.", style = T.body, color = P.sub) }
    else Panel(padding = PaddingValues(vertical = 10.dp)) {
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
            val heads = listOf("YEAR", "TEAM", "GP", "PTS", "REB", "AST", "FG%", "3P%")
            val w = listOf(54, 54, 36, 46, 46, 46, 56, 56)
            Row { heads.forEachIndexed { i, h -> Text(h, style = T.label, color = P.sub, modifier = Modifier.width(w[i].dp), textAlign = if (i < 2) TextAlign.Start else TextAlign.End) } }
            rows.forEach { r ->
                Row(Modifier.padding(vertical = 6.dp)) {
                    listOf("${r.season}", r.team, "${r.s.gp}", f1(r.s.ppg), f1(r.s.rpg), f1(r.s.apg), pct(r.s.fgPct), pct(r.s.tpPct)).forEachIndexed { i, v ->
                        Text(v, style = T.small, color = P.ink, modifier = Modifier.width(w[i].dp), textAlign = if (i < 2) TextAlign.Start else TextAlign.End)
                    }
                }
            }
            if (s.gp > 0 && p.career.none { it.season == lg.season }) Row(Modifier.padding(vertical = 6.dp)) {
                listOf("${lg.season}", if (p.teamId >= 0) lg.team(p.teamId).abbr else "", "${s.gp}", f1(s.ppg), f1(s.rpg), f1(s.apg), pct(s.fgPct), pct(s.tpPct)).forEachIndexed { i, v ->
                    Text(v, style = T.small, color = P.accent, modifier = Modifier.width(w[i].dp), textAlign = if (i < 2) TextAlign.Start else TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun GrowthTab(p: Player) {
    Section("Overall by year")
    Panel {
        val pts = (p.ovrHistory + if (p.ovrHistory.size <= p.year) listOf(p.ovr) else emptyList()).take(5)
        Sparkline(pts, p.pot, Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            pts.indices.forEach { i -> Text(classLabel(i), style = T.label, color = P.sub, modifier = Modifier.weight(1f), textAlign = TextAlign.Center) }
        }
    }
    Section("Development")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.focus.label, style = T.head, color = P.ink)
                Text(p.focus.blurb, style = T.small, color = P.sub)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Text("XP TO NEXT LEVEL", style = T.label, color = P.sub, modifier = Modifier.weight(1f))
            Text("${p.xp} / ${Ratings.xpNeeded()}", style = T.small, color = P.ink)
        }
        Spacer(Modifier.height(6.dp))
        Bar(p.xp.toFloat() / Ratings.xpNeeded(), Modifier.fillMaxWidth(), height = 8.dp)
        Spacer(Modifier.height(10.dp))
        Text("${p.seasonLevels} level-ups this season · ${p.careerLevels} career" + if (p.banked > 0) " · ${p.banked} banked" else "", style = T.small, color = P.sub)
    }
}

@Composable
private fun Sparkline(values: List<Int>, pot: Int, modifier: Modifier) {
    val accent = P.accent
    val grid = P.line
    val ink = P.ink
    val sub = P.faint
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val lo = (values.min() - 6).coerceAtLeast(20)
        val hi = maxOf(pot, values.max()) + 4
        val w = size.width
        val h = size.height
        val n = 5
        fun x(i: Int) = w * (i + 0.5f) / n
        fun y(v: Int) = h - 14f - (h - 34f) * (v - lo) / (hi - lo).toFloat()
        val py = y(pot)
        drawLine(grid, Offset(0f, py), Offset(w, py), strokeWidth = 2f)
        val potLabel = measurer.measure("POT $pot", TextStyle(fontFamily = Body, fontSize = 10.sp, color = sub))
        drawText(potLabel, topLeft = Offset(w - potLabel.size.width, py - potLabel.size.height - 2f))
        val path = Path()
        values.forEachIndexed { i, v -> if (i == 0) path.moveTo(x(i), y(v)) else path.lineTo(x(i), y(v)) }
        drawPath(path, accent, style = Stroke(5f, cap = StrokeCap.Round))
        values.forEachIndexed { i, v ->
            drawCircle(accent, 9f, Offset(x(i), y(v)))
            val l = measurer.measure("$v", TextStyle(fontFamily = Display, fontSize = 14.sp, color = ink))
            drawText(l, topLeft = Offset(x(i) - l.size.width / 2f, y(v) - l.size.height - 8f))
        }
    }
}

@Composable
private fun BioTab(lg: League, p: Player) {
    Section("Background")
    GroupList(
        listOfNotNull(
            "Hometown" to p.hometown,
            "High school" to p.highSchool,
            "Recruit rating" to when {
                p.recruitRank > 0 -> "${p.stars}★ · No. ${p.recruitRank} overall"
                p.tryout > 0 -> "Unrated · made the team at ${p.tryout} tryouts"
                else -> "Unrated walk-on"
            },
            if (p.teamId >= 0) "Roster spot" to (if (p.walkOn) "Walk-on (no scholarship)" else "Scholarship") else null,
            "Position rank" to if (p.posRank > 0) "No. ${p.posRank} ${p.pos}" else "–",
            if (p.teamId >= 0) "NIL deal" to money(p.nil) else null,
        ),
    ) { (k, v) ->
        ListRow {
            Text(k, style = T.body, color = P.sub, modifier = Modifier.weight(1f))
            Text(v, style = T.bodyB, color = P.ink)
        }
    }
    if (p.traits.isNotEmpty()) {
        Section("Personality")
        GroupList(p.traits) { t ->
            ListRow {
                Column {
                    Text(t.label, style = T.bodyB, color = traitColor(t))
                    Text(traitBlurb(t), style = T.small, color = P.sub)
                }
            }
        }
    }
    if (p.awards.isNotEmpty()) {
        Section("Honors")
        GroupList(p.awards.reversed().take(12)) { a ->
            ListRow {
                Text(a.substringBefore(' '), style = T.numSm, color = P.sub, modifier = Modifier.width(56.dp))
                Text(awardName(a.substringAfter(' ')), style = T.bodyB, color = P.ink)
            }
        }
    }
}

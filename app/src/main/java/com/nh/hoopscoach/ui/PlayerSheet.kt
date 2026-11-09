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
import com.nh.hoopscoach.game.Careers
import com.nh.hoopscoach.game.Finance
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Mood
import com.nh.hoopscoach.game.Morale
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Redshirt
import com.nh.hoopscoach.game.Redshirts
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
    val former = if (team == null && p.lastTeam >= 0) lg.team(p.lastTeam) else null
    var tab by remember(p.id) { mutableIntStateOf(if (former != null) 1 else 0) }
    var editNil by remember { mutableStateOf(false) }
    val board = remember(p.id, vm.rev) { Offseason.draftBoard(lg) }
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, team ?: former, 72.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.first.uppercase(), style = T.label, color = P.sub)
                    Text(p.last.uppercase(), style = T.title, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${p.pos} · ${p.cls} · ${Ratings.heightLabel(p.height)} · ${p.weight} lbs", style = T.small, color = P.sub)
                    if (team != null) Text("${team.school} · ${Gameplan.role(lg, team, p.id)}", style = T.small, color = P.accent)
                    else if (former != null) Text(p.left.ifEmpty { "Former ${former.school} player" }, style = T.small, color = P.accent)
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
                if (team != null) Tag("DRAFT: ${Offseason.projection(board[p.id], p.year).uppercase()}", P.sub) else Tag("FORMER PLAYER", P.sub)
                if (team != null) {
                    val m = Morale.mood(p)
                    Tag(m.label.uppercase(), when (m) { Mood.HAPPY -> P.good; Mood.CONTENT -> P.sub; Mood.UNHAPPY -> P.warn; Mood.TRANSFER -> P.bad })
                }
                if (p.walkOn) Tag("WALK-ON", P.sub)
                if (p.redshirt == Redshirt.ACADEMIC) Tag("ACADEMIC RS", P.accent)
                if (p.redshirt == Redshirt.MEDICAL) Tag("MEDICAL RS", P.good)
                else if (p.rsClass && p.redshirt == Redshirt.NONE) Tag("RS", P.sub)
                p.traits.forEach { Tag(it.label.uppercase(), traitColor(it)) }
                if (p.favorite) Tag("★ FAVORITE", P.gold)
            }
            Spacer(Modifier.height(12.dp))
            FlowRow(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (mine) SmallButton("NIL ${money(p.nil)}") { editNil = true }
                if (mine) SmallButton("Training focus") { vm.sheet = null; vm.push(Route.Focus(p.id)) }
                if (mine) SmallButton("Edit") { vm.sheet = null; vm.push(Route.EditPlayer(p.id)) }
                SmallButton(if (p.favorite) "Unstar" else "Star") { vm.act { p.favorite = !p.favorite } }
            }
            if (mine) {
                val canAc = Redshirts.canAcademic(lg, p)
                val canMed = Redshirts.canMedical(lg, p)
                val sitting = p.redshirt == Redshirt.ACADEMIC
                if (canAc || canMed || sitting || p.redshirt == Redshirt.MEDICAL || p.rsClass) {
                    Spacer(Modifier.height(10.dp))
                    Panel(padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                        Text("Redshirt", style = T.bodyB, color = P.ink)
                        Text(Redshirts.blurb(p), style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp, bottom = 10.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (sitting) SmallButton("Cancel academic RS") {
                                vm.act {
                                    if (Redshirts.setAcademic(it, p.id, false)) vm.toast("${p.short} is back in the rotation")
                                    else vm.toast("Can't cancel this redshirt")
                                }
                            }
                            if (canAc) SmallButton("Academic redshirt", primary = true) {
                                vm.act {
                                    if (Redshirts.setAcademic(it, p.id, true)) vm.toast("${p.short} will sit out this season")
                                    else vm.toast("Can't redshirt him now")
                                }
                            }
                            if (canMed) SmallButton("Medical redshirt", primary = true) {
                                vm.act {
                                    if (Redshirts.setMedical(it, p.id)) vm.toast("${p.short} keeps this year of eligibility")
                                    else vm.toast("Not eligible for a medical redshirt")
                                }
                            }
                        }
                    }
                }
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
            Segmented(listOf("Ratings", "Career", "Growth", "Bio"), tab, { tab = it })
        }
        item {
            when (tab) {
                0 -> RatingsTab(p)
                1 -> CareerTab(vm, lg, p)
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

/** One row of the career table. */
private class CareerRow(val year: String, val team: String, val cls: String, val s: StatLine, val current: Boolean = false, val total: Boolean = false)

@Composable
private fun CareerTab(vm: GameViewModel, lg: League, p: Player) {
    val s = p.stats
    val inSchool = p.teamId >= 0
    if (inSchool && s.gp > 0) {
        Section("This season")
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
            StatRow(listOf(Triple("PTS", f1(s.ppg), null), Triple("REB", f1(s.rpg), null), Triple("AST", f1(s.apg), null), Triple("STL", f1(s.spg), null), Triple("BLK", f1(s.bpg), null)), style = T.numSm)
            Spacer(Modifier.height(10.dp))
            StatRow(listOf(Triple("GP", "${s.gp}", null), Triple("MIN", f1(s.mpg), null), Triple("FG%", pct(s.fgPct), null), Triple("3P%", pct(s.tpPct), null), Triple("FT%", pct(s.ftPct), null)), style = T.numSm)
        }
    }

    // Season by season, with the season in progress and a career line.
    var totals by remember(p.id) { mutableStateOf(false) }
    val rows = mutableListOf<CareerRow>()
    p.career.forEach { rows += CareerRow("${it.season}", it.team, classLabel(it.cls), it.s) }
    if (inSchool && s.gp > 0 && p.career.none { it.season == lg.season }) rows += CareerRow("${lg.season}", lg.team(p.teamId).abbr, p.cls, s, current = true)
    Section("Season by season", action = if (rows.isEmpty()) null else if (totals) "Per game" else "Totals", onAction = { totals = !totals })
    if (rows.isEmpty()) Panel { Text("No college stats yet.", style = T.body, color = P.sub) }
    else {
        if (rows.size > 1) rows += CareerRow("Career", "${rows.size} yrs", "", Careers.total(p), total = true)
        Panel(padding = PaddingValues(vertical = 10.dp)) { StatTable(rows, totals) }
        if (rows.any { it.current }) Text("Highlighted: season in progress.", style = T.small, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 6.dp))
    }

    // NCAA Tournament.
    val march = p.career.filter { it.ncaa != null && it.ncaa.gp > 0 }.map { CareerRow("${it.season}", it.team, classLabel(it.cls), it.ncaa!!) }.toMutableList()
    if (inSchool && p.tourney.gp > 0 && p.career.none { it.season == lg.season }) march += CareerRow("${lg.season}", lg.team(p.teamId).abbr, p.cls, p.tourney, current = true)
    if (march.isNotEmpty()) {
        if (march.size > 1) march += CareerRow("Career", "${march.sumOf { it.s.gp }} G", "", Careers.total(p, ncaa = true), total = true)
        Section("NCAA Tournament")
        Panel(padding = PaddingValues(vertical = 10.dp)) { StatTable(march, totals) }
    }

    // Career highs.
    if (p.highs.any { it > 0 }) {
        Section("Career highs")
        Panel(padding = PaddingValues(vertical = 12.dp, horizontal = 6.dp)) {
            for (rowStart in listOf(0, 3)) {
                Row(Modifier.fillMaxWidth()) {
                    for (i in rowStart until rowStart + 3) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${p.highs.getOrElse(i) { 0 }}", style = T.num, color = P.ink)
                            Text(Careers.HIGHS[i].uppercase(), style = T.label, color = P.sub)
                            Text(p.highGames.getOrElse(i) { "" }, style = T.small.copy(fontSize = 11.sp), color = P.faint, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    HonorsTimeline(lg, p)

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
}

@Composable
private fun StatTable(rows: List<CareerRow>, totals: Boolean) {
    val heads = listOf("YEAR", "TEAM", "CL", "GP", "MIN", "PTS", "REB", "AST", "STL", "BLK", "TO", "FG%", "3P%", "FT%")
    val w = listOf(52, 54, 30, 30, 44, 44, 42, 42, 38, 38, 36, 52, 52, 52)
    fun values(r: CareerRow): List<String> {
        val s = r.s
        val per = if (totals) listOf("${s.sec / 60}", "${s.pts}", "${s.reb}", "${s.ast}", "${s.stl}", "${s.blk}", "${s.tov}")
        else listOf(f1(s.mpg), f1(s.ppg), f1(s.rpg), f1(s.apg), f1(s.spg), f1(s.bpg), f1(s.per(s.tov)))
        return listOf(r.year, r.team, r.cls, "${s.gp}") + per + listOf(pct(s.fgPct), pct(s.tpPct), pct(s.ftPct))
    }
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp)) {
        Row { heads.forEachIndexed { i, h -> Text(h, style = T.label, color = P.sub, modifier = Modifier.width(w[i].dp), textAlign = if (i < 3) TextAlign.Start else TextAlign.End) } }
        rows.forEach { r ->
            if (r.total) Box(Modifier.width(w.sum().dp).padding(top = 4.dp).height(1.dp).background(P.line))
            Row(Modifier.padding(vertical = 6.dp)) {
                values(r).forEachIndexed { i, v ->
                    Text(
                        v, style = if (r.total) T.bodyB.copy(fontSize = 13.sp) else T.small,
                        color = when { r.current -> P.accent; else -> P.ink }, maxLines = 1,
                        modifier = Modifier.width(w[i].dp), textAlign = if (i < 3) TextAlign.Start else TextAlign.End,
                    )
                }
            }
        }
    }
}

/** Every season with the team's record and result, and the honors he won. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HonorsTimeline(lg: League, p: Player) {
    val seasons = Careers.honorsBySeason(p, lg.season)
    if (seasons.isEmpty()) return
    val legend = lg.teams.flatMap { t -> t.legends.filter { it.pid == p.id }.map { t to it } }.firstOrNull()
    Section("Honors by season")
    GroupList(seasons) { sh ->
        val line = p.career.firstOrNull { it.season == sh.season }
        val current = line == null && sh.season == lg.season && p.teamId >= 0
        val team = line?.let { l -> lg.teams.getOrNull(l.teamId) ?: lg.teams.firstOrNull { it.abbr == l.team } } ?: if (current) lg.team(p.teamId) else null
        Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${sh.season}", style = T.numSm, color = P.accent, modifier = Modifier.width(56.dp))
                if (team != null) { TeamBadge(team, 22.dp); Spacer(Modifier.width(8.dp)) }
                Column(Modifier.weight(1f)) {
                    Text(listOfNotNull(team?.school, line?.let { classLabel(it.cls) } ?: if (current) p.cls else null).joinToString(" · "), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val res = when {
                        line != null && line.w + line.l > 0 -> "${line.w}-${line.l} · ${line.result}"
                        line != null -> line.result
                        current && team != null -> "${team.record} · season in progress"
                        else -> ""
                    }
                    if (res.isNotEmpty()) Text(res, style = T.small, color = P.sub, maxLines = 1)
                }
            }
            val extra = mutableListOf<Pair<String, Int>>()
            if (p.tryout == sh.season) extra += "Made the team at walk-on tryouts" to 1
            if (legend != null && sh.season == seasons.first().season && p.teamId < 0) extra += (if (legend.second.retired) "No. ${legend.second.number} retired by ${legend.first.school}" else "${legend.first.school} Hall of Fame") to 5
            val all = extra + sh.honors
            if (all.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.padding(start = 56.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    all.forEach { (text, rank) -> Tag(text.uppercase(), when { rank >= 4 -> P.gold; rank == 3 -> P.accent; rank == 2 -> P.good; else -> P.sub }) }
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

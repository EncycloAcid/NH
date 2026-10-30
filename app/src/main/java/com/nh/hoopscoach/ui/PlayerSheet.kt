package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Attr
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Morale
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Trait
import com.nh.hoopscoach.game.classLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSheet(vm: GameViewModel, lg: League, p: Player) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = { vm.sheet = null },
        sheetState = state,
        containerColor = C.card,
        dragHandle = { Box(Modifier.padding(top = 10.dp, bottom = 6.dp).size(44.dp, 5.dp).clip(RoundedCornerShape(3.dp)).background(C.dim)) },
    ) {
        PlayerSheetContent(vm, lg, p, Modifier.fillMaxHeight(0.94f))
    }
}

private fun traitBlurb(t: Trait) = when (t) {
    Trait.AMBITIOUS -> "Wants a big role and gets restless on the bench."
    Trait.BAG_CHASER -> "Cares a lot about his NIL deal."
    Trait.DISLOYAL -> "Quick to look at the transfer portal."
    Trait.LOYAL -> "Committed to the program through ups and downs."
    Trait.TEAM_PLAYER -> "Accepts whatever role helps the team."
    Trait.GYM_RAT -> "Lives in the gym. Develops faster."
    Trait.CLUTCH -> "Shoots better when the game is on the line."
}

@Composable
fun PlayerSheetContent(vm: GameViewModel, lg: League, p: Player, modifier: Modifier = Modifier) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val team = if (p.teamId >= 0) lg.team(p.teamId) else null
    val mine = team?.id == lg.userTeamId
    var tab by remember(p.id) { mutableIntStateOf(0) }
    var editNil by remember { mutableStateOf(false) }
    val board = remember(p.id, vm.rev) { Offseason.draftBoard(lg) }
    val expected = Ratings.expectedNil(p)
    LazyColumn(modifier.fillMaxWidth()) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                PlayerAvatar(p, lg, 84.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val role = team?.let { Gameplan.role(it, p.id) } ?: "Free Agent"
                    Text("$role · ${p.style.label}", color = C.sub, fontSize = 15.sp)
                    if (team != null) {
                        val gap = expected - p.nil
                        Text(
                            when { gap <= 10 -> "⚖ Deal matches expected NIL"; else -> "⚖ Underpaid by ${money(gap)}" },
                            color = if (gap <= 10) C.green else C.yellow, fontSize = 14.sp,
                        )
                    }
                    p.awards.lastOrNull { !it.contains("Draft") }?.let { Text("🎖 ${it.substringAfter(' ')}", color = C.blue, fontSize = 14.sp) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (mine) IconBox(onClick = { editNil = true }) { Icon(Icons.Default.Edit, "Edit NIL", tint = C.sub, modifier = Modifier.size(18.dp)) }
                    IconBox(onClick = { vm.act { p.favorite = !p.favorite } }) {
                        Icon(Icons.Default.Star, "Favorite", tint = if (p.favorite) C.yellow else C.dim, modifier = Modifier.size(18.dp))
                    }
                }
            }
            if (team != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp).background(Color(team.primary)).clickable { vm.sheet = null; vm.push(Route.TeamPage(team.id)) }.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    TeamLogo(team, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${team.name} (${team.record})", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(14.dp))
            val s = p.stats
            StatGrid(
                listOf(
                    Triple("Position", p.pos.name, C.text), Triple("Grade", p.cls, C.text), Triple("Height", Ratings.heightLabel(p.height), C.text),
                    Triple("Weight", "${p.weight}", C.text), Triple("Draft", Offseason.projection(board[p.id], p.year).let { if (it == "1st Round") "1st Rd" else if (it == "2nd Round") "2nd Rd" else it }, C.text),
                    Triple("Overall", "${p.ovr}", ratingColor(p.ovr)), Triple("Potential", "${p.pot}", ratingColor(p.pot)),
                    Triple("Offense", "${Ratings.offense(p)}", ratingColor(Ratings.offense(p))), Triple("Defense", "${Ratings.defense(p)}", ratingColor(Ratings.defense(p))),
                    Triple("Shooting", "${Ratings.shooting(p)}", ratingColor(Ratings.shooting(p))),
                    Triple("PPG", f1(s.ppg), C.text), Triple("APG", f1(s.apg), C.text), Triple("RPG", f1(s.rpg), C.text), Triple("SPG", f1(s.spg), C.text), Triple("BPG", f1(s.bpg), C.text),
                ),
                size = 21.sp,
            )
            Spacer(Modifier.height(16.dp))
            Segmented(listOf("Profile", "Stats", "Growth"), tab, { tab = it })
        }
        when (tab) {
            0 -> item { ProfileTab(lg, p) }
            1 -> item { StatsTab(vm, lg, p) }
            else -> item { GrowthTab(vm, lg, p, mine) }
        }
        item { Spacer(Modifier.height(30.dp)) }
    }
    if (editNil && team != null) {
        var amount by remember { mutableIntStateOf(p.nil) }
        val room = com.nh.hoopscoach.game.Finance.available(lg, team) + p.nil
        AlertDialog(
            onDismissRequest = { editNil = false },
            containerColor = C.card2,
            title = { Text("NIL deal for ${p.short}", color = C.text) },
            text = {
                Column {
                    Text("Market value ${money(expected)} · Room ${money(room)}", color = C.sub, fontSize = 14.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallButton("−") { amount = (amount - 25).coerceAtLeast(0) }
                        Text(money(amount), fontSize = 24.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        SmallButton("+") { amount = (amount + 25).coerceAtMost(room.coerceAtLeast(amount)) }
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
                }) { Text("Save", color = C.orangeText) }
            },
            dismissButton = { TextButton({ editNil = false }) { Text("Cancel", color = C.sub) } },
        )
    }
}

@Composable
private fun IconBox(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(C.card2).clickable(onClick = onClick), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ProfileTab(lg: League, p: Player) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            AttrColumn("Offense", listOf(Attr.INS, Attr.MID, Attr.OUT, Attr.HND, Attr.PAS), p, Modifier.weight(1f))
            AttrColumn("Defense", listOf(Attr.REB, Attr.PER, Attr.IDF, Attr.STL, Attr.BLK), p, Modifier.weight(1f))
        }
        Text("DETAILS", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.padding(horizontal = Gutter, vertical = 6.dp))
        KeyValue("Hometown", p.hometown)
        KeyValue("High School", p.highSchool)
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recruit Rating", color = C.sub, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Stars(p.stars, 18.sp)
        }
        Divider(Modifier.padding(horizontal = Gutter))
        KeyValue("Recruit Rank", if (p.recruitRank > 0) "#${p.recruitRank}" else "Walk-on", C.orangeText)
        KeyValue("Recruit Pos. Rank", if (p.posRank > 0) "#${p.posRank} ${p.pos}" else "—", C.orangeText)
        KeyValue("NIL Deal", money(p.nil))
        KeyValue("Mood", "${Morale.mood(p).label} (${p.happiness})", when (Morale.mood(p)) {
            com.nh.hoopscoach.game.Mood.HAPPY -> C.green; com.nh.hoopscoach.game.Mood.CONTENT -> C.text
            com.nh.hoopscoach.game.Mood.UNHAPPY -> C.yellow; else -> C.red
        })
        Text("PERSONALITY", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp, bottom = 6.dp))
        if (p.traits.isEmpty()) Text("Even-keeled. No strong traits.", color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter))
        p.traits.forEach { t ->
            Column(Modifier.padding(horizontal = Gutter, vertical = 6.dp)) {
                Text(t.label, color = traitColor(t), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(traitBlurb(t), color = C.sub, fontSize = 14.sp)
            }
        }
        if (p.awards.isNotEmpty()) {
            Text("HONORS", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.padding(horizontal = Gutter).padding(top = 18.dp, bottom = 6.dp))
            p.awards.reversed().take(12).forEach { Text(it, color = C.text, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 3.dp)) }
        }
    }
}

@Composable
private fun AttrColumn(title: String, attrs: List<Attr>, p: Player, modifier: Modifier) {
    Column(modifier) {
        Text(title.uppercase(), style = HoopsType.labelMedium, color = C.sub)
        Spacer(Modifier.height(8.dp))
        attrs.forEach { a ->
            Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, fontSize = 16.sp, color = C.text, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${p[a]}", fontSize = 17.sp, color = ratingColor(p[a]), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun StatsTab(vm: GameViewModel, lg: League, p: Player) {
    var all by remember(p.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (p.log.isNotEmpty()) {
            SectionLabel("Game Log", action = if (all) "Show Less" else "See All", onAction = { all = !all })
            val entries = p.log.reversed().let { if (all) it else it.take(5) }
            entries.forEachIndexed { i, e ->
                val opp = lg.team(e.oppId)
                Row(Modifier.fillMaxWidth().clickable { vm.sheet = null; vm.push(Route.Box(e.gameId)) }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp).clip(CircleShape).background(if (e.won) C.green else C.red), contentAlignment = Alignment.Center) {
                        Text(if (e.won) "W" else "L", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${e.site} ${opp.abbr}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("P: ${e.pts}  A: ${e.ast}  R: ${e.reb}  S: ${e.stl}  B: ${e.blk}  TO: ${e.tov}  M: ${e.min}", color = C.sub, fontSize = 13.sp)
                    }
                    Text(e.score, fontSize = 17.sp)
                }
                if (i < entries.lastIndex) Divider(Modifier.padding(horizontal = Gutter))
            }
        }
        SectionLabel("Averages")
        val cols = (1..4).map { yr -> p.career.lastOrNull { it.cls == yr } to (if (p.year == yr && p.stats.gp > 0) p.stats else null) }
        val total = StatLine()
        p.career.forEach { total.add(it.s) }
        if (p.stats.gp > 0 && p.career.none { it.season == lg.season }) total.add(p.stats)
        val currentTeam = if (p.teamId >= 0) lg.team(p.teamId) else null
        val rows = listOf<Pair<String, (StatLine) -> String>>(
            "GP" to { s -> "${s.gp}" }, "MIN" to { s -> f1(s.mpg) }, "PPG" to { s -> f1(s.ppg) }, "RPG" to { s -> f1(s.rpg) },
            "APG" to { s -> f1(s.apg) }, "SPG" to { s -> f1(s.spg) }, "BPG" to { s -> f1(s.bpg) },
            "FG%" to { s -> pct(s.fgPct) }, "3P%" to { s -> pct(s.tpPct) }, "FT%" to { s -> pct(s.ftPct) },
        )
        Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            Row { Cell("", 64, C.sub); listOf("FR", "SO", "JR", "SR", "TOTAL").forEachIndexed { i, h -> Cell(h, 66, if (i + 1 == p.year) C.orangeText else C.sub) } }
            Divider()
            Row {
                Cell("TM", 64, C.sub)
                cols.forEachIndexed { i, (line, cur) -> Cell(line?.team ?: if (cur != null) currentTeam?.abbr ?: "-" else "-", 66, if (i + 1 == p.year) C.orangeText else C.text) }
                Cell(currentTeam?.abbr ?: p.career.lastOrNull()?.team ?: "-", 66, C.text)
            }
            Divider()
            for ((label, fmt) in rows) {
                Row {
                    Cell(label, 64, C.sub)
                    cols.forEachIndexed { i, (line, cur) ->
                        val s = cur ?: line?.s
                        Cell(if (s == null || s.gp == 0) "-" else fmt(s), 66, if (i + 1 == p.year) C.orangeText else C.text)
                    }
                    Cell(if (total.gp == 0) "-" else fmt(total), 66, C.text)
                }
                Divider()
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: Int, color: Color) {
    Text(text, color = color, fontSize = 15.sp, modifier = Modifier.width(width.dp).padding(vertical = 10.dp), textAlign = TextAlign.End, maxLines = 1)
}

@Composable
private fun GrowthTab(vm: GameViewModel, lg: League, p: Player, mine: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        SectionLabel("OVR Progression")
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
            for (y in 0..4) {
                val value: Int? = when {
                    y < p.ovrHistory.size && y < p.year -> p.ovrHistory[y]
                    y == p.year -> p.ovr
                    y == 0 && p.ovrHistory.isNotEmpty() -> p.ovrHistory[0]
                    else -> null
                }
                val current = y == p.year
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).border(2.dp, if (current) C.orange else C.dim, CircleShape)
                            .background(if (current) C.orangeDim else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) { Text(value?.toString() ?: "–", color = if (current) C.orangeText else C.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold) }
                    Spacer(Modifier.height(6.dp))
                    Text(classLabel(y), color = if (current) C.orangeText else C.sub, fontSize = 13.sp)
                }
            }
        }
        SectionLabel("Development")
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), color = C.card2, onClick = if (mine) ({ vm.sheet = null; vm.push(Route.Focus(p.id)) }) else null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(p.focus.label + " · " + p.pos, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(p.focus.blurb, color = C.sub, fontSize = 14.sp)
                }
                if (mine) Text("Change ›", color = C.orangeText, fontSize = 14.sp)
            }
            if (p.focus.boosts.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth()) {
                    p.focus.boosts.forEach { a ->
                        Column(Modifier.weight(1f)) {
                            Text("+1", fontSize = 20.sp, color = C.text)
                            Text(a.short, fontSize = 13.sp, color = C.sub)
                        }
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Row {
                Text("XP TO NEXT LEVEL", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
                Text("${p.xp} / ${Ratings.xpNeeded()}", fontSize = 14.sp)
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(p.xp.toFloat() / Ratings.xpNeeded(), Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Row {
                Text("SEASON LVL-UPS  ${p.seasonLevels}", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.weight(1f))
                Text("CAREER  ${p.careerLevels}", style = HoopsType.labelMedium, color = C.sub)
            }
            if (p.banked > 0) Text("⚡ ${p.banked} banked level-up${if (p.banked > 1) "s" else ""} to spend", color = C.yellow, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
        }
        SectionLabel("Attribute Gains This Season")
        val attrs = Attr.entries
        Column(Modifier.padding(horizontal = Gutter)) {
            for (pair in attrs.chunked(2)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    pair.forEach { a ->
                        val g = p.seasonGains[a.ordinal]
                        Row(Modifier.weight(1f)) {
                            Text(a.label, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text(if (g > 0) "+$g" else "—", color = if (g > 0) C.green else C.dim, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.AwardWinner
import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.CoachSkill
import com.nh.hoopscoach.game.Coaching
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.WrapUp

private const val PAGES = 5

@Composable
fun WrapUpScreen(vm: GameViewModel, lg: League) {
    val w = lg.wrapUp ?: run { EmptyNote("Season complete."); return }
    val page = vm.wrapPage.coerceIn(0, PAGES - 1)
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (page) {
                0 -> summaryPage(vm, lg, w)
                1 -> awardsPage(vm, lg)
                2 -> graduationsPage(vm, lg)
                3 -> standingPage(vm, lg, w)
                else -> coachPage(vm, lg, w)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
        PagerBar(page, PAGES, onBack = { vm.wrapPage = page - 1 }, onNext = { if (page < PAGES - 1) vm.wrapPage = page + 1 else vm.beginOffseason() }, lastLabel = "Continue")
    }
}

@Composable
fun PagerBar(page: Int, pages: Int, onBack: () -> Unit, onNext: () -> Unit, lastLabel: String) {
    Column(Modifier.fillMaxWidth().background(C.bg)) {
        Divider()
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(enabled = page > 0, onClick = onBack), contentAlignment = Alignment.Center) {
                if (page > 0) Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = C.sub)
            }
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(pages) { i -> Box(Modifier.padding(horizontal = 5.dp).size(9.dp).clip(CircleShape).background(if (i == page) C.orange else C.dim)) }
            }
            if (page < pages - 1) Text("Next ›", color = C.orangeText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onNext).padding(8.dp))
            else SmallButton(lastLabel, primary = true, onClick = onNext)
        }
    }
}

// ---------- page 1: summary ----------

private fun LazyListScope.summaryPage(vm: GameViewModel, lg: League, w: WrapUp) {
    val t = lg.team(w.teamId)
    item {
        Text("SEASON ${w.season} WRAP-UP", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.padding(start = Gutter, end = Gutter, top = 18.dp))
        Text("${t.school} · ${lg.conf(t.confId).abbr} · Coach ${lg.userCoach.name}", color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter, vertical = 2.dp))
        Spacer(Modifier.height(12.dp))
        val good = w.result.contains("Champion") || w.result.contains("Runner") || w.result.contains("Final")
        Box(
            Modifier.fillMaxWidth().padding(horizontal = Gutter).clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF5A2E18), Color(0xFF2A1D18))))
                .border(1.dp, C.orange.copy(alpha = 0.55f), RoundedCornerShape(18.dp)).padding(18.dp),
        ) {
            Column {
                Text(w.result.uppercase(), color = if (good) C.orangeText else C.text, fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                Text(if (w.seed > 0) "NCAA TOURNAMENT" else "SEASON RESULT", color = C.orangeText, style = HoopsType.labelLarge, letterSpacing = 1.5.sp)
                if (w.seed > 0) Text("#${w.seed} Seed", color = C.green, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (w.path.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        w.path.forEachIndexed { i, opp ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(42.dp).clip(CircleShape).background(if (w.pathWon[i]) C.green else C.red).padding(3.dp), contentAlignment = Alignment.Center) {
                                    TeamLogo(lg.team(opp), 34.dp)
                                }
                                Text(com.nh.hoopscoach.game.Season.NCAA_ROUNDS.getOrElse(i) { "" }, color = if (w.pathWon[i]) C.sub else C.red, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        val diff = w.w - w.expWins
        StatGrid(
            listOf(
                Triple("Overall", "${w.w}-${w.l}", C.text), Triple("Reg Season", "${w.regW}-${w.regL}", C.text),
                Triple("Conf", "${w.cw}-${w.cl}", C.text), Triple("Exp Wins", "%.0f %+.0f".format(w.expWins, diff), if (diff >= 0) C.green else C.red),
                Triple("NET", "${w.net}", C.orangeText), Triple("AP Final", if (w.ap > 0) "#${w.ap}" else "NR", C.text),
                Triple("Conf Finish", ordinal(w.confFinish), C.green), Triple("Prestige", "${w.prestige}", ratingColor(w.prestige)),
                Triple("Q1", w.q1, C.green), Triple("Q2", w.q2, C.green), Triple("WAB", "%+.1f".format(w.wab), if (w.wab >= 0) C.green else C.red),
                Triple("SOS", "#${w.sosRank}", C.text),
            ),
            columns = 4, size = 22.sp,
        )
    }
    // awards
    val byPlayer = w.awards.groupBy { it.pid }
    if (w.coachAwards.isNotEmpty() || byPlayer.isNotEmpty()) item { SectionLabel("Awards") }
    if (w.coachAwards.isNotEmpty()) item {
        AwardBlock(vm, lg, null, lg.userCoach.name, null, w.coachAwards, "")
    }
    items(byPlayer.entries.toList().sortedByDescending { it.value.size }, key = { "wa${it.key}" }) { (pid, list) ->
        val p = lg.playerOrNull(pid)
        AwardBlock(vm, lg, pid, list.first().name, p, list.map { Awards.badge(it.award) }, list.first().line)
    }
    // team stats
    item {
        val tot = t.totals
        val gp = t.gp.coerceAtLeast(1).toDouble()
        SectionLabel("Team Stats")
        StatGrid(
            listOf(
                Triple("PPG", f1(tot.pts / gp), C.text), Triple("Opp PPG", f1(t.opp.pts / gp), C.text),
                Triple("Margin", "%+.1f".format((tot.pts - t.opp.pts) / gp), if (tot.pts >= t.opp.pts) C.green else C.red),
                Triple("FG%", pct(tot.fgPct), C.text), Triple("3P%", pct(tot.tpPct), C.text), Triple("FT%", pct(tot.ftPct), C.text),
                Triple("RPG", f1(tot.reb / gp), C.text), Triple("APG", f1(tot.ast / gp), C.text), Triple("TOV", f1(tot.tov / gp), C.text),
            ),
            columns = 3, size = 22.sp,
        )
    }
    // notable regular season games
    val reg = lg.games.filter { it.played && it.involves(t.id) && (it.type == GameType.CONF || it.type == GameType.NONCONF || it.type == GameType.EVENT) }
    val wins = reg.filter { it.winner() == t.id }
    val losses = reg.filter { it.winner() != t.id }
    val best = wins.minByOrNull { lg.team(it.opp(t.id)).net }
    val worst = losses.maxByOrNull { lg.team(it.opp(t.id)).net }
    val closest = reg.minByOrNull { kotlin.math.abs(it.hs - it.aws) }
    item { SectionLabel("Regular Season", action = "Full Results", onAction = { vm.push(Route.Schedule(t.id)) }) }
    listOfNotNull(best?.let { it to ("BEST WIN" to C.green) }, worst?.let { it to ("WORST L" to C.red) }, closest?.let { it to ("CLOSEST" to C.green) }).forEach { (g, tag) ->
        item { GameLine(vm, lg, g, t.id, gameTypeShort(g).take(4).uppercase(), tag) }
    }
    // in-season event
    val ev = lg.events.firstOrNull { t.id in it.teams }
    if (ev != null && ev.placement.isNotEmpty()) {
        item {
            SectionLabel("In-Season Tournament")
            val place = ev.placement.indexOf(t.id) + 1
            Text(ev.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = Gutter))
            Text(if (place == 1) "Champions" else "${ordinal(place)} place", color = if (place == 1) C.yellow else C.sub, fontSize = 17.sp, modifier = Modifier.padding(horizontal = Gutter))
        }
        val evGames = ev.gameIds.map { lg.game(it) }.filter { it.involves(t.id) }
        evGames.forEachIndexed { i, g -> item { GameLine(vm, lg, g, t.id, if (i == 2) "F" else "R${i + 1}", null) } }
    }
    // postseason
    val ncaa = lg.games.filter { it.type == GameType.NCAA && it.involves(t.id) && it.played }.sortedBy { it.day }
    val ct = lg.games.filter { it.type == GameType.CONF_TOURNEY && it.involves(t.id) && it.played }.sortedBy { it.day }
    item {
        SectionLabel("Postseason")
        Text("NCAA Tournament", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = Gutter))
        Text(w.result, color = C.orangeText, fontSize = 17.sp, modifier = Modifier.padding(horizontal = Gutter))
    }
    ncaa.forEachIndexed { i, g -> item { GameLine(vm, lg, g, t.id, com.nh.hoopscoach.game.Season.NCAA_ROUNDS.getOrElse(i) { "" }, null, seed = lg.ncaa?.seeds?.get(g.opp(t.id))) } }
    if (ct.isNotEmpty()) {
        item {
            Spacer(Modifier.height(10.dp))
            Text("${lg.conf(t.confId).abbr} Tournament", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = Gutter))
            Text(w.confTourney, color = if (w.confTourney == "Champions") C.yellow else C.sub, fontSize = 17.sp, modifier = Modifier.padding(horizontal = Gutter))
        }
        val b = lg.confBrackets.firstOrNull { it.confId == t.confId }
        ct.forEach { g -> item { GameLine(vm, lg, g, t.id, g.label.substringAfter("· ").let { if (it == "Final") "F" else it }, null, seed = b?.seeds?.get(g.opp(t.id))) } }
    }
    // roster
    item {
        SectionLabel("Roster")
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Spacer(Modifier.weight(1f))
            Text("PTS", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.width(52.dp))
            Text("OVR", style = HoopsType.labelMedium, color = C.sub, modifier = Modifier.width(48.dp))
        }
    }
    items(lg.rosterOf(t).sortedByDescending { it.stats.pts }, key = { "wr${it.id}" }) { p ->
        Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p, lg, 50.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(p.name, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text("  ${p.pos} · ${p.cls}", color = C.sub, fontSize = 13.sp)
                }
                Text(Awards.line(p), color = C.sub, fontSize = 13.sp)
            }
            Text("${p.stats.pts}", fontSize = 16.sp, modifier = Modifier.width(52.dp))
            RatingNum(p.ovr, 24.sp, Modifier.width(48.dp))
        }
    }
}

fun ordinal(n: Int): String = "$n" + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

@Composable
private fun GameLine(vm: GameViewModel, lg: League, g: Game, me: Int, tag: String, badge: Pair<String, Color>?, seed: Int? = null) {
    val opp = lg.team(g.opp(me))
    val won = g.winner() == me
    Row(Modifier.fillMaxWidth().clickable { vm.push(Route.Box(g.id)) }.padding(horizontal = Gutter, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(tag, color = C.sub, fontSize = 13.sp, modifier = Modifier.width(48.dp), maxLines = 1)
        Text(if (won) "W" else "L", color = if (won) C.green else C.red, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.width(22.dp))
        Text("${g.scoreFor(me)}-${g.scoreAgainst(me)}", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(74.dp))
        TeamLogo(opp, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text((seed?.let { "#$it " } ?: if (opp.ap > 0) "#${opp.ap} " else "") + opp.school + " · " + g.site(me).let { if (it == "vs") "H" else if (it == "@") "A" else "N" },
            color = C.sub, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (badge != null) Pill(badge.first, badge.second)
        Chevron()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AwardBlock(vm: GameViewModel, lg: League, pid: Int?, name: String, p: com.nh.hoopscoach.game.Player?, badges: List<String>, line: String) {
    Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { pid?.let { vm.openPlayer(it) } }.padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        if (p != null) PlayerAvatar(p, lg, 52.dp) else Avatar(lg.userCoach.look, Color.Unspecified, null, 52.dp, coach = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (p != null) Text("  ${p.pos} · ${p.cls}", color = C.sub, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                badges.forEachIndexed { i, b -> Pill("🏆 $b", if (i == 0 || b.contains("MVP") || b.contains("AA 1st") || b.contains("COTY") || b.contains("Coach")) C.yellow else C.text, bg = if (i == 0) Color(0xFF3B3216) else C.card2, size = 13.sp) }
            }
            if (line.isNotEmpty()) Text(line, color = C.sub, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (p != null) RatingNum(p.ovr, 26.sp)
    }
}

// ---------- page 2: awards ----------

private fun LazyListScope.awardsPage(vm: GameViewModel, lg: League) {
    item {
        ScreenTitle("Season Awards") { Text("${lg.season - 1}–${lg.season % 100}", color = C.dim, fontSize = 16.sp) }
        Segmented(listOf("National", lg.conf(lg.user.confId).abbr), vm.leagueScope.coerceIn(0, 1), { vm.leagueScope = it })
        Spacer(Modifier.height(10.dp))
    }
    val national = vm.leagueScope == 0
    val list = if (national) lg.seasonAwards else lg.confAwards.filter { lg.team(it.teamId).confId == lg.user.confId }
    val headline = list.firstOrNull { it.award == if (national) "Nat MVP" else "Conf POY" }
    if (headline != null) item {
        val p = lg.playerOrNull(headline.pid)
        Outlined(Modifier.fillMaxWidth().padding(horizontal = Gutter), color = C.orange) {
            Text("🏆 " + if (national) "PLAYER OF THE YEAR" else "${lg.conf(lg.user.confId).abbr} PLAYER OF THE YEAR", color = C.yellow, style = HoopsType.labelLarge, letterSpacing = 1.sp)
            Spacer(Modifier.height(10.dp))
            AwardPerson(vm, lg, headline, big = true)
            if (p != null) {
                Spacer(Modifier.height(8.dp))
                Row { MiniStat(f1(p.stats.ppg), "PPG"); Spacer(Modifier.weight(1f)); MiniStat(f1(p.stats.rpg), "RPG"); Spacer(Modifier.weight(1f)); MiniStat(f1(p.stats.apg), "APG") }
            }
        }
    }
    val singles = if (national) listOf("MOP" to "Most Outstanding Player", "DPOY" to "Defensive Player of the Year", "FOY" to "Freshman of the Year", "COTY" to "Coach of the Year")
    else listOf("Conf DPOY" to "Defensive Player of the Year", "Conf FOY" to "Freshman of the Year", "Conf COTY" to "Coach of the Year")
    item {
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = PaddingValues(0.dp)) {
            singles.forEach { (code, title) ->
                list.firstOrNull { it.award == code }?.let { a ->
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text(title.uppercase(), color = C.orangeText, style = HoopsType.labelMedium, letterSpacing = 1.sp)
                        AwardPerson(vm, lg, a)
                    }
                    Divider()
                }
            }
            if (national) list.filter { it.award == "Pos OY" }.forEach { a ->
                Column(Modifier.fillMaxWidth().padding(14.dp)) {
                    Text("BEST ${lg.playerOrNull(a.pid)?.pos?.name ?: ""}".uppercase(), color = C.orangeText, style = HoopsType.labelMedium, letterSpacing = 1.sp)
                    AwardPerson(vm, lg, a)
                }
                Divider()
            }
        }
    }
    val teams = if (national) listOf("AA 1st" to "All-American First Team", "AA 2nd" to "All-American Second Team", "AA 3rd" to "All-American Third Team", "All-Tourn" to "All-Tournament Team")
    else listOf("All-Conf 1st" to "All-Conference First Team")
    teams.forEach { (code, title) ->
        val members = list.filter { it.award == code }
        if (members.isNotEmpty()) {
            item { SectionLabel(title) }
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = Gutter), padding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                    members.forEach { a -> Box(Modifier.padding(vertical = 8.dp)) { AwardPerson(vm, lg, a) } }
                }
            }
        }
    }
    if (list.isEmpty()) item { EmptyNote("Awards are announced at the end of the season.") }
}

@Composable
private fun AwardPerson(vm: GameViewModel, lg: League, a: AwardWinner, big: Boolean = false) {
    val t = lg.team(a.teamId)
    val mine = a.teamId == lg.userTeamId
    val p = if (a.pid > 0) lg.playerOrNull(a.pid) else null
    Row(Modifier.fillMaxWidth().clickable(enabled = p != null) { vm.openPlayer(a.pid) }, verticalAlignment = Alignment.CenterVertically) {
        if (p != null) PlayerAvatar(p, lg, if (big) 60.dp else 48.dp)
        else Avatar(lg.coaches.firstOrNull { it.id == -a.pid }?.look ?: 1, Color.Unspecified, null, if (big) 60.dp else 48.dp, coach = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.name, fontSize = if (big) 20.sp else 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (mine) { Spacer(Modifier.width(8.dp)); Pill("MY TEAM", C.orangeText) }
            }
            Text(t.school + (p?.let { " · ${it.cls} · ${it.pos}" } ?: " · ${a.line}"), color = C.sub, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (p != null) RatingNum(p.ovr, if (big) 30.sp else 22.sp)
    }
}

// ---------- page 3: graduations ----------

private fun LazyListScope.graduationsPage(vm: GameViewModel, lg: League) {
    val seniors = lg.rosterOf(lg.user).filter { it.year >= 4 }.sortedByDescending { it.ovr }
    val board = Offseason.draftBoard(lg)
    item {
        ScreenTitle("Graduations")
        Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(40.dp)) {
            Column { Text("${seniors.size}", fontSize = 30.sp, fontWeight = FontWeight.Bold); Text("LEAVING", style = HoopsType.labelMedium, color = C.sub) }
            Column { Text(money(seniors.sumOf { it.nil }), fontSize = 30.sp, fontWeight = FontWeight.Bold, color = C.green); Text("FREED FROM NIL", style = HoopsType.labelMedium, color = C.sub) }
        }
        Spacer(Modifier.height(16.dp))
    }
    if (seniors.isEmpty()) item { EmptyNote("No seniors on the roster this year.") }
    items(seniors, key = { "g${it.id}" }) { p ->
        Row(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            PlayerAvatar(p, lg, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(p.short, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(" (${money(p.nil)})", color = C.green, fontSize = 14.sp)
                }
                Text(Awards.line(p), color = C.sub, fontSize = 14.sp)
                Text(Offseason.gradNote(board[p.id]), color = C.sub, fontSize = 14.sp, fontStyle = FontStyle.Italic)
            }
            Text(p.pos.name, color = C.sub, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 8.dp))
            RatingNum(p.ovr, 28.sp)
        }
        Divider(Modifier.padding(horizontal = Gutter))
    }
}

// ---------- page 4: program standing ----------

private fun LazyListScope.standingPage(vm: GameViewModel, lg: League, w: WrapUp) {
    val t = lg.team(w.teamId)
    item {
        ScreenTitle("Program Standing")
        Text("Where the program sits after the year.", color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter))
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(t, 60.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(t.school, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                    Text(prestigeLabel(t.prestige).uppercase(), color = C.orangeText, style = HoopsType.labelLarge, letterSpacing = 1.sp)
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("${w.prestigeBefore} → ${w.prestige}", fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        Text("  %+d".format(w.prestigeDelta), color = if (w.prestigeDelta > 0) C.green else if (w.prestigeDelta < 0) C.red else C.sub, fontSize = 16.sp)
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Text("SEASON SIGNALS", style = HoopsType.labelMedium, color = C.sub)
            w.signals.forEach { s ->
                Spacer(Modifier.height(12.dp))
                Row {
                    Text(s.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(s.value, color = when { s.value == "Champions" -> C.yellow; s.good -> C.green; else -> C.red }, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(s.detail, color = C.sub, fontSize = 14.sp)
            }
            Spacer(Modifier.height(14.dp))
            Divider()
            Spacer(Modifier.height(12.dp))
            Text("WHY DID PRESTIGE CHANGE?", style = HoopsType.labelSmall, color = C.sub)
            Spacer(Modifier.height(6.dp))
            Text(prestigeReason(w), fontSize = 16.sp, color = C.text)
        }
        SectionLabel("National Movers")
    }
    val movers = lg.teams.sortedByDescending { it.prestige - it.prevPrestige }
    item {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoverColumn(vm, lg, "Top Risers", movers.take(5), Modifier.weight(1f))
            MoverColumn(vm, lg, "Top Fallers", movers.takeLast(5).reversed(), Modifier.weight(1f))
        }
    }
}

private fun prestigeReason(w: WrapUp): String = when {
    w.prestigeDelta >= 3 -> "A ${w.w}-${w.l} season and a ${w.result.lowercase()} finish pushed the program up. Keep winning in March to keep climbing."
    w.prestigeDelta > 0 -> "A solid year nudged the brand forward. Prestige moves slowly; sustained tournament runs are what really lift it."
    w.prestigeDelta == 0 && w.prestige >= 90 -> "The season confirmed the current tier, but expectations are sky-high here and the number needs title-level proof to move again."
    w.prestigeDelta == 0 -> "The program held steady. A deeper tournament run would start to move the needle."
    else -> "The program slipped after a ${w.w}-${w.l} season. Prestige follows wins and tournament runs over several years, so one good year can start the climb back."
}

@Composable
private fun MoverColumn(vm: GameViewModel, lg: League, title: String, teams: List<com.nh.hoopscoach.game.Team>, modifier: Modifier) {
    Column(modifier) {
        Text(title.uppercase(), style = HoopsType.labelMedium, color = C.sub)
        Spacer(Modifier.height(6.dp))
        teams.forEach { t ->
            val d = t.prestige - t.prevPrestige
            Row(Modifier.fillMaxWidth().clickable { vm.push(Route.TeamPage(t.id)) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TeamLogo(t, 24.dp)
                Spacer(Modifier.width(8.dp))
                Text(t.abbr, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1)
                Text("${t.prestige}", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                Text("%+d".format(d), color = if (d > 0) C.green else if (d < 0) C.red else C.sub, fontSize = 16.sp)
            }
        }
    }
}

// ---------- page 5: coach development ----------

private fun LazyListScope.coachPage(vm: GameViewModel, lg: League, w: WrapUp) {
    item {
        ScreenTitle("Coach Development")
        Text("Your reputation after the season, then how to spend the growth.", color = C.sub, fontSize = 15.sp, modifier = Modifier.padding(horizontal = Gutter))
        Spacer(Modifier.height(14.dp))
        val c = lg.userCoach
        val t = lg.team(w.teamId)
        Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(c.look, Color.Unspecified, null, 84.dp, coach = true)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text(t.school, color = C.sub, fontSize = 15.sp)
                    Text("Age ${c.age} · ${ordinal(c.history.size)} season", color = C.sub, fontSize = 14.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("RATING", style = HoopsType.labelMedium, color = C.sub)
                    Text("${c.rating}", fontSize = 40.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.height(12.dp))
            Card(color = C.card2) {
                Row {
                    StatCell("Record", "${w.w}-${w.l}", Modifier.weight(1f))
                    StatCell("Conference", "${w.cw}-${w.cl}", Modifier.weight(1f))
                    StatCell("Postseason", w.result.replace("National ", "").replace("Round of", "R"), Modifier.weight(1.3f), C.green, 18.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("REPUTATION DRIVERS", style = HoopsType.labelMedium, color = C.sub)
            w.drivers.forEach { d ->
                Spacer(Modifier.height(10.dp))
                Row {
                    Text(d.label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                    Text(d.value, color = if (d.good) C.green else C.red, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Text(d.detail, color = C.sub, fontSize = 14.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        CoachPointsCard(vm, lg, earned = w.coachPoints)
    }
}

@Composable
fun CoachPointsCard(vm: GameViewModel, lg: League, earned: Int = 0) {
    val c = lg.userCoach
    Card(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("COACHING POINTS", style = HoopsType.labelMedium, color = C.sub)
                if (earned > 0) Text("+$earned earned this season", color = C.green, fontSize = 13.sp)
            }
            Text("${c.points}", color = C.orangeText, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(" left", color = C.sub, fontSize = 15.sp)
        }
        Spacer(Modifier.height(10.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        Text("RATINGS", style = HoopsType.labelMedium, color = C.sub)
        listOf("Offense" to c.off, "Defense" to c.def, "Recruiting" to c.rec, "Development" to c.dev).forEachIndexed { i, (label, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, fontSize = 17.sp)
                Text("  (${Coaching.cost(i)} pts)", color = C.sub, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text("$v", color = ratingColor(v), fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
                PlusButton(enabled = c.points >= Coaching.cost(i) && v < 99) { vm.act { Coaching.buyRating(it.userCoach, i) } }
            }
        }
        Spacer(Modifier.height(10.dp))
        Divider()
        Spacer(Modifier.height(10.dp))
        Text("ACTIVE SKILLS · ${Coaching.SKILL_COST} pts, permanent", style = HoopsType.labelMedium, color = C.sub)
        CoachSkill.entries.forEach { s ->
            val owned = c.has(s)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(12.dp)).background(C.card2).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(s.blurb, color = C.sub, fontSize = 14.sp)
                }
                if (owned) Text("✓ Owned", color = C.sub, fontSize = 16.sp)
                else SmallButton("${Coaching.SKILL_COST} pts", enabled = c.points >= Coaching.SKILL_COST) { vm.act { Coaching.buySkill(it.userCoach, s) } }
            }
        }
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The 15 pages of the issue: section kicker and headline. */
private fun pages(lg: League) = listOf(
    "Cover" to "Preseason Preview",
    "Inside" to "In this issue",
    "The rankings" to "Top 25: Nos. 1-5",
    "The rankings" to "Top 25: Nos. 6-25",
    "National honors" to "Preseason All-America",
    "Recruiting" to "The freshman class",
    "Transfer portal" to "New faces, last calls",
    "Players to watch" to "Breakouts & sleepers",
    "Conference call" to "${lg.conf(lg.user.confId).name} in depth",
    "Conference call" to "The power conferences",
    "Around the country" to "Mid-major roundup",
    "Team report" to lg.user.school,
    "The sidelines" to "Coaches",
    "Mark your calendar" to "Games to watch",
    "The picks" to "Our predictions",
)

private fun topTeam(lg: League, pv: Preview): Team = lg.team(pv.proj.entries.first { it.value.nat == 1 }.key)
private fun ranked(lg: League, pv: Preview): List<Team> = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }
private fun players(lg: League, ids: List<Int>): List<Player> = ids.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId >= 0 }

@Composable
fun PreviewScreen(vm: GameViewModel, lg: League) {
    val pv = remember(lg, lg.season) { Previews.current(lg) }
    val list = pages(lg)
    val pager = rememberPagerState { list.size }
    val scope = rememberCoroutineScope()
    val go = { i: Int -> scope.launch { pager.animateScrollToPage(i.coerceIn(0, list.lastIndex)) }; Unit }
    Column(Modifier.fillMaxSize()) {
        BackBar("Preview ${pv.season}", "Page ${pager.currentPage + 1} of ${list.size} · ${list[pager.currentPage].second}", onBack = { vm.back() })
        // Thumb index: one tick per page.
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            list.indices.forEach { i ->
                Box(
                    Modifier.weight(1f).height(if (i == pager.currentPage) 6.dp else 4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (i <= pager.currentPage) P.accent else P.line).clickable { go(i) },
                )
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    // A light page-turn: pages swing around the edge they share.
                    val o = offset.coerceIn(-1f, 1f)
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
                    rotationY = -o * 28f
                    alpha = 1f - abs(o) * 0.35f
                },
            ) {
                LazyColumn(Modifier.fillMaxSize().background(P.bg)) {
                    val (kicker, title) = list[page]
                    if (page > 0) item { PageHead(kicker, title) }
                    when (page) {
                        0 -> cover(lg, pv) { go(1) }
                        1 -> contents(lg, pv, list, go)
                        2 -> topFive(vm, lg, pv)
                        3 -> restOfTop25(vm, lg, pv)
                        4 -> allAmerica(vm, lg, pv)
                        5 -> freshmen(vm, lg, pv)
                        6 -> transfers(vm, lg, pv)
                        7 -> breakouts(vm, lg, pv)
                        8 -> item { ConferenceDepth(vm, lg, pv, lg.user.confId) }
                        9 -> powerConferences(vm, lg, pv)
                        10 -> midMajors(vm, lg, pv)
                        11 -> teamReport(vm, lg, pv)
                        12 -> coaches(lg, pv)
                        13 -> gamesToWatch(vm, lg, pv)
                        else -> predictions(vm, lg, pv)
                    }
                    item {
                        Text(
                            "HOOPS DYNASTY PREVIEW ${pv.season} · ${page + 1}",
                            style = T.label.copy(fontSize = 9.sp), color = P.faint, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(P.bg).padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            SmallButton("‹ Prev", enabled = pager.currentPage > 0) { go(pager.currentPage - 1) }
            Text("${pager.currentPage + 1} / ${list.size}", style = T.numSm, color = P.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (pager.currentPage < list.lastIndex) SmallButton("Next ›", primary = true) { go(pager.currentPage + 1) }
            else SmallButton("Close", primary = true) { vm.back() }
        }
    }
}

@Composable
private fun PageHead(kicker: String, title: String) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter, top = 10.dp, bottom = 4.dp)) {
        Box(Modifier.background(P.accent).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(kicker.uppercase(), style = T.label, color = P.onAccent) }
        Spacer(Modifier.height(6.dp))
        Text(title.uppercase(), style = T.title.copy(fontSize = 32.sp, lineHeight = 34.sp), color = P.ink)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(P.ink))
    }
}

@Composable
private fun Deck(text: String) {
    Text(text, style = T.body.copy(fontStyle = FontStyle.Italic), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 8.dp, bottom = 4.dp))
}

private fun lastLine(p: Player): String {
    val l = p.career.lastOrNull() ?: return ""
    return " · %.1f ppg last season".format(l.s.ppg)
}

// ---------- 1: cover ----------

private fun LazyListScope.cover(lg: League, pv: Preview, onRead: () -> Unit) {
    item {
        val top = topTeam(lg, pv)
        val star = Previews.star(lg, top)
        val primary = Color(top.primary)
        val secondary = Color(top.secondary)
        val fg = onColor(primary)
        val accent = if (contrast(secondary, primary) >= 2.2f) secondary else fg
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        val aa = pv.allAmerica.firstOrNull()?.let { lg.playerOrNull(it) }
        val r = ranked(lg, pv)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(primary, lerp(primary, Color.Black, 0.45f))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOOPS DYNASTY", style = T.label, color = fg.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text("COLLEGE BASKETBALL ${pv.season}", style = T.label, color = fg.copy(alpha = 0.85f))
            }
            Text("PREVIEW", style = T.hero.copy(fontSize = 68.sp, lineHeight = 64.sp, letterSpacing = 2.sp), color = fg)
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
            Spacer(Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (star != null) Jersey(star.number, secondary, primary, 112.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("NO. 1", style = T.label, color = accent)
                    Text(top.school.uppercase(), style = T.title.copy(fontSize = 34.sp, lineHeight = 34.sp), color = fg)
                    Text(Previews.coverDek(lg, top), style = T.bodyB, color = fg)
                    if (star != null) Text("${star.name} · ${star.pos} · ${star.cls}", style = T.small, color = fg.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(18.dp))
            CoverLine("Top 25", "${r[1].school} and ${r[2].school} chase ${r[0].school}", fg, accent)
            if (aa != null) CoverLine("All-America", "${aa.name} headlines our first team", fg, accent)
            CoverLine("Team report", "${u.school}: picked ${Previews.ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}, No. ${mine.nat} nationally", fg, accent)
            CoverLine("Plus", "All 32 conferences, the freshman class, the portal, breakouts and our Final Four", fg, accent)
            Spacer(Modifier.height(12.dp))
            Text("15 pages · swipe to turn →", style = T.bodyB, color = accent, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun CoverLine(kicker: String, text: String, fg: Color, accent: Color) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(kicker.uppercase(), style = T.label.copy(fontSize = 10.sp), color = accent)
        Text(text, style = T.bodyB, color = fg)
    }
}

// ---------- 2: contents ----------

private fun LazyListScope.contents(lg: League, pv: Preview, list: List<Pair<String, String>>, go: (Int) -> Unit) {
    item {
        Section("From the editor")
        Panel { Previews.editorsLetter(lg, pv).forEach { Text(it, style = T.body, color = P.ink, modifier = Modifier.padding(vertical = 4.dp)) } }
        Section("Contents")
        GroupList(list.withIndex().drop(2).toList()) { (i, pg) ->
            ListRow(onClick = { go(i) }) {
                Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(34.dp))
                Column(Modifier.weight(1f)) {
                    Text(pg.second, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(pg.first, style = T.small, color = P.sub)
                }
                Chevron()
            }
        }
    }
}

// ---------- 3-4: top 25 ----------

private fun LazyListScope.topFive(vm: GameViewModel, lg: League, pv: Preview) {
    item { Deck("The five teams with the best shot at cutting down the nets.") }
    ranked(lg, pv).take(5).forEach { t ->
        item {
            val pr = pv.proj.getValue(t.id)
            val star = Previews.star(lg, t)
            val (strength, concern) = Previews.scoutingProse(lg, t)
            Spacer(Modifier.height(10.dp))
            Panel(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pr.nat}", style = T.hero.copy(fontSize = 54.sp, lineHeight = 54.sp), color = P.accent, modifier = Modifier.width(52.dp))
                    TeamBadge(t, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school.uppercase(), style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${t.mascot} · ${lg.conf(t.confId).abbr} · projected ${pr.w}-${pr.l} · ${Previews.expectation(pr.nat)}", style = T.small, color = P.sub, maxLines = 2)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(Previews.blurb(lg, t), style = T.body, color = P.ink)
                if (star != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlayerJersey(star, t, 40.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("STAR", style = T.label.copy(fontSize = 9.sp), color = P.accent)
                            Text("${star.name} · ${star.pos} · ${star.cls} · ${star.style.label}", style = T.bodyB, color = P.ink)
                        }
                        Ring(star.ovr, 38.dp, textSize = 14.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(strength, style = T.small, color = P.good)
                Text(concern, style = T.small, color = P.warn)
            }
        }
    }
}

private fun LazyListScope.restOfTop25(vm: GameViewModel, lg: League, pv: Preview) {
    val r = ranked(lg, pv)
    item {
        Spacer(Modifier.height(8.dp))
        GroupList(r.subList(5, 25)) { t -> PreviewTeamRow(vm, lg, t, "${pv.proj.getValue(t.id).nat}", "Proj. ${pv.proj.getValue(t.id).w}-${pv.proj.getValue(t.id).l}", Previews.blurb(lg, t)) }
        Section("Also receiving votes")
        Text(r.subList(25, 35).joinToString(", ") { it.school }, style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        if (mine.nat > 25) {
            Section("Your team")
            GroupList(listOf(u)) { t -> PreviewTeamRow(vm, lg, t, "${mine.nat}", "Proj. ${mine.w}-${mine.l}", Previews.blurb(lg, t)) }
        }
    }
}

@Composable
private fun PreviewTeamRow(vm: GameViewModel, lg: League, t: Team, rank: String, right: String, blurb: String?) {
    val mine = t.id == lg.userTeamId
    Column(
        Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier)
            .clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(rank, style = T.num.copy(fontSize = 24.sp), color = if (mine) P.accent else P.ink, modifier = Modifier.width(40.dp))
            TeamBadge(t, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lg.conf(t.confId).abbr + if (t.gp > 0) " · now ${t.record}${if (t.ap > 0) ", AP #${t.ap}" else ""}" else "", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
            }
            Text(right, style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
        }
        if (!blurb.isNullOrEmpty()) Text(blurb, style = T.small, color = P.sub, modifier = Modifier.padding(start = 40.dp, top = 6.dp))
    }
}

// ---------- 5: all-america ----------

private fun LazyListScope.allAmerica(vm: GameViewModel, lg: League, pv: Preview) {
    val aa = players(lg, pv.allAmerica)
    item { Deck("The best players in the country, before a single game is played.") }
    listOf("First team" to aa.take(5), "Second team" to aa.drop(5).take(5), "Third team" to aa.drop(10).take(5)).forEach { (title, list) ->
        if (list.isNotEmpty()) item {
            Section(title)
            GroupList(list, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.pos} · ${p.cls}${lastLine(p)}") }
        }
    }
    item {
        Section("Award picks")
        val picks = listOfNotNull(
            aa.firstOrNull()?.let { "Player of the Year" to it },
            lg.playerOrNull(pv.dpoy)?.let { "Defensive Player of the Year" to it },
            players(lg, pv.freshmen).firstOrNull()?.let { "Freshman of the Year" to it },
        )
        GroupList(picks, inset = 70.dp) { (award, p) -> PlayerLine(vm, lg, lg.team(p.teamId), p, "$award · ${lg.team(p.teamId).school}") }
    }
}

// ---------- 6: freshmen ----------

private fun LazyListScope.freshmen(vm: GameViewModel, lg: League, pv: Preview) {
    val classes = lg.teams.map { t ->
        val fr = lg.rosterOf(t).filter { it.year == 1 && it.recruitRank > 0 }
        Triple(t, fr, fr.sumOf { it.stars * it.stars })
    }.filter { it.second.isNotEmpty() }.sortedByDescending { it.third }.take(8)
    item {
        Deck("Who won recruiting, and the freshmen who could start from day one.")
        Section("Top recruiting classes")
        GroupList(classes.withIndex().toList()) { (i, c) ->
            val (t, fr, _) = c
            val five = fr.count { it.stars == 5 }
            val four = fr.count { it.stars == 4 }
            ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (t.id == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier) {
                Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(30.dp))
                TeamBadge(t, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.school, style = T.bodyB, color = P.ink)
                    Text(listOfNotNull(if (five > 0) "$five five-star" else null, if (four > 0) "$four four-star" else null, "${fr.size} signees").joinToString(" · "), style = T.small, color = P.sub)
                }
                Text(fr.maxBy { it.ovr }.last, style = T.small, color = P.sub)
            }
        }
        Section("Top 10 freshmen")
        GroupList(players(lg, pv.freshmen), inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${p.stars}★ · No. ${p.recruitRank} recruit · ${lg.team(p.teamId).school}") }
    }
}

// ---------- 7: transfers ----------

private fun LazyListScope.transfers(vm: GameViewModel, lg: League, pv: Preview) {
    val list = players(lg, pv.transfers)
    val winners = lg.teams.map { t -> t to lg.rosterOf(t).filter { p -> p.career.isNotEmpty() && p.career.last().teamId >= 0 && p.career.last().teamId != t.id } }
        .filter { it.second.isNotEmpty() }.sortedByDescending { (_, ps) -> ps.sumOf { it.ovr } + ps.maxOf { it.ovr } * 2 }.take(5)
    item {
        Deck("The portal moves that matter most, and the seniors taking their last ride.")
        Section("Impact transfers")
        if (list.isEmpty()) Panel { Text("A quiet portal: no impact transfers moved this offseason, so rosters stayed together.", style = T.body, color = P.sub) }
        GroupList(list, inset = 70.dp) { p ->
            val from = p.career.last().teamId.takeIf { it >= 0 }?.let { lg.team(it).school } ?: p.career.last().team
            PlayerLine(vm, lg, lg.team(p.teamId), p, "$from → ${lg.team(p.teamId).school} · ${p.pos}${lastLine(p)}")
        }
        if (winners.isNotEmpty()) {
            Section("Portal winners")
            GroupList(winners) { (t, ps) ->
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school, style = T.bodyB, color = P.ink)
                        Text(ps.sortedByDescending { it.ovr }.take(3).joinToString(", ") { "${it.last} (${it.ovr})" }, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val seniors = lg.teams.flatMap { lg.rosterOf(it) }.filter { it.year >= 4 && !it.walkOn }.sortedByDescending { it.ovr }.take(8)
        if (seniors.isNotEmpty()) {
            Section("Last call: top seniors")
            GroupList(seniors, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.pos} · final season${lastLine(p)}") }
        }
    }
}

// ---------- 8: breakouts ----------

private fun LazyListScope.breakouts(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("Next year's All-Americans, and stars hiding in plain sight.")
        Section("Breakout candidates")
        GroupList(players(lg, pv.breakouts), inset = 70.dp) { p ->
            PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.pos} · ${p.cls}${lastLine(p)}\n${Previews.breakoutLine(p)}")
        }
        Section("Under the radar")
        GroupList(players(lg, pv.underRadar), inset = 70.dp) { p ->
            PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} (${lg.conf(lg.team(p.teamId).confId).abbr}) · ${p.pos} · ${p.cls}${lastLine(p)}\n${Previews.sleeperLine(lg, p)}")
        }
    }
}

// ---------- 9-11: conferences ----------

@Composable
private fun ConferenceDepth(vm: GameViewModel, lg: League, pv: Preview, confId: Int) {
    val c = lg.conf(confId)
    val started = lg.teams.any { it.cw + it.cl > 0 }
    val now = if (started) Season.standings(lg, c.id).withIndex().associate { (i, t) -> t.id to i + 1 } else emptyMap()
    Deck("Predicted order of finish, with a word on every team.")
    pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.let { p ->
        Spacer(Modifier.height(8.dp))
        Panel(onClick = { vm.openPlayer(p.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, lg.team(p.teamId), 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PRESEASON PLAYER OF THE YEAR", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Text(p.name, style = T.head, color = P.ink)
                    Text("${lg.team(p.teamId).school} · ${p.pos} · ${p.cls}${lastLine(p)}", style = T.small, color = P.sub)
                }
                Ring(p.ovr, 44.dp)
            }
        }
    }
    Section("Predicted finish")
    val order = c.teamIds.map { lg.team(it) }.sortedBy { pv.proj.getValue(it.id).confRank }
    GroupList(order) { t ->
        val pr = pv.proj.getValue(t.id)
        val mine = t.id == lg.userTeamId
        val star = Previews.star(lg, t)
        Column(Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pr.confRank}", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(30.dp))
                TeamBadge(t, 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(t.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${pr.cw}-${pr.cl} · ${pr.w}-${pr.l}", style = T.numSm.copy(fontSize = 14.sp), color = P.ink)
                if (started) {
                    val n = now[t.id] ?: 0
                    Text("  now ${Previews.ordinal(n)}", style = T.small, color = when { n < pr.confRank -> P.good; n > pr.confRank -> P.bad; else -> P.sub })
                }
            }
            Text(Previews.confLine(lg, t, pr.confRank), style = T.small, color = P.sub, modifier = Modifier.padding(start = 66.dp, top = 2.dp))
        }
    }
    Text("Projected conference record · overall record.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
}

private fun LazyListScope.powerConferences(vm: GameViewModel, lg: League, pv: Preview) {
    item { Deck("Champion picks, contenders and the best player in each of the big leagues.") }
    lg.confs.filter { it.tier == 0 && it.id != lg.user.confId }.forEach { c ->
        item {
            val order = c.teamIds.map { lg.team(it) }.sortedBy { pv.proj.getValue(it.id).confRank }
            Section(c.name)
            GroupList(order.take(4)) { t ->
                val pr = pv.proj.getValue(t.id)
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    Text("${pr.confRank}", style = T.numSm, color = P.sub, modifier = Modifier.width(30.dp))
                    TeamBadge(t, 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(t.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    if (pr.confRank == 1) Tag("PICK", P.gold)
                    Spacer(Modifier.width(8.dp))
                    Text("${pr.cw}-${pr.cl}", style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
                }
            }
            pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.let { p ->
                Text("Player of the Year pick: ${p.name}, ${lg.team(p.teamId).school}", style = T.small, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
            }
        }
    }
}

private fun LazyListScope.midMajors(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("Every other league in one place: our pick to win it and the player to watch.")
        Spacer(Modifier.height(8.dp))
        GroupList(lg.confs.filter { it.tier >= 1 && it.id != lg.user.confId }) { c ->
            val pick = c.teamIds.map { lg.team(it) }.minBy { pv.proj.getValue(it.id).confRank }
            val poy = pv.confPoy[c.id]?.let { lg.playerOrNull(it) }
            ListRow(onClick = { vm.push(Route.TeamPage(pick.id)) }) {
                Text(c.abbr, style = T.numSm.copy(fontSize = 15.sp), color = P.accent, modifier = Modifier.width(58.dp))
                TeamBadge(pick, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pick.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (poy != null) Text("POY: ${poy.name}, ${lg.team(poy.teamId).abbr}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text("No. ${pv.proj.getValue(pick.id).nat}", style = T.small, color = P.sub)
            }
        }
    }
}

// ---------- 12: team report ----------

private fun LazyListScope.teamReport(vm: GameViewModel, lg: League, pv: Preview) {
    val u = lg.user
    val pr = pv.proj.getValue(u.id)
    val roster = lg.rosterOf(u)
    val starters = u.plan.slots.mapNotNull { s -> roster.firstOrNull { it.id == s.starter } }
    val bench = u.plan.slots.mapNotNull { s -> roster.firstOrNull { it.id == s.reserve } }
    val newcomers = roster.filter { Previews.isNewcomer(it) }.sortedByDescending { it.ovr }
    val (strength, concern) = Previews.scoutingProse(lg, u)
    item {
        Spacer(Modifier.height(8.dp))
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(u, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Previews.expectation(pr.nat).uppercase(), style = T.label, color = P.accent)
                    Text("${u.school} ${u.mascot}", style = T.head, color = P.ink)
                    Text("Coach ${lg.userCoach.name}", style = T.small, color = P.sub)
                }
            }
            Spacer(Modifier.height(12.dp))
            StatRow(listOf(Triple("National", "No. ${pr.nat}", null), Triple(lg.conf(u.confId).abbr, Previews.ordinal(pr.confRank), null), Triple("Projected", "${pr.w}-${pr.l}", null)), style = T.numSm)
            if (u.gp > 0) {
                Spacer(Modifier.height(8.dp))
                val confPart = if (u.cw + u.cl > 0) ", ${Previews.ordinal(Season.standings(lg, u.confId).indexOf(u) + 1)} in the ${lg.conf(u.confId).abbr}" else ""
                Text("So far: ${u.record}${if (u.ap > 0) ", AP No. ${u.ap}" else ""}$confPart.", style = T.small, color = P.accent)
            }
        }
        Section("Projected starting five")
        GroupList(starters, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.pos} · ${p.cls}${lastLine(p)}") }
        if (bench.isNotEmpty()) {
            Section("Off the bench")
            GroupList(bench, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.pos} · ${p.cls} · ${p.style.label}") }
        }
        if (newcomers.isNotEmpty()) {
            Section("Newcomers")
            GroupList(newcomers.take(5), inset = 70.dp) { p ->
                val from = p.career.lastOrNull()?.teamId?.takeIf { it >= 0 && it != u.id }?.let { "Transfer from ${lg.team(it).school}" } ?: if (p.recruitRank > 0) "${p.stars}★ freshman · No. ${p.recruitRank}" else "Walk-on"
                PlayerLine(vm, lg, u, p, "${p.pos} · $from")
            }
        }
        Section("Scouting report")
        Panel {
            Text("STRENGTHS", style = T.label, color = P.good)
            Text(strength, style = T.body, color = P.ink)
            Spacer(Modifier.height(8.dp))
            Text("CONCERNS", style = T.label, color = P.warn)
            Text(concern, style = T.body, color = P.ink)
        }
        val circle = lg.games.filter { it.involves(u.id) && it.day <= com.nh.hoopscoach.game.Schedule.REG_DAYS }
            .filter { g -> lg.rivalry(g) != null || pv.proj.getValue(g.opp(u.id)).nat <= 25 }.sortedBy { it.day }.take(6)
        if (circle.isNotEmpty()) {
            Section("Circle these dates")
            GroupList(circle) { g ->
                val opp = lg.team(g.opp(u.id))
                ListRow(onClick = { vm.push(Route.TeamPage(opp.id)) }) {
                    Text("G${g.day}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(40.dp))
                    Text(g.site(u.id).let { if (it == "N") "vs" else it }, style = T.small, color = P.sub, modifier = Modifier.width(24.dp))
                    TeamBadge(opp, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(opp.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                    Text(lg.rivalry(g)?.title ?: "No. ${pv.proj.getValue(opp.id).nat}", style = T.small, color = if (lg.rivalry(g) != null) P.bad else P.sub)
                }
            }
        }
        Section("The bottom line")
        Panel { Text("${Previews.blurb(lg, u)} We see a ${pr.w}-${pr.l} team that finishes ${Previews.ordinal(pr.confRank)} in the ${lg.conf(u.confId).abbr}. ${Previews.expectation(pr.nat)}.", style = T.body, color = P.ink) }
    }
}

// ---------- 13: coaches ----------

private fun LazyListScope.coaches(lg: League, pv: Preview) {
    item {
        Deck("The best in the business, and the men who need a big year.")
        Section("Top 10 coaches")
        GroupList(lg.coaches.filter { it.teamId >= 0 }.sortedByDescending { it.rating }.take(10).withIndex().toList()) { (i, c) ->
            val t = lg.team(c.teamId)
            ListRow(modifier = if (c.isUser) Modifier.background(P.accentSoft) else Modifier) {
                Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(30.dp))
                CoachBadge(c.first, c.last, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(c.name, style = T.bodyB, color = P.ink)
                    Text("${t.school} · ${c.w}-${c.l} career${if (c.titles > 0) " · ${c.titles} title${if (c.titles > 1) "s" else ""}" else ""}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Ring(c.rating, 36.dp, textSize = 13.sp)
            }
        }
        if (pv.hotSeat.isNotEmpty()) {
            Section("On the hot seat")
            GroupList(pv.hotSeat.map { lg.team(it) }) { t ->
                val c = lg.coachOf(t)
                val last = t.history.lastOrNull()
                ListRow {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${c.name}, ${t.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (last != null) Text("${last.w}-${last.l} and ${last.result.lowercase()} last season", style = T.small, color = P.sub)
                    }
                    Tag("HOT SEAT", P.bad)
                }
            }
        }
        val me = lg.userCoach
        Section("Your coach")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoachBadge(me.first, me.last, 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.name, style = T.head, color = P.ink)
                    Text("${me.w}-${me.l} career · ${me.tourneys} NCAA trips · job security ${me.security}/100", style = T.small, color = P.sub)
                }
                Ring(me.rating, 44.dp)
            }
        }
        lg.teams.getOrNull(pv.cotyTeam)?.let { t ->
            Text("Our Coach of the Year pick: ${lg.coachOf(t).name}, ${t.school}.", style = T.small, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
        }
    }
}

// ---------- 14: games to watch ----------

private fun LazyListScope.gamesToWatch(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("Early-season tournaments and the regular-season games worth staying up for.")
        if (lg.events.isNotEmpty()) {
            Section("Early-season tournaments")
            GroupList(lg.events) { ev ->
                val field = ev.teams.map { lg.team(it) }.sortedBy { pv.proj.getValue(it.id).nat }
                val winner = ev.placement.firstOrNull()?.let { lg.team(it) }
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(ev.name, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                        if (winner != null) Tag("WON BY ${winner.abbr}", P.good) else Tag("PICK: ${field.first().abbr}", P.gold)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { field.forEach { TeamBadge(it, 26.dp) } }
                }
            }
        }
        val games = pv.marquee.mapNotNull { id -> lg.games.firstOrNull { it.id == id } }
        if (games.isNotEmpty()) {
            Section("Circle these games")
            GroupList(games) { g ->
                val h = lg.team(g.home)
                val a = lg.team(g.away)
                val riv = lg.rivalry(g)
                ListRow(onClick = if (g.played) ({ vm.push(Route.Box(g.id)) }) else null) {
                    Text("G${g.day}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${a.school} ${if (g.neutral) "vs" else "at"} ${h.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(riv?.let { if (it.name.isEmpty()) "Rivalry game" else "Rivalry · ${it.name}" } ?: "No. ${pv.proj.getValue(a.id).nat} vs No. ${pv.proj.getValue(h.id).nat}", style = T.small, color = if (riv != null) P.bad else P.sub)
                    }
                    if (g.played) Text("${g.aws}-${g.hs}", style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
                    else Row { TeamBadge(a, 22.dp); Spacer(Modifier.width(4.dp)); TeamBadge(h, 22.dp) }
                }
            }
        }
    }
}

// ---------- 15: predictions ----------

private fun LazyListScope.predictions(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        if (lg.user.gp > 0) {
            Section("Scorecard so far")
            val top25 = ranked(lg, pv).take(25)
            val onPace = top25.count { t -> t.w + t.l >= 5 && t.w.toDouble() / t.gp >= pv.proj.getValue(t.id).w / (pv.proj.getValue(t.id).w + pv.proj.getValue(t.id).l).toDouble() - 0.08 }
            val f4Alive = pv.finalFour.count { id ->
                val t = lg.team(id)
                t.l <= (t.w + t.l) / 2 + 2
            }
            val u = lg.user
            val mine = pv.proj.getValue(u.id)
            Panel {
                Text(
                    "Of our preseason Top 25, $onPace are roughly on their projected win pace. $f4Alive of our Final Four picks still look like tournament teams.",
                    style = T.body, color = P.ink,
                )
                Spacer(Modifier.height(8.dp))
                val delta = u.w - (mine.w * u.gp / (mine.w + mine.l).coerceAtLeast(1).toDouble()).roundToInt()
                Text(
                    when {
                        u.gp < 5 -> "${u.school} is ${u.record} — too early to grade the projection."
                        delta >= 2 -> "${u.school} is beating the projection (${u.record} vs a ${mine.w}-${mine.l} pace)."
                        delta <= -2 -> "${u.school} is behind the projection (${u.record}; we had them at ${mine.w}-${mine.l})."
                        else -> "${u.school} is right about where we picked them (${u.record}, projected ${mine.w}-${mine.l})."
                    },
                    style = T.small, color = P.sub,
                )
            }
        }
        Section("Final Four")
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                pv.finalFour.forEachIndexed { i, id ->
                    val t = lg.team(id)
                    Column(Modifier.weight(1f).clickable { vm.push(Route.TeamPage(id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamBadge(t, 46.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(t.abbr, style = T.bodyB, color = P.ink)
                        if (i == 0) Tag("CHAMPION", P.gold) else Text(" ", style = T.label)
                    }
                }
            }
        }
        Section("Long shots")
        GroupList(listOf("Cinderella" to lg.team(pv.cinderella), "Sleeper" to lg.team(pv.sleeper))) { (label, t) ->
            ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                Text(label.uppercase(), style = T.label, color = P.accent, modifier = Modifier.width(92.dp))
                TeamBadge(t, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.school, style = T.bodyB, color = P.ink)
                    Text(
                        if (label == "Cinderella") "Mid-major path to a deep March run · No. ${pv.proj.getValue(t.id).nat}"
                        else "Quietly built for a top-25 leap · No. ${pv.proj.getValue(t.id).nat}",
                        style = T.small, color = P.sub,
                    )
                }
            }
        }
        val aa = players(lg, pv.allAmerica)
        val awards = listOfNotNull(
            aa.firstOrNull()?.let { "Player of the Year" to "${it.name}, ${lg.team(it.teamId).school}" },
            players(lg, pv.freshmen).firstOrNull()?.let { "Freshman of the Year" to "${it.name}, ${lg.team(it.teamId).school}" },
            lg.playerOrNull(pv.dpoy)?.let { "Defensive POY" to "${it.name}, ${lg.team(it.teamId).school}" },
            lg.teams.getOrNull(pv.cotyTeam)?.let { "Coach of the Year" to "${lg.coachOf(it).name}, ${it.school}" },
        )
        Section("Award picks")
        GroupList(awards) { (k, v) ->
            ListRow {
                Text(k, style = T.small, color = P.accent, modifier = Modifier.width(120.dp))
                Text(v, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
            }
        }
        if (pv.bold.isNotEmpty()) {
            Section("Bold predictions")
            GroupList(pv.bold.withIndex().toList()) { (i, text) ->
                ListRow {
                    Text("${i + 1}", style = T.num.copy(fontSize = 22.sp), color = P.accent, modifier = Modifier.width(34.dp))
                    Text(text, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        Text("See you in March.", style = T.title.copy(fontSize = 26.sp), color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/** Today card: the new issue is out. */
@Composable
fun PreviewCard(vm: GameViewModel, lg: League) {
    val pv = lg.preview?.takeIf { it.season == lg.season } ?: return
    if (lg.user.gp >= 8) return
    val u = lg.user
    val pr = pv.proj[u.id] ?: return
    val top = topTeam(lg, pv)
    Section("Season preview")
    Panel(color = P.hero, onClick = { vm.push(Route.Preview) }) {
        Text("THE ${pv.season} PRESEASON ISSUE · 15 PAGES", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text("${u.school.uppercase()} PICKED ${Previews.ordinal(pr.confRank).uppercase()} IN THE ${lg.conf(u.confId).abbr}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onHero)
        Spacer(Modifier.height(4.dp))
        Text("No. ${pr.nat} nationally, projected ${pr.w}-${pr.l}. ${top.school} is the preseason No. 1. Plus every conference, the freshman class, the portal and our Final Four.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Text("Read the issue →", style = T.bodyB, color = P.onHero)
    }
    @Suppress("UNUSED_VARIABLE") val unused = GameType.NCAA to Ratings
}

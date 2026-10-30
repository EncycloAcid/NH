package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch

private val PAGES = listOf("Cover", "Top 25", "Conferences", "Players", "Picks")

private fun topTeam(lg: League, pv: Preview): Team = lg.team(pv.proj.entries.first { it.value.nat == 1 }.key)

@Composable
fun PreviewScreen(vm: GameViewModel, lg: League) {
    val pv = remember(lg, lg.season) { Previews.current(lg) }
    val pager = rememberPagerState { PAGES.size }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxSize()) {
        BackBar("Season Preview", "The ${pv.season} preseason issue", onBack = { vm.back() })
        Segmented(PAGES, pager.currentPage, { scope.launch { pager.animateScrollToPage(it) } }, scroll = true)
        Spacer(Modifier.height(6.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth()) { page ->
            LazyColumn(Modifier.fillMaxSize()) {
                when (page) {
                    0 -> cover(lg, pv) { scope.launch { pager.animateScrollToPage(1) } }
                    1 -> top25(vm, lg, pv)
                    2 -> item { Conferences(vm, lg, pv) }
                    3 -> playersToWatch(vm, lg, pv)
                    else -> picks(vm, lg, pv)
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
}

// ---------- cover ----------

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
        Column(
            Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(primary, lerp(primary, Color.Black, 0.45f))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOOPS DYNASTY", style = T.label, color = fg.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text("PRESEASON ${pv.season}", style = T.label, color = fg.copy(alpha = 0.85f))
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
                    Text("Is this ${top.school}${if (top.school.endsWith("s")) "'" else "'s"} year?", style = T.bodyB, color = fg)
                    if (star != null) Text("${star.name} · ${star.pos} · ${star.cls}", style = T.small, color = fg.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(18.dp))
            val ranked = pv.proj.entries.sortedBy { it.value.nat }.take(3).map { lg.team(it.key).school }
            CoverLine("Top 25", "${ranked[1]} and ${ranked[2]} chase ${ranked[0]}", fg, accent)
            if (aa != null) CoverLine("Players to watch", "${aa.name} leads the Preseason All-America team", fg, accent)
            CoverLine("Your team", "${u.school} picked ${ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}, No. ${mine.nat} nationally", fg, accent)
            CoverLine("Sleeper", "Don't sleep on ${lg.team(pv.sleeper).school}", fg, accent)
            Spacer(Modifier.height(12.dp))
            Text("Swipe to read →", style = T.bodyB, color = accent, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
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

// ---------- top 25 ----------

private fun LazyListScope.top25(vm: GameViewModel, lg: League, pv: Preview) {
    val ranked = pv.proj.entries.sortedBy { it.value.nat }
    item {
        Section("Preseason Top 25")
        Text(
            "Ranked by team strength. Projected records cover the regular season.",
            style = T.small, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, bottom = 8.dp),
        )
        GroupList(ranked.take(25)) { (id, pr) -> PreviewTeamRow(vm, lg, lg.team(id), "${pr.nat}", "Proj. ${pr.w}-${pr.l}", Previews.blurb(lg, lg.team(id))) }
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

// ---------- conferences ----------

@Composable
private fun Conferences(vm: GameViewModel, lg: League, pv: Preview) {
    var conf by remember { mutableIntStateOf(lg.user.confId) }
    val c = lg.conf(conf)
    val started = lg.teams.any { it.cw + it.cl > 0 }
    val now = if (started) Season.standings(lg, c.id).withIndex().associate { (i, t) -> t.id to i + 1 } else emptyMap()
    Spacer(Modifier.height(6.dp))
    Chips(lg.confs.map { it.abbr }, conf, { conf = it })
    Section("${c.name} · predicted finish")
    pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.let { p ->
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
        Spacer(Modifier.height(8.dp))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 14.dp, vertical = 4.dp)) {
        Text("TEAM", style = T.label, color = P.sub, modifier = Modifier.weight(1f))
        Text("CONF", style = T.label, color = P.sub, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
        Text("ALL", style = T.label, color = P.sub, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
        if (started) Text("NOW", style = T.label, color = P.sub, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
    }
    val order = c.teamIds.map { lg.team(it) }.sortedBy { pv.proj.getValue(it.id).confRank }
    GroupList(order) { t ->
        val pr = pv.proj.getValue(t.id)
        val mine = t.id == lg.userTeamId
        ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
            Text("${pr.confRank}", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(30.dp))
            TeamBadge(t, 26.dp)
            Spacer(Modifier.width(10.dp))
            Text(t.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pr.cw}-${pr.cl}", style = T.numSm.copy(fontSize = 15.sp), color = P.ink, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
            Text("${pr.w}-${pr.l}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(50.dp), textAlign = TextAlign.End)
            if (started) {
                val n = now[t.id] ?: 0
                Text(ordinal(n), style = T.numSm.copy(fontSize = 15.sp), color = when { n < pr.confRank -> P.good; n > pr.confRank -> P.bad; else -> P.sub }, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
            }
        }
    }
    if (started) Text("NOW is the current standings place: green is ahead of the prediction, red is behind.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
}

private fun lastLine(p: Player): String {
    val l = p.career.lastOrNull() ?: return ""
    return " · %.1f ppg last season".format(l.s.ppg)
}

// ---------- players to watch ----------

private fun LazyListScope.playersToWatch(vm: GameViewModel, lg: League, pv: Preview) {
    fun players(ids: List<Int>) = ids.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId >= 0 }
    val groups = listOf(
        Triple("Preseason All-America · First team", players(pv.allAmerica.take(5))) { p: Player -> "${lg.team(p.teamId).school} · ${p.pos} · ${p.cls}${lastLine(p)}" },
        Triple("Preseason All-America · Second team", players(pv.allAmerica.drop(5))) { p: Player -> "${lg.team(p.teamId).school} · ${p.pos} · ${p.cls}${lastLine(p)}" },
        Triple("Freshmen to watch", players(pv.freshmen)) { p: Player -> "${lg.team(p.teamId).school} · ${p.stars}★ · No. ${p.recruitRank} recruit · ${p.pos}" },
        Triple("Transfers to watch", players(pv.transfers)) { p: Player ->
            val from = p.career.last().teamId.takeIf { it >= 0 }?.let { lg.team(it).school } ?: p.career.last().team
            "$from → ${lg.team(p.teamId).school} · ${p.pos}${lastLine(p)}"
        },
        Triple("Breakout candidates", players(pv.breakouts)) { p: Player -> "${lg.team(p.teamId).school} · ${p.pos} · ${p.cls} · scouts see a lot more in him" },
    )
    for ((title, list, sub) in groups) {
        if (list.isEmpty()) continue
        item {
            Section(title)
            GroupList(list, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, sub(p)) }
        }
    }
}

// ---------- picks & your team ----------

private fun LazyListScope.picks(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Section("Final Four picks")
        Panel(padding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
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
        val sleeper = lg.team(pv.sleeper)
        Section("Sleeper")
        GroupList(listOf(sleeper)) { t -> PreviewTeamRow(vm, lg, t, "${pv.proj.getValue(t.id).nat}", lg.conf(t.confId).abbr, Previews.blurb(lg, t)) }

        val u = lg.user
        val pr = pv.proj.getValue(u.id)
        Section("Your team")
        Panel {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(u, 52.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(Previews.expectation(pr.nat).uppercase(), style = T.label, color = P.accent)
                    Text(u.school.uppercase(), style = T.title.copy(fontSize = 28.sp, lineHeight = 30.sp), color = P.ink)
                }
            }
            Spacer(Modifier.height(12.dp))
            StatRow(
                listOf(
                    Triple("National", "No. ${pr.nat}", null),
                    Triple(lg.conf(u.confId).abbr, ordinal(pr.confRank), null),
                    Triple("Projected", "${pr.w}-${pr.l}", null),
                ),
                style = T.numSm,
            )
            if (u.gp > 0) {
                Spacer(Modifier.height(8.dp))
                val confNow = Season.standings(lg, u.confId).indexOf(u) + 1
                val confPart = if (u.cw + u.cl > 0) ", ${ordinal(confNow)} in the ${lg.conf(u.confId).abbr}" else ""
                Text("So far: ${u.record}${if (u.ap > 0) ", AP No. ${u.ap}" else ""}$confPart.", style = T.small, color = if (u.cw + u.cl == 0 || confNow <= pr.confRank) P.good else P.bad)
            }
            Spacer(Modifier.height(10.dp))
            Text(Previews.blurb(lg, u), style = T.small, color = P.sub)
        }
        val newcomers = lg.rosterOf(u).filter { Previews.isNewcomer(it) }.sortedByDescending { it.ovr }
        Section("Key players")
        GroupList(lg.rosterOf(u).sortedByDescending { it.ovr }.take(3), inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.pos} · ${p.cls}${lastLine(p)}") }
        if (newcomers.isNotEmpty()) {
            Section("Newcomers")
            GroupList(newcomers.take(4), inset = 70.dp) { p ->
                val from = p.career.lastOrNull()?.teamId?.takeIf { it >= 0 && it != u.id }?.let { "Transfer from ${lg.team(it).school}" } ?: if (p.recruitRank > 0) "${p.stars}★ freshman · No. ${p.recruitRank}" else "Freshman walk-on"
                PlayerLine(vm, lg, u, p, "${p.pos} · $from")
            }
        }
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
    Panel(color = P.accent, onClick = { vm.push(Route.Preview) }) {
        Text("THE ${pv.season} PRESEASON ISSUE", style = T.label, color = P.onAccent.copy(alpha = 0.8f))
        Text("${u.school.uppercase()} PICKED ${ordinal(pr.confRank).uppercase()} IN THE ${lg.conf(u.confId).abbr}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onAccent)
        Spacer(Modifier.height(4.dp))
        Text("No. ${pr.nat} nationally, projected ${pr.w}-${pr.l}. ${top.school} is the preseason No. 1. Plus every conference and the players to watch.", style = T.small, color = P.onAccent.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Text("Read the issue →", style = T.bodyB, color = P.onAccent)
    }
}

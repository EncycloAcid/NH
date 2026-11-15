package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NbaPreview
import com.nh.hoopscoach.game.NbaPreviews
import com.nh.hoopscoach.game.Odds
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Story
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch

/*
 * The preseason magazine, short and clean: six pages you can flip through in a minute.
 *   Cover · Your team · Rankings · Storylines · Players · Our picks
 * Tabs across the top jump to any page; every page is a handful of cards, no page furniture.
 */

/** The shell every issue shares: a back bar, the page tabs, the pages, and prev/next at the bottom. */
@Composable
private fun Issue(vm: GameViewModel, title: String, tabs: List<String>, page: @Composable (Int, (Int) -> Unit) -> Unit) {
    val pager = rememberPagerState { tabs.size }
    val scope = rememberCoroutineScope()
    val go = { i: Int -> scope.launch { pager.animateScrollToPage(i.coerceIn(0, tabs.lastIndex)) }; Unit }
    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar(title, "${pager.currentPage + 1} of ${tabs.size} · ${tabs[pager.currentPage]}", onBack = { vm.back() })
        Segmented(tabs, pager.currentPage, { go(it) }, scroll = true)
        Spacer(Modifier.height(6.dp))
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { i -> page(i, go) }
        Hairline()
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage == 0) SmallButton("Close") { vm.back() } else SmallButton("‹ ${tabs[pager.currentPage - 1]}") { go(pager.currentPage - 1) }
            Spacer(Modifier.weight(1f))
            if (pager.currentPage < tabs.lastIndex) SmallButton("${tabs[pager.currentPage + 1]} ›", primary = true) { go(pager.currentPage + 1) }
            else SmallButton("Done", primary = true) { vm.back() }
        }
    }
}

/** A page's title: a small kicker and one line, no more. */
@Composable
private fun MagHead(kicker: String, title: String) {
    Column(Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 8.dp, bottom = 4.dp)) {
        Text(kicker.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Text(title, style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.ink)
    }
}

@Composable
private fun Page(content: LazyListScope.() -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        content()
        item { Spacer(Modifier.height(24.dp)) }
    }
}

// ---------- the cover ----------

/**
 * The front: the team to beat's arena and color, the masthead, the star cut out in front of it, the
 * headline down the left with three cover lines, and a "your team" flap at the bottom.
 */
@Composable
private fun MagCover(team: Team, star: Player?, masthead: String, issue: String, headline: String, lines: List<String>, yours: @Composable () -> Unit, onRead: () -> Unit) {
    val base = teamCard(team)
    val accent = Color(team.secondary).let { if (contrast(it, base) >= 2.2f) it else P.gold }
    Column {
        BoxWithConstraints(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(22.dp))) {
            val w = maxWidth
            Box(Modifier.fillMaxWidth().height(w * 1.28f)) {
                ArenaBackdrop(team, Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(base.copy(alpha = 0.55f), base.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.75f)))))
                // The star, big, in the right half.
                Box(Modifier.align(Alignment.BottomEnd).offset(x = w * 0.28f).wrapContentSize(unbounded = true, align = Alignment.BottomEnd)) {
                    if (star != null) PlayerJersey(star, team, w * 1.05f, plate = false) else TeamBadge(team, w * 0.5f)
                }
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    androidx.compose.foundation.text.BasicText(
                        masthead, style = T.hero.copy(color = Color.White, lineHeight = 1.0.em), maxLines = 1,
                        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 28.sp, maxFontSize = 96.sp, stepSize = 2.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(issue, style = T.label.copy(fontSize = 10.sp, letterSpacing = 1.5.sp), color = accent)
                    Spacer(Modifier.height(18.dp))
                    Column(Modifier.fillMaxWidth(0.5f)) {
                        Text(headline.uppercase(), style = T.hero.copy(fontSize = 30.sp, lineHeight = 30.sp), color = Color.White, maxLines = 4)
                        Spacer(Modifier.height(14.dp))
                        lines.take(3).forEach { l ->
                            Box(Modifier.width(24.dp).height(3.dp).background(accent))
                            Text(l, style = T.bodyB.copy(fontSize = 14.sp, lineHeight = 17.sp), color = Color.White, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    yours()
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Read the issue", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { onRead() }
    }
}

/** The flap on the cover: the reader's team in one line. */
@Composable
private fun CoverFlap(t: Team, line1: String, line2: String) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(t, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("YOUR TEAM", style = T.label.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.7f))
            Text(line1, style = T.bodyB, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(line2, style = T.small, color = Color.White.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------- shared cards ----------

/** A team's season in a box: big rank, the projection, and the odds as bars. */
@Composable
private fun OutlookCard(t: Team, rank: String, record: String, range: String, bars: List<Pair<String, Double>>) {
    val base = teamCard(t)
    val fg = onColor(base)
    Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp))
        .background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.35f)))).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(t, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(rank.uppercase(), style = T.label.copy(fontSize = 10.sp), color = fg.copy(alpha = 0.75f))
                Text(record, style = T.hero.copy(fontSize = 40.sp, lineHeight = 40.sp), color = fg)
                Text(range, style = T.small, color = fg.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(14.dp))
        bars.forEach { (label, v) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = T.small, color = fg.copy(alpha = 0.9f), modifier = Modifier.width(96.dp))
                Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fg.copy(alpha = 0.18f))) {
                    Box(Modifier.fillMaxWidth(v.toFloat().coerceIn(0.02f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(fg))
                }
                Text(odd(v), style = T.bodyB.copy(fontSize = 13.sp), color = fg, modifier = Modifier.width(48.dp).padding(start = 8.dp))
            }
        }
    }
}

/** One player in a list: portrait, name, team, a line, and his rating. */
@Composable
private fun PlayerLine(vm: GameViewModel, lg: League, p: Player, tag: String, line: String) {
    val t = lg.teams.getOrNull(p.teamId)
    ListRow(onClick = { vm.openPlayer(p.id) }) {
        PlayerJersey(p, t, 42.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(tag.ifEmpty { null }, t?.let { if (lg.pro) it.name else it.school }, line.ifEmpty { null }).joinToString(" · "), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ring(p.ovr, 38.dp, textSize = 14.sp)
    }
}

/** A storyline: kicker, headline and a short body (three lines, tap to open the team or player). */
@Composable
private fun StoryTile(vm: GameViewModel, lg: League, s: Story, lead: Boolean) {
    val t = lg.teams.getOrNull(s.teamId)
    val open: (() -> Unit)? = when {
        s.pid >= 0 && lg.playerOrNull(s.pid) != null -> ({ vm.openPlayer(s.pid) })
        t != null -> ({ vm.push(Route.TeamPage(t.id)) })
        else -> null
    }
    if (lead && t != null) {
        val base = teamCard(t); val fg = onColor(base)
        Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.4f))))
            .then(if (open != null) Modifier.clickable(onClick = open) else Modifier).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.kicker.uppercase(), style = T.label.copy(fontSize = 10.sp), color = fg.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
                TeamBadge(t, 30.dp)
            }
            Text(s.head, style = T.title.copy(fontSize = 24.sp, lineHeight = 26.sp), color = fg, modifier = Modifier.padding(top = 4.dp))
            Text(s.body, style = T.body, color = fg.copy(alpha = 0.9f), maxLines = 4, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        }
    } else Panel(onClick = open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(s.kicker.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.weight(1f))
            t?.let { TeamBadge(it, 24.dp) }
        }
        Text(s.head, style = T.h2.copy(fontSize = 18.sp, lineHeight = 21.sp), color = P.ink, modifier = Modifier.padding(top = 2.dp))
        Text(s.body, style = T.small, color = P.sub, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A pick: label on the left, the team or player on the right. */
@Composable
private fun PickRow(label: String, t: Team?, name: String, sub: String = "", onClick: (() -> Unit)? = null) {
    ListRow(onClick = onClick) {
        Text(label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.width(104.dp))
        t?.let { TeamBadge(it, 28.dp); Spacer(Modifier.width(8.dp)) }
        Column(Modifier.weight(1f)) {
            Text(name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub.isNotEmpty()) Text(sub, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Group(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    Panel(padding = PaddingValues(vertical = 4.dp), content = content)

private fun Story.mine() = kicker == "Your team" || kicker == "Your program" || kicker == "Spotlight"

// ---------- the college issue ----------

private val NoOdds = Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)

@Composable
fun CollegeMagazine(vm: GameViewModel, lg: League) {
    val pv = remember(lg, lg.season) { Previews.current(lg) }
    fun o(t: Team) = pv.odds[t.id] ?: NoOdds
    val ranked = remember(pv) { pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) } }
    val u = lg.user
    val mine = pv.proj.getValue(u.id)
    val hero = lg.hero?.takeIf { lg.playerMode && it.teamId >= 0 }
    val tabs = listOf("Cover", if (hero != null) "You" else "Your team", "Top 25", "Stories", "Players", "Picks")
    Issue(vm, "Preview ${pv.season}", tabs) { page, go ->
        Page {
            when (page) {
                0 -> item {
                    val top = ranked.first()
                    val story = pv.cover?.takeIf { pv.coverStyle == 1 }
                    val coverTeam = story?.let { lg.teams.getOrNull(it.teamId) } ?: top
                    MagCover(
                        coverTeam, story?.let { lg.playerOrNull(it.pid) }?.takeIf { it.teamId == coverTeam.id } ?: Previews.star(lg, coverTeam),
                        "HOOPS DYNASTY", "COLLEGE BASKETBALL PREVIEW · ${pv.season}",
                        story?.head ?: pv.headline.ifEmpty { "${top.school}: the team to beat" },
                        pv.stories.filter { !it.mine() && it.teamId != coverTeam.id }.take(3).map { it.head },
                        { CoverFlap(u, "${u.school}: No. ${mine.nat}, ${Previews.ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}", "${o(u).lo}-${o(u).hi} wins · NCAA ${odd(o(u).ncaa)}") },
                    ) { go(1) }
                }
                1 -> {
                    item { MagHead(if (hero != null) "Your season" else "Your team", u.school) }
                    if (hero != null) item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { TradingCard(lg, hero, width = 210.dp) }
                    }
                    item {
                        OutlookCard(u, "Preseason No. ${mine.nat} · ${Previews.ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}", "${mine.w}-${mine.l}", "${o(u).lo} to ${o(u).hi} wins in most seasons",
                            listOf("${lg.conf(u.confId).abbr} title" to o(u).conf, "NCAA bid" to o(u).ncaa, "Sweet 16" to o(u).s16, "Final Four" to o(u).f4, "Title" to o(u).title))
                    }
                    pv.reports[u.id]?.let { r ->
                        item {
                            Spacer(Modifier.height(10.dp))
                            Panel {
                                Text("THE STRENGTH", style = T.label.copy(fontSize = 10.sp), color = P.good)
                                Text(r.strength, style = T.body, color = P.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(10.dp))
                                Text("THE CONCERN", style = T.label.copy(fontSize = 10.sp), color = P.bad)
                                Text(r.concern, style = T.body, color = P.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        Section("Who leads the way")
                        Group {
                            lg.rosterOf(u).sortedByDescending { it.ovr }.take(3).forEachIndexed { i, p ->
                                if (i > 0) Hairline(Modifier.padding(start = 66.dp))
                                PlayerLine(vm, lg, p, if (i == 0) "The star" else p.posLabel, p.cls)
                            }
                        }
                    }
                    val games = pv.marquee.mapNotNull { id -> lg.games.firstOrNull { it.id == id } }.filter { it.home == u.id || it.away == u.id }.take(3)
                    if (games.isNotEmpty()) item {
                        Section("Circle these")
                        Group {
                            games.forEachIndexed { i, g ->
                                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                                val opp = lg.team(if (g.home == u.id) g.away else g.home)
                                PickRow(if (g.home == u.id) "Home" else "Away", opp, opp.school, "Day ${g.day}") { vm.push(Route.TeamPage(opp.id)) }
                            }
                        }
                    }
                }
                2 -> {
                    item { MagHead("The rankings", "Preseason Top 25") }
                    item {
                        Group {
                            ranked.take(25).forEachIndexed { i, t ->
                                if (i > 0) Hairline(Modifier.padding(start = 56.dp))
                                val pj = pv.proj.getValue(t.id)
                                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                                    Text("${i + 1}", style = T.numSm.copy(fontSize = if (i < 5) 22.sp else 16.sp), color = if (t.id == u.id) P.accent else P.ink, modifier = Modifier.width(30.dp))
                                    TeamBadge(t, if (i < 5) 34.dp else 26.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(t.school, style = T.bodyB, color = if (t.id == u.id) P.accent else P.ink, maxLines = 1)
                                        Text("${pj.w}-${pj.l} · ${lg.conf(t.confId).abbr}", style = T.small, color = P.sub)
                                    }
                                    Text(odd(o(t).title), style = T.bodyB.copy(fontSize = 13.sp), color = P.sub)
                                }
                            }
                        }
                        Text("Right column: chance to win the title.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                    }
                }
                3 -> {
                    item { MagHead("Storylines", "What to watch") }
                    val stories = pv.stories.filter { !it.mine() }.take(5)
                    stories.forEachIndexed { i, s -> item { StoryTile(vm, lg, s, i == 0); Spacer(Modifier.height(10.dp)) } }
                }
                4 -> {
                    item { MagHead("Players to watch", "The names to know") }
                    fun block(title: String, ids: List<Int>, tag: (Int, Player) -> String) = item {
                        val ps = ids.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId >= 0 }
                        if (ps.isNotEmpty()) {
                            Section(title)
                            Group {
                                ps.forEachIndexed { i, p ->
                                    if (i > 0) Hairline(Modifier.padding(start = 66.dp))
                                    PlayerLine(vm, lg, p, tag(i, p), "${p.posLabel} · ${p.cls}")
                                }
                            }
                        }
                    }
                    block("Preseason All-America", pv.allAmerica.take(5)) { i, _ -> if (i == 0) "Player of the Year pick" else "" }
                    block("Freshmen to watch", pv.freshmen.take(4)) { _, p -> "${p.stars}★" }
                    block("Breakout candidates", pv.breakouts.take(3)) { _, _ -> "" }
                }
                else -> {
                    item { MagHead("Our picks", "How it ends") }
                    item {
                        val ff = pv.finalFour.mapNotNull { lg.teams.getOrNull(it) }
                        Panel {
                            Text("THE FINAL FOUR", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                ff.forEachIndexed { i, t ->
                                    Column(Modifier.clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        TeamBadge(t, if (i == 0) 56.dp else 44.dp)
                                        Text(t.abbr, style = T.bodyB.copy(fontSize = 13.sp), color = if (i == 0) P.accent else P.ink, modifier = Modifier.padding(top = 4.dp))
                                        if (i == 0) Text("CHAMPION", style = T.label.copy(fontSize = 9.sp), color = P.gold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                        Group {
                            val poy = pv.poy.firstOrNull()?.let { lg.playerOrNull(it.pid) } ?: pv.allAmerica.firstOrNull()?.let { lg.playerOrNull(it) }
                            poy?.let { p -> PickRow("Player of the Year", lg.teams.getOrNull(p.teamId), p.name, "${p.posLabel} · ${lg.teams.getOrNull(p.teamId)?.school ?: ""}") { vm.openPlayer(p.id) } }
                            lg.playerOrNull(pv.dpoy)?.let { p -> Hairline(Modifier.padding(start = 16.dp)); PickRow("Defensive POY", lg.teams.getOrNull(p.teamId), p.name) { vm.openPlayer(p.id) } }
                            pv.freshmen.firstOrNull()?.let { lg.playerOrNull(it) }?.let { p -> Hairline(Modifier.padding(start = 16.dp)); PickRow("Freshman of the Year", lg.teams.getOrNull(p.teamId), p.name) { vm.openPlayer(p.id) } }
                            lg.teams.getOrNull(pv.cotyTeam)?.let { t -> Hairline(Modifier.padding(start = 16.dp)); PickRow("Coach of the Year", t, "${lg.coachOf(t).first} ${lg.coachOf(t).last}", t.school) { vm.push(Route.TeamPage(t.id)) } }
                            lg.teams.getOrNull(pv.sleeper)?.let { t -> Hairline(Modifier.padding(start = 16.dp)); PickRow("Sleeper", t, t.school) { vm.push(Route.TeamPage(t.id)) } }
                            lg.teams.getOrNull(pv.cinderella)?.let { t -> Hairline(Modifier.padding(start = 16.dp)); PickRow("Cinderella", t, t.school) { vm.push(Route.TeamPage(t.id)) } }
                        }
                    }
                    if (pv.bold.isNotEmpty()) item {
                        Section("Bold calls")
                        Group {
                            pv.bold.take(3).forEachIndexed { i, b ->
                                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                                Text(b, style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- the NBA issue ----------

@Composable
fun NbaMagazine(vm: GameViewModel, lg: League) {
    val pv: NbaPreview = remember(lg, lg.season) { NbaPreviews.current(lg) } ?: run { vm.back(); return }
    val ranked = remember(pv) { pv.proj.entries.sortedWith(compareByDescending<Map.Entry<Int, com.nh.hoopscoach.game.NbaProj>> { it.value.w }.thenByDescending { it.value.title }).map { lg.team(it.key) } }
    val u = lg.user
    val mine = pv.proj.getValue(u.id)
    val myRank = ranked.indexOfFirst { it.id == u.id } + 1
    val hero = lg.hero?.takeIf { lg.playerMode && it.teamId >= 0 }
    val tabs = listOf("Cover", if (hero != null) "You" else "Your team", "Rankings", "Stories", "Awards", "Picks")
    fun name(t: Team) = t.name
    Issue(vm, "NBA Preview ${pv.season}", tabs) { page, go ->
        Page {
            when (page) {
                0 -> item {
                    val story = pv.cover?.takeIf { pv.coverStyle == 1 }
                    val coverTeam = story?.let { lg.teams.getOrNull(it.teamId) } ?: ranked.first()
                    val star = story?.let { lg.playerOrNull(it.pid) }?.takeIf { it.teamId == coverTeam.id } ?: pv.mvp.firstOrNull()?.let { lg.playerOrNull(it.pid) }?.takeIf { it.teamId == coverTeam.id }
                        ?: lg.rosterOf(coverTeam).maxByOrNull { it.ovr }
                    MagCover(coverTeam, star, "HOOPS DYNASTY", "NBA SEASON PREVIEW · ${pv.season - 1}-${(pv.season % 100).toString().padStart(2, '0')}",
                        story?.head ?: pv.headline.ifEmpty { "${coverTeam.name}: the team to beat" },
                        pv.stories.filter { !it.mine() && it.teamId != coverTeam.id }.take(3).map { it.head },
                        { CoverFlap(u, "${u.name}: No. $myRank of 30", "${mine.w}-${mine.l} projected · playoffs ${odd(mine.playoffs)}") },
                    ) { go(1) }
                }
                1 -> {
                    item { MagHead(if (hero != null) "Your season" else "Your team", u.name) }
                    if (hero != null) item { Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) { TradingCard(lg, hero, width = 210.dp) } }
                    item {
                        OutlookCard(u, "No. $myRank in our rankings · ${pv.last[u.id] ?: "a new season"}", "${mine.w}-${mine.l}", "${mine.lo} to ${mine.hi} wins in most seasons",
                            listOf("Playoffs" to mine.playoffs, "Second round" to mine.second, "Conf. finals" to mine.confFinals, "Finals" to mine.finals, "Title" to mine.title))
                    }
                    pv.reports[u.id]?.let { r ->
                        item {
                            Spacer(Modifier.height(10.dp))
                            Panel {
                                Text("THE STRENGTH", style = T.label.copy(fontSize = 10.sp), color = P.good)
                                Text(r.strength, style = T.body, color = P.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(10.dp))
                                Text("THE CONCERN", style = T.label.copy(fontSize = 10.sp), color = P.bad)
                                Text(r.concern, style = T.body, color = P.ink, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    item {
                        Section("Who leads the way")
                        Group {
                            lg.rosterOf(u).sortedByDescending { it.ovr }.take(3).forEachIndexed { i, p ->
                                if (i > 0) Hairline(Modifier.padding(start = 66.dp))
                                PlayerLine(vm, lg, p, if (i == 0) "The star" else p.posLabel, "age ${p.age}")
                            }
                        }
                    }
                }
                2 -> {
                    item { MagHead("Power rankings", "All 30, one to thirty") }
                    item {
                        Group {
                            ranked.forEachIndexed { i, t ->
                                if (i > 0) Hairline(Modifier.padding(start = 56.dp))
                                val pj = pv.proj.getValue(t.id)
                                val was = pv.lastWins[t.id]?.takeIf { it > 0 }
                                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                                    Text("${i + 1}", style = T.numSm.copy(fontSize = if (i < 5) 22.sp else 16.sp), color = if (t.id == u.id) P.accent else P.ink, modifier = Modifier.width(30.dp))
                                    TeamBadge(t, if (i < 5) 34.dp else 26.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(name(t), style = T.bodyB, color = if (t.id == u.id) P.accent else P.ink, maxLines = 1)
                                        Text("${pj.w}-${pj.l}" + (was?.let { " · ${if (pj.w >= it) "+" else ""}${pj.w - it} wins on last year" } ?: ""), style = T.small, color = P.sub, maxLines = 1)
                                    }
                                    Text(odd(pj.title), style = T.bodyB.copy(fontSize = 13.sp), color = P.sub)
                                }
                            }
                        }
                        Text("Right column: chance to win the title.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                    }
                }
                3 -> {
                    item { MagHead("Storylines", "What to watch") }
                    val stories = pv.stories.filter { !it.mine() }.take(5)
                    stories.forEachIndexed { i, s -> item { StoryTile(vm, lg, s, i == 0); Spacer(Modifier.height(10.dp)) } }
                }
                4 -> {
                    item { MagHead("Award races", "The favorites") }
                    fun race(title: String, list: List<com.nh.hoopscoach.game.PoyOdds>, n: Int) = item {
                        val ps = list.mapNotNull { po -> lg.playerOrNull(po.pid)?.let { it to po.p } }.take(n)
                        if (ps.isNotEmpty()) {
                            Section(title)
                            Group {
                                ps.forEachIndexed { i, (p, odds) ->
                                    if (i > 0) Hairline(Modifier.padding(start = 66.dp))
                                    PlayerLine(vm, lg, p, odd(odds), p.posLabel)
                                }
                            }
                        }
                    }
                    race("MVP", pv.mvp, 5)
                    race("Rookie of the Year", pv.roy, 3)
                    race("Defensive Player of the Year", pv.dpoy, 3)
                    race("Sixth Man", pv.sixth, 2)
                }
                else -> {
                    item { MagHead("Our picks", "How it ends") }
                    item {
                        val finalists = pv.proj.entries.sortedByDescending { it.value.finals }.take(2).map { lg.team(it.key) }
                        val champ = pv.proj.entries.maxByOrNull { it.value.title }?.let { lg.team(it.key) }
                        Panel {
                            Text("THE FINALS", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                finalists.forEach { t ->
                                    Column(Modifier.clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                        TeamBadge(t, 56.dp)
                                        Text(t.abbr, style = T.bodyB, color = if (t.id == champ?.id) P.accent else P.ink, modifier = Modifier.padding(top = 4.dp))
                                        if (t.id == champ?.id) Text("CHAMPION", style = T.label.copy(fontSize = 9.sp), color = P.gold)
                                    }
                                }
                            }
                        }
                    }
                    item {
                        Spacer(Modifier.height(10.dp))
                        Group {
                            listOf("MVP" to pv.mvp, "Rookie of the Year" to pv.roy, "Defensive POY" to pv.dpoy, "Sixth Man" to pv.sixth, "Most Improved" to pv.mip).forEachIndexed { i, (label, list) ->
                                val p = list.firstOrNull()?.let { lg.playerOrNull(it.pid) } ?: return@forEachIndexed
                                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                                PickRow(label, lg.teams.getOrNull(p.teamId), p.name, lg.teams.getOrNull(p.teamId)?.name ?: "") { vm.openPlayer(p.id) }
                            }
                        }
                    }
                    if (pv.bold.isNotEmpty()) item {
                        Section("Bold calls")
                        Group {
                            pv.bold.take(3).forEachIndexed { i, b ->
                                if (i > 0) Hairline(Modifier.padding(start = 16.dp))
                                Text(b, style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

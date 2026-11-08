package com.nh.hoopscoach.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Odds
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Preview
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Story
import com.nh.hoopscoach.game.CollegeHistory
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch
import kotlin.math.abs

/** The pages of the issue: section kicker and headline. */
private fun pageIds(pv: Preview) = listOf("cover", "inside", "stories") + pv.features.indices.map { "f$it" } +
    listOf("title", "top5", "top25", "aa", "positions", "freshmen", "transfers", "breakouts", "confdepth", "power", "midmajors", "report", "coaches", "games", "picks")

private fun pages(lg: League, pv: Preview) = listOf(
    "Cover" to "Preseason Preview",
    "Inside" to "In this issue",
    "Storylines" to "What to watch",
) + pv.features.map { "Feature" to it.title } + listOf(
    "The title race" to "Who can win it all",
    "The rankings" to "Top 25: Nos. 1-5",
    "The rankings" to "Top 25: Nos. 6-25",
    "National honors" to "Preseason All-America",
    "Position rankings" to "Best at every spot",
    "Recruiting" to "The freshman class",
    "Transfer portal" to "New faces, last calls",
    "Players to watch" to "Breakouts & sleepers",
    "Conference call" to "${lg.conf(lg.user.confId).name} in depth",
    "Conference call" to "Power rankings",
    "Around the country" to "Mid-major roundup",
    "Team report" to lg.user.school,
    "The sidelines" to "Coaches",
    "Mark your calendar" to "Games to watch",
    "The picks" to "Our predictions",
)

private val NoOdds = Odds(0.0, 0.0, 0.0, 0.0, 0.0, 0, 0)
private fun odds(pv: Preview, t: Team): Odds = pv.odds[t.id] ?: NoOdds
private fun topTeam(lg: League, pv: Preview): Team = lg.team(pv.proj.entries.first { it.value.nat == 1 }.key)
private fun ranked(lg: League, pv: Preview): List<Team> = pv.proj.entries.sortedBy { it.value.nat }.map { lg.team(it.key) }
private fun players(lg: League, ids: List<Int>): List<Player> = ids.mapNotNull { lg.playerOrNull(it) }.filter { it.teamId >= 0 }

/** Odds as a short label: "<1%", "37%". */
internal fun odd(v: Double): String = when {
    v >= 0.995 -> "99%"
    v < 0.01 -> "<1%"
    else -> "${(v * 100).toInt()}%"
}

/** A national percentile as a letter grade. */
private fun letter(p: Double): String = when {
    p >= 0.95 -> "A+"; p >= 0.85 -> "A"; p >= 0.75 -> "A-"; p >= 0.65 -> "B+"; p >= 0.55 -> "B"; p >= 0.45 -> "B-"
    p >= 0.35 -> "C+"; p >= 0.25 -> "C"; p >= 0.15 -> "D"; else -> "F"
}

private fun lastLine(p: Player): String {
    val l = p.career.lastOrNull() ?: return ""
    return " · %.1f ppg last season".format(l.s.ppg)
}

private fun lastStats(p: Player): String {
    val l = p.career.lastOrNull() ?: return "First college season"
    return "%.1f pts · %.1f reb · %.1f ast last season".format(l.s.ppg, l.s.rpg, l.s.apg)
}

/** Where a team finished last season in the final poll (0 = unranked, -1 = no season). */
private fun lastAp(lg: League, t: Team): Int = t.history.lastOrNull()?.takeIf { it.season == lg.season - 1 }?.ap ?: -1

@Composable
fun PreviewScreen(vm: GameViewModel, lg: League) {
    val pv = remember(lg, lg.season) { Previews.current(lg) }
    val list = pages(lg, pv)
    val ids = pageIds(pv)
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
                    if (page > 0) item { PageHead(kicker, title, page + 1) }
                    when (val id = ids[page]) {
                        "cover" -> cover(lg, pv, list.size) { go(1) }
                        "inside" -> contents(lg, pv, list, go)
                        "stories" -> storylines(vm, lg, pv)
                        "title" -> titleRace(vm, lg, pv)
                        "top5" -> topFive(vm, lg, pv)
                        "top25" -> restOfTop25(vm, lg, pv)
                        "aa" -> allAmerica(vm, lg, pv)
                        "positions" -> positions(vm, lg)
                        "freshmen" -> freshmen(vm, lg, pv)
                        "transfers" -> transfers(vm, lg, pv)
                        "breakouts" -> breakouts(vm, lg, pv)
                        "confdepth" -> item { ConferenceDepth(vm, lg, pv, lg.user.confId) }
                        "power" -> powerRankings(vm, lg, pv)
                        "midmajors" -> midMajors(vm, lg, pv)
                        "report" -> teamReport(vm, lg, pv)
                        "coaches" -> coaches(vm, lg, pv)
                        "games" -> gamesToWatch(vm, lg, pv)
                        "picks" -> predictions(vm, lg, pv)
                        else -> pv.features.getOrNull(id.removePrefix("f").toIntOrNull() ?: -1)?.let { featurePage(vm, lg, it) }
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

// ---------- shared pieces ----------

@Composable
internal fun PageHead(kicker: String, title: String, page: Int) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.background(P.accent).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(kicker.uppercase(), style = T.label, color = P.onAccent) }
            Spacer(Modifier.weight(1f))
            Text("P. $page", style = T.label, color = P.faint)
        }
        Spacer(Modifier.height(8.dp))
        Text(title.uppercase(), style = T.title.copy(fontSize = 34.sp, lineHeight = 35.sp), color = P.ink)
        Spacer(Modifier.height(8.dp))
        Row {
            Box(Modifier.weight(1f).height(3.dp).background(P.ink))
            Spacer(Modifier.width(4.dp))
            Box(Modifier.width(36.dp).height(3.dp).background(P.accent))
        }
    }
}

@Composable
internal fun Deck(text: String) {
    Text(text, style = T.body.copy(fontStyle = FontStyle.Italic), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter + 4.dp, top = 8.dp, bottom = 4.dp))
}

/** A bar that fills in when the page comes into view. */
@Composable
internal fun GrowBar(fraction: Float, modifier: Modifier = Modifier, color: Color = P.accent, height: Dp = 6.dp, track: Color = P.surface2) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val f by animateFloatAsState(if (shown) fraction.coerceIn(0f, 1f) else 0f, tween(800, easing = FastOutSlowInEasing), label = "grow")
    Bar(f, modifier, color, track, height)
}

/** One number over a filling bar: "FINAL FOUR 41%". */
@Composable
internal fun OddsCell(label: String, v: Double, color: Color, modifier: Modifier = Modifier, scale: Double = 1.0) {
    Column(modifier) {
        Text(label.uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.sub, maxLines = 1)
        Text(odd(v), style = T.numSm.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.ink)
        Spacer(Modifier.height(4.dp))
        GrowBar((v / scale).toFloat(), Modifier.fillMaxWidth(), color, 5.dp)
    }
}

/** Five dots: returning starters. */
@Composable
internal fun StarterDots(n: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(5) { i -> Box(Modifier.size(10.dp).clip(CircleShape).background(if (i < n) color else P.line)) }
    }
}

/** A colored band in a team's colors, for headers. */
@Composable
internal fun TeamBand(t: Team, modifier: Modifier = Modifier, content: @Composable (fg: Color, accent: Color) -> Unit) {
    val primary = Color(t.primary)
    val secondary = Color(t.secondary)
    val fg = onColor(primary)
    val accent = if (contrast(secondary, primary) >= 2.2f) secondary else fg
    Column(modifier.fillMaxWidth().background(Brush.linearGradient(listOf(primary, lerp(primary, Color.Black, 0.4f)))).padding(16.dp)) {
        content(fg, accent)
    }
}

@Composable
internal fun Movement(now: Int, last: Int) {
    val (text, color) = when {
        last < 0 -> "—" to P.faint
        last == 0 -> "NEW" to P.accent
        last > now -> "▲${last - now}" to P.good
        last < now -> "▼${now - last}" to P.bad
        else -> "—" to P.faint
    }
    Text(text, style = T.label.copy(fontSize = 10.sp), color = color, modifier = Modifier.width(34.dp), maxLines = 1)
}

// ---------- 1: cover ----------

/** The user's team in the cover's corner. */
@Composable
private fun CollegeUserBox(lg: League, pv: Preview) {
    val u = lg.user
    val mine = pv.proj.getValue(u.id)
    val uo = odds(pv, u)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(u, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("YOUR TEAM", style = T.label.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.75f))
            Text("${u.school}: No. ${mine.nat}, ${Previews.ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}", style = T.bodyB, color = Color.White, maxLines = 2)
            Text("NCAA ${odd(uo.ncaa)} · ${lg.conf(u.confId).abbr} title ${odd(uo.conf)} · ${uo.lo}-${uo.hi} wins", style = T.small, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private fun LazyListScope.cover(lg: League, pv: Preview, count: Int, onRead: () -> Unit) {
    // This year's cover design: a feature on the biggest story, the two-horse race, or the classic No. 1 cover.
    val story = pv.cover
    val subject = story?.let { lg.teams.getOrNull(it.teamId) }
    if (pv.coverStyle == 1 && story != null && subject != null) {
        item {
            val lines = pv.stories.filter { it.head != story.head && it.kicker != "Your team" && it.kicker != "Your program" }.take(3).map { it.kicker to it.head }
            FeatureCover("HOOPS DYNASTY", "PRESEASON ${pv.season}", subject, lg.playerOrNull(story.pid)?.takeIf { it.teamId >= 0 }, story.kicker, story.head,
                story.body.substringBefore(". ").take(220) + ".", lines, count, onRead) { CollegeUserBox(lg, pv) }
        }
        return
    }
    val rk = ranked(lg, pv)
    if (pv.coverStyle == 2 && rk.size >= 2) {
        item {
            val a = rk[0]
            val b = rk[1]
            val lines = pv.stories.filter { it.teamId != a.id && it.teamId != b.id && it.kicker != "Your team" && it.kicker != "Your program" }.take(3).map { it.kicker to it.head }
            VersusCover("HOOPS DYNASTY", "PRESEASON ${pv.season}", pv.headline.ifEmpty { "Two teams, one title" }, a, b, odds(pv, a).title, odds(pv, b).title,
                "No. 1 · ${pv.proj.getValue(a.id).w}-${pv.proj.getValue(a.id).l}", "No. 2 · ${pv.proj.getValue(b.id).w}-${pv.proj.getValue(b.id).l}", lines, count, onRead) { CollegeUserBox(lg, pv) }
        }
        return
    }
    item {
        val top = topTeam(lg, pv)
        val star = Previews.star(lg, top)
        val primary = Color(top.primary)
        val secondary = Color(top.secondary)
        val fg = onColor(primary)
        val accent = if (contrast(secondary, primary) >= 2.2f) secondary else fg
        val u = lg.user
        val mine = pv.proj.getValue(u.id)
        val uo = odds(pv, u)
        val to = odds(pv, top)
        val lines = pv.stories.filter { it.kicker != "Your team" && it.kicker != "Your program" && it.kicker != "Spotlight" && it.teamId != top.id }.take(3)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(primary, lerp(primary, Color.Black, 0.55f))))
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOOPS DYNASTY", style = T.label, color = fg.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text("PRESEASON ${pv.season}", style = T.label, color = fg.copy(alpha = 0.85f))
            }
            Text("PREVIEW", style = T.hero.copy(fontSize = 72.sp, lineHeight = 68.sp, letterSpacing = 2.sp), color = fg)
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
            Spacer(Modifier.height(16.dp))
            Text(pv.headline.ifEmpty { "The team to beat" }.uppercase(), style = T.title.copy(fontSize = 38.sp, lineHeight = 38.sp), color = accent)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (star != null) Jersey(star.number, secondary, primary, 104.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("NO. 1 · ${odd(to.title)} TO WIN IT ALL", style = T.label, color = accent)
                    Text(top.school.uppercase(), style = T.title.copy(fontSize = 32.sp, lineHeight = 32.sp), color = fg, maxLines = 2)
                    if (star != null) Text("${star.name} · ${star.posLabel} · ${star.cls}", style = T.small, color = fg.copy(alpha = 0.85f))
                    Text("Projected ${pv.proj.getValue(top.id).w}-${pv.proj.getValue(top.id).l} · Final Four ${odd(to.f4)}", style = T.small, color = fg.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(14.dp))
            lines.forEach { CoverLine(it.kicker, it.head, fg, accent) }
            Spacer(Modifier.height(12.dp))
            // The user's team, on the cover's corner.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamBadge(u, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR TEAM", style = T.label.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.75f))
                    Text("${u.school}: No. ${mine.nat}, ${Previews.ordinal(mine.confRank)} in the ${lg.conf(u.confId).abbr}", style = T.bodyB, color = Color.White, maxLines = 2)
                    Text("NCAA ${odd(uo.ncaa)} · ${lg.conf(u.confId).abbr} title ${odd(uo.conf)} · ${uo.lo}-${uo.hi} wins", style = T.small, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text("$count pages · swipe to open →", style = T.bodyB, color = accent, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
        }
    }
}

@Composable
internal fun CoverLine(kicker: String, text: String, fg: Color, accent: Color) {
    Row(Modifier.padding(vertical = 5.dp).height(IntrinsicSize.Min)) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(kicker.uppercase(), style = T.label.copy(fontSize = 10.sp), color = accent)
            Text(text, style = T.bodyB, color = fg)
        }
    }
}

// ---------- 2: contents ----------

private fun LazyListScope.contents(lg: League, pv: Preview, list: List<Pair<String, String>>, go: (Int) -> Unit) {
    item {
        Section("From the editor")
        Panel {
            Previews.editorsLetter(lg, pv).forEachIndexed { i, para ->
                Text(para, style = if (i == 0) T.bodyB else T.body, color = P.ink, modifier = Modifier.padding(vertical = 4.dp))
            }
            Text("— The editors", style = T.small.copy(fontStyle = FontStyle.Italic), color = P.sub, modifier = Modifier.padding(top = 4.dp))
        }
        Section("How we project")
        Panel {
            Text("Every team gets a strength rating from its roster (its best players, weighted) and its coach. We play the season 400 times from those ratings and the real schedule: conference tournaments, a 68-team field and the bracket. Projected records are the average; the win range covers 8 seasons in 10.", style = T.small, color = P.ink)
            Spacer(Modifier.height(6.dp))
            Text("Tuned against how seasons really play out in the game, the projections miss by about 2 to 3 wins on average. During the season every result moves a team's strength, and your Program panel shows where your season is heading.", style = T.small, color = P.sub)
        }
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

// ---------- 3: storylines ----------

private fun LazyListScope.storylines(vm: GameViewModel, lg: League, pv: Preview) {
    item { Deck("The questions that will decide the season, from our ${pv.stories.size} biggest storylines.") }
    pv.stories.forEachIndexed { i, s ->
        item {
            Spacer(Modifier.height(10.dp))
            StoryCard(vm, lg, s, i, lead = i == 0)
        }
    }
    if (pv.stories.isEmpty()) item { Panel { Text("No storylines this year.", style = T.body, color = P.sub) } }
}

@Composable
internal fun StoryCard(vm: GameViewModel, lg: League, s: Story, i: Int, lead: Boolean) {
    val t = lg.teams.getOrNull(s.teamId)
    val open = when {
        s.pid >= 0 && lg.playerOrNull(s.pid) != null -> ({ vm.openPlayer(s.pid) })
        t != null -> ({ vm.push(Route.TeamPage(t.id)) })
        else -> null
    }
    val mine = t?.id == lg.userTeamId
    if (lead && t != null) {
        Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(18.dp)).then(if (open != null) Modifier.clickable(onClick = open) else Modifier)) {
            TeamBand(t) { fg, accent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("%02d · %s".format(i + 1, s.kicker.uppercase()), style = T.label, color = accent, modifier = Modifier.weight(1f))
                    TeamBadge(t, 36.dp)
                }
                Spacer(Modifier.height(6.dp))
                Text(s.head, style = T.title.copy(fontSize = 30.sp, lineHeight = 31.sp), color = fg)
                Spacer(Modifier.height(8.dp))
                Text(s.body, style = T.body, color = fg.copy(alpha = 0.92f))
            }
        }
        return
    }
    val stripe = t?.let { teamInk(it) } ?: P.accent
    Row(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (mine) P.accentSoft else P.surface)
            .then(if (open != null) Modifier.clickable(onClick = open) else Modifier).height(IntrinsicSize.Min),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("%02d".format(i + 1), style = T.numSm.copy(fontSize = 16.sp), color = P.faint)
                Spacer(Modifier.width(8.dp))
                Tag(s.kicker.uppercase(), if (mine) P.accent else stripe)
                Spacer(Modifier.weight(1f))
                if (t != null) TeamBadge(t, 28.dp)
            }
            Spacer(Modifier.height(6.dp))
            Text(s.head, style = T.h2, color = P.ink)
            Spacer(Modifier.height(4.dp))
            Text(s.body, style = T.body, color = P.sub)
        }
    }
}

// ---------- 4: the title race ----------

private fun LazyListScope.titleRace(vm: GameViewModel, lg: League, pv: Preview) {
    val byTitle = lg.teams.filter { odds(pv, it).f4 > 0 }.sortedByDescending { odds(pv, it).title * 10 + odds(pv, it).f4 }
    val max = byTitle.firstOrNull()?.let { odds(pv, it).title }?.coerceAtLeast(0.01) ?: 1.0
    val tiers = listOf(
        "The favorites" to byTitle.filter { odds(pv, it).title >= 0.1 },
        "Contenders" to byTitle.filter { odds(pv, it).title >= 0.04 && odds(pv, it).title < 0.1 },
        "Dark horses" to byTitle.filter { odds(pv, it).title >= 0.01 && odds(pv, it).title < 0.04 }.take(10),
        "Long shots" to byTitle.filter { odds(pv, it).title < 0.01 }.take(6),
    ).filter { it.second.isNotEmpty() }
    item {
        val contenders = byTitle.count { odds(pv, it).title >= 0.05 }
        Deck("We played the season 400 times, from the first tip to the title game. $contenders teams won at least one in twenty.")
    }
    tiers.forEach { (name, teams) ->
        item {
            Section(name)
            GroupList(teams) { t ->
                val o = odds(pv, t)
                Column(Modifier.fillMaxWidth().then(if (t.id == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${pv.proj.getValue(t.id).nat}", style = T.numSm.copy(fontSize = 16.sp), color = P.sub, modifier = Modifier.width(30.dp))
                        TeamBadge(t, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Final Four ${odd(o.f4)} · Sweet 16 ${odd(o.s16)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                        }
                        Text(odd(o.title), style = T.num.copy(fontSize = 22.sp), color = P.ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    GrowBar((o.title / max).toFloat(), Modifier.fillMaxWidth().padding(start = 30.dp), teamInk(t), 6.dp)
                    if (o.title >= 0.04) Text(remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.collegeEdge(lg, pv, t) }, style = T.small.copy(fontSize = 12.sp), color = P.sub,
                        modifier = Modifier.padding(start = 30.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    item {
        // Where the title is likely to come from, conference by conference.
        val byConf = lg.confs.map { c -> c to c.teamIds.sumOf { odds(pv, lg.team(it)).title } }.sortedByDescending { it.second }
        val shown = byConf.filter { it.second >= 0.02 }.take(7)
        val rest = 1.0 - shown.sumOf { it.second }
        Section("Title odds by conference")
        Panel {
            shown.forEach { (c, v) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(c.abbr, style = T.bodyB, color = P.ink, modifier = Modifier.width(64.dp), maxLines = 1)
                    GrowBar(v.toFloat(), Modifier.weight(1f), if (c.id == lg.user.confId) P.accent else P.sub, 10.dp)
                    Text(odd(v), style = T.numSm.copy(fontSize = 15.sp), color = P.ink, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
            if (rest >= 0.005) Text("Everyone else: ${odd(rest)}", style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

// ---------- 5-6: top 25 ----------

private fun LazyListScope.topFive(vm: GameViewModel, lg: League, pv: Preview) {
    item { Deck("The five teams with the best shot at cutting down the nets, and what could stop them.") }
    ranked(lg, pv).take(5).forEach { t -> item { Spacer(Modifier.height(12.dp)); TeamFeature(vm, lg, pv, t) } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TeamFeature(vm: GameViewModel, lg: League, pv: Preview, t: Team) {
    val pr = pv.proj.getValue(t.id)
    val o = odds(pv, t)
    val star = Previews.star(lg, t)
    val nc = Previews.newcomer(lg, t)
    val (st, ppg) = Previews.returning(lg, t)
    val (forIt, against) = Previews.case(lg, pv, t)
    val (strengths, concerns) = Previews.scouting(lg, t)
    val last = t.history.lastOrNull()?.takeIf { it.season == lg.season - 1 }
    val ink = teamInk(t)
    Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.surface)) {
        Box(Modifier.clickable { vm.push(Route.TeamPage(t.id)) }) {
            TeamBand(t) { fg, accent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${pr.nat}", style = T.hero.copy(fontSize = 58.sp, lineHeight = 58.sp), color = accent, modifier = Modifier.width(56.dp))
                    TeamBadge(t, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school.uppercase(), style = T.title.copy(fontSize = 26.sp, lineHeight = 27.sp), color = fg, maxLines = 2)
                        Text("${t.mascot} · ${lg.conf(t.confId).abbr}", style = T.small, color = fg.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    listOf(
                        "PROJECTED" to "${pr.w}-${pr.l}",
                        "WIN RANGE" to "${o.lo}-${o.hi}",
                        "LAST SEASON" to (last?.let { "${it.w}-${it.l}" } ?: "—"),
                    ).forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.75f))
                            Text(v, style = T.numSm, color = fg)
                        }
                    }
                }
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OddsCell(lg.conf(t.confId).abbr + " title", o.conf, ink, Modifier.weight(1f))
                OddsCell("Final Four", o.f4, ink, Modifier.weight(1f))
                OddsCell("Title", o.title, ink, Modifier.weight(1f), scale = 0.5)
            }
            Spacer(Modifier.height(14.dp))
            if (Previews.hasLastSeason(lg)) Row(verticalAlignment = Alignment.CenterVertically) {
                StarterDots(st, ink)
                Spacer(Modifier.width(10.dp))
                Text("$st of 5 starters back · ${f1(ppg)} ppg returning", style = T.small, color = P.sub)
            } else Text(CollegeHistory.line(lg, t), style = T.small, color = P.sub)
            if (star != null) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.clickable { vm.openPlayer(star.id) }, verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(star, t, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("THE STAR", style = T.label.copy(fontSize = 9.sp), color = P.accent)
                        Text("${star.name} · ${star.posLabel} · ${star.cls}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(lastStats(star), style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    }
                    Ring(star.ovr, 38.dp, textSize = 14.sp)
                }
            }
            if (nc != null) {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.clickable { vm.openPlayer(nc.id) }, verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(nc, t, 40.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("X-FACTOR", style = T.label.copy(fontSize = 9.sp), color = P.accent)
                        Text("${nc.name} · ${nc.posLabel} · ${nc.cls}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (Previews.isTransfer(nc)) "Transfer from ${Previews.cameFrom(lg, nc)}" else "${nc.stars}-star freshman, No. ${nc.recruitRank} recruit", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    }
                    Ring(nc.ovr, 38.dp, textSize = 14.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text("WHY THEY'LL WIN IT", style = T.label, color = P.good)
            Text(forIt, style = T.body, color = P.ink)
            Spacer(Modifier.height(8.dp))
            Text("WHY THEY WON'T", style = T.label, color = P.warn)
            Text(against, style = T.body, color = P.ink)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                strengths.forEach { Tag(it.uppercase(), P.good) }
                concerns.forEach { Tag(it.uppercase(), P.warn) }
            }
        }
    }
}

private fun LazyListScope.restOfTop25(vm: GameViewModel, lg: League, pv: Preview) {
    val r = ranked(lg, pv)
    item {
        Deck("Nos. 6 through 25, with how far each moved from last season's final poll.")
        Spacer(Modifier.height(8.dp))
        GroupList(r.subList(5, 25)) { t -> RankRow(vm, lg, pv, t) }
        Section("Also receiving votes")
        Text(r.subList(25, 35).joinToString(", ") { it.school }, style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
        val u = lg.user
        if (pv.proj.getValue(u.id).nat > 25) {
            Section("Your team")
            GroupList(listOf(u)) { t -> RankRow(vm, lg, pv, t) }
        }
    }
}

@Composable
private fun RankRow(vm: GameViewModel, lg: League, pv: Preview, t: Team) {
    val pr = pv.proj.getValue(t.id)
    val o = odds(pv, t)
    val mine = t.id == lg.userTeamId
    Column(
        Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier)
            .clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${pr.nat}", style = T.num.copy(fontSize = 24.sp), color = if (mine) P.accent else P.ink, modifier = Modifier.width(40.dp))
            Movement(pr.nat, lastAp(lg, t))
            TeamBadge(t, 30.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(lg.conf(t.confId).abbr + " · proj. ${pr.w}-${pr.l}" + if (t.gp > 0) " · now ${t.record}${if (t.ap > 0) ", AP #${t.ap}" else ""}" else "", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(odd(o.f4), style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
                Text("FINAL 4", style = T.label.copy(fontSize = 8.sp), color = P.faint)
            }
        }
        val last = t.history.lastOrNull()?.takeIf { it.season == lg.season - 1 }
        val take = remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.college(lg, pv, t, 3) }
        Column(Modifier.padding(start = 74.dp, top = 6.dp)) {
            if (last != null) Text("Last season: ${last.w}-${last.l}, ${last.result.ifEmpty { "no postseason" }}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(take, style = T.small, color = P.ink, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

// ---------- 7: all-america ----------

private fun LazyListScope.allAmerica(vm: GameViewModel, lg: League, pv: Preview) {
    val aa = players(lg, pv.allAmerica)
    item { Deck("The best players in the country, before a single game is played.") }
    (pv.poy.firstOrNull()?.let { lg.playerOrNull(it.pid) }?.takeIf { it.teamId >= 0 } ?: aa.firstOrNull())?.let { p ->
        item {
            val t = lg.team(p.teamId)
            Spacer(Modifier.height(10.dp))
            Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable { vm.openPlayer(p.id) }) {
                TeamBand(t) { fg, accent ->
                    Text("PRESEASON PLAYER OF THE YEAR", style = T.label, color = accent)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Jersey(p.number, Color(t.secondary), Color(t.primary), 84.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name.uppercase(), style = T.title.copy(fontSize = 28.sp, lineHeight = 29.sp), color = fg)
                            Text("${t.school} · ${p.posLabel} · ${p.cls}", style = T.small, color = fg.copy(alpha = 0.85f))
                            Text(lastStats(p), style = T.small, color = fg.copy(alpha = 0.85f))
                        }
                    }
                }
            }
        }
    }
    listOf("First team" to aa.take(5), "Second team" to aa.drop(5).take(5), "Third team" to aa.drop(10).take(5)).forEach { (title, list) ->
        if (list.isNotEmpty()) item {
            Section(title)
            GroupList(list, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.posLabel} · ${p.cls}\n${lastStats(p)}") }
        }
    }
    if (pv.poy.isNotEmpty()) item {
        Section("Player of the Year odds")
        val max = pv.poy.maxOf { it.p }.coerceAtLeast(0.01)
        GroupList(pv.poy.mapNotNull { o -> lg.playerOrNull(o.pid)?.takeIf { it.teamId >= 0 }?.let { it to o.p } }) { (p, v) ->
            Column(Modifier.fillMaxWidth().clickable { vm.openPlayer(p.id) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(lg.team(p.teamId), 26.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1)
                        Text("${lg.team(p.teamId).school} · ${p.posLabel} · ${p.cls}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    }
                    Text(odd(v), style = T.numSm, color = P.ink)
                }
                Spacer(Modifier.height(6.dp))
                GrowBar((v / max).toFloat(), Modifier.fillMaxWidth().padding(start = 36.dp), teamInk(lg.team(p.teamId)), 5.dp)
            }
        }
    }
    item {
        Section("Award picks")
        val picks = listOfNotNull(
            lg.playerOrNull(pv.dpoy)?.takeIf { it.teamId >= 0 }?.let { "Defensive Player of the Year" to it },
            players(lg, pv.freshmen).firstOrNull()?.let { "Freshman of the Year" to it },
        )
        GroupList(picks, inset = 70.dp) { (award, p) -> PlayerLine(vm, lg, lg.team(p.teamId), p, "$award · ${lg.team(p.teamId).school}") }
    }
}

// ---------- 8: position rankings ----------

private fun LazyListScope.positions(vm: GameViewModel, lg: League) {
    val active = lg.players.filter { it.teamId >= 0 }
    item { Deck("The top five at every position, and where your best player at each spot ranks.") }
    listOf(Pos.PG to "Point guards", Pos.SG to "Shooting guards", Pos.SF to "Small forwards", Pos.PF to "Power forwards", Pos.C to "Centers").forEach { (pos, name) ->
        item {
            val list = active.filter { it.pos == pos }.sortedByDescending { Previews.value(it) }
            Section(name)
            GroupList(list.take(5).withIndex().toList(), inset = 92.dp) { (i, p) ->
                PositionRow(vm, lg, p, i + 1)
            }
            val mineIdx = list.indexOfFirst { it.teamId == lg.userTeamId }
            if (mineIdx >= 5) {
                Spacer(Modifier.height(6.dp))
                GroupList(listOf(list[mineIdx]), inset = 92.dp) { p -> PositionRow(vm, lg, p, mineIdx + 1) }
            }
        }
    }
}

@Composable
private fun PositionRow(vm: GameViewModel, lg: League, p: Player, rank: Int) {
    val t = lg.team(p.teamId)
    val mine = p.teamId == lg.userTeamId
    ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (mine || p.id == lg.heroId) Modifier.background(P.accentSoft) else Modifier) {
        Text("$rank", style = T.numSm, color = if (rank <= 5) P.accent else P.sub, modifier = Modifier.width(36.dp))
        PlayerJersey(p, t, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.school} · ${p.cls} · ${p.style.label}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Ring(p.ovr, 38.dp, textSize = 14.sp)
    }
}

// ---------- 9: freshmen ----------

private fun LazyListScope.freshmen(vm: GameViewModel, lg: League, pv: Preview) {
    val classes = lg.teams.map { t ->
        val fr = lg.rosterOf(t).filter { it.year == 1 && it.recruitRank > 0 }
        Triple(t, fr, fr.sumOf { it.stars * it.stars })
    }.filter { it.second.isNotEmpty() }.sortedByDescending { it.third }.take(8)
    item {
        Deck("Who won recruiting, and the freshmen who could start from day one.")
        Section("Top recruiting classes")
        val max = classes.firstOrNull()?.third?.coerceAtLeast(1) ?: 1
        GroupList(classes.withIndex().toList()) { (i, c) ->
            val (t, fr, pts) = c
            val five = fr.count { it.stars == 5 }
            val four = fr.count { it.stars == 4 }
            Column(Modifier.fillMaxWidth().then(if (t.id == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(30.dp))
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school, style = T.bodyB, color = P.ink)
                        Text(listOfNotNull(if (five > 0) "$five five-star" else null, if (four > 0) "$four four-star" else null, "${fr.size} signees").joinToString(" · "), style = T.small, color = P.sub)
                    }
                    Text(fr.maxBy { it.ovr }.last, style = T.small, color = P.sub)
                }
                Spacer(Modifier.height(6.dp))
                GrowBar(pts / max.toFloat(), Modifier.fillMaxWidth().padding(start = 30.dp), teamInk(t), 5.dp)
            }
        }
        Section("Top 10 freshmen")
        GroupList(players(lg, pv.freshmen), inset = 70.dp) { p ->
            PlayerLine(vm, lg, lg.team(p.teamId), p, "No. ${p.recruitRank} recruit · ${lg.team(p.teamId).school}\n${p.posLabel} · ${p.hometown}") {
                Column(horizontalAlignment = Alignment.End) {
                    Ring(p.ovr, 38.dp, textSize = 14.sp)
                    Stars(p.stars, 10.sp)
                }
            }
        }
    }
}

// ---------- 10: transfers ----------

private fun LazyListScope.transfers(vm: GameViewModel, lg: League, pv: Preview) {
    val list = players(lg, pv.transfers)
    val winners = Previews.portalWinners(lg)
    item {
        Deck("The portal moves that matter most, the teams starting over and the seniors taking their last ride.")
        Section("Impact transfers")
        if (list.isEmpty()) Panel { Text("A quiet portal: no impact transfers moved this offseason, so rosters stayed together.", style = T.body, color = P.sub) }
        GroupList(list, inset = 70.dp) { p ->
            PlayerLine(vm, lg, lg.team(p.teamId), p, "${Previews.cameFrom(lg, p)} → ${lg.team(p.teamId).school} · ${p.posLabel}\n${lastStats(p)}")
        }
        if (winners.isNotEmpty()) {
            Section("Portal winners")
            GroupList(winners) { (t, ps) ->
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school, style = T.bodyB, color = P.ink)
                        val scale = LocalRatingScale.current
                        Text(ps.take(3).joinToString(", ") { "${it.last} (${scale(it.ovr)})" }, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("+${ps.size}", style = T.numSm, color = P.good)
                }
            }
        }
        // Good programs that lost the most.
        val rebuild = lg.teams.filter { lastAp(lg, it) > 0 || it.prestige >= 70 }
            .map { it to Previews.returning(lg, it) }.sortedWith(compareBy({ it.second.first }, { it.second.second })).take(5)
        if (rebuild.isNotEmpty() && Previews.hasLastSeason(lg)) {
            Section("Starting over")
            GroupList(rebuild) { (t, back) ->
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1)
                        Text("${f1(back.second)} ppg back · No. ${pv.proj.getValue(t.id).nat} now", style = T.small, color = P.sub)
                    }
                    StarterDots(back.first, P.warn)
                }
            }
        }
        val seniors = lg.teams.flatMap { lg.rosterOf(it) }.filter { it.year >= 4 && !it.walkOn }.sortedByDescending { it.ovr }.take(8)
        if (seniors.isNotEmpty()) {
            Section("Last call: top seniors")
            GroupList(seniors, inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.posLabel} · final season\n${lastStats(p)}") }
        }
    }
}

// ---------- 11: breakouts ----------

private fun LazyListScope.breakouts(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("Next year's All-Americans, and stars hiding in plain sight.")
        Section("Breakout candidates")
        GroupList(players(lg, pv.breakouts), inset = 70.dp) { p ->
            PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} · ${p.posLabel} · ${p.cls}\nCeiling ${shown(p.pot)} · ${lastStats(p)}")
        }
        Section("Under the radar")
        GroupList(players(lg, pv.underRadar), inset = 70.dp) { p -> PlayerLine(vm, lg, lg.team(p.teamId), p, "${lg.team(p.teamId).school} (${lg.conf(lg.team(p.teamId).confId).abbr}) · ${p.posLabel} · ${p.cls}\n${lastStats(p)}") }
    }
}

// ---------- 12-14: conferences ----------

@Composable
private fun ConferenceDepth(vm: GameViewModel, lg: League, pv: Preview, confId: Int) {
    val c = lg.conf(confId)
    val started = lg.teams.any { it.cw + it.cl > 0 }
    val now = if (started) Season.standings(lg, c.id).withIndex().associate { (i, t) -> t.id to i + 1 } else emptyMap()
    val order = c.teamIds.map { lg.team(it) }.sortedBy { pv.proj.getValue(it.id).confRank }
    Deck("Predicted order of finish, every team's chance to win the league and a word on each.")
    Spacer(Modifier.height(8.dp))
    Panel {
        Row {
            Stat("Projected bids", f1(pv.bids[c.id] ?: 0.0), Modifier.weight(1f), style = T.numSm)
            Stat("Title favorite", order.firstOrNull()?.abbr ?: "—", Modifier.weight(1f), style = T.numSm)
            Stat("Its odds", order.firstOrNull()?.let { odd(odds(pv, it).conf) } ?: "—", Modifier.weight(1f), style = T.numSm)
        }
    }
    pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.takeIf { it.teamId >= 0 }?.let { p ->
        Spacer(Modifier.height(8.dp))
        Panel(onClick = { vm.openPlayer(p.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, lg.team(p.teamId), 48.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("PRESEASON PLAYER OF THE YEAR", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Text(p.name, style = T.head, color = P.ink)
                    Text("${lg.team(p.teamId).school} · ${p.posLabel} · ${p.cls}${lastLine(p)}", style = T.small, color = P.sub)
                }
                Ring(p.ovr, 44.dp)
            }
        }
    }
    Section("Predicted finish")
    val max = order.maxOfOrNull { odds(pv, it).conf }?.coerceAtLeast(0.01) ?: 1.0
    GroupList(order) { t ->
        val pr = pv.proj.getValue(t.id)
        val o = odds(pv, t)
        val mine = t.id == lg.userTeamId
        val star = Previews.star(lg, t)
        Column(Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${pr.confRank}", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(30.dp))
                TeamBadge(t, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${pr.cw}-${pr.cl} in league · ${pr.w}-${pr.l} overall · NCAA ${odd(o.ncaa)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(odd(o.conf), style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
                    if (started) {
                        val n = now[t.id] ?: 0
                        Text("now ${Previews.ordinal(n)}", style = T.small.copy(fontSize = 11.sp), color = when { n < pr.confRank -> P.good; n > pr.confRank -> P.bad; else -> P.sub })
                    }
                }
            }
            Spacer(Modifier.height(5.dp))
            GrowBar((o.conf / max).toFloat(), Modifier.fillMaxWidth().padding(start = 66.dp), teamInk(t), 4.dp)
            val take = remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.college(lg, pv, t, 2, from = 1) }
            Text(take.ifEmpty { star?.let { "${it.name} (${it.posLabel}, ${it.cls}) leads the way." } ?: "" }, style = T.small, color = P.sub, modifier = Modifier.padding(start = 66.dp, top = 4.dp))
        }
    }
    Text("Right column: chance to win the regular-season title.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
}

private fun LazyListScope.powerRankings(vm: GameViewModel, lg: League, pv: Preview) {
    val strength = lg.confs.map { c -> c to c.teamIds.map { lg.team(it).elo }.sortedDescending().take(8).average() }.sortedByDescending { it.second }
    item {
        Deck("Every league ranked by its best eight teams, with the NCAA bids we expect it to earn.")
        Section("Conference power rankings")
        val hi = strength.first().second
        val lo = strength.last().second
        GroupList(strength.take(12).withIndex().toList()) { (i, cs) ->
            val (c, v) = cs
            Column(Modifier.fillMaxWidth().then(if (c.id == lg.user.confId) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = T.numSm.copy(fontSize = 16.sp), color = P.accent, modifier = Modifier.width(30.dp))
                    Text(c.name, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${f1(pv.bids[c.id] ?: 0.0)} bids", style = T.small, color = P.sub)
                }
                Spacer(Modifier.height(5.dp))
                GrowBar(((v - lo) / (hi - lo).coerceAtLeast(1.0)).toFloat().coerceAtLeast(0.03f), Modifier.fillMaxWidth().padding(start = 30.dp), if (c.id == lg.user.confId) P.accent else P.sub, 6.dp)
            }
        }
    }
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
                    Text(t.school, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pr.confRank == 1) Tag("PICK", P.gold)
                    Spacer(Modifier.width(8.dp))
                    Text(odd(odds(pv, t).conf), style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
                }
            }
            pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.takeIf { it.teamId >= 0 }?.let { p ->
                Text("Player of the Year pick: ${p.name}, ${lg.team(p.teamId).school}", style = T.small, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
            }
        }
    }
}

private fun LazyListScope.midMajors(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("Every other league in one place: our pick, its chance to go dancing and the player to watch.")
        Spacer(Modifier.height(8.dp))
        GroupList(lg.confs.filter { it.tier >= 1 && it.id != lg.user.confId }.sortedByDescending { c -> c.teamIds.maxOf { lg.team(it).elo } }) { c ->
            val pick = c.teamIds.map { lg.team(it) }.minBy { pv.proj.getValue(it.id).confRank }
            val poy = pv.confPoy[c.id]?.let { lg.playerOrNull(it) }?.takeIf { it.teamId >= 0 }
            ListRow(onClick = { vm.push(Route.TeamPage(pick.id)) }) {
                Text(c.abbr, style = T.numSm.copy(fontSize = 15.sp), color = P.accent, modifier = Modifier.width(58.dp), maxLines = 1)
                TeamBadge(pick, 26.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(pick.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (poy != null) Text("POY: ${poy.name}, ${lg.team(poy.teamId).abbr}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(remember(pick.id, lg.season) { com.nh.hoopscoach.game.Takes.college(lg, pv, pick, 1) }, style = T.small.copy(fontSize = 12.sp), color = P.ink)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(odd(odds(pv, pick).ncaa), style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
                    Text("NCAA", style = T.label.copy(fontSize = 8.sp), color = P.faint)
                }
            }
        }
    }
}

// ---------- 15: team report ----------

private fun LazyListScope.teamReport(vm: GameViewModel, lg: League, pv: Preview) {
    val u = lg.user
    val pr = pv.proj.getValue(u.id)
    val o = odds(pv, u)
    val roster = lg.rosterOf(u)
    val starters = u.plan.slots.mapNotNull { s -> roster.firstOrNull { it.id == s.starter } }
    val bench = u.plan.slots.mapNotNull { s -> roster.firstOrNull { it.id == s.reserve } }
    val newcomers = roster.filter { Previews.isNewcomer(it) }.sortedByDescending { it.ovr }
    val (st, ppg) = Previews.returning(lg, u)
    item {
        val ink = teamInk(u)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.surface)) {
            TeamBand(u) { fg, accent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(u, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Previews.expectation(pr.nat).uppercase(), style = T.label, color = accent)
                        Text("${u.school} ${u.mascot}".uppercase(), style = T.title.copy(fontSize = 24.sp, lineHeight = 25.sp), color = fg)
                        Text("Coach ${lg.userCoach.name}", style = T.small, color = fg.copy(alpha = 0.85f))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    listOf("NATIONAL" to "No. ${pr.nat}", lg.conf(u.confId).abbr to Previews.ordinal(pr.confRank), "PROJECTED" to "${pr.w}-${pr.l}", "RANGE" to "${o.lo}-${o.hi} W").forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.75f), maxLines = 1)
                            Text(v, style = T.numSm.copy(fontSize = 17.sp), color = fg, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text("SEASON ODDS", style = T.label, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OddsCell("${lg.conf(u.confId).abbr} title", o.conf, ink, Modifier.weight(1f))
                    OddsCell("NCAA bid", o.ncaa, ink, Modifier.weight(1f))
                    OddsCell("Sweet 16", o.s16, ink, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OddsCell("Final Four", o.f4, ink, Modifier.weight(1f))
                    OddsCell("Title", o.title, ink, Modifier.weight(1f))
                    val known = Previews.hasLastSeason(lg)
                    Column(Modifier.weight(1f)) {
                        if (known) {
                            Text("STARTERS BACK", style = T.label.copy(fontSize = 9.sp), color = P.sub, maxLines = 1)
                            Text("$st of 5", style = T.numSm.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.ink)
                            Spacer(Modifier.height(4.dp))
                            StarterDots(st, ink)
                        } else {
                            Text("NATIONAL TITLES", style = T.label.copy(fontSize = 9.sp), color = P.sub, maxLines = 1)
                            Text("${CollegeHistory.titles(lg, u).size}", style = T.numSm.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.ink)
                        }
                    }
                }
                Text(if (Previews.hasLastSeason(lg)) "${f1(ppg)} points a game return from last season." else CollegeHistory.line(lg, u), style = T.small, color = P.sub, modifier = Modifier.padding(top = 10.dp))
                if (u.gp > 0) {
                    val confPart = if (u.cw + u.cl > 0) ", ${Previews.ordinal(Season.standings(lg, u.confId).indexOf(u) + 1)} in the ${lg.conf(u.confId).abbr}" else ""
                    Text("So far: ${u.record}${if (u.ap > 0) ", AP No. ${u.ap}" else ""}$confPart.", style = T.small, color = P.accent, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Section("Scouting grades")
        val peers = lg.teams.filter { abs(pv.proj.getValue(it.id).nat - pr.nat) <= 30 }
        Panel {
            Previews.profile(lg, u, peers).forEach { (area, p) ->
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(area, style = T.small, color = P.ink, modifier = Modifier.width(132.dp), maxLines = 1)
                    GrowBar(p.toFloat().coerceAtLeast(0.03f), Modifier.weight(1f), when { p >= 0.66 -> P.good; p >= 0.33 -> P.accent; else -> P.warn }, 8.dp)
                    Text(letter(p), style = T.numSm.copy(fontSize = 16.sp), color = P.ink, textAlign = TextAlign.End, modifier = Modifier.width(38.dp))
                }
            }
            Text("Each area graded against the ${peers.size - 1} teams closest to ${u.school} in our rankings.", style = T.small.copy(fontSize = 12.sp), color = P.sub, modifier = Modifier.padding(top = 6.dp))
        }
        Section("Key questions")
        GroupList(Previews.questions(lg, pv, u).withIndex().toList()) { (i, q) ->
            ListRow {
                Text("${i + 1}", style = T.num.copy(fontSize = 22.sp), color = P.accent, modifier = Modifier.width(30.dp))
                Text(q, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
            }
        }
        Section("Projected starting five")
        GroupList(starters, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.posLabel} · ${p.cls} · ${p.style.label}\n${lastStats(p)}") }
        if (bench.isNotEmpty()) {
            Section("Off the bench")
            GroupList(bench, inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.posLabel} · ${p.cls} · ${p.style.label}") }
        }
        if (newcomers.isNotEmpty()) {
            Section("Newcomers")
            GroupList(newcomers.take(5), inset = 70.dp) { p ->
                val from = p.career.lastOrNull()?.teamId?.takeIf { it >= 0 && it != u.id }?.let { "Transfer from ${lg.team(it).school}" } ?: if (p.recruitRank > 0) "${p.stars}★ freshman · No. ${p.recruitRank}" else "Walk-on"
                PlayerLine(vm, lg, u, p, "${p.posLabel} · $from")
            }
        }
        val circle = lg.games.filter { it.involves(u.id) && it.day <= com.nh.hoopscoach.game.Schedule.REG_DAYS }
            .filter { g -> lg.rivalry(g) != null || pv.proj.getValue(g.opp(u.id)).nat <= 25 }.sortedBy { it.day }.take(6)
        if (circle.isNotEmpty()) {
            Section("Circle these dates")
            GroupList(circle) { g ->
                val opp = lg.team(g.opp(u.id))
                val win = com.nh.hoopscoach.game.Rankings.winProbFor(lg, g, u.id)
                ListRow(onClick = { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(opp.id)) }) {
                    Text("G${g.day}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(40.dp))
                    Text(g.site(u.id).let { if (it == "N") "vs" else it }, style = T.small, color = P.sub, modifier = Modifier.width(24.dp))
                    TeamBadge(opp, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(opp.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(lg.rivalry(g)?.title ?: "No. ${pv.proj.getValue(opp.id).nat}", style = T.small.copy(fontSize = 12.sp), color = if (lg.rivalry(g) != null) P.bad else P.sub, maxLines = 1)
                    }
                    if (g.played) Text(if ((g.home == u.id) == (g.hs > g.aws)) "W" else "L", style = T.numSm, color = if ((g.home == u.id) == (g.hs > g.aws)) P.good else P.bad)
                    else Text("${(win * 100).toInt()}%", style = T.numSm.copy(fontSize = 15.sp), color = if (win >= 0.5) P.good else P.warn)
                }
            }
            Text("Right column: our chance to win each game.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
        }
        pv.odds[u.id]?.let { od -> ScoutingReport(vm, lg, u, od.lo, od.hi) }
        Section("The bottom line")
        Panel { Text(remember(u.id, lg.season) { com.nh.hoopscoach.game.Takes.collegeBottom(lg, pv, u) }, style = T.body, color = P.ink) }
    }
}

// ---------- 16: coaches ----------

private fun LazyListScope.coaches(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Deck("The best in the business, the new hires and the men who need a big year.")
        Section("Top 10 coaches")
        GroupList(lg.coaches.filter { it.teamId >= 0 }.sortedByDescending { it.rating }.take(10).withIndex().toList()) { (i, c) ->
            val t = lg.team(c.teamId)
            ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (c.isUser) Modifier.background(P.accentSoft) else Modifier) {
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
        val hires = lg.teams.filter { it.id != lg.userTeamId && Previews.newCoach(lg, it) }.sortedByDescending { it.prestige }.take(6)
        if (hires.isNotEmpty()) {
            Section("New faces")
            GroupList(hires) { t ->
                val c = lg.coachOf(t)
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${c.name}, ${t.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Picked ${Previews.ordinal(pv.proj.getValue(t.id).confRank)} in the ${lg.conf(t.confId).abbr} in year one", style = T.small, color = P.sub)
                    }
                    Tag("NEW", P.accent)
                }
            }
        }
        if (pv.hotSeat.isNotEmpty()) {
            Section("On the hot seat")
            GroupList(pv.hotSeat.map { lg.team(it) }) { t ->
                val c = lg.coachOf(t)
                val last = t.history.lastOrNull()
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${c.name}, ${t.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (last != null) Text("${last.w}-${last.l} and ${last.result.lowercase()} last season · NCAA ${odd(odds(pv, t).ncaa)}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                    Text("${me.w}-${me.l} career · ${me.tourneys} NCAA ${if (me.tourneys == 1) "trip" else "trips"}", style = T.small, color = P.sub)
                }
                Ring(me.rating, 44.dp)
            }
            if (!lg.playerMode) {
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("JOB SECURITY", style = T.label, color = P.sub, modifier = Modifier.width(110.dp))
                    GrowBar(me.security / 100f, Modifier.weight(1f), when { me.security >= 60 -> P.good; me.security >= 35 -> P.warn; else -> P.bad }, 8.dp)
                    Text("${me.security}", style = T.numSm.copy(fontSize = 16.sp), color = P.ink, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                }
            }
        }
        lg.teams.getOrNull(pv.cotyTeam)?.let { t ->
            Text("Our Coach of the Year pick: ${lg.coachOf(t).name}, ${t.school}.", style = T.small, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
        }
    }
}

// ---------- 17: games to watch ----------

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
                val homeWin = com.nh.hoopscoach.game.Rankings.winProbFor(lg, g, h.id)
                ListRow(onClick = if (g.played) ({ vm.push(Route.Box(g.id)) }) else null) {
                    Text("G${g.day}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(40.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${a.school} ${if (g.neutral) "vs" else "at"} ${h.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(riv?.let { if (it.name.isEmpty()) "Rivalry game" else "Rivalry · ${it.name}" } ?: "No. ${pv.proj.getValue(a.id).nat} vs No. ${pv.proj.getValue(h.id).nat}", style = T.small, color = if (riv != null) P.bad else P.sub)
                        if (!g.played) Text("Our pick: ${if (homeWin >= 0.5) h.abbr else a.abbr} (${(maxOf(homeWin, 1 - homeWin) * 100).toInt()}%)", style = T.small.copy(fontSize = 12.sp), color = P.accent)
                    }
                    if (g.played) Text("${g.aws}-${g.hs}", style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
                    else Row { TeamBadge(a, 22.dp); Spacer(Modifier.width(4.dp)); TeamBadge(h, 22.dp) }
                }
            }
        }
    }
}

// ---------- 18: predictions ----------

private fun LazyListScope.predictions(vm: GameViewModel, lg: League, pv: Preview) {
    item {
        Section("Final Four")
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                pv.finalFour.forEachIndexed { i, id ->
                    val t = lg.team(id)
                    Column(Modifier.weight(1f).clickable { vm.push(Route.TeamPage(id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                        TeamBadge(t, 46.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(t.abbr, style = T.bodyB, color = P.ink)
                        Text(odd(odds(pv, t).f4), style = T.small, color = P.sub)
                        if (i == 0) Tag("CHAMPION", P.gold) else Text(" ", style = T.label)
                    }
                }
            }
        }
        Text("Under each team: its chance to reach the Final Four.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
        Section("Long shots")
        GroupList(listOf("Cinderella" to lg.team(pv.cinderella), "Sleeper" to lg.team(pv.sleeper))) { (label, t) ->
            ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                Text(label.uppercase(), style = T.label, color = P.accent, modifier = Modifier.width(92.dp))
                TeamBadge(t, 28.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(t.school, style = T.bodyB, color = P.ink)
                    Text("${lg.conf(t.confId).abbr} · No. ${pv.proj.getValue(t.id).nat} · Sweet 16 ${odd(odds(pv, t).s16)}", style = T.small, color = P.sub)
                }
            }
        }
        Section("Conference champions")
        Panel(padding = PaddingValues(12.dp)) {
            lg.confs.sortedBy { it.tier }.chunked(2).forEach { pair ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    pair.forEach { c ->
                        val pick = c.teamIds.map { lg.team(it) }.minBy { pv.proj.getValue(it.id).confRank }
                        Row(Modifier.weight(1f).clickable { vm.push(Route.TeamPage(pick.id)) }, verticalAlignment = Alignment.CenterVertically) {
                            Text(c.abbr, style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.width(52.dp), maxLines = 1)
                            TeamBadge(pick, 22.dp)
                            Spacer(Modifier.width(6.dp))
                            Text(pick.abbr, style = T.bodyB, color = if (pick.id == lg.userTeamId) P.accent else P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(odd(odds(pv, pick).conf), style = T.small.copy(fontSize = 12.sp), color = P.sub)
                            Spacer(Modifier.width(8.dp))
                        }
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        val aa = players(lg, pv.allAmerica)
        val awards = listOfNotNull(
            (pv.poy.firstOrNull()?.let { lg.playerOrNull(it.pid) }?.takeIf { it.teamId >= 0 } ?: aa.firstOrNull())?.let { "Player of the Year" to "${it.name}, ${lg.team(it.teamId).school}" },
            players(lg, pv.freshmen).firstOrNull()?.let { "Freshman of the Year" to "${it.name}, ${lg.team(it.teamId).school}" },
            lg.playerOrNull(pv.dpoy)?.takeIf { it.teamId >= 0 }?.let { "Defensive POY" to "${it.name}, ${lg.team(it.teamId).school}" },
            lg.teams.getOrNull(pv.cotyTeam)?.let { "Coach of the Year" to "${lg.coachOf(it).name}, ${it.school}" },
        )
        Section("Award picks")
        GroupList(awards) { (k, v) ->
            ListRow {
                Text(k, style = T.small, color = P.accent, modifier = Modifier.width(120.dp))
                Text(v, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
            }
        }
        if (pv.bold.isNotEmpty()) BoldPredictions(pv.bold, pv.graded, pv.outcomes)
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
    val o = pv.odds[u.id]
    Section("Season preview")
    Panel(color = P.hero, onClick = { vm.push(Route.Preview) }) {
        Text("THE ${pv.season} PRESEASON ISSUE · ${pages(lg, pv).size} PAGES", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text("${u.school.uppercase()} PICKED ${Previews.ordinal(pr.confRank).uppercase()} IN THE ${lg.conf(u.confId).abbr}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onHero)
        Spacer(Modifier.height(4.dp))
        Text(
            "No. ${pr.nat} nationally, projected ${pr.w}-${pr.l}" + (o?.let { ", NCAA odds ${odd(it.ncaa)}" } ?: "") +
                ". ${top.school} is the preseason No. 1. Plus storylines, title odds, every conference and our Final Four.",
            style = T.small, color = P.onHero.copy(alpha = 0.9f),
        )
        Spacer(Modifier.height(8.dp))
        Text("Read the issue →", style = T.bodyB, color = P.onHero)
    }
}

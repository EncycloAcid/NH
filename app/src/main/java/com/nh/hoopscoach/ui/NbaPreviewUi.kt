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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import com.nh.hoopscoach.game.Ollama
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
import com.nh.hoopscoach.game.Eras
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NbaPreview
import com.nh.hoopscoach.game.NbaPreviews
import com.nh.hoopscoach.game.NbaProj
import com.nh.hoopscoach.game.Payroll
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.PoyOdds
import com.nh.hoopscoach.game.Previews
import com.nh.hoopscoach.game.ProSeason
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

/** The issue's pages: the fixed ones, with this year's three features after the storylines. */
/** Player mode: the user's player has a page of his own. */
private fun heroPage(lg: League) = lg.playerMode && lg.hero?.teamId?.let { it >= 0 } == true

private fun nbaPageIds(lg: League, pv: NbaPreview) = listOf("cover", "inside") + (if (heroPage(lg)) listOf("you") else emptyList()) + listOf("stories") +
    (if (pv.debates.isNotEmpty()) listOf("debate") else emptyList()) + pv.features.indices.map { "f$it" } +
    listOf("power", "title", "conf0", "conf1", "awards", "best", "offseason", "rookies", "report", "picks")

private fun nbaPages(lg: League, pv: NbaPreview) = listOf(
    "Cover" to "Season Preview",
    "Inside" to "In this issue",
) + (if (heroPage(lg)) listOf("Your player" to (lg.hero?.name ?: "")) else emptyList()) + listOf(
    "Storylines" to "What to watch",
) + (if (pv.debates.isNotEmpty()) listOf("The debate" to "${com.nh.hoopscoach.game.TvShow.showName(lg)} picks a side") else emptyList()) +
    pv.features.map { "Feature" to it.title } + listOf(
    "Power rankings" to "All 30, one to thirty",
    "The title race" to "Who can win it all",
    "Conference call" to lg.conf(0).name,
    "Conference call" to lg.conf(1).name,
    "Award races" to "MVP and the rest",
    "The best" to "Top 30 players",
    "The offseason" to "Who moved",
    "The rookies" to "The new class",
    "Team report" to lg.user.name,
    "The picks" to "Our predictions",
)

private fun proj(pv: NbaPreview, t: Team): NbaProj = pv.proj.getValue(t.id)

/** A last-season line for a pro: "24.1 pts · 6.2 reb · 5.0 ast last season". */
private fun proLast(lg: League, p: Player): String {
    val l = NbaPreviews.lastLine(lg, p) ?: return when { p.proYears == 0 -> "Rookie"; p.proYears == 1 -> "Second season"; else -> "${p.proYears} seasons in the league" }
    return "%.1f pts · %.1f reb · %.1f ast last season".format(l.s.ppg, l.s.rpg, l.s.apg)
}

@Composable
fun NbaPreviewScreen(vm: GameViewModel, lg: League) {
    val pv = remember(lg, lg.season) { NbaPreviews.current(lg) }
    if (pv == null) { vm.back(); return }
    val list = nbaPages(lg, pv)
    val ids = nbaPageIds(lg, pv)
    val pager = rememberPagerState { list.size }
    val scope = rememberCoroutineScope()
    val go = { i: Int -> scope.launch { pager.animateScrollToPage(i.coerceIn(0, list.lastIndex)) }; Unit }
    Column(Modifier.fillMaxSize()) {
        BackBar("Preview ${pv.season}", "Page ${pager.currentPage + 1} of ${list.size} · ${list[pager.currentPage].second}", onBack = { vm.back() }, actions = {
            SmallButton("Skip") { vm.back() }
        })
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            list.indices.forEach { i ->
                // The progress bar fills page by page, the current page's segment taller.
                val h by androidx.compose.animation.core.animateDpAsState(if (i == pager.currentPage) 6.dp else 4.dp, androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 500f), label = "seg")
                val c by androidx.compose.animation.animateColorAsState(if (i <= pager.currentPage) P.accent else P.line, androidx.compose.animation.core.tween(260), label = "segc")
                Box(Modifier.weight(1f).height(h).clip(RoundedCornerShape(2.dp)).background(c).clickable { go(i) })
            }
        }
        HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), beyondViewportPageCount = 1) { page ->
            val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
            Box(
                Modifier.fillMaxSize().graphicsLayer {
                    val o = offset.coerceIn(-1f, 1f)
                    cameraDistance = 16f * density
                    transformOrigin = TransformOrigin(if (o > 0) 1f else 0f, 0.5f)
                    rotationY = -o * 28f
                    alpha = 1f - abs(o) * 0.35f
                },
            ) {
                // The cover lands with a little spring and a sweep of light; the rest turn like pages.
                LazyColumn(Modifier.fillMaxSize().background(P.bg).then(if (page == 0) Modifier.popIn(pv.season).sheen() else Modifier)) {
                    val (kicker, title) = list[page]
                    if (page > 0) item { PageHead(kicker, title, page + 1) }
                    when (val id = ids[page]) {
                        "cover" -> nbaCover(lg, pv, list.size, onRead = { go(1) }, onSkip = { vm.back() })
                        "inside" -> nbaContents(lg, pv, list, go)
                        "stories" -> nbaStories(vm, lg, pv)
                        "you" -> heroSpotlight(vm, lg, pv)
                        "debate" -> debates(lg, pv)
                        "power" -> powerRankings(vm, lg, pv)
                        "title" -> nbaTitleRace(vm, lg, pv)
                        "conf0" -> conference(vm, lg, pv, 0)
                        "conf1" -> conference(vm, lg, pv, 1)
                        "awards" -> awardRaces(vm, lg, pv)
                        "best" -> topPlayers(vm, lg)
                        "offseason" -> offseason(vm, lg)
                        "rookies" -> rookies(vm, lg, pv)
                        "report" -> nbaTeamReport(vm, lg, pv)
                        "picks" -> nbaPredictions(vm, lg, pv)
                        else -> pv.features.getOrNull(id.removePrefix("f").toIntOrNull() ?: -1)?.let { featurePage(vm, lg, it) }
                    }
                    item {
                        Text(
                            "HOOPS DYNASTY PRO PREVIEW ${pv.season} · ${page + 1}",
                            style = T.label.copy(fontSize = 9.sp), color = P.faint, textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().background(P.bg).padding(horizontal = Gutter, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage == 0) SmallButton("Skip") { vm.back() }
            else SmallButton("‹ Prev", enabled = pager.currentPage > 0) { go(pager.currentPage - 1) }
            Text("${pager.currentPage + 1} / ${list.size}", style = T.numSm, color = P.sub, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            if (pager.currentPage < list.lastIndex) SmallButton("Next ›", primary = true) { go(pager.currentPage + 1) }
            else SmallButton("Close", primary = true) { vm.back() }
        }
    }
}

// ---------- the user's player and the TV page ----------

/** His own page: the portrait, what's new around him (home, a last ride, a comeback, his shoe), our odds and his stories. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun LazyListScope.heroSpotlight(vm: GameViewModel, lg: League, pv: NbaPreview) {
    val h = lg.hero ?: return
    val t = lg.teams.getOrNull(h.teamId) ?: return
    val r = lg.road
    item {
        Spacer(Modifier.height(6.dp))
        Panel(color = P.hero, onClick = { vm.openPlayer(h.id) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(h, t, 96.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text((if (h.proYears == 0) "ROOKIE SEASON" else "YEAR ${h.proYears + 1}") + " · ${t.name.uppercase()}", style = T.label, color = heroKicker)
                    Text(h.last.uppercase(), style = T.hero.copy(fontSize = 38.sp, lineHeight = 38.sp), color = P.onHero, maxLines = 1)
                    Text("${h.posLabel} · age ${h.age}", style = T.small, color = P.onHero.copy(alpha = 0.85f))
                }
            }
            // What's new around him this season.
            val tags = listOfNotNull(
                if (com.nh.hoopscoach.game.Hometown.active(lg)) "HOMETOWN KID · ${com.nh.hoopscoach.game.Hometown.city(lg).uppercase()}" else null,
                if (r?.farewellSeason == lg.season) "FINAL SEASON" else null,
                if (r != null && r.comebackSeason in (lg.season - 1)..lg.season) "THE COMEBACK" else null,
                r?.shoe?.let { com.nh.hoopscoach.game.Shoes.shoeName(lg, it).uppercase() },
                com.nh.hoopscoach.game.Jerseys.rank(lg, h).takeIf { it > 0 && lg.jerseySeason == lg.season - 1 }?.let { "NO. $it JERSEY" },
            )
            if (tags.isNotEmpty()) androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEachIndexed { i, tag -> Tag(tag, if (i == 0 && tag.startsWith("HOMETOWN")) P.gold else P.onHero, filled = i == 0 && tag.startsWith("HOMETOWN")) }
            }
            val l = NbaPreviews.lastLine(lg, h)
            Spacer(Modifier.height(14.dp))
            if (l != null) Row(Modifier.fillMaxWidth()) {
                for ((label, v) in listOf("PTS" to l.s.ppg, "REB" to l.s.rpg, "AST" to l.s.apg)) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("%.1f".format(v), style = T.num, color = P.onHero)
                    Text("$label LAST SEASON", style = T.label.copy(fontSize = 9.sp), color = P.onHero.copy(alpha = 0.75f))
                }
            } else Text("Our projection: ${com.nh.hoopscoach.game.Analysis.project(lg, h)}.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
        }
    }
    item {
        // Our odds: his awards, his team.
        val rows = listOfNotNull(
            pv.mvp.indexOfFirst { it.pid == h.id }.takeIf { it >= 0 }?.let { Triple("MVP", it + 1, pv.mvp[it].p) },
            pv.roy.indexOfFirst { it.pid == h.id }.takeIf { it >= 0 }?.let { Triple("Rookie of the Year", it + 1, pv.roy[it].p) },
            pv.dpoy.indexOfFirst { it.pid == h.id }.takeIf { it >= 0 }?.let { Triple("Defensive Player", it + 1, pv.dpoy[it].p) },
            pv.sixth.indexOfFirst { it.pid == h.id }.takeIf { it >= 0 }?.let { Triple("Sixth Man", it + 1, pv.sixth[it].p) },
            pv.mip.indexOfFirst { it.pid == h.id }.takeIf { it >= 0 }?.let { Triple("Most Improved", it + 1, pv.mip[it].p) },
        )
        val to = proj(pv, t)
        Section("Our odds")
        Panel {
            for ((label, rank, p) in rows) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    Text("No. $rank · ${odd(p)}", style = T.small, color = P.sub)
                }
                GrowBar((p / 0.5).toFloat(), Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 10.dp), P.gold, 5.dp)
            }
            if (rows.isEmpty()) Text("Not on any of our award boards yet. That can change by Christmas.", style = T.small, color = P.sub, modifier = Modifier.padding(bottom = 10.dp))
            Text("THE ${t.name.uppercase()}", style = T.label, color = P.accent)
            Text("${to.w}-${to.l} (${to.lo} to ${to.hi} wins) · Playoffs ${odd(to.playoffs)} · Title ${odd(to.title)}", style = T.small, color = P.ink)
            GrowBar(to.playoffs.toFloat(), Modifier.fillMaxWidth().padding(top = 6.dp), P.accent, 5.dp)
        }
    }
    // His stories in this issue.
    val mine = pv.stories.filter { it.pid == h.id }
    if (mine.isNotEmpty()) item {
        Section("His stories")
        GroupList(mine) { st ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(st.kicker.uppercase(), style = T.label, color = P.accent)
                Text(st.head, style = T.head, color = P.ink)
                Text(st.body, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

/** The preseason TV page: the season's biggest questions, argued. */
private fun LazyListScope.debates(lg: League, pv: NbaPreview) {
    item { Deck("Before a game is played, ${com.nh.hoopscoach.game.TvShow.showName(lg)} picks a side. One voice argues our projections; the other argues the other side.") }
    pv.debates.forEachIndexed { i, seg ->
        item {
            Spacer(Modifier.height(10.dp))
            Panel(color = P.hero) {
                Text("QUESTION ${i + 1} OF ${pv.debates.size}", style = T.label, color = heroKicker)
                Text(seg.topic, style = T.title.copy(fontSize = 22.sp, lineHeight = 25.sp), color = P.onHero)
                DebateTakes(seg)
            }
        }
    }
}

// ---------- cover and contents ----------

/** The user's team in the cover's corner. */
@Composable
private fun NbaUserBox(lg: League, pv: NbaPreview) {
    val u = lg.user
    val uo = proj(pv, u)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TeamBadge(u, 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("YOUR TEAM", style = T.label.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.75f))
            Text("${u.name}: ${uo.w}-${uo.l}, about ${Previews.ordinal(uo.seed.roundToInt())} in the ${lg.conf(u.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }}", style = T.bodyB, color = Color.White, maxLines = 2)
            Text("Playoffs ${odd(uo.playoffs)} · Title ${odd(uo.title)} · ${uo.lo}-${uo.hi} wins", style = T.small, color = Color.White.copy(alpha = 0.85f))
        }
    }
}

private fun LazyListScope.nbaCover(lg: League, pv: NbaPreview, count: Int, onRead: () -> Unit, onSkip: () -> Unit) {
    // This year's cover design: a feature on the biggest story, or the two-horse race, or the classic favorite cover.
    val ranked = NbaPreviews.ranked(lg, pv)
    val story = pv.cover
    val subject = story?.let { lg.teams.getOrNull(it.teamId) }
    if (pv.coverStyle == 1 && story != null && subject != null) {
        item {
            val lines = pv.stories.filter { it !== story && it.head != story.head && it.kicker != "Your team" }.take(3).map { it.kicker to it.head }
            FeatureCover("HOOPS DYNASTY PRO", "${pv.season} SEASON PREVIEW", subject, lg.playerOrNull(story.pid)?.takeIf { it.teamId >= 0 }, story.kicker, story.head,
                story.body.substringBefore(". ").take(220) + ".", lines, count, onRead, onSkip) { NbaUserBox(lg, pv) }
        }
        return
    }
    if (pv.coverStyle == 2 && ranked.size >= 2) {
        item {
            val a = ranked[0]
            val b = ranked[1]
            val lines = pv.stories.filter { it.teamId != a.id && it.teamId != b.id && it.kicker != "Your team" }.take(3).map { it.kicker to it.head }
            VersusCover("HOOPS DYNASTY PRO", "${pv.season} SEASON PREVIEW", pv.headline.ifEmpty { "Two teams, one title" }, a, b, proj(pv, a).title, proj(pv, b).title,
                "Projected ${proj(pv, a).w}-${proj(pv, a).l}", "Projected ${proj(pv, b).w}-${proj(pv, b).l}", lines, count, onRead, onSkip) { NbaUserBox(lg, pv) }
        }
        return
    }
    item {
        val top = NbaPreviews.ranked(lg, pv).first()
        val star = NbaPreviews.star(lg, top)
        val primary = Color(top.primary)
        val secondary = Color(top.secondary)
        val fg = onColor(primary)
        val accent = if (contrast(secondary, primary) >= 2.2f) secondary else fg
        val to = proj(pv, top)
        val u = lg.user
        val uo = proj(pv, u)
        val lines = pv.stories.filter { it.kicker != "Your team" && it.kicker != "Spotlight" && it.teamId != top.id }.take(3)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(Brush.verticalGradient(listOf(primary, lerp(primary, Color.Black, 0.55f)))).padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("HOOPS DYNASTY PRO", style = T.label, color = fg.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
                Text("${pv.season} SEASON", style = T.label, color = fg.copy(alpha = 0.85f))
            }
            Text("PREVIEW", style = T.hero.copy(fontSize = 72.sp, lineHeight = 68.sp, letterSpacing = 2.sp), color = fg)
            Box(Modifier.fillMaxWidth().height(3.dp).background(accent))
            Spacer(Modifier.height(16.dp))
            Text(pv.headline.ifEmpty { "The team to beat" }.uppercase(), style = T.title.copy(fontSize = 36.sp, lineHeight = 37.sp), color = accent)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (star != null) PlayerJersey(star, top, 104.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("TITLE FAVORITE · ${odd(to.title)}", style = T.label, color = accent)
                    Text(top.name.uppercase(), style = T.title.copy(fontSize = 30.sp, lineHeight = 31.sp), color = fg, maxLines = 2)
                    if (star != null) Text("${star.name} · ${star.posLabel}", style = T.small, color = fg.copy(alpha = 0.85f))
                    Text("Projected ${to.w}-${to.l} · Finals ${odd(to.finals)}", style = T.small, color = fg.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(14.dp))
            lines.forEach { CoverLine(it.kicker, it.head, fg, accent) }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.28f)).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TeamBadge(u, 40.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("YOUR TEAM", style = T.label.copy(fontSize = 9.sp), color = Color.White.copy(alpha = 0.75f))
                    Text("${u.name}: ${uo.w}-${uo.l}, about ${Previews.ordinal(uo.seed.roundToInt())} in the ${lg.conf(u.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }}", style = T.bodyB, color = Color.White, maxLines = 2)
                    Text("Playoffs ${odd(uo.playoffs)} · Title ${odd(uo.title)} · ${uo.lo}-${uo.hi} wins", style = T.small, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("$count pages · swipe to open →", style = T.bodyB, color = accent, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
                Text("Skip preview →", style = T.small, color = fg.copy(alpha = 0.85f), modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 6.dp))
            }
        }
    }
}

private fun LazyListScope.nbaContents(lg: League, pv: NbaPreview, list: List<Pair<String, String>>, go: (Int) -> Unit) {
    item {
        Section("From the editor")
        Panel {
            NbaPreviews.letter(lg, pv).forEachIndexed { i, para -> Text(para, style = if (i == 0) T.bodyB else T.body, color = P.ink, modifier = Modifier.padding(vertical = 4.dp)) }
            Text("— The editors", style = T.small.copy(fontStyle = FontStyle.Italic), color = P.sub, modifier = Modifier.padding(top = 4.dp))
        }
        Section("How we project")
        Panel {
            Text("Every team gets a strength rating from its roster: its eight best players, the best counting most. We play the whole season 400 times from those ratings and the real schedule, through the play-in, every playoff series and the Finals. A team's projected record is its average; the win range covers 8 seasons in 10.", style = T.small, color = P.ink)
            Spacer(Modifier.height(6.dp))
            Text("It's tuned against how seasons really play out in the game: projections miss by about 7 wins on average, about what real preseason models miss by in the NBA. Once the season starts, every result moves a team's strength, trades change it, and the projection is re-run each week.", style = T.small, color = P.sub)
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

private fun LazyListScope.nbaStories(vm: GameViewModel, lg: League, pv: NbaPreview) {
    item { Deck("The ${pv.stories.size} storylines that will decide the season.") }
    if (lg.ollama.enabled) item {
        Spacer(Modifier.height(10.dp))
        var generating by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Panel(color = P.accentSoft) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OLLAMA AI STORYLINES · ${lg.ollama.model}", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Text("Have Ollama control and generate this season's NBA storylines.", style = T.small, color = P.ink)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(if (generating) "Writing..." else "Generate with Ollama", primary = true, enabled = !generating) {
                    generating = true
                    scope.launch {
                        val top = NbaPreviews.ranked(lg, pv).firstOrNull()?.name ?: "Favorites"
                        val context = "Contenders: " + NbaPreviews.ranked(lg, pv).take(4).joinToString { it.name }
                        val res = Ollama.generateStorylines(lg.ollama, pv.season, top, lg.user.name, lg.userTeamId, context)
                        generating = false
                        res.onSuccess { newStories ->
                            vm.act {
                                pv.stories = newStories
                            }
                        }.onFailure { err ->
                            vm.toast("Ollama: ${err.message ?: "Failed"}")
                        }
                    }
                }
            }
        }
    } else item {
        Spacer(Modifier.height(10.dp))
        var configuring by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        Panel(color = P.surface2) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("OLLAMA AI STORYLINES", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                    Text("Run Ollama on your phone or PC to generate dynamic season storylines.", style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton(if (configuring) "Checking..." else "Auto-Configure", primary = false, enabled = !configuring) {
                    configuring = true
                    scope.launch {
                        val res = Ollama.autoConfigure(lg.ollama.host)
                        configuring = false
                        res.onSuccess { cfg ->
                            vm.act {
                                it.ollama.enabled = true
                                it.ollama.host = cfg.host
                                it.ollama.model = cfg.selectedModel
                            }
                            vm.toast("Ollama auto-configured with ${cfg.selectedModel}!")
                        }.onFailure {
                            vm.push(Route.Settings)
                        }
                    }
                }
            }
        }
    }
    pv.stories.forEachIndexed { i, s -> item { Spacer(Modifier.height(10.dp)); StoryCard(vm, lg, s, i, lead = i == 0) } }
}

// ---------- rankings ----------

private fun LazyListScope.powerRankings(vm: GameViewModel, lg: League, pv: NbaPreview) {
    val order = NbaPreviews.power(lg, pv)
    val lastRank = lg.teams.filter { (pv.lastWins[it.id] ?: -1) >= 0 }.sortedByDescending { pv.lastWins[it.id] ?: 0 }.withIndex().associate { (i, t) -> t.id to i + 1 }
    item {
        Deck("Every team, ranked by its projected record. Arrows compare with last season's records.")
        Spacer(Modifier.height(8.dp))
        GroupList(order.withIndex().toList()) { (i, t) ->
            val o = proj(pv, t)
            val mine = t.id == lg.userTeamId
            Column(Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}", style = T.num.copy(fontSize = 22.sp), color = if (mine) P.accent else P.ink, modifier = Modifier.width(38.dp))
                    Movement(i + 1, lastRank[t.id] ?: -1)
                    TeamBadge(t, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pv.last[t.id]?.let { "Last season: $it" } ?: lg.conf(t.confId).name, style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${o.w}-${o.l}", style = T.numSm.copy(fontSize = 17.sp), color = P.ink)
                        Text("TITLE ${odd(o.title)}", style = T.label.copy(fontSize = 8.sp), color = P.faint)
                    }
                }
                Text(remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.nba(lg, pv, t, 3) }, style = T.small, color = P.ink, modifier = Modifier.padding(start = 72.dp, top = 4.dp))
            }
        }
    }
}

private fun LazyListScope.nbaTitleRace(vm: GameViewModel, lg: League, pv: NbaPreview) {
    val byTitle = NbaPreviews.ranked(lg, pv)
    val max = byTitle.firstOrNull()?.let { proj(pv, it).title }?.coerceAtLeast(0.01) ?: 1.0
    val tiers = listOf(
        "The favorites" to byTitle.filter { proj(pv, it).title >= 0.12 },
        "Contenders" to byTitle.filter { proj(pv, it).title >= 0.04 && proj(pv, it).title < 0.12 },
        "Dark horses" to byTitle.filter { proj(pv, it).title >= 0.01 && proj(pv, it).title < 0.04 },
        "Long shots" to byTitle.filter { proj(pv, it).title < 0.01 && proj(pv, it).playoffs >= 0.4 }.take(8),
    ).filter { it.second.isNotEmpty() }
    item { Deck("We played the season ${NbaPreviews.SIMS} times, through the play-in and the Finals. Here's how often each team won it all.") }
    tiers.forEach { (name, teams) ->
        item {
            Section(name)
            GroupList(teams) { t ->
                val o = proj(pv, t)
                Column(Modifier.fillMaxWidth().then(if (t.id == lg.userTeamId) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TeamBadge(t, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Finals ${odd(o.finals)} · Conf. finals ${odd(o.confFinals)} · ${o.w}-${o.l}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                        }
                        Text(odd(o.title), style = T.num.copy(fontSize = 22.sp), color = P.ink)
                    }
                    Spacer(Modifier.height(6.dp))
                    GrowBar((o.title / max).toFloat(), Modifier.fillMaxWidth().padding(start = 38.dp), teamInk(t), 6.dp)
                    if (o.title >= 0.04) Text(remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.nbaEdge(lg, pv, t) }, style = T.small.copy(fontSize = 12.sp), color = P.sub,
                        modifier = Modifier.padding(start = 38.dp, top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    item {
        Section("Title odds by conference")
        Panel {
            for (c in 0..1) {
                val v = lg.teams.filter { it.confId == c }.sumOf { proj(pv, it).title }
                Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(lg.conf(c).abbr, style = T.bodyB, color = P.ink, modifier = Modifier.width(64.dp))
                    GrowBar(v.toFloat(), Modifier.weight(1f), if (c == lg.user.confId) P.accent else P.sub, 10.dp)
                    Text(odd(v), style = T.numSm.copy(fontSize = 15.sp), color = P.ink, textAlign = TextAlign.End, modifier = Modifier.width(48.dp))
                }
            }
        }
    }
}

private fun LazyListScope.conference(vm: GameViewModel, lg: League, pv: NbaPreview, conf: Int) {
    val order = lg.teams.filter { it.confId == conf }.sortedBy { proj(pv, it).seed }
    val playIn = Eras.of(lg).playIn
    val started = lg.teams.any { it.w + it.l > 0 }
    val now = if (started) ProSeason.standings(lg, conf).withIndex().associate { (i, t) -> t.id to i + 1 } else emptyMap()
    item {
        Deck("Projected finish, with each team's record, win range and chances. " + if (playIn) "Seeds 7 to 10 play in for the last two spots." else "The top eight make the playoffs.")
        val fav = order.maxByOrNull { proj(pv, it).finals }
        if (fav != null) {
            Spacer(Modifier.height(8.dp))
            Panel {
                Row {
                    Stat("Favorite", fav.abbr, Modifier.weight(1f), style = T.numSm)
                    Stat("Finals odds", odd(proj(pv, fav).finals), Modifier.weight(1f), style = T.numSm)
                    Stat("No. 1 seed", odd(proj(pv, order.first()).top), Modifier.weight(1f), style = T.numSm)
                }
            }
        }
    }
    val groups = if (playIn) listOf("Playoffs · 1 to 6" to 0..5, "Play-in · 7 to 10" to 6..9, "Lottery" to 10..29) else listOf("Playoffs · 1 to 8" to 0..7, "Lottery" to 8..29)
    for ((label, range) in groups) {
        val part = order.filterIndexed { i, _ -> i in range }
        if (part.isEmpty()) continue
        item {
            Text(label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = if (range.first == 0) P.accent else if (label.startsWith("Play-in")) P.gold else P.faint,
                modifier = Modifier.padding(start = Gutter + 4.dp, top = 14.dp, bottom = 4.dp))
            GroupList(part) { t ->
                val o = proj(pv, t)
                val i = order.indexOf(t) + 1
                val mine = t.id == lg.userTeamId
                Column(Modifier.fillMaxWidth().then(if (mine) Modifier.background(P.accentSoft) else Modifier).clickable { vm.push(Route.TeamPage(t.id)) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$i", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(28.dp))
                        TeamBadge(t, 28.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${o.w}-${o.l} (${o.lo}-${o.hi} W)" + (if (playIn) " · play-in ${odd(o.playIn)}" else "") + (now[t.id]?.let { " · now ${Previews.ordinal(it)}" } ?: ""),
                                style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(odd(o.playoffs), style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
                            Text("PLAYOFFS", style = T.label.copy(fontSize = 8.sp), color = P.faint)
                        }
                    }
                    Spacer(Modifier.height(5.dp))
                    GrowBar(o.playoffs.toFloat(), Modifier.fillMaxWidth().padding(start = 66.dp), teamInk(t), 4.dp)
                    Text(remember(t.id, lg.season) { com.nh.hoopscoach.game.Takes.nba(lg, pv, t, 2, from = 1) }, style = T.small, color = P.sub, modifier = Modifier.padding(start = 66.dp, top = 4.dp))
                }
            }
        }
    }
}

// ---------- awards and players ----------

private fun LazyListScope.awardRaces(vm: GameViewModel, lg: League, pv: NbaPreview) {
    item { Deck("Opening odds for the season's awards: the best players on the best teams lead the MVP race.") }
    listOf(
        "Most Valuable Player" to pv.mvp.take(8),
        "Rookie of the Year" to pv.roy.take(5),
        "Defensive Player of the Year" to pv.dpoy.take(5),
        "Sixth Man of the Year" to pv.sixth.take(5),
        "Most Improved Player" to pv.mip.take(5),
    ).forEach { (award, list) ->
        if (list.isNotEmpty()) item {
            Section(award)
            OddsList(vm, lg, list)
        }
    }
}

@Composable
private fun OddsList(vm: GameViewModel, lg: League, list: List<PoyOdds>) {
    val rows = list.mapNotNull { o -> lg.playerOrNull(o.pid)?.takeIf { it.teamId >= 0 }?.let { it to o.p } }
    val max = rows.maxOfOrNull { it.second }?.coerceAtLeast(0.01) ?: 1.0
    GroupList(rows) { (p, v) ->
        val t = lg.team(p.teamId)
        Column(Modifier.fillMaxWidth().then(if (p.id == lg.heroId) Modifier.background(P.accentSoft) else Modifier).clickable { vm.openPlayer(p.id) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PlayerJersey(p, t, 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.abbr} · ${p.posLabel} · ${proLast(lg, p)}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(odd(v), style = T.numSm, color = P.ink)
            }
            Spacer(Modifier.height(5.dp))
            GrowBar((v / max).toFloat(), Modifier.fillMaxWidth().padding(start = 46.dp), teamInk(t), 5.dp)
        }
    }
}

private fun LazyListScope.topPlayers(vm: GameViewModel, lg: League) {
    val best = lg.teams.flatMap { lg.rosterOf(it) }.sortedByDescending { NbaPreviews.value(lg, it) }.take(30)
    item {
        Deck("The league's best 30 players going into the season.")
        Spacer(Modifier.height(8.dp))
        GroupList(best.withIndex().toList(), inset = 92.dp) { (i, p) ->
            val t = lg.team(p.teamId)
            ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == lg.heroId) Modifier.background(P.accentSoft) else Modifier) {
                Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(36.dp))
                PlayerJersey(p, t, 42.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.abbr} · ${p.posLabel} · age ${p.age}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    Text(proLast(lg, p), style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                }
                Ring(p.ovr, 40.dp, textSize = 15.sp)
            }
        }
    }
}

// ---------- the offseason and the rookies ----------

private fun LazyListScope.offseason(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    val moved = NbaPreviews.newHomes(lg).take(10)
    val trades = ps.trades.filter { it.season == lg.season - 1 && it.offseason }
    item {
        Deck("Everything that changed since last season: new homes, trades, coaches and front offices.")
        if (moved.isNotEmpty()) {
            Section("New homes")
            GroupList(moved, inset = 70.dp) { (p, from, how) ->
                PlayerLine(vm, lg, lg.team(p.teamId), p, "${from.abbr} → ${lg.team(p.teamId).abbr} · ${if (how == "traded") "Traded" else "Signed"}\n${proLast(lg, p)}")
            }
        }
        if (ps.faMoves.isNotEmpty()) {
            Section("Free agency")
            GroupList(ps.faMoves.take(12)) { line -> ListRow { Text(line, style = T.small, color = P.ink, modifier = Modifier.weight(1f)) } }
        }
        if (trades.isNotEmpty()) {
            Section("Trades")
            GroupList(trades.take(10)) { tr ->
                ListRow {
                    TeamBadge(lg.team(tr.teamA), 24.dp)
                    Spacer(Modifier.width(4.dp))
                    TeamBadge(lg.team(tr.teamB), 24.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${lg.team(tr.teamA).abbr} get ${tr.toA.joinToString(", ")}; ${lg.team(tr.teamB).abbr} get ${tr.toB.joinToString(", ")}", style = T.small, color = P.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        if (ps.staffMoves.isNotEmpty()) {
            Section("Coaches and front offices")
            GroupList(ps.staffMoves) { line -> ListRow { Text(line, style = T.small, color = P.ink, modifier = Modifier.weight(1f)) } }
        }
        if (moved.isEmpty() && ps.faMoves.isEmpty() && trades.isEmpty() && ps.staffMoves.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Panel { Text("A quiet summer: the league starts where it left off.", style = T.body, color = P.sub) }
        }
    }
}

private fun LazyListScope.rookies(vm: GameViewModel, lg: League, pv: NbaPreview) {
    val class_ = lg.teams.flatMap { lg.rosterOf(it) }.filter { it.proYears == 0 }
        .sortedWith(compareBy<Player> { NbaPreviews.draftPick(lg, it) ?: 99 }.thenByDescending { it.ovr }).take(15)
    item {
        Deck("The new class, in draft order, with each rookie's Rookie of the Year odds.")
        Spacer(Modifier.height(8.dp))
        if (class_.isEmpty()) Panel { Text("No rookies this season.", style = T.body, color = P.sub) }
        GroupList(class_, inset = 70.dp) { p ->
            val pick = NbaPreviews.draftPick(lg, p)
            val roy = pv.roy.firstOrNull { it.pid == p.id }?.p ?: 0.0
            PlayerLine(vm, lg, lg.team(p.teamId), p, "${if (pick != null) "No. $pick pick" else "Undrafted"} · ${lg.team(p.teamId).abbr} · ${p.posLabel}\n${p.highSchool.ifEmpty { "—" }} · ROY ${odd(roy)}")
        }
    }
}

// ---------- your team ----------

private fun LazyListScope.nbaTeamReport(vm: GameViewModel, lg: League, pv: NbaPreview) {
    val u = lg.user
    val o = proj(pv, u)
    val roster = lg.rosterOf(u).sortedByDescending { it.ovr }
    val hero = lg.playerOrNull(lg.heroId)?.takeIf { it.teamId == u.id }
    item {
        val ink = teamInk(u)
        Spacer(Modifier.height(8.dp))
        Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(P.surface)) {
            TeamBand(u) { fg, accent ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(u, 52.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(when { o.title >= 0.12 -> "TITLE CONTENDER"; o.six >= 0.6 -> "PLAYOFF TEAM"; o.playoffs >= 0.35 -> "ON THE BUBBLE"; else -> "REBUILDING" }, style = T.label, color = accent)
                        Text(u.name.uppercase(), style = T.title.copy(fontSize = 24.sp, lineHeight = 25.sp), color = fg)
                        Text("Coach ${lg.coachOf(u).name} · ${Payroll.status(lg, u)}", style = T.small, color = fg.copy(alpha = 0.85f), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row {
                    listOf("PROJECTED" to "${o.w}-${o.l}", "RANGE" to "${o.lo}-${o.hi} W", "SEED" to Previews.ordinal(o.seed.roundToInt()), "PAYROLL" to Road.dollars(Payroll.total(lg, u))).forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = T.label.copy(fontSize = 9.sp), color = fg.copy(alpha = 0.75f), maxLines = 1)
                            Text(v, style = T.numSm.copy(fontSize = 16.sp), color = fg, maxLines = 1)
                        }
                    }
                }
            }
            Column(Modifier.padding(16.dp)) {
                Text("SEASON ODDS", style = T.label, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OddsCell("Playoffs", o.playoffs, ink, Modifier.weight(1f))
                    OddsCell("Top-six seed", o.six, ink, Modifier.weight(1f))
                    OddsCell("No. 1 seed", o.top, ink, Modifier.weight(1f))
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OddsCell("Conf. finals", o.confFinals, ink, Modifier.weight(1f))
                    OddsCell("Finals", o.finals, ink, Modifier.weight(1f))
                    OddsCell("Title", o.title, ink, Modifier.weight(1f), scale = 0.5)
                }
            }
        }
        if (hero != null) {
            val rank = roster.indexOf(hero) + 1
            val mvp = pv.mvp.indexOfFirst { it.pid == hero.id }
            Section("Your role")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlayerJersey(hero, u, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(hero.name, style = T.head, color = P.ink)
                        Text(when (rank) { 1 -> "The team's best player"; in 2..3 -> "One of the top three players here"; in 4..5 -> "A starter"; in 6..8 -> "In the rotation"; else -> "Fighting for minutes" } +
                            " · No. $rank on the roster", style = T.small, color = P.sub)
                        Text(proLast(lg, hero), style = T.small, color = P.sub)
                        if (mvp >= 0) Text("MVP odds: ${odd(pv.mvp[mvp].p)} (No. ${mvp + 1})", style = T.small, color = P.accent)
                    }
                    Ring(hero.ovr, 46.dp)
                }
            }
        }
        Section("The rotation")
        GroupList(roster.take(10), inset = 70.dp) { p -> PlayerLine(vm, lg, u, p, "${p.posLabel} · age ${p.age} · ${Road.dollars(Payroll.salaryOf(lg, p))}\n${proLast(lg, p)}") }
        val big = lg.games.filter { it.involves(u.id) && !it.played }.filter { g -> proj(pv, lg.team(g.opp(u.id))).title >= 0.06 || g.label.isNotEmpty() }.sortedBy { it.day }.take(8)
        if (big.isNotEmpty()) {
            Section("Big nights")
            GroupList(big) { g ->
                val opp = lg.team(g.opp(u.id))
                val win = Rankings.winProbFor(lg, g, u.id)
                ListRow(onClick = { vm.push(Route.TeamPage(opp.id)) }) {
                    Text("D${g.day}", style = T.numSm.copy(fontSize = 15.sp), color = P.sub, modifier = Modifier.width(44.dp))
                    Text(if (g.home == u.id) "vs" else "@", style = T.small, color = P.sub, modifier = Modifier.width(24.dp))
                    TeamBadge(opp, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(opp.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(g.label.ifEmpty { "Title odds ${odd(proj(pv, opp).title)}" }, style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                    }
                    Text("${(win * 100).toInt()}%", style = T.numSm.copy(fontSize = 15.sp), color = if (win >= 0.5) P.good else P.warn)
                }
            }
            Text("Right column: our chance to win each game.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
        }
        ScoutingReport(vm, lg, u, o.lo, o.hi)
        Section("The bottom line")
        Panel {
            Text(remember(u.id, lg.season) { com.nh.hoopscoach.game.Takes.nbaBottom(lg, pv, u) }, style = T.body, color = P.ink)
        }
    }
}

// ---------- predictions ----------

private fun LazyListScope.nbaPredictions(vm: GameViewModel, lg: League, pv: NbaPreview) {
    item {
        for (c in 0..1) {
            val seeds = lg.teams.filter { it.confId == c }.sortedBy { proj(pv, it).seed }.take(8)
            Section("${lg.conf(c).name}: playoff seeds")
            Panel(padding = PaddingValues(10.dp)) {
                seeds.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        row.forEach { t ->
                            Column(Modifier.weight(1f).clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${seeds.indexOf(t) + 1}", style = T.label, color = P.accent)
                                TeamBadge(t, 36.dp)
                                Text(t.abbr, style = T.bodyB.copy(fontSize = 13.sp), color = if (t.id == lg.userTeamId) P.accent else P.ink)
                            }
                        }
                    }
                }
            }
        }
        val east = lg.teams.filter { it.confId == 0 }.maxByOrNull { proj(pv, it).finals }
        val west = lg.teams.filter { it.confId == 1 }.maxByOrNull { proj(pv, it).finals }
        val champ = NbaPreviews.ranked(lg, pv).first()
        if (east != null && west != null) {
            Section("The Finals")
            Panel {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    for (t in listOf(east, west)) {
                        Column(Modifier.weight(1f).clickable { vm.push(Route.TeamPage(t.id)) }, horizontalAlignment = Alignment.CenterHorizontally) {
                            TeamBadge(t, 54.dp)
                            Spacer(Modifier.height(6.dp))
                            Text(t.name, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center)
                            Text("Finals ${odd(proj(pv, t).finals)}", style = T.small, color = P.sub)
                            if (t.id == champ.id) Tag("CHAMPION", P.gold)
                        }
                        if (t == east) Text("VS", style = T.h2, color = P.faint)
                    }
                }
                if (champ.id != east.id && champ.id != west.id) Text("Our title pick is the ${champ.name}, even though neither finalist above is them: they win it most often in our simulations.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
            }
        }
        val awards = listOfNotNull(
            pv.mvp.firstOrNull()?.let { "MVP" to it },
            pv.roy.firstOrNull()?.let { "Rookie of the Year" to it },
            pv.dpoy.firstOrNull()?.let { "Defensive Player" to it },
            pv.sixth.firstOrNull()?.let { "Sixth Man" to it },
            pv.mip.firstOrNull()?.let { "Most Improved" to it },
        ).mapNotNull { (k, o) -> lg.playerOrNull(o.pid)?.takeIf { it.teamId >= 0 }?.let { Triple(k, it, o.p) } }
        Section("Award picks")
        GroupList(awards) { (k, p, v) ->
            ListRow(onClick = { vm.openPlayer(p.id) }) {
                Text(k, style = T.small, color = P.accent, modifier = Modifier.width(120.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1)
                    Text(lg.team(p.teamId).name, style = T.small, color = P.sub, maxLines = 1)
                }
                Text(odd(v), style = T.numSm.copy(fontSize = 15.sp), color = P.ink)
            }
        }
        if (pv.bold.isNotEmpty()) BoldPredictions(pv.bold, pv.graded, pv.outcomes)
        Text("See you in June.", style = T.title.copy(fontSize = 26.sp), color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
    }
}

/** Today card for the pro season's first weeks: the new issue is out. */
@Composable
fun NbaPreviewCard(vm: GameViewModel, lg: League) {
    val pv = lg.proState?.preview?.takeIf { it.season == lg.season } ?: return
    val u = lg.user
    if (u.w + u.l >= 10) return
    val o = pv.proj[u.id] ?: return
    val top = NbaPreviews.ranked(lg, pv).firstOrNull() ?: return
    Section("Season preview")
    Panel(color = P.hero, onClick = { vm.push(Route.NbaPreview) }) {
        Text("THE ${pv.season} PRESEASON ISSUE · ${nbaPages(lg, pv).size} PAGES", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text("${u.name.uppercase()}: ${o.w}-${o.l}", style = T.title.copy(fontSize = 26.sp, lineHeight = 28.sp), color = P.onHero)
        Spacer(Modifier.height(4.dp))
        Text("Playoff odds ${odd(o.playoffs)}, title odds ${odd(o.title)}. The ${top.name} are the favorites. Plus storylines, power rankings, award races and the rookie class.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
        Spacer(Modifier.height(8.dp))
        Text("Read the issue →", style = T.bodyB, color = P.onHero)
    }
}

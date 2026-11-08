package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.DraftPick
import com.nh.hoopscoach.game.Awards
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.Geo
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Rival
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.Summer
import com.nh.hoopscoach.game.SummerEvent
import com.nh.hoopscoach.game.SchoolOffer
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private fun classLong(year: Int) = when (year) { 1 -> "Freshman"; 2 -> "Sophomore"; 3 -> "Junior"; 4 -> "Senior"; else -> "Graduate" }

private fun closenessLabel(c: Int) = when (c) { 2 -> "In-state"; 1 -> "Same region"; else -> "" }

private fun heightRange(pos: Pos) = when (pos) { Pos.PG -> 70..78; Pos.SG -> 72..80; Pos.SF -> 75..82; Pos.PF -> 77..84; Pos.C -> 79..87 }

// ---------- new game: create a player, then pick a school ----------

/** The player-mode half of the new game screen. */
@Composable
fun RoadNewGame(vm: GameViewModel, lg: League, header: @Composable () -> Unit) {
    val hero = vm.roadHero
    if (hero != null) { RoadOffers(vm, lg, hero, header); return }
    // Start from the last player made here, so going back from the offers keeps every choice (the era above all).
    val saved = vm.roadSpec
    var first by remember { mutableStateOf(saved?.first ?: "") }
    var last by remember { mutableStateOf(saved?.last ?: "") }
    var pos by remember { mutableStateOf(saved?.pos ?: Pos.SG) }
    var pos2 by remember { mutableStateOf(saved?.pos2) }
    var era by remember { mutableStateOf(saved?.era ?: com.nh.hoopscoach.game.Era.TODAY) }
    var height by remember { mutableIntStateOf(saved?.height ?: 76) }
    var weight by remember { mutableIntStateOf(saved?.weight ?: 200) }
    var town by remember { mutableStateOf(saved?.town ?: "") }
    var state by remember { mutableStateOf(saved?.state ?: "GA") }
    var style by remember { mutableStateOf(saved?.style ?: Arch.SHOT_CREATOR) }
    var stars by remember { mutableIntStateOf(saved?.stars ?: 4) }
    var number by remember { mutableIntStateOf(saved?.number ?: 3) }
    val range = heightRange(pos)
    if (pos2 != null && pos2 !in Ratings.secondOptions(pos)) pos2 = null
    val styles = Ratings.styles(pos) + (pos2?.let { Ratings.styles(it) } ?: emptyList())
    if (style !in styles) style = styles.first()
    if (height !in range) height = (range.first + range.last) / 2
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item { header() }
            item {
                // When: today, or a past era from his first college season to the pros.
                Section("Era")
                val eras = com.nh.hoopscoach.game.Era.entries.sortedBy { if (it.year == 0) 9999 else it.year }.reversed()
                Segmented(eras.map { it.label }, eras.indexOf(era), { era = eras[it] }, scroll = true)
                Text(
                    if (era == com.nh.hoopscoach.game.Era.TODAY) "Today's college game, then today's NBA."
                    else "College starts in ${era.year - 1}-${(era.year % 100).toString().padStart(2, '0')}, with the real future pros at their schools (${eraCollegeNames(era)}). You turn pro into that era's NBA: its teams that year, its stars, and a real draft class every summer.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
                )
                Section("Your player")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(first, { first = it.take(16) }, placeholder = { Text("First name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(last, { last = it.take(18) }, placeholder = { Text("Last name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                Section("Position")
                Segmented(Pos.entries.map { it.name }, pos.ordinal, { pos = Pos.entries[it] })
                // A second position: none, or a spot next to his.
                val seconds = listOf<Pos?>(null) + Ratings.secondOptions(pos)
                Text("SECOND POSITION", style = T.label, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 12.dp, bottom = 6.dp))
                Segmented(seconds.map { it?.name ?: "None" }, seconds.indexOf(pos2).coerceAtLeast(0), { pos2 = seconds[it] })
                Text(if (pos2 == null) "One position. Coaches play you there." else "You can play $pos or $pos2: coaches use you at either, and you can train either position's styles.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                Section("Size")
                Panel {
                    Text("Height · ${Ratings.heightLabel(height)}", style = T.bodyB, color = P.ink)
                    HoopsSlider(height.toFloat(), { height = it.roundToInt() }, range.first.toFloat()..range.last.toFloat())
                    Text("Weight · $weight lbs", style = T.bodyB, color = P.ink)
                    HoopsSlider(weight.toFloat(), { weight = it.roundToInt() }, 160f..290f)
                }
                Section("Play style")
                Picker("Archetype", style.label, styles.map { it.label to "${it.blurb}. Boosts ${it.boosts.joinToString(" · ") { b -> b.label }}." }, Modifier.padding(horizontal = Gutter).fillMaxWidth()) { style = styles[it] }
                Text("${style.blurb}. Boosts ${style.boosts.joinToString(" · ") { it.label }}.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                Section("Hometown")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(town, { town = it.take(22) }, placeholder = { Text("City", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    val states = Geo.states
                    Picker("State", state, states.map { it to (Geo.region(it) ?: "") }, Modifier.width(120.dp)) { state = states[it] }
                }
                Text("Schools near home have an edge in recruiting you.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
                Section("Prospect level")
                Segmented(listOf("3★ Sleeper", "4★ Prospect", "5★ Blue-chip"), stars - 3, { stars = it + 3 })
                Text(
                    when (stars) {
                        5 -> "The biggest programs come calling, but you'll fight veterans for minutes."
                        4 -> "Strong programs want you, and a starting job is within reach."
                        else -> "Smaller schools will start you right away. Prove the big ones wrong."
                    } + " Your starting overall is rolled from ${Road.ovrRange(stars).first} to ${Road.ovrRange(stars).last} when the offers come in. Every level can reach the league.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
                )
                Section("Jersey number")
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallButton("−") { number = (number - 1).coerceIn(0, 99) }
                        Text("$number", style = T.num, color = P.ink, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        SmallButton("+") { number = (number + 1).coerceIn(0, 99) }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(P.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
            PrimaryButton("See scholarship offers", Modifier.fillMaxWidth()) {
                vm.roadFindOffers(Road.Spec(first, last, pos, height, weight, town, state, style, stars, number, pos2, era))
            }
        }
    }
}

@Composable
private fun RoadOffers(vm: GameViewModel, lg: League, hero: Player, header: @Composable () -> Unit) {
    var pick by remember(hero.id) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item {
                header()
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Jersey(hero.number, P.accent, P.surface, 56.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(hero.name, style = T.head, color = P.ink)
                            Text("${hero.posLabel} · ${Ratings.heightLabel(hero.height)} · ${hero.weight} lbs · ${hero.hometown}", style = T.small, color = P.sub)
                            Text("${Road.starLabel(hero.stars)} · No. ${hero.recruitRank} nationally · ${hero.style.label}", style = T.small, color = P.accent)
                            val range = Road.ovrRange(hero.stars)
                            Text(
                                "Rolled ${hero.ovr} (range ${range.first}-${range.last}): " + when {
                                    hero.ovr >= range.last - 2 -> "a top-end start."
                                    hero.ovr <= range.first + 2 -> "a slow start. Plenty of room to grow."
                                    else -> "right in the middle."
                                },
                                style = T.small, color = P.sub,
                            )
                        }
                        Ring(hero.ovr, 48.dp, label = "OVR", textSize = 17.sp)
                        Spacer(Modifier.width(6.dp))
                        Ring(hero.pot, 48.dp, label = "POT", textSize = 17.sp)
                    }
                    // Which era this career is in, so a wrong pick is caught before signing.
                    val era = com.nh.hoopscoach.game.Era.entries.firstOrNull { it.name == lg.era }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (era == null || era == com.nh.hoopscoach.game.Era.TODAY) "ERA: TODAY · today's college game, then today's NBA"
                        else "ERA: ${era.year} · college starts in ${lg.season - 1}-${(lg.season % 100).toString().padStart(2, '0')}, then the real NBA of the year you turn pro",
                        style = T.label.copy(fontSize = 10.sp), color = P.accent,
                    )
                }
                Section("Scholarship offers · ${vm.roadOffers.size}")
                Text("Pick where you'll play. A school with fewer good players at your position means minutes sooner; richer programs pay more NIL.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
                Spacer(Modifier.height(6.dp))
                OfferBrowser(lg, vm.roadOffers, hero, pick) { pick = it.teamId }
                Spacer(Modifier.height(16.dp))
            }
        }
        Column(Modifier.fillMaxWidth().background(P.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
            val t = if (pick >= 0) lg.team(pick) else null
            PrimaryButton(if (t == null) "Pick a school" else "Commit to ${t.school}", Modifier.fillMaxWidth(), enabled = t != null) { vm.startRoad(pick) }
            Text("Change your player", style = T.bodyB.copy(fontSize = 14.sp), color = P.accent, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { vm.roadBack() }.padding(top = 10.dp))
        }
    }
}

/** A long list of schools: sort it, search it, and see it a page at a time. */
@Composable
private fun OfferBrowser(lg: League, offers: List<SchoolOffer>, hero: Player, selected: Int, onPick: (SchoolOffer) -> Unit) {
    var sort by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var shown by remember { mutableStateOf(20) }
    Segmented(listOf("Top programs", "Playing time", "Most NIL", "Near home"), sort, { sort = it; shown = 20 }, scroll = true)
    OutlinedTextField(query, { query = it; shown = 20 }, placeholder = { Text("Search a school or conference", color = P.faint) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 8.dp), colors = fieldColors())
    val q = query.trim().lowercase()
    val list = offers.filter { o ->
        val t = lg.team(o.teamId)
        q.isEmpty() || t.school.lowercase().contains(q) || t.mascot.lowercase().contains(q) || lg.conf(t.confId).name.lowercase().contains(q) || lg.conf(t.confId).abbr.lowercase() == q
    }.sortedWith(when (sort) {
        1 -> compareBy<SchoolOffer> { it.ahead }.thenByDescending { lg.team(it.teamId).prestige }
        2 -> compareByDescending { it.nil }
        3 -> compareByDescending<SchoolOffer> { it.closeness }.thenByDescending { lg.team(it.teamId).prestige }
        else -> compareByDescending { lg.team(it.teamId).prestige }
    })
    if (list.isEmpty()) Panel { Text("No school matches \"$query\".", style = T.body, color = P.sub) }
    GroupList(list.take(shown), inset = 64.dp) { o -> OfferRow(lg, o, hero, selected == o.teamId) { onPick(o) } }
    if (list.size > shown) Box(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        SecondaryButton("Show more · ${list.size - shown} more schools", Modifier.fillMaxWidth()) { shown += 20 }
    }
}

@Composable
private fun OfferRow(lg: League, o: SchoolOffer, hero: Player, selected: Boolean, onClick: () -> Unit) {
    val t = lg.team(o.teamId)
    ListRow(onClick = onClick, modifier = if (selected) Modifier.background(P.accentSoft) else Modifier) {
        TeamBadge(t, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${lg.conf(t.confId).abbr} · ${prestigeLabel(t.prestige)}" + closenessLabel(o.closeness).let { if (it.isEmpty()) "" else " · $it" },
                style = T.small, color = P.sub, maxLines = 1)
            Text("${o.role} · ${if (o.ahead == 0) "nobody" else "${o.ahead}"} ahead at ${hero.pos}", style = T.small,
                color = if (o.ahead == 0) P.good else if (o.ahead >= 2) P.warn else P.ink, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(money(o.nil), style = T.numSm, color = P.ink)
            Text("NIL / YEAR", style = T.label.copy(fontSize = 9.sp), color = P.sub)
        }
    }
}

// ---------- the "Me" tab ----------

@Composable
fun HeroScreen(vm: GameViewModel, lg: League) {
    if (Road.inGreenRoom(lg)) { GreenRoomWait(vm); return }
    val r = lg.road
    val h = lg.playerOrNull(lg.heroId)
    if (h == null || r == null || h.teamId < 0) {
        LazyColumn(Modifier.fillMaxWidth()) {
            item { ScreenHeader("Road to the League", lg.hero?.last ?: "Career over", r?.ending) }
            item { CareerStory(lg) }
        }
        return
    }
    val t = lg.team(h.teamId)
    val board = remember(vm.rev, lg) { Offseason.draftBoard(lg) }
    val rank = board[h.id]
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Road to the League · ${classLong(h.year)}", h.last, "${h.posLabel} · ${Ratings.heightLabel(h.height)} · ${t.school}") { PlayerJersey(h, t, 50.dp) }
            Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring(h.ovr, 54.dp, label = "OVR", textSize = 19.sp)
                    Spacer(Modifier.width(8.dp))
                    Ring(h.pot, 54.dp, label = "POT", textSize = 19.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("DRAFT STOCK", style = T.label, color = P.sub)
                        Text(Offseason.projection(rank, h.year), style = T.head, color = P.accent)
                        Text(if (rank != null) "No. $rank on the big board" else "Not on the board yet", style = T.small, color = P.sub)
                    }
                }
            }
        }
        if (Road.needsDeal(lg)) item { DealPicker(vm, lg) }
        // Four pages instead of one long scroll; the deal and the press stay on top.
        item { Spacer(Modifier.height(10.dp)); Segmented(listOf("You", "Team", "Growth", "Media"), vm.meTab.coerceIn(0, 3), { vm.meTab = it }) }
        if (lg.road?.press != null) item { PressCard(vm, lg) }
        if (vm.meTab == 0) item { RacePanel(vm, lg) }
        if (vm.meTab == 0) item { RivalPanel(vm, lg) }
        if (vm.meTab == 1) item { ImpactPanel(vm, lg) }
        if (vm.meTab == 2) item { PracticePanel(vm, lg) }
        if (vm.meTab == 2) item { StaffPanel(vm, lg) }
        if (vm.meTab == 3) item { MediaPanel(lg) }
        if (vm.meTab == 3 && (lg.road?.feed?.isNotEmpty() == true)) item { SocialStrip(vm, lg) }
        if (vm.meTab == 1) item {
            val mins = Gameplan.plannedMinutes(t, h.id)
            Section("Your role")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Road.role(lg).uppercase(), style = T.label, color = P.accent)
                        Text(if (mins > 0) "$mins minutes a game in the plan" else "Not in the rotation yet", style = T.head, color = P.ink)
                        Text("Coach ${lg.coachOf(t).last} plays the best players at each spot. Pass the man ahead of you to start.", style = T.small, color = P.sub)
                    }
                }
                if (h.stats.gp > 0) {
                    Spacer(Modifier.height(10.dp))
                    StatRow(listOf(
                        Triple("PPG", "%.1f".format(h.stats.ppg), null), Triple("RPG", "%.1f".format(h.stats.rpg), null),
                        Triple("APG", "%.1f".format(h.stats.apg), null), Triple("MPG", "%.1f".format(h.stats.mpg), null),
                    ), style = T.numSm)
                }
            }
            Section("Depth chart · ${h.posLabel}")
            GroupList(Road.depth(lg), inset = 64.dp) { p ->
                ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == h.id) Modifier.background(P.accentSoft) else Modifier) {
                    PlayerJersey(p, t, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (p.id == h.id) "${p.name} (you)" else p.name, style = T.bodyB, color = if (p.id == h.id) P.accent else P.ink, maxLines = 1)
                        Text("${p.cls} · ${Gameplan.plannedMinutes(t, p.id)} min planned", style = T.small, color = P.sub)
                    }
                    Ring(p.ovr, 40.dp)
                }
            }
        }
        if (vm.meTab == 2) item {
            Section("Development")
            Panel {
                Text("Training focus: ${h.focus.label}", style = T.bodyB, color = P.ink)
                Text("Every level-up raises its three ratings. Minutes and production earn XP.", style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Bar(h.xp / Ratings.xpNeeded().toFloat(), Modifier.fillMaxWidth(), height = 10.dp, wavy = true)
                Spacer(Modifier.height(4.dp))
                Text("${h.xp} / ${Ratings.xpNeeded()} XP to the next level", style = T.small, color = P.sub)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Training focus", Modifier.weight(1f), primary = true) { vm.push(Route.Focus(h.id)) }
                    SmallButton("Player card", Modifier.weight(1f)) { vm.openPlayer(h.id) }
                    SmallButton("Portrait", Modifier.weight(1f)) { vm.push(Route.Portrait(h.id)) }
                }
            }
        }
        if (vm.meTab == 3) item {
            Section("NIL")
            Panel {
                if (r.dealSeason == lg.season && r.deal != NilDeal.NONE) {
                    Text(r.deal.label, style = T.bodyB, color = P.ink)
                    Text("${money(Road.guaranteed(r.deal, r.dealBase))} guaranteed this season" +
                        (if (r.deal == NilDeal.PERFORMANCE) " · bonus so far ${money(Road.bonus(lg, h))}" else ""), style = T.small, color = P.sub)
                } else Text("No deal signed for ${lg.season} yet.", style = T.body, color = P.sub)
                Spacer(Modifier.height(6.dp))
                Text("Career NIL earned: ${money(r.earned)} · in the bank ${money(Road.bank(lg))}" +
                    (if (r.spent > 0) " · spent on your team ${money(r.spent)}" else ""), style = T.small, color = P.good)
            }
            CareerStory(lg)
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** Three deals to pick from at the start of every season. */
@Composable
fun DealPicker(vm: GameViewModel, lg: League) {
    val base = Road.market(lg)
    Section("Sign your ${lg.season} NIL deal")
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (d in listOf(NilDeal.STEADY, NilDeal.PERFORMANCE, NilDeal.BRAND)) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.surface).border(1.dp, P.line, RoundedCornerShape(16.dp))
                    .clickable { vm.act { Road.chooseDeal(it, d) } }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(d.label, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
                    Text(money(Road.guaranteed(d, base)), style = T.numSm, color = P.accent)
                }
                Text(d.blurb, style = T.small, color = P.sub)
            }
        }
    }
    Text("Your market value at this school is ${money(base)} a year, ${money((lg.road?.followers ?: 0) / 1000)} of it from your ${Road.count(lg.road?.followers ?: 0)} followers. Richer programs pay more.", style = T.small, color = P.sub,
        modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
}

/** "Your player" card at the top of Today. */
@Composable
fun HeroTodayCard(vm: GameViewModel, lg: League) {
    val h = lg.playerOrNull(lg.heroId) ?: return
    if (h.teamId < 0) return
    val t = lg.team(h.teamId)
    Section("Your player", action = "Me", onAction = { vm.select(Tab.RECRUIT) })
    Panel(onClick = { vm.select(Tab.RECRUIT) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayerJersey(h, t, 48.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(h.name, style = T.bodyB, color = P.ink)
                Text("${Road.role(lg)} · ${planMin(lg, t, h.id)} min · ${if (lg.pro) "age ${h.age}" else if (h.age > 0) "${h.cls} · age ${h.age}" else h.cls}", style = T.small, color = P.accent)
                if (h.stats.gp > 0) Text(statLine(h), style = T.small, color = P.sub)
            }
            Ring(h.ovr, 44.dp)
        }
        h.log.lastOrNull()?.let { g ->
            Spacer(Modifier.height(8.dp))
            Text("Last game: ${if (g.won) "W" else "L"} ${g.score} ${g.site} ${lg.team(g.oppId).abbr} · ${g.pts} pts, ${g.reb} reb, ${g.ast} ast in ${g.min} min",
                style = T.small, color = P.ink)
        }
        lg.road?.let { r ->
            Spacer(Modifier.height(6.dp))
            val race = if (r.raceRank in 1..100) "${if (lg.pro) "MVP" else "POY"} race No. ${r.raceRank} · " else ""
            Text("$race${Road.fatigueLabel(r.fatigue)} · ${r.practice.label} · ${Road.count(r.followers)} followers", style = T.small, color = P.sub)
        }
        val today = com.nh.hoopscoach.game.Season.userGameToday(lg)
        val rv = Rival.active(lg)
        if (today != null && rv != null && Rival.isDuel(lg, today)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("RIVAL GAME", P.bad)
                Spacer(Modifier.width(8.dp))
                Text("Today: you against ${rv.name} and ${lg.team(rv.teamId).school}.", style = T.small, color = P.ink, modifier = Modifier.weight(1f))
            }
        }
        if (Road.needsDeal(lg)) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag("TO DO", P.warn)
                Spacer(Modifier.width(8.dp))
                Text("Sign your NIL deal for the season.", style = T.small, color = P.ink, modifier = Modifier.weight(1f))
                SmallButton("Pick a deal", primary = true) { vm.select(Tab.RECRUIT) }
            }
        }
    }
}

/** The postgame podium: one question, three ways to answer. */
@Composable
fun PressCard(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val q = r.press ?: return
    Section("Postgame press · ${q.topic}")
    Panel {
        if (q.reporter.isNotEmpty()) Text(q.reporter.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(bottom = 6.dp))
        Row(verticalAlignment = Alignment.Top) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(17.dp)).background(P.accentSoft), contentAlignment = Alignment.Center) {
                Text("?", style = T.head, color = P.accent)
            }
            Spacer(Modifier.width(10.dp))
            Text("\u201c${q.prompt}\u201d", style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        q.answers.forEachIndexed { i, a ->
            Column(
                Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(14.dp)).background(P.surface2)
                    .clickable {
                        var d = 0
                        vm.act { d = Road.answer(it, i) }
                        vm.toast("${if (d >= 0) "+" else "−"}${Road.count(kotlin.math.abs(d))} followers")
                    }.padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(a.text, style = T.body, color = P.ink)
                val tags = listOfNotNull(
                    if (a.persona < 0) "Humble" else if (a.persona > 0) "Brash" else "Even",
                    if (a.fans > 0) "fans like it" else if (a.fans < 0) "fans won't" else null,
                    if (a.teammates > 0) "teammates like it" else if (a.teammates < 0) "teammates won't" else null,
                )
                Text(tags.joinToString(" · "), style = T.small, color = if (a.persona > 0) P.warn else if (a.persona < 0) P.good else P.sub)
            }
        }
        Text("Skip it and the next game brings new questions.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Player of the Year ladder, All-America pace, and the conference and freshman races. */
/** The class rival, side by side with you: overall, scoring, the races and the big board, plus the head-to-head. */
@Composable
internal fun RivalPanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val rv = Rival.player(lg) ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    val active = Rival.active(lg)
    val board = remember(vm.rev, lg) { Offseason.draftBoard(lg) }
    val (w, l) = Rival.record(lg)
    Section("Your rival")
    Panel(onClick = { vm.openPlayer(rv.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (active != null) { PlayerJersey(rv, lg.team(rv.teamId), 44.dp); Spacer(Modifier.width(12.dp)) }
            Column(Modifier.weight(1f)) {
                Text(rv.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        active != null && lg.pro -> "${lg.team(rv.teamId).name} · ${rv.posLabel} · age ${rv.age}"
                        active != null -> "${lg.team(rv.teamId).school} · ${rv.posLabel} · ${rv.cls} · was the No. ${rv.recruitRank} recruit"
                        lg.pro && r.rivalLeft.isEmpty() -> "Still in college. He enters next year's draft."
                        else -> "Your rival ${r.rivalLeft}."
                    },
                    style = T.small, color = P.sub, maxLines = 2,
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text("$w-$l", style = T.num, color = if (w > l) P.good else if (l > w) P.bad else P.ink)
                Text("HEAD TO HEAD", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            }
        }
        if (active != null) {
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                Spacer(Modifier.weight(1f))
                Text("YOU", style = T.label, color = P.accent, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                Text(rv.last.uppercase(), style = T.label, color = P.sub, modifier = Modifier.width(84.dp), textAlign = TextAlign.End, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // On the scale every other screen shows (the pros' 2K scale), not the engine's.
            RivalRow("Overall", "${shown(h.ovr)}", "${shown(rv.ovr)}", h.ovr.compareTo(rv.ovr))
            if (h.stats.gp > 0 || rv.stats.gp > 0) RivalRow("Points per game", "%.1f".format(h.stats.ppg), "%.1f".format(rv.stats.ppg), h.stats.ppg.compareTo(rv.stats.ppg))
            val myRace = Rival.raceRank(lg, h)
            val hisRace = Rival.raceRank(lg, rv)
            if (myRace in 1..100 || hisRace in 1..100) RivalRow(if (lg.pro) "MVP race" else "Player of the Year race", rankText(myRace), rankText(hisRace), rankCompare(myRace, hisRace))
            if (lg.pro) {
                val ms = h.awards.count { it.endsWith(" All-Star") }
                val hs = rv.awards.count { it.endsWith(" All-Star") }
                RivalRow("All-Star selections", "$ms", "$hs", ms.compareTo(hs))
                val mr = h.awards.count { it.endsWith(" Champion") }
                val hr = rv.awards.count { it.endsWith(" Champion") }
                RivalRow("Rings", "$mr", "$hr", mr.compareTo(hr))
            } else {
                val mb = board[h.id] ?: 0
                val hb = board[rv.id] ?: 0
                RivalRow("Big board", rankText(mb), rankText(hb), rankCompare(mb, hb))
            }
        }
        r.duels.lastOrNull()?.let { d ->
            Spacer(Modifier.height(8.dp))
            val where = if (lg.pro && d.season < r.proStart) "in college" else "against ${if (lg.pro) lg.team(d.opp).name else lg.team(d.opp).school}"
            Text("Last meeting: ${if (d.won) "W" else "L"} $where, you ${d.me} points, ${rv.last} ${d.him}", style = T.small, color = P.ink)
        }
        val next = if (active != null) Rival.nextDuel(lg) else null
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                next != null -> "Next meeting: ${if (next.home == h.teamId) "vs" else "at"} ${if (lg.pro) lg.team(rv.teamId).name else lg.team(rv.teamId).school}. Win the duel and your following grows faster."
                active != null && lg.pro -> if (lg.phase == com.nh.hoopscoach.game.Phase.REGULAR) "No meeting left on the schedule. You may still cross paths in the playoffs." else "The new schedule will say when you meet."
                active != null -> "No meeting on the schedule. You may still cross paths in March."
                lg.pro -> if (r.rivalLeft.isEmpty()) "He's still in college. He'll be in the league next year." else "His career is over. Yours isn't."
                else -> "He's gone pro. Now it's your turn."
            },
            style = T.small, color = P.sub,
        )
    }
}

private fun rankText(n: Int) = if (n in 1..100) "No. $n" else "—"

/** 1 when [mine] is the better (lower) rank, -1 when his is; an unranked side always loses. */
private fun rankCompare(mine: Int, his: Int): Int = when {
    mine in 1..100 && his !in 1..100 -> 1
    his in 1..100 && mine !in 1..100 -> -1
    mine !in 1..100 && his !in 1..100 -> 0
    mine == his -> 0
    mine < his -> 1
    else -> -1
}

@Composable
private fun RivalRow(label: String, mine: String, his: String, better: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = T.small, color = P.sub, modifier = Modifier.weight(1f))
        Text(mine, style = T.bodyB, color = if (better > 0) P.good else P.ink, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
        Text(his, style = T.bodyB, color = if (better < 0) P.bad else P.ink, modifier = Modifier.width(84.dp), textAlign = TextAlign.End)
    }
}

@Composable
internal fun RacePanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.playerOrNull(lg.heroId) ?: return
    if (h.teamId < 0) return
    val ladder = remember(vm.rev, lg) { Awards.ladder(lg) }
    val rank = remember(vm.rev, lg) { Road.raceRank(lg) }
    Section("Award races")
    Panel {
        if (rank == 0) {
            Text("The races start once you've played.", style = T.body, color = P.sub)
            return@Panel
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (lg.pro) "MVP RACE" else "PLAYER OF THE YEAR", style = T.label, color = P.sub)
                Text(if (rank <= 100) "No. $rank" else "Outside the top 100", style = T.title.copy(fontSize = if (rank <= 100) 30.sp else 22.sp), color = P.accent)
                Text(if (lg.pro) proPace(rank) else Road.allAmericaPace(rank), style = T.small, color = P.ink)
            }
            if (r.lastRaceRank in 1..100 && r.raceRank in 1..100 && r.raceRank != r.lastRaceRank) {
                val up = r.raceRank < r.lastRaceRank
                Text("${if (up) "▲" else "▼"} ${kotlin.math.abs(r.lastRaceRank - r.raceRank)} this week", style = T.bodyB, color = if (up) P.good else P.bad)
            }
        }
        val t = lg.team(h.teamId)
        val conf = Road.rankAmong(lg) { _, pt -> pt.confId == t.confId }
        val fr = if (lg.pro) (if (h.proYears == 0) Road.rankAmong(lg) { p, _ -> p.proYears == 0 } else 0) else if (h.year == 1) Road.rankAmong(lg) { p, _ -> p.year == 1 } else 0
        Spacer(Modifier.height(6.dp))
        Text(listOfNotNull(
            if (conf > 0) (if (lg.pro) "No. $conf in the ${lg.conf(t.confId).name}" else "No. $conf in the ${lg.conf(t.confId).abbr} Player of the Year race") else null,
            if (fr > 0) (if (lg.pro) "No. $fr in the Rookie of the Year race" else "No. $fr among freshmen") else null,
        ).joinToString(" · "), style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))
        val top = ladder.take(5)
        top.forEachIndexed { i, (p, pt) -> RaceRow(i + 1, p, pt, p.id == h.id) }
        if (rank > 5) {
            Text("···", style = T.small, color = P.faint, modifier = Modifier.padding(start = 6.dp))
            RaceRow(rank, h, t, true)
        }
        Text("The ladder uses the same formula as the end-of-season awards: production, efficiency and winning.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
        if (h.stats.gp < (t.gp * 0.6)) Text("Play in 60% of games to qualify for ${if (lg.pro) "league" else "national"} awards.", style = T.small, color = P.warn, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun RaceRow(rank: Int, p: Player, t: Team, you: Boolean) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(if (you) P.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
            .padding(horizontal = 6.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$rank", style = T.numSm, color = P.accent, modifier = Modifier.width(34.dp))
        TeamBadge(t, 22.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(if (you) "${p.name} (you)" else p.name, style = T.bodyB.copy(fontSize = 14.sp), color = if (you) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${t.abbr} · ${statLine(p)}", style = T.small, color = P.sub, maxLines = 1)
        }
    }
}

/** Your own staff, paid out of your NIL money one season at a time. */
@Composable
internal fun StaffPanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val bank = Road.bank(lg)
    Section("Your team")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("IN THE BANK", style = T.label, color = P.sub)
                Text(money(bank), style = T.title.copy(fontSize = 28.sp), color = P.good)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${r.hired.count { Road.has(lg, it) }} of ${com.nh.hoopscoach.game.Staff.entries.size}", style = T.bodyB, color = P.ink)
                Text("HIRED THIS SEASON", style = T.label.copy(fontSize = 9.sp), color = P.sub)
            }
        }
        Text("Your ${if (lg.pro) "" else "NIL "}money pays for people who make you better. Contracts run through the season.", style = T.small, color = P.sub)
        Spacer(Modifier.height(10.dp))
        for (s in com.nh.hoopscoach.game.Staff.entries) {
            val on = Road.has(lg, s)
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (on) P.accentSoft else P.surface2).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(s.label, style = T.bodyB, color = if (on) P.accent else P.ink)
                    Text(s.blurb, style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                when {
                    on -> Tag("HIRED", P.good)
                    lg.phase == com.nh.hoopscoach.game.Phase.OFFSEASON -> Text(money(s.cost), style = T.small, color = P.faint)
                    else -> SmallButton("Hire · ${money(s.cost)}", primary = bank >= s.cost, enabled = bank >= s.cost) {
                        vm.act { if (Road.hire(it, s)) vm.toast("${s.label} hired for ${it.season}") else vm.toast("Not enough in the bank") }
                    }
                }
            }
        }
        if (lg.phase == com.nh.hoopscoach.game.Phase.OFFSEASON) Text("Hire for next season once it starts.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
        else if (Road.needsDeal(lg)) Text("Sign your NIL deal first: the guaranteed money lands in your bank right away.", style = T.small, color = P.warn, modifier = Modifier.padding(top = 4.dp))
    }
}

/** This week's practice plan, and what it's costing. */
@Composable
internal fun PracticePanel(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    Section("Practice this week")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("FATIGUE", style = T.label, color = P.sub, modifier = Modifier.weight(1f))
            Text(Road.fatigueLabel(r.fatigue), style = T.bodyB, color = when { r.fatigue >= 75 -> P.bad; r.fatigue >= 55 -> P.warn; else -> P.good })
        }
        Spacer(Modifier.height(6.dp))
        Bar(r.fatigue / 100f, Modifier.fillMaxWidth(), color = when { r.fatigue >= 75 -> P.bad; r.fatigue >= 55 -> P.warn; else -> P.good }, height = 6.dp)
        Text(Road.fatigueEffect(r.fatigue), style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(10.dp))
        for (plan in com.nh.hoopscoach.game.Practice.entries) {
            val sel = r.practice == plan
            val boosts = Road.practiceBoosts(lg, plan).joinToString(", ") { it.label }
            Column(
                Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (sel) P.accentSoft else P.surface2)
                    .border(if (sel) 2.dp else 0.dp, if (sel) P.accent else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(14.dp))
                    .clickable { vm.act { Road.setPractice(it, plan) } }.padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(plan.label, style = T.bodyB, color = if (sel) P.accent else P.ink, modifier = Modifier.weight(1f))
                    Text(if (plan.tiring > 0) "+${plan.tiring} fatigue" else "${plan.tiring} fatigue", style = T.small, color = if (plan.tiring > 5) P.warn else if (plan.tiring < 0) P.good else P.sub)
                }
                Text(if (boosts.isNotEmpty()) "${plan.blurb.substringBefore('.')}. Builds $boosts." else plan.blurb, style = T.small, color = P.sub)
            }
        }
        Text("Applied after every game. Minutes tire you too; each game you recover a little.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** Followers, persona, sponsors and recent press. */
@Composable
internal fun MediaPanel(lg: League) {
    val r = lg.road ?: return
    Section("Media")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Road.count(r.followers), style = T.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = P.accent)
                val d = r.followers - r.seasonStartFollowers
                Text("FOLLOWERS · ${if (d >= 0) "+" else "−"}${Road.count(kotlin.math.abs(d))} THIS SEASON", style = T.label, color = P.sub)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Road.personaLabel(r.persona), style = T.bodyB, color = P.ink)
                Text("PERSONA", style = T.label.copy(fontSize = 9.sp), color = P.sub)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(Road.personaBlurb(r.persona), style = T.small, color = P.sub)
        Text(
            if (lg.pro) "Endorsements pay about \$2 a follower for the first two million, 50 cents after that: ${money(com.nh.hoopscoach.game.ProCareer.endorsements(lg))} a year right now."
            else "Every follower adds \$1 a year to your NIL value: ${money(r.followers / 1000)} right now.",
            style = T.small, color = P.good,
        )
        Road.nextMilestone(lg)?.let { (need, pay) ->
            Spacer(Modifier.height(8.dp))
            Bar(r.followers / need.toFloat(), Modifier.fillMaxWidth(), height = 10.dp, wavy = true)
            Spacer(Modifier.height(4.dp))
            Text("Next sponsor at ${Road.count(need)} followers: ${money(pay)} for one post.", style = T.small, color = P.sub)
        }
        if (r.moments > 0) {
            Spacer(Modifier.height(8.dp))
            Text("Big moments: came through in ${r.momentsWon} of ${r.moments}.", style = T.small, color = P.ink)
        }
        if (r.pressLog.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("RECENT PRESS", style = T.label, color = P.sub)
            for (line in r.pressLog.takeLast(3).reversed()) Text(line, style = T.small, color = P.ink, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** Season by season, as written at the end of each one. */
@Composable
internal fun CareerStory(lg: League) {
    val r = lg.road ?: return
    if (r.story.isEmpty()) return
    Section("Your story")
    GroupList(r.story) { line ->
        Text(line, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp))
    }
}

// ---------- the career tab ----------

@Composable
fun HeroCareerScreen(vm: GameViewModel, lg: League) {
    if (Road.inGreenRoom(lg)) { GreenRoomWait(vm); return }
    val h = lg.hero
    val r = lg.road
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Road to the League", h?.name ?: "Your career", r?.let { "Since ${it.startSeason} · ${it.schools.joinToString(" → ") { id -> lg.team(id).school }}" }) {
                IconCircle({ vm.push(Route.Settings) }) { Icon(NavIcons.Gear, "Settings", tint = P.sub, modifier = Modifier.size(22.dp)) }
            }
        }
        item { TrophyRoomLink(vm, lg) }
        if (h != null) item { CareerTotals(h) }
        item { CareerStory(lg) }
        if (h != null && h.awards.isNotEmpty()) item {
            Section("Honors")
            GroupList(h.awards.reversed()) { a -> Text(a, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item {
            if (r != null) Panel(Modifier.padding(top = 14.dp)) {
                Text("Career NIL earned: ${money(r.earned)}", style = T.bodyB, color = P.good)
                if (r.spent > 0) Text("Invested in your own team: ${money(r.spent)}", style = T.small, color = P.ink)
                Text("${Road.count(r.followers)} followers · ${Road.personaLabel(r.persona)}", style = T.small, color = P.ink)
                if (r.ended) Text(r.ending, style = T.small, color = P.sub)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CareerTotals(h: Player) {
    val tot = StatLine()
    h.career.forEach { tot.add(it.s) }
    tot.add(h.stats)
    if (tot.gp == 0) return
    Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
        StatRow(listOf(
            Triple("GP", "${tot.gp}", null), Triple("PPG", "%.1f".format(tot.ppg), null),
            Triple("RPG", "%.1f".format(tot.rpg), null), Triple("APG", "%.1f".format(tot.apg), null),
        ), style = T.numSm)
    }
}

// ---------- the offseason ----------

@Composable
fun RoadOffseasonFlow(vm: GameViewModel, lg: League) {
    val os = lg.offseason ?: return
    val r = lg.road ?: return
    if (Road.inGreenRoom(lg)) { GreenRoom(vm, lg); return }
    if (r.goingPro) { GoProScreen(vm, lg); return }
    if (r.ended) { RoadEnding(vm, lg); return }
    val h = lg.playerOrNull(lg.heroId) ?: return
    val t = lg.team(h.teamId)
    val steps = ROAD_STEPS
    val idx = when (os.step) { OffStep.ROSTER -> 0; OffStep.DRAFT -> 1; OffStep.CAREER, OffStep.DEVELOPMENT -> 2; else -> 3 }
    val label = when (os.step) {
        OffStep.ROSTER -> "Go to Draft Night"
        OffStep.DRAFT -> "Next: the summer"
        else -> if (r.portal) "Stay at ${t.school} and start ${lg.season + 1}" else "Start the ${lg.season + 1} season"
    }
    Column(Modifier.fillMaxSize()) {
        StepHeader("Offseason · ${lg.season}", steps, idx)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            when (os.step) {
                OffStep.ROSTER -> decisionStep(vm, lg, h, t)
                OffStep.DRAFT -> draftResultStep(vm, lg, h, t)
                else -> summerStep(vm, lg, h, t)
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        FlowFooter(null, label) { vm.roadNext() }
    }
}

private fun LazyListScope.decisionStep(vm: GameViewModel, lg: League, h: Player, t: Team) {
    val r = lg.road!!
    item {
        val board = Offseason.draftBoard(lg)
        val rank = board[h.id]
        Panel {
            Text("${classLong(h.year).uppercase()} SEASON COMPLETE", style = T.label, color = P.accent)
            Text("${h.name} · ${h.ovr} overall", style = T.head, color = P.ink)
            Text("Draft projection: ${Offseason.projection(rank, h.year)}" + (rank?.let { " · No. $it on the big board" } ?: ""), style = T.small, color = P.sub)
            Rival.active(lg)?.let { rv ->
                val rr = board[rv.id]
                Text("Your rival ${rv.name} is " + (rr?.let { "No. $it on the big board" } ?: "not on the big board yet") + ".", style = T.small, color = P.ink)
            }
        }
        if (h.year >= 4) {
            Panel(Modifier.padding(top = 10.dp)) {
                Text("Your eligibility is up. Draft Night decides what comes next.", style = T.body, color = P.ink)
            }
            return@item
        }
        Section("Your decision")
        val choice = when { r.declared -> 1; r.portal -> 2; else -> 0 }
        Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ChoiceCard("Return to ${t.school}", "Another year to grow, play and raise your stock.", choice == 0) { vm.act { r.declared = false; r.portal = false } }
            ChoiceCard("Declare for the draft", "Go if a team picks you. If nobody does, you come back for another season.", choice == 1) { vm.act { r.declared = true; r.portal = false } }
            ChoiceCard("Enter the transfer portal", "Pick a new school this summer. You keep your eligibility.", choice == 2) { vm.act { r.portal = true; r.declared = false } }
        }
    }
}

@Composable
private fun ChoiceCard(title: String, blurb: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (selected) P.accentSoft else P.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) P.accent else P.line, RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(14.dp),
    ) {
        Text(title, style = T.bodyB, color = if (selected) P.accent else P.ink)
        Text(blurb, style = T.small, color = P.sub)
    }
}

private fun LazyListScope.draftResultStep(vm: GameViewModel, lg: League, h: Player, t: Team) {
    item {
        Panel {
            Text("BACK TO SCHOOL", style = T.label, color = P.accent)
            Text("You'll play your ${classLong(h.year + 1).lowercase()} season at ${t.school}.", style = T.head, color = P.ink)
        }
        val picks = lg.draftHistory.filter { it.season == lg.season }.take(30)
        if (picks.isNotEmpty()) {
            Section("First round")
            GroupList(picks) { d ->
                ListRow(onClick = if (d.pid >= 0 && lg.anyPlayer(d.pid) != null) ({ vm.openPlayer(d.pid) }) else null) {
                    Text("${d.pick}", style = T.numSm, color = P.accent, modifier = Modifier.width(34.dp))
                    Column(Modifier.weight(1f)) {
                        Text(d.playerName, style = T.bodyB, color = P.ink, maxLines = 1)
                        Text("${d.school} · ${d.pos} · ${com.nh.hoopscoach.game.classLabel(d.cls)}", style = T.small, color = P.sub)
                    }
                    Text("${d.ovr}", style = T.numSm, color = P.ink)
                }
            }
        }
    }
}

@Composable
internal fun SummerCard(lg: League, ev: SummerEvent, onGo: () -> Unit) {
    val u19 = ev == SummerEvent.U19 || ev == SummerEvent.NATIONAL_TEAM
    Panel(color = if (u19) P.hero else P.surface, onClick = onGo) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                if (u19) Text("NATIONAL TEAM INVITE" + (Summer.proTournament(lg.season)?.takeIf { lg.pro }?.let { " · ${it.uppercase()}" } ?: ""), style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text(ev.label, style = T.bodyB, color = if (u19) P.onHero else P.ink)
                Text(ev.blurb, style = T.small, color = if (u19) P.onHero.copy(alpha = 0.85f) else P.sub)
                Spacer(Modifier.height(4.dp))
                Text(
                    (if (u19) "Every rating up to +1" else ev.boosts.joinToString(" · ") { "${it.label} up to +2" }) + " · " + Summer.followerRange(lg, ev),
                    style = T.small, color = if (u19) P.onHero else P.accent,
                )
            }
            Spacer(Modifier.width(10.dp))
            SmallButton("Go", primary = !u19, onClick = onGo)
        }
    }
}

// ---------- draft night ----------

private val ROAD_STEPS = listOf("Decision", "Draft Night", "Summer", "Next season")

private val GREEN_ROOM = listOf(
    "Cameras sweep across the green room.",
    "Your agent checks his phone again.",
    "Your family hasn't touched the food.",
    "A scout gives you a nod from across the room.",
    "The commissioner walks back to the podium.",
    "Someone at your table exhales. Not yet.",
    "Your coach texts: \"Stay ready.\"",
    "The room is a little emptier now.",
)

/** How long each pick takes to come in: the lottery is slow, the second round flies. */
private fun pickPace(pick: Int): Long = when { pick == 1 -> 1800L; pick <= 14 -> 1200L; pick <= 30 -> 700L; else -> 350L }

/** What the room is talking about while the next team is on the clock. */
private fun greenRoomLine(last: DraftPick?, shown: Int, h: Player, schoolId: Int, rivalId: Int): String = when {
    last == null -> "The commissioner steps to the podium."
    last.pid == rivalId -> "Your rival ${last.playerName} is off the board at No. ${last.pick}."
    last.teamId == schoolId && last.pid != h.id -> "Your teammate ${last.playerName} just went No. ${last.pick}."
    last.pos == h.pos -> "Another ${last.pos} comes off the board."
    else -> GREEN_ROOM[shown % GREEN_ROOM.size]
}

/** Draft Night from the green room: picks come in one at a time until your name is called, or the night ends. */
@Composable
private fun GreenRoom(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero ?: return
    val picks = remember(lg.season, lg.draftHistory.size) { lg.draftHistory.filter { it.season == lg.season }.sortedBy { it.pick } }
    val mine = picks.indexOfFirst { it.pid == lg.heroId }
    val stop = if (mine >= 0) mine + 1 else picks.size
    val shown = if (vm.revealSeason == lg.season) vm.reveal.coerceAtMost(stop) else 0
    var ringing by remember { mutableStateOf(false) }
    LaunchedEffect(lg.season, stop) {
        if (vm.revealSeason != lg.season) { vm.revealSeason = lg.season; vm.reveal = 0 }
        while (vm.reveal < stop) {
            if (vm.reveal == mine) { ringing = true; delay(2400); ringing = false } else delay(pickPace(vm.reveal + 1))
            vm.reveal = (vm.reveal + 1).coerceAtMost(stop)
        }
    }
    val done = shown >= stop
    val school = r.schools.lastOrNull() ?: h.teamId
    val myPick = picks.getOrNull(mine)
    val onHero = P.onHero
    Column(Modifier.fillMaxSize()) {
        StepHeader("Offseason · ${lg.season}", ROAD_STEPS, 1)
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Panel(color = P.hero) {
                    when {
                        done && myPick != null -> {
                            Text("YOUR NAME IS CALLED", style = T.label, color = onHero.copy(alpha = 0.8f))
                            Text("NO. ${myPick.pick} OVERALL", style = T.hero, color = onHero)
                            val verdict = when {
                                r.draftRank <= 0 -> ""
                                myPick.pick <= r.draftRank - 5 -> " Earlier than anyone projected."
                                myPick.pick >= r.draftRank + 8 -> " A longer wait than the mock drafts said, but you're in."
                                else -> " Right about where the mock drafts had you."
                            }
                            Text((if (myPick.proTeam.isNotEmpty()) "The ${myPick.proTeam} take you. " else "") + "${h.name} hugs his family and walks to the stage.$verdict", style = T.small, color = onHero.copy(alpha = 0.9f))
                        }
                        done -> {
                            Text("THE DRAFT IS OVER", style = T.label, color = onHero.copy(alpha = 0.8f))
                            Text("UNDRAFTED", style = T.hero, color = onHero)
                            val next = if (r.goingPro) "Your agent gets you a two-way deal. Make a roster and prove them wrong."
                                else if (r.ended) "Your agent already has calls in from overseas."
                                else "You head back to ${lg.team(school).school} for your ${classLong(h.year + 1).lowercase()} season."
                            Text("${picks.size} names called, none of them yours. $next", style = T.small, color = onHero.copy(alpha = 0.9f))
                        }
                        else -> {
                            Text("THE GREEN ROOM · ${lg.season} DRAFT", style = T.label, color = onHero.copy(alpha = 0.8f))
                            Text("PICK ${shown + 1}", style = T.hero, color = onHero)
                            Text(
                                if (ringing) "Your phone is ringing..." else "On the clock. " + greenRoomLine(picks.getOrNull(shown - 1), shown, h, school, r.rivalId),
                                style = if (ringing) T.bodyB else T.small, color = onHero.copy(alpha = 0.9f),
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Bar(if (picks.isEmpty()) 1f else shown / picks.size.toFloat(), Modifier.fillMaxWidth(), color = onHero, track = onHero.copy(alpha = 0.22f))
                    Spacer(Modifier.height(6.dp))
                    val status = when {
                        done && myPick == null -> "All ${picks.size} picks in"
                        done -> "Round ${if (myPick!!.pick <= 30) 1 else 2} · pick ${myPick.pick} of ${picks.size}"
                        else -> "Round ${if (shown < 30) 1 else 2} · $shown of ${picks.size} picks in"
                    }
                    val proj = if (r.draftRank > 0) " · mock draft No. ${r.draftRank}" else ""
                    Text(status + proj, style = T.small, color = onHero.copy(alpha = 0.75f))
                }
            }
            if (shown > 0) item {
                Section(if (done) "The board" else "Picks so far")
                GroupList(picks.take(shown).asReversed()) { d -> DraftRow(vm, lg, d, me = d.pid == lg.heroId, fresh = !done && d.pick == shown, rival = d.pid == r.rivalId) }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
        when {
            !done -> FlowFooter(null, "Skip ahead") { vm.revealSeason = lg.season; vm.reveal = stop }
            // On to the going-pro screen, where you pick the league (today's or a past era).
            r.goingPro -> FlowFooter(null, "Next: choose your league") { vm.act { Road.watchedDraft(it) } }
            r.ended -> FlowFooter(null, "Continue") { vm.act { Road.watchedDraft(it) } }
            else -> FlowFooter(null, "Next: the summer") { vm.roadNext() }
        }
    }
}

/** Other tabs during Draft Night: no spoilers, just a way back to the green room. */
@Composable
private fun GreenRoomWait(vm: GameViewModel) {
    Column(Modifier.fillMaxWidth()) {
        ScreenHeader("Road to the League", "Draft Night", "You're waiting in the green room. Your story picks up once the night is over.")
        Box(Modifier.padding(horizontal = Gutter)) {
            PrimaryButton("Back to the green room", Modifier.fillMaxWidth()) { vm.select(Tab.TODAY) }
        }
    }
}

@Composable
private fun DraftRow(vm: GameViewModel, lg: League, d: DraftPick, me: Boolean, fresh: Boolean, rival: Boolean = false) {
    val t = lg.team(d.teamId)
    ListRow(onClick = if (d.pid >= 0 && lg.anyPlayer(d.pid) != null) ({ vm.openPlayer(d.pid) }) else null, modifier = if (me) Modifier.background(P.accentSoft) else Modifier) {
        Text("${d.pick}", style = T.numSm, color = P.accent, modifier = Modifier.width(34.dp))
        Jersey(d.number, androidx.compose.ui.graphics.Color(t.primary), androidx.compose.ui.graphics.Color(t.secondary), 40.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(d.playerName, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (me) { Spacer(Modifier.width(6.dp)); Tag("YOU", P.accent, filled = true) }
                else if (rival) { Spacer(Modifier.width(6.dp)); Tag("RIVAL", P.bad) }
                else if (fresh) { Spacer(Modifier.width(6.dp)); Tag("JUST IN", P.good) }
            }
            if (d.proTeam.isNotEmpty()) Text("${d.proTeam} · ${t.abbr} · ${com.nh.hoopscoach.game.classLabel(d.cls)} · ${d.pos}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text("${t.school} · ${com.nh.hoopscoach.game.classLabel(d.cls)} · ${d.pos}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("${d.ovr}", style = T.numSm, color = tier(d.ovr))
    }
}

private fun LazyListScope.summerStep(vm: GameViewModel, lg: League, h: Player, t: Team) {
    val r = lg.road!!
    val os = lg.offseason!!
    item {
        val g = os.growth.firstOrNull { it.pid == h.id }
        Panel {
            Text("SUMMER WORKOUTS", style = T.label, color = P.accent)
            if (g != null) {
                Text("${g.before} → ${h.ovr} overall", style = T.title.copy(fontSize = 30.sp), color = P.ink)
                val gains = com.nh.hoopscoach.game.Attr.entries.mapIndexedNotNull { i, a -> g.gains.getOrNull(i)?.takeIf { it > 0 }?.let { "${a.short} +$it" } }
                if (gains.isNotEmpty()) Text(gains.joinToString(" · "), style = T.small, color = P.good)
            } else Text("${h.ovr} overall heading into ${classLong(h.year).lowercase()} year.", style = T.head, color = P.ink)
        }
    }
    if (Summer.open(lg)) item {
        Section("Summer invitations")
        Text("Pick one. There's only room for one in your summer.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
        for (ev in Summer.invites(lg)) {
            Spacer(Modifier.height(8.dp))
            SummerCard(lg, ev) { vm.act { Summer.attend(it, ev) } }
        }
    } else if (r.summer == lg.season && r.summerNote.isNotEmpty()) item {
        Section("Your summer")
        Panel(color = P.accentSoft) { Text(r.summerNote, style = T.body, color = P.ink) }
    }
    if (r.portal) item {
        Section("Transfer offers")
        if (r.offers.isEmpty()) Panel { Text("No offers came in. You'll stay at ${t.school}.", style = T.body, color = P.sub) }
        OfferBrowser(lg, r.offers, h, -1) { o -> vm.act { Road.transfer(it, o.teamId) }; vm.toast("Welcome to ${lg.team(o.teamId).school}") }
        Text("Tap a school to transfer. Or stay with the button below.", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
    }
}

/** How it ended: drafted, or not, and the whole story. */
@Composable
fun RoadEnding(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Road to the League", "Career complete", null)
            Panel(color = P.hero) {
                Text(if (r.draftPick > 0) "DRAFTED" else "UNDRAFTED", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text(if (r.draftPick > 0) "NO. ${r.draftPick} OVERALL" else "OVERSEAS", style = T.hero, color = P.onHero)
                Text(r.ending, style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        if (h != null) item { Spacer(Modifier.height(10.dp)); CareerTotals(h) }
        item { CareerStory(lg) }
        item {
            Panel(Modifier.padding(top = 14.dp)) {
                Text("Career NIL earned: ${money(r.earned)}", style = T.bodyB, color = P.good)
                if (r.spent > 0) Text("Invested in your own team: ${money(r.spent)}", style = T.small, color = P.ink)
                Text("${Road.count(r.followers)} followers · ${Road.personaLabel(r.persona)}", style = T.small, color = P.ink)
                Text("Schools: ${r.schools.joinToString(" → ") { lg.team(it).school }}", style = T.small, color = P.sub)
            }
        }
        item {
            val jobs = remember(lg) { com.nh.hoopscoach.game.Afterward.coachingJobs(lg) }
            val years = com.nh.hoopscoach.game.Afterward.yearsAway(lg)
            Section("What's next: coaching")
            Text("After $years years overseas you come home to coach. Taking a job starts your coaching career in ${lg.season + years}, with your playing career on your résumé. Win in college and NBA teams call.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
            GroupList(jobs, inset = 16.dp) { j ->
                ListRow(onClick = { vm.retireToCoach(j.school) }) {
                    Column(Modifier.weight(1f)) {
                        Text(j.school, style = T.bodyB, color = P.ink)
                        Text(j.why, style = T.small, color = P.sub)
                    }
                    SmallButton("Coach here", primary = true) { vm.retireToCoach(j.school) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = Gutter)) {
                SecondaryButton("Start a new career", Modifier.fillMaxWidth()) { vm.abandonCareer() }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ---------- season review ----------

fun LazyListScope.heroSeasonPage(vm: GameViewModel, lg: League) {
    val h = lg.playerOrNull(lg.heroId) ?: return
    val r = lg.road ?: return
    item {
        Panel {
            Text("YOUR ${lg.season} SEASON", style = T.label, color = P.accent)
            Text(h.name, style = T.title, color = P.ink)
            Text("${h.cls} · ${h.ovr} overall · ${Road.role(lg)}", style = T.small, color = P.sub)
            Spacer(Modifier.height(10.dp))
            StatRow(listOf(
                Triple("PPG", "%.1f".format(h.stats.ppg), null), Triple("RPG", "%.1f".format(h.stats.rpg), null),
                Triple("APG", "%.1f".format(h.stats.apg), null), Triple("MPG", "%.1f".format(h.stats.mpg), null),
            ), style = T.numSm)
        }
        r.story.lastOrNull()?.let { Panel(Modifier.padding(top = 10.dp)) { Text(it, style = T.small, color = P.ink) } }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.padding(horizontal = Gutter)) { SecondaryButton("Open your player card", Modifier.fillMaxWidth()) { vm.openPlayer(h.id) } }
    }
}

/** A few real college players of an era's first season, for the creator. */
private fun eraCollegeNames(era: com.nh.hoopscoach.game.Era): String =
    com.nh.hoopscoach.game.EraData.collegeSeason(era.year).mapNotNull { c -> com.nh.hoopscoach.game.EraData.pickOf(c.id)?.let { it to c } }
        .sortedBy { it.first.pick + (it.first.year - it.second.season) * 100 }.take(3).joinToString(", ") { "${it.first.name}, ${it.second.ours}" }

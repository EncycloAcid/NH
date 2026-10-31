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
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.Geo
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NilDeal
import com.nh.hoopscoach.game.OffStep
import com.nh.hoopscoach.game.Offseason
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.SchoolOffer
import com.nh.hoopscoach.game.StatLine
import com.nh.hoopscoach.game.Team
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
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var pos by remember { mutableStateOf(Pos.SG) }
    var height by remember { mutableIntStateOf(76) }
    var weight by remember { mutableIntStateOf(200) }
    var town by remember { mutableStateOf("") }
    var state by remember { mutableStateOf("GA") }
    var style by remember { mutableStateOf(Arch.SHOT_CREATOR) }
    var stars by remember { mutableIntStateOf(4) }
    var number by remember { mutableIntStateOf(3) }
    val range = heightRange(pos)
    val styles = Ratings.styles(pos)
    if (style !in styles) style = styles.first()
    if (height !in range) height = (range.first + range.last) / 2
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item { header() }
            item {
                Section("Your player")
                Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(first, { first = it.take(16) }, placeholder = { Text("First name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    OutlinedTextField(last, { last = it.take(18) }, placeholder = { Text("Last name", color = P.faint) }, singleLine = true, modifier = Modifier.weight(1f),
                        colors = fieldColors(), keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                Section("Position")
                Segmented(Pos.entries.map { it.name }, pos.ordinal, { pos = Pos.entries[it] })
                Section("Size")
                Panel {
                    Text("Height · ${Ratings.heightLabel(height)}", style = T.bodyB, color = P.ink)
                    HoopsSlider(height.toFloat(), { height = it.roundToInt() }, range.first.toFloat()..range.last.toFloat())
                    Text("Weight · $weight lbs", style = T.bodyB, color = P.ink)
                    HoopsSlider(weight.toFloat(), { weight = it.roundToInt() }, 160f..290f)
                }
                Section("Play style")
                Picker("Archetype", style.label, styles.map { it.label to it.blurb }, Modifier.padding(horizontal = Gutter).fillMaxWidth()) { style = styles[it] }
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
                    } + " Every level can reach the league.",
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
                vm.roadFindOffers(Road.Spec(first, last, pos, height, weight, town, state, style, stars, number))
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
                            Text("${hero.pos} · ${Ratings.heightLabel(hero.height)} · ${hero.weight} lbs · ${hero.hometown}", style = T.small, color = P.sub)
                            Text("${Road.starLabel(hero.stars)} · No. ${hero.recruitRank} nationally · ${hero.style.label}", style = T.small, color = P.accent)
                        }
                        Ring(hero.ovr, 48.dp, label = "OVR", textSize = 17.sp)
                        Spacer(Modifier.width(6.dp))
                        Ring(hero.pot, 48.dp, label = "POT", textSize = 17.sp)
                    }
                }
                Section("Scholarship offers · ${vm.roadOffers.size}")
                Text("Pick where you'll play. A school with fewer good players at your position means minutes sooner; richer programs pay more NIL.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
                Spacer(Modifier.height(6.dp))
                GroupList(vm.roadOffers, inset = 64.dp) { o -> OfferRow(lg, o, hero, pick == o.teamId) { pick = o.teamId } }
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
            ScreenHeader("Road to the League · ${classLong(h.year)}", h.last, "${h.pos} · ${Ratings.heightLabel(h.height)} · ${t.school}") { PlayerJersey(h, t, 50.dp) }
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
        if (lg.road?.press != null) item { PressCard(vm, lg) }
        item { MediaPanel(lg) }
        item {
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
            Section("Depth chart · ${h.pos}")
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
        item {
            Section("Development")
            Panel {
                Text("Training focus: ${h.focus.label}", style = T.bodyB, color = P.ink)
                Text("Every level-up raises its three ratings. Minutes and production earn XP.", style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                Bar(h.xp / Ratings.xpNeeded().toFloat(), Modifier.fillMaxWidth(), height = 6.dp)
                Spacer(Modifier.height(4.dp))
                Text("${h.xp} / ${Ratings.xpNeeded()} XP to the next level", style = T.small, color = P.sub)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallButton("Training focus", Modifier.weight(1f), primary = true) { vm.push(Route.Focus(h.id)) }
                    SmallButton("Player card", Modifier.weight(1f)) { vm.openPlayer(h.id) }
                    SmallButton("Edit look", Modifier.weight(1f)) { vm.push(Route.EditPlayer(h.id)) }
                }
            }
        }
        item {
            Section("NIL")
            Panel {
                if (r.dealSeason == lg.season && r.deal != NilDeal.NONE) {
                    Text(r.deal.label, style = T.bodyB, color = P.ink)
                    Text("${money(Road.guaranteed(r.deal, r.dealBase))} guaranteed this season" +
                        (if (r.deal == NilDeal.PERFORMANCE) " · bonus so far ${money(Road.bonus(lg, h))}" else ""), style = T.small, color = P.sub)
                } else Text("No deal signed for ${lg.season} yet.", style = T.body, color = P.sub)
                Spacer(Modifier.height(6.dp))
                Text("Career NIL earned: ${money(r.earned)}", style = T.small, color = P.good)
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
                Text("${Road.role(lg)} · ${Gameplan.plannedMinutes(t, h.id)} min · ${h.cls}", style = T.small, color = P.accent)
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
            Text("${Road.count(r.followers)} followers · ${Road.personaLabel(r.persona)}", style = T.small, color = P.sub)
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

/** Followers, persona, sponsors and recent press. */
@Composable
private fun MediaPanel(lg: League) {
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
        Text("Every follower adds \$1 a year to your NIL value: ${money(r.followers / 1000)} right now.", style = T.small, color = P.good)
        Road.nextMilestone(lg)?.let { (need, pay) ->
            Spacer(Modifier.height(8.dp))
            Bar(r.followers / need.toFloat(), Modifier.fillMaxWidth(), height = 6.dp)
            Spacer(Modifier.height(4.dp))
            Text("Next sponsor at ${Road.count(need)} followers: ${money(pay)} for one post.", style = T.small, color = P.sub)
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
private fun CareerStory(lg: League) {
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
    val h = lg.hero
    val r = lg.road
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Road to the League", h?.name ?: "Your career", r?.let { "Since ${it.startSeason} · ${it.schools.joinToString(" → ") { id -> lg.team(id).school }}" }) {
                IconCircle({ vm.push(Route.Settings) }) { Icon(NavIcons.Gear, "Settings", tint = P.sub, modifier = Modifier.size(22.dp)) }
            }
        }
        if (h != null) item { CareerTotals(h) }
        item { CareerStory(lg) }
        if (h != null && h.awards.isNotEmpty()) item {
            Section("Honors")
            GroupList(h.awards.reversed()) { a -> Text(a, style = T.small, color = P.ink, modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) }
        }
        item {
            if (r != null) Panel(Modifier.padding(top = 14.dp)) {
                Text("Career NIL earned: ${money(r.earned)}", style = T.bodyB, color = P.good)
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
    if (r.ended) { RoadEnding(vm, lg); return }
    val h = lg.playerOrNull(lg.heroId) ?: return
    val t = lg.team(h.teamId)
    val steps = listOf("Decision", "Draft Night", "Summer", "Next season")
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
    if (r.portal) item {
        Section("Transfer offers")
        if (r.offers.isEmpty()) Panel { Text("No offers came in. You'll stay at ${t.school}.", style = T.body, color = P.sub) }
        GroupList(r.offers, inset = 64.dp) { o ->
            OfferRow(lg, o, h, false) { vm.act { Road.transfer(it, o.teamId) }; vm.toast("Welcome to ${lg.team(o.teamId).school}") }
        }
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
                Text("${Road.count(r.followers)} followers · ${Road.personaLabel(r.persona)}", style = T.small, color = P.ink)
                Text("Schools: ${r.schools.joinToString(" → ") { lg.team(it).school }}", style = T.small, color = P.sub)
            }
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = Gutter)) {
                PrimaryButton("Start a new career", Modifier.fillMaxWidth()) { vm.abandonCareer() }
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

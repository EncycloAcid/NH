package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.HighSchool
import com.nh.hoopscoach.game.HsLine
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Road
import com.nh.hoopscoach.game.SchoolOffer
import com.nh.hoopscoach.game.SeniorYear

/** Senior year on the new-career screen: the summer circuit, the senior season, official visits, decision day. */
@Composable
fun SeniorYearFlow(vm: GameViewModel, lg: League, hero: Player, hs: HighSchool, header: @Composable () -> Unit) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val S = SeniorYear
    var visiting by remember { mutableStateOf<Int?>(null) }
    var hat by remember(hs) { mutableIntStateOf(-1) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f)) {
            item { header() }
            item { ProspectHeader(lg, hero, hs, vm.roadOffers.size) }
            if (hs.step > S.INTRO) item { Stepper(hs) }
            hs.lastResult?.let { r -> item { ResultCard(r) } }
            when {
                hs.step == S.INTRO -> item { IntroCard(vm, hs) }
                hs.step in S.CIRCUIT || hs.step in S.SEASON -> item {
                    S.prompt(lg, hero, hs)?.let { p ->
                        Section(if (hs.step in S.CIRCUIT) "The summer circuit" else "Senior season · ${hs.school}")
                        Panel {
                            Text(p.kicker, style = T.label.copy(fontSize = 10.sp), color = P.accent)
                            Text(p.title, style = T.title.copy(fontSize = 22.sp), color = P.ink, modifier = Modifier.padding(top = 4.dp))
                            Text(p.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp))
                            p.choices.forEachIndexed { i, ch ->
                                ListRow(onClick = {
                                    vm.roadOffers = S.answer(lg, hero, hs, i, vm.roadOffers)
                                    vm.seniorChanged()
                                    if (hs.lastResult?.let { it.starsTo > it.starsFrom } == true) vm.celebrate("${hero.stars}-STAR", "${hero.name} moves up to No. ${hero.recruitRank}")
                                    if (hs.stateTitle && hs.step == S.VISITS) vm.celebrate("STATE CHAMPIONS", hs.school)
                                }) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ch.text, style = T.bodyB, color = P.ink)
                                        Text(ch.hint, style = T.small.copy(fontSize = 11.sp), color = P.faint)
                                    }
                                    Text("›", style = T.title, color = P.faint)
                                }
                            }
                        }
                    }
                }
                hs.step == S.VISITS -> item { Visits(vm, lg, hero, hs) { visiting = it } }
                else -> item { DecisionDay(vm, lg, hs, hat) { hat = it } }
            }
            if (hs.step > S.INTRO && hs.log.size > 1) item { YearSoFar(hs) }
            item { Spacer(Modifier.height(16.dp)) }
        }
        if (hs.step == S.VISITS || hs.step >= S.CEREMONY) {
            Column(Modifier.fillMaxWidth().background(P.bg).navigationBarsPadding().padding(horizontal = Gutter, vertical = 10.dp)) {
                if (hs.step == S.VISITS) {
                    PrimaryButton(if (hs.visits.isEmpty()) "Skip the visits · decision day" else "On to decision day", Modifier.fillMaxWidth()) {
                        hs.finalists.clear(); hs.finalists += S.suggestFinalists(lg, hs, vm.roadOffers)
                        hs.lastResult = null
                        hs.step = S.CEREMONY
                        vm.seniorChanged()
                    }
                } else {
                    val t = lg.teams.getOrNull(hat)?.takeIf { hat in hs.finalists }
                    PrimaryButton(if (t == null) "Pick a hat" else "Put on the ${t.school} hat", Modifier.fillMaxWidth(), enabled = t != null) {
                        if (t != null) {
                            vm.celebrate("COMMITTED", "${hero.name} · ${t.school}", Color(t.primary))
                            vm.startRoad(t.id)
                        }
                    }
                }
            }
        }
    }
    visiting?.let { id ->
        val t = lg.team(id)
        AlertDialog(
            onDismissRequest = { visiting = null }, containerColor = P.surface,
            title = { Text("Official visit: ${t.school}", style = T.head, color = P.ink) },
            text = {
                Column {
                    Text("Two days on campus. What do you want to see most?", style = T.body, color = P.sub, modifier = Modifier.padding(bottom = 6.dp))
                    SeniorYear.ACTIVITIES.forEachIndexed { k, a ->
                        ListRow(onClick = {
                            visiting = null
                            vm.roadOffers = SeniorYear.visit(lg, hero, hs, id, k, vm.roadOffers)
                            vm.seniorChanged()
                        }) {
                            Column(Modifier.weight(1f)) {
                                Text(a.text, style = T.bodyB, color = P.ink)
                                Text(a.hint, style = T.small.copy(fontSize = 11.sp), color = P.faint)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton({ visiting = null }) { Text("Not now", color = P.sub) } },
        )
    }
}

/** Who he is right now: ranking, stars, overall, offers, following. */
@Composable
private fun ProspectHeader(lg: League, hero: Player, hs: HighSchool, offers: Int) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Jersey(hero.number, P.accent, P.surface, 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(hero.name, style = T.head, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${hero.posLabel} · ${hs.school} ${hs.mascot}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${"★".repeat(hero.stars)} · No. ${hero.recruitRank} nationally" + if (hero.recruitRank < hs.rankStart) " (was ${hs.rankStart})" else "",
                    style = T.small, color = P.accent)
            }
            Ring(hero.ovr, 48.dp, label = "OVR", textSize = 17.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Offers", "$offers", Modifier.weight(1f), style = T.numSm)
            Stat("Followers", followersLabel(Road.startFollowers(hero) + hs.buzz), Modifier.weight(1f), style = T.numSm)
            Stat("Record", if (hs.gp > 0) "${hs.w}-${hs.l}" else "—", Modifier.weight(1f), style = T.numSm)
            Stat("PPG", if (hs.gp > 0) "%.1f".format(hs.ppg) else "—", Modifier.weight(1f), style = T.numSm)
        }
    }
}

private fun followersLabel(n: Int) = when { n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0); n >= 1000 -> "${n / 1000}K"; else -> "$n" }

/** Summer → senior season → visits → decision day. */
@Composable
private fun Stepper(hs: HighSchool) {
    val at = SeniorYear.stage(hs)
    Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("Summer", "Senior season", "Visits", "Decision").forEachIndexed { i, label ->
            Column(Modifier.weight(1f)) {
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(when {
                    i < at -> P.accent
                    i == at -> P.accent.copy(alpha = 0.55f)
                    else -> P.surface2
                }))
                Text(label, style = T.label.copy(fontSize = 9.sp), color = if (i == at) P.accent else P.sub, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

/** What just happened: the story, the stat line, the ranking, new offers. */
@Composable
private fun ResultCard(r: HsLine) {
    val tint = if (r.good) P.good else P.bad
    Panel(Modifier.padding(bottom = 4.dp), color = mix(P.surface, tint, if (P.dark) 0.12f else 0.07f)) {
        Text(r.title, style = T.bodyB, color = P.ink)
        Text(r.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 4.dp))
        if (r.pts >= 0) {
            Spacer(Modifier.height(8.dp))
            StatRow(listOf(Triple("PTS", "${r.pts}", null), Triple("REB", "${r.reb}", null), Triple("AST", "${r.ast}", null)), style = T.numSm)
        }
        if (r.rankFrom > 0 && r.rankTo != r.rankFrom) {
            val up = r.rankTo < r.rankFrom
            Text((if (up) "▲ " else "▼ ") + "No. ${r.rankFrom} → No. ${r.rankTo} nationally", style = T.bodyB.copy(fontSize = 14.sp), color = if (up) P.good else P.bad,
                modifier = Modifier.padding(top = 8.dp))
        }
        if (r.starsTo > r.starsFrom) Text("You earned your ${starOrdinal(r.starsTo)} star.", style = T.bodyB.copy(fontSize = 14.sp), color = P.gold, modifier = Modifier.padding(top = 2.dp))
        else if (r.starsTo in 1 until r.starsFrom) Text("You slipped to ${r.starsTo} stars.", style = T.small, color = P.bad, modifier = Modifier.padding(top = 2.dp))
        if (r.offers.isNotEmpty()) Text("New offers: " + r.offers.take(4).joinToString(", ") + if (r.offers.size > 4) " and ${r.offers.size - 4} more" else "",
            style = T.small, color = P.good, modifier = Modifier.padding(top = 6.dp))
    }
}

private fun starOrdinal(n: Int) = when (n) { 4 -> "fourth"; 5 -> "fifth"; else -> "${n}th" }

@Composable
private fun IntroCard(vm: GameViewModel, hs: HighSchool) {
    Section("Your senior year")
    Panel(color = mix(P.surface, P.accent, if (P.dark) 0.12f else 0.06f)) {
        Text("BEFORE YOU PICK A SCHOOL", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Text("${hs.school} ${hs.mascot}", style = T.title.copy(fontSize = 24.sp), color = P.ink, modifier = Modifier.padding(top = 4.dp))
        Text("One more year. Play the summer circuit with ${hs.travelTeam}, your senior season, official visits, and a commitment ceremony. " +
            "Your ranking, your stars and your offers can all still change.", style = T.body, color = P.ink, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(10.dp))
        listOf("☀️" to "Summer circuit · three stops, three chances to move up", "🏀" to "Senior season · the showcase, the rivalry, the playoffs",
            "✈️" to "Official visits · up to five schools", "🧢" to "Decision day · three hats on the table").forEach { (icon, text) ->
            Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(icon, style = T.body)
                Spacer(Modifier.width(10.dp))
                Text(text, style = T.small, color = P.sub)
            }
        }
        Spacer(Modifier.height(14.dp))
        PrimaryButton("Play your senior year", Modifier.fillMaxWidth()) { hs.step = 1; vm.seniorChanged() }
        Spacer(Modifier.height(8.dp))
        SecondaryButton("Skip to the offers", Modifier.fillMaxWidth()) { hs.skipped = true; vm.seniorChanged() }
        Text("Change your player", style = T.bodyB.copy(fontSize = 14.sp), color = P.accent, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable { vm.roadBack() }.padding(top = 12.dp))
    }
}

private fun sortOffers(lg: League, offers: List<SchoolOffer>, hs: HighSchool): List<SchoolOffer> {
    val dad = com.nh.hoopscoach.game.LegacyMode.father(lg)?.school
    return offers.sortedWith(compareByDescending<SchoolOffer> { lg.team(it.teamId).school == dad }
        .thenByDescending { it.teamId in hs.visits }.thenByDescending { lg.team(it.teamId).prestige })
}

private fun closeness(c: Int) = when (c) { 2 -> " · In-state"; 1 -> " · Same region"; else -> "" }

@Composable
private fun OfferLine(lg: League, o: SchoolOffer, hero: Player, hs: HighSchool, selected: Boolean = false, onClick: (() -> Unit)? = null, trailing: @Composable () -> Unit) {
    val t = lg.team(o.teamId)
    ListRow(onClick = onClick, modifier = if (selected) Modifier.background(P.accentSoft) else Modifier) {
        TeamBadge(t, 38.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.school, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (com.nh.hoopscoach.game.LegacyMode.father(lg)?.school == t.school) { Spacer(Modifier.width(6.dp)); Tag("DAD'S SCHOOL", P.gold, filled = true) }
            }
            Text("${lg.conf(t.confId).abbr} · ${prestigeLabel(t.prestige)}${closeness(o.closeness)} · ${money(o.nil)} NIL", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val note = hs.visitNotes[o.teamId]
            Text(note ?: "${o.role} · ${if (o.ahead == 0) "nobody" else "${o.ahead}"} ahead at ${hero.pos}", style = T.small,
                color = if (note != null) P.accent else if (o.ahead == 0) P.good else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

@Composable
private fun Visits(vm: GameViewModel, lg: League, hero: Player, hs: HighSchool, onVisit: (Int) -> Unit) {
    var shown by remember { mutableIntStateOf(12) }
    var query by remember { mutableStateOf("") }
    Section("Official visits · ${hs.visits.size} of ${SeniorYear.MAX_VISITS}")
    Text("Five official visits, your pick. Each one raises the school's interest, shows you how you'd fit, and gets the coaches' best pitch.",
        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 4.dp))
    OutlinedTextField(query, { query = it; shown = 12 }, placeholder = { Text("Search a school", color = P.faint) }, singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), colors = fieldColors())
    val q = query.trim().lowercase()
    val list = sortOffers(lg, vm.roadOffers, hs).filter { q.isEmpty() || lg.team(it.teamId).school.lowercase().contains(q) }
    GroupList(list.take(shown), inset = 62.dp) { o ->
        OfferLine(lg, o, hero, hs) {
            if (o.teamId in hs.visits) Tag("VISITED · ${SeniorYear.grade(SeniorYear.fit(lg, o))}", P.accent, filled = true)
            else SmallButton("Visit", enabled = hs.visits.size < SeniorYear.MAX_VISITS) { onVisit(o.teamId) }
        }
    }
    if (list.size > shown) Box(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        SecondaryButton("Show more · ${list.size - shown} more", Modifier.fillMaxWidth()) { shown += 12 }
    }
}

@Composable
private fun DecisionDay(vm: GameViewModel, lg: League, hs: HighSchool, hat: Int, onHat: (Int) -> Unit) {
    var shown by remember { mutableIntStateOf(10) }
    val hero = vm.roadHero ?: return
    Section("Decision day")
    Panel(color = mix(P.surface, P.gold, if (P.dark) 0.10f else 0.06f)) {
        Text("THE COMMITMENT CEREMONY", style = T.label.copy(fontSize = 10.sp), color = P.gold)
        Text("Pick up to three finalists. Their hats go on the table.", style = T.body, color = P.ink, modifier = Modifier.padding(top = 4.dp, bottom = 10.dp))
        Segmented(SeniorYear.SETTINGS, hs.setting, { hs.setting = it; vm.seniorChanged() }, inset = false)
        Text(SeniorYear.settingText(hs.setting), style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
        if (hs.finalists.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("THE HATS · TAP ONE", style = T.label.copy(fontSize = 10.sp), color = P.sub)
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                for (id in hs.finalists) {
                    val t = lg.team(id)
                    val on = hat == id
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { onHat(id) }.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.size(72.dp).clip(CircleShape).border(if (on) 3.dp else 1.dp, if (on) Color(t.primary) else P.surface2, CircleShape).padding(6.dp),
                            contentAlignment = Alignment.Center) { TeamBadge(t, 58.dp) }
                        Text(t.school, style = T.small.copy(fontSize = 12.sp), color = if (on) P.ink else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
    }
    Section("Your finalists · ${hs.finalists.size} of 3")
    val list = sortOffers(lg, vm.roadOffers, hs).sortedByDescending { it.teamId in hs.finalists }
    GroupList(list.take(shown), inset = 62.dp) { o ->
        val on = o.teamId in hs.finalists
        OfferLine(lg, o, hero, hs, selected = on, onClick = {
            if (on) { hs.finalists.remove(o.teamId); if (hat == o.teamId) onHat(-1) }
            else if (hs.finalists.size < 3) hs.finalists += o.teamId
            else vm.toast("Three hats on the table at most. Take one off first.")
            vm.seniorChanged()
        }) {
            Text(if (on) "✓" else "+", style = T.title.copy(fontSize = 20.sp), color = if (on) P.accent else P.faint)
        }
    }
    if (list.size > shown) Box(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
        SecondaryButton("Show more · ${list.size - shown} more", Modifier.fillMaxWidth()) { shown += 10 }
    }
}

/** The year so far, newest first. */
@Composable
private fun YearSoFar(hs: HighSchool) {
    Section("Your year so far")
    Panel(padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        hs.log.reversed().drop(if (hs.lastResult != null) 1 else 0).forEachIndexed { i, l ->
            if (i > 0) Hairline(Modifier.padding(start = 14.dp))
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (l.good) P.good else P.bad))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.title, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (l.pts >= 0) Text("${l.pts} pts · ${l.reb} reb · ${l.ast} ast" + if (l.rankFrom > 0 && l.rankTo != l.rankFrom) " · No. ${l.rankTo}" else "",
                        style = T.small, color = P.sub)
                }
            }
        }
    }
}

/** On the career tab: his senior year. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HighSchoolPanel(lg: League) {
    val hs = lg.road?.hs ?: return
    Section("High school")
    Panel {
        Text("${hs.school.uppercase()} ${hs.mascot.uppercase()} · SENIOR YEAR", style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Text("${hs.w}-${hs.l}" + if (hs.stateResult.isNotEmpty()) " · ${hs.stateResult}" else "", style = T.title.copy(fontSize = 20.sp), color = P.ink, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.height(8.dp))
        StatRow(listOf(Triple("PPG", "%.1f".format(hs.ppg), null), Triple("RPG", "%.1f".format(hs.rpg), null), Triple("APG", "%.1f".format(hs.apg), null)), style = T.numSm)
        if (hs.honors.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hs.honors.forEach { Tag(it.uppercase(), P.gold) }
            }
        }
        val t = lg.teams.getOrNull(hs.committedTo)
        if (t != null) {
            val others = hs.finalists.filter { it != t.id }.map { lg.team(it).school }
            val how = when (hs.setting) { 0 -> "live on TV"; 1 -> "in his school gym"; else -> "on social media" }
            Text("Committed to ${t.school}" + (if (others.isNotEmpty()) " over ${others.joinToString(" and ")}" else "") + ", $how. " +
                (if (hs.rankEnd > 0 && hs.rankEnd != hs.rankStart) "No. ${hs.rankStart} in the class a year earlier, No. ${hs.rankEnd} when he signed." else "No. ${hs.rankStart} in the class."),
                style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
        }
        if (hs.visits.isNotEmpty()) Text("Visited " + hs.visits.joinToString(", ") { lg.team(it).school } + ".", style = T.small, color = P.faint, modifier = Modifier.padding(top = 2.dp))
    }
}

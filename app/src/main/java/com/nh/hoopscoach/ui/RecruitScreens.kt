package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.nh.hoopscoach.game.Ollama
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Brand
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Recruit
import com.nh.hoopscoach.game.Recruiting
import kotlin.math.roundToInt


@Composable
fun RecruitScreen(vm: GameViewModel, lg: League) {
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            val cycle = Recruiting.inCycle(lg)
            val off = lg.phase == Phase.OFFSEASON
            ScreenHeader(
                if (cycle) "Round ${Recruiting.round(lg)} of ${Recruiting.ROUNDS}" else if (off) "Offseason" else "Class of ${lg.season + 1}",
                "Recruiting",
                when {
                    cycle -> "Set your points, preview your position, then lock in on Today."
                    off -> "The five recruiting rounds start after the NIL budget step."
                    else -> "Scouting season. Recruiting runs in five rounds after the season."
                },
            ) { GuideButton { vm.openGuide(Guide.RECRUITING) } }
        }
        recruitBoard(vm, lg)
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** Shared by the Recruit tab and the offseason recruiting step. */
fun LazyListScope.recruitBoard(vm: GameViewModel, lg: League) {
    val u = lg.user
    if (lg.recruits.isEmpty()) {
        item { Empty("The next recruiting class opens when the new season starts.") }
        return
    }
    val cycle = Recruiting.inCycle(lg)
    val open = Recruiting.openSpots(lg, u)
    val commits = lg.recruits.filter { it.committed == u.id }
    item {
        val pull = remember(vm.rev, lg) { Brand.pull(lg, u) }
        Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
            Row(Modifier.fillMaxWidth()) {
                if (cycle) {
                    val avail = Recruiting.available(lg)
                    Stat("Available", "$avail", Modifier.weight(1f), if (avail > 0) P.accent else P.sub)
                    Stat("Budget", "${lg.recruitBudget}", Modifier.weight(1f))
                } else {
                    Stat("Scouting", "${lg.recruitPoints}", Modifier.weight(1f), P.accent)
                    Stat("Rec. Pull", "$pull", Modifier.weight(1f))
                }
                Stat("Open spots", "$open", Modifier.weight(1f), if (open > 0) P.good else P.bad)
                Stat("Commits", "${commits.size}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                if (cycle) "Recruiting Pull $pull" + Recruiting.classRank(lg, u.id).let { if (it > 0) " · class ranked No. $it" else "" } + ". Points stay on a prospect until you move them or he commits somewhere."
                else "Scouting clears the fog in three looks — film, a campus visit, then a full report. Each costs scouting points and narrows his OVR/POT range. Add the ones you like to your targets.",
                style = T.small, color = P.sub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            )
        }
        if (cycle) {
            Spacer(Modifier.height(10.dp))
            RoundReportPanel(vm, lg)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = Gutter)) {
                SecondaryButton("Preview my position", Modifier.fillMaxWidth()) { vm.push(Route.RecruitPreview) }
            }
        }
        Spacer(Modifier.height(12.dp))
        Segmented(listOf("Targets", "Prospects", "Transfers", "Commits"), vm.recruitView, { vm.recruitView = it })
        Spacer(Modifier.height(10.dp))
        val opts = listOf("All") + Pos.entries.map { pos ->
            val n = u.roster.count { lg.player(it).pos == pos && Recruiting.stays(lg, lg.player(it)) } + lg.recruits.count { it.committed == u.id && it.p.pos == pos }
            "${pos.name} · $n"
        }
        Chips(opts, vm.recruitPos?.let { it.ordinal + 1 } ?: 0, { i -> vm.recruitPos = if (i == 0) null else Pos.entries[i - 1] })
        Spacer(Modifier.height(10.dp))
    }
    val list = when (vm.recruitView) {
        0 -> lg.recruits.filter { it.committed < 0 && (it.onBoard || u.id in it.alloc) }
        1 -> lg.recruits.filter { it.committed < 0 && !it.transfer }
        2 -> lg.recruits.filter { it.committed < 0 && it.transfer }
        else -> commits
    }.filter { vm.recruitPos == null || it.p.pos == vm.recruitPos }
        .sortedWith(compareByDescending<Recruit> { Recruiting.mine(lg, it) }.thenByDescending { if (it.transfer) it.p.ovr + 8 else it.estOvr + it.p.stars * 3 })
    if (list.isEmpty()) item {
        Empty(when (vm.recruitView) {
            0 -> if (cycle) "No targets yet. Open a prospect and put points on him." else "No targets yet. Open a prospect and add him to your targets."
            2 -> if (lg.phase == Phase.OFFSEASON) "No transfers match this filter." else "The transfer portal opens in the offseason."
            3 -> "No commitments yet."
            else -> "No prospects match this filter."
        })
    }
    item {
        val ctx = remember(vm.rev, lg) { Recruiting.Ctx(lg) }
        GroupList(list.take(120), inset = 70.dp) { r -> RecruitRow(vm, lg, r, ctx) }
    }
}

@Composable
private fun RoundReportPanel(vm: GameViewModel, lg: League) {
    val rep = lg.offseason?.report ?: return
    Panel {
        Text("ROUND ${rep.round} RESULTS", style = T.label, color = P.accent)
        Spacer(Modifier.height(6.dp))
        val names = rep.mine.mapNotNull { id -> lg.playerOrNull(id)?.name ?: lg.recruits.firstOrNull { it.p.id == id }?.p?.name }
        Text(
            if (names.isEmpty()) "No commitments for you this round." else "Committed to you: ${names.joinToString(", ")}.",
            style = T.bodyB, color = if (names.isEmpty()) P.ink else P.good,
        )
        val lost = rep.lost.mapNotNull { id -> lg.recruits.firstOrNull { it.p.id == id } }
        if (lost.isNotEmpty()) Text(
            "Chose elsewhere: " + lost.joinToString(", ") { "${it.p.name} (${lg.team(it.committed).abbr})" } + ".",
            style = T.small, color = P.bad,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "On your board: ${rep.open} still open, ${rep.dropped} school${if (rep.dropped == 1) "" else "s"} dropped out, ${rep.entered} new school${if (rep.entered == 1) "" else "s"} entered. ${rep.commits} commitments nationwide.",
            style = T.small, color = P.sub,
        )
    }
    @Suppress("UNUSED_VARIABLE") val unused = vm
}

@Composable
fun RecruitRow(vm: GameViewModel, lg: League, r: Recruit, ctx: Recruiting.Ctx) {
    val u = lg.user
    val cycle = Recruiting.inCycle(lg)
    val mine = r.interest[u.id] ?: 0
    val pts = Recruiting.mine(lg, r)
    val leader = r.interest.entries.filter { it.key != u.id }.maxByOrNull { it.value }
    ListRow(onClick = { vm.push(Route.RecruitView(r.p.id)) }) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(P.surface2), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (r.transfer) Text("TR", style = T.numSm, color = P.accent)
                else {
                    Text("${r.p.stars}★", style = T.numSm.copy(fontSize = 16.sp), color = P.gold)
                    Text("#${r.p.recruitRank}", style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.3.sp), color = P.sub)
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(r.p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val from = if (r.transfer && r.p.transferFrom >= 0) "from ${lg.team(r.p.transferFrom).school}" else r.p.hometown
            Text("${r.p.pos} · ${Ratings.heightLabel(r.p.height)} · $from", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(5.dp))
            when {
                r.committed == u.id -> Text("Committed to you" + if (r.round in 1..Recruiting.ROUNDS) " in round ${r.round}" else "", style = T.small, color = P.good)
                r.committed >= 0 -> Text("Signed with ${lg.team(r.committed).school}", style = T.small, color = P.faint)
                cycle && (pts > 0 || mine > 0) -> {
                    val proj = Recruiting.project(lg, r, ctx)
                    val next = proj[u.id] ?: mine
                    val place = Recruiting.place(proj, u.id)
                    Bar(next / 100f, Modifier.fillMaxWidth(0.92f), color = if (place == 1) P.good else P.accent, height = 5.dp)
                    Spacer(Modifier.height(3.dp))
                    Text(
                        "$pts pts · You $mine → $next (${ordinal(place)})" + (leader?.let { " · ${lg.team(it.key).abbr} ${proj[it.key] ?: it.value}" } ?: ""),
                        style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                else -> Text(
                    (if (leader != null) "Leader ${lg.team(leader.key).abbr} ${leader.value} · ${r.interest.size} schools" else "No schools yet") + " · ${r.pref.label}",
                    style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        val fog = remember(r.scoutLevel, r.scouted, r.estOvr, r.estPot, r.noiseO, r.noiseP) { Recruiting.fog(r) }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(44.dp)) {
            Text(if (fog.known) grade(fog.midOvr) else fog.ovrText(), style = T.numSm.copy(fontSize = if (fog.known) 14.sp else 11.sp), color = tier(fog.midOvr + 4))
            Text(if (fog.known) grade(fog.midPot) else fog.potText(), style = T.small.copy(fontSize = 10.sp), color = P.sub)
        }
    }
}

@Composable
private fun factorColor(v: Double): Color = when {
    v >= 1.08 -> P.good
    v >= 1.02 -> P.accent
    v > 0.97 -> P.sub
    else -> P.bad
}

private fun factorWord(v: Double) = when {
    v >= 1.2 -> "Big plus"
    v >= 1.08 -> "Plus"
    v >= 1.02 -> "Slight plus"
    v > 0.97 -> "Neutral"
    v > 0.85 -> "Minus"
    else -> "Big minus"
}

@Composable
fun RecruitDetail(vm: GameViewModel, lg: League, playerId: Int) {
    val r = lg.recruits.firstOrNull { it.p.id == playerId }
    if (r == null) {
        Column { BackBar("Prospect", onBack = { vm.back() }); Empty("This prospect has already signed.") }
        return
    }
    val u = lg.user
    val p = r.p
    val committed = r.committed >= 0
    val cycle = Recruiting.inCycle(lg)
    val ctx = remember(vm.rev, lg) { Recruiting.Ctx(lg) }
    val proj = if (cycle && !committed) Recruiting.project(lg, r, ctx) else r.interest
    var pitchLetter by remember { mutableStateOf<com.nh.hoopscoach.game.AiText?>(null) }
    var draftingPitch by remember { mutableStateOf(false) }
    var pitchSent by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar(p.name, if (r.transfer) "Transfer · ${p.pos} · ${p.cls}" else "${p.stars}-star ${p.pos} · No. ${p.recruitRank} nationally · No. ${p.posRank} ${p.pos}", onBack = { vm.back() })
            val fog = remember(r.scoutLevel, r.scouted, r.estOvr, r.estPot, r.noiseO, r.noiseP, p.ovr, p.pot) { Recruiting.fog(r) }
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (!r.transfer) Stars(p.stars, 18.sp)
                        Text("${Ratings.heightLabel(p.height)} · ${p.weight} lbs", style = T.bodyB, color = P.ink)
                        Text(if (r.transfer && p.transferFrom >= 0) "Leaving ${lg.team(p.transferFrom).school}" else "${p.hometown} · ${p.highSchool}", style = T.small, color = P.sub)
                        if (p.traits.isNotEmpty()) Text(p.traits.joinToString(" · ") { it.label }, style = T.small, color = P.sub)
                    }
                    Ring(fog.midOvr, 52.dp, label = if (fog.known) "OVR" else "EST", textSize = 18.sp)
                    Spacer(Modifier.width(8.dp))
                    Ring(fog.midPot, 52.dp, label = "POT", textSize = 18.sp)
                }
                if (!fog.known) {
                    Spacer(Modifier.height(8.dp))
                    Text("Scout range · OVR ${fog.ovrText()} · POT ${fog.potText()}", style = T.small, color = P.accent)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    Stat("Style", p.style.short, Modifier.weight(1f), style = T.numSm)
                    Stat("NIL ask", money(r.nilAsk), Modifier.weight(1f), style = T.numSm)
                    Stat("Scout", fog.label, Modifier.weight(1f), if (fog.known) P.good else P.sub, T.numSm)
                }
                if (p.buildName.isNotEmpty() || p.likeA.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    if (p.buildName.isNotEmpty()) Text(p.buildName, style = T.bodyB, color = P.ink)
                    if (p.likeA.isNotEmpty()) {
                        val likes = listOf(p.likeA, p.likeB).filter { it.isNotEmpty() }.joinToString(" · ")
                        Text("Closest to: $likes", style = T.small, color = P.accent)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag("WANTS", P.accent)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(r.pref.label, style = T.bodyB, color = P.ink)
                        Text(r.pref.blurb, style = T.small, color = P.sub)
                    }
                }

                if (lg.ollama.enabled && lg.ollama.recruitingPitches && !lg.pro) {
                    Spacer(Modifier.height(14.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(P.line))
                    Spacer(Modifier.height(10.dp))

                    Column(
                        Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (P.dark) Color(0xFF1E262A) else Color(0xFFF0FDF4))
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("COACH'S RECRUITING PITCH", style = T.label, color = P.good, modifier = Modifier.weight(1f))
                                    AiBadge(pitchLetter?.isAi == true, pitchLetter?.provider.orEmpty())
                                }
                                Text("Personalized pitch letter targeting ${r.pref.label}.", style = T.small, color = P.sub)
                            }
                            if (pitchLetter == null) {
                                SmallButton(if (draftingPitch) "Drafting..." else "Draft Letter ✍️", primary = true, enabled = !draftingPitch) {
                                    draftingPitch = true
                                    scope.launch {
                                        val res = Ollama.generateRecruitPitchLetter(lg.ollama, lg, r)
                                        draftingPitch = false
                                        res.onSuccess { text ->
                                            pitchLetter = text
                                        }.onFailure { err ->
                                            vm.toast("AI: ${err.message ?: "Failed"}")
                                        }
                                    }
                                }
                            }
                        }
                        if (pitchLetter != null) {
                            Spacer(Modifier.height(10.dp))
                            Column(
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (P.dark) Color(0xFF181F22) else Color(0xFFFFFFFF))
                                    .padding(12.dp)
                            ) {
                                for (pPara in pitchLetter!!.text.split("\n\n")) {
                                    Text(pPara.trim(), style = T.body, color = P.ink)
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (!pitchSent && !committed) {
                                    SmallButton("Send to ${p.name} (+Interest)", primary = true) {
                                        vm.act {
                                            val cur = r.interest[u.id] ?: 0
                                            r.interest[u.id] = (cur + 5).coerceAtMost(100)
                                        }
                                        pitchSent = true
                                        vm.toast("Pitch letter sent to ${p.name}! +5 Interest gained.")
                                    }
                                } else if (pitchSent) {
                                    Tag("✓ LETTER SENT (+5 INTEREST)", P.good)
                                }
                                SmallButton(if (draftingPitch) "Re-drafting..." else "Re-draft", enabled = !draftingPitch) {
                                    draftingPitch = true
                                    scope.launch {
                                        val res = Ollama.generateRecruitPitchLetter(lg.ollama, lg, r)
                                        draftingPitch = false
                                        res.onSuccess { text ->
                                            pitchLetter = text
                                        }.onFailure { err ->
                                            vm.toast("AI: ${err.message ?: "Failed"}")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            if (committed) {
                Panel(Modifier.padding(top = 12.dp)) {
                    Text(
                        if (r.committed == u.id) "Committed to you" else "Committed to ${lg.team(r.committed).school}",
                        style = T.bodyB, color = if (r.committed == u.id) P.good else P.ink,
                    )
                }
            } else if (cycle) {
                PointsPanel(vm, lg, r, proj)
            } else {
                Section("Scouting · ${lg.recruitPoints} points")
                Panel {
                    val fogNow = Recruiting.fog(r)
                    val cost = Recruiting.scoutCost(lg, r)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallButton(
                            if (fogNow.known) "Fully scouted" else "${fogNow.nextAction} · $cost",
                            Modifier.weight(1f),
                            enabled = !fogNow.known,
                            primary = !fogNow.known,
                        ) {
                            vm.act {
                                if (!Recruiting.scout(it, r)) vm.toast("Not enough scouting points")
                                else vm.toast(Recruiting.fog(r).label)
                            }
                        }
                        if (r.onBoard) SmallButton("Remove target", Modifier.weight(1f)) { vm.act { Recruiting.removeFromBoard(it, r) } }
                        else SmallButton("Add to targets", Modifier.weight(1f), primary = fogNow.known) { vm.act { Recruiting.addToBoard(it, r) } }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        when {
                            fogNow.known -> "You know his real ratings. " + if (lg.phase == Phase.OFFSEASON) "Put recruiting points on him once the five rounds begin." else "Add him to your board before the offseason."
                            fogNow.level == 0 -> "Watch film to shrink a wide OVR/POT band. Campus visits and a full report clear the rest."
                            fogNow.level == 1 -> "Film is in. A campus visit tightens the range further."
                            else -> "One more look — the full report — reveals his true overall and ceiling."
                        },
                        style = T.small, color = P.sub,
                    )
                }
            }
        }
        if (!committed) item {
            val room = Recruiting.nilMax(lg, r)
            val step = Recruiting.nilStep(r)
            val gain = Recruiting.nilGain(r, r.nilOffer).roundToInt()
            val pct = if (r.nilAsk <= 0) 100 else (r.nilOffer * 100.0 / r.nilAsk).roundToInt()
            val set: (Int) -> Unit = { v -> vm.act { if (!Recruiting.setNil(it, r, v)) vm.toast("Not enough NIL room. Offer set to the most you can afford.") } }
            Section("NIL offer · ${money((room - r.nilOffer).coerceAtLeast(0))} room left")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(money(r.nilOffer), style = T.num, color = if (r.nilOffer > 0) P.ink else P.sub)
                        Text(if (r.nilOffer > 0) "$pct% of his ${money(r.nilAsk)} ask" else "He asks for ${money(r.nilAsk)} a year", style = T.small, color = P.sub)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(if (gain > 0) "+$gain" else "+0", style = T.num, color = if (gain > 0) P.good else P.faint)
                        Text("INTEREST A ROUND", style = T.label.copy(fontSize = 9.sp), color = P.sub)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("−${money(step)}", Modifier.weight(1f), enabled = r.nilOffer > 0) { set((r.nilOffer - step).coerceAtLeast(0)) }
                    SmallButton("+${money(step)}", Modifier.weight(1f), primary = true, enabled = room > r.nilOffer) { set(r.nilOffer + step) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SmallButton("Clear", Modifier.weight(1f), enabled = r.nilOffer > 0) { set(0) }
                    SmallButton("Match ask", Modifier.weight(1f), enabled = r.nilOffer != r.nilAsk) { set(r.nilAsk) }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    (if (Recruiting.inCycle(lg)) "" else "Counts once the recruiting rounds start. ") +
                        "An NIL offer adds interest every round on its own, even with no points on him. Matching his ask adds about +8; double the ask about +13" +
                        (if (r.pref == com.nh.hoopscoach.game.RecruitPref.NIL || r.p.has(com.nh.hoopscoach.game.Trait.BAG_CHASER)) ", and he cares about NIL, so more." else ".") +
                        " Rival programs offer NIL too, the richest the most.",
                    style = T.small, color = P.sub,
                )
            }
        }
        item {
            val fs = Recruiting.factors(lg, u, r, ctx)
            val fit = fs.fold(1.0) { a, f -> if (f.multiplier) a * f.value else a }
            Section("Why he'd pick ${u.school}")
            GroupList(fs) { f ->
                ListRow {
                    Column(Modifier.weight(1f)) {
                        Text(f.label, style = T.bodyB, color = P.ink)
                        Text(f.note, style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.width(8.dp))
                    Tag(factorWord(f.value).uppercase(), factorColor(f.value))
                }
            }
            Text(
                "Overall fit ×%.2f: every point you put on him is worth %d%% of a neutral school's.".format(fit, (fit * 100).roundToInt()),
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )
        }
        item {
            Section(if (cycle && !committed) "The race after this round" else "Who's in the race")
            val ranked = proj.entries.sortedByDescending { it.value }.take(8)
            if (ranked.isEmpty()) Empty("No schools are in on him yet.")
            GroupList(ranked) { (tid, v) ->
                val t = lg.team(tid)
                val now = r.interest[tid] ?: 0
                ListRow(onClick = { vm.push(Route.TeamPage(tid)) }) {
                    TeamBadge(t, 30.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(t.school, style = T.bodyB, color = if (tid == u.id) P.accent else P.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            if (cycle && !committed) {
                                val pts = r.alloc[tid] ?: 0
                                Text(if (tid == u.id) "$pts pts" else Recruiting.effortLabel(pts), style = T.small, color = if (pts > 0) P.sub else P.faint)
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Bar(v / 100f, Modifier.fillMaxWidth(), color = if (tid == u.id) P.accent else P.faint, height = 5.dp)
                    }
                    Text(if (cycle && !committed && v != now) "$now→$v" else "$v", style = T.numSm, color = P.ink, modifier = Modifier.width(64.dp), textAlign = TextAlign.End)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PointsPanel(vm: GameViewModel, lg: League, r: Recruit, proj: Map<Int, Int>) {
    val u = lg.user
    val pts = Recruiting.mine(lg, r)
    val avail = Recruiting.available(lg)
    val full = Recruiting.openSpots(lg, u) <= 0
    Section("Your points · $avail available")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$pts", style = T.hero.copy(fontSize = 40.sp, lineHeight = 42.sp), color = P.accent)
                Text("POINTS ON HIM", style = T.label, color = P.sub)
            }
            val next = proj[u.id]
            Column(horizontalAlignment = Alignment.End) {
                if (next != null) {
                    val place = Recruiting.place(proj, u.id)
                    Text("${ordinal(place)} of ${proj.size}", style = T.head, color = if (place == 1) P.good else P.ink)
                    Text("Interest ${r.interest[u.id] ?: 0} → $next", style = T.small, color = P.sub)
                } else Text("Not in the race yet", style = T.small, color = P.sub)
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (d in listOf(-25, -5, 5, 25)) {
                SmallButton(if (d > 0) "+$d" else "−${-d}", Modifier.weight(1f), primary = d > 0, enabled = if (d > 0) !full || pts > 0 else pts > 0) {
                    vm.act { if (!Recruiting.setPoints(it, r, (pts + d).coerceAtLeast(0))) vm.toast(if (full && pts == 0) "No open scholarships" else "Not enough points") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Clear", Modifier.weight(1f), enabled = pts > 0) { vm.act { Recruiting.setPoints(it, r, 0) } }
            SmallButton("+100", Modifier.weight(1f), enabled = !full || pts > 0) {
                vm.act { if (!Recruiting.setPoints(it, r, pts + minOf(100, Recruiting.available(it)))) vm.toast("Not enough points") }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            if (full && pts == 0) "All your scholarships are spoken for, so no new prospect can commit to you."
            else "Rivals have already made this round's moves. Points have diminishing returns: 100 points add twice what 25 do, not four times.",
            style = T.small, color = if (full && pts == 0) P.warn else P.sub,
        )
    }
}

/** Every target, where he stands now and where he'd stand if you lock in. */
@Composable
fun RecruitPreviewScreen(vm: GameViewModel, lg: League) {
    val u = lg.user
    val ctx = remember(vm.rev, lg) { Recruiting.Ctx(lg) }
    val rows = lg.recruits.filter { it.committed < 0 && (Recruiting.mine(lg, it) > 0 || it.onBoard || u.id in it.interest) }
        .map { r ->
            val proj = Recruiting.project(lg, r, ctx)
            Triple(r, Recruiting.place(r.interest, u.id), proj)
        }.sortedWith(compareBy<Triple<Recruit, Int, Map<Int, Int>>> { Recruiting.place(it.third, u.id).let { p -> if (p == 0) 99 else p } }.thenByDescending { Recruiting.mine(lg, it.first) })
    val leading = rows.count { Recruiting.place(it.third, u.id) == 1 }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Preview round ${Recruiting.round(lg)}", "Where your targets stand if you lock in now", onBack = { vm.back() })
            Panel(padding = PaddingValues(vertical = 14.dp, horizontal = 8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Stat("Leading", "$leading", Modifier.weight(1f), P.good)
                    Stat("Targets", "${rows.size}", Modifier.weight(1f))
                    Stat("Points used", "${Recruiting.tied(lg)}", Modifier.weight(1f))
                    Stat("Open spots", "${Recruiting.openSpots(lg, u)}", Modifier.weight(1f))
                }
            }
            Text(
                "Interest moves exactly as shown. Whether a prospect commits is decided when you lock in: leaders with high interest and a clear margin usually do, and later rounds produce more commitments.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 10.dp),
            )
            if (rows.isEmpty()) Empty("No targets yet. Put points on prospects from the board.")
            GroupList(rows, inset = 16.dp) { (r, now, proj) ->
                val next = Recruiting.place(proj, u.id)
                val pts = Recruiting.mine(lg, r)
                ListRow(onClick = { vm.push(Route.RecruitView(r.p.id)) }) {
                    Column(Modifier.weight(1f)) {
                        Text(r.p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text((if (r.transfer) "Transfer" else "${r.p.stars}★ #${r.p.recruitRank}") + " · ${r.p.pos} · $pts pts", style = T.small, color = P.sub)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        val moved = when { now == 0 && next > 0 -> P.accent; next in 1 until now -> P.good; next > now -> P.bad; else -> P.ink }
                        Text(
                            (if (now == 0) "–" else ordinal(now)) + " → " + (if (next == 0) "–" else ordinal(next)),
                            style = T.numSm, color = if (next == 1) P.good else moved,
                        )
                        Text("${r.interest[u.id] ?: 0} → ${proj[u.id] ?: 0} of ${proj.values.maxOrNull() ?: 0}", style = T.small, color = P.sub)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

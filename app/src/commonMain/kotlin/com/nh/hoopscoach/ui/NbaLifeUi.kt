package com.nh.hoopscoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.AllStarSaturday
import com.nh.hoopscoach.game.CoachTrust
import com.nh.hoopscoach.game.League

@Composable
private fun trustColor(t: Int): Color = when { t >= 80 -> P.good; t >= 60 -> P.accent; t >= 40 -> P.sub; t >= 20 -> P.warn; else -> P.bad }

// ---------- the coach ----------

/** Me, Team: where you stand with the coach, his ask, and a talk with him (every ten days). */
@Composable
fun CoachPanel(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    if (!CoachTrust.active(lg)) return
    val r = lg.road ?: return
    val h = lg.hero ?: return
    val trust = CoachTrust.trust(lg)
    val coach = lg.coachOf(lg.team(h.teamId)).last
    Section("You and Coach $coach")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TRUST", style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.weight(1f))
            Text(CoachTrust.label(trust).uppercase(), style = T.label.copy(fontSize = 10.sp), color = trustColor(trust))
        }
        Bar(trust / 100f, Modifier.fillMaxWidth().padding(top = 5.dp), color = trustColor(trust), height = 5.dp)
        Text(CoachTrust.meaning(trust, r.rolePromise == lg.season), style = T.small, color = P.sub, modifier = Modifier.padding(top = 5.dp))
        if (r.coachAskLeft > 0) {
            Spacer(Modifier.height(10.dp))
            Hairline()
            Text("HIS ASK · ${5 - r.coachAskLeft} OF 5 GAMES PLAYED · ${r.coachAskHits} DONE", style = T.label.copy(fontSize = 10.sp), color = P.accent, modifier = Modifier.padding(top = 8.dp))
            Text(CoachTrust.askText(lg), style = T.body, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Text("Three of five and he'll trust you more.", style = T.small, color = P.faint)
        }
        Spacer(Modifier.height(10.dp))
        Hairline()
        if (CoachTrust.canTalk(lg)) {
            Text("TALK TO HIM", style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
            fun talk(f: (League) -> String) { var msg = ""; vm.act { msg = f(it) }; if (msg.isNotEmpty()) vm.toast(msg) }
            ChoiceRow(onClick = { talk { CoachTrust.askForRole(it) } }) {
                Column(Modifier.weight(1f)) {
                    Text("Ask for a bigger role", style = T.body, color = P.ink)
                    Text("${(CoachTrust.roleOdds(lg) * 100).toInt()}% he says yes: more minutes all season. A no costs you trust.", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
            if (r.coachAskLeft == 0) ChoiceRow(onClick = { talk { CoachTrust.askWhatHeNeeds(it) } }) {
                Column(Modifier.weight(1f)) {
                    Text("Ask what he needs from you", style = T.body, color = P.ink)
                    Text("He'll give you one thing to show him over five games.", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
            ChoiceRow(onClick = { talk { CoachTrust.buyIn(it) } }) {
                Column(Modifier.weight(1f)) {
                    Text("Tell him you'll do whatever the team needs", style = T.body, color = P.ink)
                    Text("Trust up, teammates happier; your pride takes a small hit.", style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        } else Text("Next talk in ${CoachTrust.daysToTalk(lg)} day${if (CoachTrust.daysToTalk(lg) == 1) "" else "s"}. Let your game do the talking until then.",
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Today: the coach's ask while it's on, or a nudge when you can talk to him and he doesn't trust you yet. */
@Composable
fun CoachTodayCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    if (!CoachTrust.active(lg)) return
    val r = lg.road ?: return
    val trust = CoachTrust.trust(lg)
    val show = r.coachAskLeft > 0 || (CoachTrust.canTalk(lg) && trust < 60)
    if (!show) return
    Spacer(Modifier.height(CardGap))
    Panel(onClick = { vm.meTab = 1; vm.select(Tab.RECRUIT) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (r.coachAskLeft > 0) "COACH'S ASK · ${r.coachAskHits} OF ${5 - r.coachAskLeft} SO FAR" else "COACH'S TRUST · ${CoachTrust.label(trust).uppercase()}",
                    style = T.label.copy(fontSize = 10.sp), color = trustColor(trust))
                Text(if (r.coachAskLeft > 0) CoachTrust.askText(lg) else "He hasn't made up his mind about you. Go talk to him.", style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(8.dp))
            SmallButton(if (r.coachAskLeft > 0) "Details" else "Talk") { vm.meTab = 1; vm.select(Tab.RECRUIT) }
        }
    }
}

// ---------- All-Star Saturday ----------

/** Today, the days before the All-Star break: the contests you're invited to, a tap from playing your round. */
@Composable
fun AllStarInviteCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val inv = AllStarSaturday.invites(lg)
    if (inv.isEmpty()) return
    Section("All-Star Saturday")
    Panel(color = P.hero) {
        Text("YOU'RE INVITED", style = T.label, color = P.onHero.copy(alpha = 0.8f))
        Text(inv.joinToString(" and ") { if (it == "three") "the 3-Point Contest" else "the Dunk Contest" }.replaceFirstChar { it.uppercase() }, style = T.head, color = P.onHero)
        Text("Your round counts when the weekend comes. Skip it and you'll take it as it comes on Saturday.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if ("three" in inv) {
                val s = AllStarSaturday.threeScore(lg)
                SmallButton(if (s >= 0) "3-Point: $s of 34" else "Shoot your round", Modifier.weight(1f), enabled = s < 0) { vm.push(Route.ThreeContest) }
            }
            if ("dunk" in inv) {
                val s = AllStarSaturday.dunkScore(lg)
                SmallButton(if (s >= 0) "Dunks: $s of 100" else "Take your dunks", Modifier.weight(1f), enabled = s < 0) { vm.push(Route.DunkContest) }
            }
        }
    }
}

/** A timing meter: the marker sweeps, the green is the window. Re-made for each attempt ([attempt]). */
@Composable
private fun Meter(attempt: Int, sweepMs: Int, half: Double, onPos: (Float) -> Unit) {
    key(attempt) {
        val anim = rememberInfiniteTransition(label = "meter")
        val pos by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(sweepMs, easing = LinearEasing), RepeatMode.Reverse), label = "pos")
        onPos(pos)
        BoxWithConstraints(Modifier.padding(horizontal = Gutter).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(P.surface2)) {
            val w = maxWidth
            Box(Modifier.offset(x = w * (0.5f - half.toFloat())).width(w * (2 * half).toFloat()).fillMaxSize().background(P.good.copy(alpha = 0.55f)))
            Box(Modifier.offset(x = w * 0.5f - 1.dp).width(2.dp).fillMaxSize().background(P.good))
            Box(Modifier.offset(x = w * pos - 3.dp).width(6.dp).fillMaxSize().background(P.ink))
        }
    }
}

/** The 3-Point Contest: five racks of five, money balls worth two, the last rack all money. Out of 34. */
@Composable
fun ThreeContestScreen(vm: GameViewModel, lg: League) {
    val h = lg.hero ?: run { vm.back(); return }
    var shot by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    val marks = remember { mutableStateOf(listOf<Boolean>()) }
    var last by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(AllStarSaturday.threeScore(lg) >= 0) }
    val cur = remember { floatArrayOf(0f) }
    val half = AllStarSaturday.window(lg, h)
    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("3-Point Contest", if (done) "Your round is in" else "Rack ${(shot / 5 + 1).coerceAtMost(5)} of 5 · ball ${shot % 5 + 1}", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Panel(color = P.hero) {
                    Text("ALL-STAR SATURDAY", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text("${if (done) AllStarSaturday.threeScore(lg) else score} OF 34", style = T.hero, color = P.onHero)
                    Text("Stop the marker in the green. The last ball on each rack is a money ball (2 points), and the fifth rack is all money balls. It speeds up as you go.",
                        style = T.small, color = P.onHero.copy(alpha = 0.9f))
                    Spacer(Modifier.height(10.dp))
                    // The racks: five rows of five, money balls ringed in gold.
                    for (rack in 0 until 5) Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (b in 0 until 5) {
                            val i = rack * 5 + b
                            val m = marks.value.getOrNull(i)
                            val money = AllStarSaturday.ballValue(i) == 2
                            Box(Modifier.size(16.dp).clip(CircleShape)
                                .background(when (m) { true -> if (money) P.gold else P.onHero; false -> P.onHero.copy(alpha = 0.2f); else -> Color.Transparent })
                                .border(1.5.dp, if (money) P.gold else P.onHero.copy(alpha = 0.6f), CircleShape))
                        }
                    }
                }
            }
            if (!done) item {
                Spacer(Modifier.height(16.dp))
                val money = AllStarSaturday.ballValue(shot) == 2
                Text(if (money) "MONEY BALL · 2 POINTS" else "RACK ${shot / 5 + 1}", style = T.label, color = if (money) P.gold else P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                Spacer(Modifier.height(10.dp))
                Meter(shot, AllStarSaturday.sweepMs(shot), half) { cur[0] = it }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Shoot", Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                    val made = com.nh.hoopscoach.game.Shootaround.made(cur[0].toDouble(), half)
                    if (made) score += AllStarSaturday.ballValue(shot)
                    marks.value = marks.value + made
                    last = if (made) (if (AllStarSaturday.ballValue(shot) == 2) "Money." else listOf("Splash.", "Wet.", "Nothing but net.").random()) else listOf("Rim out.", "Short.", "Off the back iron.").random()
                    shot++
                    if (shot >= 25) {
                        val final = score
                        vm.act { AllStarSaturday.recordThree(it, final) }
                        done = true
                    }
                }
                if (last.isNotEmpty()) Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (done) item {
                Spacer(Modifier.height(12.dp))
                Panel {
                    val s = AllStarSaturday.threeScore(lg)
                    Text("YOUR ROUND", style = T.label, color = P.accent)
                    Text("$s of 34", style = T.head, color = P.ink)
                    Text(when {
                        s >= 26 -> "That wins most years. Saturday is going to be fun."
                        s >= 20 -> "A real shot at the final."
                        else -> "The money balls didn't fall. It's locked in for Saturday."
                    } + " It counts as your first round when the weekend comes.", style = T.small, color = P.sub)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Back to Today", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { vm.back() }
            }
        }
    }
}

/** The Dunk Contest: pick your dunk, time the takeoff; harder dunks score higher and have a smaller green. Two dunks, five judges. */
@Composable
fun DunkContestScreen(vm: GameViewModel, lg: League) {
    val h = lg.hero ?: run { vm.back(); return }
    var dunkNo by remember { mutableIntStateOf(0) }
    var choice by remember { mutableStateOf<AllStarSaturday.Dunk?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val scores = remember { mutableStateOf(listOf<Int>()) }
    var last by remember { mutableStateOf("") }
    var done by remember { mutableStateOf(AllStarSaturday.dunkScore(lg) >= 0) }
    val cur = remember { floatArrayOf(0f) }
    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("Dunk Contest", if (done) "Your dunks are in" else "Dunk ${dunkNo + 1} of 2", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Panel(color = P.hero) {
                    Text("ALL-STAR SATURDAY", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text("${if (done) AllStarSaturday.dunkScore(lg) else scores.value.sum()} OF 100", style = T.hero, color = P.onHero)
                    Text("Pick a dunk, then stop the marker in the green to take off. Harder dunks score higher but the green is smaller. Up to three tries each; every miss costs you with the judges.",
                        style = T.small, color = P.onHero.copy(alpha = 0.9f))
                    scores.value.forEachIndexed { i, s ->
                        Text("Dunk ${i + 1}: ${AllStarSaturday.cards(s).joinToString(" · ")} = $s", style = T.bodyB, color = P.onHero, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            if (!done && choice == null) item {
                Section("Pick dunk ${dunkNo + 1}")
                Panel {
                    AllStarSaturday.DUNKS.forEach { d ->
                        val half = AllStarSaturday.dunkWindow(h, d)
                        ChoiceRow(onClick = { choice = d; attempt = 0; last = "" }) {
                            Column(Modifier.weight(1f)) {
                                Text(d.name, style = T.bodyB, color = P.ink)
                                Text(d.blurb, style = T.small, color = P.sub)
                                Text("Scores ${d.lo}-${d.hi} · " + when { half >= 0.15 -> "easy for you"; half >= 0.09 -> "doable"; else -> "a real risk" }, style = T.small.copy(fontSize = 11.sp), color = P.faint)
                            }
                            Text("${"★".repeat(d.diff)}", style = T.small, color = P.gold)
                        }
                    }
                }
            }
            val d = choice
            if (!done && d != null) item {
                val half = AllStarSaturday.dunkWindow(h, d)
                Spacer(Modifier.height(16.dp))
                Text(d.name.uppercase() + " · TRY ${attempt + 1} OF 3", style = T.label, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                Text(d.blurb, style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                Spacer(Modifier.height(10.dp))
                Meter(dunkNo * 10 + attempt, 900, half) { cur[0] = it }
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Take off", Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                    val s = AllStarSaturday.dunkScore(d, cur[0].toDouble(), half, attempt)
                    val landed = when {
                        s != null -> s
                        attempt >= 2 -> AllStarSaturday.missedScore()
                        else -> null
                    }
                    if (landed == null) {
                        attempt++
                        last = listOf("Off the back of the rim.", "Lost the handle in the air.", "Too early. The crowd groans.").random()
                    } else {
                        last = if (s != null) (if (s >= 48) "The building loses its mind." else "Down. The judges like it.") else "Three misses. The judges give you something for trying."
                        scores.value = scores.value + landed
                        choice = null
                        attempt = 0
                        dunkNo++
                        if (dunkNo >= 2) {
                            val total = scores.value.sum()
                            vm.act { AllStarSaturday.recordDunk(it, total) }
                            done = true
                        }
                    }
                }
                if (last.isNotEmpty()) Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (!done && d == null && last.isNotEmpty()) item {
                Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            if (done) item {
                Spacer(Modifier.height(12.dp))
                Panel {
                    val s = AllStarSaturday.dunkScore(lg)
                    Text("YOUR DUNKS", style = T.label, color = P.accent)
                    Text("$s of 100", style = T.head, color = P.ink)
                    Text(when {
                        s >= 94 -> "A perfect-ish night. The final is yours to lose."
                        s >= 85 -> "That gets you to the final most years."
                        else -> "Not your night in the air. It's locked in for Saturday."
                    } + " It counts as your first round when the weekend comes.", style = T.small, color = P.sub)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Back to Today", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { vm.back() }
            }
        }
    }
}

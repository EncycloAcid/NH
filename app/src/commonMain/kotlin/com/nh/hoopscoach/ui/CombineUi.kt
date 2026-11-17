package com.nh.hoopscoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Combine
import com.nh.hoopscoach.game.CombineResult
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.PlayerCombine
import com.nh.hoopscoach.game.Shootaround

/**
 * A marker sweeping a track: stop it in the middle. [half] draws the green window (a shot); without one the
 * track is a gradient (a test: the closer to the middle, the better).
 */
@Composable
private fun TimingMeter(key: Any, sweepMs: Int, half: Double?, label: String, onStop: (Double) -> Unit) {
    key(key) {
        val anim = rememberInfiniteTransition(label = "meter")
        val pos by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(sweepMs, easing = LinearEasing), RepeatMode.Reverse), label = "pos")
        BoxWithConstraints(Modifier.padding(horizontal = Gutter).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(P.surface2)) {
            val w = maxWidth
            if (half != null) {
                Box(Modifier.offset(x = w * (0.5f - half.toFloat())).width(w * (2 * half).toFloat()).fillMaxSize().background(P.good.copy(alpha = 0.55f)))
            } else {
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(P.bad.copy(alpha = 0.35f), P.warn.copy(alpha = 0.35f), P.good.copy(alpha = 0.6f), P.warn.copy(alpha = 0.35f), P.bad.copy(alpha = 0.35f)))))
            }
            Box(Modifier.offset(x = w * 0.5f - 1.dp).width(2.dp).fillMaxSize().background(P.good))
            Box(Modifier.offset(x = w * pos - 3.dp).width(6.dp).fillMaxSize().background(P.ink))
        }
        Spacer(Modifier.height(16.dp))
        PrimaryButton(label, Modifier.fillMaxWidth().padding(horizontal = Gutter)) { onStop(pos.toDouble()) }
    }
}

/** In the offseason, a player in this year's draft: the combine, or how it went. */
@Composable
fun PlayerCombineCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val r = lg.road ?: return
    if (PlayerCombine.done(lg)) {
        val c = r.combine ?: return
        Section("Your combine")
        Panel {
            Text(Combine.line(c), style = T.small, color = P.ink)
            c.notes.take(3).forEach { n -> Text(n, style = T.small, color = if (n.startsWith("Shaky") || n.startsWith("Short")) P.bad else P.good, modifier = Modifier.padding(top = 2.dp)) }
            Text(when {
                c.move > 0 -> "Up ${c.move} spots on the big board: No. ${r.combineBefore} to No. ${r.combineAfter}."
                c.move < 0 -> "Down ${-c.move} spots on the big board: No. ${r.combineBefore} to No. ${r.combineAfter}."
                else -> "Holding at No. ${r.combineAfter} on the big board."
            }, style = T.bodyB, color = if (c.move > 0) P.good else if (c.move < 0) P.bad else P.ink, modifier = Modifier.padding(top = 8.dp))
            r.combineTalks.forEach { Text(it, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp)) }
        }
        return
    }
    if (!PlayerCombine.open(lg)) return
    Section("The draft combine")
    Panel(onClick = { vm.push(Route.PlayerCombine) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("MEASURED, TESTED, INTERVIEWED", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                Text("Measurements, the vertical, the lane agility and the sprint, a shooting drill, and three team interviews. It moves you on the big board, and the big board decides Draft Night.",
                    style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(8.dp))
            SmallButton("Go", primary = true) { vm.push(Route.PlayerCombine) }
        }
    }
}

/** The combine, one event at a time. */
@Composable
fun PlayerCombineScreen(vm: GameViewModel, lg: League) {
    val h = lg.playerOrNull(lg.heroId) ?: run { vm.back(); return }
    val body = remember { PlayerCombine.body(lg) }
    val talks = remember { PlayerCombine.talks(lg) }
    val effort = remember { DoubleArray(3) { 0.5 } }
    val answers = remember { mutableStateListOf<Int>() }
    var step by remember { mutableIntStateOf(0) }
    var shots by remember { mutableIntStateOf(0) }
    var makes by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<CombineResult?>(null) }
    val events = listOf(
        Triple("Max vertical", "Two steps and up. Stop the marker in the middle for your best jump.", "Jump"),
        Triple("Lane agility", "Around the lane: sprint, slide, backpedal. Time your cuts.", "Go"),
        Triple("Three-quarter sprint", "Baseline to the far free-throw line, flat out.", "Run"),
    )
    val titles = listOf("Measurements") + events.map { it.first } + "Shooting" + talks.map { "Interview: ${it.team.substringAfterLast(' ')}" } + "Results"
    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("The draft combine", "${(step + 1).coerceAtMost(titles.size)} of ${titles.size} · ${titles[step.coerceAtMost(titles.lastIndex)]}", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Panel(color = P.hero) {
                    Text("DRAFT COMBINE · ${lg.season}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    FitText(titles[step.coerceAtMost(titles.lastIndex)].uppercase(), T.hero, P.onHero)
                    Text("${h.name} · ${h.posLabel} · No. ${com.nh.hoopscoach.game.Offseason.draftBoard(lg)[h.id] ?: "?"} on the big board",
                        style = T.small, color = P.onHero.copy(alpha = 0.9f))
                }
                Spacer(Modifier.height(14.dp))
            }
            when {
                step == 0 && body != null -> item {
                    Panel {
                        listOf("Height without shoes" to Combine.feet(body.height), "Wingspan" to Combine.feet(body.wingspan),
                            "Standing reach" to Combine.feet(body.reach), "Weight" to "${body.weight} lb").forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                Text(k, style = T.body, color = P.sub, modifier = Modifier.weight(1f))
                                Text(v, style = T.bodyB, color = P.ink)
                            }
                        }
                        Text(if (body.length >= 5) "Long arms: +${Combine.f1(body.length)}\" over your height. Scouts love that." else if (body.length <= 0.5) "Your arms are short for your height; the testing has to make up for it." else "Normal length for your size.",
                            style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("On to the testing", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { step = 1 }
                }
                step in 1..3 -> item {
                    val (name, how, verb) = events[step - 1]
                    Text(how, style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                    Spacer(Modifier.height(14.dp))
                    TimingMeter(step, listOf(900, 760, 640)[step - 1], null, verb) { pos ->
                        val q = (1.0 - kotlin.math.abs(pos - 0.5) * 2).coerceIn(0.0, 1.0)
                        effort[step - 1] = q
                        last = "$name: " + when { q >= 0.85 -> "nailed it."; q >= 0.6 -> "a good one."; q >= 0.35 -> "okay."; else -> "you slipped." }
                        step++
                    }
                    if (last.isNotEmpty()) Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                step == 4 -> item {
                    Text("Ten spot-up threes around the arc. ${makes} of $shots so far.", style = T.body, color = P.ink, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                    Spacer(Modifier.height(14.dp))
                    val half = Shootaround.window(lg, h, 2)
                    TimingMeter("shot$shots", 820, half, "Shoot") { pos ->
                        val made = Shootaround.made(pos, half)
                        if (made) makes++
                        shots++
                        last = if (made) "Good." else "Off."
                        if (shots >= 10) { last = ""; step = 5 }
                    }
                    if (last.isNotEmpty()) Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
                }
                step in 5 until 5 + talks.size -> item {
                    val t = talks[step - 5]
                    Panel {
                        Text(t.team.uppercase(), style = T.label, color = P.accent)
                        Text(t.kind, style = T.small, color = P.sub)
                        Text("“${t.question}”", style = T.head, color = P.ink, modifier = Modifier.padding(top = 8.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    t.answers.forEachIndexed { i, a ->
                        Panel(onClick = { answers += i; step++ }) { Text(a, style = T.body, color = P.ink) }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                else -> item {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        if (result == null) {
                            var res: CombineResult? = null
                            vm.act { res = PlayerCombine.finish(it, body, effort, makes, answers.toList()) }
                            result = res
                        }
                    }
                    val c = result
                    if (c != null) {
                        val r = lg.road
                        Panel {
                            Text(Combine.line(c), style = T.small, color = P.ink)
                            Spacer(Modifier.height(8.dp))
                            c.pct.forEach { (k, v) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(k, style = T.small, color = P.sub, modifier = Modifier.width(110.dp))
                                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(P.line)) {
                                        Box(Modifier.fillMaxWidth((v / 100f).coerceIn(0.03f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp))
                                            .background(if (v >= 70) P.good else if (v >= 35) P.accent else P.bad))
                                    }
                                    Text("${v}th", style = T.small.copy(fontSize = 11.sp), color = P.sub, modifier = Modifier.width(44.dp).padding(start = 6.dp))
                                }
                            }
                            Text("Percentile in this year's class", style = T.small.copy(fontSize = 10.sp), color = P.faint)
                            c.notes.take(3).forEach { n -> Text(n, style = T.small, color = if (n.startsWith("Shaky") || n.startsWith("Short")) P.bad else P.good, modifier = Modifier.padding(top = 3.dp)) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Panel {
                            Text("THE INTERVIEWS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                            r?.combineTalks?.forEach { Text(it, style = T.small, color = P.ink, modifier = Modifier.padding(top = 2.dp)) }
                            Text(when {
                                c.move > 0 -> "Up ${c.move} spots on the big board: No. ${r?.combineBefore} to No. ${r?.combineAfter}."
                                c.move < 0 -> "Down ${-c.move} spots: No. ${r?.combineBefore} to No. ${r?.combineAfter}."
                                else -> "Holding at No. ${r?.combineAfter} on the big board."
                            }, style = T.bodyB, color = if (c.move > 0) P.good else if (c.move < 0) P.bad else P.ink, modifier = Modifier.padding(top = 10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Back to the offseason", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { vm.back() }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

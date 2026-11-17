package com.nh.hoopscoach.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LeagueLists
import com.nh.hoopscoach.game.Pickem
import com.nh.hoopscoach.game.Shootaround

// ---------- weekly Pick'em ----------

/** Today: the week's three biggest games to pick, the streak, and what it pays. */
@Composable
fun PickemCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    androidx.compose.runtime.LaunchedEffect(lg.season, lg.day / 7) { vm.act { Pickem.ensure(it) } }
    val s = lg.pickem
    if (s.games.isEmpty() || s.season != lg.season) return
    val schedule = LeagueLists.games(lg)
    Section("Pick'em · week ${s.week + 1}")
    Panel {
        Text("Pick the winners. Three right: two card packs. Two right: one. Five in a row: a bonus pack.", style = T.small, color = P.sub)
        s.games.forEach { pg ->
            val g = schedule.firstOrNull { it.id == pg.gameId } ?: return@forEach
            val away = lg.team(g.away); val home = lg.team(g.home)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("DAY ${g.day}" + (lg.rivalry(g)?.let { " · RIVALRY" } ?: ""), style = T.label.copy(fontSize = 9.sp), color = P.faint, modifier = Modifier.weight(1f))
                if (g.played) Text(
                    when (pg.result) { 1 -> "RIGHT"; -1 -> "WRONG"; else -> "NO PICK" } + " · ${g.aws}-${g.hs}",
                    style = T.label.copy(fontSize = 9.sp), color = when (pg.result) { 1 -> P.good; -1 -> P.bad; else -> P.faint },
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(away, home).forEach { t ->
                    val picked = pg.pick == t.id
                    val won = g.played && g.winner() == t.id
                    val shape = RoundedCornerShape(12.dp)
                    Row(
                        Modifier.weight(1f).clip(shape).background(if (picked) P.accentSoft else P.surface2.copy(alpha = 0.5f))
                            .border(1.dp, if (picked) P.accent else P.line, shape)
                            .then(if (!g.played) Modifier.clickable { vm.act { Pickem.pick(it, g.id, t.id) } } else Modifier)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TeamBadge(t, 26.dp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (lg.pro) t.mascot else t.school, style = T.bodyB.copy(fontSize = 14.sp), color = if (won) P.good else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (t.id == home.id) "home · " else "away · ") + t.record + (if (!lg.pro && t.ap in 1..25) " · No. ${t.ap}" else ""),
                                style = T.small.copy(fontSize = 11.sp), color = P.sub, maxLines = 1)
                        }
                    }
                }
            }
        }
        Text("Streak ${s.streak} · best ${s.best} · this season ${s.right} of ${s.total}" + if (s.lastNote.isNotEmpty()) "\n${s.lastNote}" else "",
            style = T.small.copy(fontSize = 11.sp), color = P.faint, modifier = Modifier.padding(top = 10.dp))
    }
}

// ---------- the pregame shootaround ----------

/** Today, a game day in a player career: get your shots up. */
@Composable
fun ShootaroundCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val r = lg.road ?: return
    if (!lg.playerMode || Shootaround.todaysGame(lg) == null) return
    val done = r.shootSeason == lg.season && r.shootDay == lg.day
    val hot = Shootaround.edge(lg) > 0
    Panel(onClick = if (done) null else ({ vm.push(Route.Shootaround) })) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("PREGAME SHOOTAROUND", style = T.label.copy(fontSize = 10.sp), color = if (hot) P.good else P.accent)
                Text(when {
                    done && hot -> "${r.shootLast} of 10. You're feeling it tonight."
                    done -> "${r.shootLast} of 10 this morning · best ${r.shootBest}"
                    else -> "Ten shots on the meter. Eight or better and you're feeling it tonight."
                }, style = T.small, color = P.sub)
            }
            if (!done) SmallButton("Shoot", primary = true) { vm.push(Route.Shootaround) }
        }
    }
}

/** Ten shots: stop the marker in the green. */
@Composable
fun ShootaroundScreen(vm: GameViewModel, lg: League) {
    val h = lg.hero ?: run { vm.back(); return }
    var shot by remember { mutableIntStateOf(0) }
    var threes by remember { mutableIntStateOf(0) }
    var mids by remember { mutableIntStateOf(0) }
    var last by remember { mutableStateOf("") }
    val marks = remember { mutableStateOf(listOf<Boolean>()) }
    var summary by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("Shootaround", if (summary == null) "Shot ${(shot + 1).coerceAtMost(10)} of 10" else "Done", onBack = { vm.back() })
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                Panel(color = P.hero) {
                    Text("PREGAME SHOOTAROUND", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text(if (shot == 0) "10 SHOTS" else "${threes + mids} OF $shot", style = T.hero, color = P.onHero)
                    Text("Stop the marker in the green. Your shooting ratings and badges make the green wider; a deep three moves fastest.",
                        style = T.small, color = P.onHero.copy(alpha = 0.9f))
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (0 until 10).forEach { i ->
                            val m = marks.value.getOrNull(i)
                            Box(Modifier.size(16.dp).clip(CircleShape).background(when (m) { true -> P.onHero; false -> P.onHero.copy(alpha = 0.25f); else -> Color.Transparent })
                                .border(1.dp, P.onHero.copy(alpha = 0.6f), CircleShape))
                        }
                    }
                }
            }
            if (summary == null) item {
                val spot = Shootaround.SPOTS[shot.coerceAtMost(9)]
                val half = Shootaround.window(lg, h, spot.type)
                Spacer(Modifier.height(16.dp))
                Text(spot.name.uppercase(), style = T.label, color = P.accent, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                Text(if (spot.type == 2) "A three" else "A mid-range jumper", style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp))
                Spacer(Modifier.height(14.dp))
                // A fresh marker for each shot, at its spot's speed.
                key(shot) {
                    val anim = rememberInfiniteTransition(label = "meter")
                    val pos by anim.animateFloat(0f, 1f, infiniteRepeatable(tween(Shootaround.sweepMs(spot.type, shot), easing = LinearEasing), RepeatMode.Reverse), label = "pos")
                    BoxWithConstraints(Modifier.padding(horizontal = Gutter).fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(P.surface2)) {
                        val w = maxWidth
                        Box(Modifier.offset(x = w * (0.5f - half.toFloat())).width(w * (2 * half).toFloat()).fillMaxSize().background(P.good.copy(alpha = 0.55f)))
                        Box(Modifier.offset(x = w * 0.5f - 1.dp).width(2.dp).fillMaxSize().background(P.good))
                        Box(Modifier.offset(x = w * pos - 3.dp).width(6.dp).fillMaxSize().background(P.ink))
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Shoot", Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                        val made = Shootaround.made(pos.toDouble(), half)
                        if (made) { if (spot.type == 2) threes++ else mids++ }
                        marks.value = marks.value + made
                        last = if (made) listOf("Swish.", "Wet.", "Nothing but net.", "Cash.").random() else listOf("Rim out.", "Front rim.", "Off the back iron.", "Short.").random()
                        shot++
                        if (shot >= 10) {
                            var msg = ""
                            vm.act { msg = Shootaround.finish(it, threes, mids) }
                            summary = msg
                        }
                    }
                }
                if (last.isNotEmpty()) Text(last, style = T.bodyB, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 12.dp))
            }
            summary?.let { s ->
                item {
                    Spacer(Modifier.height(12.dp))
                    Panel {
                        Text("SHOOTAROUND DONE", style = T.label, color = P.accent)
                        Text(s, style = T.bodyB, color = P.ink)
                        Text(if (threes + mids >= 8) "Eight or better: a little extra on your shots tonight." else "Eight or better puts you in rhythm for the game. Next game day, go again.",
                            style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Back to Today", Modifier.fillMaxWidth().padding(horizontal = Gutter)) { vm.back() }
                }
            }
        }
    }
}

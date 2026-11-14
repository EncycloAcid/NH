package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.Highlight
import com.nh.hoopscoach.game.Highlights
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.PlayerLine

/** Today: last night's Top 10, its top three, and where the user's plays landed. */
@Composable
fun TopTenCard(vm: GameViewModel, lg: League) {
    if (lg.topSeason != lg.season || lg.topPlays.isEmpty() || lg.day - lg.topDay > 1) return
    val mine = Highlights.myRanks(lg)
    Section("Last night's Top 10", action = "All 10", onAction = { vm.push(Route.TopTen) })
    Panel(color = P.hero, onClick = { vm.push(Route.TopTen) }, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        if (mine.isNotEmpty()) Text("YOU'RE ON IT: NO. ${mine.joinToString(" & NO. ")}", style = T.label, color = heroKicker, modifier = Modifier.padding(bottom = 6.dp))
        lg.topPlays.take(3).forEachIndexed { i, h -> PlayRowHero(vm, lg, i + 1, h) }
        Text("See all 10 →", style = T.bodyB, color = P.onHero.copy(alpha = 0.85f), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun PlayRowHero(vm: GameViewModel, lg: League, rank: Int, h: Highlight) {
    val t = lg.teams.getOrNull(h.teamId)
    val p = lg.anyPlayer(h.pid)
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("$rank", style = T.num.copy(fontSize = 26.sp), color = if (Highlights.mine(lg, h)) P.gold else P.onHero, modifier = Modifier.width(34.dp))
        if (p != null) PlayerJersey(p, t, 36.dp) else if (t != null) TeamBadge(t, 36.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(h.text, style = T.bodyB.copy(fontSize = 14.sp), color = P.onHero, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${t?.abbr ?: ""} vs ${lg.teams.getOrNull(h.oppId)?.abbr ?: ""} · ${h.time}", style = T.small, color = P.onHero.copy(alpha = 0.75f))
        }
    }
}

/** The full countdown, ten to one: each play rises in a beat after the one before. */
@Composable
fun TopTenScreen(vm: GameViewModel, lg: League) {
    val plays = lg.topPlays
    Column(Modifier.fillMaxSize()) {
        BackBar("Top 10 plays", "Last night · day ${lg.topDay}", onBack = { vm.back() })
        if (plays.isEmpty()) { Empty("No plays yet. Sim a day."); return }
        LazyColumn(Modifier.fillMaxSize()) {
            item { Deck("The ten best plays from last night around the league, counted down to No. 1.") }
            val order = plays.indices.reversed().toList()
            order.forEachIndexed { k, i ->
                item(key = "play-$i") {
                    val h = plays[i]
                    val mine = Highlights.mine(lg, h)
                    val top = i == 0
                    Staggered(k) {
                        Panel(Modifier.padding(top = 8.dp), color = if (top) P.hero else if (mine) P.accentSoft else P.surface,
                            onClick = { if (lg.games.any { it.id == h.gameId && it.box != null }) vm.push(Route.Box(h.gameId)) else vm.openPlayer(h.pid) }) {
                            val fg = if (top) P.onHero else P.ink
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${i + 1}", style = T.hero.copy(fontSize = if (top) 46.sp else 34.sp), color = if (top) heroKicker else if (mine) P.accent else P.faint, modifier = Modifier.width(if (top) 58.dp else 48.dp))
                                lg.anyPlayer(h.pid)?.let { PlayerJersey(it, lg.teams.getOrNull(h.teamId), if (top) 52.dp else 42.dp) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    if (mine) Text("YOUR PLAY", style = T.label.copy(fontSize = 9.sp), color = if (top) heroKicker else P.accent)
                                    Text(h.text, style = if (top) T.head else T.bodyB, color = fg)
                                    Text("${lg.teams.getOrNull(h.teamId)?.name ?: ""} vs ${lg.teams.getOrNull(h.oppId)?.abbr ?: ""} · ${h.time}", style = T.small, color = if (top) P.onHero.copy(alpha = 0.8f) else P.sub)
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Its content springs in [index] beats after the screen opens. */
@Composable
private fun Staggered(index: Int, content: @Composable () -> Unit) {
    val p = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(90L * index.coerceAtMost(10))
        p.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 360f))
    }
    Box(Modifier.graphicsLayer { alpha = p.value.coerceIn(0f, 1f); translationX = (1f - p.value) * 60f }) { content() }
}

/** His season mixtape: his best plays of the year, biggest first. */
@Composable
fun MixtapeScreen(vm: GameViewModel, lg: League) {
    val plays = Highlights.mixtape(lg)
    val who = if (lg.playerMode) lg.hero?.name ?: "Your" else lg.user.name
    Column(Modifier.fillMaxSize()) {
        BackBar("Season mixtape", "${lg.season} · ${plays.size} plays", onBack = { vm.back() })
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Panel(Modifier.padding(top = 4.dp).popIn(lg.season), color = P.hero) {
                    Text("THE ${lg.season} MIXTAPE", style = T.label, color = heroKicker)
                    Text(who.uppercase(), style = T.hero.copy(fontSize = 34.sp, lineHeight = 36.sp), color = P.onHero)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth()) {
                        for ((label, n) in Highlights.tally(plays)) Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("$n", style = T.num, color = P.onHero)
                            Text(label.uppercase(), style = T.label.copy(fontSize = 9.sp), color = P.onHero.copy(alpha = 0.75f))
                        }
                    }
                    val top10 = lg.news.count { it.season == lg.season && it.text.startsWith("No. 1 on tonight's Top 10") }
                    if (top10 > 0) Text("No. 1 on the Top 10 $top10 ${if (top10 == 1) "time" else "times"} this season.", style = T.small, color = P.onHero.copy(alpha = 0.9f), modifier = Modifier.padding(top = 10.dp))
                }
            }
            if (plays.isEmpty()) item { Empty("No highlights yet this season. Dunks, clutch threes, blocks and game-winners land here.") }
            else item {
                Section("The plays")
                GroupList(plays, inset = 50.dp) { h ->
                    ListRow(onClick = { if (lg.games.any { it.id == h.gameId && it.box != null }) vm.push(Route.Box(h.gameId)) }) {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(P.accentSoft), contentAlignment = Alignment.Center) {
                            Text(when (h.kind) { "dunk", "poster" -> "🔨"; "block" -> "✋"; "winner", "tie" -> "⏱"; else -> "🎯" }, style = T.body)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(h.text, style = T.bodyB, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("Day ${h.day} · vs ${lg.teams.getOrNull(h.oppId)?.name ?: ""} · ${h.time}", style = T.small, color = P.sub, maxLines = 1)
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** The box score's player of the game: the best line in the game, by game score. */
@Composable
fun PlayerOfTheGame(vm: GameViewModel, lg: League, g: Game) {
    val b = g.box ?: return
    fun gs(l: PlayerLine) = l.s.pts + 0.4 * l.s.fgm - 0.7 * l.s.fga - 0.4 * (l.s.fta - l.s.ftm) + 0.7 * l.s.reb + 0.7 * l.s.ast + l.s.stl + 0.7 * l.s.blk - l.s.tov
    val winners = if (g.hs >= g.aws) b.home else b.away
    val best = (b.home + b.away).maxByOrNull { gs(it) + if (it in winners) 3.0 else 0.0 } ?: return
    val p = lg.anyPlayer(best.pid)
    val t = lg.teams.getOrNull(if (best in b.home) g.home else g.away)
    Spacer(Modifier.height(10.dp))
    Panel(color = P.hero, onClick = { vm.openPlayer(best.pid) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (p != null) PlayerJersey(p, t, 54.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("PLAYER OF THE GAME", style = T.label, color = heroKicker)
                Text(p?.name ?: best.name, style = T.head, color = P.onHero)
                Text("${best.s.pts} pts · ${best.s.reb} reb · ${best.s.ast} ast · ${best.s.fgm}-${best.s.fga} FG", style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
            t?.let { TeamBadge(it, 36.dp) }
        }
    }
}

@Suppress("unused")
private val keepArrangement = Arrangement.Center

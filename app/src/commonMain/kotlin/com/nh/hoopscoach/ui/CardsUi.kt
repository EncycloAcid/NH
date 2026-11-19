package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Card
import com.nh.hoopscoach.game.Cards
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.League

/** A rarity's edge colors and the ink that reads on them: Bronze, Silver, Gold, Diamond. */
private fun rarityEdge(r: Int): List<Color> = when (r) {
    3 -> listOf(Color(0xFF7DF9FF), Color(0xFFB98CFF), Color(0xFFFF8CE0), Color(0xFF7DF9FF))
    2 -> listOf(Color(0xFFFFE08A), Color(0xFFC9962E), Color(0xFFFFE9A8), Color(0xFFB07F22))
    1 -> listOf(Color(0xFFF2F4F7), Color(0xFF9BA3AE), Color(0xFFE3E7EC), Color(0xFF7D8590))
    else -> listOf(Color(0xFFE2A46E), Color(0xFF8A5530), Color(0xFFD99A62), Color(0xFF6E4122))
}

/** A collected card, small: the rarity edge, his portrait in his team's colors, his rating and name. */
@Composable
fun MiniCard(c: Card, width: Dp = 96.dp, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(width * 0.08f)
    Column(
        modifier.width(width).aspectRatio(0.71f).clip(shape).background(Brush.linearGradient(rarityEdge(c.rarity))).padding(width * 0.045f),
    ) {
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(width * 0.06f)).background(Color(c.primary))) {
            BasketballCharacterCanvas(
                face = Face.decode(c.face) ?: Face.random(c.name.hashCode().toLong()), jerseyKey = null,
                primary = c.primary.toInt(), secondary = c.secondary.toInt(), number = c.number, label = c.abbr.ifEmpty { null },
                background = Color(c.primary).copy(alpha = 0.85f), corner = 0.dp, headshot = true, modifier = Modifier.fillMaxSize(),
            )
            Text("${c.rating}", style = T.num.copy(fontSize = (width.value * 0.17f).sp), color = Color.White,
                modifier = Modifier.padding(width * 0.05f).background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp))
        }
        Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.72f)).padding(horizontal = 4.dp, vertical = 3.dp)) {
            Text(c.name, style = T.label.copy(fontSize = (width.value * 0.095f).sp), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${c.pos} · ${c.team}", style = T.small.copy(fontSize = (width.value * 0.08f).sp), color = Color.White.copy(alpha = 0.75f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A slot in a set you don't have yet: a silhouette where the card goes, and whose card it is. */
@Composable
private fun EmptySlot(name: String, width: Dp) {
    val shape = RoundedCornerShape(width * 0.08f)
    val ink = P.line
    Column(Modifier.width(width).aspectRatio(0.71f).clip(shape).background(P.surface).border(1.dp, P.line, shape)) {
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().weight(1f).padding(top = width * 0.14f, start = width * 0.2f, end = width * 0.2f)) {
            val r = size.width * 0.22f
            drawCircle(ink, r, androidx.compose.ui.geometry.Offset(size.width / 2, r + 1f))
            drawRoundRect(ink, androidx.compose.ui.geometry.Offset(0f, r * 2.35f), androidx.compose.ui.geometry.Size(size.width, size.height - r * 2.35f),
                androidx.compose.ui.geometry.CornerRadius(size.width * 0.45f, size.width * 0.45f))
        }
        Box(Modifier.fillMaxWidth().background(P.surface2).padding(horizontal = 4.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
            Text(name, style = T.label.copy(fontSize = (width.value * 0.105f).sp, letterSpacing = 0.sp), color = P.sub, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = (width.value * 0.125f).sp)
        }
    }
}

/** Cards in a grid that fills the width: as many columns of at least [minWidth] as fit (three at the least). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> CardGrid(items: List<T>, minWidth: Dp = 78.dp, gap: Dp = 8.dp, card: @Composable (T, Dp) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
        val cols = ((maxWidth + gap) / (minWidth + gap)).toInt().coerceAtLeast(3)
        val w = (maxWidth - gap * (cols - 1)) / cols
        FlowRow(horizontalArrangement = Arrangement.spacedBy(gap), verticalArrangement = Arrangement.spacedBy(gap)) {
            items.forEach { card(it, w) }
        }
    }
}

/** Today: packs waiting, and how the sets stand. */
@Composable
fun BinderTodayCard(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val b = lg.binder
    if (!b.started && Cards.myTeam(lg) == null) return
    val current = b.sets.filter { it.season == lg.season }
    Spacer(Modifier.height(CardGap))
    Panel(onClick = { vm.push(Route.Binder) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CARD COLLECTION", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                Text(if (b.packs > 0 || !b.started) "${if (b.started) b.packs else 3} pack${if (b.packs == 1 && b.started) "" else "s"} to open" else "${b.cards.size} cards",
                    style = T.bodyB, color = P.ink)
                Text(if (current.isNotEmpty()) current.joinToString(" · ") { "${it.title.substringBefore(" ${lg.season}")} ${Cards.progress(lg, it)}/${it.pids.size}" }
                    else "Win games to earn packs: every three wins, upsets and big games.", style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("Open", primary = b.packs > 0 || !b.started) { vm.push(Route.Binder) }
        }
    }
}

/** The binder: open packs, chase the season's sets, and look through everything you've pulled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BinderScreen(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    androidx.compose.runtime.LaunchedEffect(lg.season) { vm.act { Cards.ensure(it) } }
    val b = lg.binder
    var filter by remember { mutableIntStateOf(0) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            ScreenHeader("Card collection", "The binder", "${b.cards.size} cards · ${b.sets.count { it.done }} of ${b.sets.size} sets complete")
            Panel(color = P.hero) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("PACKS TO OPEN", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                        Text("${b.packs}", style = T.hero, color = P.onHero)
                        Text("A pack every 3 wins (${b.points}/3), and one for every upset, rivalry or postseason win. 5 duplicates trade in for a pack (${b.dupes}/5).",
                            style = T.small, color = P.onHero.copy(alpha = 0.85f))
                    }
                }
            }
            PrimaryButton(if (b.packs > 0) "Open a pack" else "No packs: go win some games", Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 10.dp), enabled = b.packs > 0) {
                vm.act { Cards.open(it) }
            }
        }
        if (b.lastPack.isNotEmpty()) item {
            Section("Your last pack")
            CardGrid(b.lastPack, minWidth = 100.dp) { c, w -> MiniCard(c, w, Modifier.popIn(c.pid * 31 + b.opened)) }
        }
        val sets = b.sets.sortedWith(compareBy({ it.done }, { -it.season }))
        sets.forEach { s ->
            item {
                val have = Cards.progress(lg, s)
                Section("${s.title} · $have of ${s.pids.size}${if (s.done) " · complete" else ""}")
                Text(s.blurb + if (s.done) "." else ". Finish it for two packs and ${if (lg.playerMode) "a bigger following" else "more fan interest"}.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 2.dp))
                Spacer(Modifier.height(6.dp))
                CardGrid(s.pids) { pid, w ->
                    val c = b.cards.lastOrNull { it.pid == pid }
                    if (c != null) MiniCard(c, w) else EmptySlot(lg.anyPlayer(pid)?.name ?: "?", w)
                }
            }
        }
        if (b.cards.isNotEmpty()) item {
            Section("Every card · ${b.cards.size}")
            Box(Modifier.padding(horizontal = Gutter)) {
                Segmented(listOf("All", "Diamond", "Gold", "Silver", "Bronze"), filter, { filter = it })
            }
            Spacer(Modifier.height(8.dp))
            val shown = b.cards.filter { filter == 0 || it.rarity == 4 - filter }.sortedWith(compareByDescending<Card> { it.rarity }.thenByDescending { it.rating })
            CardGrid(shown.take(120)) { c, w -> MiniCard(c, w) }
            if (shown.size > 120) Text("And ${shown.size - 120} more.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
            Spacer(Modifier.height(24.dp))
        }
    }
}

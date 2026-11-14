package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Feature
import com.nh.hoopscoach.game.FeatureItem
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

// ---------- the rotating feature pages and the alternate covers ----------

/** A feature page: its deck, then its items (grouped under their tags, like tiers, or each with a tag chip). */
internal fun LazyListScope.featurePage(vm: GameViewModel, lg: League, f: Feature) {
    item { Deck(f.deck) }
    if (f.grouped) {
        val groups = f.items.groupBy { it.tag }
        for ((tag, items) in groups) item {
            Section("$tag · ${items.size}")
            GroupList(items, inset = 60.dp) { FeatureRow(vm, lg, it, showTag = false) }
        }
    } else item {
        Spacer(Modifier.height(10.dp))
        GroupList(f.items, inset = 60.dp) { FeatureRow(vm, lg, it, showTag = true) }
    }
}

@Composable
private fun tagColor(tag: String): Color = when {
    tag.startsWith("OVER") || tag == "LOCK" || tag.contains("TOP 10") -> P.good
    tag.startsWith("UNDER") || tag == "HOT" -> P.bad
    tag == "WARM" || tag == "EXPIRING" -> P.warn
    tag.startsWith("Q") -> P.accent
    else -> P.accent
}

@Composable
private fun FeatureRow(vm: GameViewModel, lg: League, it: FeatureItem, showTag: Boolean) {
    val team = lg.teams.getOrNull(it.teamId)
    val player = lg.playerOrNull(it.pid)
    ListRow(onClick = when { player != null -> ({ vm.openPlayer(player.id) }); team != null -> ({ vm.push(Route.TeamPage(team.id)) }); else -> null }) {
        when {
            player != null && player.teamId >= 0 -> PlayerJersey(player, lg.team(player.teamId), 44.dp)
            team != null -> TeamBadge(team, 40.dp)
            else -> Box(Modifier.width(40.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (showTag && it.tag.isNotEmpty()) { Tag(it.tag.uppercase(), tagColor(it.tag)); Spacer(Modifier.height(3.dp)) }
            Text(it.head, style = T.bodyB, color = P.ink)
            Text(it.body, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/**
 * A feature cover: the year's biggest story on the front, in its team's colors, with the player at its
 * center when there is one.
 */
@Composable
internal fun FeatureCover(
    masthead: String, season: String, team: Team, player: Player?, kicker: String, headline: String, deck: String,
    lines: List<Pair<String, String>>, count: Int, onRead: () -> Unit, onSkip: (() -> Unit)? = null, userBox: @Composable () -> Unit,
) {
    val primary = teamCard(team)
    val secondary = Color(team.secondary)
    val fg = onColor(primary)
    val accent = if (contrast(secondary, primary) >= 2.2f) secondary else fg
    Spacer(Modifier.height(6.dp))
    Column(
        Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.verticalGradient(listOf(lerp(primary, Color.Black, 0.15f), lerp(primary, Color.Black, 0.7f)))).padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(masthead, style = T.label, color = fg.copy(alpha = 0.85f), modifier = Modifier.weight(1f))
            Text(season, style = T.label, color = fg.copy(alpha = 0.85f))
        }
        Spacer(Modifier.height(10.dp))
        Text(kicker.uppercase(), style = T.label.copy(fontSize = 13.sp, letterSpacing = 3.sp), color = accent)
        Text(headline.uppercase(), style = T.hero.copy(fontSize = 44.sp, lineHeight = 44.sp), color = fg)
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (player != null) PlayerJersey(player, team, 120.dp) else TeamBadge(team, 96.dp)
            Spacer(Modifier.width(14.dp))
            Text(deck, style = T.body, color = fg.copy(alpha = 0.92f), modifier = Modifier.weight(1f), maxLines = 7, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(accent.copy(alpha = 0.7f)))
        Spacer(Modifier.height(10.dp))
        Text("ALSO IN THIS ISSUE", style = T.label.copy(fontSize = 10.sp), color = fg.copy(alpha = 0.7f))
        lines.forEach { (k, h) -> CoverLine(k, h, fg, accent) }
        Spacer(Modifier.height(12.dp))
        userBox()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$count pages · swipe to open →", style = T.bodyB, color = accent, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
            if (onSkip != null) Text("Skip preview →", style = T.small, color = fg.copy(alpha = 0.85f), modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 6.dp))
        }
    }
}

/** A versus cover, for a two-horse race: the two favorites side by side in their colors, their odds on a shared bar. */
@Composable
internal fun VersusCover(
    masthead: String, season: String, headline: String, a: Team, b: Team, aOdds: Double, bOdds: Double, aLine: String, bLine: String,
    lines: List<Pair<String, String>>, count: Int, onRead: () -> Unit, onSkip: (() -> Unit)? = null, userBox: @Composable () -> Unit,
) {
    Spacer(Modifier.height(6.dp))
    Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xFF101318)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(masthead, style = T.label, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
            Text(season, style = T.label, color = Color.White.copy(alpha = 0.8f))
        }
        Text("THE RACE", style = T.hero.copy(fontSize = 64.sp, lineHeight = 62.sp, letterSpacing = 2.sp), color = Color.White)
        Text(headline.uppercase(), style = T.title.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.gold)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            for ((t, line) in listOf(a to aLine, b to bLine)) {
                val c = teamCard(t)
                val fg = onColor(c)
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Brush.verticalGradient(listOf(c, lerp(c, Color.Black, 0.5f)))).padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TeamBadge(t, 56.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(t.school.uppercase(), style = T.bodyB, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                    Text(odd(if (t.id == a.id) aOdds else bOdds), style = T.hero.copy(fontSize = 30.sp), color = fg)
                    Text(line, style = T.small, color = fg.copy(alpha = 0.85f), textAlign = TextAlign.Center, maxLines = 2)
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        // Their title odds on one bar.
        val share = (aOdds / (aOdds + bOdds).coerceAtLeast(1e-6)).toFloat()
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp))) {
            Box(Modifier.weight(share.coerceIn(0.05f, 0.95f)).height(10.dp).background(Color(a.primary)))
            Box(Modifier.weight((1 - share).coerceIn(0.05f, 0.95f)).height(10.dp).background(Color(b.primary)))
        }
        Spacer(Modifier.height(14.dp))
        lines.forEach { (k, h) -> CoverLine(k, h, Color.White, P.gold) }
        Spacer(Modifier.height(12.dp))
        userBox()
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("$count pages · swipe to open →", style = T.bodyB, color = P.gold, modifier = Modifier.clickable(onClick = onRead).padding(vertical = 6.dp))
            if (onSkip != null) Text("Skip preview →", style = T.small, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.clickable(onClick = onSkip).padding(vertical = 6.dp))
        }
    }
}

/**
 * The bold predictions; once the season is over, each one marked right or wrong, with what really
 * happened and the tally at the top.
 */
@Composable
internal fun BoldPredictions(bold: List<String>, graded: List<Int>, outcomes: List<String>) {
    val done = graded.count { it >= 0 }
    Section(if (done > 0) "Bold predictions · ${graded.count { it == 1 }} of $done right" else "Bold predictions")
    GroupList(bold.withIndex().toList()) { (i, text) ->
        val g = graded.getOrNull(i) ?: -1
        ListRow {
            Text("${i + 1}", style = T.num.copy(fontSize = 22.sp), color = when (g) { 1 -> P.good; 0 -> P.bad; else -> P.accent }, modifier = Modifier.width(34.dp))
            Column(Modifier.weight(1f)) {
                Text(text, style = T.body, color = if (g == 0) P.sub else P.ink)
                outcomes.getOrNull(i)?.takeIf { g >= 0 && it.isNotEmpty() }?.let { Text(it, style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp)) }
            }
            if (g >= 0) {
                Spacer(Modifier.width(8.dp))
                Tag(if (g == 1) "RIGHT" else "WRONG", if (g == 1) P.good else P.bad)
            }
        }
    }
}

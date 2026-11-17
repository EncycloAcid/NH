package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Badge
import com.nh.hoopscoach.game.Badges
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player

fun badgeIcon(b: Badge): ImageVector = when (b) {
    Badge.SNIPER -> GameIcons.Target
    Badge.PULL_UP -> GameIcons.Ball
    Badge.ICE_WATER -> GameIcons.Stopwatch
    Badge.RIM_WRECKER -> GameIcons.Hoop
    Badge.AND_ONE -> GameIcons.Bolt
    Badge.FLOOR_GENERAL -> GameIcons.Clipboard
    Badge.TIGHT_HANDLES -> GameIcons.Lock
    Badge.LOCKDOWN -> GameIcons.Shield
    Badge.RIM_PROTECTOR -> GameIcons.Arena
    Badge.BALL_HAWK -> GameIcons.Swap
    Badge.GLASS_CLEANER -> GameIcons.Repost
}

/** Bronze, Silver, Gold, Legend (and grey for not yet). */
fun tierColor(t: Int): Color = when (t) {
    1 -> Color(0xFFB8793F)
    2 -> Color(0xFF7C8BA1)
    3 -> Color(0xFFD9A62A)
    4 -> Color(0xFF8250D8)
    else -> Color(0xFF6B7280)
}

/** A badge's medal: its icon on its tier's color (faded when he doesn't have it yet). */
@Composable
fun BadgeMedal(b: Badge, tier: Int, size: Dp = 34.dp) {
    val c = tierColor(tier)
    Box(
        Modifier.size(size).clip(CircleShape).background(if (tier > 0) c else c.copy(alpha = 0.18f))
            .border(2.dp, if (tier >= 2) Color.White.copy(alpha = 0.55f) else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(badgeIcon(b), b.label, tint = if (tier > 0) Color.White else P.faint, modifier = Modifier.size(size * 0.52f))
    }
}

/**
 * A player's badges. Your own player (a player career) sees every badge with its progress: the badge points he's
 * earned in games and the rating each tier needs. Anyone else shows the badges his ratings earn him.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgesPanel(vm: GameViewModel, lg: League, p: Player) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val mine = Badges.earns(lg, p)
    val list = com.nh.hoopscoach.game.Badge.entries.map { it to Badges.tier(lg, p, it) }
    val have = list.filter { it.second > 0 }.sortedByDescending { it.second }
    Section("Badges · ${have.size}")
    Panel {
        Text(
            if (mine) "Earn each tier by doing it in games (badge points from your box scores); your ratings set how high each can go. The pros raise the bar."
            else "From where his ratings rank in the league: top quarter Bronze, top 12% Silver, top 4% Gold, top 1% Legend.",
            style = T.small, color = P.sub,
        )
        if (have.isEmpty() && !mine) Text("No badges yet.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 10.dp))
        val rows = if (mine) list.sortedWith(compareByDescending<Pair<Badge, Int>> { it.second }.thenBy { it.first.ordinal }) else have
        rows.forEach { (b, t) ->
            val shape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(shape)
                    .background(if (t > 0) tierColor(t).copy(alpha = 0.10f) else P.surface2.copy(alpha = 0.5f))
                    .border(1.dp, if (t > 0) tierColor(t).copy(alpha = 0.45f) else P.line, shape).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BadgeMedal(b, t)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(b.label, style = T.bodyB.copy(fontSize = 14.sp), color = if (t > 0) P.ink else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false))
                        if (t > 0) {
                            Spacer(Modifier.width(6.dp))
                            Text(Badges.TIERS[t].uppercase(), style = T.label.copy(fontSize = 9.sp), color = tierColor(t))
                        }
                    }
                    Text(if (t > 0) b.effectAt(t) else "${b.group} · ${b.does}", style = T.small, color = if (t > 0) P.sub else P.faint)
                    if (mine && t < 4) {
                        val x = p.badgeXp[b.name] ?: 0
                        val need = b.xp[t]
                        val prev = if (t > 0) b.xp[t - 1] else 0
                        val f = ((x - prev).toFloat() / (need - prev).coerceAtLeast(1)).coerceIn(0f, 1f)
                        Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.line)) {
                            Box(Modifier.fillMaxWidth(f).height(5.dp).clip(RoundedCornerShape(3.dp)).background(tierColor(t + 1)))
                        }
                        Badges.next(lg, p, b).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.small.copy(fontSize = 11.sp), color = P.faint, modifier = Modifier.padding(top = 2.dp)) }
                    }
                }
            }
        }
    }
}

/** The badges in a row of medals, for cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BadgeRow(lg: League, p: Player, max: Int = 8, size: Dp = 26.dp) {
    val have = Badges.of(lg, p).take(max)
    if (have.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        have.forEach { (b, t) -> BadgeMedal(b, t, size) }
    }
}

/** Today, in a player career: your badges and the one you're closest to leveling. */
@Composable
fun HeroBadgesCard(vm: GameViewModel, lg: League) {
    val h = lg.hero ?: return
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val have = Badges.of(lg, h)
    // The closest next tier by badge points (among those his ratings allow).
    val next = Badge.entries.mapNotNull { b ->
        val t = Badges.tier(lg, h, b)
        if (t >= 4 || Badges.ratingTier(lg, h, b) + 1 <= t) return@mapNotNull null
        val x = h.badgeXp[b.name] ?: 0
        Triple(b, t, x.toFloat() / b.xp[t])
    }.maxByOrNull { it.third }
    Panel(onClick = { vm.openPlayer(h.id) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("YOUR BADGES · ${have.size}", style = T.label.copy(fontSize = 10.sp), color = P.accent)
                if (have.isEmpty()) Text("Earn them in games: made threes, rebounds, assists, stops.", style = T.small, color = P.sub)
            }
            Text("All ›", style = T.small, color = P.sub)
        }
        if (have.isNotEmpty()) { Spacer(Modifier.height(8.dp)); BadgeRow(lg, h, max = 11) }
        next?.let { (b, t, f) ->
            Text("Next: ${b.label} ${Badges.TIERS[t + 1]} · ${(h.badgeXp[b.name] ?: 0)}/${b.xp[t]} ${b.unit}", style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
            Box(Modifier.fillMaxWidth().padding(top = 4.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(P.line)) {
                Box(Modifier.fillMaxWidth(f.coerceIn(0f, 1f)).height(5.dp).clip(RoundedCornerShape(3.dp)).background(tierColor(t + 1)))
            }
        }
    }
}

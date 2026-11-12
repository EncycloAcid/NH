package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Moment
import com.nh.hoopscoach.game.MomentKind
import com.nh.hoopscoach.game.News

@Composable
fun momentColor(kind: MomentKind): Color = when (kind) {
    MomentKind.BUZZER, MomentKind.TITLE -> P.gold
    MomentKind.UPSET -> P.bad
    MomentKind.CINDERELLA -> P.accent
    MomentKind.FORCED_OT, MomentKind.CLASSIC -> P.warn
}

/** Tag for a headline in the news feed, if it's a special one. */
@Composable
fun NewsTag(n: News, modifier: Modifier = Modifier) {
    val regularTag = when {
        n.rivalry -> "RIVALRY" to P.bad
        n.kind == "upset" -> "UPSET ALERT" to P.bad
        n.kind == "buzzer" -> "BUZZER-BEATER" to P.gold
        n.kind == "moment" -> "MARCH MOMENT" to P.accent
        n.kind == "game" -> "BIG NIGHT" to P.gold
        n.kind == "recap" -> "GAME STORY" to P.accent
        n.kind == "series" -> "PLAYOFFS" to P.bad
        n.kind == "trade" -> "TRADE" to P.good
        n.kind == "column" -> "COLUMN" to P.sub
        n.kind == "team" -> "STREAK" to P.warn
        else -> null
    }
    if (!n.isAiGenerated && n.aiFallbackReason.isBlank() && regularTag == null) return

    Row(modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (regularTag != null) {
            Tag(regularTag.first, regularTag.second)
        }
        Spacer(Modifier.weight(1f))
        AiBadge(com.nh.hoopscoach.game.ArticleWriting.checked(n), n.source.substringAfterLast(" · ", ""), fallbackReason = n.aiFallbackReason)
    }
    Spacer(Modifier.height(3.dp))
}

/** One highlight: kind, where it happened, the headline, and the final with seeds. */
@Composable
fun MomentCard(vm: GameViewModel, lg: League, m: Moment, showSeason: Boolean = false) {
    val color = momentColor(m.kind)
    val live = m.season == lg.season && m.gameId >= 0 && lg.games.any { it.id == m.gameId }
    val base = Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(if (m.mine) P.accentSoft else P.surface)
    Row((if (live) base.clickable { vm.push(Route.Box(m.gameId)) } else base).height(IntrinsicSize.Min)) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(color))
        Column(Modifier.weight(1f).padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(m.kind.tag.uppercase(), color, filled = m.kind == MomentKind.BUZZER || m.kind == MomentKind.UPSET || m.kind == MomentKind.TITLE)
                Spacer(Modifier.width(8.dp))
                Text(
                    (if (showSeason) "${m.season} · " else "") + m.stage.removePrefix("NCAA ").removePrefix("· "),
                    style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(m.headline, style = T.head, color = P.ink)
            Spacer(Modifier.height(2.dp))
            Text(m.detail, style = T.small, color = P.sub)
            Spacer(Modifier.height(10.dp))
            ScoreLine(lg, m.winner, m.wSeed, m.wScore, true)
            Spacer(Modifier.height(4.dp))
            ScoreLine(lg, m.loser, m.lSeed, m.lScore, false)
        }
    }
}

@Composable
private fun ScoreLine(lg: League, teamId: Int, seed: Int, score: Int, won: Boolean) {
    val t = lg.team(teamId)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(if (seed > 0) "$seed" else "", style = T.small, color = P.faint, modifier = Modifier.width(22.dp))
        TeamBadge(t, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(t.school, style = T.bodyB.copy(fontSize = 14.sp), color = if (teamId == lg.userTeamId) P.accent else if (won) P.ink else P.sub, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$score", style = T.numSm.copy(fontSize = 17.sp), color = if (won) P.ink else P.faint)
    }
}

/** The latest day's big moments, for the tournament Today screen. */
@Composable
fun UpsetAlerts(vm: GameViewModel, lg: League) {
    val recent = lg.moments.filter { it.season == lg.season }
    val day = recent.maxOfOrNull { it.day } ?: return
    if (day < lg.day - 1) return
    val today = recent.filter { it.day == day }.sortedByDescending { it.weight }
    Section(if (today.any { it.kind == MomentKind.UPSET }) "Upset alerts" else "March moments", action = "Highlight reel", onAction = { vm.push(Route.History(HistoryKind.MOMENTS)) })
    today.take(4).forEach { m -> MomentCard(vm, lg, m); Spacer(Modifier.height(8.dp)) }
}

/** The user's own big moment from the last game, during the season. */
@Composable
fun LatestMyMoment(vm: GameViewModel, lg: League) {
    val m = lg.moments.lastOrNull { it.mine && it.season == lg.season } ?: return
    if (m.day < lg.day - 1) return
    Spacer(Modifier.height(8.dp))
    MomentCard(vm, lg, m)
}

/** Highlight reel screen: every season's big moments. */
@Composable
fun HighlightReel(vm: GameViewModel, lg: League, mineOnly: Boolean) {
    val seasons = lg.moments.map { it.season }.distinct().sortedDescending()
    if (seasons.isEmpty()) { Empty("No highlights yet. Upsets, buzzer-beaters and Cinderella runs land here every March."); return }
    for (s in seasons) {
        val list = lg.moments.filter { it.season == s && (!mineOnly || it.mine) }.sortedByDescending { it.weight }
        if (list.isEmpty()) continue
        Section("$s")
        list.forEach { m -> MomentCard(vm, lg, m); Spacer(Modifier.height(8.dp)) }
    }
}

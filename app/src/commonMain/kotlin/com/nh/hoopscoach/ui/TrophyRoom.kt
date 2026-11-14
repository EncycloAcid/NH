package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.GreatTeams
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Trophies

/** A row on a career screen that opens the trophy room. */
@Composable
fun TrophyRoomLink(vm: GameViewModel, lg: League) {
    val got = lg.achievements.size
    Spacer(Modifier.height(8.dp))
    Panel(onClick = { vm.push(Route.Trophies) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🏆", style = T.title.copy(fontSize = 26.sp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Trophy room", style = T.bodyB, color = P.ink)
                Text("$got of ${Trophies.ALL.size} achievements" + (lg.achievements.lastOrNull()?.let { a -> Trophies.def(a.id)?.let { " · latest: ${it.title}" } } ?: ""), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Chevron()
        }
    }
    // His season's best plays, right under the trophies.
    val plays = com.nh.hoopscoach.game.Highlights.mixtape(lg)
    if (plays.isNotEmpty()) {
        Spacer(Modifier.height(8.dp))
        Panel(onClick = { vm.push(Route.Mixtape) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🎬", style = T.title.copy(fontSize = 26.sp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Season mixtape", style = T.bodyB, color = P.ink)
                    Text("${plays.size} plays · best: ${plays.first().text}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Chevron()
            }
        }
    }
}

@Composable
fun TrophyRoomScreen(vm: GameViewModel, lg: League) {
    val got = lg.achievements.associateBy { it.id }
    val h = lg.hero?.takeIf { lg.road != null }
    val c = if (lg.road == null) runCatching { lg.userCoach }.getOrNull() else null
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Trophy room", "${got.size} of ${Trophies.ALL.size} achievements", onBack = { vm.back() }) }
        item {
            Panel(color = P.hero) {
                Text("THE TROPHY ROOM", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                Text("${got.size} / ${Trophies.ALL.size}", style = T.hero, color = P.onHero)
                val titles = (h?.awards?.count { it.endsWith(" Champion") } ?: 0) + (c?.history?.count { it.result == "Champion" || it.result == "National Champion" } ?: 0) +
                    (h?.career?.count { it.cls != com.nh.hoopscoach.game.PRO_YEAR && it.result == "National Champion" } ?: 0)
                Text(if (titles > 0) "$titles championship${if (titles == 1) "" else "s"} on the shelf." else "The shelf has room. Go fill it.", style = T.small, color = P.onHero.copy(alpha = 0.9f))
            }
        }
        // The banners.
        val banners = buildList {
            h?.awards?.filter { it.endsWith(" Champion") }?.forEach { add("${it.substringBefore(" ")} · NBA champion") }
            h?.career?.filter { it.cls != com.nh.hoopscoach.game.PRO_YEAR && it.result == "National Champion" }?.forEach { add("${it.season} · National champion with ${it.team}") }
            c?.history?.filter { it.result == "Champion" || it.result == "National Champion" }?.forEach { add("${it.season} · ${if (it.result == "Champion") "NBA" else "National"} champion, ${it.team} (${it.w}-${it.l})") }
        }
        if (banners.isNotEmpty()) item {
            Section("Banners")
            GroupList(banners) { b -> ListRow { Text("🏆", style = T.bodyB); Spacer(Modifier.width(10.dp)); Text(b, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f)) } }
        }
        if (h != null) item { TrophyCase(h) }
        if (lg.road == null) item { RivalryTrophyShelf(lg) }
        if (c != null && c.history.isNotEmpty()) item {
            Section("Coaching résumé")
            Panel {
                Text("${c.history.sumOf { it.w }}-${c.history.sumOf { it.l }} over ${c.history.size} seasons", style = T.bodyB, color = P.ink)
                val ff = c.history.count { it.result in setOf("Final Four", "Runner-Up", "National Champion") }
                Text(listOfNotNull(
                    ff.takeIf { it > 0 }?.let { "$it Final Four${if (it == 1) "" else "s"}" },
                    c.natCoty.takeIf { it > 0 }?.let { "$it× National Coach of the Year" },
                    c.confTitles.takeIf { it > 0 }?.let { "$it conference title${if (it == 1) "" else "s"}" },
                    c.hofSeason.takeIf { it > 0 }?.let { "Hall of Fame, class of $it" },
                ).joinToString(" · ").ifEmpty { "Your first banner is out there." }, style = T.small, color = P.sub)
            }
        }
        // The achievements, by part of the career.
        val showPlayer = lg.road != null || lg.playedAs != null || got.keys.any { id -> Trophies.def(id)?.group == "Player" }
        val showCoach = lg.road == null || got.keys.any { id -> Trophies.def(id)?.group == "Coach" }
        for (group in listOf("Player", "Coach")) {
            if ((group == "Player" && !showPlayer) || (group == "Coach" && !showCoach)) continue
            val defs = Trophies.ALL.filter { it.group == group }
            item {
                Section("$group achievements · ${defs.count { it.id in got }} of ${defs.size}")
                GroupList(defs.sortedBy { if (it.id in got) 0 else 1 }, inset = 60.dp) { d ->
                    val a = got[d.id]
                    ListRow {
                        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (a != null) P.gold.copy(alpha = 0.22f) else P.surface2), contentAlignment = Alignment.Center) {
                            Text(if (a != null) "★" else "🔒", style = T.bodyB, color = if (a != null) P.gold else P.faint)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.title, style = T.bodyB, color = if (a != null) P.ink else P.sub)
                            Text(if (a != null) listOf("${a.season}", a.detail).filter { it.isNotEmpty() }.joinToString(" · ") else d.how, style = T.small, color = P.sub)
                        }
                    }
                }
            }
        }
        // His best nights.
        val reel = lg.road?.reel?.filter { it.big }?.takeLast(8)?.reversed().orEmpty()
        if (reel.isNotEmpty()) item {
            Section("Best nights")
            GroupList(reel) { e -> ListRow { Column(Modifier.weight(1f)) { Text(e.title, style = T.bodyB, color = P.ink); Text("${e.season} · ${e.line}", style = T.small, color = P.sub) } } }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** The greatest champions ever, rated on one scale. */
internal fun LazyListScope.greatestTeams(vm: GameViewModel, lg: League) {
    val list = GreatTeams.list(lg)
    item {
        Text(
            "Every champion on one scale: regular-season win rate, scoring margin and the playoff run." +
                (if (lg.pro) " Real champions since 1980 are rated from their records (margins estimated, marked ~)." else ""),
            style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
        )
    }
    if (list.isEmpty()) { item { Empty("No champions yet. Finish a season.") }; return }
    val me = if (lg.road == null) runCatching { lg.userCoach.name }.getOrNull() else null
    val hero = lg.hero?.takeIf { lg.road != null }
    item {
        GroupList(list.take(30).withIndex().toList(), inset = 60.dp) { (i, r) ->
            val t = lg.teams.getOrNull(r.teamId)
            val mine = !r.real && ((me != null && r.coach == me) || (hero != null && hero.awards.contains("${r.season} Champion") && r.teamId == hero.lastTeam.takeIf { it >= 0 } ?: r.teamId))
            ListRow(onClick = t?.let { { vm.push(Route.TeamPage(it.id)) } }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
                Text("${i + 1}", style = T.numSm.copy(fontSize = 16.sp), color = if (i < 3) P.gold else P.faint, modifier = Modifier.width(28.dp))
                if (t != null) TeamBadge(t, 34.dp) else Spacer(Modifier.width(34.dp))
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${r.season} ${r.name}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (mine) { Spacer(Modifier.width(6.dp)); Tag("YOURS", P.accent, filled = true) }
                    }
                    Text(
                        "${r.w}-${r.l} · ${if (r.estimated) "~" else ""}${if (r.margin >= 0) "+" else ""}${"%.1f".format(r.margin)} margin" +
                            (if (r.pw + r.pl > 0) " · ${r.pw}-${r.pl} playoffs" else "") + (if (r.coach.isNotEmpty()) " · ${r.coach}" else ""),
                        style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(GreatTeams.scoreText(r), style = T.numSm, color = P.ink)
            }
        }
    }
}

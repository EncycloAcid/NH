package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.SeasonCalendar
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

/** What's on one date: the user's game (if any), the game day it is, and dated markers. */
private class DayInfo(val date: LocalDate, val day: Int?, val game: Game?, val markers: List<SeasonCalendar.Marker>)

private fun monthName(m: YearMonth) = m.month.getDisplayName(TextStyle.FULL, Locale.US) + " " + m.year

/**
 * The season as a calendar: a month at a time, the user's games on their dates (rivalries marked, wins
 * and losses once played), the tournaments, Selection Sunday, signing day and the draft. Tap a date to
 * see it, and sim straight to it.
 */
@Composable
fun CalendarScreen(vm: GameViewModel, lg: League) {
    val season = lg.season
    val u = lg.user
    val months = remember(season) { SeasonCalendar.months(season) }
    val today = remember(season, lg.day, lg.phase) {
        if (lg.phase == Phase.OFFSEASON || lg.phase == Phase.WRAP_UP) SeasonCalendar.dateOf(season, Schedule.LAST_DAY).plusDays(1)
        else SeasonCalendar.dateOf(season, lg.day.coerceIn(1, Schedule.LAST_DAY))
    }
    var month by remember(season) { mutableStateOf(months.indexOfFirst { YearMonth.from(today) == it }.coerceAtLeast(0)) }
    var picked by remember(season) { mutableStateOf<LocalDate?>(today) }
    val games = remember(season, vm.rev) {
        Schedule.teamGames(lg, u.id).groupBy { SeasonCalendar.dateOf(season, it.day) }.mapValues { it.value.first() }
    }
    val markers = remember(season) { SeasonCalendar.markers(season).groupBy { it.date } }
    fun info(d: LocalDate) = DayInfo(d, SeasonCalendar.dayOn(season, d), games[d], markers[d].orEmpty())

    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Calendar", "${season - 1}-${(season % 100).toString().padStart(2, '0')} season · ${u.school}", onBack = { vm.back() }) }
        item {
            val ym = months[month]
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                SmallButton("‹", enabled = month > 0) { month-- }
                Text(monthName(ym), style = T.head, color = P.ink, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                SmallButton("›", enabled = month < months.lastIndex) { month++ }
            }
            Panel(padding = androidx.compose.foundation.layout.PaddingValues(8.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in listOf("S", "M", "T", "W", "T", "F", "S")) Text(d, style = T.label, color = P.faint, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(4.dp))
                val first = ym.atDay(1)
                val lead = first.dayOfWeek.value % 7                    // Sunday first
                val cells = lead + ym.lengthOfMonth()
                for (week in 0 until (cells + 6) / 7) {
                    Row(Modifier.fillMaxWidth()) {
                        for (dow in 0..6) {
                            val n = week * 7 + dow - lead + 1
                            Box(Modifier.weight(1f).aspectRatio(0.78f).padding(1.5.dp)) {
                                if (n in 1..ym.lengthOfMonth()) {
                                    val d = ym.atDay(n)
                                    DayCell(lg, info(d), d == today, d == picked) { picked = d }
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Legend(P.good, "Win"); Legend(P.bad, "Loss / rivalry"); Legend(P.gold, "Postseason"); Legend(P.accent, "Key date")
            }
        }
        picked?.let { d ->
            item(key = "picked") {
                val i = info(d)
                Section(d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.US) + ", " + d.month.getDisplayName(TextStyle.SHORT, Locale.US) + " ${d.dayOfMonth}")
                Panel {
                    var any = false
                    i.game?.let { g -> any = true; GameLine(vm, lg, g) }
                    if (i.game == null) i.day?.let { day -> SeasonCalendar.roundName(day)?.let { any = true; Text(it, style = T.bodyB, color = P.gold) } }
                    i.markers.forEach { m -> any = true; Text(m.label, style = T.bodyB, color = P.accent, modifier = Modifier.padding(top = 4.dp)) }
                    if (!any) Text(if (i.day != null) "No game for ${u.school} today." else "No games on this date.", style = T.body, color = P.sub)
                    val target = i.day
                    val canSim = target != null && target > lg.day && (lg.phase == Phase.REGULAR || lg.phase == Phase.CONF_TOURNEY || lg.phase == Phase.NCAA) && vm.busy == null
                    if (canSim && target != null) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryButton("Sim to this day", Modifier.fillMaxWidth()) {
                            vm.simUntil("Simming to ${d.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${d.dayOfMonth}", target - lg.day) { it.day >= target }
                        }
                        Text("Plays every game before it and stops on the morning of the game.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
        }
        item {
            val ym = months[month]
            val inMonth = games.entries.filter { YearMonth.from(it.key) == ym }.sortedBy { it.key }
            if (inMonth.isNotEmpty()) {
                Section("${ym.month.getDisplayName(TextStyle.FULL, Locale.US)} schedule · ${inMonth.size} game${if (inMonth.size == 1) "" else "s"}")
                GroupList(inMonth) { (d, g) ->
                    ListRow(onClick = { picked = d }) {
                        Text("${d.month.getDisplayName(TextStyle.SHORT, Locale.US)} ${d.dayOfMonth}", style = T.small, color = P.sub, modifier = Modifier.width(54.dp))
                        GameLine(vm, lg, g, compact = true)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Legend(c: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(c))
        Spacer(Modifier.width(4.dp))
        Text(label, style = T.small.copy(fontSize = 11.sp), color = P.sub)
    }
}

@Composable
private fun DayCell(lg: League, i: DayInfo, today: Boolean, picked: Boolean, onClick: () -> Unit) {
    val u = lg.user
    val g = i.game
    val rival = g != null && lg.rivalry(g) != null
    val post = i.day != null && i.day >= Schedule.CT_START
    val result = g?.takeIf { it.played }?.let { (if (it.home == u.id) it.hs > it.aws else it.aws > it.hs) }
    val border = when {
        picked -> P.ink
        today -> P.accent
        else -> Color.Transparent
    }
    Column(
        Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp))
            .background(if (g != null) P.surface2 else Color.Transparent)
            .border(if (picked || today) 2.dp else 0.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick).padding(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("${i.date.dayOfMonth}", style = T.small.copy(fontSize = 11.sp, lineHeight = 13.sp), color = if (today) P.accent else P.sub)
        if (g != null) {
            val opp = lg.team(g.opp(u.id))
            Box(contentAlignment = Alignment.Center) {
                TeamBadge(opp, 24.dp)
            }
            Text(
                when (result) { true -> "W"; false -> "L"; null -> if (g.neutral) "N" else if (g.home == u.id) "vs" else "@" },
                style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.sp),
                color = when (result) { true -> P.good; false -> P.bad; null -> if (rival) P.bad else P.sub },
            )
        } else if (i.markers.isNotEmpty()) {
            Box(Modifier.padding(top = 4.dp).size(7.dp).clip(CircleShape).background(P.accent))
        } else if (post) {
            Box(Modifier.padding(top = 4.dp).size(7.dp).clip(CircleShape).background(P.gold))
        }
        if (rival && result == null) Box(Modifier.padding(top = 1.dp).width(14.dp).height(2.dp).background(P.bad))
    }
}

/** One game: opponent, where, rivalry, and the result or the odds. */
@Composable
private fun GameLine(vm: GameViewModel, lg: League, g: Game, compact: Boolean = false) {
    val u = lg.user
    val opp = lg.team(g.opp(u.id))
    val r = lg.rivalry(g)
    Row(Modifier.fillMaxWidth().clickable { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(opp.id)) }, verticalAlignment = Alignment.CenterVertically) {
        TeamBadge(opp, if (compact) 26.dp else 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("${if (g.neutral) "vs" else if (g.home == u.id) "vs" else "at"} ${opp.school}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val bits = listOfNotNull(
                r?.title?.let { "Rivalry: $it" },
                g.label.takeIf { it.isNotBlank() }?.substringBefore(" ·"),
                if (g.neutral) "Neutral site" else null,
            )
            if (bits.isNotEmpty()) Text(bits.joinToString(" · "), style = T.small, color = if (r != null) P.bad else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (g.played) {
            val mine = if (g.home == u.id) g.hs else g.aws
            val theirs = if (g.home == u.id) g.aws else g.hs
            Text("${if (mine > theirs) "W" else "L"} $mine-$theirs", style = T.numSm, color = if (mine > theirs) P.good else P.bad)
        } else {
            val p = Rankings.winProbFor(lg, g, u.id)
            Text("${(p * 100).toInt()}%", style = T.numSm, color = P.sub)
        }
    }
}

/**
 * Records: single games (national only), best seasons and careers. With [teamId], one program's own
 * records; [n] marks per category.
 */
@Composable
fun RecordBookView(vm: GameViewModel, lg: League, teamId: Int?, n: Int = 5) {
    val tabs = if (teamId == null) listOf("Single game", "Season", "Career") else listOf("Season", "Career")
    var tab by remember(teamId) { mutableStateOf(if (teamId == null) 1 else 0) }
    val kind = tabs[tab.coerceIn(0, tabs.lastIndex)]
    val cats = remember(teamId, kind, vm.rev) {
        when (kind) {
            "Season" -> com.nh.hoopscoach.game.StatRecords.seasons(lg, teamId, n)
            "Career" -> com.nh.hoopscoach.game.StatRecords.careers(lg, teamId, n)
            else -> emptyList()
        }
    }
    Spacer(Modifier.height(8.dp))
    Segmented(tabs, tab, { tab = it })
    if (kind == "Single game") {
        Spacer(Modifier.height(8.dp))
        GroupList(lg.records) { r ->
            ListRow {
                Column(Modifier.weight(1f)) {
                    Text(r.label, style = T.bodyB, color = P.ink)
                    Text(if (r.holder.isEmpty()) "Not set yet" else "${r.holder} · ${r.detail}", style = T.small, color = P.sub)
                }
                Text("${r.value}", style = T.num, color = P.accent)
            }
        }
        return
    }
    if (cats.all { it.marks.isEmpty() }) {
        Spacer(Modifier.height(8.dp))
        Panel { Empty("No records yet. Play some games and they'll fill in.") }
        return
    }
    for (c in cats) {
        if (c.marks.isEmpty()) continue
        Section(c.label)
        GroupList(c.marks.withIndex().toList()) { (i, m) ->
            ListRow(onClick = { vm.openPlayer(m.pid) }) {
                Text("${i + 1}", style = T.numSm, color = if (i == 0) P.gold else P.faint, modifier = Modifier.width(26.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.holder, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(m.detail, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(m.shown, style = T.numSm, color = if (i == 0) P.accent else P.ink)
            }
        }
    }
}

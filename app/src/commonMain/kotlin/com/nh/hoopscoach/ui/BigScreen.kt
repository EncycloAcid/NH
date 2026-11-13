package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Season

/**
 * The big-screen layout (a PC window, a tablet in landscape): the same screens as the phone, with the
 * tabs and the next game in a sidebar on the left, the screen itself kept to a readable width in the
 * middle, and, when there's room, the day's headlines and the rankings on the right.
 */
object BigScreen {
    /** From this width the sidebar replaces the bottom bar. */
    val WIDE = 900.dp
    /** From this width the league panel opens on the right. */
    val WIDEST = 1320.dp
    /** The widest a screen's own content gets. */
    val CONTENT = 760.dp
    /** The widest a form (new career, loading) gets. */
    val FORM = 560.dp
    val SIDEBAR = 288.dp
    val PANEL = 340.dp
}

/** A tab's name as this kind of career calls it. */
@Composable
internal fun tabLabel(vm: GameViewModel, t: Tab): String =
    if (t == Tab.RECRUIT && vm.league?.let { com.nh.hoopscoach.game.NbaCoach.active(it) } == true) "Trades"
    else if (t == Tab.RECRUIT && vm.league?.playerMode == true) "Me" else t.label

@Composable
internal fun BigScreenShell(vm: GameViewModel, lg: League, wide: Boolean) {
    Row(Modifier.fillMaxSize().background(P.bg)) {
        Sidebar(vm, lg)
        Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = BigScreen.CONTENT).fillMaxHeight()) {
                Screens(vm, lg, Modifier.weight(1f).fillMaxWidth())
                // The next game and the sim buttons under the screen, as on the phone, with room to read.
                val flowOnScreen = vm.tab == Tab.TODAY && (lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON)
                if (vm.stack.isEmpty() && !flowOnScreen) Box(Modifier.padding(bottom = 8.dp)) { if (lg.pro) ProContinueBar(vm, lg) else ContinueBar(vm, lg) }
            }
        }
        if (wide) LeaguePanel(vm, lg)
    }
}

@Composable
private fun Sidebar(vm: GameViewModel, lg: League) {
    val u = lg.user
    Column(Modifier.width(BigScreen.SIDEBAR).fillMaxHeight().background(P.surface).padding(vertical = 18.dp)) {
        Text("HOOPS DYNASTY", style = T.label, color = P.accent, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { vm.push(Route.TeamPage(u.id)) }.padding(horizontal = 18.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(u, 46.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (lg.pro) u.name else u.school, style = T.head, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${lg.season} · ${u.w}-${u.l}" + (if (!lg.pro && u.ap in 1..25) " · No. ${u.ap}" else ""), style = T.small, color = P.sub, maxLines = 1)
                Text(Season.statusLine(lg), style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.height(16.dp))
        for (t in Tab.entries) {
            val sel = vm.tab == t && vm.stack.isEmpty()
            val icon = when (t) { Tab.TODAY -> NavIcons.Today; Tab.TEAM -> NavIcons.Team; Tab.RECRUIT -> NavIcons.Recruit; Tab.LEAGUE -> NavIcons.League; Tab.CAREER -> NavIcons.Career }
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 2.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(if (sel) P.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { vm.stack.clear(); vm.select(t) }.padding(horizontal = 14.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (sel) P.accent else P.sub, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(14.dp))
                Text(tabLabel(vm, t), style = T.bodyB.copy(fontSize = 15.sp), color = if (sel) P.ink else P.sub)
            }
        }
        Spacer(Modifier.weight(1f))
        if (vm.stack.isNotEmpty()) Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.back() }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("← Back  (Esc)", style = T.bodyB.copy(fontSize = 14.sp), color = P.accent)
        }
        Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { vm.push(Route.Settings) }.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("Settings & saves", style = T.bodyB.copy(fontSize = 14.sp), color = P.sub)
        }
    }
}

/** The league at a glance: today's headlines and the rankings, each a click from its story or team. */
@Composable
private fun LeaguePanel(vm: GameViewModel, lg: League) {
    Column(Modifier.width(BigScreen.PANEL).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 6.dp, bottom = 18.dp)) {
        val news = lg.news.filter { it.season == lg.season }.takeLast(8).reversed()
        Section("Headlines", action = "All news", onAction = { vm.push(Route.News) })
        if (news.isEmpty()) Empty("No stories yet this season.")
        else GroupList(news, inset = 34.dp) { n -> HeadlineRow(vm, lg, n) }
        val ranked = if (lg.pro) lg.teams.sortedByDescending { it.winPct }.take(10)
            else lg.teams.filter { it.ap in 1..25 }.sortedBy { it.ap }.take(15)
        Section(if (lg.pro) "Best records" else "AP Top 25", action = "League", onAction = { vm.stack.clear(); vm.select(Tab.LEAGUE) })
        if (ranked.isEmpty()) Empty("Rankings come out once games are played.")
        else GroupList(ranked) { t ->
            ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                Text(if (lg.pro) "${ranked.indexOf(t) + 1}" else "${t.ap}", style = T.numSm, color = if (t.id == lg.userTeamId) P.accent else P.sub, modifier = Modifier.width(30.dp))
                TeamBadge(t, 28.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (lg.pro) t.name else t.school, style = T.bodyB, color = if (t.id == lg.userTeamId) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text("${t.w}-${t.l}", style = T.small, color = P.sub)
            }
        }
    }
}

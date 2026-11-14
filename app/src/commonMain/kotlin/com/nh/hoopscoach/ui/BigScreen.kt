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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
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
    val CONTENT = 960.dp
    /** The widest a form (new career, loading) gets. */
    val FORM = 560.dp
    val SIDEBAR = 252.dp
    val PANEL = 360.dp
}

/** Line icons for the sidebar's extra links, drawn like the tab icons. */
private object SideIcons {
    private fun line(name: String, d: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    val Explore = line("explore", "M12 21a9 9 0 1 0 0-18 9 9 0 0 0 0 18z M15.5 8.5l-2 5-5 2 2-5z")
    val News = line("news", "M5 4.5h11.5v15H6.5A1.5 1.5 0 0 1 5 18z M16.5 8.5H19v9.5a1.5 1.5 0 0 1-3 0 M8 8h5.5 M8 11.5h5.5 M8 15h3.5")
    val Back = line("back", "M14.5 6l-6 6 6 6")
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
                // On a big screen the next game sits at the top, where the eye starts, instead of at the
                // bottom edge of a tall window.
                val flowOnScreen = vm.tab == Tab.TODAY && (lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON)
                if (vm.stack.isEmpty() && !flowOnScreen) Box(Modifier.padding(top = 10.dp, start = 6.dp, end = 6.dp)) { if (lg.pro) ProContinueBar(vm, lg) else ContinueBar(vm, lg) }
                Screens(vm, lg, Modifier.weight(1f).fillMaxWidth())
            }
        }
        if (wide) LeaguePanel(vm, lg)
    }
}

/** A sidebar row: icon, label, and on a PC the key that opens it. */
@Composable
private fun NavItem(icon: ImageVector, label: String, selected: Boolean = false, key: String? = null, color: Color = P.sub, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 10.dp, vertical = 1.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) P.accentSoft else Color.Transparent)
            .hoverClick(onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (selected) P.accent else color, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(13.dp))
        Text(label, style = T.bodyB.copy(fontSize = 14.5.sp), color = if (selected) P.ink else color, modifier = Modifier.weight(1f), maxLines = 1)
        if (key != null) KeyCap(key)
    }
}

/** A keyboard key, small and quiet. */
@Composable
private fun KeyCap(key: String) {
    Box(Modifier.clip(RoundedCornerShape(5.dp)).border(1.dp, P.line, RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
        Text(key, style = T.small.copy(fontSize = 10.5.sp), color = P.faint, maxLines = 1)
    }
}

@Composable
private fun SideHeading(text: String) =
    Text(text.uppercase(), style = T.label.copy(fontSize = 10.5.sp), color = P.faint, modifier = Modifier.padding(start = 24.dp, top = 14.dp, bottom = 6.dp))

@Composable
private fun Sidebar(vm: GameViewModel, lg: League) {
    val u = lg.user
    val pc = com.nh.hoopscoach.SaveTransfer.host != null
    Column(Modifier.width(BigScreen.SIDEBAR).fillMaxHeight().background(P.surface).verticalScroll(rememberScrollState()).padding(vertical = 18.dp)) {
        Text("HOOPS DYNASTY", style = T.label, color = P.accent, modifier = Modifier.padding(horizontal = 22.dp))
        Spacer(Modifier.height(12.dp))
        // The team card: badge, record and where the season stands.
        Column(Modifier.padding(horizontal = 10.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(P.bg.copy(alpha = if (P.dark) 0.6f else 0.7f))
            .hoverClick({ vm.push(Route.TeamPage(u.id)) }).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamBadge(u, 42.dp)
                Spacer(Modifier.width(11.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (lg.pro) u.name else u.school, style = T.head.copy(fontSize = 16.sp), color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(Season.statusLine(lg), style = T.small, color = P.faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth()) {
                MiniStat("Record", "${u.w}-${u.l}", Modifier.weight(1f))
                if (!lg.pro) MiniStat("Conf", u.confRecord, Modifier.weight(1f))
                MiniStat(if (lg.pro) "Season" else "Rank", if (lg.pro) "${lg.season}" else if (u.ap in 1..25) "No. ${u.ap}" else "NR", Modifier.weight(1f),
                    color = if (!lg.pro && u.ap in 1..25) P.accent else P.ink)
            }
        }
        SideHeading("Play")
        Tab.entries.forEachIndexed { i, t ->
            val icon = when (t) { Tab.TODAY -> NavIcons.Today; Tab.TEAM -> NavIcons.Team; Tab.RECRUIT -> NavIcons.Recruit; Tab.LEAGUE -> NavIcons.League; Tab.CAREER -> NavIcons.Career }
            NavItem(icon, tabLabel(vm, t), selected = vm.tab == t && vm.stack.isEmpty(), key = if (pc) "${i + 1}" else null) { vm.stack.clear(); vm.select(t) }
        }
        SideHeading("More")
        if (!lg.pro) NavItem(MenuIcons.Calendar, "Calendar", selected = vm.stack.lastOrNull() == Route.Calendar) { vm.push(Route.Calendar) }
        NavItem(SideIcons.News, "News", selected = vm.stack.lastOrNull() == Route.News) { vm.push(Route.News) }
        NavItem(SideIcons.Explore, "Explore", selected = vm.stack.lastOrNull() == Route.Explore) { vm.push(Route.Explore) }
        NavItem(NavIcons.Gear, "Settings & saves", selected = vm.stack.lastOrNull() == Route.Settings) { vm.push(Route.Settings) }
        Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
        if (vm.stack.isNotEmpty()) NavItem(SideIcons.Back, "Back", key = if (pc) "Esc" else null, color = P.accent) { vm.back() }
        if (pc) Text("Space plays or continues · S sims a day · F1 all keys", style = T.small.copy(fontSize = 11.sp), color = P.faint,
            modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 8.dp))
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier, color: Color = P.ink) {
    Column(modifier) {
        Text(value, style = T.numSm, color = color, maxLines = 1)
        Text(label.uppercase(), style = T.label.copy(fontSize = 9.5.sp), color = P.faint, maxLines = 1)
    }
}

/**
 * The league at a glance, in three tabs so nothing gets cut off: today's headlines, the rankings, and
 * the user's conference standings, each row a click from its story or team.
 */
@Composable
private fun LeaguePanel(vm: GameViewModel, lg: League) {
    var tab by androidx.compose.runtime.saveable.rememberSaveable { mutableIntStateOf(0) }
    Column(Modifier.width(BigScreen.PANEL).fillMaxHeight().padding(top = 10.dp)) {
        Segmented(listOf("News", if (lg.pro) "Best records" else "Top 25", "Standings"), tab, { tab = it })
        Spacer(Modifier.height(4.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(bottom = 18.dp)) {
            when (tab) {
                0 -> {
                    val news = lg.news.filter { it.season == lg.season }.takeLast(14).reversed()
                    Section("Headlines", action = "All news", onAction = { vm.push(Route.News) })
                    if (news.isEmpty()) Empty("No stories yet this season.")
                    else GroupList(news, inset = 34.dp) { n -> HeadlineRow(vm, lg, n) }
                }
                1 -> {
                    val ranked = if (lg.pro) lg.teams.sortedByDescending { it.winPct }.take(25)
                        else lg.teams.filter { it.ap in 1..25 }.sortedBy { it.ap }
                    Section(if (lg.pro) "Best records" else "AP Top 25", action = "League", onAction = { vm.stack.clear(); vm.select(Tab.LEAGUE) })
                    if (ranked.isEmpty()) Empty("Rankings come out once games are played.")
                    else GroupList(ranked) { t -> TeamLine(vm, lg, if (lg.pro) "${ranked.indexOf(t) + 1}" else "${t.ap}", t, "${t.w}-${t.l}") }
                }
                else -> {
                    val u = lg.user
                    val table = if (lg.pro) com.nh.hoopscoach.game.ProSeason.standings(lg, u.confId) else Season.standings(lg, u.confId)
                    val confName = runCatching { if (lg.pro) "Conference" else lg.conf(u.confId).name }.getOrDefault("Conference")
                    Section(confName, action = "League", onAction = { vm.stack.clear(); vm.select(Tab.LEAGUE) })
                    if (table.isEmpty()) Empty("No standings yet.")
                    else GroupList(table) { t ->
                        TeamLine(vm, lg, "${table.indexOf(t) + 1}", t, if (lg.pro) "${t.w}-${t.l}" else "${t.cw}-${t.cl}", sub = if (lg.pro) null else "${t.w}-${t.l}")
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamLine(vm: GameViewModel, lg: League, rank: String, t: com.nh.hoopscoach.game.Team, record: String, sub: String? = null) {
    val me = t.id == lg.userTeamId
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (me) Modifier.background(P.accentSoft.copy(alpha = 0.5f)) else Modifier) {
        Text(rank, style = T.numSm, color = if (me) P.accent else P.sub, modifier = Modifier.width(30.dp))
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(if (lg.pro) t.name else t.school, style = T.bodyB, color = if (me) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(record, style = T.bodyB.copy(fontSize = 14.sp), color = P.ink)
            if (sub != null) Text(sub, style = T.small, color = P.faint)
        }
    }
}

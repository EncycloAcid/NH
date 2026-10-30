package com.nh.hoopscoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import kotlinx.coroutines.delay

object NavIcons {
    private fun line(name: String, d: String): ImageVector = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
        addPath(addPathNodes(d), fill = null, stroke = SolidColor(Color.Black), strokeLineWidth = 1.9f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round)
    }.build()

    val Today = line("today", "M4 6.5A1.5 1.5 0 0 1 5.5 5h13A1.5 1.5 0 0 1 20 6.5v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5z M4 10h16 M8 3v4 M16 3v4")
    val Team = line("team", "M8.5 3.5c1.2 1.8 5.8 1.8 7 0l3.2 1.4c-.4 2.6.3 4.1 1.6 5L18.6 20.5H5.4L3.7 9.9C5 9 5.7 7.5 5.3 4.9z")
    val Recruit = line("recruit", "M12 3.5l2.6 5.3 5.9.9-4.3 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8L3.5 9.7l5.9-.9z")
    val League = line("league", "M8 4h8v5a4 4 0 0 1-8 0z M8 5.5H5.5a3 3 0 0 0 2.8 4 M16 5.5h2.5a3 3 0 0 1-2.8 4 M12 13v3.5 M8.5 20h7 M10 16.5h4V20h-4z")
    val Career = line("career", "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8z M4.5 20c.8-3.6 3.8-5.5 7.5-5.5s6.7 1.9 7.5 5.5")
    val Gear = line("gear", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6z M19.4 13.5l1.6 1.2-2 3.4-1.9-.7a7.6 7.6 0 0 1-2.1 1.2l-.3 2H10.3l-.3-2a7.6 7.6 0 0 1-2.1-1.2l-1.9.7-2-3.4 1.6-1.2a7.7 7.7 0 0 1 0-3l-1.6-1.2 2-3.4 1.9.7A7.6 7.6 0 0 1 10 4.2l.3-2h3.4l.3 2a7.6 7.6 0 0 1 2.1 1.2l1.9-.7 2 3.4-1.6 1.2a7.7 7.7 0 0 1 0 3z")
}

@Composable
fun App(vm: GameViewModel) {
    val lg = vm.league
    val user = lg?.user
    HoopsTheme(user?.let { Color(it.primary) }, user?.let { Color(it.secondary) }) {
        Box(Modifier.fillMaxSize().background(P.bg)) {
            val busy = vm.busy
            when {
                vm.loading -> BusyScreen("Loading")
                busy != null -> BusyScreen(busy)
                lg == null -> NewGameScreen(vm)
                else -> Shell(vm, lg)
            }
            Toast(vm)
        }
    }
}

@Composable
private fun Shell(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    BackHandler(enabled = vm.sheet != null || vm.stack.isNotEmpty()) { vm.back() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val route = vm.stack.lastOrNull()
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (route == null) {
                when (vm.tab) {
                    Tab.TODAY -> TodayScreen(vm, lg)
                    Tab.TEAM -> TeamScreen(vm, lg)
                    Tab.RECRUIT -> RecruitScreen(vm, lg)
                    Tab.LEAGUE -> LeagueScreen(vm, lg)
                    Tab.CAREER -> CareerScreen(vm, lg)
                }
            } else RouteScreen(vm, lg, route)
        }
        if (route == null) {
            val flowOnScreen = vm.tab == Tab.TODAY && (lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON)
            if (!flowOnScreen) ContinueBar(vm, lg)
            NavBar(vm)
        } else Spacer(Modifier.navigationBarsPadding())
    }
    vm.sheet?.let { pid -> lg.anyPlayer(pid)?.let { PlayerSheet(vm, lg, it) } ?: run { vm.sheet = null } }
}

@Composable
private fun RouteScreen(vm: GameViewModel, lg: League, r: Route) {
    when (r) {
        is Route.Live -> LiveScreen(vm, lg)
        is Route.Box -> BoxScoreScreen(vm, lg, r.gameId)
        is Route.TeamPage -> TeamPage(vm, lg, r.teamId)
        is Route.BracketView -> BracketScreen(vm, lg, r.confId)
        is Route.Schedule -> ScheduleScreen(vm, lg, r.teamId)
        is Route.History -> HistoryScreen(vm, lg, r.kind)
        is Route.Settings -> SettingsScreen(vm, lg)
        is Route.EditTeam -> EditTeamScreen(vm, lg, r.teamId)
        is Route.Focus -> FocusScreen(vm, lg, r.playerId)
        is Route.RecruitView -> RecruitDetail(vm, lg, r.playerId)
        is Route.Leaders -> LeadersScreen(vm, lg)
        is Route.CoachDev -> CoachDevScreen(vm, lg)
        is Route.WrapUp -> WrapUpFlow(vm, lg)
        is Route.Preview -> PreviewScreen(vm, lg)
    }
}

// ---------- continue bar ----------

@Composable
private fun ContinueBar(vm: GameViewModel, lg: League) {
    val u = lg.user
    val game = Season.userGameToday(lg)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).shadow(10.dp, RoundedCornerShape(18.dp), clip = false)
            .clip(RoundedCornerShape(18.dp)).background(P.surface).padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (lg.phase) {
            Phase.WRAP_UP, Phase.OFFSEASON -> {
                Column(Modifier.weight(1f)) {
                    Text(if (lg.phase == Phase.WRAP_UP) "SEASON COMPLETE" else "OFFSEASON", style = T.label, color = P.accent)
                    Text(if (lg.phase == Phase.WRAP_UP) "Review the ${lg.season} season" else "Pick up where you left off", style = T.bodyB, color = P.ink, maxLines = 1)
                }
                SmallButton("Continue", primary = true) { vm.select(Tab.TODAY) }
            }
            else -> {
                Column(Modifier.weight(1f)) {
                    if (game != null) {
                        val opp = lg.team(game.opp(u.id))
                        val where = when { game.neutral -> "NEUTRAL"; game.home == u.id -> "HOME"; else -> "AWAY" }
                        val tag = if (game.day <= Schedule.REG_DAYS) "GAME ${u.gp + 1}" else game.label.substringAfter("· ").uppercase()
                        val rival = lg.rivalry(game) != null
                        Text("$tag · ${if (rival) "RIVALRY · " else ""}$where", style = T.label, color = if (rival) P.bad else P.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${if (game.home == u.id || game.neutral) "vs" else "at"} ${teamLabel(opp)}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    } else {
                        Text(Season.statusLine(lg).uppercase(), style = T.label, color = P.accent, maxLines = 1)
                        Text(if (lg.phase == Phase.REGULAR) "No game today" else "Your team is off", style = T.bodyB, color = P.ink, maxLines = 1)
                    }
                }
                Box {
                    Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, "More sim options", tint = P.sub)
                    }
                    DropdownMenu(menu, { menu = false }, Modifier.background(P.surface)) {
                        @Composable
                        fun item(label: String, a: () -> Unit) = DropdownMenuItem(text = { Text(label, style = T.body, color = P.ink) }, onClick = { menu = false; a() })
                        if (lg.phase == Phase.REGULAR) {
                            item("Sim one week") { vm.simDays(7) }
                            item("Sim to conference tournament") { vm.simToPhase(Phase.CONF_TOURNEY, "Simulating the regular season") }
                        }
                        if (lg.phase != Phase.NCAA) item("Sim to Selection Sunday") { vm.simToPhase(Phase.NCAA, "Simulating to Selection Sunday") }
                        item("Sim to end of season") { vm.simToPhase(Phase.WRAP_UP, "Simulating the rest of the season") }
                    }
                }
                Spacer(Modifier.width(4.dp))
                if (game != null) {
                    SmallButton("Sim") { vm.simDay() }
                    Spacer(Modifier.width(8.dp))
                    SmallButton("Play", primary = true) { vm.startLive() }
                } else SmallButton(if (lg.phase == Phase.REGULAR) "Next day" else "Sim round", primary = true) { vm.simDay() }
            }
        }
    }
}

// ---------- bottom navigation ----------

@Composable
private fun NavBar(vm: GameViewModel) {
    Column(Modifier.fillMaxWidth().background(P.bg)) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 4.dp, bottom = 6.dp)) {
            for (t in Tab.entries) {
                val icon = when (t) { Tab.TODAY -> NavIcons.Today; Tab.TEAM -> NavIcons.Team; Tab.RECRUIT -> NavIcons.Recruit; Tab.LEAGUE -> NavIcons.League; Tab.CAREER -> NavIcons.Career }
                val sel = vm.tab == t
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { vm.select(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(if (sel) P.accentSoft else Color.Transparent).padding(horizontal = 16.dp, vertical = 4.dp)) {
                        Icon(icon, t.label, tint = if (sel) P.accent else P.sub, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(t.label, style = T.label.copy(fontSize = 11.sp, letterSpacing = 0.3.sp), color = if (sel) P.ink else P.sub)
                }
            }
        }
    }
}

@Composable
fun BusyScreen(label: String) {
    val spin = rememberInfiniteTransition(label = "spin")
    val sweep by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "a")
    val accent = P.accent
    val track = P.line
    Column(Modifier.fillMaxSize().background(P.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Canvas(Modifier.size(54.dp)) {
            drawCircle(track, size.minDimension / 2 - 4f, style = Stroke(8f))
            drawArc(accent, sweep, 90f, false, topLeft = Offset(4f, 4f), size = androidx.compose.ui.geometry.Size(size.width - 8f, size.height - 8f), style = Stroke(8f, cap = StrokeCap.Round))
        }
        Spacer(Modifier.height(18.dp))
        Text("$label…", style = T.bodyB, color = P.sub)
    }
}

@Composable
private fun Toast(vm: GameViewModel) {
    val msg = vm.message ?: return
    LaunchedEffect(msg) {
        delay(2600)
        if (vm.message == msg) vm.message = null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 150.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Box(
            Modifier.clip(RoundedCornerShape(14.dp)).background(if (P.dark) P.surface2 else P.ink).border(1.dp, P.line, RoundedCornerShape(14.dp))
                .clickable { vm.message = null }.padding(horizontal = 16.dp, vertical = 12.dp),
        ) { Text(msg, style = T.bodyB, color = if (P.dark) P.ink else Color.White) }
    }
}

fun teamLabel(t: com.nh.hoopscoach.game.Team) = if (t.ap in 1..25) "#${t.ap} ${t.school}" else t.school

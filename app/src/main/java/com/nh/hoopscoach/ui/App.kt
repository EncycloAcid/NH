package com.nh.hoopscoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import kotlinx.coroutines.delay

@Composable
fun App(vm: GameViewModel) {
    HoopsTheme {
        Box(Modifier.fillMaxSize().background(C.bg)) {
            val busy = vm.busy
            val lg = vm.league
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
        Box(Modifier.weight(1f).fillMaxWidth()) {
            val route = vm.stack.lastOrNull()
            if (route == null) {
                when (vm.tab) {
                    Tab.HOME -> HomeScreen(vm, lg)
                    Tab.PROGRAM -> ProgramScreen(vm, lg)
                    Tab.LEAGUE -> LeagueScreen(vm, lg)
                    Tab.DYNASTY -> DynastyScreen(vm, lg)
                }
            } else RouteScreen(vm, lg, route)
        }
        if (vm.stack.isEmpty()) BottomBar(vm) else Spacer(Modifier.navigationBarsPadding())
    }
    vm.sheet?.let { pid -> lg.playerOrNull(pid)?.let { PlayerSheet(vm, lg, it) } ?: run { vm.sheet = null } }
}

@Composable
private fun RouteScreen(vm: GameViewModel, lg: League, r: Route) {
    when (r) {
        is Route.Live -> LiveScreen(vm, lg)
        is Route.Box -> BoxScoreScreen(vm, lg, r.gameId)
        is Route.TeamPage -> TeamScreen(vm, lg, r.teamId)
        is Route.BracketView -> BracketScreen(vm, lg, r.confId)
        is Route.Schedule -> ScheduleScreen(vm, lg, r.teamId)
        is Route.History -> HistoryScreen(vm, lg, r.kind)
        is Route.Settings -> SettingsScreen(vm, lg)
        is Route.EditTeam -> EditTeamScreen(vm, lg, r.teamId)
        is Route.Focus -> TrainingFocusScreen(vm, lg, r.playerId)
        is Route.RecruitView -> RecruitScreen(vm, lg, r.playerId)
        is Route.Leaders -> LeadersScreen(vm, lg)
        is Route.CoachDev -> Column(Modifier.fillMaxSize()) {
            BackHeader("Coach Development", "Spend coaching points", onBack = { vm.back() })
            androidx.compose.foundation.lazy.LazyColumn { item { CoachPointsCard(vm, lg) } }
        }
    }
}

@Composable
private fun BottomBar(vm: GameViewModel) {
    Column(Modifier.fillMaxWidth().background(C.bg)) {
        Divider()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 6.dp)) {
            for (t in Tab.entries) {
                val icon: ImageVector = when (t) { Tab.HOME -> AppIcons.Court; Tab.PROGRAM -> AppIcons.Group; Tab.LEAGUE -> AppIcons.Trophy; Tab.DYNASTY -> AppIcons.Crown }
                val sel = vm.tab == t
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.select(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(icon, t.label, tint = if (sel) C.orange else C.dim, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.height(3.dp))
                    Text(t.label, fontSize = 13.sp, color = if (sel) C.orange else C.dim, fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal)
                }
            }
        }
    }
}

@Composable
fun BusyScreen(label: String) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(1100), RepeatMode.Restart), label = "angle")
    Column(Modifier.fillMaxSize().background(C.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(AppIcons.Trophy, null, tint = C.orange, modifier = Modifier.size(54.dp).rotate(angle))
        Spacer(Modifier.height(18.dp))
        Text("$label…", fontSize = 17.sp, color = C.sub)
    }
}

@Composable
private fun Toast(vm: GameViewModel) {
    val msg = vm.message ?: return
    LaunchedEffect(msg) {
        delay(2600)
        if (vm.message == msg) vm.message = null
    }
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 86.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.clip(RoundedCornerShape(12.dp)).background(C.card2).clickable { vm.message = null }.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(msg, color = C.text, fontSize = 15.sp)
        }
    }
}

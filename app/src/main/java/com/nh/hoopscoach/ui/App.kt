package com.nh.hoopscoach.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
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
    SpriteSheets.install(LocalContext.current.assets)
    val lg = vm.league
    val user = lg?.user
    HoopsTheme(user?.let { Color(it.primary) }, user?.let { Color(it.secondary) }) {
        Box(Modifier.fillMaxSize().background(P.bg)) {
            val busy = vm.busy
            // Loading, a long sim and the game itself fade into each other.
            val mode = when { vm.loading -> 0; busy != null -> 1; lg == null -> 2; else -> 3 }
            androidx.compose.animation.AnimatedContent(
                mode, label = "mode",
                transitionSpec = { (androidx.compose.animation.fadeIn(tween(360, 60)) + androidx.compose.animation.scaleIn(tween(420, 60, easing = Motion.EmphasizedDecel), 0.97f)) togetherWith androidx.compose.animation.fadeOut(tween(160)) },
            ) { m ->
                when (m) {
                    0 -> BusyScreen("Loading")
                    1 -> BusyScreen(vm.busy ?: "Working", vm.progress, vm.progressNote)
                    2 -> NewGameScreen(vm)
                    // A pro league shows ratings on a 2K-style scale.
                    else -> vm.league?.let { l -> androidx.compose.runtime.CompositionLocalProvider(LocalRatingScale provides (if (l.pro) com.nh.hoopscoach.game.Ratings::shownPro else { v: Int -> v }), LocalRetroAll provides l.retroAll, LocalPfpSeed provides pfpSeedOf(l)) { Shell(vm, l) } }
                }
            }
            Toast(vm)
        }
    }
}

@Composable
private fun Shell(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    BackHandler(enabled = vm.guide != null || vm.sheet != null || vm.stack.isNotEmpty()) { vm.back() }
    // A career moment (his retirement, his introduction as a coach) takes the whole screen until he moves on.
    lg.ceremony?.let { CeremonyScreen(vm, lg, it); return }
    // Big moments pop up one at a time.
    lg.alerts.firstOrNull()?.let { a -> AlertPopup(vm, a) }
    // The preseason magazine opens by itself once, before the first game of every season.
    // The roster's portraits get drawn in the background right away.
    LaunchedEffect(lg, lg.season, lg.userTeamId) { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { warmPortraits(lg) } }
    LaunchedEffect(lg, lg.season, lg.phase) {
        val pv = lg.preview
        if (!lg.pro && lg.phase == Phase.REGULAR && pv != null && pv.season == lg.season && !pv.opened && lg.user.gp == 0) {
            pv.opened = true
            vm.push(Route.Preview)
        }
        // The NBA's issue opens the same way, before the first game of each pro season.
        val nba = lg.proState?.preview
        if (lg.pro && lg.phase == Phase.REGULAR && nba != null && nba.season == lg.season && !nba.opened && lg.user.w + lg.user.l == 0 && lg.road?.ended != true) {
            nba.opened = true
            vm.push(Route.NbaPreview)
        }
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        val route = vm.stack.lastOrNull()
        // Screens slide in when opened and back out when closed; tabs cross-fade.
        val screen = Screen(route, vm.tab, vm.stack.size)
        androidx.compose.animation.AnimatedContent(
            targetState = screen,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentKey = { s -> s.route?.let { "${s.depth}:$it" } ?: "tab:${s.tab}" },
            transitionSpec = {
                val push = targetState.depth > initialState.depth
                val pop = targetState.depth < initialState.depth
                val e = Motion.Emphasized
                when {
                    // A new screen slides in over the old one, which eases back and dims: depth, like a card on a stack.
                    push -> (androidx.compose.animation.slideInHorizontally(tween(420, easing = e)) { it / 3 } + androidx.compose.animation.fadeIn(tween(260, 40))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(420, easing = e)) { -it / 8 } + androidx.compose.animation.scaleOut(tween(420, easing = e), 0.94f) + androidx.compose.animation.fadeOut(tween(300)))
                    // Back: the screen slides away and the one under it comes forward.
                    pop -> (androidx.compose.animation.slideInHorizontally(tween(380, easing = e)) { -it / 8 } + androidx.compose.animation.scaleIn(tween(380, easing = e), 0.94f) + androidx.compose.animation.fadeIn(tween(240))) togetherWith
                        (androidx.compose.animation.slideOutHorizontally(tween(340, easing = Motion.EmphasizedAccel)) { it / 3 } + androidx.compose.animation.fadeOut(tween(220, 80)))
                    // Tabs fade through: the old one fades quickly, the new one settles in from a little smaller.
                    else -> (androidx.compose.animation.fadeIn(tween(260, 70)) + androidx.compose.animation.scaleIn(tween(320, 70, easing = Motion.EmphasizedDecel), 0.96f)) togetherWith
                        androidx.compose.animation.fadeOut(tween(90))
                }.apply { targetContentZIndex = if (pop) -1f else 1f }
            },
            label = "screens",
        ) { s ->
            // Read the revision here too, so the screen redraws after every change to the league.
            @Suppress("UNUSED_VARIABLE") val rev2 = vm.rev
            val route = s.route
            // When this screen opened, so its cards can wave in (see [reveal]).
            val opened = remember { now() }
            androidx.compose.runtime.CompositionLocalProvider(LocalScreenStart provides opened) {
            Box(Modifier.fillMaxSize()) {
            if (route == null) {
                if (com.nh.hoopscoach.game.NbaCoach.active(lg)) when (s.tab) {
                    Tab.TODAY -> NbaCoachToday(vm, lg)
                    Tab.TEAM -> NbaCoachTeam(vm, lg)
                    Tab.RECRUIT -> NbaTradeScreen(vm, lg)
                    Tab.LEAGUE -> ProLeagueScreen(vm, lg)
                    Tab.CAREER -> NbaCoachCareer(vm, lg)
                } else if (lg.pro) when (s.tab) {
                    Tab.TODAY -> TodayScreen(vm, lg)
                    Tab.TEAM -> ProTeamScreen(vm, lg)
                    Tab.RECRUIT -> ProHeroScreen(vm, lg)
                    Tab.LEAGUE -> ProLeagueScreen(vm, lg)
                    Tab.CAREER -> ProCareerScreen(vm, lg)
                } else when (s.tab) {
                    Tab.TODAY -> TodayScreen(vm, lg)
                    Tab.TEAM -> TeamScreen(vm, lg)
                    Tab.RECRUIT -> if (lg.playerMode) HeroScreen(vm, lg) else RecruitScreen(vm, lg)
                    Tab.LEAGUE -> LeagueScreen(vm, lg)
                    Tab.CAREER -> if (lg.playerMode) HeroCareerScreen(vm, lg) else CareerScreen(vm, lg)
                }
            } else RouteScreen(vm, lg, route)
            }
            }
        }
        if (route == null) {
            val flowOnScreen = vm.tab == Tab.TODAY && (lg.phase == Phase.WRAP_UP || lg.phase == Phase.OFFSEASON)
            if (!flowOnScreen) { if (lg.pro) ProContinueBar(vm, lg) else ContinueBar(vm, lg) }
            NavBar(vm)
        } else Spacer(Modifier.navigationBarsPadding())
    }
    vm.sheet?.let { pid -> lg.anyPlayer(pid)?.let { PlayerSheet(vm, lg, it) } ?: run { vm.sheet = null } }
    vm.guide?.let { GuideSheet(vm, it) }
}

/** What's on screen: an open route (or none, for a tab), the tab, and how deep the stack is. */
private data class Screen(val route: Route?, val tab: Tab, val depth: Int)

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
        is Route.NbaPreview -> NbaPreviewScreen(vm, lg)
        is Route.News -> NewsScreen(vm, lg)
        is Route.EditPlayer -> EditPlayerScreen(vm, lg, r.playerId)
        is Route.BuildLab -> BuildLabScreen(vm, lg, r.playerId)
        is Route.RecruitPreview -> RecruitPreviewScreen(vm, lg)
        is Route.Social -> SocialScreen(vm, lg)
        is Route.Article -> ArticleScreen(vm, lg, r.newsIndex)
        is Route.Trophies -> TrophyRoomScreen(vm, lg)
        is Route.Staff -> StaffScreen(vm, lg)
        is Route.Portrait -> PortraitMakerScreen(vm, lg, r.playerId)
        is Route.MyPlayer -> MyPlayerScreen(vm, lg)
        is Route.TopTen -> TopTenScreen(vm, lg)
        is Route.Mixtape -> MixtapeScreen(vm, lg)
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
                    val draft = com.nh.hoopscoach.game.Road.inGreenRoom(lg)
                    val done = lg.road?.ended == true && !draft
                    Text(if (draft) "DRAFT NIGHT" else if (done) "CAREER COMPLETE" else if (lg.phase == Phase.WRAP_UP) "SEASON COMPLETE" else "OFFSEASON", style = T.label, color = P.accent)
                    Text(if (draft) "Back to the green room" else if (done) "See how it ended" else if (lg.phase == Phase.WRAP_UP) "Review the ${lg.season} season" else "Pick up where you left off", style = T.bodyB, color = P.ink, maxLines = 1)
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
                    Box(Modifier.size(40.dp).clip(CircleShape).background(if (menu) P.accentSoft else Color.Transparent).clickable { menu = true }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.MoreVert, "More sim options", tint = if (menu) P.accent else P.sub)
                    }
                    AppMenu(menu, { menu = false }, title = "Sim ahead") {
                        fun pick(a: () -> Unit) { menu = false; a() }
                        val left = lg.games.count { it.involves(u.id) && !it.played && it.day <= Schedule.REG_DAYS }
                        if (lg.phase == Phase.REGULAR) {
                            AppMenuItem("Sim one week", "The next 7 days", MenuIcons.Week) { pick { vm.simDays(7) } }
                            AppMenuItem("Sim to the conference tournament", "$left regular-season ${if (left == 1) "game" else "games"} left", MenuIcons.Calendar) {
                                pick { vm.simToPhase(Phase.CONF_TOURNEY, "Simulating the regular season", Schedule.REG_DAYS - lg.day + 1) }
                            }
                        }
                        if (lg.phase != Phase.NCAA) AppMenuItem("Sim to Selection Sunday", "Conference tournaments, then the bracket", MenuIcons.Bracket) {
                            pick { vm.simToPhase(Phase.NCAA, "Simulating to Selection Sunday") }
                        }
                        AppMenuDivider()
                        AppMenuItem("Sim to the end of the season", "Through the national championship", MenuIcons.Trophy) {
                            pick { vm.simToPhase(Phase.WRAP_UP, "Simulating the rest of the season") }
                        }
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
                val label = if (t == Tab.RECRUIT && vm.league?.let { com.nh.hoopscoach.game.NbaCoach.active(it) } == true) "Trades"
                    else if (t == Tab.RECRUIT && vm.league?.playerMode == true) "Me" else t.label
                // The selected tab's pill grows in from the middle and its icon gives a little bounce.
                val pill by androidx.compose.animation.core.animateFloatAsState(if (sel) 1f else 0f, androidx.compose.animation.core.spring(dampingRatio = 0.7f, stiffness = 500f), label = "navPill")
                val tint by androidx.compose.animation.animateColorAsState(if (sel) P.accent else P.sub, tween(200), label = "navTint")
                val soft = P.accentSoft
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).clickable { vm.select(t) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier.drawBehind {
                            if (pill > 0.01f) {
                                val w = size.width * (0.45f + 0.55f * pill)
                                drawRoundRect(soft.copy(alpha = pill.coerceIn(0f, 1f)), topLeft = androidx.compose.ui.geometry.Offset((size.width - w) / 2, 0f),
                                    size = androidx.compose.ui.geometry.Size(w, size.height), cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2))
                            }
                        }.padding(horizontal = 16.dp, vertical = 4.dp),
                    ) {
                        Icon(icon, label, tint = tint, modifier = Modifier.size(22.dp).pulseOn(sel.takeIf { it }, 0.18f))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(label, style = T.label.copy(fontSize = 11.sp, letterSpacing = 0.3.sp), color = if (sel) P.ink else P.sub)
                }
            }
        }
    }
}

/**
 * Work in progress: the Material 3 loading indicator, what's happening, and for a sim of known
 * length a wavy progress bar with the day and the record so far.
 */
@Composable
fun BusyScreen(label: String, progress: Float? = null, note: String = "") {
    Column(Modifier.fillMaxSize().background(P.bg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        LoadingIndicator(size = 64.dp)
        Spacer(Modifier.height(20.dp))
        Text("$label…", style = T.bodyB, color = P.ink)
        if (progress != null) {
            Spacer(Modifier.height(14.dp))
            Bar(progress, Modifier.width(220.dp), height = 10.dp, wavy = true)
            Spacer(Modifier.height(8.dp))
            Text("${(progress * 100).toInt()}%" + if (note.isNotEmpty()) " · $note" else "", style = T.small, color = P.sub)
        } else if (note.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(note, style = T.small, color = P.sub)
        }
    }
}

@Composable
private fun Toast(vm: GameViewModel) {
    val msg = vm.message
    LaunchedEffect(msg) {
        if (msg == null) return@LaunchedEffect
        delay(2600)
        if (vm.message == msg) vm.message = null
    }
    // Keep the last message on screen while it slides away.
    var shown by remember { mutableStateOf("") }
    if (msg != null) shown = msg
    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 150.dp, start = 24.dp, end = 24.dp), contentAlignment = Alignment.BottomCenter) {
        androidx.compose.animation.AnimatedVisibility(
            msg != null,
            enter = androidx.compose.animation.slideInVertically(androidx.compose.animation.core.spring(dampingRatio = 0.72f, stiffness = 420f)) { it } + androidx.compose.animation.fadeIn(tween(160)) +
                androidx.compose.animation.scaleIn(tween(260, easing = Motion.EmphasizedDecel), 0.9f),
            exit = androidx.compose.animation.slideOutVertically(tween(220, easing = Motion.EmphasizedAccel)) { it / 2 } + androidx.compose.animation.fadeOut(tween(180)),
        ) {
            Box(
                Modifier.shadow(12.dp, RoundedCornerShape(14.dp), clip = false).clip(RoundedCornerShape(14.dp)).background(if (P.dark) P.surface2 else P.ink).border(1.dp, P.line, RoundedCornerShape(14.dp))
                    .clickable { vm.message = null }.padding(horizontal = 16.dp, vertical = 12.dp),
            ) { Text(shown, style = T.bodyB, color = if (P.dark) P.ink else Color.White) }
        }
    }
}

fun teamLabel(t: com.nh.hoopscoach.game.Team) = if (t.ap in 1..25) "#${t.ap} ${t.school}" else t.school

/** A big moment: an award, an All-Star nod, a trade, a record, a title. */
@Composable
private fun AlertPopup(vm: GameViewModel, a: com.nh.hoopscoach.game.Alert) {
    val dismiss = { vm.act { if (it.alerts.isNotEmpty()) it.alerts.removeAt(0) } }
    val left = vm.league?.alerts?.size ?: 1
    val (kicker, icon) = when (a.kind) {
        "award" -> "HONOR" to MenuIcons.Star; "star" -> "ALL-STAR" to MenuIcons.Star; "trade" -> "TRADE" to MenuIcons.Swap
        "record" -> "RECORDS BOOK" to MenuIcons.Flag; "title" -> "CHAMPIONSHIP" to MenuIcons.Trophy; "trophy" -> "TROPHY ROOM" to MenuIcons.Trophy
        "farewell" -> "FAREWELL TOUR" to MenuIcons.Flag; "signing" -> "SIGNED" to MenuIcons.Swap; else -> "NEWS" to MenuIcons.Calendar
    }
    // Gold for the big honors, the team's color for everything else.
    val tone = if (a.kind in setOf("award", "star", "record", "title", "trophy", "farewell")) P.gold else P.accent
    androidx.compose.ui.window.Dialog(onDismissRequest = { dismiss() }) {
        // The card springs up into place; each new alert gets its own entrance.
        val enter = remember(a) { androidx.compose.animation.core.Animatable(0f) }
        LaunchedEffect(a) { enter.animateTo(1f, androidx.compose.animation.core.spring(dampingRatio = 0.68f, stiffness = 340f)) }
        val glow = breathe()
        Column(
            Modifier.graphicsLayer {
                val v = enter.value
                alpha = v.coerceIn(0f, 1f); scaleX = 0.86f + 0.14f * v; scaleY = 0.86f + 0.14f * v; translationY = (1f - v) * 60f
            }.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(P.surface).padding(22.dp),
        ) {
            Box(Modifier.size(48.dp).graphicsLayer { scaleX = glow; scaleY = glow }.clip(RoundedCornerShape(14.dp)).background(tone.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = tone, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(kicker, style = T.label, color = tone, modifier = Modifier.weight(1f))
                if (left > 1) Text("1 of $left", style = T.label, color = P.faint)
            }
            Text(a.title, style = T.h2, color = P.ink, modifier = Modifier.padding(top = 2.dp))
            Text(a.body, style = T.body, color = P.sub, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(20.dp))
            PrimaryButton(if (left > 1) "Next" else "Nice", Modifier.fillMaxWidth()) { dismiss() }
        }
    }
}

/** The save's profile-picture seed, picked at random the first time and kept with the save from then on. */
private fun pfpSeedOf(lg: League): Long {
    if (lg.pfpSeed == 0L) lg.pfpSeed = kotlin.random.Random.nextLong().let { if (it == 0L) 1L else it }
    return lg.pfpSeed
}

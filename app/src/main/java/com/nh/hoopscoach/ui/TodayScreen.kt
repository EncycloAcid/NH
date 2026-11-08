package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Bracket
import com.nh.hoopscoach.game.Bracketology
import com.nh.hoopscoach.game.Game
import com.nh.hoopscoach.game.GameType
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Rankings
import com.nh.hoopscoach.game.Ratings
import com.nh.hoopscoach.game.Rivalries
import com.nh.hoopscoach.game.Schedule
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team

@Composable
fun TodayScreen(vm: GameViewModel, lg: League) {
    if (lg.pro) { ProTodayScreen(vm, lg); return }
    when (lg.phase) {
        Phase.REGULAR -> SeasonToday(vm, lg)
        Phase.CONF_TOURNEY, Phase.NCAA, Phase.PLAYOFFS -> TourneyToday(vm, lg)
        Phase.WRAP_UP -> WrapUpFlow(vm, lg)
        Phase.OFFSEASON -> if (lg.playerMode) RoadOffseasonFlow(vm, lg) else OffseasonFlow(vm, lg)
    }
}

fun recordLine(lg: League, t: Team): String {
    // The pros have no polls or NET: their record, conference record and place in the conference.
    if (lg.pro) {
        val conf = lg.conf(t.confId).abbr.lowercase().replaceFirstChar { it.uppercase() }
        val pos = com.nh.hoopscoach.game.ProSeason.standings(lg, t.confId).indexOfFirst { it.id == t.id } + 1
        return listOfNotNull(t.record, "${t.confRecord} $conf", if (t.gp > 0 && pos > 0) "${ordinal(pos)} in the $conf" else null).joinToString("  ·  ")
    }
    val parts = mutableListOf("${t.record}", "${t.confRecord} ${lg.conf(t.confId).abbr}")
    if (t.ap > 0) parts += "AP #${t.ap}"
    if (t.gp > 0) parts += "NET ${t.net}"
    return parts.joinToString("  ·  ")
}

@Composable
private fun SeasonToday(vm: GameViewModel, lg: League) {
    val u = lg.user
    val today = Season.userGameToday(lg) ?: Season.nextUserGame(lg)
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            ScreenHeader("Season ${lg.season} · Game ${(u.gp + 1).coerceAtMost(Schedule.REG_DAYS)} of ${Schedule.REG_DAYS}", u.school, recordLine(lg, u)) {
                TeamBadge(u, 46.dp)
            }
        }
        if (lg.playerMode) item { HeroTodayCard(vm, lg); PressCard(vm, lg) }
        Rivalries.latestUserRivalry(lg)?.let { g -> item { Spacer(Modifier.height(4.dp)); NewspaperBanner(vm, lg, g) } }
        item { LatestMyMoment(vm, lg) }
        item { PreviewCard(vm, lg) }
        if (today != null) item { NextUp(vm, lg, today) }
        item { TitleDefenseCard(lg) }
        item { TvCard(vm, lg) }
        item { MarqueeCard(vm, lg) }
        item { InjuryReport(vm, lg, u) }
        if (!lg.playerMode) item { GoalsCard(lg) }
        item { Form(vm, lg, u) }
        if (u.gp >= 8) item { Outlook(vm, lg) }
        item { ConfSnapshot(vm, lg, u) }
        item { TopPlayers(vm, lg, u) }
        if (lg.playerMode) item { SocialStrip(vm, lg) }
        item { Headlines(vm, lg) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

@Composable
fun NextUp(vm: GameViewModel, lg: League, g: Game, title: String? = null) {
    val u = lg.user
    val opp = lg.team(g.opp(u.id))
    val site = when { g.neutral -> 0; g.home == u.id -> 1; else -> -1 }
    val p = Rankings.winProbFor(lg, g, u.id)
    val rivalry = lg.rivalry(g)
    val where = when (site) { 0 -> "Neutral site"; 1 -> "Home"; else -> "Away" }
    val kind = when (g.type) {
        GameType.CONF -> lg.conf(u.confId).abbr
        GameType.NONCONF -> "Non-conference"
        else -> g.label.substringBefore(" ·")
    }
    val seeds = Season.userBracket(lg)?.seeds
    Section(title ?: "Next up", action = "Scout", onAction = { vm.push(Route.TeamPage(opp.id)) })
    Panel {
        if (rivalry != null) {
            RivalryPreview(lg, g, rivalry)
            Spacer(Modifier.height(12.dp))
        }
        val big = com.nh.hoopscoach.game.Marquee.label(lg, g)
        if (big.isNotEmpty()) Text(big.uppercase(), style = T.label, color = P.gold)
        Text("$kind · $where".uppercase(), style = T.label, color = P.sub)
        com.nh.hoopscoach.game.CoachRivals.pregame(lg, g)?.let { Text(it, style = T.small, color = P.bad) }
        if (rivalry == null && !g.neutral) {
            val home = lg.team(g.home)
            val fill = com.nh.hoopscoach.game.Fans.expectedFill(lg, g)
            Text(
                "Expected crowd ${if (fill >= 0.99) "sellout" else "about ${fans((home.arena * fill).toInt() / 100 * 100)}"} · ${home.abbr} fans are ${com.nh.hoopscoach.game.Fans.mood(home.fans).lowercase()}",
                style = T.small, color = P.sub,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Side(lg, u, seeds?.get(u.id), Modifier.weight(1f)) { vm.push(Route.TeamPage(u.id)) }
            Text(if (site >= 0) "VS" else "AT", style = T.h2, color = P.faint, modifier = Modifier.padding(horizontal = 6.dp))
            Side(lg, opp, seeds?.get(opp.id), Modifier.weight(1f)) { vm.push(Route.TeamPage(opp.id)) }
        }
        Spacer(Modifier.height(16.dp))
        val mine = P.accent
        val theirs = teamInk(opp).let { if (contrast(it, mine) < 1.4f) P.faint else it }
        WinChance(p, opp)
        Spacer(Modifier.height(12.dp))
        Compare("Overall", Gameplan.teamRating(lg, u), Gameplan.teamRating(lg, opp), mine, theirs)
        Compare("Offense", Gameplan.teamRating(lg, u) { Ratings.offense(it) }, Gameplan.teamRating(lg, opp) { Ratings.offense(it) }, mine, theirs)
        Compare("Defense", Gameplan.teamRating(lg, u) { Ratings.defense(it) }, Gameplan.teamRating(lg, opp) { Ratings.defense(it) }, mine, theirs)
    }
}

/** Our chance against [opp]: both numbers and a split bar in the two teams' colors. */
@Composable
fun WinChance(p: Double, opp: Team) {
    val mine = P.accent
    val theirs = teamInk(opp).let { if (contrast(it, mine) < 1.4f) P.faint else it }
    val pct = (p * 100).toInt().coerceIn(1, 99)
    Row(verticalAlignment = Alignment.Bottom) {
        Text("$pct%", style = T.num, color = P.ink)
        Text("  win chance", style = T.small, color = P.sub, modifier = Modifier.padding(bottom = 3.dp).weight(1f))
        Text("${100 - pct}%", style = T.num, color = P.sub)
    }
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))) {
        Box(Modifier.weight(p.toFloat().coerceIn(0.02f, 0.98f)).height(8.dp).background(mine))
        Spacer(Modifier.width(3.dp))
        Box(Modifier.weight((1 - p).toFloat().coerceIn(0.02f, 0.98f)).height(8.dp).background(theirs))
    }
}

@Composable
private fun Side(lg: League, t: Team, seed: Int?, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        TeamBadge(t, 58.dp)
        Spacer(Modifier.height(8.dp))
        val label = (seed?.let { "($it) " } ?: if (t.ap > 0) "#${t.ap} " else "") + t.school
        // Long names step down a size and may wrap instead of being cut off.
        Text(label, style = if (label.length > 15) T.head.copy(fontSize = 16.sp, lineHeight = 19.sp) else T.head, color = if (t.id == lg.userTeamId) P.accent else P.ink,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text("${t.mascot} · ${t.record}", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Form(vm: GameViewModel, lg: League, t: Team) {
    val recent = lg.games.filter { it.played && it.involves(t.id) }.sortedByDescending { it.day }.take(5)
    Section("Recent form", action = "Schedule", onAction = { vm.push(Route.Schedule(t.id)) })
    if (recent.isEmpty()) {
        Panel { Text("The season tips off with your next game.", style = T.body, color = P.sub) }
        return
    }
    Panel(padding = PaddingValues(horizontal = 8.dp, vertical = 12.dp)) {
        Row(Modifier.fillMaxWidth()) {
            recent.reversed().forEach { g ->
                val won = g.winner() == t.id
                Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { vm.push(Route.Box(g.id)) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ResultDot(won)
                    Spacer(Modifier.height(6.dp))
                    Text("${g.scoreFor(t.id)}-${g.scoreAgainst(t.id)}", style = T.numSm.copy(fontSize = 16.sp), color = P.ink)
                    Text("${if (g.home == t.id || g.neutral) "" else "@"}${lg.team(g.opp(t.id)).abbr}", style = T.small.copy(fontSize = 12.sp), color = P.sub, maxLines = 1)
                }
            }
            repeat(5 - recent.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun ConfSnapshot(vm: GameViewModel, lg: League, t: Team) {
    val st = Season.standings(lg, t.confId)
    val idx = st.indexOf(t)
    val shown = (st.take(4).mapIndexed { i, x -> i to x } + if (idx >= 4) listOf(idx to t) else emptyList())
    Section("${lg.conf(t.confId).name} standings", action = "All", onAction = { vm.leagueConf = t.confId; vm.leagueTab = 1; vm.select(Tab.LEAGUE) })
    GroupList(shown) { (i, x) -> StandingRow(vm, lg, i + 1, x) }
}

@Composable
fun StandingRow(vm: GameViewModel, lg: League, rank: Int, t: Team) {
    val mine = t.id == lg.userTeamId
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
        Text("$rank", style = T.numSm, color = if (mine) P.accent else P.sub, modifier = Modifier.width(30.dp))
        TeamBadge(t, 28.dp)
        Spacer(Modifier.width(10.dp))
        Text(teamLabel(t), style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(t.confRecord, style = T.numSm.copy(fontSize = 16.sp), color = P.ink, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
        Text(t.record, style = T.numSm.copy(fontSize = 16.sp), color = P.sub, modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
    }
}

@Composable
private fun TopPlayers(vm: GameViewModel, lg: League, t: Team) {
    val ps = lg.rosterOf(t)
    val top = if (t.gp > 0) ps.sortedByDescending { it.stats.ppg }.take(3) else ps.sortedByDescending { it.ovr }.take(3)
    Section(if (t.gp > 0) "Top performers" else "Key players", action = "Roster", onAction = { vm.teamTab = 0; vm.select(Tab.TEAM) })
    GroupList(top, inset = 70.dp) { p -> PlayerLine(vm, lg, t, p, if (t.gp > 0) statLine(p) else "${p.posLabel} · ${p.cls} · ${p.style.label}") }
}

fun statLine(p: Player) = "${f1(p.stats.ppg)} pts · ${f1(p.stats.rpg)} reb · ${f1(p.stats.apg)} ast"

@Composable
fun PlayerLine(vm: GameViewModel, lg: League, t: Team?, p: Player, sub: String, trailing: (@Composable () -> Unit)? = null) {
    val team = t ?: if (p.teamId >= 0) lg.team(p.teamId) else null
    ListRow(onClick = { vm.openPlayer(p.id) }, modifier = if (p.id == lg.heroId) Modifier.background(P.accentSoft) else Modifier) {
        PlayerJersey(p, team, 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(p.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // A line break in the subtitle means two lines were meant to show.
            Text(sub, style = T.small, color = P.sub, maxLines = if ('\n' in sub) 2 else 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        if (trailing != null) trailing() else Ring(p.ovr, 42.dp)
    }
}

@Composable
fun PlayerJersey(p: Player, t: Team?, size: androidx.compose.ui.unit.Dp) {
    val primary = t?.let { Color(it.primary) } ?: P.faint
    // A real headshot when there is one (unless the setting draws everyone retro); otherwise his retro portrait.
    if (p.photo.isNotEmpty() && !LocalRetroAll.current) {
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val img = remember(p.photo) { Logos.asset(ctx, "nba/heads/${p.photo}.webp") }
        if (img != null) {
            // A headshot on a soft team-colored plate, cropped to a circle from the top.
            Box(Modifier.size(size).clip(CircleShape).background(primary.copy(alpha = 0.18f))) {
                androidx.compose.foundation.Image(
                    img, p.name, Modifier.size(size),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter,
                )
            }
            return
        }
    }
    PixelPortrait(p, t, size)
}

@Composable
private fun Headlines(vm: GameViewModel, lg: League) {
    val items = lg.news.filter { it.season == lg.season }.takeLast(6).reversed()
    if (items.isEmpty()) return
    Section("Around the league", action = "All news", onAction = { vm.push(Route.News) })
    GroupList(items, inset = 34.dp) { n -> HeadlineRow(vm, lg, n) }
}

// ---------- tournaments ----------

@Composable
private fun TourneyToday(vm: GameViewModel, lg: League) {
    val u = lg.user
    val post = lg.phase == Phase.NCAA
    val b: Bracket = Season.userBracket(lg) ?: return
    val isNcaa = b.confId == -1
    val isNit = b.confId == Season.NIT_ID
    val ffDay = isNcaa && Season.firstFourDay(lg)
    val myPlayIn = if (isNcaa) b.playInOf(u.id) else null
    val inEvent = b.hasTeam(u.id)
    val today = Season.userGameToday(lg)
    val roundName = when { b.done -> "Champions"; ffDay -> "First Four"; else -> roundTitle(b, b.current, isNcaa) }
    val alive = !b.done && ((b.current < b.rounds.size && u.id in b.rounds[b.current]) || (ffDay && myPlayIn != null))
    LazyColumn(Modifier.fillMaxWidth()) {
        item {
            val eyebrow = when { isNcaa -> "NCAA Tournament"; isNit -> "NIT"; else -> "${lg.conf(u.confId).abbr} Tournament" }
            val sub = when {
                isNcaa -> Season.regionOf(lg, u.id)?.let { "$it Region · No. ${b.seeds[u.id]} seed" } ?: "${u.school} missed the field"
                else -> "No. ${b.seeds[u.id] ?: "–"} seed · ${recordLine(lg, u)}"
            }
            ScreenHeader(eyebrow, roundName, sub) { TeamBadge(u, 46.dp) }
        }
        if (lg.playerMode) item { HeroTodayCard(vm, lg); PressCard(vm, lg) }
        Rivalries.latestUserRivalry(lg)?.takeIf { it.day > Schedule.REG_DAYS }?.let { g -> item { Spacer(Modifier.height(4.dp)); NewspaperBanner(vm, lg, g) } }
        if (inEvent) item { PathStepper(b, u.id) }
        when {
            today != null -> item { NextUp(vm, lg, today, if (ffDay) "Your First Four game" else "Your game") }
            b.done && b.champion == u.id -> item {
                Section("Your season")
                Panel(color = P.hero) {
                    Text("${b.name.uppercase()} CHAMPIONS", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                    Text(u.school.uppercase(), style = T.title, color = P.onHero)
                    if (isNit) Text("Prestige, coaching points and extra player development arrive in the season review.", style = T.small, color = P.onHero.copy(alpha = 0.85f))
                }
            }
            alive -> item { Section("Your game"); Panel { Text("You have a bye this round. Sim it to see who you'll face.", style = T.body, color = P.sub) } }
            else -> item {
                Section("Your season")
                Panel {
                    Text(
                        when {
                            post && !inEvent -> "${u.school} missed the NCAA Tournament and the NIT. Follow the brackets from here."
                            !post -> "Out of the ${lg.conf(u.confId).abbr} Tournament. The resume is set: sim ahead to Selection Sunday and see where the committee puts you."
                            else -> "${u.school}'s season is over. Sim ahead to follow the rest of March."
                        },
                        style = T.body, color = P.sub,
                    )
                }
            }
        }
        if (!post) item { Outlook(vm, lg) }
        item { UpsetAlerts(vm, lg) }
        item {
            val r = if (b.done) b.gameIds.lastIndex else b.current
            val games = if (ffDay) b.playIns.mapNotNull { pi -> pi.gameId.takeIf { it >= 0 }?.let { lg.game(it) } }
            else (b.gameIds.getOrNull(r) ?: emptyList()).filter { it >= 0 }.map { lg.game(it) }
            val region = if (isNcaa) Season.regionOf(lg, u.id) else null
            val shown = if (isNcaa && !ffDay && b.current < 4 && region != null) games.filter { it.label.contains(region) } else games
            Section(if (b.done) "Championship" else "$roundName games", action = "Bracket", onAction = { vm.push(Route.BracketView(b.confId)) })
            if (shown.isEmpty()) Panel { Text("No games left this round.", style = T.body, color = P.sub) }
            GroupList(shown.take(8)) { g -> MatchRow(vm, lg, b, g) }
        }
        if (post) item { OtherEvents(vm, lg, b) }
        if (lg.playerMode) item { SocialStrip(vm, lg) }
        item { Headlines(vm, lg) }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

/** Short status line for a postseason bracket. */
fun eventStatus(lg: League, b: Bracket): String = when {
    b.done -> "Champion: ${lg.team(b.champion).school}"
    b.confId == -1 && Season.firstFourDay(lg) -> "Now: First Four"
    else -> "Now: ${roundTitle(b, b.current, b.confId == -1)}"
}

@Composable
private fun OtherEvents(vm: GameViewModel, lg: League, mine: Bracket) {
    val others = listOfNotNull(lg.ncaa, lg.nit).filter { it !== mine }
    if (others.isEmpty()) return
    Section("Around the postseason")
    GroupList(others) { ob ->
        ListRow(onClick = { vm.push(Route.BracketView(ob.confId)) }) {
            Text(ob.short, style = T.numSm, color = P.accent, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Text(eventStatus(lg, ob).removePrefix("Now: "), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (ob.confId == -1) "${ob.seeds.size} teams" else "32 teams", style = T.small, color = P.sub)
            }
            Chevron()
        }
    }
}

/** Where the user's team stands for Selection Sunday, from the Bubble Watch projection. */
@Composable
fun Outlook(vm: GameViewModel, lg: League) {
    val b = remember(lg, lg.day, lg.phase) { Bracketology.board(lg) } ?: return
    val u = lg.user
    val s = b.sheet(u.id) ?: return
    val key = b.groupOf(u.id)?.key
    val status = Bracketology.status(b, u.id)
    val seed = b.seeds[u.id]
    val conf = lg.conf(u.confId).abbr
    val open = { vm.leagueTab = TAB_BUBBLE; vm.select(Tab.LEAGUE) }
    val text = when (key) {
        "lock" -> "Safely in the field."
        "safe" -> "Should be in. One more good result seals it."
        "bubble" -> if (status == "Last four in") "One of the last four in. Right now that means the First Four." else "On the bubble, just above the First Four."
        "ffo" -> "Just outside the field. A quality win could flip it."
        "nfo" -> "Outside the field. It will take a big run."
        "auto" -> "In as the projected $conf champion. Winning the conference tournament makes it official."
        else -> "Outside the bubble. Winning the $conf tournament is the way in."
    }
    Section(if (lg.phase == Phase.CONF_TOURNEY) "Selection Sunday outlook" else "NCAA outlook", action = "Bubble Watch", onAction = open)
    Panel(onClick = open) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Tag(status.uppercase(), groupColor(key))
                Spacer(Modifier.height(6.dp))
                Text(text, style = T.body, color = P.ink)
            }
            if (seed != null) {
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("$seed", style = T.num, color = P.ink)
                    Text("PROJ. SEED", style = T.label.copy(fontSize = 9.sp), color = P.sub)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        StatRow(
            listOf(
                Triple("NET", "${s.net}", null), Triple("Quad 1", s.quad(1), null), Triple("SOS", ordinal(s.sos), null),
                Triple("WAB", "%+.1f".format(s.wab), if (s.wab >= 0) P.good else P.bad),
            ),
            style = T.numSm,
        )
    }
}

fun roundTitle(b: Bracket, r: Int, ncaa: Boolean): String = if (ncaa) when (r) {
    0 -> "First Round"; 1 -> "Second Round"; 2 -> "Sweet 16"; 3 -> "Elite Eight"; 4 -> "Final Four"; 5 -> "Championship"; else -> "Champions"
} else when (b.roundNames.getOrNull(r)) {
    "R1" -> "First Round"; "R2" -> "Second Round"; "QF" -> "Quarterfinals"; "SF" -> "Semifinals"; "Final" -> "Championship"; else -> "Champions"
}

/** Steps to the title, with the First Four in front for play-in teams. */
@Composable
fun PathStepper(b: Bracket, teamId: Int) {
    // 0 upcoming, 1 now, 2 won, 3 lost
    val steps = mutableListOf<Pair<String, Int>>()
    b.playInOf(teamId)?.let { pi ->
        val won = teamId in b.rounds[0]
        val lost = !won && b.rounds[0][pi.slot] >= 0
        steps += "FF" to when { won -> 2; lost -> 3; else -> 1 }
    }
    b.roundNames.forEachIndexed { i, name ->
        val reached = b.rounds.getOrNull(i)?.contains(teamId) == true
        val advanced = b.rounds.getOrNull(i + 1)?.contains(teamId) == true || (i == b.roundNames.lastIndex && b.champion == teamId)
        val current = i == b.current && reached
        val lost = reached && !advanced && i < b.current
        steps += name to when { advanced -> 2; lost -> 3; current -> 1; else -> 0 }
    }
    Section("Road to the title")
    Panel(padding = PaddingValues(horizontal = 10.dp, vertical = 14.dp)) {
        Row(Modifier.fillMaxWidth()) {
            steps.forEachIndexed { i, (name, state) ->
                val color = when (state) { 2 -> P.good; 3 -> P.bad; 1 -> P.accent; else -> P.line }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(26.dp).clip(CircleShape).background(if (state == 1 || state == 2) color.copy(alpha = 0.15f) else Color.Transparent).border(2.dp, color, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(when (state) { 2 -> "✓"; 3 -> "×"; else -> "${i + 1}" }, style = T.label, color = color)
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(name.uppercase(), style = T.label.copy(fontSize = 9.sp, letterSpacing = 0.4.sp), color = if (state == 1) P.accent else P.sub, maxLines = 1)
                }
            }
        }
    }
}

@Composable
fun MatchRow(vm: GameViewModel, lg: League, b: Bracket, g: Game) {
    val h = lg.team(g.home)
    val a = lg.team(g.away)
    ListRow(onClick = { if (g.played) vm.push(Route.Box(g.id)) else vm.push(Route.TeamPage(if (h.id == lg.userTeamId) a.id else h.id)) }) {
        Column(Modifier.weight(1f)) {
            MatchTeam(lg, b, h, g)
            Spacer(Modifier.height(6.dp))
            MatchTeam(lg, b, a, g)
        }
        Chevron()
    }
}

@Composable
private fun MatchTeam(lg: League, b: Bracket, t: Team, g: Game) {
    val won = g.played && g.winner() == t.id
    val lost = g.played && !won
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("${b.seeds[t.id] ?: ""}", style = T.small, color = P.faint, modifier = Modifier.width(24.dp))
        TeamBadge(t, 22.dp)
        Spacer(Modifier.width(8.dp))
        Text(t.school, style = T.bodyB, color = when { t.id == lg.userTeamId -> P.accent; lost -> P.faint; else -> P.ink }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (g.played) Text("${g.scoreFor(t.id)}", style = T.numSm.copy(fontSize = 16.sp), color = if (won) P.ink else P.faint)
    }
}

@Suppress("unused")
private val keepArrangement = Arrangement.Center

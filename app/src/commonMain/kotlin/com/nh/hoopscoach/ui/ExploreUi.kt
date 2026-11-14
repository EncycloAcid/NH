package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.nh.hoopscoach.game.Coach
import com.nh.hoopscoach.game.Conference
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Season
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.game.Timeline
import com.nh.hoopscoach.game.WebNode

private fun nameOf(lg: League, t: Team) = if (lg.pro) t.name else t.school

private fun tierName(lg: League, tier: Int) = if (lg.pro) "Conference" else when (tier) { 0 -> "Power conferences"; 1 -> "Mid-majors"; else -> "Low-majors" }

/** Deep runs on file: Final Fours (titles and title-game losses included). */
private fun deepRuns(t: Team) = t.history.count { it.result == "Final Four" || it.result == "Runner-Up" || it.result == "National Champion" }

fun isFollowing(lg: League, t: Team) = t.id in lg.followTeams
fun isFollowing(lg: League, c: Coach) = c.id in lg.followCoaches

/** A follow / unfollow button for a school. */
@Composable
fun FollowTeamButton(vm: GameViewModel, lg: League, t: Team) {
    if (t.id == lg.userTeamId) return
    val on = isFollowing(lg, t)
    SmallButton(if (on) "Following" else "Follow", primary = !on) {
        vm.act { if (on) it.followTeams.remove(t.id) else it.followTeams.add(t.id) }
        vm.toast(if (on) "Stopped following ${nameOf(lg, t)}" else "Following ${nameOf(lg, t)}: it shows on your Today screen")
    }
}

/** A follow / unfollow button for a coach. */
@Composable
fun FollowCoachButton(vm: GameViewModel, lg: League, c: Coach) {
    if (c.id == lg.userCoachId) return
    val on = isFollowing(lg, c)
    SmallButton(if (on) "Following" else "Follow", primary = !on) {
        vm.act { if (on) it.followCoaches.remove(c.id) else it.followCoaches.add(c.id) }
        vm.toast(if (on) "Stopped following ${c.name}" else "Following ${c.name}: he shows on your Today screen")
    }
}

/**
 * The whole world: search any school or coach, every conference by level, the best coaches in the
 * game, and everything you follow.
 */
@Composable
fun ExploreScreen(vm: GameViewModel, lg: League) {
    var query by remember { mutableStateOf("") }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Explore", "${lg.confs.size} conferences · ${lg.teams.size} ${if (lg.pro) "teams" else "programs"} · ${lg.coaches.count { it.teamId >= 0 }} coaches", onBack = { vm.back() })
            OutlinedTextField(
                value = query, onValueChange = { query = it.take(40) }, singleLine = true,
                label = { Text("Find a school or coach") }, colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 6.dp),
            )
        }
        val q = query.trim()
        if (q.length >= 2) {
            val teams = lg.teams.filter { it.school.contains(q, true) || it.name.contains(q, true) || it.abbr.equals(q, true) }.take(12)
            val coaches = lg.coaches.filter { it.name.contains(q, true) }.take(12)
            item {
                if (teams.isEmpty() && coaches.isEmpty()) Panel { Empty("Nothing matches \"$q\".") }
                if (teams.isNotEmpty()) {
                    Section(if (lg.pro) "Teams" else "Schools")
                    GroupList(teams) { t -> TeamRowExplore(vm, lg, t) }
                }
                if (coaches.isNotEmpty()) {
                    Section("Coaches")
                    GroupList(coaches) { c -> CoachRowExplore(vm, lg, c) }
                }
            }
            return@LazyColumn
        }
        val followedTeams = lg.followTeams.mapNotNull { lg.teams.getOrNull(it) }
        val followedCoaches = lg.followCoaches.mapNotNull { id -> lg.coaches.firstOrNull { it.id == id } }
        if (followedTeams.isNotEmpty() || followedCoaches.isNotEmpty()) item {
            Section("Following")
            GroupList(followedTeams.map { it as Any } + followedCoaches) { x ->
                when (x) {
                    is Team -> TeamRowExplore(vm, lg, x)
                    is Coach -> CoachRowExplore(vm, lg, x)
                }
            }
        }
        for ((tier, confs) in lg.confs.filter { it.teamIds.isNotEmpty() }.groupBy { it.tier }.toSortedMap()) item(key = "tier$tier") {
            Section("${tierName(lg, tier)} · ${confs.size}")
            GroupList(confs.sortedByDescending { c -> c.teamIds.map { lg.team(it).prestige }.average() }) { c ->
                val leader = Season.standings(lg, c.id).firstOrNull()
                ListRow(onClick = { vm.push(Route.ConfPage(c.id)) }) {
                    Column(Modifier.weight(1f)) {
                        Text(c.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${c.teamIds.size} ${if (lg.pro) "teams" else "schools"}" + (leader?.takeIf { it.gp > 0 }?.let { " · ${nameOf(lg, it)} leads at ${it.confRecord}" } ?: ""),
                            style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Chevron()
                }
            }
        }
        item(key = "coaches") {
            Section("The best coaches in the game")
            GroupList(lg.coaches.filter { it.teamId >= 0 }.sortedByDescending { it.rating }.take(12)) { c -> CoachRowExplore(vm, lg, c) }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun TeamRowExplore(vm: GameViewModel, lg: League, t: Team) {
    ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
        TeamBadge(t, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(nameOf(lg, t), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val c = runCatching { lg.coachOf(t) }.getOrNull()
            Text("${t.record} · ${lg.conf(t.confId).abbr}" + (c?.let { " · ${it.name}" } ?: ""), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isFollowing(lg, t)) Tag("FOLLOWING", P.accent)
    }
}

@Composable
private fun CoachRowExplore(vm: GameViewModel, lg: League, c: Coach) {
    ListRow(onClick = { vm.push(Route.Web(WebNode.COACH, c.id)) }) {
        CoachBadge(c.first, c.last, 32.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(c.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val at = lg.teams.getOrNull(c.teamId)?.let { nameOf(lg, it) } ?: "Out of coaching"
            Text("$at · ${c.w}-${c.l}" + (if (c.titles > 0) " · ${c.titles} title${if (c.titles == 1) "" else "s"}" else ""), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (isFollowing(lg, c)) Tag("FOLLOWING", P.accent)
        Text("${c.rating}", style = T.numSm, color = P.accent, modifier = Modifier.padding(start = 8.dp))
    }
}

/** A conference: this season's standings, its coaches, its programs' history, and what's happened to it. */
@Composable
fun ConferencePage(vm: GameViewModel, lg: League, confId: Int) {
    val c: Conference = lg.conf(confId)
    val teams = c.teamIds.map { lg.team(it) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar(c.name, "${tierName(lg, c.tier).removeSuffix("s").removeSuffix(" conference")} · ${teams.size} ${if (lg.pro) "teams" else "schools"}", onBack = { vm.back() })
            Section("Standings")
            GroupList(Season.standings(lg, confId).withIndex().toList()) { (i, t) ->
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    Text("${i + 1}", style = T.numSm, color = P.faint, modifier = Modifier.width(28.dp))
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(nameOf(lg, t), style = T.bodyB, color = if (t.id == lg.userTeamId) P.accent else P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(t.confRecord, style = T.numSm, color = P.ink, modifier = Modifier.width(56.dp))
                    Text(t.record, style = T.small, color = P.sub)
                }
            }
        }
        item {
            Section("Coaches")
            GroupList(teams.mapNotNull { t -> runCatching { lg.coachOf(t) }.getOrNull() }.sortedByDescending { it.rating }) { co -> CoachRowExplore(vm, lg, co) }
        }
        item {
            Section("Program history")
            GroupList(teams.sortedWith(compareByDescending<Team> { it.titles }.thenByDescending { deepRuns(it) }.thenByDescending { it.tourneys })) { t ->
                ListRow(onClick = { vm.push(Route.TeamPage(t.id)) }) {
                    TeamBadge(t, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(nameOf(lg, t), style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (lg.pro) "${t.titles} titles · ${t.history.size} seasons on record"
                            else "${t.titles} titles · ${deepRuns(t)} Final Fours · ${t.tourneys} NCAA trips · ${t.confTitles} conference titles", style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        val events = lg.timeline.filter { e -> lg.teams.getOrNull(e.teamId)?.confId == confId && e.weight >= 2 }.sortedByDescending { it.season * 10 + it.weight }.take(10)
        if (events.isNotEmpty()) item {
            Section("Recent history")
            TimelineRows(vm, lg, events)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

/** On the Today screen: what the schools and coaches you follow are up to. */
@Composable
fun FollowingCard(vm: GameViewModel, lg: League) {
    val teams = lg.followTeams.mapNotNull { lg.teams.getOrNull(it) }
    val coaches = lg.followCoaches.mapNotNull { id -> lg.coaches.firstOrNull { it.id == id } }
    if (teams.isEmpty() && coaches.isEmpty()) return
    Section("Following", action = "Explore", onAction = { vm.push(Route.Explore) })
    GroupList(teams.map { it as Any } + coaches) { x ->
        when (x) {
            is Team -> {
                val last = com.nh.hoopscoach.game.Schedule.teamGames(lg, x.id).lastOrNull { it.played }
                val next = com.nh.hoopscoach.game.Schedule.teamGames(lg, x.id).firstOrNull { !it.played }
                val news = Timeline.of(lg, x.id).lastOrNull { it.season == lg.season }
                ListRow(onClick = { vm.push(Route.TeamPage(x.id)) }) {
                    TeamBadge(x, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${nameOf(lg, x)} · ${x.record}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val line = news?.title ?: listOfNotNull(
                            last?.let { g ->
                                val mine = if (g.home == x.id) g.hs else g.aws
                                val theirs = if (g.home == x.id) g.aws else g.hs
                                "${if (mine > theirs) "Beat" else "Lost to"} ${nameOf(lg, lg.team(g.opp(x.id)))} $mine-$theirs"
                            },
                            next?.let { "next: ${nameOf(lg, lg.team(it.opp(x.id)))}" },
                        ).joinToString(" · ")
                        if (line.isNotEmpty()) Text(line, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            is Coach -> {
                val t = lg.teams.getOrNull(x.teamId)
                ListRow(onClick = { vm.push(Route.Web(WebNode.COACH, x.id)) }) {
                    CoachBadge(x.first, x.last, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(x.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val news = Timeline.ofCoach(lg, x.name).lastOrNull { it.season >= lg.season - 1 }
                        Text(news?.title ?: (t?.let { "${nameOf(lg, it)} · ${it.record} this season" } ?: "Out of coaching"), style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/**
 * Phone and PC: on the PC, start receiving and read the address and code off the screen; on the phone,
 * type them in and send this career to the PC or bring the PC's career over.
 */
@Composable
fun TransferPanel(vm: GameViewModel) {
    val host = com.nh.hoopscoach.SaveTransfer.host
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Section("Phone and PC")
    if (host != null) {
        var code by remember { mutableStateOf("") }
        var addresses by remember { mutableStateOf<List<String>>(emptyList()) }
        var error by remember { mutableStateOf("") }
        androidx.compose.runtime.DisposableEffect(Unit) { onDispose { host.stop() } }
        Panel {
            if (addresses.isEmpty()) {
                Text("Move a career between this PC and your phone over your home Wi-Fi. Start here, then use Phone and PC in the phone's Settings.", style = T.small, color = P.sub)
                if (error.isNotEmpty()) Text(error, style = T.small, color = P.bad, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                PrimaryButton("Start phone transfer", Modifier.fillMaxWidth()) {
                    val c = com.nh.hoopscoach.SaveTransfer.newCode()
                    runCatching {
                        host.start(c,
                            outgoing = { kotlinx.coroutines.runBlocking { vm.saveNow(); vm.exportSlotBytes(vm.activeSlotId) } },
                            incoming = { bytes -> scope.launch { vm.importSave(bytes); vm.toast("Career received from your phone") } },
                        )
                    }.onSuccess { addresses = it; code = c; error = "" }
                        .onFailure { error = "Couldn't start: ${it.message ?: "the port may be in use"}" }
                }
            } else {
                Text("ON YOUR PHONE, ENTER", style = T.label, color = P.sub)
                addresses.forEach { Text(it, style = T.num, color = P.ink) }
                Text("Code $code", style = T.head, color = P.accent, modifier = Modifier.padding(top = 4.dp))
                Text("Keep this screen open while the phone sends or fetches. If Windows asks about network access, allow it on private networks.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Stop", Modifier.fillMaxWidth()) { host.stop(); addresses = emptyList() }
            }
        }
        return
    }
    var address by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    Panel {
        Text("Play the same career on your PC. On the PC, open Settings, Phone and PC, and tap Start; then type what it shows here.", style = T.small, color = P.sub)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(address, { address = it.take(40) }, singleLine = true, label = { Text("PC address (like 192.168.1.20:${com.nh.hoopscoach.SaveTransfer.PORT})") },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(code, { code = it.filter(Char::isDigit).take(4) }, singleLine = true, label = { Text("Code") },
            colors = fieldColors(), modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        val ready = address.isNotBlank() && code.length == 4 && !working
        Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            PrimaryButton(if (working) "Working…" else "Send to PC", Modifier.weight(1f), enabled = ready) {
                working = true
                scope.launch {
                    vm.saveNow()
                    val bytes = vm.exportSlotBytes(vm.activeSlotId)
                    val r = if (bytes == null) Result.failure(Exception("No career to send yet."))
                        else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.nh.hoopscoach.SaveTransfer.send(address, code, bytes) }
                    working = false
                    vm.toast(r.fold({ "Sent. It's in the PC's save slots now." }, { it.message ?: "Couldn't send" }))
                }
            }
            SecondaryButton("Get from PC", Modifier.weight(1f), enabled = ready) {
                working = true
                scope.launch {
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.nh.hoopscoach.SaveTransfer.fetch(address, code) }
                    working = false
                    r.fold({ vm.importSave(it); vm.toast("Career brought over from the PC") }, { vm.toast(it.message ?: "Couldn't get the career") })
                }
            }
        }
        Text("Both go into a new save slot; nothing is overwritten.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 8.dp))
    }
}

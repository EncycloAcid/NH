package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import kotlinx.coroutines.launch
import com.nh.hoopscoach.game.Era
import com.nh.hoopscoach.game.Eras
import com.nh.hoopscoach.game.Honor
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.NbaAwards
import com.nh.hoopscoach.game.Ollama
import com.nh.hoopscoach.game.Phase
import com.nh.hoopscoach.game.Player

private val NIGHT = listOf(
    "MVP", "Rookie of the Year", "Defensive Player of the Year", "Sixth Man of the Year", "Most Improved Player",
    "Clutch Player of the Year", "Coach of the Year", "Executive of the Year", "Hustle Award",
)
private val TITLES = listOf("Scoring title", "Rebounding title", "Assists title", "Steals title", "Blocks title")
private val PLAYOFFS = listOf("Eastern Conference Finals MVP", "Western Conference Finals MVP", "Finals MVP")
private val CHARACTER = listOf("Sportsmanship Award", "Teammate of the Year", "Citizenship Award", "Social Justice Champion")
private val ALL_STAR = listOf("All-Star MVP", "Rising Stars MVP", "3-Point Contest winner", "Slam Dunk Contest winner")
private val TIERS = listOf("1st" to "FIRST TEAM", "2nd" to "SECOND TEAM", "3rd" to "THIRD TEAM")

private fun confLabel(conf: Int) = when (conf) { 0 -> "East"; 1 -> "West"; else -> "" }

/** One honor: the award (and its trophy), the winner, his team and his numbers. Tap a player to open him. */
@Composable
private fun HonorRow(vm: GameViewModel, lg: League, h: Honor, era: Era, title: String? = h.award) {
    val me = h.pid >= 0 && h.pid == lg.heroId
    val mine = me || (h.pid < 0 && h.teamId == lg.userTeamId)
    val t = lg.team(h.teamId)
    val open = if (h.pid >= 0 && lg.playerOrNull(h.pid) != null) ({ vm.openPlayer(h.pid) }) else null
    ListRow(onClick = open, modifier = if (mine) Modifier.background(P.accentSoft) else Modifier) {
        TeamBadge(t, 30.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                val conf = confLabel(h.conf)
                Text((if (conf.isNotEmpty()) "$conf · " else "") + NbaAwards.label(title).uppercase(), style = T.label, color = P.accent)
                val trophy = NbaAwards.trophy(h.award, era)
                if (trophy.isNotEmpty()) Text(trophy, style = T.small.copy(fontSize = 11.sp), color = P.faint)
            }
            Text(h.name + if (me) " (you)" else "", style = T.bodyB, color = if (me) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.name + if (h.line.isNotEmpty()) " · ${h.line}" else "", style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** A team of five, one line each. */
@Composable
private fun FiveRow(vm: GameViewModel, lg: League, h: Honor) {
    val me = h.pid == lg.heroId
    val p = lg.playerOrNull(h.pid)
    ListRow(onClick = if (p != null) ({ vm.openPlayer(h.pid) }) else null, modifier = if (me) Modifier.background(P.accentSoft) else Modifier, padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        TeamBadge(lg.team(h.teamId), 24.dp)
        Spacer(Modifier.width(10.dp))
        Text(h.name + if (me) " (you)" else "", style = T.bodyB, color = if (me) P.accent else P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(p?.posLabel ?: "", style = T.small, color = P.sub)
    }
}

private fun LazyListScope.fives(vm: GameViewModel, lg: League, all: List<Honor>, prefix: String, title: String) {
    val tiers = TIERS.map { (key, label) -> label to all.filter { it.award == "$prefix $key" } }.filter { it.second.isNotEmpty() }
    if (tiers.isEmpty()) return
    item {
        Section(title)
        for ((label, five) in tiers) {
            Text(label, style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = Gutter + 4.dp, top = 6.dp, bottom = 4.dp))
            GroupList(five, inset = 48.dp) { h -> FiveRow(vm, lg, h) }
        }
    }
}

private fun LazyListScope.honors(vm: GameViewModel, lg: League, title: String, rows: List<Honor>, era: Era, note: String = "") {
    if (rows.isEmpty()) return
    item {
        Section(title)
        GroupList(rows, inset = 54.dp) { h -> HonorRow(vm, lg, h, era) }
        if (note.isNotEmpty()) Text(note, style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
}

/** The League tab's Awards page: every honor of a season, this one or any before. */
fun LazyListScope.proAwards(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    val era = Eras.of(lg)
    val seasons = (ps.honors.map { it.season } + lg.season).distinct().sortedDescending()
    val season = vm.awardsSeason.takeIf { it in seasons } ?: lg.season
    val all = NbaAwards.of(lg, season)
    fun pick(names: List<String>) = names.flatMap { n -> all.filter { it.award == n } }
    item {
        if (seasons.size > 1) Picker("Season", "$season", seasons.map { "$it" to if (it == lg.season) "This season" else "" }, Modifier.padding(horizontal = Gutter).fillMaxWidth()) { vm.awardsSeason = seasons[it] }
        if (all.isEmpty()) Panel {
            Text("No honors yet", style = T.head, color = P.ink)
            Text(
                (if (NbaAwards.byConference(era)) "A Player of the Week for each conference" else "A Player of the Week") +
                    " every four game days, and Players, Rookies and Coaches of the Month. Awards night comes after the regular season: the MVP vote, the other major awards, the All-NBA, All-Defensive and All-Rookie teams and the statistical titles.",
                style = T.small, color = P.sub,
            )
        }
    }
    val night = pick(NIGHT)
    honors(vm, lg, "Awards night", night, era)
    if (lg.ollama.enabled && lg.ollama.awardCeremonies && night.isNotEmpty()) {
        item {
            AiAwardCeremonyCard(lg, night.first())
        }
    }
    if (season == lg.season && ps.mvpVotes.isNotEmpty()) item {
        Section("MVP vote")
        GroupList(ps.mvpVotes.take(10).withIndex().toList()) { (i, v) ->
            ListRow(onClick = { vm.openPlayer(v.pid) }, modifier = if (v.pid == lg.heroId) Modifier.background(P.accentSoft) else Modifier) {
                Text("${i + 1}", style = T.numSm, color = P.accent, modifier = Modifier.width(28.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${lg.team(v.teamId).name} · ${v.firsts} first-place", style = T.small, color = P.sub, maxLines = 1)
                }
                Text("${v.points}", style = T.numSm, color = P.ink)
            }
        }
        Text("100 voters rank five players: 10, 7, 5, 3 and 1 points.", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    }
    fives(vm, lg, all, "All-NBA", "All-NBA")
    fives(vm, lg, all, "All-Defensive", "All-Defensive")
    fives(vm, lg, all, "All-Rookie", "All-Rookie")
    honors(vm, lg, "Statistical titles", pick(TITLES), era, "Per game, for players in 70% of the games.")
    honors(vm, lg, "Playoffs", pick(PLAYOFFS), era)
    honors(vm, lg, "NBA Cup", pick(listOf("NBA Cup MVP")), era)
    all.filter { it.award == "NBA Cup All-Tournament" }.takeIf { it.isNotEmpty() }?.let { five ->
        item {
            Text("ALL-TOURNAMENT TEAM", style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = Gutter + 4.dp, top = 10.dp, bottom = 4.dp))
            GroupList(five, inset = 48.dp) { h -> FiveRow(vm, lg, h) }
        }
    }
    val stars = all.filter { it.award == "All-Star starter" || it.award == "All-Star reserve" }
    if (stars.isNotEmpty()) item {
        Section("All-Stars")
        for (conf in stars.map { it.conf }.distinct().sorted()) {
            for (kind in listOf("All-Star starter" to "STARTERS", "All-Star reserve" to "RESERVES")) {
                val list = stars.filter { it.conf == conf && it.award == kind.first }
                if (list.isEmpty()) continue
                Text("${lg.confs.getOrNull(conf)?.name?.uppercase() ?: ""} · ${kind.second}", style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = Gutter + 4.dp, top = 6.dp, bottom = 4.dp))
                GroupList(list, inset = 48.dp) { h -> FiveRow(vm, lg, h) }
            }
        }
    }
    honors(vm, lg, "All-Star Weekend", pick(ALL_STAR), era)
    honors(vm, lg, "Off the court", pick(CHARACTER), era, "Voted by players, coaches and the league office.")
    val months = NbaAwards.MONTHS.filter { m -> all.any { it.period == m } }.reversed()
    if (months.isNotEmpty()) item {
        Section("Monthly honors")
        for (m in months) {
            val rows = listOf("Player of the Month", "Rookie of the Month", "Coach of the Month").flatMap { a -> all.filter { it.period == m && it.award == a }.sortedBy { it.conf } }
            Text(m.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.faint, modifier = Modifier.padding(start = Gutter + 4.dp, top = 6.dp, bottom = 4.dp))
            GroupList(rows, inset = 54.dp) { h -> HonorRow(vm, lg, h, era) }
        }
    }
    val weeks = all.filter { it.award == "Player of the Week" }.groupBy { it.period }.entries.sortedByDescending { it.key.removePrefix("Week ").toIntOrNull() ?: 0 }
    if (weeks.isNotEmpty()) item {
        Section("Players of the Week")
        GroupList(weeks.map { it.value.sortedBy { h -> h.conf } }, inset = 16.dp) { list ->
            Column(Modifier.fillMaxWidth().then(if (list.any { it.pid == lg.heroId }) Modifier.background(P.accentSoft) else Modifier).padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(list.first().period.uppercase(), style = T.label, color = P.accent)
                for (h in list) {
                    val conf = confLabel(h.conf)
                    Text((if (conf.isNotEmpty()) "$conf: " else "") + h.name + " · " + lg.team(h.teamId).mascot + if (h.pid == lg.heroId) " (you)" else "",
                        style = T.bodyB, color = if (h.pid == lg.heroId) P.accent else P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(h.line, style = T.small, color = P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    item { Spacer(Modifier.height(16.dp)) }
}

/** Today: the latest Players of the Week, and the month's honors for a few days after they're named. */
@Composable
fun HonorsStrip(vm: GameViewModel, lg: League) {
    val ps = lg.proState ?: return
    if (lg.phase != Phase.REGULAR || ps.week == 0) return
    val era = Eras.of(lg)
    val all = NbaAwards.of(lg)
    val week = all.filter { it.award == "Player of the Week" && it.period == "Week ${ps.week}" }.sortedBy { it.conf }
    val monthName = NbaAwards.MONTHS.getOrNull(ps.month - 1)
    val month = all.filter { it.period == monthName && it.award == "Player of the Month" && lg.day - it.day <= 5 }.sortedBy { it.conf }
    if (week.isEmpty() && month.isEmpty()) return
    Section(if (month.isNotEmpty()) "League honors" else "Player of the Week · Week ${ps.week}", action = "All awards", onAction = { vm.leagueTab = 4; vm.awardsSeason = lg.season; vm.select(Tab.LEAGUE) })
    GroupList(month + week, inset = 54.dp) { h -> HonorRow(vm, lg, h, era, title = "${h.award} · ${h.period}") }
}

/** His honors grouped by award, newest first: "Player of the Week ×4" and the seasons. */
@Composable
fun TrophyCase(p: Player, limit: Int = 16) {
    val groups = NbaAwards.trophyCase(p).sortedByDescending { it.second.max() }
    if (groups.isEmpty()) return
    Section("Honors")
    GroupList(groups.take(limit)) { (a, years) ->
        ListRow {
            Column(Modifier.weight(1f)) {
                Text(NbaAwards.label(awardName(a)), style = T.bodyB, color = P.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(years.distinct().sorted().joinToString(", "), style = T.small, color = P.sub, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (years.size > 1) Text("×${years.size}", style = T.numSm, color = P.accent)
        }
    }
    if (groups.size > limit) Text("And ${groups.size - limit} more", style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
}

/** Award ceremony acceptance speech and media tribute card labeled by AI. */
@Composable
fun AiAwardCeremonyCard(lg: League, honor: Honor) {
    var speech by remember(honor.award, honor.pid, honor.season) { mutableStateOf<Ollama.AwardSpeech?>(null) }
    var loading by remember(honor.award, honor.pid, honor.season) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(honor.award, honor.pid, honor.season) {
        val cacheKey = "award_${honor.award}_${honor.pid}_${honor.season}_${lg.ollama.activeModelName}"
        Ollama.Cache.get<Ollama.AwardSpeech>(cacheKey)?.let { speech = it }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(P.surface)
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("AWARD CEREMONY PODIUM", style = T.label.copy(fontSize = 10.sp), color = P.gold)
            Spacer(Modifier.width(6.dp))
            Tag("✨ LABELED BY AI · ${lg.ollama.providerDisplayName.uppercase()}", P.good)
            Spacer(Modifier.weight(1f))
            if (speech == null && !loading) {
                SmallButton("Generate Ceremony", primary = true) {
                    loading = true
                    scope.launch {
                        val res = Ollama.generateAwardSpeech(lg.ollama, lg, honor)
                        loading = false
                        res.onSuccess { speech = it }
                    }
                }
            }
        }
        if (loading) {
            Spacer(Modifier.height(6.dp))
            Text("AI is generating acceptance speech and columnist tribute...", style = T.small, color = P.accent)
        } else if (speech != null) {
            val sp = speech!!
            Spacer(Modifier.height(8.dp))
            Text(sp.awardTitle, style = T.head, color = P.ink)
            Spacer(Modifier.height(6.dp))
            Text("ACCEPTANCE SPEECH", style = T.label.copy(fontSize = 9.sp), color = P.accent)
            Spacer(Modifier.height(2.dp))
            Text("\u201c${sp.acceptanceSpeech}\u201d", style = T.bodyB.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), color = P.ink)
            Spacer(Modifier.height(6.dp))
            Text("MEDIA TRIBUTE", style = T.label.copy(fontSize = 9.sp), color = P.sub)
            Spacer(Modifier.height(2.dp))
            Text(sp.mediaTribute, style = T.small, color = P.faint)
        }
    }
}

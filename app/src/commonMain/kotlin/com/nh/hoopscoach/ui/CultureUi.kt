package com.nh.hoopscoach.ui

import androidx.compose.ui.unit.sp

import androidx.compose.foundation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Culture
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

/** The locker room: chemistry, the captains the players voted for, and who's dragging it down. */
@Composable
fun LockerRoomPanel(vm: GameViewModel, lg: League, t: Team) {
    if (lg.pro) return
    val chem = Culture.chemistry(lg, t)
    val color = when { chem >= 60 -> P.good; chem >= 42 -> P.warn; else -> P.bad }
    val captains = t.captains.mapNotNull { lg.playerOrNull(it)?.takeIf { p -> p.teamId == t.id } }
    val apples = lg.rosterOf(t).filter { Culture.badApple(it) }
    Section("Locker room")
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Culture.label(chem), style = T.head, color = color)
                Text("Team chemistry $chem/100", style = T.small, color = P.sub)
            }
            Ring(chem, 48.dp, label = "CHEM")
        }
        Spacer(Modifier.height(8.dp))
        Bar(chem / 100f, Modifier.fillMaxWidth(), color = color, height = 6.dp)
        if (captains.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("CAPTAINS · VOTED BY THE PLAYERS", style = T.label, color = P.accent)
            captains.forEachIndexed { i, p ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(p.name, style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${t.captainVotes.getOrNull(i) ?: 0} votes · leads ${Culture.leadership(lg, t, p)}", style = T.small, color = P.sub)
                }
            }
        }
        if (captains.isEmpty()) Text("The players vote for captains when the season starts.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 10.dp))
        if (apples.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("DRAGGING THE ROOM DOWN", style = T.label, color = P.bad)
            Text(apples.joinToString(", ") { it.name }, style = T.small, color = P.ink)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                chem >= 60 -> "A together team plays a little better: cleaner possessions, better looks."
                chem >= 42 -> "Strong captains pull unhappy teammates back up. Playing time and NIL fix the rest."
                else -> "A fractured room costs you on the floor and drags every player's mood down. Fix the unhappy players first."
            },
            style = T.small, color = P.faint,
        )
    }
}

/** Mentors: pair each freshman with a junior or senior at his position; the freshman grows faster. */
@Composable
fun MentorsPanel(vm: GameViewModel, lg: League, t: Team) {
    if (lg.pro || lg.playerMode) return
    val freshmen = lg.rosterOf(t).filter { it.year == 1 }
    if (freshmen.isEmpty()) return
    var picking by remember { mutableStateOf<Player?>(null) }
    Section("Mentors")
    GroupList(freshmen) { f ->
        val m = lg.playerOrNull(f.mentorId)?.takeIf { it.teamId == t.id }
        ListRow(onClick = { picking = f }) {
            Column(Modifier.weight(1f)) {
                Text("${f.name} · ${f.posLabel}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(if (m != null) "Mentored by ${m.name} (${m.cls}) · +20% practice growth" else "No mentor yet", style = T.small, color = if (m != null) P.good else P.sub)
            }
            Chevron()
        }
    }
    Text("A mentor speeds up a freshman's growth all season and gives him a bigger jump that summer. Mentors enjoy the role.",
        style = T.small, color = P.faint, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp))
    picking?.let { f ->
        val options = Culture.mentorOptions(lg, t, f)
        AlertDialog(
            onDismissRequest = { picking = null }, containerColor = P.surface,
            title = { Text("A mentor for ${f.short}", style = T.head, color = P.ink) },
            text = {
                Column {
                    if (options.isEmpty()) Text("No junior or senior ${f.posLabel} is free to mentor him.", style = T.body, color = P.sub)
                    options.forEach { m ->
                        ListRow(onClick = { vm.act { it.playerOrNull(f.id)?.mentorId = m.id }; picking = null }) {
                            Column(Modifier.weight(1f)) {
                                Text(m.name, style = T.bodyB, color = P.ink)
                                Text("${m.cls} · leads ${Culture.leadership(lg, t, m)}" + m.traits.filter { it.good }.take(2).joinToString("") { " · ${it.label}" }, style = T.small, color = P.sub)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (f.mentorId >= 0) TextButton({ vm.act { it.playerOrNull(f.id)?.mentorId = -1 }; picking = null }) { Text("Remove mentor", color = P.bad) }
            },
            dismissButton = { TextButton({ picking = null }) { Text("Close", color = P.sub) } },
        )
    }
}

/** Midnight Madness, before the first game: how big a show to put on. */
@Composable
fun MidnightMadnessCard(vm: GameViewModel, lg: League) {
    if (!Culture.madnessOpen(lg)) return
    val t = lg.user
    com.nh.hoopscoach.game.Business.ensure(lg, t)
    val k = Culture.hype(t)
    Section("Midnight Madness")
    Panel(color = P.hero) {
        Text("THE SEASON TIPS OFF TONIGHT", style = T.label, color = heroKicker)
        Text("How big a show?", style = T.h2, color = P.onHero)
        Text("Your program's hype multiplies the night: ${"%.1f".format(k)}×. Upgrade budget: ${money(t.cash.toInt())}.", style = T.small, color = P.onHero.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        for (s in Culture.SHOWS) {
            val afford = t.cash >= s.cost
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.name + if (s.cost > 0) " · ${money(s.cost.toInt())}" else "", style = T.bodyB, color = P.onHero)
                    Text(s.blurb, style = T.small, color = P.onHero.copy(alpha = 0.8f))
                }
                Spacer(Modifier.width(8.dp))
                SmallButton("Host", primary = true, enabled = afford) {
                    vm.act { Culture.midnightMadness(it, s.id) }
                    vm.toast(lg.madnessNote.ifEmpty { "Midnight Madness is on" })
                }
            }
        }
    }
}

/** The summer tour, every four years, before the season's first game. */
@Composable
fun SummerTourCard(vm: GameViewModel, lg: League) {
    if (!com.nh.hoopscoach.game.Tours.available(lg)) return
    val t = lg.user
    com.nh.hoopscoach.game.Business.ensure(lg, t)
    Section("Summer foreign tour", action = "Not this year", onAction = { vm.act { it.tourDeclined = it.season } })
    Panel {
        Text("Once every four years, the NCAA lets a team travel abroad: a few games, extra practice, and a trip the players never forget. Upgrade budget: ${money(t.cash.toInt())}.",
            style = T.small, color = P.sub)
        for (tour in com.nh.hoopscoach.game.Tours.ALL) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${tour.place} · ${money(tour.cost.toInt())}", style = T.bodyB, color = P.ink)
                    Text(tour.blurb, style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton("Go", primary = true, enabled = t.cash >= tour.cost) {
                    var note: String? = null
                    vm.act { note = com.nh.hoopscoach.game.Tours.go(it, tour.id) }
                    vm.toast(note ?: "Not enough in the upgrade budget")
                }
            }
        }
    }
}

/** Cinderella watch, on the tournament's Today screen. */
@Composable
fun CinderellaCard(vm: GameViewModel, lg: League) {
    val r = com.nh.hoopscoach.game.Cinderella.peek(lg) ?: return
    Section("Cinderella watch", action = "The story", onAction = { vm.push(Route.Cinderella) })
    Panel(onClick = { vm.push(Route.Cinderella) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TeamBadge(r.team, 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("No. ${r.seed} ${r.team.school}", style = T.head, color = P.ink)
                Text(when {
                    lg.ncaa?.champion == r.team.id -> "National champions"
                    r.out != null -> "Out after ${r.wins.size} win${if (r.wins.size == 1) "" else "s"}"
                    else -> "${r.wins.size} win${if (r.wins.size == 1) "" else "s"} and still dancing"
                }, style = T.small, color = if (r.out == null) P.gold else P.sub)
            }
            Chevron()
        }
    }
}

/** The Cinderella's own story page. */
@Composable
fun CinderellaScreen(vm: GameViewModel, lg: League) {
    val r = com.nh.hoopscoach.game.Cinderella.peek(lg)
    androidx.compose.foundation.lazy.LazyColumn(androidx.compose.ui.Modifier.fillMaxWidth()) {
        item { BackBar("Cinderella watch", r?.let { "No. ${it.seed} ${it.team.school}" } ?: "", onBack = { vm.back() }) }
        if (r == null) {
            item { Panel { Empty("No Cinderella yet. A low seed has to win a game first.") } }
            return@LazyColumn
        }
        item {
            Panel(color = P.hero, onClick = { vm.push(Route.TeamPage(r.team.id)) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TeamBadge(r.team, 56.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("A ${r.seed} SEED IN MARCH", style = T.label, color = heroKicker)
                        Text(r.team.school.uppercase(), style = T.title, color = P.onHero, maxLines = 2)
                        Text("${r.team.record} · ${lg.coachOf(r.team).name}", style = T.small, color = P.onHero.copy(alpha = 0.85f))
                    }
                }
            }
        }
        item {
            Section("The story")
            Panel {
                com.nh.hoopscoach.game.Cinderella.story(lg, r).forEachIndexed { i, para ->
                    if (i > 0) Spacer(Modifier.height(10.dp))
                    Text(para, style = T.body, color = P.ink)
                }
            }
        }
        val games = (r.wins + listOfNotNull(r.out)).sortedBy { it.day }
        if (games.isNotEmpty()) item {
            Section("The run")
            GroupList(games) { g ->
                val o = lg.team(g.opp(r.team.id))
                val won = g.winner() == r.team.id
                ListRow(onClick = { vm.push(Route.Box(g.id)) }) {
                    TeamBadge(o, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${lg.ncaa?.seeds?.get(o.id)?.let { "No. $it " } ?: ""}${o.school}", style = T.bodyB, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${if (won) "W" else "L"} ${g.scoreFor(r.team.id)}-${g.scoreFor(o.id)}", style = T.numSm, color = if (won) P.good else P.bad)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** The coach's weekly radio show, one caller at a time. */
@Composable
fun RadioShowCard(vm: GameViewModel, lg: League) {
    val R = com.nh.hoopscoach.game.RadioShow
    if (!R.due(lg)) return
    val calls = R.calls(lg)
    val i = lg.radioAnswered
    val call = calls.getOrNull(i) ?: return
    // With AI on, the caller's question is written fresh in his own voice (the answers and what they do stay the game's).
    val key = R.aiKey(lg, i)
    val ai = lg.radioAi[key]
    if (ai == null && lg.ollama.enabled) androidx.compose.runtime.LaunchedEffect(key) {
        com.nh.hoopscoach.game.Ollama.generate(lg.ollama, R.aiPrompt(lg, call),
            system = "You write short, natural lines for sports-radio callers. Plain text only.", temperature = 0.8, numPredict = 90)
            .onSuccess { text ->
                val clean = text.trim().trim('"').lines().firstOrNull { it.isNotBlank() }?.take(240)
                if (!clean.isNullOrBlank()) vm.act { it.radioAi[key] = clean; while (it.radioAi.size > 24) it.radioAi.remove(it.radioAi.keys.first()) }
            }
    }
    Section("The coach's show · call ${i + 1} of ${calls.size}", action = "Skip this week", onAction = { vm.act { R.skip(it) } })
    Panel {
        Text("ON THE LINE: ${call.caller.uppercase()}", style = T.label, color = P.accent)
        if (call.callback != null) Text("REMEMBERS WHAT YOU SAID: \"${call.callback.text}\"", style = T.label.copy(fontSize = 10.sp), color = P.bad, modifier = Modifier.padding(top = 4.dp))
        Text("\"${ai ?: call.question}\"", style = T.bodyB, color = P.ink, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp))
        call.answers.forEachIndexed { ai, a ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = R.answer(it, i, ai) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Text(a.text, style = T.body, color = P.ink, modifier = Modifier.weight(1f))
            }
        }
        Text("What you say moves the fans, your rivals and your bosses.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** The user's NCAA case, step by step, with the decision that's his at each step. */
@Composable
fun NcaaCaseCard(vm: GameViewModel, lg: League) {
    val N = com.nh.hoopscoach.game.NcaaCases
    val c = N.mine(lg) ?: return
    val steps = listOf("rumor" to "Rumors", "investigation" to "Investigation", "ruling" to "Ruling", "appeal" to "Appeal")
    val at = steps.indexOfFirst { it.first == c.stage }.coerceAtLeast(0)
    Section("NCAA case")
    Panel(color = mix(P.surface, P.bad, if (P.dark) 0.14f else 0.07f)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            steps.forEachIndexed { i, (_, label) ->
                Column(Modifier.weight(1f)) {
                    androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().height(4.dp).background(if (i <= at) P.bad else P.line, androidx.compose.foundation.shape.RoundedCornerShape(2.dp)))
                    Text(label.uppercase(), style = T.label.copy(fontSize = 9.sp), color = if (i == at) P.bad else P.sub, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("The NCAA and ${c.what}", style = T.head, color = P.ink)
        when (c.stage) {
            "rumor" -> Text("Reports are out there. If the NCAA opens a case about a week into the season, you'll choose how to handle it.", style = T.small, color = P.sub)
            "investigation" -> if (c.stance.isEmpty()) {
                Text("The ruling comes in late February. How do you handle the investigation?", style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                ListRow(onClick = { vm.act { N.choose(it, "cooperate") } }) {
                    Column(Modifier.weight(1f)) {
                        Text("Cooperate fully", style = T.bodyB, color = P.ink)
                        Text("A small hit to job security now. Half the time a major case is knocked down to a minor one, and appeals go better.", style = T.small, color = P.sub)
                    }
                }
                ListRow(onClick = { vm.act { N.choose(it, "stonewall") } }) {
                    Column(Modifier.weight(1f)) {
                        Text("Stonewall", style = T.bodyB, color = P.ink)
                        Text("40% chance the case falls apart with no penalties. Otherwise it's ruled major and your job security takes a big hit.", style = T.small, color = P.sub)
                    }
                }
            } else Text(if (c.stance == "cooperate") "You're cooperating. The ruling comes in late February." else "You're fighting it. The ruling comes in late February.", style = T.small, color = P.sub)
            "ruling" -> {
                Text(c.outcome + " " + com.nh.hoopscoach.game.WorldEvents.sanctionLine(lg, lg.user), style = T.small, color = P.bad)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SecondaryButton("Accept it", Modifier.weight(1f)) { vm.act { N.accept(it) } }
                    PrimaryButton("Appeal", Modifier.weight(1f)) { vm.act { N.appeal(it) } }
                }
                Text("An appeal is decided before Selection Sunday: ${if (c.stance == "cooperate") "40" else "25"}% to lift the ban or win a scholarship back; if it fails, one more scholarship is lost.",
                    style = T.small, color = P.faint, modifier = Modifier.padding(top = 6.dp))
            }
            "appeal" -> Text("Your appeal is in. A decision comes before Selection Sunday.", style = T.small, color = P.sub)
        }
    }
}

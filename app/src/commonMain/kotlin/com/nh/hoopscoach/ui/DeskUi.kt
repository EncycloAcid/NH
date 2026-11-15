package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.CoachDesk
import com.nh.hoopscoach.game.League

/** The coach's desk on Today: the postgame presser, media day, senior night, the mailbag and the preseason call. */
@Composable
fun CoachDeskCards(vm: GameViewModel, lg: League) {
    if (!CoachDesk.pressActive(lg)) return
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    if (CoachDesk.active(lg) && CoachDesk.predictionDue(lg)) PredictionCard(vm, lg)
    if (com.nh.hoopscoach.game.Clubhouse.bracketDue(lg)) BracketCard(vm, lg)
    com.nh.hoopscoach.game.Clubhouse.bigGame(lg)?.let { PregameCard(vm, lg, it) }
    com.nh.hoopscoach.game.Clubhouse.incident(lg)?.let { IncidentCard(vm, lg, it) }
    com.nh.hoopscoach.game.OffCourt.booster(lg)?.let { BoosterCard(vm, lg, it) }
    for (pr in CoachDesk.current(lg)) DeskPrompt(vm, lg, pr)
}

@Composable
private fun DeskPrompt(vm: GameViewModel, lg: League, pr: CoachDesk.Prompt) {
    val title = when (pr.kind) { "press" -> "Press conference"; "media" -> "Media day"; "senior" -> "Senior night"; else -> "The mailbag" }
    Section(title + if (pr.steps > 1) " · ${pr.step + 1} of ${pr.steps}" else "", action = "Skip", onAction = { vm.act { CoachDesk.skip(it, pr) } })
    Panel {
        Text(pr.label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent)
        Text((if (pr.kind == "mail") "FROM: " else "ASKED BY ") + pr.who.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(top = 2.dp))
        Text(pr.question, style = T.bodyB, color = P.ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        pr.choices.forEachIndexed { i, ch ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = CoachDesk.answer(it, pr, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(ch.text, style = T.body, color = P.ink)
                    effects(ch).takeIf { it.isNotEmpty() }?.let { Text(it, style = T.small.copy(fontSize = 11.sp), color = P.faint) }
                }
            }
        }
        Text(when (pr.kind) {
            "press" -> "What you say at the podium moves the fans, your bosses, your players and your rivals."
            "media" -> "Media day sets the tone for the season. Recruits are listening too."
            "senior" -> "The seniors and their families will remember how tonight went."
            else -> "Little gestures add up."
        }, style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A hint of what an answer might move, without the numbers. */
private fun effects(ch: CoachDesk.Choice): String = listOfNotNull(
    if (ch.fans >= 3) "fans ↑" else if (ch.fans <= -1) "fans ↓" else null,
    if (ch.security >= 2) "bosses ↑" else if (ch.security < 0) "bosses ↓" else null,
    if (ch.mood >= 4) "players ↑" else if (ch.mood < 0) "players ↓" else null,
    if (ch.heat >= 2) "rivalry heats up" else if (ch.heat < 0) "rivalry cools" else null,
    if (ch.recruits >= 2) "recruits ↑" else null,
).joinToString(" · ")

@Composable
private fun PredictionCard(vm: GameViewModel, lg: League) {
    Section("Your preseason call", action = "No prediction", onAction = { vm.act { it.deskPredSeason = it.season; it.deskPredBet = "" } })
    Panel {
        Text("The reporters want a prediction on the record. Make one, and it's graded at season's end: hit it and the bosses love you; miss and they remember.",
            style = T.small, color = P.sub)
        Spacer(Modifier.height(6.dp))
        for (b in CoachDesk.bets(lg)) ListRow(onClick = { vm.act { CoachDesk.predict(it, b.id) }; vm.toast("On the record: \"${b.label}.\"") }) {
            Column(Modifier.weight(1f)) {
                Text("\"${b.label}.\"", style = T.bodyB, color = P.ink)
                Text(b.detail, style = T.small, color = P.sub)
            }
        }
    }
}

// ---------- the clubhouse ----------

@Composable
private fun IncidentCard(vm: GameViewModel, lg: League, inc: com.nh.hoopscoach.game.Clubhouse.Incident) {
    val C = com.nh.hoopscoach.game.Clubhouse
    Section("Locker room", action = "Let it go", onAction = { vm.act { C.skipIncident(it) } })
    Panel(color = mix(P.surface, P.warn, if (P.dark) 0.12f else 0.06f)) {
        Text(inc.title.uppercase(), style = T.label, color = P.warn)
        lg.playerOrNull(inc.playerId)?.let { p ->
            androidx.compose.foundation.layout.Row(Modifier.padding(top = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                PlayerJersey(p, lg.user, 40.dp)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 10.dp))
                Column {
                    Text(p.name, style = T.bodyB, color = P.ink)
                    Text("${p.posLabel} · ${p.cls} · mood ${p.happiness}", style = T.small, color = P.sub)
                }
            }
        }
        Text(inc.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
        inc.options.forEachIndexed { i, op ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = C.resolve(it, inc, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(op.text, style = T.body, color = P.ink)
                    val hints = listOfNotNull(
                        if (op.suspend > 0) "sits ${op.suspend} game${if (op.suspend == 1) "" else "s"}" else null,
                        if (op.mood >= 4) "he's happier" else if (op.mood <= -4) "he won't like it" else null,
                        if (op.team >= 2) "room respects it" else if (op.team <= -2) "room notices" else null,
                        if (op.security >= 2) "bosses ↑" else if (op.security < 0) "bosses ↓" else null,
                        if (op.fans >= 2) "fans ↑" else null,
                    )
                    if (hints.isNotEmpty()) Text(hints.joinToString(" · "), style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        }
    }
}

@Composable
private fun PregameCard(vm: GameViewModel, lg: League, g: com.nh.hoopscoach.game.Game) {
    val C = com.nh.hoopscoach.game.Clubhouse
    Section("Pregame speech", action = "No speech", onAction = { vm.act { it.deskSpeechGame = g.id } })
    Panel {
        Text(C.why(lg, g).uppercase(), style = T.label, color = P.accent)
        Text("The locker room is quiet. Everybody's looking at you.", style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp, bottom = 4.dp))
        for (s in C.speeches(lg, g)) ListRow(onClick = {
            var reaction = ""
            vm.act { reaction = C.speak(it, g, s) }
            if (reaction.isNotEmpty()) vm.toast(reaction)
        }) {
            Column(Modifier.weight(1f)) {
                Text(s.label.uppercase(), style = T.label.copy(fontSize = 10.sp), color = P.accent)
                Text("\"${s.text}\"", style = T.body, color = P.ink)
            }
        }
        Text("A good speech lifts the room, and a happy room plays better together.", style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun BracketCard(vm: GameViewModel, lg: League) {
    val C = com.nh.hoopscoach.game.Clubhouse
    val b = lg.ncaa ?: return
    Section("Bracket challenge", action = "Not this year", onAction = { vm.act { it.deskBracketSeason = it.season; it.deskBracketPick = -1 } })
    Panel {
        Text("It's Selection Sunday. Everybody in the country is filling out a bracket, the coaches too. Who wins it all?", style = T.small, color = P.sub)
        Text("Get it right and the fans love it (and the bosses smile).", style = T.small, color = P.faint, modifier = Modifier.padding(bottom = 6.dp))
        for (t in C.bracketChoices(lg)) ListRow(onClick = { vm.act { C.pickChampion(it, t.id) }; vm.toast("Locked in: ${t.school}.") }) {
            Text("${b.seeds[t.id] ?: ""}", style = T.numSm, color = if (t.id == lg.userTeamId) P.accent else P.sub, modifier = Modifier.width(28.dp))
            TeamBadge(t, 28.dp)
            androidx.compose.foundation.layout.Spacer(Modifier.width(10.dp))
            Text(t.school, style = T.bodyB, color = if (t.id == lg.userTeamId) P.accent else P.ink, modifier = Modifier.weight(1f))
            Text(t.record, style = T.small, color = P.sub)
        }
    }
}

// ---------- off the court ----------

@Composable
private fun BoosterCard(vm: GameViewModel, lg: League, rq: com.nh.hoopscoach.game.OffCourt.Request) {
    val O = com.nh.hoopscoach.game.OffCourt
    Section("A booster calls", action = "Let it ring", onAction = { vm.act { O.skipBooster(it) } })
    Panel(color = mix(P.surface, P.gold, if (P.dark) 0.12f else 0.07f)) {
        Text(rq.title.uppercase(), style = T.label, color = P.gold)
        Text("FROM: ${rq.who.uppercase()}", style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(top = 2.dp))
        Text("\"${rq.text}\"", style = T.body, color = P.ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        rq.asks.forEachIndexed { i, a ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = O.answerBooster(it, rq, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(a.text, style = T.body, color = P.ink)
                    val hints = listOfNotNull(
                        if (a.cash > 0) "+${money(a.cash)}" else if (a.cash < 0) "${money(-a.cash)} less" else null,
                        if (a.security >= 1) "bosses ↑" else if (a.security < 0) "bosses ↓" else null,
                        if (a.fans >= 2) "fans ↑" else if (a.fans < 0) "fans ↓" else null,
                        if (a.mood >= 2) "players ↑" else if (a.mood < 0) "players ↓" else null,
                        if (a.recruits >= 2) "recruits ↑" else null,
                    )
                    if (hints.isNotEmpty()) Text(hints.joinToString(" · "), style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        }
    }
}

/** Player careers: life off the court. */
@Composable
fun LifeCard(vm: GameViewModel, lg: League) {
    val O = com.nh.hoopscoach.game.OffCourt
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val l = O.life(lg) ?: return
    Section("Off the court", action = "Ignore", onAction = { vm.act { O.skipLife(it) } })
    Panel {
        Text(l.title.uppercase(), style = T.label, color = P.accent)
        Text(l.text, style = T.body, color = P.ink, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        l.moves.forEachIndexed { i, mv ->
            ListRow(onClick = {
                var reaction = ""
                vm.act { reaction = O.live(it, l, i) }
                if (reaction.isNotEmpty()) vm.toast(reaction)
            }) {
                Column(Modifier.weight(1f)) {
                    Text(mv.text, style = T.body, color = P.ink)
                    val hints = listOfNotNull(
                        if (mv.followers >= 0.03) "followers ↑" else null,
                        if (mv.leadership >= 2) "leadership ↑" else if (mv.leadership < 0) "leadership ↓" else null,
                        if (mv.fatigue >= 4) "tired legs" else if (mv.fatigue <= -4) "fresh legs" else null,
                        if (mv.money > 0) "+${money(mv.money)}" else if (mv.money < 0) "costs ${money(-mv.money)}" else null,
                        if (mv.mates >= 3) "teammates ↑" else if (mv.mates < 0) "teammates ↓" else null,
                        if (mv.mood >= 5) "feels great" else if (mv.mood < 0) "mood ↓" else null,
                    )
                    if (hints.isNotEmpty()) Text(hints.joinToString(" · "), style = T.small.copy(fontSize = 11.sp), color = P.faint)
                }
            }
        }
    }
}

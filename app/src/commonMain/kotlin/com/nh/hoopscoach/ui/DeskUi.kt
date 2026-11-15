package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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

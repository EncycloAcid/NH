package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.CoachContracts
import com.nh.hoopscoach.game.Interviews
import com.nh.hoopscoach.game.JobOffer
import com.nh.hoopscoach.game.League

/** The job interview: the AD's three questions, the verdict, then the terms to sign, push on, or walk away from. */
@Composable
fun InterviewDialog(vm: GameViewModel, lg: League, o: JobOffer, onClose: () -> Unit) {
    val t = lg.team(o.teamId)
    val answers = remember(o.teamId) { mutableStateListOf<Int>() }
    var step by remember(o.teamId) { mutableIntStateOf(if (o.score >= 0) Interviews.QUESTIONS.size else 0) }
    var verdict by remember(o.teamId) { mutableStateOf(if (o.score >= 0) (if (o.withdrawn) "${t.school} has moved on." else "Their offer: ${CoachContracts.money(o.salary)} a year for ${o.years} years.") else "") }
    AlertDialog(
        onDismissRequest = onClose, containerColor = P.surface,
        title = { Text(if (step < Interviews.QUESTIONS.size) "Interview · ${t.school}" else "${t.school}'s offer", style = T.head, color = P.ink) },
        text = {
            Column {
                if (step < Interviews.QUESTIONS.size) {
                    if (step == 0) {
                        Text(Interviews.brief(lg, t), style = T.small, color = P.accent)
                        Spacer(Modifier.height(10.dp))
                    }
                    val q = Interviews.QUESTIONS[step]
                    Text("QUESTION ${step + 1} OF ${Interviews.QUESTIONS.size}", style = T.label, color = P.sub)
                    Text("\"${q.text}\"", style = T.bodyB, color = P.ink, modifier = Modifier.padding(vertical = 6.dp))
                    q.answers.forEachIndexed { i, a ->
                        ListRow(onClick = {
                            answers += i
                            if (step == Interviews.QUESTIONS.lastIndex) {
                                var msg = ""
                                vm.act { msg = Interviews.interview(it, o, answers.toList()) }
                                verdict = msg
                            }
                            step++
                        }) { Text(a.text, style = T.body, color = P.ink) }
                    }
                } else {
                    Text(verdict, style = T.body, color = if (o.withdrawn) P.bad else P.ink)
                    if (!o.withdrawn && o.score >= 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Interview score ${o.score}/100 · You'd leave ${lg.user.school} and take over their roster, budget and recruiting class.", style = T.small, color = P.sub)
                        if (!o.pushed) {
                            Spacer(Modifier.height(10.dp))
                            SecondaryButton("Push for more money", Modifier.fillMaxWidth()) {
                                var msg = ""
                                vm.act { msg = Interviews.push(it, o) }
                                verdict = msg
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (step >= Interviews.QUESTIONS.size && !o.withdrawn && o.score >= 0)
                TextButton({ onClose(); vm.act { Interviews.accept(it, o) }; vm.toast("Welcome to ${t.school}") }) { Text("Sign", color = P.accent) }
        },
        dismissButton = { TextButton(onClose) { Text(if (step >= Interviews.QUESTIONS.size) "Walk away" else "Not now", color = P.sub) } },
    )
}

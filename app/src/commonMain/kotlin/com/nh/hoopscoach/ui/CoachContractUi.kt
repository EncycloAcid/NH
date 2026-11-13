package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.CoachContracts
import com.nh.hoopscoach.game.League

/**
 * The head coach's contract: what he makes, how long it runs, what he's earned, and an extension when
 * the boss offers one (take it, ask for more, or walk away and take calls). [onWalk] opens the job
 * market when he walks away from an expired deal.
 */
@Composable
fun CoachContractPanel(vm: GameViewModel, lg: League, onWalk: (League) -> Unit) {
    CoachContracts.ensure(lg)
    val c = lg.userCoach
    val boss = if (lg.pro) "owner" else "AD"
    Section("Your contract")
    Panel(color = if (c.offerSalary > 0) P.hero else P.surface) {
        val fg = if (c.offerSalary > 0) P.onHero else P.ink
        val soft = if (c.offerSalary > 0) P.onHero.copy(alpha = 0.85f) else P.sub
        if (c.contractSalary > 0) {
            Text(CoachContracts.money(c.contractSalary) + " a year", style = T.head, color = fg)
            Text(when { c.contractYears <= 0 -> "Your deal is up"; c.contractYears == 1 -> "Final year of your deal"; else -> "${c.contractYears} years left, signed in ${c.contractSigned}" } +
                " · career earnings ${CoachContracts.money(c.earnings)}", style = T.small, color = if (c.contractYears <= 1 && c.offerSalary == 0) P.warn else soft)
        } else Text("No contract: your deal is up · career earnings ${CoachContracts.money(c.earnings)}", style = T.small, color = soft)
        if (c.offerSalary > 0) {
            Spacer(Modifier.height(12.dp))
            Text(if (c.offerEarly) "EARLY EXTENSION OFFER" else "THE ${boss.uppercase()} WANTS YOU BACK", style = T.label, color = heroKicker)
            Text("${c.offerYears} years · ${CoachContracts.money(c.offerSalary)} a year", style = T.title.copy(fontSize = androidx.compose.ui.unit.TextUnit(24f, androidx.compose.ui.unit.TextUnitType.Sp)), color = P.onHero)
            Text("${CoachContracts.money(c.offerSalary * c.offerYears)} in all" + if (c.offerEarly) ". It replaces the rest of your current deal." else ".", style = T.small, color = soft)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("Sign it", Modifier.weight(1f), primary = true) { vm.act { CoachContracts.accept(it) }; vm.toast("Signed through ${lg.season + c.offerYears}") }
                SmallButton("Ask for more", Modifier.weight(1f), enabled = !c.countered) { var msg = ""; vm.act { msg = CoachContracts.counter(it); if (it.userCoach.offerSalary <= 0) onWalk(it) }; vm.toast(msg) }
            }
            Spacer(Modifier.height(6.dp))
            SmallButton(if (c.offerEarly) "Not now: let my deal run" else "Walk away and take calls", Modifier.fillMaxWidth(), danger = !c.offerEarly) {
                vm.act { if (CoachContracts.decline(it)) onWalk(it) }
            }
            if (!c.offerEarly) Text("Leave it unanswered and you'll sign it when the offseason moves on.", style = T.small, color = soft, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

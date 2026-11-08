package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Business
import com.nh.hoopscoach.game.Fans
import com.nh.hoopscoach.game.FrontOffice
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Team
import kotlin.math.roundToInt

/** The building and the money: ticket price, crowds, the gate, the budget and the upgrades it buys. */
@Composable
fun BusinessPanel(vm: GameViewModel, lg: League, t: Team) {
    Business.ensure(lg, t)
    Section("Arena & business")
    Panel {
        Row {
            Stat("Avg crowd", if (t.homeGames == 0) "—" else "%,d".format(Fans.avgCrowd(t)), Modifier.weight(1f))
            Stat("Full", if (t.homeGames == 0) "—" else "${(Fans.avgFill(t) * 100).roundToInt()}%", Modifier.weight(1f))
            Stat("Sellouts", "${t.sellouts}/${t.homeGames}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row {
            Stat("Gate", Business.money(t.gate), Modifier.weight(1f))
            Stat("Budget", Business.money(t.cash), Modifier.weight(1f), color = P.good)
            Stat("Seats", "%,d".format(t.arena), Modifier.weight(1f))
        }
        val edge = Business.crowdElo(t)
        Text(
            "Home crowd: " + when { t.homeGames < 3 -> "not enough home games yet."; edge >= 4 -> "loud. It's worth a few points a night."; edge <= -3 -> "quiet. Empty seats don't scare anybody."; else -> "about normal." } +
                " A quarter of the gate goes into the budget.",
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp),
        )
    }
    Section("Ticket prices · $${Business.ticket(lg, t)} a seat")
    Segmented(Business.PRICES, t.price, { i -> vm.act { Business.setPrice(it, t, i) } })
    Text(
        when (t.price) { 0 -> "Cheap seats fill the building (+8%) but bring in less."; 1 -> "Fair prices: a few more fans (+4%)."; 2 -> "Standard prices."; 3 -> "Premium prices: more money a seat, fewer seats sold (−7%)."; else -> "Luxury prices: big money from the fans who come, and plenty who don't (−15%)." },
        style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
    )
    Section("Upgrades")
    GroupList(Business.UPGRADES) { u ->
        val owned = Business.has(t, u.id)
        val cost = Business.cost(lg, u)
        ListRow {
            Column(Modifier.weight(1f)) {
                Text(u.name, style = T.bodyB, color = P.ink)
                Text(u.what, style = T.small, color = P.sub)
            }
            Spacer(Modifier.width(8.dp))
            if (owned) Tag("BUILT", P.good)
            else SmallButton(Business.money(cost), primary = t.cash >= cost, enabled = t.cash >= cost) { vm.act { vm.toast(Business.buy(it, t, u.id)) } }
        }
    }
}

/** The boss's goals for the season and where each one stands. */
@Composable
fun GoalsCard(lg: League) {
    val goals = lg.goals.filter { it.season == lg.season }
    if (goals.isEmpty()) return
    Section(if (lg.pro) "The owner's goals · ±${lg.goalStake} each" else "The AD's goals · ±${lg.goalStake} each")
    GroupList(goals) { g ->
        val (text, ok) = FrontOffice.progress(lg, g)
        val done = g.met >= 0
        ListRow {
            Text(if (done) (if (g.met == 1) "✓" else "✗") else if (ok) "●" else "○", style = T.bodyB,
                color = if (done) (if (g.met == 1) P.good else P.bad) else if (ok) P.good else P.warn, modifier = Modifier.width(22.dp))
            Column(Modifier.weight(1f)) {
                Text(g.label, style = T.bodyB, color = P.ink)
                Text(if (done) (if (g.met == 1) "Met" else "Missed") else "$text · ${if (ok) "on track" else "behind"}", style = T.small, color = P.sub)
            }
        }
    }
}

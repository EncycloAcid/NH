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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Shoes
import com.nh.hoopscoach.game.Team

/** A player's competing shoe bids, each signable; the school's own brand marked. */
@Composable
fun ShoeBids(vm: GameViewModel, lg: League, p: Player) {
    val school = lg.teams.getOrNull(p.teamId)?.let { Shoes.of(it) }
    val bids = p.shoeBids.mapNotNull { b -> Shoes.parseBid(b)?.let { b to it } }.sortedByDescending { it.second.second }
    for ((raw, bid) in bids) {
        val (co, pay, years) = bid
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${co.label} · ${Shoes.moneyK(pay)} a year · $years yr${if (years == 1) "" else "s"}", style = T.bodyB, color = P.ink)
                Text(if (co == school) "Your school's brand: no clash on game day, and he's a little happier" else co.blurb, style = T.small, color = if (co == school) P.good else P.sub,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            SmallButton("Sign", primary = true) {
                vm.act { lg2 -> lg2.playerOrNull(p.id)?.let { Shoes.signBid(lg2, it, raw) } }
                vm.toast("${p.name} signs with ${co.label}")
            }
        }
    }
}

/** The school's apparel bids when its deal is up. */
@Composable
fun ApparelBids(vm: GameViewModel, lg: League, t: Team) {
    if (t.apparelBids.isEmpty()) return
    Section("Your apparel deal is up")
    Panel {
        Text("Brands are bidding to outfit ${t.school}. If you don't choose, the best-paying bid is signed when the offseason ends.", style = T.small, color = P.sub)
        for (raw in t.apparelBids) {
            val parts = raw.split('|')
            val co = Shoes.byId(parts[0]) ?: continue
            val pay = parts.getOrNull(1)?.toIntOrNull() ?: 0
            val years = parts.getOrNull(2)?.toIntOrNull() ?: 0
            val bonus = parts.getOrNull(3)?.toIntOrNull() ?: 0
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${co.label} · ${Shoes.moneyK(pay)} a season · $years years", style = T.bodyB, color = P.ink)
                    Text("Recruiting pull +${co.pullBonus} · ${Shoes.moneyK(bonus)} Final Four bonus", style = T.small, color = P.sub)
                }
                Spacer(Modifier.width(8.dp))
                SmallButton("Sign", primary = true) { vm.act { Shoes.signApparel(it, it.user, raw) }; vm.toast("${t.school} signs with ${co.label}") }
            }
        }
    }
}

/** Apparel & shoes on the program page: the school's deal and every player's endorsement. */
@Composable
fun ApparelPanel(lg: League, t: Team) {
    val co = Shoes.of(t) ?: return
    val deals = lg.rosterOf(t).filter { it.shoePay > 0 }.sortedByDescending { it.shoePay }
    Section("Apparel & shoes")
    Panel {
        Text(co.label, style = T.head, color = P.ink)
        Text("${Shoes.moneyK(Shoes.apparelMoney(t))} a season · ${t.apparelLeft} season${if (t.apparelLeft == 1) "" else "s"} left · recruiting pull +${co.pullBonus}" +
            (if (t.apparelBonus > 0) " · ${Shoes.moneyK(t.apparelBonus)} Final Four bonus" else ""), style = T.small, color = P.sub)
        if (deals.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text("PLAYER ENDORSEMENTS · ${Shoes.moneyK(deals.sumOf { it.shoePay })} A YEAR", style = T.label, color = P.accent)
            for (p in deals) Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(p.name, style = T.small, color = P.ink, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${Shoes.ofPlayer(p)?.label ?: ""} · ${Shoes.moneyK(p.shoePay)} · ${p.shoeLeft} yr", style = T.small, color = if (Shoes.ofPlayer(p) == co) P.good else P.sub)
            }
        }
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Business
import com.nh.hoopscoach.game.Facilities
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Team

/** Five dots: how built-up a facility is. */
@Composable
private fun LevelPips(level: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        for (i in 1..5) Box(Modifier.size(9.dp).clip(CircleShape).background(if (i <= level) color else P.surface2))
    }
}

/** On the Program tab: the fund, the home-court edge, the boosters, and the way in. */
@Composable
fun FacilitiesCard(vm: GameViewModel, lg: League, t: Team) {
    Facilities.ensure(t)
    Business.ensure(lg, t)
    Section("Program building", action = "Open", onAction = { vm.push(Route.Facilities) })
    Panel(onClick = { vm.push(Route.Facilities) }) {
        Row {
            Stat("Fund", Business.money(t.cash), Modifier.weight(1f), color = P.good)
            Stat("Home edge", "+%.0f%%".format(Facilities.homeEdgePct(lg, t)), Modifier.weight(1f))
            Stat("Boosters", Facilities.moodLabel(t.boosterMood), Modifier.weight(1f))
        }
        val next = Facilities.ALL.mapNotNull { k -> Facilities.cost(t, k.id)?.let { k to it } }.minByOrNull { it.second }
        if (next != null) Text(
            if (t.cash >= next.second) "You can build: ${next.first.name}, level ${Facilities.level(t, next.first.id) + 1} (${Business.money(next.second)})."
            else "Next: ${next.first.name}, level ${Facilities.level(t, next.first.id) + 1}, at ${Business.money(next.second)}.",
            style = T.small, color = if (t.cash >= next.second) P.good else P.sub, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Building the program: the fund, the home court, six facilities, the booster club and the ticket price. */
@Composable
fun FacilitiesScreen(vm: GameViewModel, lg: League) {
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val t = lg.user
    Facilities.ensure(t)
    Business.ensure(lg, t)
    var naming by remember { mutableStateOf<String?>(null) }
    val base = teamCard(t)
    val fg = onColor(base)
    LazyColumn(Modifier.fillMaxSize()) {
        item { BackBar("Program building", t.school, onBack = { vm.back() }) }
        item {
            Column(Modifier.padding(horizontal = Gutter).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(base, lerp(base, Color.Black, 0.4f)))).padding(16.dp)) {
                Text("FACILITIES FUND", style = T.label, color = fg.copy(alpha = 0.75f))
                CountUpText(t.cash, T.hero.copy(fontSize = 44.sp, lineHeight = 44.sp), fg) { Business.money(it) }
                Spacer(Modifier.height(8.dp))
                Row {
                    Stat("From the gate", Business.money(t.gate / 4), Modifier.weight(1f), color = fg)
                    Stat("Boosters", Business.money(t.boosterSeason), Modifier.weight(1f), color = fg)
                }
                Text("A quarter of the gate, the boosters' giving and postseason money all go into the fund.", style = T.small, color = fg.copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp))
            }
        }
        item {
            Section("Home court")
            Panel {
                Text("+%.1f%% win chance at home".format(Facilities.homeEdgePct(lg, t)), style = T.title.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.ink)
                Spacer(Modifier.height(6.dp))
                val crowd = Facilities.winPct(Business.crowdElo(t))
                val building = Facilities.winPct(Facilities.homeElo(lg, t))
                Text("Every home court +%.1f%% · your crowd %s%.1f%% · your arena %s%.1f%%".format(
                    Facilities.winPct(com.nh.hoopscoach.game.Rankings.HOME_ELO), if (crowd >= 0) "+" else "−", kotlin.math.abs(crowd), if (building >= 0) "+" else "−", kotlin.math.abs(building)),
                    style = T.small, color = P.sub)
                Text(if (t.homeGames < 3) "The crowd counts once you've played a few home games." else "Avg crowd ${"%,d".format(com.nh.hoopscoach.game.Fans.avgCrowd(t))} of ${"%,d".format(t.arena)} · ${t.sellouts} sellouts.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(top = 2.dp))
            }
        }
        item { Section("Facilities") }
        for (k in Facilities.ALL) item(k.id) {
            val lvl = Facilities.level(t, k.id)
            val cost = Facilities.cost(t, k.id)
            Panel(Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconTile(GameIcons.facility(k.id), P.accent, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(Facilities.displayName(t, k.id), style = T.bodyB, color = P.ink)
                        Spacer(Modifier.height(3.dp))
                        LevelPips(lvl, P.accent)
                    }
                    Text("LEVEL $lvl", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                }
                Text(k.blurb, style = T.small, color = P.sub, modifier = Modifier.padding(top = 8.dp))
                Text("Now: ${Facilities.effectAt(k.id, lvl)}", style = T.small, color = P.ink, modifier = Modifier.padding(top = 2.dp))
                if (cost != null) {
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Level ${lvl + 1}: ${Facilities.effectAt(k.id, lvl + 1)}", style = T.small, color = P.good, modifier = Modifier.weight(1f))
                        SmallButton("Build · ${Business.money(cost)}", primary = t.cash >= cost, enabled = t.cash >= cost || (lvl >= 3 && t.cash >= cost * 0.6)) {
                            if (lvl >= 3) naming = k.id else vm.act { vm.toast(Facilities.build(it, it.user, k.id)) }
                        }
                    }
                } else Text("Top level: as good as it gets.", style = T.small, color = P.gold, modifier = Modifier.padding(top = 6.dp))
            }
        }
        item {
            Section("Boosters")
            Panel {
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text("BOOSTER MOOD", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                        Text(Facilities.moodLabel(t.boosterMood), style = T.title.copy(fontSize = 22.sp, lineHeight = 24.sp), color = P.ink)
                    }
                    Text("${Business.money(Facilities.weekly(t))} a week", style = T.bodyB, color = P.good)
                }
                Spacer(Modifier.height(6.dp))
                Bar(t.boosterMood / 100f, Modifier.fillMaxWidth(), height = 8.dp)
                Text("Wins, ranked wins, rivalry wins and March make them happier, and happy boosters give more. New buildings please them too.",
                    style = T.small, color = P.sub, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(10.dp))
                Text("TOP DONORS", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                Facilities.donors(t).forEach { d -> Text("${d.name} · ${d.business}", style = T.small, color = P.ink, modifier = Modifier.padding(top = 2.dp)) }
                Spacer(Modifier.height(12.dp))
                if (Facilities.canGala(lg, t)) PrimaryButton("Host the booster gala · about ${Business.money(Facilities.galaAmount(t))}", Modifier.fillMaxWidth()) {
                    vm.act { vm.toast(Facilities.gala(it, it.user)) }
                    vm.celebrate("GALA NIGHT", "${t.school} boosters")
                } else Text(if (t.galaSeason == lg.season) "This season's gala is done." else "The gala happens during the season.", style = T.small, color = P.faint)
            }
        }
        item {
            Section("Ticket prices · $${Business.ticket(lg, t)} a seat")
            Segmented(Business.PRICES, t.price, { i -> vm.act { Business.setPrice(it, it.user, i) } })
            Text(
                when (t.price) {
                    0 -> "Cheap: the student section packs in (+8% crowds, recruiting pull +1), less money a seat."
                    1 -> "Fair: a few more fans (+4%)."
                    2 -> "Standard prices."
                    3 -> "Premium: more money a seat, fewer seats sold (−7%)."
                    else -> "Luxury: big money from the fans who come, plenty who don't (−15%), and recruits notice the empty seats (pull −1)."
                } + " Fuller crowds are louder: that's home-court edge.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 6.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
    naming?.let { id ->
        val cost = Facilities.cost(t, id) ?: 0L
        val donor = Facilities.donors(t).first()
        AlertDialog(
            onDismissRequest = { naming = null }, containerColor = P.surface,
            title = { Text("Build the ${Facilities.ALL.first { it.id == id }.name.lowercase()}", style = T.head, color = P.ink) },
            text = { Text("${donor.name} of ${donor.business} will cover 40% (${Business.money((cost * 0.4).toLong())}) if his name goes on it: the ${donor.name.substringAfterLast(' ')} ${Facilities.ALL.first { it.id == id }.name}.", style = T.body, color = P.sub) },
            confirmButton = {
                TextButton({ naming = null; vm.act { vm.toast(Facilities.build(it, it.user, id, named = true)) } }) { Text("Name it · pay ${Business.money(cost - (cost * 0.4).toLong())}", style = T.bodyB, color = P.accent) }
            },
            dismissButton = {
                TextButton({ naming = null; if (t.cash >= cost) vm.act { vm.toast(Facilities.build(it, it.user, id)) } else vm.toast("Not enough to pay in full.") }) { Text("Pay ${Business.money(cost)}", color = P.sub) }
            },
        )
    }
}

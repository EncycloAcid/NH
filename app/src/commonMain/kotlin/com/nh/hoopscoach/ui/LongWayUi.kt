package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.AbroadOffer
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.LongWay
import com.nh.hoopscoach.game.Road

/**
 * The long way, undrafted: your agent's offers (the G League, clubs overseas), then the season week by week, and
 * the NBA's calls when they come.
 */
@Composable
fun LongWayScreen(vm: GameViewModel, lg: League) {
    val r = lg.road ?: return
    val h = lg.hero ?: return
    @Suppress("UNUSED_VARIABLE") val rev = vm.rev
    val a = r.abroad
    var retire by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            item {
                ScreenHeader("Road to the League", "The long way", if (a != null) a.offer.league else "Undrafted")
                Panel(color = P.hero) {
                    when {
                        a == null -> {
                            Text("YOUR AGENT'S CALLS", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                            Text("NOT DONE YET", style = T.hero, color = P.onHero)
                            Text(if (r.abroadPast.isEmpty()) "Sixty names called, none of them yours. Play your way to the NBA: the G League is a tryout every night, and clubs overseas pay more and give you the ball."
                                else "Another season away. ${h.name} is ${h.age}, rated ${h.ovr}. One more good year and the calls come.",
                                style = T.small, color = P.onHero.copy(alpha = 0.9f))
                        }
                        else -> {
                            val o = a.offer
                            Text(if (o.kind == "gleague") "G LEAGUE · ${o.where.uppercase()}" else "${o.league.uppercase()} · ${o.where.uppercase()}", style = T.label, color = P.onHero.copy(alpha = 0.8f))
                            FitText(o.club.uppercase(), style = T.hero, color = P.onHero)
                            Text("${a.w}-${a.l} · game ${a.games} of ${a.total} · ${o.role.lowercase()} · ${money(o.salary)}" + if (a.done) " · season over" else "",
                                style = T.small, color = P.onHero.copy(alpha = 0.9f))
                            Spacer(Modifier.height(10.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                val s = a.stats
                                Stat("PPG", "%.1f".format(s.ppg), color = P.onHero, labelColor = P.onHero.copy(alpha = 0.7f))
                                Stat("RPG", "%.1f".format(s.rpg), color = P.onHero, labelColor = P.onHero.copy(alpha = 0.7f))
                                Stat("APG", "%.1f".format(s.apg), color = P.onHero, labelColor = P.onHero.copy(alpha = 0.7f))
                                Stat("FG%", if (s.fga > 0) "%.0f".format(s.fgm * 100.0 / s.fga) else "—", color = P.onHero, labelColor = P.onHero.copy(alpha = 0.7f))
                                Stat("OVR", "${h.ovr}", color = P.onHero, labelColor = P.onHero.copy(alpha = 0.7f))
                            }
                        }
                    }
                }
            }
            // NBA teams calling: take it now, or (mid-season) keep playing.
            if (r.calls.isNotEmpty()) item {
                Section("The NBA is calling")
                GroupList(r.calls.withIndex().toList(), inset = 16.dp) { (i, c) ->
                    ListRow {
                        Column(Modifier.weight(1f)) {
                            Text(c.team, style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${c.deal} · ${c.note}", style = T.small, color = P.sub)
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallButton("Sign", primary = true) { vm.act { LongWay.accept(it, i) } }
                    }
                }
            }
            if (a == null) item {
                Section("Offers")
                r.wayOffers.forEachIndexed { i, o -> OfferCard(o) { vm.act { LongWay.sign(it, i) } } }
            }
            if (a != null && a.recent.isNotEmpty()) item {
                Section(if (a.done) "How it ended" else "Recent games")
                Panel {
                    a.recent.forEach { Text(it, style = T.small, color = P.ink, modifier = Modifier.padding(vertical = 2.dp)) }
                }
            }
            if (a != null) item {
                Spacer(Modifier.height(4.dp))
                Panel(padding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                    Text("YOUR BADGES", style = T.label.copy(fontSize = 10.sp), color = P.sub)
                    Text("Badge points count here too.", style = T.small, color = P.faint, modifier = Modifier.padding(bottom = 6.dp))
                    BadgeRow(lg, h, max = 11)
                }
            }
            if (r.abroadPast.isNotEmpty()) item {
                Section("Seasons away")
                Panel { r.abroadPast.forEach { Text(it, style = T.small, color = P.sub, modifier = Modifier.padding(vertical = 2.dp)) } }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.padding(horizontal = Gutter)) {
                    SecondaryButton("Call it a career", Modifier.fillMaxWidth()) { retire = true }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
        when {
            a != null && !a.done -> Row(Modifier.fillMaxWidth().background(P.bg).padding(horizontal = Gutter, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Sim the season", Modifier.weight(1f)) { vm.act { LongWay.play(it, 99) } }
                Spacer(Modifier.width(10.dp))
                PrimaryButton("Play the next week", Modifier.weight(1.3f)) { vm.act { LongWay.play(it, 3) } }
            }
            a != null && a.done -> FlowFooter(null, if (r.calls.isEmpty()) "Go again: this year's offers" else "Another season away instead") { vm.act { LongWay.again(it) } }
        }
    }
    if (retire) AlertDialog(
        onDismissRequest = { retire = false }, containerColor = P.surface,
        title = { Text("Call it a career?", style = T.head, color = P.ink) },
        text = { Text("Your playing days end here. You can still coach, or play your son's career.", style = T.body, color = P.sub) },
        confirmButton = { TextButton({ retire = false; vm.act { LongWay.retire(it) } }) { Text("Retire", color = P.bad) } },
        dismissButton = { TextButton({ retire = false }) { Text("Keep playing", color = P.sub) } },
    )
}

/** One offer: the club, the league, the money, the role, and what it's good for. */
@Composable
private fun OfferCard(o: AbroadOffer, onSign: () -> Unit) {
    val g = o.kind == "gleague"
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.padding(horizontal = Gutter, vertical = 5.dp).fillMaxWidth().clip(shape).background(P.surface)
            .border(1.dp, if (g) P.accent.copy(alpha = 0.5f) else P.line, shape).padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (g) "G LEAGUE" else "OVERSEAS · ${o.where.uppercase()}", style = T.label.copy(fontSize = 10.sp), color = if (g) P.accent else P.gold)
                Text(if (g) o.club else "${o.club} · ${o.league}", style = T.bodyB, color = P.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            SmallButton("Sign", primary = true) { onSign() }
        }
        Text("${money(o.salary)} for the season · ${o.role.lowercase()}" + (if (g) " · ${LongWay.G_LEAGUE_GAMES} games" else " · ${LongWay.OVERSEAS_GAMES} games"),
            style = T.small, color = P.sub, modifier = Modifier.padding(top = 4.dp))
        Text(if (g) "Every game is a tryout for the ${o.where}. Play well and they can call you up on a two-way deal, mid-season."
            else "Better money and a bigger role. NBA scouts watch, and teams call in the summer if you've earned it.",
            style = T.small, color = P.faint, modifier = Modifier.padding(top = 4.dp))
    }
}

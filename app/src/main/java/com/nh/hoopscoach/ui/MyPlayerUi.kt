package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Legacy
import com.nh.hoopscoach.game.Signature

/**
 * MyPlayer: make your player yours. His identity (nickname, number, where he's from), his gear (the
 * undershirt and arm sleeves show on his portrait), and his signature style: the dunk, go-to move,
 * jumper and celebration the play-by-play and the fans will know him by. Links to the portrait maker
 * for his face, and to his body and position.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MyPlayerScreen(vm: GameViewModel, lg: League) {
    val h = lg.playerOrNull(lg.heroId)
    val r = lg.road
    if (h == null || r == null) {
        Column { BackBar("MyPlayer", onBack = { vm.back() }); Empty("MyPlayer is for your own player in a player career.") }
        return
    }
    val t = if (h.teamId >= 0) lg.team(h.teamId) else null
    var tab by remember { mutableIntStateOf(0) }
    var nick by remember { mutableStateOf(h.nickname) }
    var number by remember { mutableIntStateOf(h.number) }
    var town by remember { mutableStateOf(h.hometown) }
    var school by remember { mutableStateOf(h.highSchool) }
    var gear by remember { mutableStateOf(Gear.decode(h.gear)) }
    var dunk by remember { mutableIntStateOf(r.sigDunk) }
    var move by remember { mutableIntStateOf(r.sigMove) }
    var shot by remember { mutableIntStateOf(r.sigShot) }
    var cele by remember { mutableIntStateOf(r.celebration) }

    val taken = t?.roster?.filter { it != h.id }?.mapNotNull { lg.playerOrNull(it)?.number }?.toSet().orEmpty()
    val retired = t?.let { Legacy.retiredNumbers(it) }.orEmpty()
    val numberProblem = when {
        number in taken -> "A teammate already wears No. $number."
        number in retired && number != h.number -> "No. $number is retired here."
        else -> null
    }
    val short = "${h.first.take(1)}. ${h.last}"

    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("MyPlayer", h.name, onBack = { vm.back() })
            // The preview: his portrait with the gear he's picking.
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(26.dp)).background(P.surface).padding(6.dp).clickable { vm.push(Route.Portrait(h.id)) }) {
                    PixelPortrait(h, t, 132.dp, gear = gear)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(h.first.uppercase(), style = T.label, color = P.sub)
                    Text(h.last.uppercase(), style = T.title, color = P.ink, maxLines = 1)
                    if (nick.isNotBlank()) Text("\"${nick.trim()}\"", style = T.bodyB, color = P.accent, maxLines = 1)
                    Text("No. $number · ${h.posLabel}" + (t?.let { " · ${if (lg.pro) it.name else it.school}" } ?: ""), style = T.small, color = P.sub)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SmallButton("Face", primary = true) { vm.push(Route.Portrait(h.id)) }
                        if (t != null && h.teamId == lg.userTeamId) SmallButton("Body") { vm.push(Route.EditPlayer(h.id)) }
                    }
                }
            }
            Segmented(listOf("Identity", "Gear", "Signature"), tab, { tab = it })
        }

        when (tab) {
            0 -> item {
                Section("Nickname")
                Panel {
                    OutlinedTextField(nick, { nick = it.take(20) }, label = { Text("What they call you") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Signature.nicknames(h).forEach { n -> SmallButton(n, primary = nick == n) { nick = n } }
                        if (nick.isNotBlank()) SmallButton("None", danger = true) { nick = "" }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("The play-by-play and your fans use it now and then.", style = T.small, color = P.sub)
                }
                Section("Jersey number")
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SmallButton("−10") { number = (number - 10).coerceIn(0, 99) }
                        Spacer(Modifier.width(6.dp))
                        SmallButton("−") { number = (number - 1).coerceIn(0, 99) }
                        Text("$number", style = T.num, color = if (numberProblem == null) P.ink else P.bad, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                        SmallButton("+") { number = (number + 1).coerceIn(0, 99) }
                        Spacer(Modifier.width(6.dp))
                        SmallButton("+10") { number = (number + 10).coerceIn(0, 99) }
                    }
                    if (numberProblem != null) { Spacer(Modifier.height(6.dp)); Text(numberProblem, style = T.small, color = P.bad) }
                }
                Section("Where you're from")
                Panel {
                    OutlinedTextField(town, { town = it.take(28) }, label = { Text("Hometown") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(school, { school = it.take(28) }, label = { Text("High school") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
            }
            1 -> item {
                Text("The undershirt and arm sleeves show on your portrait; the rest is on your player card.", style = T.small, color = P.sub,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
                Gear.PARTS.forEachIndexed { i, label ->
                    if (i == 2 && gear.sleeve == 0) return@forEachIndexed
                    Section(label)
                    Chips(Gear.OPTIONS[i], gear.parts()[i], { v -> gear = gear.with(i, v) })
                }
                Spacer(Modifier.height(10.dp))
                Panel { Text(gear.describe() + ".", style = T.small, color = P.ink) }
            }
            else -> item {
                Text("How you play, in the play-by-play and on social media.", style = T.small, color = P.sub,
                    modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
                Section("Signature dunk")
                Chips(Signature.DUNKS, dunk, { dunk = it })
                Preview("$short ${Signature.dunkVerb[dunk]}!")
                Section("Go-to move")
                Chips(Signature.MOVES, move, { move = it })
                Preview("$short hits a ${Signature.moveText[move]} jumper")
                Section("Jumper")
                Chips(Signature.SHOTS, shot, { shot = it })
                Preview("$short knocks down the ${Signature.shotWord[shot]} jumper")
                Section("Celebration")
                Chips(Signature.CELEBRATIONS, cele, { cele = it })
                Preview("$short ${Signature.celebrateText[cele]}")
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", Modifier.weight(1f)) { vm.back() }
                PrimaryButton("Save", Modifier.weight(1f), enabled = numberProblem == null) {
                    val n = nick.trim(); val num = number; val g = gear; val tn = town.trim(); val hs = school.trim()
                    val d = dunk; val mv = move; val sh = shot; val ce = cele
                    vm.act {
                        val pl = it.player(h.id)
                        pl.nickname = n
                        pl.number = num
                        pl.gear = if (g.isPlain) "" else g.encode()
                        if (tn.isNotEmpty()) pl.hometown = tn
                        if (hs.isNotEmpty()) pl.highSchool = hs
                        it.road?.let { rd -> rd.sigDunk = d; rd.sigMove = mv; rd.sigShot = sh; rd.celebration = ce }
                    }
                    vm.toast("Saved")
                    vm.back()
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

/** A line of the play-by-play, as it'll read. */
@Composable
private fun Preview(text: String) {
    Text("▶ $text", style = T.small, color = P.accent, modifier = Modifier.padding(start = Gutter + 4.dp, end = Gutter, top = 6.dp))
}

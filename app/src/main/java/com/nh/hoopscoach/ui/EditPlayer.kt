package com.nh.hoopscoach.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Arch
import com.nh.hoopscoach.game.Gameplan
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Legacy
import com.nh.hoopscoach.game.Pos
import com.nh.hoopscoach.game.Ratings
import kotlin.math.roundToInt

/** Edit one of your players: name, number, position, size, background and play style. */
@Composable
fun EditPlayerScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId)
    if (p == null || p.teamId != lg.userTeamId) {
        Column { BackBar("Edit player", onBack = { vm.back() }); Empty("Only players on your roster can be edited.") }
        return
    }
    val t = lg.team(p.teamId)
    var first by remember(playerId) { mutableStateOf(p.first) }
    var last by remember(playerId) { mutableStateOf(p.last) }
    var number by remember(playerId) { mutableIntStateOf(p.number) }
    var pos by remember(playerId) { mutableStateOf(p.pos) }
    var pos2 by remember(playerId) { mutableStateOf(p.pos2) }
    var height by remember(playerId) { mutableIntStateOf(p.height) }
    var weight by remember(playerId) { mutableIntStateOf(p.weight) }
    var town by remember(playerId) { mutableStateOf(p.hometown) }
    var school by remember(playerId) { mutableStateOf(p.highSchool) }
    var style by remember(playerId) { mutableStateOf(p.style) }
    val taken = t.roster.filter { it != p.id }.map { lg.player(it).number }.toSet()
    val retired = Legacy.retiredNumbers(t)
    val numberProblem = when {
        number in taken -> "A teammate already wears No. $number."
        number in retired && number != p.number -> "No. $number is retired at ${t.school}."
        else -> null
    }
    val ovr = Ratings.ovr(pos, p.attrs)
    if (pos2 != null && pos2 !in Ratings.secondOptions(pos)) pos2 = null
    val styles = Ratings.styles(pos) + (pos2?.let { Ratings.styles(it) } ?: emptyList())
    if (style !in styles) style = styles.first()
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Edit player", p.name, onBack = { vm.back() })
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter + 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Jersey(number, androidx.compose.ui.graphics.Color(t.primary), androidx.compose.ui.graphics.Color(t.secondary), 84.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(first.ifBlank { "First" }.uppercase(), style = T.label, color = P.sub)
                    Text(last.ifBlank { "Last" }.uppercase(), style = T.title, color = P.ink, maxLines = 1)
                    Text("$pos · ${p.cls} · ${Ratings.heightLabel(height)} · $weight lbs", style = T.small, color = P.sub)
                    Text("${style.label} · ${t.school}", style = T.small, color = P.accent)
                }
                Ring(ovr, 52.dp, label = "OVR", textSize = 18.sp())
            }

            Section("Name")
            Panel {
                OutlinedTextField(first, { first = it.take(16) }, label = { Text("First name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(last, { last = it.take(18) }, label = { Text("Last name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }

            Section("Jersey number")
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SmallButton("−10") { number = (number - 10).coerceIn(0, 99) }
                    Spacer(Modifier.width(6.dp))
                    SmallButton("−") { number = (number - 1).coerceIn(0, 99) }
                    Text("$number", style = T.num, color = if (numberProblem == null) P.ink else P.bad, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    SmallButton("+") { number = (number + 1).coerceIn(0, 99) }
                    Spacer(Modifier.width(6.dp))
                    SmallButton("+10") { number = (number + 10).coerceIn(0, 99) }
                }
                if (numberProblem != null) { Spacer(Modifier.height(6.dp)); Text(numberProblem, style = T.small, color = P.bad) }
            }

            Section("Position")
            Segmented(Pos.entries.map { it.name }, pos.ordinal, { pos = Pos.entries[it] })
            val seconds = listOf<Pos?>(null) + Ratings.secondOptions(pos)
            Text("SECOND POSITION", style = T.label, color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 12.dp, bottom = 6.dp))
            Segmented(seconds.map { it?.name ?: "None" }, seconds.indexOf(pos2).coerceAtLeast(0), { pos2 = seconds[it] })
            Text(
                if (pos == p.pos) "His overall is rated for the position he plays." else "At $pos his overall would be ${shown(ovr)} (now ${shown(p.ovr)} at ${p.pos}). Ratings don't change, only how they're weighed.",
                style = T.small, color = if (pos == p.pos) P.sub else P.warn, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )

            Section("Size")
            Panel {
                Text("Height · ${Ratings.heightLabel(height)}", style = T.bodyB, color = P.ink)
                HoopsSlider(height.toFloat(), { height = it.roundToInt() }, 66f..90f)
                Spacer(Modifier.height(6.dp))
                Text("Weight · $weight lbs", style = T.bodyB, color = P.ink)
                HoopsSlider(weight.toFloat(), { weight = it.roundToInt() }, 150f..320f)
            }

            Section("Play style")
            Picker("Archetype", style.label, styles.map { it.label to it.blurb }, Modifier.padding(horizontal = Gutter).fillMaxWidth()) { style = styles[it] }
            Text(
                "The archetype is his identity: it's the training focus that suits him best. It doesn't change his ratings.",
                style = T.small, color = P.sub, modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp),
            )

            Section("Background")
            Panel {
                OutlinedTextField(town, { town = it.take(28) }, label = { Text("Hometown") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(school, { school = it.take(28) }, label = { Text("High school") }, singleLine = true, modifier = Modifier.fillMaxWidth(), colors = fieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
            }
            Spacer(Modifier.height(18.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Cancel", Modifier.weight(1f)) { vm.back() }
                PrimaryButton("Save", Modifier.weight(1f), enabled = first.isNotBlank() && last.isNotBlank() && numberProblem == null) {
                    val f = first.trim(); val l = last.trim(); val tn = town.trim(); val hs = school.trim()
                    val n = number; val ps = pos; val ps2 = pos2; val h = height; val w = weight; val st = style
                    vm.act {
                        val pl = it.player(playerId)
                        val moved = pl.pos != ps || pl.pos2 != ps2
                        pl.first = f; pl.last = l; pl.number = n; pl.pos = ps; pl.pos2 = ps2; pl.height = h; pl.weight = w
                        if (tn.isNotEmpty()) pl.hometown = tn
                        if (hs.isNotEmpty()) pl.highSchool = hs
                        pl.style = st
                        val tm = it.team(pl.teamId)
                        if (moved) { if (tm.plan.auto) Gameplan.autoAllocate(it, tm) else Gameplan.validate(it, tm) }
                    }
                    vm.toast("Saved")
                    vm.back()
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

private fun Int.sp() = androidx.compose.ui.unit.TextUnit(this.toFloat(), androidx.compose.ui.unit.TextUnitType.Sp)

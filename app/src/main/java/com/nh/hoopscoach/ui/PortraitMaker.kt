package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.League

/**
 * The portrait maker: his sports-card portrait big, a row of parts (skin, face, hair, hair color, facial
 * hair, eyes, brows, mouth, extras), and every option for the part as a little portrait of him with it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortraitMakerScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId) ?: run { BackBar("Portrait", onBack = { vm.back() }); return }
    val t = lg.teams.getOrNull(p.teamId)
    var face by remember(playerId) { mutableStateOf(Face.of(p)) }
    var part by remember { mutableIntStateOf(2) }
    var seed by remember { mutableIntStateOf(1) }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            BackBar("Portrait maker", p.name, onBack = { vm.back() })
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.clip(RoundedCornerShape(30.dp)).background(P.surface).padding(10.dp)) { PixelPortrait(p, t, 200.dp, face) }
            }
            Text("${p.name} · No. ${p.number}${t?.let { " · ${it.name}" } ?: ""}", style = T.small, color = P.sub, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
            Spacer(Modifier.height(10.dp))
            Row(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Randomize", Modifier.weight(1f)) { seed++; face = Face.random(System.nanoTime() + seed) }
                SecondaryButton("Reset", Modifier.weight(1f)) { face = Face.generated(p) }
            }
            Spacer(Modifier.height(10.dp))
            Segmented(Face.PARTS, part, { part = it }, scroll = true)
        }
        item {
            Section(Face.PARTS[part])
            FlowRow(Modifier.padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Face.OPTIONS[part].forEachIndexed { i, label ->
                    val option = face.with(part, i)
                    val on = face.parts()[part] == i
                    Column(
                        Modifier.width(76.dp).clip(RoundedCornerShape(14.dp)).background(if (on) P.accentSoft else P.surface)
                            .border(if (on) 2.dp else 0.dp, if (on) P.accent else P.surface, RoundedCornerShape(14.dp))
                            .clickable { face = option }.padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PixelPortrait(p, t, 56.dp, option)
                        Text(label, style = T.small, color = if (on) P.accent else P.sub, maxLines = 1, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.padding(horizontal = Gutter)) {
                PrimaryButton("Save portrait", Modifier.fillMaxWidth()) {
                    val code = face.encode()
                    vm.act { it.playerOrNull(playerId)?.face = code }
                    vm.toast("Portrait saved.")
                    vm.back()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

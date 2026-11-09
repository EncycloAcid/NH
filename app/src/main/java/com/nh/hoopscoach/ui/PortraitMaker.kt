package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.League

private enum class CustomTab(val title: String) {
    SKIN("Skin"),
    HAIR("Hair"),
    COLOR("Color"),
    BEARD("Beard"),
    EYES("Eyes"),
    MOUTH("Mouth"),
    GEAR("Gear"),
}

/** Live pixel-art portrait maker — big zero-offset preview, chip options, instant updates. */
@Composable
fun PortraitMakerScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId) ?: run { BackBar("Portrait", onBack = { vm.back() }); return }
    val t = lg.teams.getOrNull(p.teamId)
    val jerseyKey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name)
    val primary = (t?.primary ?: 0xFF5C6B7AL).toInt()
    val secondary = (t?.secondary ?: 0xFFFFFFFFL).toInt()
    val courtBg = Color(0xFF1A1714)

    var face by remember(playerId) { mutableStateOf(Face.of(p)) }
    var tab by remember { mutableStateOf(CustomTab.SKIN) }
    var seed by remember { mutableStateOf(0) }

    val skinSwatches = listOf(
        0xFFEEBCB0L, 0xFFE1A28EL, 0xFFCA8E5CL, 0xFFC27A44L,
        0xFFA85830L, 0xFF8E4826L, 0xFF703A24L, 0xFF4B3930L,
    )

    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("Player customizer", p.name, onBack = { vm.back() }) {
            IconButton(onClick = {
                seed++
                face = Face.random(System.nanoTime() + seed)
            }) {
                Icon(Icons.Default.Refresh, contentDescription = "Randomize", tint = P.accent)
            }
        }

        // Live hero preview
        Box(
            Modifier
                .weight(1.15f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasketballCharacterCanvas(
                face = face,
                jerseyKey = jerseyKey,
                primary = primary,
                secondary = secondary,
                number = p.number,
                background = courtBg,
                corner = 18.dp,
                modifier = Modifier
                    .fillMaxHeight()
                    .border(3.dp, P.line, RoundedCornerShape(18.dp)),
            )
        }

        Text(
            "${p.name} · No. ${p.number}${t?.let { " · ${it.name}" } ?: ""}",
            style = T.small,
            color = P.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        // Category tabs
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .background(P.surface)
                .padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CustomTab.entries.forEach { tTab ->
                val on = tab == tTab
                Text(
                    tTab.title,
                    style = T.bodyB,
                    color = if (on) P.onAccent else P.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (on) P.accent else Color.Transparent)
                        .clickable { tab = tTab }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        // Option panel
        Column(
            Modifier
                .weight(0.95f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            when (tab) {
                CustomTab.SKIN -> {
                    PanelLabel("Skin tone")
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(Face.OPTIONS[0]) { i, name ->
                            val on = face.skin == i
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(
                                    Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(skinSwatches[i.coerceIn(0, skinSwatches.lastIndex)]))
                                        .border(
                                            if (on) 3.dp else 1.dp,
                                            if (on) P.accent else P.line,
                                            CircleShape,
                                        )
                                        .clickable { face = face.with(0, i) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(20.dp))
                                }
                                Text(name, style = T.small, color = if (on) P.accent else P.sub, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
                            }
                        }
                    }
                }

                CustomTab.HAIR -> {
                    PanelLabel("Hairstyle")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[2],
                        selected = face.hair,
                        onSelect = { face = face.with(2, it) },
                    )
                }

                CustomTab.COLOR -> {
                    PanelLabel("Hair color")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[3],
                        selected = face.hairColor,
                        onSelect = { face = face.with(3, it) },
                    )
                }

                CustomTab.BEARD -> {
                    PanelLabel("Facial hair")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[4],
                        selected = face.beard,
                        onSelect = { face = face.with(4, it) },
                    )
                }

                CustomTab.EYES -> {
                    PanelLabel("Eyes")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[5],
                        selected = face.eyes,
                        onSelect = { face = face.with(5, it) },
                    )
                }

                CustomTab.MOUTH -> {
                    PanelLabel("Expression")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[7],
                        selected = face.mouth,
                        onSelect = { face = face.with(7, it) },
                    )
                }

                CustomTab.GEAR -> {
                    PanelLabel("Headband & eyewear")
                    Spacer(Modifier.height(10.dp))
                    OptionChipRow(
                        labels = Face.OPTIONS[8],
                        selected = face.acc,
                        onSelect = { face = face.with(8, it) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Reset look", Modifier.weight(1f)) { face = Face.generated(p) }
                SecondaryButton("Randomize", Modifier.weight(1f)) {
                    seed++
                    face = Face.random(System.nanoTime() + seed)
                }
            }
        }

        // Lock in
        Box(Modifier.padding(16.dp)) {
            PrimaryButton("Lock in look", Modifier.fillMaxWidth()) {
                val code = face.encode()
                vm.act { it.playerOrNull(playerId)?.face = code }
                Pixels.clear()
                vm.toast("Portrait saved.")
                vm.back()
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = T.small, color = P.sub, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun OptionChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        itemsIndexed(labels) { i, label ->
            val on = selected == i
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) P.accent else P.surface)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(
                    label,
                    color = if (on) P.onAccent else P.ink,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    fontSize = 14.sp,
                )
            }
        }
    }
}

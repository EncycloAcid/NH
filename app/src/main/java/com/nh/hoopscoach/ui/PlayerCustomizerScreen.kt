package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Player

enum class CustomizerTab(val label: String) {
    SKIN("Skin"),
    HAIR("Hair"),
    BEARD("Beard"),
    FACE("Face"),
    GEAR("Gear"),
}

/**
 * Additive customizer drop-in using the same chrome as [PortraitMakerScreen]
 * (BackBar, chip tabs, PrimaryButton) so it matches the rest of the app.
 */
@Composable
fun PlayerCustomizerScreen(
    player: Player,
    teamAbbr: String,
    onBack: () -> Unit,
    onSave: (PlayerLook) -> Unit,
) {
    val initialLook = remember(player.id) { PlayerLook.fromInt(player.look) }
    var currentLook by remember(player.id) { mutableStateOf(initialLook) }
    var selectedTab by remember { mutableStateOf(CustomizerTab.HAIR) }
    val dirty = currentLook.toInt() != initialLook.toInt()

    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("Player customizer", player.name, onBack = onBack)

        Box(
            modifier = Modifier
                .weight(1.1f)
                .fillMaxWidth()
                .padding(horizontal = Gutter, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            PlayerAvatar(
                look = currentLook,
                teamAbbr = teamAbbr,
                size = 260.dp,
                cornerRadius = 20.dp,
                modifier = Modifier.border(
                    2.dp,
                    if (dirty) P.accent.copy(alpha = 0.55f) else P.line,
                    RoundedCornerShape(20.dp),
                ),
            )
        }

        Text(
            buildString {
                append(player.name)
                append(" · No. ")
                append(player.number)
                append(" · ")
                append(teamAbbr.uppercase())
                if (dirty) append(" · unsaved")
            },
            style = T.small,
            color = if (dirty) P.accent else P.sub,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CustomizerTab.entries.forEach { tab ->
                val on = selectedTab == tab
                Text(
                    tab.label,
                    style = T.bodyB,
                    color = if (on) P.onAccent else P.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) P.accent else P.surface)
                        .border(1.dp, if (on) P.accent else P.line, RoundedCornerShape(12.dp))
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Column(
            Modifier
                .weight(0.9f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Gutter),
        ) {
            when (selectedTab) {
                CustomizerTab.SKIN -> {
                    Text("Skin tone", style = T.small, color = P.sub)
                    Spacer(Modifier.height(12.dp))
                    val toneColors = listOf(
                        0xFF4B3930, 0xFF703A24, 0xFF8E4826, 0xFFA85830,
                        0xFFC27A44, 0xFFCA8E5C, 0xFFE1A28E, 0xFFEEBCB0,
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        itemsIndexed(PlayerLook.skinTones) { idx, _ ->
                            val isSelected = currentLook.skinToneIndex == idx
                            Box(
                                Modifier
                                    .size(52.dp)
                                    .clip(CircleShape)
                                    .background(Color(toneColors[idx.coerceIn(0, toneColors.lastIndex)]))
                                    .border(if (isSelected) 3.dp else 1.dp, if (isSelected) P.accent else P.line, CircleShape)
                                    .clickable { currentLook = currentLook.copy(skinToneIndex = idx) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                CustomizerTab.HAIR -> ChipSection(
                    "Hairstyle",
                    PlayerLook.hairstyles.map { it.replace('_', ' ') },
                    currentLook.hairIndex,
                ) { currentLook = currentLook.copy(hairIndex = it) }

                CustomizerTab.BEARD -> ChipSection(
                    "Beard & facial hair",
                    listOf("Clean shaven", "Full beard", "Goatee", "Stubble"),
                    currentLook.beardIndex,
                ) { currentLook = currentLook.copy(beardIndex = it) }

                CustomizerTab.FACE -> ChipSection(
                    "Eyes & expression",
                    listOf("Default", "Focused", "Clutch stare", "Swagger", "Alert", "Blue", "Green"),
                    currentLook.eyeIndex,
                ) { currentLook = currentLook.copy(eyeIndex = it) }

                CustomizerTab.GEAR -> {
                    ChipSection(
                        "Headband",
                        listOf("None", "White", "Red", "Black"),
                        currentLook.headbandIndex,
                    ) { currentLook = currentLook.copy(headbandIndex = it) }
                    Spacer(Modifier.height(16.dp))
                    ChipSection(
                        "Eyewear",
                        listOf("None", "Rec specs", "Clear mask"),
                        currentLook.eyewearIndex,
                    ) { currentLook = currentLook.copy(eyewearIndex = it) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(P.surface)
                .padding(horizontal = Gutter, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("Reset", Modifier.weight(1f)) { currentLook = initialLook }
                SecondaryButton("Undo", Modifier.weight(1f), enabled = dirty) { currentLook = initialLook }
            }
            Spacer(Modifier.height(8.dp))
            PrimaryButton(if (dirty) "Lock in look" else "Done", Modifier.fillMaxWidth()) {
                if (dirty) onSave(currentLook) else onBack()
            }
        }
    }
}

@Composable
private fun ChipSection(label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Text(label, style = T.small, color = P.sub)
    Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(options) { idx, name ->
            val on = selected == idx
            Text(
                name,
                style = T.small,
                color = if (on) P.onAccent else P.ink,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) P.accent else P.surface2)
                    .border(1.dp, if (on) P.accent else P.line, RoundedCornerShape(10.dp))
                    .clickable { onSelect(idx) }
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

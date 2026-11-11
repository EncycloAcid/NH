package com.nh.hoopscoach.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player

enum class CustomizerTab(val label: String) {
    SKIN("Skin"),
    HAIR("Hair"),
    BEARD("Beard"),
    FACE("Face"),
    GEAR("Gear")
}

/**
 * Player Customizer Screen matching the Hoops Coach / Hoops Dynasty design.
 * Integrates directly with Player.look.
 */
@Composable
fun PlayerCustomizerScreen(
    player: Player,
    teamAbbr: String,
    onBack: () -> Unit,
    onSave: (PlayerLook) -> Unit
) {
    val initialLook = remember(player.id) { PlayerLook.fromInt(player.look) }
    var currentLook by remember(player.id) { mutableStateOf(initialLook) }
    var selectedTab by remember { mutableStateOf(CustomizerTab.HAIR) }

    Scaffold(
        topBar = {
            Column(Modifier.background(P.bg).padding(horizontal = Gutter, vertical = 8.dp)) {
                BackBar("Player customizer", player.name, onBack = onBack)
            }
        },
        bottomBar = {
            Button(
                onClick = { onSave(currentLook) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 12.dp)
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = P.accent)
            ) {
                Text("Lock in look", style = T.bodyB, color = P.onAccent)
            }
        },
        containerColor = P.bg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Live Preview Card
            Box(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxWidth()
                    .padding(horizontal = Gutter, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                PlayerAvatar(
                    look = currentLook,
                    teamAbbr = teamAbbr,
                    size = 280.dp,
                    cornerRadius = 20.dp,
                    modifier = Modifier.border(2.dp, P.line, RoundedCornerShape(20.dp))
                )
            }

            Text(
                "${player.name} · No. ${player.number} · ${teamAbbr.uppercase()}",
                style = T.small,
                color = P.sub
            )

            Spacer(Modifier.height(10.dp))

            // 2. Customizer Tabs
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = P.surface,
                contentColor = P.accent,
                divider = { HorizontalDivider(color = P.line) }
            ) {
                CustomizerTab.values().forEach { tab ->
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        text = { Text(tab.label, style = T.bodyB, color = if (selectedTab == tab) P.accent else P.sub) }
                    )
                }
            }

            // 3. Category Controls
            Column(
                modifier = Modifier
                    .weight(0.9f)
                    .fillMaxWidth()
                    .background(P.surface)
                    .padding(horizontal = Gutter, vertical = 14.dp)
            ) {
                when (selectedTab) {
                    CustomizerTab.SKIN -> {
                        Text("Skin Tone", style = T.label, color = P.sub)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            itemsIndexed(PlayerLook.skinTones) { idx, tone ->
                                val isSelected = currentLook.skinToneIndex == idx
                                val toneColors = listOf(
                                    0xFF4B3930, 0xFF703A24, 0xFF8E4826, 0xFFA85830,
                                    0xFFC27A44, 0xFFCA8E5C, 0xFFE1A28E, 0xFFEEBCB0
                                )
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(Color(toneColors[idx]))
                                        .border(if (isSelected) 3.dp else 1.dp, if (isSelected) P.accent else P.line, CircleShape)
                                        .clickable { currentLook = currentLook.copy(skinToneIndex = idx) }
                                )
                            }
                        }
                    }

                    CustomizerTab.HAIR -> {
                        Text("Hairstyle", style = T.label, color = P.sub)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            itemsIndexed(PlayerLook.hairstyles) { idx, hName ->
                                val isSel = currentLook.hairIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) P.accent else P.surface2)
                                        .clickable { currentLook = currentLook.copy(hairIndex = idx) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        hName.replace("_", " ").capitalize(),
                                        style = T.small,
                                        color = if (isSel) P.onAccent else P.ink
                                    )
                                }
                            }
                        }
                    }

                    CustomizerTab.BEARD -> {
                        Text("Beard & Facial Hair", style = T.label, color = P.sub)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val beardNames = listOf("Clean Shaven", "Full Beard", "Goatee", "Stubble")
                            itemsIndexed(beardNames) { idx, bName ->
                                val isSel = currentLook.beardIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) P.accent else P.surface2)
                                        .clickable { currentLook = currentLook.copy(beardIndex = idx) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(bName, style = T.small, color = if (isSel) P.onAccent else P.ink)
                                }
                            }
                        }
                    }

                    CustomizerTab.FACE -> {
                        Text("Eyes & Expressions", style = T.label, color = P.sub)
                        Spacer(Modifier.height(12.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val eyeNames = listOf("Default Eyes", "Focused", "Clutch Stare", "Swagger", "Alert", "Blue", "Green")
                            itemsIndexed(eyeNames) { idx, eName ->
                                val isSel = currentLook.eyeIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) P.accent else P.surface2)
                                        .clickable { currentLook = currentLook.copy(eyeIndex = idx) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(eName, style = T.small, color = if (isSel) P.onAccent else P.ink)
                                }
                            }
                        }
                    }

                    CustomizerTab.GEAR -> {
                        Text("Headband", style = T.label, color = P.sub)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val hbNames = listOf("None", "White Headband", "Red Headband", "Black Headband")
                            itemsIndexed(hbNames) { idx, hbName ->
                                val isSel = currentLook.headbandIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) P.accent else P.surface2)
                                        .clickable { currentLook = currentLook.copy(headbandIndex = idx) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(hbName, style = T.small, color = if (isSel) P.onAccent else P.ink)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Text("Eyewear", style = T.label, color = P.sub)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            val ewNames = listOf("None", "Sports Rec Specs", "Clear Face Mask")
                            itemsIndexed(ewNames) { idx, ewName ->
                                val isSel = currentLook.eyewearIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (isSel) P.accent else P.surface2)
                                        .clickable { currentLook = currentLook.copy(eyewearIndex = idx) }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                ) {
                                    Text(ewName, style = T.small, color = if (isSel) P.onAccent else P.ink)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

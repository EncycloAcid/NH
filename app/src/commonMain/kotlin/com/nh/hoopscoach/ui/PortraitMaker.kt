package com.nh.hoopscoach.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.League
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

private enum class CustomTab(val title: String) {
    SKIN("Skin"),
    HAIR("Hair"),
    BEARD("Beard"),
    FACE("Face"),
    GEAR("Extras"),
}

private val CourtBg = Color(0xFF1A1714)
internal val SkinSwatches = listOf(
    0xFFEEBCB0L, 0xFFE1A28EL, 0xFFCA8E5CL, 0xFFC27A44L,
    0xFFA85830L, 0xFF8E4826L, 0xFF703A24L, 0xFF4B3930L,
)

/** Live pixel-art portrait maker — big zero-offset preview, chip options, instant updates. */
@Composable
fun PortraitMakerScreen(vm: GameViewModel, lg: League, playerId: Int) {
    val p = lg.playerOrNull(playerId) ?: run { BackBar("Portrait", onBack = { vm.back() }); return }
    val t = lg.teams.getOrNull(p.teamId)
    val jerseyKey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name)
    val primary = (t?.primary ?: 0xFF5C6B7AL).toInt()
    val secondary = (t?.secondary ?: 0xFFFFFFFFL).toInt()
    val saved = remember(playerId) { Face.of(p) }

    var face by remember(playerId) { mutableStateOf(saved) }
    var tab by remember { mutableStateOf(CustomTab.SKIN) }
    var seed by remember { mutableStateOf(0) }
    val dirty = face.encode() != saved.encode()

    Column(Modifier.fillMaxSize().background(P.bg)) {
        BackBar("Edit look", p.name, onBack = { vm.back() })

        // Live hero
        Box(
            Modifier
                .weight(1.2f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(CourtBg)
                    .border(2.dp, if (dirty) P.accent.copy(alpha = 0.55f) else P.line, RoundedCornerShape(20.dp)),
            ) {
                ArenaBackdrop(t, Modifier.fillMaxSize())
                BasketballCharacterCanvas(
                    face = face,
                    jerseyKey = jerseyKey,
                    primary = primary,
                    secondary = secondary,
                    number = p.number,
                    label = t?.abbr,
                    gear = com.nh.hoopscoach.game.Gear.decode(p.gear),
                    background = Color.Transparent,
                    corner = 0.dp,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (dirty) "Unsaved changes" else "No. ${p.number}" + (t?.let { " · ${if (lg.pro) it.name else it.school}" } ?: ""),
                style = T.small, color = if (dirty) P.accent else P.sub, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            SmallButton("Randomize") { seed++; face = Face.random(System.nanoTime() + seed) }
            Spacer(Modifier.width(6.dp))
            SmallButton("Default") { face = Face.generated(p) }
        }

        // Tabs
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CustomTab.entries.forEach { tTab ->
                val on = tab == tTab
                Text(
                    tTab.title,
                    style = T.bodyB,
                    color = if (on) P.onAccent else P.ink,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (on) P.accent else P.surface)
                        .border(1.dp, if (on) P.accent else P.line, RoundedCornerShape(12.dp))
                        .clickable { tab = tTab }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Options
        AnimatedContent(
            targetState = tab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            modifier = Modifier
                .weight(0.9f)
                .fillMaxWidth(),
            label = "customTab",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                when (current) {
                    CustomTab.SKIN -> {
                        PanelLabel("Skin tone · ${Face.OPTIONS[0][face.skin]}")
                        Spacer(Modifier.height(10.dp))
                        SkinRow(face.skin, { face = face.with(0, it) })
                    }

                    CustomTab.HAIR -> {
                        PanelLabel("Hairstyle · ${Face.OPTIONS[2][face.hair]}")
                        Spacer(Modifier.height(8.dp))
                        FaceOptionRow(
                            player = p, team = t, jerseyKey = jerseyKey,
                            labels = Face.OPTIONS[2], selected = face.hair,
                            preview = { face.with(2, it) },
                            onSelect = { face = face.with(2, it) },
                        )
                        Spacer(Modifier.height(16.dp))
                        PanelLabel("Hair color · ${Face.OPTIONS[3][face.hairColor]}")
                        Spacer(Modifier.height(8.dp))
                        OptionChipRow(
                            labels = Face.OPTIONS[3],
                            selected = face.hairColor,
                            onSelect = { face = face.with(3, it) },
                        )
                    }

                    CustomTab.BEARD -> {
                        PanelLabel("Facial hair · ${Face.OPTIONS[4][face.beard]}")
                        Spacer(Modifier.height(8.dp))
                        FaceOptionRow(
                            player = p, team = t, jerseyKey = jerseyKey,
                            labels = Face.OPTIONS[4], selected = face.beard,
                            preview = { face.with(4, it) },
                            onSelect = { face = face.with(4, it) },
                        )
                    }

                    CustomTab.FACE -> {
                        PanelLabel("Eyes · ${Face.OPTIONS[5][face.eyes]}")
                        Spacer(Modifier.height(8.dp))
                        FaceOptionRow(
                            player = p, team = t, jerseyKey = jerseyKey,
                            labels = Face.OPTIONS[5], selected = face.eyes,
                            preview = { face.with(5, it) },
                            onSelect = { face = face.with(5, it) },
                        )
                        Spacer(Modifier.height(16.dp))
                        PanelLabel("Expression · ${Face.OPTIONS[7][face.mouth]}")
                        Spacer(Modifier.height(8.dp))
                        FaceOptionRow(
                            player = p, team = t, jerseyKey = jerseyKey,
                            labels = Face.OPTIONS[7], selected = face.mouth,
                            preview = { face.with(7, it) },
                            onSelect = { face = face.with(7, it) },
                        )
                    }

                    CustomTab.GEAR -> {
                        PanelLabel("Extras · ${Face.OPTIONS[8][face.acc]}")
                        Spacer(Modifier.height(8.dp))
                        FaceOptionRow(
                            player = p, team = t, jerseyKey = jerseyKey,
                            labels = listOf("None", "White band", "Red band", "Black band", "Goggles", "Clear mask", "Carbon mask"), selected = face.acc,
                            preview = { face.with(8, it) },
                            onSelect = { face = face.with(8, it) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Sticky footer
        Column(
            Modifier
                .fillMaxWidth()
                .background(P.surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SecondaryButton("Undo", Modifier.weight(1f), enabled = dirty) { face = saved }
            PrimaryButton(if (dirty) "Lock in look" else "Done", Modifier.weight(2f)) {
                if (dirty) {
                    vm.act { it.playerOrNull(playerId)?.face = face.encode() }
                    Pixels.clear()
                    vm.toast("Look saved.")
                }
                vm.back()
            }
            }
        }
    }
}

@Composable
private fun PanelLabel(text: String) {
    Text(text, style = T.small, color = P.sub, fontWeight = FontWeight.SemiBold)
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun OptionChipRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        labels.forEachIndexed { i, label ->
            val on = selected == i
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) P.accent else P.surface)
                    .border(1.dp, if (on) P.accent else P.line, RoundedCornerShape(10.dp))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
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

@Composable
private fun FaceOptionRow(
    player: Player,
    team: Team?,
    jerseyKey: String?,
    labels: List<String>,
    selected: Int,
    preview: (Int) -> Face,
    onSelect: (Int) -> Unit,
) {
    val primary = (team?.primary ?: 0xFF5C6B7AL).toInt()
    val secondary = (team?.secondary ?: 0xFFFFFFFFL).toInt()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(end = 8.dp),
    ) {
        itemsIndexed(labels) { i, label ->
            val on = selected == i
            val option = preview(i)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(84.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (on) P.accentSoft else P.surface)
                    .border(if (on) 2.dp else 1.dp, if (on) P.accent else P.line, RoundedCornerShape(14.dp))
                    .clickable { onSelect(i) }
                    .padding(6.dp),
            ) {
                BasketballCharacterCanvas(
                    face = option,
                    jerseyKey = jerseyKey,
                    primary = primary,
                    secondary = secondary,
                    number = player.number,
                    label = team?.abbr,
                    background = CourtBg,
                    corner = 10.dp,
                    headshot = true,
                    modifier = Modifier.size(72.dp),
                )
                Text(
                    label,
                    style = T.small,
                    color = if (on) P.accent else P.sub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
                )
            }
        }
    }
}

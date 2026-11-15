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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nh.hoopscoach.game.Face

/** Chips that wrap onto new lines, so every option is in view (no sideways scrolling to find the last one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WrapChips(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier.fillMaxWidth().padding(horizontal = Gutter), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, l ->
            val sel = i == selected
            Box(
                Modifier.clip(RoundedCornerShape(50)).background(if (sel) P.accent else P.surface)
                    .border(1.dp, if (sel) P.accent else P.line, RoundedCornerShape(50)).clickable { onSelect(i) }.padding(horizontal = 14.dp, vertical = 7.dp),
            ) { Text(l, style = T.bodyB.copy(fontSize = 13.sp), color = if (sel) P.onAccent else P.sub) }
        }
    }
}

/** A row of skin tone swatches that always fits the width; the full name of the pick is shown above it. */
@Composable
fun SkinRow(selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Face.OPTIONS[0].indices.forEach { i ->
            val on = selected == i
            Box(
                Modifier.weight(1f).aspectRatio(1f).clip(CircleShape)
                    .background(Color(SkinSwatches[i.coerceIn(0, SkinSwatches.lastIndex)]))
                    .border(if (on) 3.dp else 1.dp, if (on) P.accent else P.line, CircleShape)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
        }
    }
}

/**
 * The look step of a new player: a live portrait in a plain practice jersey, with the main choices (skin,
 * hair, hair color, facial hair) right under it. Eyes, expression and extras wait for the full portrait
 * maker on MyPlayer.
 */
@Composable
fun LookPicker(face: Face, onFace: (Face) -> Unit, onRandom: () -> Unit, first: String, last: String, line: String, number: Int) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BasketballCharacterCanvas(
                face = face, jerseyKey = null, primary = 0xFF3A3F47.toInt(), secondary = 0xFFFFFFFF.toInt(), number = number,
                background = Color(0xFF1A1714), corner = 16.dp, modifier = Modifier.size(118.dp),
            )
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(first.trim().ifEmpty { "First" }.uppercase(), style = T.label, color = P.sub, maxLines = 1)
                Text(last.trim().ifEmpty { "Last name" }.uppercase(), style = T.title.copy(fontSize = 24.sp, lineHeight = 25.sp), color = if (last.isBlank()) P.faint else P.ink, maxLines = 1)
                Text(line, style = T.small, color = P.sub)
                Spacer(Modifier.height(8.dp))
                SmallButton("Randomize look") { onRandom() }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("SKIN · ${Face.OPTIONS[0][face.skin].uppercase()}", style = T.label.copy(fontSize = 10.sp), color = P.sub)
        Spacer(Modifier.height(6.dp))
        SkinRow(face.skin, { onFace(face.with(0, it)) })
    }
    Text("HAIR", style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 12.dp, bottom = 6.dp))
    WrapChips(Face.OPTIONS[2], face.hair, { onFace(face.with(2, it)) })
    if (face.hair != 0) {
        Text("HAIR COLOR", style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 12.dp, bottom = 6.dp))
        WrapChips(Face.OPTIONS[3], face.hairColor, { onFace(face.with(3, it)) })
    }
    Text("FACIAL HAIR", style = T.label.copy(fontSize = 10.sp), color = P.sub, modifier = Modifier.padding(start = Gutter + 4.dp, top = 12.dp, bottom = 6.dp))
    WrapChips(Face.OPTIONS[4], face.beard, { onFace(face.with(4, it)) })
    Text("Eyes, expression and extras are on MyPlayer once you've signed.", style = T.small, color = P.faint,
        modifier = Modifier.padding(horizontal = Gutter + 4.dp, vertical = 8.dp))
}

package com.nh.hoopscoach.ui

import com.nh.hoopscoach.imageBitmapFromArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear

/**
 * Live Jetpack Compose portrait for the modular 512×512 character system.
 * Recomposites on every [face] change and draws with [FilterQuality.None]
 * so retro pixels stay crisp. Layers are pre-aligned — no custom offsets.
 */
@Composable
fun BasketballCharacterCanvas(
    face: Face,
    jerseyKey: String?,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF23201D),
    primary: Int = 0xFF5C6B7A.toInt(),
    secondary: Int = 0xFFFFFFFF.toInt(),
    number: Int = 0,
    gear: Gear = Gear(),
    corner: androidx.compose.ui.unit.Dp = 16.dp,
    /** The team's abbreviation, worn across the chest when the team has no jersey art of its own. */
    label: String? = null,
) {
    SpriteSheets.install()
    val image = remember(face.encode(), jerseyKey, primary, secondary, number, gear.encode(), label) {
        val px = SpriteCompose.render(face, primary, secondary, number, gear, jerseyKey, label)
        imageBitmapFromArgb(px, SpriteCompose.N, SpriteCompose.N)
    }

    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(background, shape),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // Integer dst size keeps nearest-neighbor pixels crisp when scaled up.
            val side = minOf(size.width.toInt(), size.height.toInt()).coerceAtLeast(1)
            val ox = ((size.width.toInt() - side) / 2).coerceAtLeast(0)
            val oy = ((size.height.toInt() - side) / 2).coerceAtLeast(0)
            drawImage(
                image = image,
                dstOffset = IntOffset(ox, oy),
                dstSize = IntSize(side, side),
                filterQuality = FilterQuality.None,
            )
        }
    }
}

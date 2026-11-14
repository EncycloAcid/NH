package com.nh.hoopscoach.ui

import com.nh.hoopscoach.imageBitmapFromArgb
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * Pixel-Perfect Player Avatar Composable matching the NH repository theme.
 * Stacks 512x512 pre-aligned layers at IntOffset.Zero using FilterQuality.None.
 */
@Composable
fun PlayerAvatar(
    look: PlayerLook,
    teamAbbr: String? = null,
    size: Dp = 96.dp,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    number: Int = 0,
    teamSlot: String? = null,
) {
    SpriteSheets.install()
    val jerseyKey = SpriteSheets.jerseyKey(teamAbbr, teamSlot)
    val image = remember(look, jerseyKey, number) {
        val px = SpriteCompose.renderLook(look, jerseyKey, number)
        imageBitmapFromArgb(px, SpriteCompose.N, SpriteCompose.N)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(P.surface2)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
            drawImage(
                image = image,
                dstOffset = IntOffset.Zero,
                dstSize = dstSize,
                filterQuality = FilterQuality.None
            )
        }
    }
}

package com.nh.hoopscoach.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team

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
    cornerRadius: Dp = 12.dp
) {
    val ctx = LocalContext.current

    // Resolve asset paths
    val skinFile = PlayerLook.skinTones.getOrElse(look.skinToneIndex) { "04_medium_brown" }
    val bodyBmp: ImageBitmap? = CharacterSprites.get(ctx, "character/bodies/$skinFile.png")

    // Jersey lookup: check college jerseys by team abbr or default
    val jerseyPath = if (!teamAbbr.isNullOrBlank()) {
        "character/college_jerseys/${teamAbbr.lowercase()}.png"
    } else {
        "character/college_jerseys/01_duke.png"
    }
    val jerseyBmp: ImageBitmap? = CharacterSprites.get(ctx, jerseyPath)

    val hairFile = PlayerLook.hairstyles.getOrNull(look.hairIndex)
    val hairBmp: ImageBitmap? = hairFile?.let { CharacterSprites.get(ctx, "character/hairstyles/$it.png") }

    val beardFile = PlayerLook.beards.getOrNull(look.beardIndex)
    val beardBmp: ImageBitmap? = beardFile?.let { CharacterSprites.get(ctx, "character/beards/$it.png") }

    val headbandFile = PlayerLook.headbands.getOrNull(look.headbandIndex)
    val headbandBmp: ImageBitmap? = headbandFile?.let { CharacterSprites.get(ctx, "character/headbands/$it.png") }

    val eyewearFile = PlayerLook.eyewear.getOrNull(look.eyewearIndex)
    val eyewearBmp: ImageBitmap? = eyewearFile?.let { CharacterSprites.get(ctx, "character/eyewear/$it.png") }

    val mouthFile = PlayerLook.mouths.getOrNull(look.mouthIndex)
    val mouthBmp: ImageBitmap? = mouthFile?.let { CharacterSprites.get(ctx, "character/mouths/$it.png") }

    val eyeFile = PlayerLook.eyes.getOrNull(look.eyeIndex)
    val eyeBmp: ImageBitmap? = eyeFile?.let { CharacterSprites.get(ctx, "character/eyes/$it.png") }

    // Stacking order
    val layers = listOfNotNull(bodyBmp, eyeBmp, mouthBmp, beardBmp, jerseyBmp, headbandBmp, eyewearBmp, hairBmp)

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(P.surface2)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())
            for (layer in layers) {
                drawImage(
                    image = layer,
                    dstOffset = IntOffset.Zero,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.None // Retains crisp pixel art on all Android screens
                )
            }
        }
    }
}

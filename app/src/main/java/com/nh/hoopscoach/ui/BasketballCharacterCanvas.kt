package com.nh.hoopscoach.ui

import android.graphics.Bitmap
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
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
) {
    val context = LocalContext.current
    SpriteSheets.install(context.assets)
    val image = remember(face.encode(), jerseyKey, primary, secondary, number, gear.encode()) {
        val px = SpriteCompose.render(face, primary, secondary, number, gear, jerseyKey)
        Bitmap.createBitmap(px, SpriteCompose.N, SpriteCompose.N, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(corner))
            .background(background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dstSize = IntSize(size.width.toInt(), size.height.toInt())
            drawImage(
                image = image,
                dstOffset = IntOffset.Zero,
                dstSize = dstSize,
                filterQuality = FilterQuality.None,
            )
        }
    }
}

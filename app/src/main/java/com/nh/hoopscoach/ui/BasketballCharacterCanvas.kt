package com.nh.hoopscoach.ui

import android.graphics.BitmapFactory
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.nh.hoopscoach.game.Face

/**
 * Live Jetpack Compose canvas for the modular 512×512 character system.
 * Loads pre-aligned layers from `assets/character/` and draws each at [IntOffset.Zero]
 * with [FilterQuality.None] so retro pixels stay crisp on mobile.
 *
 * Layer order: body → eyes → mouth → beard → jersey → headband → eyewear → hair.
 */
@Composable
fun BasketballCharacterCanvas(
    face: Face,
    jerseyKey: String?,
    modifier: Modifier = Modifier,
    background: Color = Color(0xFF23201D),
) {
    val context = LocalContext.current

    fun load(path: String?): ImageBitmap? {
        if (path == null) return null
        return try {
            context.assets.open(path).use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }

    val skinFile = when (face.skin.coerceIn(0, 7)) {
        0 -> "08_pale_ivory"; 1 -> "07_fair_peach"; 2 -> "06_olive_bronze"
        3 -> "05_warm_caramel"; 4 -> "04_medium_brown"; 5 -> "03_chestnut_brown"
        6 -> "02_dark_chocolate"; else -> "01_deep_ebony"
    }
    val hairFile = SpriteSheets.HAIR_FILES.getOrNull(face.hair)
    val eyeFile = when (face.eyes) {
        1 -> "01_intense_focused"; 2 -> "02_clutch_stare"; 3 -> "03_confident_swagger"
        4 -> "04_alert_court_vision"; 5 -> "05_light_blue_eyes"; 6 -> "06_emerald_green_eyes"
        else -> null
    }
    val mouthFile = when (face.mouth) {
        1 -> "01_confident_grin"; 2 -> "02_game_face_shout"; 3 -> "03_athletic_mouthguard"
        else -> null
    }
    val beardFile = when (face.beard) {
        1 -> "03_stubble_shadow"; 2, 3, 4 -> "02_goatee"; 5 -> "01_full_beard"
        else -> null
    }
    val headbandFile = when {
        face.hair == 7 -> "03_stealth_black"
        face.acc == 1 -> "01_classic_white"
        face.acc == 2 -> "02_bulls_red"
        face.acc == 3 -> "03_stealth_black"
        else -> null
    }
    val eyewearFile = when (face.acc) {
        4 -> "01_sports_rec_specs"; 5 -> "02_protective_clear_mask"
        else -> null
    }
    val jerseyPath = when {
        jerseyKey == null -> null
        jerseyKey.firstOrNull()?.isDigit() == true -> "character/college_jerseys/$jerseyKey.png"
        else -> "character/nba_jerseys/$jerseyKey.png"
    }

    val bodyBitmap = remember(skinFile) { load("character/bodies/$skinFile.png") }
    val eyesBitmap = remember(eyeFile) { load(eyeFile?.let { "character/eyes/$it.png" }) }
    val mouthBitmap = remember(mouthFile) { load(mouthFile?.let { "character/mouths/$it.png" }) }
    val beardBitmap = remember(beardFile) { load(beardFile?.let { "character/beards/$it.png" }) }
    val jerseyBitmap = remember(jerseyPath) { load(jerseyPath) }
    val headbandBitmap = remember(headbandFile) { load(headbandFile?.let { "character/headbands/$it.png" }) }
    val eyewearBitmap = remember(eyewearFile) { load(eyewearFile?.let { "character/eyewear/$it.png" }) }
    val hairBitmap = remember(hairFile) { load(hairFile?.let { "character/hairstyles/$it.png" }) }

    val layers = listOfNotNull(
        bodyBitmap,
        eyesBitmap,
        mouthBitmap,
        beardBitmap,
        jerseyBitmap,
        headbandBitmap,
        eyewearBitmap,
        hairBitmap,
    )

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(background),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val dstSize = IntSize(size.width.toInt(), size.height.toInt())
            for (layer in layers) {
                drawImage(
                    image = layer,
                    dstOffset = IntOffset.Zero,
                    dstSize = dstSize,
                    filterQuality = FilterQuality.None,
                )
            }
        }
    }
}

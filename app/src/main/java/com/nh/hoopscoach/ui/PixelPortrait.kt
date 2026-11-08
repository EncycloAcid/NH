package com.nh.hoopscoach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draw every player as a retro portrait, real headshots included (a setting). */
val LocalRetroAll = compositionLocalOf { false }

/** Cel-shaded anime portraits (see [CelRender]), cached once drawn. */
object Pixels {
    const val N = CelRender.N
    private val cache = android.util.LruCache<String, ImageBitmap>(200)

    fun key(f: Face, primary: Long, secondary: Long, number: Int) = "${f.encode()}|$primary|$secondary|$number"

    fun cached(key: String): ImageBitmap? = cache.get(key)

    fun bitmap(f: Face, primary: Long, secondary: Long, number: Int): ImageBitmap {
        val key = key(f, primary, secondary, number)
        cache.get(key)?.let { return it }
        val px = CelRender.render(f, primary.toInt(), secondary.toInt(), number)
        val bmp = android.graphics.Bitmap.createBitmap(px, N, N, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }
}

/** His portrait, drawn off the main thread the first time; a team-colored card shows until it's ready. */
@Composable
fun PixelPortrait(p: Player, t: Team?, size: Dp, face: Face = Face.of(p)) {
    val primary = t?.primary ?: 0xFF5C6B7AL
    val secondary = t?.secondary ?: 0xFFFFFFFFL
    val key = Pixels.key(face, primary, secondary, p.number)
    val bmp by produceState(Pixels.cached(key), key) {
        if (value == null) value = withContext(Dispatchers.Default) { Pixels.bitmap(face, primary, secondary, p.number) }
    }
    val shape = RoundedCornerShape(size * 0.2f)
    Box(Modifier.size(size).clip(shape).background(Color(primary).copy(alpha = 0.55f))) {
        bmp?.let {
            Image(it, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        }
    }
}

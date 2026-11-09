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
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Draw every player as a sports-card portrait, real headshots included (a setting). */
val LocalRetroAll = compositionLocalOf { false }

/** Sports-card portraits (see [CelRender]), cached once drawn. */
object Pixels {
    const val N = CelRender.N
    private val cache = android.util.LruCache<String, ImageBitmap>(200)

    fun key(f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear()) = "${f.encode()}|$primary|$secondary|$number|${gear.encode()}"

    fun cached(key: String): ImageBitmap? = cache.get(key)

    fun bitmap(f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear()): ImageBitmap {
        val key = key(f, primary, secondary, number, gear)
        cache.get(key)?.let { return it }
        val px = CelRender.render(f, primary.toInt(), secondary.toInt(), number, gear)
        val bmp = android.graphics.Bitmap.createBitmap(px, N, N, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }
}

/** Draws the user's team's portraits ahead of time, so the roster opens with faces, not placeholders. */
fun warmPortraits(lg: com.nh.hoopscoach.game.League) {
    val t = lg.teams.getOrNull(lg.userTeamId) ?: return
    val all = lg.retroAll
    for (p in lg.rosterOf(t)) if (all || p.photo.isEmpty()) runCatching { Pixels.bitmap(Face.of(p), t.primary, t.secondary, p.number, Gear.decode(p.gear)) }
}

/** His portrait, drawn off the main thread the first time; a team-colored card shows until it's ready. */
@Composable
fun PixelPortrait(p: Player, t: Team?, size: Dp, face: Face = Face.of(p), gear: Gear = Gear.decode(p.gear)) {
    val primary = t?.primary ?: 0xFF5C6B7AL
    val secondary = t?.secondary ?: 0xFFFFFFFFL
    val key = Pixels.key(face, primary, secondary, p.number, gear)
    val bmp by produceState(Pixels.cached(key), key) {
        if (value == null) value = withContext(Dispatchers.Default) { Pixels.bitmap(face, primary, secondary, p.number, gear) }
    }
    val shape = RoundedCornerShape(size * 0.2f)
    Box(Modifier.size(size).clip(shape).background(Color(primary)), contentAlignment = androidx.compose.ui.Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, filterQuality = FilterQuality.High)
        else {
            // While it's drawn: his initials on his team's color.
            val fs = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.34f).toSp() }
            androidx.compose.material3.Text("${p.first.take(1)}${p.last.take(1)}".uppercase(), style = T.numSm.copy(fontSize = fs, lineHeight = fs), color = onColor(Color(primary)))
        }
    }
}

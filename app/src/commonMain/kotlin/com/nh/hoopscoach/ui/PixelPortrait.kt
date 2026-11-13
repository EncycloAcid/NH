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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import com.nh.hoopscoach.imageBitmapFromArgb
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val LocalRetroAll = compositionLocalOf { false }

object Pixels {
    const val N = SpriteCompose.N
    private val cache = HashMap<String, ImageBitmap>()
    private var cacheBytes = 0
    private const val MAX_BYTES = 32 * 1024 * 1024

    fun key(f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null) =
        "aligned1|${f.encode()}|$primary|$secondary|$number|${gear.encode()}|${jersey ?: ""}"

    fun cached(key: String): ImageBitmap? = cache[key]
    fun clear() { cache.clear(); cacheBytes = 0 }

    fun bitmap(f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null): ImageBitmap {
        val key = key(f, primary, secondary, number, gear, jersey)
        cache[key]?.let { return it }
        val px = SpriteCompose.render(f, primary.toInt(), secondary.toInt(), number, gear, jersey)
        val bmp = imageBitmapFromArgb(px, N, N)
        val size = N * N * 4
        if (cacheBytes + size > MAX_BYTES && cache.isNotEmpty()) {
            val victim = cache.keys.first()
            val old = cache.remove(victim)
            if (old != null) cacheBytes -= old.width * old.height * 4
        }
        cache[key] = bmp
        cacheBytes += size
        return bmp
    }
}

fun warmPortraits(lg: com.nh.hoopscoach.game.League) {
    val t = lg.teams.getOrNull(lg.userTeamId) ?: return
    val jersey = SpriteSheets.jerseyKey(t.abbr, t.slot, t.name)
    for (p in lg.rosterOf(t)) if (lg.retroAll || p.photo.isEmpty()) {
        runCatching { Pixels.bitmap(Face.of(p), t.primary, t.secondary, p.number, Gear.decode(p.gear), jersey) }
    }
}

@Composable
fun PixelPortrait(p: Player, t: Team?, size: Dp, face: Face = Face.of(p), gear: Gear = Gear.decode(p.gear)) {
    SpriteSheets.install()
    val primary = t?.primary ?: 0xFF5C6B7AL
    val secondary = t?.secondary ?: 0xFFFFFFFFL
    val jersey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name)
    val key = Pixels.key(face, primary, secondary, p.number, gear, jersey)
    val bmp by produceState(Pixels.cached(key), key) {
        value = Pixels.cached(key) ?: withContext(Dispatchers.Default) {
            Pixels.bitmap(face, primary, secondary, p.number, gear, jersey)
        }
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(Color(primary)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        } else {
            val fs = with(androidx.compose.ui.platform.LocalDensity.current) { (size * 0.34f).toSp() }
            androidx.compose.material3.Text("${p.first.take(1)}${p.last.take(1)}".uppercase(),
                style = T.numSm.copy(fontSize = fs, lineHeight = fs), color = onColor(Color(primary)))
        }
    }
}

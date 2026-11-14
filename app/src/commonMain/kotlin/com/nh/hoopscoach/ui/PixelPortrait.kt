package com.nh.hoopscoach.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
import com.nh.hoopscoach.imageBitmapFromArgb
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

val LocalRetroAll = compositionLocalOf { false }

/**
 * Finished portraits. Rows and cards use a half-size copy (a quarter of the memory), so well over a
 * hundred players stay ready and a list doesn't fall back to initials every time you come back to it;
 * only big portraits keep every pixel.
 */
object Pixels {
    const val N = SpriteCompose.N
    const val SMALL = N / 2

    private fun megabytes(share: Int, least: Int, most: Int) =
        (Runtime.getRuntime().maxMemory() / share).coerceIn(least.toLong() shl 20, most.toLong() shl 20).toInt()

    /** A least-recently-used shelf of finished portraits, safe to use from the drawing threads. */
    private class Shelf(private val max: Int) {
        private val map = LinkedHashMap<String, ImageBitmap>(64, 0.75f, true)
        private var bytes = 0

        @Synchronized fun get(key: String): ImageBitmap? = map[key]

        @Synchronized fun put(key: String, b: ImageBitmap) {
            map.remove(key)?.let { bytes -= it.width * it.height * 4 }
            val size = b.width * b.height * 4
            while (bytes + size > max && map.isNotEmpty()) {
                val victim = map.keys.first()
                map.remove(victim)?.let { bytes -= it.width * it.height * 4 }
            }
            map[key] = b
            bytes += size
        }

        @Synchronized fun clear() { map.clear(); bytes = 0 }
    }

    private val full = Shelf(megabytes(12, 12, 24))
    private val small = Shelf(megabytes(8, 16, 40))

    // Two background threads draw portraits, so a long list can't flood the machine with them, and a
    // portrait several rows ask for at the same moment is only drawn once.
    private val workers = Executors.newFixedThreadPool(2) { run ->
        Thread(run, "portraits").apply { isDaemon = true; priority = Thread.NORM_PRIORITY - 1 }
    }.asCoroutineDispatcher()
    private val scope = CoroutineScope(SupervisorJob() + workers)
    private val drawing = ConcurrentHashMap<String, Deferred<ImageBitmap?>>()

    fun key(f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null, label: String? = null) =
        "aligned2|${f.encode()}|$primary|$secondary|$number|${gear.encode()}|${jersey ?: ""}|${if (jersey == null) label.orEmpty() else ""}"

    fun cached(key: String, half: Boolean = false): ImageBitmap? = (if (half) small else full).get(key)

    fun clear() {
        full.clear()
        small.clear()
    }

    fun bitmap(
        f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null,
        label: String? = null, half: Boolean = false,
    ): ImageBitmap {
        val key = key(f, primary, secondary, number, gear, jersey, label)
        val shelf = if (half) small else full
        shelf.get(key)?.let { return it }
        val px = SpriteCompose.render(f, primary.toInt(), secondary.toInt(), number, gear, jersey, label)
        val bmp = if (half) imageBitmapFromArgb(SpriteCompose.half(px), SMALL, SMALL) else imageBitmapFromArgb(px, N, N)
        shelf.put(key, bmp)
        return bmp
    }

    /** [bitmap] off the main thread. Null when it couldn't be drawn; the caller keeps its placeholder. */
    suspend fun load(
        f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null,
        label: String? = null, half: Boolean = false,
    ): ImageBitmap? {
        val key = key(f, primary, secondary, number, gear, jersey, label)
        cached(key, half)?.let { return it }
        val job = drawing.computeIfAbsent(if (half) "$key@half" else key) { slot ->
            scope.async {
                try {
                    bitmap(f, primary, secondary, number, gear, jersey, label, half)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Throwable) {
                    null                  // a missing layer or a full heap costs one portrait, not the app
                } finally {
                    drawing.remove(slot)
                }
            }
        }
        return job.await()
    }
}

fun warmPortraits(lg: com.nh.hoopscoach.game.League) {
    val t = lg.teams.getOrNull(lg.userTeamId) ?: return
    val jersey = SpriteSheets.jerseyKey(t.abbr, t.slot, t.name)
    for (p in lg.rosterOf(t)) if (lg.retroAll || p.photo.isEmpty()) {
        runCatching { Pixels.bitmap(Face.of(p), t.primary, t.secondary, p.number, Gear.decode(p.gear), jersey, t.abbr, half = true) }
    }
}

@Composable
fun PixelPortrait(p: Player, t: Team?, size: Dp, face: Face = Face.of(p), gear: Gear = Gear.decode(p.gear)) {
    SpriteSheets.install()
    val primary = t?.primary ?: 0xFF5C6B7AL
    val secondary = t?.secondary ?: 0xFFFFFFFFL
    val jersey = SpriteSheets.jerseyKey(t?.abbr, t?.slot, t?.name)
    val label = t?.abbr
    val half = with(LocalDensity.current) { size.toPx() } <= Pixels.SMALL * 0.8f
    val key = Pixels.key(face, primary, secondary, p.number, gear, jersey, label)
    // Keyed on the portrait, so a row that now shows someone else never keeps the last player's picture.
    var bmp by remember(key, half) { mutableStateOf(Pixels.cached(key, half)) }
    LaunchedEffect(key, half) {
        if (bmp == null) bmp = Pixels.load(face, primary, secondary, p.number, gear, jersey, label, half)
    }
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.2f)).background(Color(primary)),
        contentAlignment = androidx.compose.ui.Alignment.Center) {
        val b = bmp
        if (b != null) {
            Image(b, p.name, Modifier.fillMaxSize(), contentScale = ContentScale.Fit, filterQuality = FilterQuality.None)
        } else {
            val fs = with(LocalDensity.current) { (size * 0.34f).toSp() }
            androidx.compose.material3.Text("${p.first.take(1)}${p.last.take(1)}".uppercase(),
                style = T.numSm.copy(fontSize = fs, lineHeight = fs), color = onColor(Color(primary)))
        }
    }
}

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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.nh.hoopscoach.game.Face
import com.nh.hoopscoach.game.Gear
import com.nh.hoopscoach.game.Player
import com.nh.hoopscoach.game.Team
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

    private class Shelf(bytes: Int) : android.util.LruCache<String, ImageBitmap>(bytes) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    private val full = Shelf(megabytes(12, 12, 24))
    private val small = Shelf(megabytes(8, 16, 40))

    // Two background threads draw portraits, so a long list can't flood the phone with them, and a
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
        full.evictAll()
        small.evictAll()
    }

    fun bitmap(
        f: Face, primary: Long, secondary: Long, number: Int, gear: Gear = Gear(), jersey: String? = null,
        label: String? = null, half: Boolean = false,
    ): ImageBitmap {
        val key = key(f, primary, secondary, number, gear, jersey, label)
        val shelf = if (half) small else full
        shelf.get(key)?.let { return it }
        val px = SpriteCompose.render(f, primary.toInt(), secondary.toInt(), number, gear, jersey, label)
        val bmp = if (half) android.graphics.Bitmap.createBitmap(SpriteCompose.half(px), SMALL, SMALL, android.graphics.Bitmap.Config.ARGB_8888)
            else android.graphics.Bitmap.createBitmap(px, N, N, android.graphics.Bitmap.Config.ARGB_8888)
        return bmp.asImageBitmap().also { shelf.put(key, it) }
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
    SpriteSheets.install(LocalContext.current.assets)
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

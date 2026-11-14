package com.nh.hoopscoach

import androidx.compose.ui.graphics.asSkiaBitmap

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontVariation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@OptIn(ExperimentalTextApi::class)
private fun resourceFont(path: String, weight: Int) = Font(
    identity = "$path-$weight",
    data = checkNotNull(readResource(path)) { "Missing font resource $path" },
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private fun readResource(path: String): ByteArray? {
    val loader = Thread.currentThread().contextClassLoader
        ?: PlatformContext::class.java.classLoader
    return loader?.getResourceAsStream(path)?.use { it.readBytes() }
}

actual fun hoopsDisplayFont(): FontFamily = FontFamily(
    resourceFont("fonts/big_shoulders.ttf", 600),
    resourceFont("fonts/big_shoulders.ttf", 700),
    resourceFont("fonts/big_shoulders.ttf", 800),
    resourceFont("fonts/big_shoulders.ttf", 900),
)

actual fun hoopsBodyFont(): FontFamily = FontFamily(
    resourceFont("fonts/manrope.ttf", 400),
    resourceFont("fonts/manrope.ttf", 500),
    resourceFont("fonts/manrope.ttf", 600),
    resourceFont("fonts/manrope.ttf", 700),
    resourceFont("fonts/manrope.ttf", 800),
)

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()

actual fun assetBytes(path: String): ByteArray? {
    readResource(path)?.let { return it }
    // Fall back to on-disk assets for unit tests / local runs from the repo root.
    val candidates = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("../app/src/main/assets/$path"),
        File(path),
    )
    return candidates.firstOrNull { it.isFile }?.readBytes()
}

actual fun decodeArgbBitmap(bytes: ByteArray): ArgbBitmap? {
    val image = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    val w = image.width
    val h = image.height
    val info = ImageInfo(w, h, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL)
    val skia = org.jetbrains.skia.Bitmap()
    if (!skia.allocPixels(info)) return null
    if (!image.readPixels(skia)) return null
    // Row length is width * 4 bytes; asking with 0 made Skia return nothing, so every sprite (and every
    // player portrait) came out empty on PC.
    val buffer = skia.readPixels(info, w * 4, 0, 0) ?: return null
    val pixels = IntArray(w * h)
    var i = 0
    var p = 0
    while (i < buffer.size && p < pixels.size) {
        val r = buffer[i].toInt() and 0xFF
        val g = buffer[i + 1].toInt() and 0xFF
        val b = buffer[i + 2].toInt() and 0xFF
        val a = buffer[i + 3].toInt() and 0xFF
        pixels[p++] = (a shl 24) or (r shl 16) or (g shl 8) or b
        i += 4
    }
    return ArgbBitmap(w, h, pixels)
}

actual fun imageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap {
    val rgba = ByteArray(width * height * 4)
    var o = 0
    for (px in pixels) {
        rgba[o++] = ((px ushr 16) and 0xFF).toByte()
        rgba[o++] = ((px ushr 8) and 0xFF).toByte()
        rgba[o++] = (px and 0xFF).toByte()
        rgba[o++] = ((px ushr 24) and 0xFF).toByte()
    }
    val image = Image.makeRaster(
        ImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
        rgba,
        width * 4,
    )
    return image.toComposeImageBitmap()
}

actual fun squareJpegThumbnail(bytes: ByteArray, maxSide: Int): ByteArray? {
    val src = decodeArgbBitmap(bytes) ?: return null
    val side = minOf(src.width, src.height)
    val x0 = (src.width - side) / 2
    val y0 = (src.height - side) / 2
    val outSide = minOf(side, maxSide)
    val out = IntArray(outSide * outSide)
    for (y in 0 until outSide) {
        for (x in 0 until outSide) {
            val sx = x0 + x * side / outSide
            val sy = y0 + y * side / outSide
            out[y * outSide + x] = src.getPixel(sx, sy)
        }
    }
    val rgba = ByteArray(outSide * outSide * 4)
    var o = 0
    for (px in out) {
        rgba[o++] = ((px ushr 16) and 0xFF).toByte()
        rgba[o++] = ((px ushr 8) and 0xFF).toByte()
        rgba[o++] = (px and 0xFF).toByte()
        rgba[o++] = ((px ushr 24) and 0xFF).toByte()
    }
    val image = Image.makeRaster(
        ImageInfo(outSide, outSide, ColorType.RGBA_8888, ColorAlphaType.UNPREMUL),
        rgba,
        outSide * 4,
    )
    return image.encodeToData(EncodedImageFormat.JPEG, 88)?.bytes
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // Desktop uses Escape via the window key handler in Main.kt; nothing to wire here.
}

@Composable
actual fun rememberSaveExporter(
    suggestedName: String,
    getBytes: suspend () -> ByteArray?,
    onResult: (ok: Boolean) -> Unit,
    mime: String,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember(suggestedName) {
        {
            scope.launch {
                val ok = runCatching {
                    val bytes = getBytes() ?: return@runCatching false
                    val target = withContext(Dispatchers.IO) {
                        pickSaveFile(suggestedName)
                    } ?: return@runCatching false
                    withContext(Dispatchers.IO) { target.writeBytes(bytes) }
                    true
                }.getOrDefault(false)
                onResult(ok)
            }
            Unit
        }
    }
}

@Composable
actual fun rememberSaveImporter(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit {
    val scope = rememberCoroutineScope()
    return remember {
        {
            scope.launch {
                val bytes = runCatching {
                    val file = withContext(Dispatchers.IO) { pickOpenFile() } ?: return@runCatching null
                    withContext(Dispatchers.IO) { file.readBytes() }
                }.getOrNull()
                onResult(bytes)
            }
            Unit
        }
    }
}

@Composable
actual fun rememberProfilePhotoPicker(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit {
    // Profile photo picking is Android-only on this release.
    return remember {
        { onResult(null) }
    }
}

private fun pickSaveFile(suggestedName: String): File? {
    val dialog = FileDialog(null as Frame?, if (suggestedName.endsWith(".png")) "Save image" else "Back up dynasty", FileDialog.SAVE)
    dialog.file = suggestedName
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

private fun pickOpenFile(): File? {
    val dialog = FileDialog(null as Frame?, "Restore dynasty", FileDialog.LOAD)
    dialog.isVisible = true
    val dir = dialog.directory ?: return null
    val name = dialog.file ?: return null
    return File(dir, name)
}

actual fun encodePng(image: androidx.compose.ui.graphics.ImageBitmap): ByteArray? = runCatching {
    org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(org.jetbrains.skia.EncodedImageFormat.PNG)?.bytes
}.getOrNull()

actual object GameSounds {
    private val format = javax.sound.sampled.AudioFormat(SoundSynth.RATE.toFloat(), 16, 1, true, false)
    private var crowdClip: javax.sound.sampled.Clip? = null

    private fun gain(clip: javax.sound.sampled.Clip, level: Float) = runCatching {
        val c = clip.getControl(javax.sound.sampled.FloatControl.Type.MASTER_GAIN) as javax.sound.sampled.FloatControl
        val db = (20 * kotlin.math.log10(level.coerceIn(0.01f, 1f).toDouble())).toFloat()
        c.value = db.coerceIn(c.minimum, c.maximum)
    }

    actual fun crowd(level: Float) {
        runCatching {
            if (level <= 0f) { crowdClip?.stop(); crowdClip?.close(); crowdClip = null; return }
            val clip = crowdClip ?: javax.sound.sampled.AudioSystem.getClip().also { c ->
                val b = SoundSynth.bytes(SoundSynth.crowd)
                c.open(format, b, 0, b.size)
                c.loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY)
                crowdClip = c
            }
            gain(clip, level)
        }
    }

    actual fun buzzer() {
        runCatching {
            val c = javax.sound.sampled.AudioSystem.getClip()
            val b = SoundSynth.bytes(SoundSynth.buzzer)
            c.open(format, b, 0, b.size)
            gain(c, 0.8f)
            c.addLineListener { e -> if (e.type == javax.sound.sampled.LineEvent.Type.STOP) c.close() }
            c.start()
        }
    }
}

actual fun updateHomeWidget(title: String, line1: String, line2: String) {}

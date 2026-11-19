package com.nh.hoopscoach

import androidx.compose.ui.graphics.asAndroidBitmap

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

object AndroidAssets {
    @Volatile var appContext: Context? = null
}

@OptIn(ExperimentalTextApi::class)
private fun varFont(res: Int, w: Int) = Font(
    res,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

actual fun hoopsDisplayFont(): FontFamily = FontFamily(
    varFont(R.font.big_shoulders, 600),
    varFont(R.font.big_shoulders, 700),
    varFont(R.font.big_shoulders, 800),
    varFont(R.font.big_shoulders, 900),
)

actual fun hoopsBodyFont(): FontFamily = FontFamily(
    varFont(R.font.manrope, 400),
    varFont(R.font.manrope, 500),
    varFont(R.font.manrope, 600),
    varFont(R.font.manrope, 700),
    varFont(R.font.manrope, 800),
)

actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: error("Could not decode image")
    return bmp.asImageBitmap()
}

actual fun assetBytes(path: String): ByteArray? {
    val ctx = AndroidAssets.appContext
    if (ctx != null) {
        try {
            return ctx.assets.open(path).use { it.readBytes() }
        } catch (_: Exception) { /* fall through */ }
    }
    // Unit tests / classpath fallback (and disk paths used by SpriteSheets tests).
    runCatching {
        AndroidAssets::class.java.classLoader
            ?.getResourceAsStream(path)
            ?.use { return it.readBytes() }
    }
    val candidates = listOf(
        File("src/main/assets/$path"),
        File("app/src/main/assets/$path"),
        File("../app/src/main/assets/$path"),
        File(path),
    )
    return candidates.firstOrNull { it.isFile }?.readBytes()
}

actual fun decodeArgbBitmap(bytes: ByteArray): ArgbBitmap? {
    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
    val w = bmp.width
    val h = bmp.height
    val pixels = IntArray(w * h)
    bmp.getPixels(pixels, 0, w, 0, 0, w, h)
    return ArgbBitmap(w, h, pixels)
}

actual fun imageBitmapFromArgb(pixels: IntArray, width: Int, height: Int): ImageBitmap =
    Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888).asImageBitmap()

actual fun squareJpegThumbnail(bytes: ByteArray, maxSide: Int): ByteArray? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return null
    val side = minOf(bmp.width, bmp.height)
    val square = Bitmap.createBitmap(bmp, (bmp.width - side) / 2, (bmp.height - side) / 2, side, side)
    val out = Bitmap.createScaledBitmap(square, minOf(side, maxSide), minOf(side, maxSide), true)
    return ByteArrayOutputStream().use { stream ->
        out.compress(Bitmap.CompressFormat.JPEG, 88, stream)
        stream.toByteArray()
    }
}

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

@Composable
actual fun rememberSaveExporter(
    suggestedName: String,
    getBytes: suspend () -> ByteArray?,
    onResult: (ok: Boolean) -> Unit,
    mime: String,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(mime),
    ) { uri ->
        if (uri == null) {
            onResult(false)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val ok = runCatching {
                val bytes = getBytes() ?: return@runCatching false
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(bytes) }
                }
                true
            }.getOrDefault(false)
            onResult(ok)
        }
    }
    return remember(suggestedName, launcher) {
        {
            runCatching { launcher.launch(suggestedName) }.onFailure { onResult(false) }
            Unit
        }
    }
}

@Composable
actual fun rememberSaveImporter(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
            }.getOrNull()
            onResult(bytes)
        }
    }
    return remember(launcher) {
        {
            runCatching { launcher.launch(arrayOf("*/*")) }.onFailure { onResult(null) }
            Unit
        }
    }
}

@Composable
actual fun rememberProfilePhotoPicker(
    onResult: (bytes: ByteArray?) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) {
            onResult(null)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val bytes = runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                }
            }.getOrNull()
            onResult(bytes)
        }
    }
    return remember(launcher) {
        {
            runCatching {
                launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }.onFailure { onResult(null) }
            Unit
        }
    }
}

actual fun encodePng(image: androidx.compose.ui.graphics.ImageBitmap): ByteArray? = runCatching {
    val out = java.io.ByteArrayOutputStream()
    image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
    out.toByteArray()
}.getOrNull()

actual object GameSounds {
    private var crowdTrack: android.media.AudioTrack? = null

    private fun track(data: ShortArray, loop: Boolean): android.media.AudioTrack {
        val t = android.media.AudioTrack.Builder()
            .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(android.media.AudioFormat.Builder().setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SoundSynth.RATE).setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO).build())
            .setTransferMode(android.media.AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(data.size * 2)
            .build()
        t.write(data, 0, data.size)
        if (loop) t.setLoopPoints(0, data.size, -1)
        return t
    }

    actual fun crowd(level: Float) {
        runCatching {
            if (level <= 0f) { crowdTrack?.stop(); crowdTrack?.release(); crowdTrack = null; return }
            val t = crowdTrack ?: track(SoundSynth.crowd, true).also { it.play(); crowdTrack = it }
            t.setVolume(level.coerceIn(0f, 1f))
        }
    }

    private val shots = HashMap<String, android.media.AudioTrack>()

    actual fun play(name: String, volume: Float) {
        runCatching {
            val t = shots[name] ?: run {
                val data = SoundSynth.sound(name) ?: return
                track(data, false).also { shots[name] = it }
            }
            if (t.playState == android.media.AudioTrack.PLAYSTATE_PLAYING) t.stop()
            t.reloadStaticData()
            t.setVolume(volume.coerceIn(0f, 1f))
            t.play()
        }
    }

    actual fun buzzer() {
        runCatching {
            val t = track(SoundSynth.buzzer, false)
            t.setVolume(0.8f)
            t.play()
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ runCatching { t.release() } }, 1500)
        }
    }
}

actual fun updateHomeWidget(title: String, line1: String, line2: String) {
    runCatching {
        val ctx = AndroidAssets.appContext ?: return
        ctx.getSharedPreferences("hoops_widget", android.content.Context.MODE_PRIVATE).edit()
            .putString("title", title).putString("line1", line1).putString("line2", line2).apply()
        HoopsWidget.refresh(ctx)
    }
}

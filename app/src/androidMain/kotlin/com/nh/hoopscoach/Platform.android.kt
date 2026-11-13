package com.nh.hoopscoach

import android.content.Context
import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
        ?: error("Could not decode logo image")
    return bmp.asImageBitmap()
}

actual fun logoBytes(abbr: String): ByteArray? {
    val ctx = AndroidAssets.appContext ?: return null
    return try {
        ctx.assets.open("logos/${abbr.lowercase()}.webp").use { it.readBytes() }
    } catch (_: Exception) {
        // Fall back to packaged classpath resources (commonMain/resources).
        runCatching {
            AndroidAssets::class.java.classLoader
                ?.getResourceAsStream("logos/${abbr.lowercase()}.webp")
                ?.use { it.readBytes() }
        }.getOrNull()
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
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
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
